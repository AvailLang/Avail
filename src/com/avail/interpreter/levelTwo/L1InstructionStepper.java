/**
 * L1InstructionStepper.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import static com.avail.interpreter.levelOne.L1Operation.*;

import java.util.*;
import java.util.logging.Level;

import com.avail.AvailRuntime;
import com.avail.annotations.InnerAccess;
import com.avail.descriptor.*;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.exceptions.VariableGetException;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.levelTwo.operation.L2_INTERPRET_LEVEL_ONE;
import com.avail.optimizer.ReifyStackThrowable;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.Generator;

/**
 * This class is used to simulate the effect of level one nybblecodes during
 * execution of the {@link L2_INTERPRET_LEVEL_ONE} instruction, on behalf
 * of a {@link Interpreter}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L1InstructionStepper
{
	/**
	 * The {@link Interpreter} on whose behalf to step level one nybblecodes.
	 */
	final Interpreter interpreter;

	/** The current function being executed. */
	A_Function function;

	/** The current raw function bing executed. */
	A_RawFunction code;

	/** The nybblecodes tuple of the current function. */
	A_Tuple nybbles;

	/** The current one-based index into the nybblecodes. */
	public int pc;

	/** The current stack position as would be seen in a continuation. */
	public int stackp;

	/** Statistic for recording checked non-primitive returns from L1. */
	private static final Statistic checkedNonPrimitiveReturn =
		new Statistic(
			"Checked non-primitive return from L1",
			StatisticReport.NON_PRIMITIVE_RETURN_LEVELS);

	/**
	 * Construct a new {@code L1InstructionStepper}.
	 *
	 * @param interpreter
	 *            The {@link Interpreter} on whose behalf to step through
	 *            level one nybblecode instructions.
	 */
	public L1InstructionStepper (final Interpreter interpreter)
	{
		this.interpreter = interpreter;
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
	@Deprecated
	public static int skipReturnCheckRegister ()
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
	@InnerAccess AvailObject pointerAt (final int index)
	{
		return interpreter.pointerAt(index);
	}

	/**
	 * Write to the specified object register.
	 *
	 * @param index Which object register to write.
	 * @param value The value to write to that register.
	 */
	@InnerAccess void pointerAtPut (
		final int index,
		final A_BasicObject value)
	{
		interpreter.pointerAtPut(index, value);
	}

	/**
	 * Extract an integer from nybblecode stream.
	 * @return
	 */
	private int getInteger ()
	{
		final byte firstNybble = nybbles.extractNybbleFromTupleAt(pc++);
		final int shift = firstNybble << 2;
		int count = 0xF & (int) (0x8421_1100_0000_0000L >>> shift);
		int value = 0;
		while (count-- > 0)
		{
			value = (value << 4) + nybbles.extractNybbleFromTupleAt(pc++);
		}
		final int lowOff = 0xF & (int) (0x00AA_AA98_7654_3210L >>> shift);
		final int highOff = 0xF & (int) (0x0032_1000_0000_0000L >>> shift);
		return value + lowOff + (highOff << 4);
	}

	/**
	 * Push a value onto the current virtualized continuation's stack (which
	 * is just some consecutively-numbered pointer registers and an integer
	 * register that maintains the position).
	 *
	 * @param value The value to push on the virtualized stack.
	 */
	private void push (final A_BasicObject value)
	{
		pointerAtPut(--stackp, value);
	}

	/**
	 * Pop a value off the current virtualized continuation's stack (which
	 * is just some consecutively-numbered pointer registers and an integer
	 * register that maintains the position).
	 *
	 * @return The value popped off the virtualized stack.
	 */
	private AvailObject pop ()
	{
		final AvailObject popped = pointerAt(stackp);
		pointerAtPut(stackp++, NilDescriptor.nil());
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
	private AvailObject literalAt (final int literalIndex)
	{
		return code.literalAt(literalIndex);
	}

	/**
	 * Run the current code until it reaches the end.  Individual instructions,
	 * such as calls, may be subject to {@link ReifyStackThrowable}, which
	 * should cause a suitable {@link A_Continuation} to be reified.  In
	 * addition, inter-nybblecode interrupts may also signal reification, but
	 * provide a {@link ReifyStackThrowable#postReificationAction()} that
	 * handles the interrupt.
	 */
	public void run ()
	throws ReifyStackThrowable
	{
		final int nybbleCount = nybbles.tupleSize();
		while (pc <= nybbleCount)
		{
			int nybble = nybbles.extractNybbleFromTupleAt(pc++);
			if (nybble == L1_doExtension.ordinal())
			{
				nybble = nybbles.extractNybbleFromTupleAt(pc++) + 16;
			}
			final L1Operation nybblecode = L1Operation.all()[nybble];
			switch (nybblecode)
			{
				case L1_doCall:
				{
					final A_Bundle bundle = literalAt(getInteger());
					final A_Type expectedReturnType = literalAt(getInteger());
					final int numArgs = bundle.bundleMethod().numArgs();
					if (debugL1)
					{
						log(
							interpreter.fiber(),
							Level.FINE,
							"L1 call {0}",
							bundle.message().atomName());
					}
					interpreter.argsBuffer.clear();
					for (int i = stackp + numArgs - 1; i >= stackp; i--)
					{
						interpreter.argsBuffer.add(interpreter.pointerAt(i));
					}
					stackp += numArgs;
					// Push the expected type, which should be replaced on the
					// stack with the actual value when the call completes
					// (after ensuring it complies).
					push(expectedReturnType);
					final A_Method method = bundle.bundleMethod();
					final A_Definition matching;
					try
					{
						matching = method.lookupByValuesFromList(
							interpreter.argsBuffer);
					}
					catch (final MethodDefinitionException e)
					{
						throw reifyAndReportFailedLookup(method, e.errorCode());
					}

					callMethodAfterLookup(method, matching);

					// The call returned normally, without reifications, with
					// the resulting value in the interpreter's latestResult().
					final AvailObject result = interpreter.latestResult();
					assert result.isInstanceOf(expectedReturnType)
						: "Return value disagrees with expected type";
					assert stackp <= argumentOrLocalRegister(
						function.code().numArgsAndLocalsAndStack());
					// Replace the stack slot.
					pointerAtPut(stackp, result);
					break;
				}
				case L1_doPushLiteral:
				{
					push(literalAt(getInteger()));
					break;
				}
				case L1_doPushLastLocal:
				{
					final int localIndex =
						argumentOrLocalRegister(getInteger());
					final AvailObject local = pointerAt(localIndex);
					pointerAtPut(localIndex, NilDescriptor.nil());
					push(local);
					break;
				}
				case L1_doPushLocal:
				{
					final int localIndex =
						argumentOrLocalRegister(getInteger());
					final AvailObject local = pointerAt(localIndex);
					local.makeImmutable();
					push(local);
					break;
				}
				case L1_doPushLastOuter:
				{
					final int outerIndex = getInteger();
					final A_BasicObject outer = function.outerVarAt(outerIndex);
					assert !outer.equalsNil();
					if (!function.optionallyNilOuterVar(outerIndex))
					{
						outer.makeImmutable();
					}
					push(outer);
					break;
				}
				case L1_doClose:
				{
					final int numCopiedVars = getInteger();
					final AvailObject codeToClose = literalAt(getInteger());
					final A_Function newFunction =
						FunctionDescriptor.createExceptOuters(
							codeToClose, numCopiedVars);
					for (int i = numCopiedVars; i >= 1; i--)
					{
						// We don't assert assertObjectUnreachableIfMutable: on
						// the popped outer variables because each outer
						// variable's new reference from the function balances
						// the lost reference from the wiped stack.  Likewise,
						// we make them be immutable. The function itself should
						// remain mutable at this point, otherwise the outer
						// variables would have to makeImmutable() to be
						// referenced by an immutable function.
						final AvailObject value = pop();
						assert !value.equalsNil();
						newFunction.outerVarAtPut(i, value);
					}
					push(newFunction);
					break;
				}
				case L1_doSetLocal:
				{
					final int localIndex =
						argumentOrLocalRegister(getInteger());
					setVariable(pointerAt(localIndex), pop());
					break;
				}
				case L1_doGetLocalClearing:
				{
					final int localIndex =
						argumentOrLocalRegister(getInteger());
					final A_Variable localVariable = pointerAt(localIndex);
					final AvailObject value = getVariable(localVariable);
					if (localVariable.traversed().descriptor().isMutable())
					{
						localVariable.clearValue();
						push(value);
					}
					else
					{
						push(value.makeImmutable());
					}
					break;
				}
				case L1_doPushOuter:
				{
					final AvailObject outer = function.outerVarAt(getInteger());
					assert !outer.equalsNil();
					outer.makeImmutable();
					push(outer);
					break;
				}
				case L1_doPop:
				{
					pop();
					break;
				}
				case L1_doGetOuterClearing:
				{
					final A_Variable outerVariable =
						function.outerVarAt(getInteger());
					final AvailObject value = getVariable(outerVariable);
					if (outerVariable.traversed().descriptor().isMutable())
					{
						outerVariable.clearValue();
						push(value);
					}
					else
					{
						push(value.makeImmutable());
					}
					break;
				}
				case L1_doSetOuter:
				{
					final A_Variable outerVariable =
						function.outerVarAt(getInteger());
					assert !outerVariable.equalsNil();
					setVariable(outerVariable, pop());
					break;
				}
				case L1_doGetLocal:
				{
					final int localIndex =
						argumentOrLocalRegister(getInteger());
					push(getVariable(pointerAt(localIndex)).makeImmutable());
					break;
				}
				case L1_doMakeTuple:
				{
					final int count = getInteger();
					final A_Tuple tuple =
						ObjectTupleDescriptor.generateReversedFrom(
							count, this::pop);
					push(tuple);
					break;
				}
				case L1_doGetOuter:
				{
					final A_Variable outerVariable =
						function.outerVarAt(getInteger());
					push(getVariable(outerVariable).makeImmutable());
					break;
				}
				case L1_doExtension:
				{
					assert false : "Illegal dispatch nybblecode";
					break;
				}
				case L1Ext_doPushLabel:
				{
					final int numArgs = code.numArgs();
					assert code.primitiveNumber() == 0;
					final List<AvailObject> args = new ArrayList<>(numArgs);
					for (int i = 1; i <= numArgs; i++)
					{
						args.add(pointerAt(argumentOrLocalRegister(i)));
					}
					final int numLocals = code.numLocals();
					final List<AvailObject> locals = new ArrayList<>(numLocals);
					for (int i = 1; i <= numLocals; i++)
					{
						locals.add(
							pointerAt(argumentOrLocalRegister(numArgs + i)));
					}
					assert interpreter.chunk == L2Chunk.unoptimizedChunk();

					final A_Function savedFunction = interpreter.function;
					final int savedOffset = interpreter.offset;
					final AvailObject[] savedPointers = interpreter.pointers;
					final int[] savedInts = interpreter.integers;
					final boolean savedSkip = interpreter.skipReturnCheck;
					final int savedPc = pc;
					final int savedStackp = stackp;

					throw interpreter.reifyThen(
						() ->
						{
							// The Java stack has been reified into Avail
							// continuations.  Run this before continuing the L2
							// interpreter.
							interpreter.function = savedFunction;
							interpreter.offset = savedOffset;
							interpreter.pointers = savedPointers;
							interpreter.integers = savedInts;
							interpreter.skipReturnCheck = savedSkip;
							pc = savedPc;
							stackp = savedStackp;

							final A_Continuation newContinuation =
								ContinuationDescriptor.create(
									function,
									interpreter.reifiedContinuation,
									savedSkip,
									L2Chunk.unoptimizedChunk(),
									L2Chunk.offsetToReturnIntoUnoptimizedChunk(),
									args,
									locals);

							// Freeze all fields of the new object, including
							// its caller, function, and args.
							newContinuation.makeSubobjectsImmutable();
							// ...always a fresh copy, always mutable (uniquely
							// owned).
							assert newContinuation.caller().equalsNil()
								|| !newContinuation.caller().descriptor()
									.isMutable()
								: "Caller should freeze because two "
									+ "continuations can see it";
							push(newContinuation);
							// ...and continue running the chunk.
						});
					// break;
				}
				case L1Ext_doGetLiteral:
				{
					push(getVariable(literalAt(getInteger())).makeImmutable());
					break;
				}
				case L1Ext_doSetLiteral:
				{
					setVariable(literalAt(getInteger()), pop());
					break;
				}
				case L1Ext_doDuplicate:
				{
					push(pointerAt(stackp).makeImmutable());
					break;
				}
				case L1Ext_doPermute:
				{
					final A_Tuple permutation = literalAt(getInteger());
					final int size = permutation.tupleSize();
					final AvailObject[] values = new AvailObject[size];
					for (int i = 1; i <= size; i++)
					{
						values[permutation.tupleIntAt(i) - 1] =
							pointerAt(stackp + size - i);
					}
					for (int i = 1; i <= size; i++)
					{
						pointerAtPut(stackp + size - i, values[i - 1]);
					}
					break;
				}
				case L1Ext_doSuperCall:
				{
					final A_Bundle bundle = literalAt(getInteger());
					final A_Type expectedReturnType = literalAt(getInteger());
					final A_Type superUnionType = literalAt(getInteger());
					final int numArgs = bundle.bundleMethod().numArgs();
					if (debugL1)
					{
						log(
							interpreter.fiber(),
							Level.FINE,
							"L1 supercall {0}",
							bundle.message().atomName());
					}
					interpreter.argsBuffer.clear();
					final A_Tuple typesTuple =
						ObjectTupleDescriptor.generateFrom(
							numArgs,
							new Generator<A_BasicObject>()
							{
								int reversedStackp = stackp + numArgs;

								int tupleIndex = 1;

								@Override
								public A_BasicObject value ()
								{
									final AvailObject arg =
										pointerAt(--reversedStackp);
									interpreter.argsBuffer.add(arg);
									return AbstractEnumerationTypeDescriptor
										.withInstance(arg)
										.typeUnion(
											superUnionType.typeAtIndex(
												tupleIndex++));
								}
							});
					stackp += numArgs;
					// Push the expected return type.  This will be used when
					// the method returns, and it also helps distinguish label
					// continuations from call continuations.
					push(expectedReturnType);
					final A_Method method = bundle.bundleMethod();
					final A_Definition matching;
					try
					{
						matching = method.lookupByTypesFromTuple(typesTuple);
					}
					catch (final MethodDefinitionException e)
					{
						throw reifyAndReportFailedLookup(method, e.errorCode());
					}

					callMethodAfterLookup(method, matching);

					// The call returned normally, without reifications, with
					// the resulting value in the interpreter's latestResult().
					final AvailObject result = interpreter.latestResult();
					assert result.isInstanceOf(expectedReturnType)
						: "Return value disagrees with expected type";
					assert stackp <= argumentOrLocalRegister(
						function.code().numArgsAndLocalsAndStack());
					// Replace the stack slot.
					pointerAtPut(stackp, result);
					break;
				}
				default:
				{
					assert false : "Illegal dispatch nybblecode";
				}
			}
		}
		// It ran off the end of the nybblecodes, which is how a function
		// returns in Level One.  Capture the result and return to the Java
		// caller.
		interpreter.latestResult(pop());
	}

	/**
	 * Get the value from the given variable, reifying and invoking the {@link
	 * AvailRuntime#unassignedVariableReadFunction()} if the variable has no
	 * value.
	 *
	 * @param variable
	 *        The variable to read.
	 * @return The value of the variable, if it's assigned.
	 * @throws ReifyStackThrowable
	 *         If the variable was unassigned.
	 */
	private AvailObject getVariable (final A_Variable variable)
	throws ReifyStackThrowable
	{
		try
		{
			return variable.getValue();
		}
		catch (final VariableGetException e)
		{
			throw interpreter.reifyThenCall0(
				interpreter.runtime().unassignedVariableReadFunction(),
				true);
		}
	}

	/**
	 * Set a variable, triggering reification and invocation of the {@link
	 * AvailRuntime#implicitObserveFunction()} if necessary.
	 *
	 * @param variable
	 *        The variable to update.
	 * @param value
	 *        The type-safe value to write to the variable.
	 * @throws ReifyStackThrowable
	 *         If an observed variable is assigned while tracing is off.
	 */
	private void setVariable (
		final A_Variable variable,
		final AvailObject value)
	throws ReifyStackThrowable
	{
		try
		{
			// The value's reference from the stack is now from the
			// variable.
			variable.setValueNoCheck(value);
		}
		catch (final VariableSetException e)
		{
			assert e.numericCode().equals(
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED.numericCode());
			throw interpreter.reifyThenCall2(
				interpreter.runtime().implicitObserveFunction(),
				true,
				assignmentFunction(),
				TupleDescriptor.from(variable, value));
		}
	}

	private void callMethodAfterLookup (
		final A_Method method,
		final A_Definition matching)
	throws ReifyStackThrowable
	{
		if (matching.isForwardDefinition())
		{
			throw reifyAndReportFailedLookup(
				method, E_FORWARD_METHOD_DEFINITION);
		}
		if (matching.isAbstractDefinition())
		{
			throw reifyAndReportFailedLookup(
				method, E_ABSTRACT_METHOD_DEFINITION);
		}
		// At this point, the frame information is still the same,
		// but we've set up argsBuffer.
		interpreter.skipReturnCheck = false;

		final A_Function savedFunction = interpreter.function;
		final int savedOffset = interpreter.offset;
		final AvailObject[] savedPointers = interpreter.pointers;
		final int[] savedInts = interpreter.integers;
		final int savedPc = pc;
		final int savedStackp = stackp;

		final A_Function functionToInvoke = matching.bodyBlock();
		try
		{
			interpreter.invokeFunction(functionToInvoke);
		}
		catch (final ReifyStackThrowable reifier)
		{
			// At some point during the call, reification was
			// requested.  Add this frame and rethrow.
			final L2Chunk chunk = interpreter.chunk;
			assert chunk == L2Chunk.unoptimizedChunk();
			interpreter.restorePointers(savedPointers);
			interpreter.restoreInts(savedInts);
			final A_Continuation continuation =
				ContinuationDescriptor.createExceptFrame(
					functionToInvoke,
					NilDescriptor.nil(),
					savedPc,
					savedStackp + 1 - argumentOrLocalRegister(1),
					false,
					chunk,
					L2Chunk.offsetToReturnIntoUnoptimizedChunk());
			for (
				int i = code.numArgsAndLocalsAndStack();
				i >= 1;
				i--)
			{
				continuation.argOrLocalOrStackAtPut(
					i,
					pointerAt(argumentOrLocalRegister(i)));
			}
			reifier.pushContinuation(continuation);
			throw reifier;
		}
		finally
		{
			interpreter.integers = savedInts;
			interpreter.pointers = savedPointers;
			interpreter.offset = savedOffset;
			interpreter.function = savedFunction;
			pc = savedPc;
			stackp = savedStackp;
		}
	}

	/**
	 * Throw a {@link ReifyStackThrowable} to reify the Java stack into {@link
	 * A_Continuation}s, then invoke the {@link
	 * AvailRuntime#invalidMessageSendFunction()} with appropriate arguments.
	 * An {@link AvailErrorCode} is also provided to indicate what the lookup
	 * problem was.
	 *
	 * @param method
	 *        The method that failed lookup.
	 * @param errorCode
	 *        The {@link AvailErrorCode} indicating the lookup problem.
	 * @return Pretends to return the exception, so callers can pretend to
	 *         throw it, to help the compiler figure out it never returns.
	 *         Yuck.  But if exceptions (and nulls, and generics, etc) were
	 *         integrated into type signatures in a sane way, there'd be that
	 *         many less reasons for Avail.
	 * @throws ReifyStackThrowable Always, to cause reification.
	 */
	private ReifyStackThrowable reifyAndReportFailedLookup (
		final A_Method method,
		final AvailErrorCode errorCode)
	throws ReifyStackThrowable
	{
		throw interpreter.reifyThenCall3(
			interpreter.runtime().invalidMessageSendFunction(),
			true,
			errorCode.numericCode(),
			method,
			TupleDescriptor.fromList(interpreter.argsBuffer));
	}
}
