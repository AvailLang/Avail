/**
 * L1InstructionStepper.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.avail.interpreter.levelTwo;

import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Interpreter.*;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import java.util.*;
import java.util.logging.Level;
import com.avail.descriptor.*;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.exceptions.VariableGetException;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.levelTwo.operation.L2_INTERPRET_ONE_L1_INSTRUCTION;
import com.avail.interpreter.levelTwo.operation.L2_INTERPRET_UNTIL_INTERRUPT;
import com.avail.interpreter.levelTwo.register.FixedRegister;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.MutableOrNull;

/**
 * This class is used to simulate the effect of level one nybblecodes during
 * execution of the {@link L2_INTERPRET_UNTIL_INTERRUPT} instruction, on behalf
 * of a {@link Interpreter}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L1InstructionStepper
implements L1OperationDispatcher
{
	/**
	 * The {@link Interpreter} on whose behalf to step level one nybblecodes.
	 */
	Interpreter interpreter;

	/**
	 * A reusable buffer for holding arguments for method invocations.
	 */
	private final List<AvailObject> argsBuffer;

	/** Statistic for recording checked non-primitive returns from L1. */
	private final static Statistic checkedNonPrimitiveReturn =
		new Statistic(
			"Checked non-primitive return from L1",
			StatisticReport.NON_PRIMITIVE_RETURN_LEVELS);

	/**
	 * Construct a new {@link L1InstructionStepper}.
	 *
	 * @param interpreter
	 *            The {@link Interpreter} on whose behalf to step through
	 *            level one nybblecode instructions.
	 */
	public L1InstructionStepper (final Interpreter interpreter)
	{
		this.interpreter = interpreter;
		argsBuffer = interpreter.argsBuffer;
	}

	/**
	 * Answer the subscript of the integer register reserved for holding the
	 * current (virtualized) continuation's {@linkplain AvailObject#pc() pc}
	 * (program counter).
	 *
	 * @return The subscript to use with {@link Interpreter#integerAt(int)}.
	 */
	public final static int pcRegister ()
	{
		// Reserved.
		return 1;
	}

	/**
	 * Answer the subscript of the integer register reserved for holding the
	 * current (virtualized) continuation's {@linkplain AvailObject#stackp()
	 * stackp} (stack pointer). While in this register, the value refers to the
	 * exact pointer register number rather than the value that would be stored
	 * in a continuation's stackp slot, so adjustments must be made during
	 * reification and explosion of continuations.
	 *
	 * @return The subscript to use with {@link Interpreter#integerAt(int)}.
	 */
	public final static int stackpRegister ()
	{
		// Reserved.
		return 2;
	}

	/**
	 * Answer the subscript of the integer register reserved for holding the
	 * flag (0=false, 1=true) indicating whether the current virtualized
	 * continuation (that lives only in the registers) needs to check its return
	 * result's type.  If this continuation becomes reified, this flag will be
	 * placed in the {@link
	 * ContinuationDescriptor#o_SkipReturnFlag(AvailObject)}, and extracted into
	 * this register again when resuming the continuation.  Any continuation
	 * constructed artificially will have this flag clear for safety.
	 *
	 * @return The subscript to use with {@link Interpreter#integerAt(int)}.
	 */
	public final static int skipReturnCheckRegister ()
	{
		// Reserved.
		return 3;
	}

	/**
	 * Read from the specified integer register.
	 *
	 * @param index Which integer register to read.
	 * @return The value from that register.
	 */
	private int integerAt (final int index)
	{
		return interpreter.integerAt(index);
	}

	/**
	 * Write to the specified integer register.
	 *
	 * @param index Which integer register to write.
	 * @param value The value to write to that register.
	 */
	private void integerAtPut (final int index, final int value)
	{
		interpreter.integerAtPut(index, value);
	}

	/**
	 * Read from the specified object register.
	 *
	 * @param index Which object register to read.
	 * @return The value from that register.
	 */
	private AvailObject pointerAt (final int index)
	{
		return interpreter.pointerAt(index);
	}

	/**
	 * Write to the specified object register.
	 *
	 * @param index Which object register to write.
	 * @param value The value to write to that register.
	 */
	private void pointerAtPut (
		final int index,
		final A_BasicObject value)
	{
		interpreter.pointerAtPut(index, value);
	}

	/**
	 * Read from the specified {@link FixedRegister fixed object register}.
	 *
	 * @param fixedRegister Which fixed object register to read.
	 * @return The value from that register.
	 */
	private AvailObject pointerAt (
		final FixedRegister fixedRegister)
	{
		return interpreter.pointerAt(fixedRegister);
	}

	/**
	 * Write to the specified {@link FixedRegister fixed object register}.
	 *
	 * @param fixedRegister Which fixed object register to write.
	 * @param value The value to write to that register.
	 */
	private void pointerAtPut (
		final FixedRegister fixedRegister,
		final A_BasicObject value)
	{
		interpreter.pointerAtPut(fixedRegister, value);
	}

	/**
	 * Extract an integer from nybblecode stream.
	 * @return
	 */
	private int getInteger ()
	{
		final A_Function function = interpreter.pointerAt(FUNCTION);
		final A_RawFunction code = function.code();
		final A_Tuple nybbles = code.nybbles();
		int pc = interpreter.integerAt(pcRegister());
		final byte firstNybble = nybbles.extractNybbleFromTupleAt(pc);
		pc++;
		int value = 0;
		final byte[] counts =
		{
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 4, 8
		};
		for (int count = counts[firstNybble]; count > 0; count--, pc++)
		{
			value = (value << 4) + nybbles.extractNybbleFromTupleAt(pc);
		}
		final byte[] offsets =
		{
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 26, 42, 58, 0, 0
		};
		value += offsets[firstNybble];
		interpreter.integerAtPut(pcRegister(), pc);
		return value;
	}

	/**
	 * Push a value onto the current virtualized continuation's stack (which
	 * is just some consecutively-numbered pointer registers and an integer
	 * register that maintains the position).
	 *
	 * @param value The value to push on the virtualized stack.
	 */
	private final void push (final A_BasicObject value)
	{
		int stackp = integerAt(stackpRegister());
		stackp--;
		assert stackp >= argumentOrLocalRegister(1);
		pointerAtPut(stackp, value);
		integerAtPut(stackpRegister(), stackp);
	}

	/**
	 * Pop a value off the current virtualized continuation's stack (which
	 * is just some consecutively-numbered pointer registers and an integer
	 * register that maintains the position).
	 *
	 * @return The value popped off the virtualized stack.
	 */
	private final AvailObject pop ()
	{
		final int stackp = integerAt(stackpRegister());
		assert stackp <= argumentOrLocalRegister(
			pointerAt(FUNCTION).code().numArgsAndLocalsAndStack());
		final AvailObject popped = pointerAt(stackp);
		// Clear the stack slot
		pointerAtPut(stackp, NilDescriptor.nil());
		integerAtPut(stackpRegister(), stackp + 1);
		return popped;
	}

	/**
	 * Extract the specified literal from the current function's code.
	 *
	 * @param literalIndex
	 *            The index of the literal to look up in the current
	 *            function's code.
	 * @return
	 *            The literal extracted from the specified literal slot of
	 *            the code.
	 */
	private final AvailObject literalAt (final int literalIndex)
	{
		final A_Function function = pointerAt(FUNCTION);
		final A_RawFunction code = function.code();
		return code.literalAt(literalIndex);
	}

	/**
	 * Create a continuation from the values of the interpreter's registers.
	 * In particular, the {@link L1InstructionStepper} treats the object
	 * registers immediately following the fixed registers as holding the
	 * exploded content of the current continuation.  The {@link
	 * #pcRegister()}, {@link #stackpRegister()}, and {@link
	 * #skipReturnCheckRegister()} are also part of the state manipulated by the
	 * L1 stepper.
	 *
	 * <p>
	 * Write the resulting continuation into the {@linkplain
	 * FixedRegister#CALLER caller register}.
	 * </p>
	 */
	public void reifyContinuation ()
	{
		final A_Function function = pointerAt(FUNCTION);
		final A_RawFunction code = function.code();
		final L2Chunk chunk = interpreter.chunk();
		assert chunk == L2Chunk.unoptimizedChunk();
		final A_Continuation continuation =
			ContinuationDescriptor.createExceptFrame(
				function,
				pointerAt(CALLER),
				integerAt(pcRegister()),
				integerAt(stackpRegister()) + 1 - argumentOrLocalRegister(1),
				integerAt(skipReturnCheckRegister()) != 0,
				chunk,
				L2Chunk.offsetToContinueUnoptimizedChunk());
		for (int i = code.numArgsAndLocalsAndStack(); i >= 1; i--)
		{
			continuation.argOrLocalOrStackAtPut(
				i,
				pointerAt(argumentOrLocalRegister(i)));
		}
		interpreter.wipeObjectRegisters();
		pointerAtPut(CALLER, continuation);
	}

	/**
	 * Execute a single level one nybblecode.  Used by {@link
	 * L2_INTERPRET_UNTIL_INTERRUPT} and {@link
	 * L2_INTERPRET_ONE_L1_INSTRUCTION} to interpret unoptimized code.
	 */
	public void stepLevelOne ()
	{
		final A_Function function = interpreter.pointerAt(FUNCTION);
		final A_RawFunction code = function.code();
		final A_Tuple nybbles = code.nybbles();
		final int pc = interpreter.integerAt(pcRegister());

		int depth = 0;
		if (debugL1)
		{
			for (
				A_Continuation c = interpreter.pointerAt(CALLER);
				!c.equalsNil();
				c = c.caller())
			{
				depth++;
			}
		}

		// Before we extract the nybblecode, make sure that the PC hasn't
		// passed the end of the instruction sequence. If we have, then
		// execute an L1Implied_doReturn.
		if (pc > nybbles.tupleSize())
		{
			assert pc == nybbles.tupleSize() + 1;
			if (debugL1)
			{
				Interpreter.log(
					interpreter.fiber,
					Level.FINER,
					"L1 Return (pc={0}, depth={1})",
					pc,
					depth);
			}
			interpreter.levelOneStepper.L1Implied_doReturn();
			return;
		}
		final int nybble = nybbles.extractNybbleFromTupleAt(pc);
		interpreter.integerAtPut(pcRegister(), (pc + 1));

		final L1Operation operation = L1Operation.all()[nybble];
		if (debugL1)
		{
			Interpreter.log(
				interpreter.fiber,
				Level.FINER,
				"{0} (pc={1}, depth={2})",
				operation,
				pc,
				depth);
		}
		operation.dispatch(interpreter.levelOneStepper);
	}

	@Override
	public void L1_doCall()
	{
		final A_Bundle bundle = literalAt(getInteger());
		final A_Type expectedReturnType = literalAt(getInteger());
		final int numArgs = bundle.bundleMethod().numArgs();
		if (debugL1)
		{
			Interpreter.log(
				interpreter.fiber,
				Level.FINE,
				"L1 call {0}",
				bundle.message().atomName());
		}
		argsBuffer.clear();
		for (int i = numArgs; i >= 1; i--)
		{
			argsBuffer.add(0, pop());
		}
		final A_Method method = bundle.bundleMethod();
		final MutableOrNull<AvailErrorCode> errorCode = new MutableOrNull<>();
		final A_Definition matching = method.lookupByValuesFromList(
			argsBuffer, errorCode);
		if (matching.equalsNil())
		{
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().invalidMessageSendFunction(),
				Arrays.asList(
					errorCode.value().numericCode(),
					method,
					TupleDescriptor.fromList(argsBuffer)),
				true);
			return;
		}
		if (matching.isForwardDefinition())
		{
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().invalidMessageSendFunction(),
				Arrays.asList(
					E_FORWARD_METHOD_DEFINITION.numericCode(),
					method,
					TupleDescriptor.fromList(argsBuffer)),
				true);
			return;
		}
		if (matching.isAbstractDefinition())
		{
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().invalidMessageSendFunction(),
				Arrays.asList(
					E_ABSTRACT_METHOD_DEFINITION.numericCode(),
					method,
					TupleDescriptor.fromList(argsBuffer)),
				true);
			return;
		}
		// Leave the expected return type pushed on the stack.  This will be
		// used when the method returns, and it also helps distinguish label
		// continuations from call continuations.
		push(expectedReturnType);

		// Call the method...
		reifyContinuation();
		interpreter.invokePossiblePrimitiveWithReifiedCaller(
			matching.bodyBlock(),
			pointerAt(CALLER),
			expectedReturnType.isTop());
	}

	@Override
	public void L1_doPushLiteral ()
	{
		final int literalIndex = getInteger();
		final AvailObject constant = literalAt(literalIndex);
		// We don't need to make constant beImmutable because *code objects*
		// are always immutable.
		push(constant);
	}

	@Override
	public void L1_doPushLastLocal ()
	{
		final int localIndex = argumentOrLocalRegister(getInteger());
		final AvailObject local = pointerAt(localIndex);
		pointerAtPut(localIndex, NilDescriptor.nil());
		push(local);
	}

	@Override
	public void L1_doPushLocal ()
	{
		final int localIndex = argumentOrLocalRegister(getInteger());
		final AvailObject local = pointerAt(localIndex);
		local.makeImmutable();
		push(local);
	}

	@Override
	public void L1_doPushLastOuter ()
	{
		final A_Function function = pointerAt(FUNCTION);
		final int outerIndex = getInteger();
		final A_BasicObject outer = function.outerVarAt(outerIndex);
		assert !outer.equalsNil();
		if (!function.optionallyNilOuterVar(outerIndex))
		{
			outer.makeImmutable();
		}
		push(outer);
	}

	@Override
	public void L1_doClose ()
	{
		final int numCopiedVars = getInteger();
		final int literalIndexOfCode = getInteger();
		final AvailObject codeToClose = literalAt(literalIndexOfCode);
		final A_Function newFunction = FunctionDescriptor.createExceptOuters(
			codeToClose,
			numCopiedVars);
		for (int i = numCopiedVars; i >= 1; i--)
		{
			final AvailObject value = pop();
			assert !value.equalsNil();
			newFunction.outerVarAtPut(i, value);
		}
		/*
		 * We don't assert assertObjectUnreachableIfMutable: on the popped
		 * outer variables because each outer variable's new reference from
		 * the function balances the lost reference from the wiped stack.
		 * Likewise we don't tell them makeImmutable(). The function itself
		 * should remain mutable at this point, otherwise the outer
		 * variables would have to makeImmutable() to be referenced by an
		 * immutable function.
		 */
		push(newFunction);
	}

	@Override
	public void L1_doSetLocal ()
	{
		final int localIndex = argumentOrLocalRegister(getInteger());
		final A_Variable localVariable = pointerAt(localIndex);
		final AvailObject value = pop();
		try
		{
			// The value's reference from the stack is now from the variable.
			localVariable.setValueNoCheck(value);
		}
		catch (final VariableSetException e)
		{
			assert e.numericCode().equals(
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED.numericCode());
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().implicitObserveFunction(),
				Arrays.asList(
					Interpreter.assignmentFunction(),
					TupleDescriptor.from(localVariable, value)),
				true);
		}
	}

	@Override
	public void L1_doGetLocalClearing ()
	{
		final int localIndex = argumentOrLocalRegister(getInteger());
		final A_Variable localVariable = pointerAt(localIndex);
		try
		{
			final AvailObject value = localVariable.getValue();
			if (localVariable.traversed().descriptor().isMutable())
			{
				localVariable.clearValue();
			}
			else
			{
				value.makeImmutable();
			}
			push(value);
		}
		catch (final VariableGetException e)
		{
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().unassignedVariableReadFunction(),
				Collections.<A_BasicObject>emptyList(),
				true);
		}
	}

	@Override
	public void L1_doPushOuter ()
	{
		final A_Function function = pointerAt(FUNCTION);
		final int outerIndex = getInteger();
		final A_BasicObject outer = function.outerVarAt(outerIndex);
		assert !outer.equalsNil();
		outer.makeImmutable();
		push(outer);
	}

	@Override
	public void L1_doPop ()
	{
		pop();
	}

	@Override
	public void L1_doGetOuterClearing ()
	{
		final A_Function function = pointerAt(FUNCTION);
		final int outerIndex = getInteger();
		final A_Variable outerVariable = function.outerVarAt(outerIndex);
		try
		{
			final AvailObject value = outerVariable.getValue();
			if (outerVariable.traversed().descriptor().isMutable())
			{
				outerVariable.clearValue();
			}
			else
			{
				value.makeImmutable();
			}
			push(value);
		}
		catch (final VariableGetException e)
		{
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().unassignedVariableReadFunction(),
				Collections.<A_BasicObject>emptyList(),
				true);
		}
	}

	@Override
	public void L1_doSetOuter ()
	{
		final A_Function function = pointerAt(FUNCTION);
		final int outerIndex = getInteger();
		final A_Variable outerVariable = function.outerVarAt(outerIndex);
		assert !outerVariable.equalsNil();
		final AvailObject newValue = pop();
		try
		{
			// The value's reference from the stack is now from the variable.
			outerVariable.setValueNoCheck(newValue);
		}
		catch (final VariableSetException e)
		{
			assert e.numericCode().equals(
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED.numericCode());
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().implicitObserveFunction(),
				Arrays.asList(
					Interpreter.assignmentFunction(),
					TupleDescriptor.from(outerVariable, newValue)),
				true);
		}
	}

	@Override
	public void L1_doGetLocal ()
	{
		final int localIndex = argumentOrLocalRegister(getInteger());
		final A_Variable localVariable = pointerAt(localIndex);
		try
		{
			final AvailObject value = localVariable.getValue();
			value.makeImmutable();
			push(value);
		}
		catch (final VariableGetException e)
		{
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().unassignedVariableReadFunction(),
				Collections.<A_BasicObject>emptyList(),
				true);
		}
	}

	@Override
	public void L1_doMakeTuple ()
	{
		final int count = getInteger();
		final A_Tuple tuple = ObjectTupleDescriptor.createUninitialized(count);
		for (int i = count; i >= 1; i--)
		{
			tuple.objectTupleAtPut(i, pop());
		}
		tuple.hashOrZero(0);
		push(tuple);
	}

	@Override
	public void L1_doGetOuter ()
	{
		final A_Function function = pointerAt(FUNCTION);
		final int outerIndex = getInteger();
		final A_Variable outerVariable = function.outerVarAt(outerIndex);
		try
		{
			final AvailObject outer = outerVariable.getValue();
			outer.makeImmutable();
			push(outer);
		}
		catch (final VariableGetException e)
		{
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().unassignedVariableReadFunction(),
				Collections.<A_BasicObject>emptyList(),
				true);
		}
	}

	@Override
	public void L1_doExtension ()
	{
		final A_Function function = pointerAt(FUNCTION);
		final A_RawFunction code = function.code();
		final A_Tuple nybbles = code.nybbles();
		int pc = integerAt(pcRegister());
		final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
		pc++;
		integerAtPut(pcRegister(), pc);
		L1Operation.all()[nybble + 16].dispatch(this);
	}

	@Override
	public void L1Ext_doPushLabel ()
	{
		final A_Function function = pointerAt(FUNCTION);
		final A_RawFunction code = function.code();
		final int numArgs = code.numArgs();
		assert code.primitiveNumber() == 0;
		final List<AvailObject> args = new ArrayList<>(numArgs);
		for (int i = 1; i <= numArgs; i++)
		{
			args.add(pointerAt(argumentOrLocalRegister(i)));
		}
		final int numLocals = code.numLocals();
		final List<AvailObject> locals =
			new ArrayList<>(numLocals);
		for (int i = 1; i <= numLocals; i++)
		{
			locals.add(pointerAt(argumentOrLocalRegister((numArgs + i))));
		}
		assert interpreter.chunk() == L2Chunk.unoptimizedChunk();
		final A_Continuation newContinuation = ContinuationDescriptor.create(
			function,
			pointerAt(CALLER),
			integerAt(skipReturnCheckRegister()) != 0,
			L2Chunk.unoptimizedChunk(),
			L2Chunk.offsetToContinueUnoptimizedChunk(),
			args,
			locals);
		// Freeze all fields of the new object, including its caller,
		// function, and args.
		newContinuation.makeSubobjectsImmutable();
		// ...always a fresh copy, always mutable (uniquely owned).
		assert newContinuation.caller().equalsNil()
			|| !newContinuation.caller().descriptor().isMutable()
		: "Caller should freeze because two continuations can see it";
		push(newContinuation);
	}

	@Override
	public void L1Ext_doGetLiteral ()
	{
		final int literalIndex = getInteger();
		final A_Variable literalVariable = literalAt(literalIndex);
		// We don't need to make constant beImmutable because *code objects*
		// are always immutable.
		try
		{
			final AvailObject value = literalVariable.getValue();
			value.makeImmutable();
			push(value);
		}
		catch (final VariableGetException e)
		{
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().unassignedVariableReadFunction(),
				Collections.<A_BasicObject>emptyList(),
				true);
		}
	}

	@Override
	public void L1Ext_doSetLiteral ()
	{
		final int literalIndex = getInteger();
		final A_Variable literalVariable = literalAt(literalIndex);
		final AvailObject value = pop();
		try
		{
			// The value's reference from the stack is now from the variable.
			literalVariable.setValueNoCheck(value);
		}
		catch (final VariableSetException e)
		{
			assert e.numericCode().equals(
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED.numericCode());
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().implicitObserveFunction(),
				Arrays.asList(
					Interpreter.assignmentFunction(),
					TupleDescriptor.from(literalVariable, value)),
				true);
		}
	}

	@Override
	public void L1Ext_doDuplicate ()
	{
		final int stackp = integerAt(stackpRegister());
		final AvailObject value = pointerAt(stackp);
		value.makeImmutable();
		push(value);
	}

	@Override
	public void L1Ext_doPermute ()
	{
		final A_Tuple permutation = literalAt(getInteger());
		final int size = permutation.tupleSize();
		final AvailObject[] values = new AvailObject[size];
		final int stackp = integerAt(stackpRegister());
		for (int i = 1; i <= size; i++)
		{
			values[permutation.tupleIntAt(i) - 1] =
				pointerAt(stackp + size - i);
		}
		for (int i = 1; i <= size; i++)
		{
			pointerAtPut(stackp + size - i, values[i - 1]);
		}
	}

	@Override
	public void L1Ext_doGetType ()
	{
		final int stackp = integerAt(stackpRegister());
		final AvailObject value = pointerAt(stackp);
		// It's still on the stack and referenced from the type, so make it
		// immutable.
		value.makeImmutable();
		push(AbstractEnumerationTypeDescriptor.withInstance(value));
	}

	@Override
	public void L1Ext_doMakeTupleAndType ()
	{
		final int count = getInteger();
		final A_Tuple tuple = ObjectTupleDescriptor.createUninitialized(count);
		final A_Type[] types = new A_Type[count];
		for (int i = count; i >= 1; i--)
		{
			types[i - 1] = pop();
			tuple.objectTupleAtPut(i, pop());
		}
		tuple.hashOrZero(0);
		push(tuple);
		push(TupleTypeDescriptor.forTypes(types));
	}

	@Override
	public void L1Ext_doSuperCall ()
	{
		final A_Bundle bundle = literalAt(getInteger());
		final A_Type expectedReturnType = literalAt(getInteger());
		final int numArgs = bundle.bundleMethod().numArgs();
		if (debugL1)
		{
			Interpreter.log(
				interpreter.fiber,
				Level.FINE,
				"L1 supercall {0}",
				bundle.message().atomName());
		}
		argsBuffer.clear();
		final A_Tuple typesTuple =
			ObjectTupleDescriptor.createUninitialized(numArgs);
		for (int i = numArgs; i >= 1; i--)
		{
			typesTuple.objectTupleAtPut(i, pop());
			argsBuffer.add(0, pop());
		}
		typesTuple.hashOrZero(0);


		final A_Method method = bundle.bundleMethod();
		final MutableOrNull<AvailErrorCode> errorCode = new MutableOrNull<>();
		A_Definition matching;
		try
		{
			matching = method.lookupByTypesFromTuple(typesTuple);
		}
		catch (final MethodDefinitionException e)
		{
			errorCode.value = e.errorCode();
			matching = NilDescriptor.nil();
		}
		if (matching.equalsNil())
		{
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().invalidMessageSendFunction(),
				Arrays.asList(
					errorCode.value().numericCode(),
					method,
					TupleDescriptor.fromList(argsBuffer)),
				true);
			return;
		}
		if (matching.isForwardDefinition())
		{
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().invalidMessageSendFunction(),
				Arrays.asList(
					E_FORWARD_METHOD_DEFINITION.numericCode(),
					method,
					TupleDescriptor.fromList(argsBuffer)),
				true);
			return;
		}
		if (matching.isAbstractDefinition())
		{
			reifyContinuation();
			interpreter.invokeFunction(
				interpreter.runtime().invalidMessageSendFunction(),
				Arrays.asList(
					E_ABSTRACT_METHOD_DEFINITION.numericCode(),
					method,
					TupleDescriptor.fromList(argsBuffer)),
				true);
			return;
		}
		// Leave the expected return type pushed on the stack.  This will be
		// used when the method returns, and it also helps distinguish label
		// continuations from call continuations.
		push(expectedReturnType);

		// Call the method...
		reifyContinuation();
		interpreter.invokePossiblePrimitiveWithReifiedCaller(
			matching.bodyBlock(),
			pointerAt(CALLER),
			expectedReturnType.isTop());

	}

	@Override
	public void L1Ext_doReserved ()
	{
		throw new RuntimeException("Nybblecode is not supported");
	}

	@Override
	public void L1Implied_doReturn ()
	{
		final A_Continuation caller = pointerAt(CALLER);
		final AvailObject value = pop();
		final boolean skipCheck = integerAt(skipReturnCheckRegister()) != 0;
		if (skipCheck)
		{
			interpreter.returnToCaller(caller, value, skipCheck);
		}
		else
		{
			final long before = System.nanoTime();
			interpreter.returnToCaller(caller, value, skipCheck);
			final long after = System.nanoTime();
			checkedNonPrimitiveReturn.record(
				after - before, interpreter.interpreterIndex);
		}
	}
}
