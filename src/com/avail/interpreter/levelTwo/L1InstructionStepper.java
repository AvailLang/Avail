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

import com.avail.AvailRuntime;
import com.avail.annotations.InnerAccess;
import com.avail.descriptor.*;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.exceptions.VariableGetException;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.operation.L2_INTERPRET_LEVEL_ONE;
import com.avail.optimizer.ReifyStackThrowable;
import com.avail.utility.IndexedGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.instanceTypeOrMetaOn;
import static com.avail.descriptor.ContinuationDescriptor
	.createContinuationExceptFrame;
import static com.avail.descriptor.ContinuationDescriptor
	.createLabelContinuation;
import static com.avail.descriptor.FunctionDescriptor.createExceptOuters;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor
	.generateObjectTupleFrom;
import static com.avail.descriptor.ObjectTupleDescriptor.generateReversedFrom;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleDescriptor.tupleFromList;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Interpreter.assignmentFunction;
import static com.avail.interpreter.Interpreter.debugL1;
import static com.avail.interpreter.levelOne.L1Operation.L1_doExtension;
import static com.avail.interpreter.levelTwo.L2Chunk.*;
import static com.avail.utility.Nulls.stripNull;

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

	/** The nybblecodes tuple of the current function. */
	private A_Tuple nybbles = nil;

	/** The current one-based index into the nybblecodes. */
	public int pc;

	/** The current stack position as would be seen in a continuation. */
	public int stackp;

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
		interpreter.pointerAtPut(--stackp, value);
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
		interpreter.pointerAtPut(stackp++, nil);
		return popped;
	}

	/**
	 * A pre-compilable regex that matches one or more whitespace characters.
	 */
	private static final Pattern whitespaces = Pattern.compile("\\s+");

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
		final A_Function function = stripNull(interpreter.function);
		final A_RawFunction code = function.code();
		final AvailObject[] initialPointers = interpreter.pointers;
		nybbles = code.nybbles();
		if (debugL1)
		{
			System.out.println(
				"Started L1 run: "
				+ whitespaces.matcher(function.toString()).replaceAll(" "));
		}
		final int nybbleCount = nybbles.tupleSize();
		while (pc <= nybbleCount)
		{
			assert interpreter.pointers == initialPointers;
			int nybble = nybbles.extractNybbleFromTupleAt(pc++);
			if (nybble == L1_doExtension.ordinal())
			{
				nybble = nybbles.extractNybbleFromTupleAt(pc++) + 16;
			}
			final L1Operation nybblecode = L1Operation.all()[nybble];
			if (debugL1)
			{
				final int savePc = pc;
				final List<Integer> operands =
					Arrays.stream(nybblecode.operandTypes())
						.map(x -> getInteger())
						.collect(Collectors.toList());
				System.out.println(
					"L1 step: "
						+ (operands.isEmpty()
							? nybblecode
							: nybblecode + " " + operands));
				pc = savePc;
			}
			switch (nybblecode)
			{
				case L1_doCall:
				{
					final A_Bundle bundle = code.literalAt(getInteger());
					final A_Type expectedReturnType =
						code.literalAt(getInteger());
					final int numArgs = bundle.bundleMethod().numArgs();
					if (debugL1)
					{
						System.out.println(
							"         (" + bundle.message().atomName() + ")");
					}
					interpreter.argsBuffer.clear();
					for (int i = stackp + numArgs - 1; i >= stackp; i--)
					{
						interpreter.argsBuffer.add(interpreter.pointerAt(i));
						interpreter.pointerAtPut(i, nil);
					}
					stackp += numArgs;
					// Push the expected type, which should be replaced on the
					// stack with the actual value when the call completes
					// (after ensuring it complies).
					push(expectedReturnType);
					final A_Method method = bundle.bundleMethod();
					final A_Definition matching;
					final long beforeLookup = System.nanoTime();
					try
					{
						matching = method.lookupByValuesFromList(
							interpreter.argsBuffer);
					}
					catch (final MethodDefinitionException e)
					{
						throw reifyAndReportFailedLookup(method, e.errorCode());
					}
					finally
					{
						final long afterLookup = System.nanoTime();
						bundle.dynamicLookupStatistic().record(
							afterLookup - beforeLookup,
							interpreter.interpreterIndex);
					}

					callMethodAfterLookup(method, matching);

					// The call returned normally, without reifications, with
					// the resulting value in the interpreter's latestResult().
					final AvailObject result = interpreter.latestResult();
					if (debugL1)
					{
						System.out.println("Call returned: " + result);
					}
					if (!interpreter.skipReturnCheck)
					{
						interpreter.checkReturnType(
							result, expectedReturnType, function);
					}
					assert stackp <= code.numArgsAndLocalsAndStack();
					// Replace the stack slot.
					pointerAtPut(stackp, result);
					break;
				}
				case L1_doPushLiteral:
				{
					push(code.literalAt(getInteger()));
					break;
				}
				case L1_doPushLastLocal:
				{
					final int localIndex = getInteger();
					final AvailObject local = pointerAt(localIndex);
					assert !local.equalsNil();
					pointerAtPut(localIndex, nil);
					push(local);
					break;
				}
				case L1_doPushLocal:
				{
					final AvailObject local = pointerAt(getInteger());
					assert !local.equalsNil();
					push(local.makeImmutable());
					break;
				}
				case L1_doPushLastOuter:
				{
					final int outerIndex = getInteger();
					final A_BasicObject outer = function.outerVarAt(outerIndex);
					assert !outer.equalsNil();
					if (function.optionallyNilOuterVar(outerIndex))
					{
						push(outer);
					}
					else
					{
						push(outer.makeImmutable());
					}
					break;
				}
				case L1_doClose:
				{
					final int numCopiedVars = getInteger();
					final AvailObject codeToClose =
						code.literalAt(getInteger());
					final A_Function newFunction =
						createExceptOuters(codeToClose, numCopiedVars);
					for (int i = numCopiedVars; i >= 1; i--)
					{
						// We don't assert assertObjectUnreachableIfMutable: on
						// the popped outer variables because each outer
						// variable's new reference from the function balances
						// the lost reference from the continuation's stack.
						// Likewise, we make them be immutable. The function
						// itself should remain mutable at this point, otherwise
						// the outer variables would have to makeImmutable() to
						// be referenced by an immutable function.
						final AvailObject value = pop();
						assert !value.equalsNil();
						newFunction.outerVarAtPut(i, value);
					}
					push(newFunction);
					break;
				}
				case L1_doSetLocal:
				{
					setVariable(pointerAt(getInteger()), pop());
					break;
				}
				case L1_doGetLocalClearing:
				{
					final A_Variable localVariable = pointerAt(getInteger());
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
					push(outer.makeImmutable());
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
					setVariable(function.outerVarAt(getInteger()), pop());
					break;
				}
				case L1_doGetLocal:
				{
					push(getVariable(pointerAt(getInteger())).makeImmutable());
					break;
				}
				case L1_doMakeTuple:
				{
					push(generateReversedFrom(getInteger(), ignored -> pop()));
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
					assert code.primitive() == null;
					final List<AvailObject> args = new ArrayList<>(numArgs);
					for (int i = 1; i <= numArgs; i++)
					{
						final AvailObject arg = pointerAt(i);
						assert !arg.equalsNil();
						args.add(arg);
					}
					assert interpreter.chunk == unoptimizedChunk();

					final A_Function savedFunction =
						stripNull(interpreter.function);
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
							interpreter.chunk = unoptimizedChunk();
							interpreter.offset =
								offsetToReenterAfterReification();
							interpreter.pointers = savedPointers;
							interpreter.integers = savedInts;
							interpreter.skipReturnCheck = savedSkip;
							pc = savedPc;
							stackp = savedStackp;

							// Note that the locals are not present in the new
							// continuation, just arguments.  The locals will be
							// created by offsetToRestartUnoptimizedChunk()
							// when the continuation is restarted.
							final A_Continuation newContinuation =
								createLabelContinuation(
									savedFunction,
									interpreter.reifiedContinuation,
									savedSkip,
									unoptimizedChunk(),
									offsetToRestartUnoptimizedChunk(),
									args);

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
							interpreter.returnNow = false;
							// ...and continue running the chunk.
						});
					// break;
				}
				case L1Ext_doGetLiteral:
				{
					push(
						getVariable(
							code.literalAt(getInteger())).makeImmutable());
					break;
				}
				case L1Ext_doSetLiteral:
				{
					setVariable(code.literalAt(getInteger()), pop());
					break;
				}
				case L1Ext_doDuplicate:
				{
					push(pointerAt(stackp).makeImmutable());
					break;
				}
				case L1Ext_doPermute:
				{
					final A_Tuple permutation = code.literalAt(getInteger());
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
					final A_Bundle bundle = code.literalAt(getInteger());
					final A_Type expectedReturnType =
						code.literalAt(getInteger());
					final A_Type superUnionType = code.literalAt(getInteger());
					final int numArgs = bundle.bundleMethod().numArgs();
					if (debugL1)
					{
						System.out.println(
							"L1 supercall: " + bundle.message().atomName());
					}
					interpreter.argsBuffer.clear();
					final A_Tuple typesTuple =
						generateObjectTupleFrom(
							numArgs,
							new IndexedGenerator<A_BasicObject>()
							{
								int reversedStackp = stackp + numArgs;

								@Override
								public A_BasicObject value (final int index)
								{
									final AvailObject arg =
										pointerAt(--reversedStackp);
									interpreter.argsBuffer.add(arg);
									return instanceTypeOrMetaOn(arg).typeUnion(
										superUnionType.typeAtIndex(index));
								}
							});
					stackp += numArgs;
					// Push the expected type, which should be replaced on the
					// stack with the actual value when the call completes
					// (after ensuring it complies).
					push(expectedReturnType);
					final A_Method method = bundle.bundleMethod();
					final A_Definition matching;
					final long beforeLookup = System.nanoTime();
					try
					{
						matching = method.lookupByTypesFromTuple(typesTuple);
					}
					catch (final MethodDefinitionException e)
					{
						throw reifyAndReportFailedLookup(method, e.errorCode());
					}
					finally
					{
						final long afterLookup = System.nanoTime();
						bundle.dynamicLookupStatistic().record(
							afterLookup - beforeLookup,
							interpreter.interpreterIndex);
					}

					callMethodAfterLookup(method, matching);

					// The call returned normally, without reifications, with
					// the resulting value in the interpreter's latestResult().
					final AvailObject result = interpreter.latestResult();
					if (debugL1)
					{
						System.out.println("Call returned: " + result);
					}
					if (!interpreter.skipReturnCheck)
					{
						interpreter.checkReturnType(
							result, expectedReturnType, function);
					}
					assert stackp <= code.numArgsAndLocalsAndStack();
					// Replace the stack slot.
					pointerAtPut(stackp, result);
					break;
				}
				default:
				{
					assert false : "Illegal dispatch nybblecode";
				}
			}
			// Make sure an instruction didn't switch contexts by accident.
			assert initialPointers == interpreter.pointers;
		}
		// It ran off the end of the nybblecodes, which is how a function
		// returns in Level One.  Capture the result and return to the Java
		// caller.
		interpreter.latestResult(pop());
		assert stackp == interpreter.pointers.length;
		interpreter.returnNow = true;
		interpreter.returningFunction = function;
		if (debugL1)
		{
			System.out.println("L1 return");
		}
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
			//TODO MvG - Probably not right.  If the handler function is allowed
			// to "return" a value in place of the variable read, the reified
			// continuation should push the expected type before calling the
			// handler.  We can probably avoid or postpone reification as well.
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
			// The value's reference from the stack is now from the variable.
			variable.setValueNoCheck(value);
		}
		catch (final VariableSetException e)
		{
			assert e.numericCode().equals(
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED.numericCode());
			// Capture the state in a caller-less continuation.  We'll fix up
			// the caller after reification, below.
			final A_Function function = stripNull(interpreter.function);
			final A_RawFunction code = function.code();
			final A_Continuation continuation =
				createContinuationExceptFrame(
					function,
					nil,
					pc,   // Right after the set-variable instruction.
					stackp,
					false,
					unoptimizedChunk(),
					offsetToResumeInterruptedUnoptimizedChunk());
			for (
				int i = code.numArgsAndLocalsAndStack();
				i >= 1;
				i--)
			{
				continuation.argOrLocalOrStackAtPut(i, pointerAt(i));
			}
			throw interpreter.reifyThen(() ->
			{
				// Push the continuation from above onto the reified stack.
				interpreter.reifiedContinuation = continuation.replacingCaller(
					interpreter.reifiedContinuation);
				interpreter.argsBuffer.clear();
				interpreter.argsBuffer.add((AvailObject) assignmentFunction());
				interpreter.argsBuffer.add(
					(AvailObject) tuple(variable, value));
				interpreter.skipReturnCheck = true;
				interpreter.function =
					interpreter.runtime().implicitObserveFunction();
				interpreter.chunk = code.startingChunk();
				interpreter.offset = 0;
			});
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

		final A_Function savedFunction = stripNull(interpreter.function);
		assert interpreter.chunk == unoptimizedChunk();
		final int savedOffset = interpreter.offset;
		final AvailObject[] savedPointers = interpreter.pointers;
		final int[] savedInts = interpreter.integers;
		final A_Tuple savedNybbles = nybbles;
		final int savedPc = pc;
		final int savedStackp = stackp;

		final A_Function functionToInvoke = matching.bodyBlock();
		try
		{
			interpreter.invokeFunction(functionToInvoke);
		}
		catch (final ReifyStackThrowable reifier)
		{
			if (reifier.actuallyReify())
			{
				// At some point during the call, reification was
				// requested.  Add this frame and rethrow.
				interpreter.restorePointers(savedPointers);
				final A_Continuation continuation =
					createContinuationExceptFrame(
						savedFunction,
						nil,
						savedPc,
						savedStackp,
						false,
						unoptimizedChunk(),
						offsetToReturnIntoUnoptimizedChunk());
				for (
					int i = savedFunction.code().numArgsAndLocalsAndStack();
					i >= 1;
					i--)
				{
					continuation.argOrLocalOrStackAtPut(i, pointerAt(i));
				}
				reifier.pushContinuation(continuation);
			}
			throw reifier;
		}
		finally
		{
			interpreter.integers = savedInts;
			interpreter.pointers = savedPointers;
			interpreter.offset = savedOffset;
			interpreter.function = savedFunction;
			nybbles = savedNybbles;
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
			tupleFromList(interpreter.argsBuffer));
	}
}
