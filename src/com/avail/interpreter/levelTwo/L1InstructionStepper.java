/*
 * L1InstructionStepper.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
import com.avail.descriptor.*;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.exceptions.VariableGetException;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.operation.L2_INTERPRET_LEVEL_ONE;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.IndexedGenerator;
import com.avail.utility.MutableInt;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn;
import static com.avail.descriptor.ContinuationDescriptor.createContinuationExceptFrame;
import static com.avail.descriptor.ContinuationDescriptor.createLabelContinuation;
import static com.avail.descriptor.FunctionDescriptor.createExceptOuters;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.generateObjectTupleFrom;
import static com.avail.descriptor.ObjectTupleDescriptor.generateReversedFrom;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleDescriptor.tupleFromList;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Interpreter.assignmentFunction;
import static com.avail.interpreter.Interpreter.debugL1;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.*;
import static com.avail.interpreter.levelTwo.L2Chunk.unoptimizedChunk;
import static com.avail.utility.Nulls.stripNull;

/**
 * This class is used to simulate the effect of level one nybblecodes during
 * execution of the {@link L2_INTERPRET_LEVEL_ONE} instruction, on behalf
 * of an {@link Interpreter}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L1InstructionStepper
{
	/**
	 * The {@link Interpreter} on whose behalf to step level one nybblecodes.
	 */
	final Interpreter interpreter;

	/** The current one-based index into the nybblecodes. */
	public final MutableInt pc = new MutableInt(-999);

	/** The current stack position as would be seen in a continuation. */
	public int stackp;

	/** The {@link Statistic} for reifications prior to label creation in L1. */
	private static final Statistic reificationBeforeLabelCreationStat =
		new Statistic(
			"Reification before label creation in L1",
			StatisticReport.REIFICATIONS);

	/** The {@link Statistic} for reifications prior to label creation in L1. */
	private static final Statistic reificationForFailedVariableGetStat =
		new Statistic(
			"Reification for failed variable-get in L1",
			StatisticReport.REIFICATIONS);

	/** The {@link Statistic} for reifications prior to label creation in L1. */
	private static final Statistic reificationForFailedLookupStat =
		new Statistic(
			"Reification before failed lookup in L1",
			StatisticReport.REIFICATIONS);

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

	/** An empty array used for clearing the pointers quickly. */
	public static final AvailObject[] emptyPointersArray = new AvailObject[0];

	/**
	 * The registers that hold {@linkplain AvailObject Avail objects}.
	 */
	public AvailObject[] pointers = emptyPointersArray;

	/**
	 * Read from the specified object register.
	 *
	 * @param index Which object register to read.
	 * @return The value from that register.
	 */
	public AvailObject pointerAt (final int index)
	{
		return pointers[index];
	}

	/**
	 * Write to the specified object register.
	 *
	 * @param index Which object register to write.
	 * @param value The value to write to that register.
	 */
	public void pointerAtPut (
		final int index,
		final A_BasicObject value)
	{
		pointers[index] = (AvailObject) value;
	}

	/**
	 * Wipe out the existing register set for safety.
	 */
	public void wipeRegisters ()
	{
		pointers = emptyPointersArray;
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
		pointerAtPut(stackp++, nil);
		return popped;
	}

	/**
	 * A pre-compilable regex that matches one or more whitespace characters.
	 */
	private static final Pattern whitespaces = Pattern.compile("\\s+");

	/**
	 * Run the current code until it reaches the end.  Individual instructions,
	 * such as calls, may be subject to {@link StackReifier reification}, which
	 * should cause a suitable {@link A_Continuation} to be reified.  In
	 * addition, inter-nybblecode interrupts may also trigger reification, but
	 * they'll handle their own reification prior to returning here with a
	 * suitable {@link StackReifier} (to update and return again from here).
	 *
	 * @return {@code null} if the current function returns normally, otherwise
	 *         a {@link StackReifier} with which to reify the stack.
	 */
	@ReferencedInGeneratedCode
	public @Nullable StackReifier run ()
	{
		final A_Function function = stripNull(interpreter.function);
		final A_RawFunction code = function.code();
		if (debugL1)
		{
			Interpreter.log(
				Interpreter.loggerDebugL1,
				Level.FINER,
				"{0}Started L1 run: {1}",
				interpreter.debugModeString,
				whitespaces.matcher(function.toString()).replaceAll(" "));
		}
		final int nybbleCount = code.numNybbles();
		while (pc.value <= nybbleCount)
		{
			final L1Operation operation = code.nextNybblecodeOperation(pc);
			if (debugL1)
			{
				final int savePc = pc.value;
				final List<Integer> operands =
					Arrays.stream(operation.operandTypes())
						.map(x -> code.nextNybblecodeOperand(pc))
						.collect(Collectors.toList());
				Interpreter.log(
					Interpreter.loggerDebugL1,
					Level.FINER,
					"{0}L1 step: {1}",
					interpreter.debugModeString,
					operands.isEmpty()
						? operation
						: operation + " " + operands);
				pc.value = savePc;
			}
			switch (operation)
			{
				case L1_doCall:
				{
					final A_Bundle bundle =
						code.literalAt(code.nextNybblecodeOperand(pc));
					final A_Type expectedReturnType =
						code.literalAt(code.nextNybblecodeOperand(pc));
					final int numArgs = bundle.bundleMethod().numArgs();
					if (debugL1)
					{
						Interpreter.log(
							Interpreter.loggerDebugL1,
							Level.FINER,
							"{0}         L1 call ({1})",
							interpreter.debugModeString,
							bundle.message().atomName());
					}
					interpreter.argsBuffer.clear();
					for (int i = stackp + numArgs - 1; i >= stackp; i--)
					{
						interpreter.argsBuffer.add(pointerAt(i));
						pointerAtPut(i, nil);
					}
					stackp += numArgs;
					// Push the expected type, which should be replaced on the
					// stack with the actual value when the call completes
					// (after ensuring it complies).
					push(expectedReturnType);
					final A_Method method = bundle.bundleMethod();
					final A_Definition matching;
					final long beforeLookup = AvailRuntime.captureNanos();
					try
					{
						matching = method.lookupByValuesFromList(
							interpreter.argsBuffer);
					}
					catch (final MethodDefinitionException e)
					{
						return reifyAndReportFailedLookup(
							method, e.errorCode());
					}
					finally
					{
						final long afterLookup = AvailRuntime.captureNanos();
						interpreter.recordDynamicLookup(
							bundle, afterLookup - beforeLookup);
					}

					final @Nullable StackReifier reifier =
						callMethodAfterLookup(matching, expectedReturnType);
					if (reifier != null)
					{
						return reifier;
					}

					// The call returned normally, without reifications, with
					// the resulting value in the interpreter's latestResult().
					final AvailObject result = interpreter.latestResult();
					if (debugL1)
					{
						Interpreter.log(
							Interpreter.loggerDebugL1,
							Level.FINER,
							"{0}Call returned: {1}",
							interpreter.debugModeString,
							result.typeTag().name());
					}
					final @Nullable StackReifier returnCheckReifier =
						interpreter.checkReturnType(
							result, expectedReturnType, function);
					if (returnCheckReifier != null)
					{
						// Reification is happening within the handling of
						// the failed return type check.
						return returnCheckReifier;
					}
					// The return check passed.  Fall through.
					assert stackp <= code.numSlots();
					// Replace the stack slot.
					pointerAtPut(stackp, result);
					break;
				}
				case L1_doPushLiteral:
				{
					push(code.literalAt(code.nextNybblecodeOperand(pc)));
					break;
				}
				case L1_doPushLastLocal:
				{
					final int localIndex = code.nextNybblecodeOperand(pc);
					final AvailObject local = pointerAt(localIndex);
					assert !local.equalsNil();
					pointerAtPut(localIndex, nil);
					push(local);
					break;
				}
				case L1_doPushLocal:
				{
					final AvailObject local =
						pointerAt(code.nextNybblecodeOperand(pc));
					assert !local.equalsNil();
					push(local.makeImmutable());
					break;
				}
				case L1_doPushLastOuter:
				{
					final int outerIndex = code.nextNybblecodeOperand(pc);
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
					final int numCopiedVars = code.nextNybblecodeOperand(pc);
					final AvailObject codeToClose =
						code.literalAt(code.nextNybblecodeOperand(pc));
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
					final @Nullable StackReifier reifier =
						setVariable(
							pointerAt(code.nextNybblecodeOperand(pc)), pop());
					if (reifier != null)
					{
						return reifier;
					}
					break;
				}
				case L1_doGetLocalClearing:
				{
					final A_Variable localVariable =
						pointerAt(code.nextNybblecodeOperand(pc));
					final Object valueOrReifier = getVariable(localVariable);
					if (valueOrReifier instanceof StackReifier)
					{
						return (StackReifier) valueOrReifier;
					}
					final AvailObject value = (AvailObject) valueOrReifier;
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
					final AvailObject outer =
						function.outerVarAt(code.nextNybblecodeOperand(pc));
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
						function.outerVarAt(code.nextNybblecodeOperand(pc));
					final Object valueOrReifier = getVariable(outerVariable);
					if (valueOrReifier instanceof StackReifier)
					{
						return (StackReifier) valueOrReifier;
					}
					final AvailObject value = (AvailObject) valueOrReifier;
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
					final @Nullable StackReifier reifier =
						setVariable(
							function.outerVarAt(code.nextNybblecodeOperand(pc)),
							pop());
					if (reifier != null)
					{
						return reifier;
					}
					break;
				}
				case L1_doGetLocal:
				{
					final Object valueOrReifier =
						getVariable(pointerAt(code.nextNybblecodeOperand(pc)));
					if (valueOrReifier instanceof StackReifier)
					{
						return (StackReifier) valueOrReifier;
					}
					final AvailObject value = (AvailObject) valueOrReifier;
					push(value.makeImmutable());
					break;
				}
				case L1_doMakeTuple:
				{
					push(
						generateReversedFrom(
							code.nextNybblecodeOperand(pc), ignored -> pop()));
					break;
				}
				case L1_doGetOuter:
				{
					final Object valueOrReifier =
						getVariable(function.outerVarAt(
							code.nextNybblecodeOperand(pc)));
					if (valueOrReifier instanceof StackReifier)
					{
						return (StackReifier) valueOrReifier;
					}
					final AvailObject value = (AvailObject) valueOrReifier;
					push(value.makeImmutable());
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
					assert interpreter.chunk == unoptimizedChunk;

					final A_Function savedFunction =
						stripNull(interpreter.function);
					final AvailObject[] savedPointers = pointers;
					final int savedPc = pc.value;
					final int savedStackp = stackp;

					return interpreter.reifyThen(
						reificationBeforeLabelCreationStat,
						() ->
						{
							// The Java stack has been reified into Avail
							// continuations.  Run this before continuing the L2
							// interpreter.
							interpreter.function = savedFunction;
							interpreter.chunk = unoptimizedChunk;
							interpreter.offset =
								AFTER_REIFICATION.offsetInDefaultChunk;
							pointers = savedPointers;
							pc.value = savedPc;
							stackp = savedStackp;

							// Note that the locals are not present in the new
							// continuation, just arguments.  The locals will be
							// created by offsetToRestartUnoptimizedChunk()
							// when the continuation is restarted.
							final A_Continuation newContinuation =
								createLabelContinuation(
									savedFunction,
									stripNull(interpreter.reifiedContinuation),
									unoptimizedChunk,
									TO_RESTART.offsetInDefaultChunk,
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
					final Object valueOrReifier =
						getVariable(
							code.literalAt(code.nextNybblecodeOperand(pc)));
					if (valueOrReifier instanceof StackReifier)
					{
						return (StackReifier) valueOrReifier;
					}
					final AvailObject value = (AvailObject) valueOrReifier;
					push(value.makeImmutable());
					break;
				}
				case L1Ext_doSetLiteral:
				{
					setVariable(
						code.literalAt(code.nextNybblecodeOperand(pc)), pop());
					break;
				}
				case L1Ext_doDuplicate:
				{
					push(pointerAt(stackp).makeImmutable());
					break;
				}
				case L1Ext_doPermute:
				{
					final A_Tuple permutation =
						code.literalAt(code.nextNybblecodeOperand(pc));
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
					final A_Bundle bundle =
						code.literalAt(code.nextNybblecodeOperand(pc));
					final A_Type expectedReturnType =
						code.literalAt(code.nextNybblecodeOperand(pc));
					final A_Type superUnionType =
						code.literalAt(code.nextNybblecodeOperand(pc));
					final int numArgs = bundle.bundleMethod().numArgs();
					if (debugL1)
					{
						Interpreter.log(
							Interpreter.loggerDebugL1,
							Level.FINER,
							"{0}L1 supercall: {1}",
							interpreter.debugModeString,
							bundle.message().atomName());
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
					final long beforeLookup = AvailRuntime.captureNanos();
					try
					{
						matching = method.lookupByTypesFromTuple(typesTuple);
					}
					catch (final MethodDefinitionException e)
					{
						return reifyAndReportFailedLookup(
							method, e.errorCode());
					}
					finally
					{
						final long afterLookup = AvailRuntime.captureNanos();
						interpreter.recordDynamicLookup(
							bundle, afterLookup - beforeLookup);
					}

					final @Nullable StackReifier reifier =
						callMethodAfterLookup(matching, expectedReturnType);
					if (reifier != null)
					{
						return reifier;
					}

					// The call returned normally, without reifications, with
					// the resulting value in the interpreter's latestResult().
					final AvailObject result = interpreter.latestResult();
					if (debugL1)
					{
						Interpreter.log(
							Interpreter.loggerDebugL1,
							Level.FINER,
							"{0}Call returned: {1}",
							interpreter.debugModeString,
							result.typeTag().name());
					}
					final @Nullable StackReifier returnCheckReifier =
						interpreter.checkReturnType(
							result, expectedReturnType, function);
					if (returnCheckReifier != null)
					{
						// Reification is happening within the handling of
						// the failed return type check.
						return returnCheckReifier;
					}
					// The return check passed.
					assert stackp <= code.numSlots();
					// Replace the stack slot.
					pointerAtPut(stackp, result);
					break;
				}
				case L1Ext_doSetLocalSlot:
				{
					pointerAtPut(code.nextNybblecodeOperand(pc), pop());
					break;
				}
			}
		}
		// It ran off the end of the nybblecodes, which is how a function
		// returns in Level One.  Capture the result and return to the Java
		// caller.
		interpreter.latestResult(pop());
		assert stackp == pointers.length;
		interpreter.returnNow = true;
		interpreter.returningFunction = function;
		if (debugL1)
		{
			Interpreter.log(
				Interpreter.loggerDebugL1,
				Level.FINER,
				"{0}L1 return",
				interpreter.debugModeString);
		}
		return null;
	}

	/**
	 * Get the value from the given variable, reifying and invoking the {@link
	 * AvailRuntime#unassignedVariableReadFunction()} if the variable has no
	 * value.
	 *
	 * @param variable
	 *        The variable to read.
	 * @return A {@link StackReifier} if the variable was unassigned, otherwise
	 *         the {@link AvailObject} that's the current value of the variable.
	 */
	private Object getVariable (final A_Variable variable)
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
			return interpreter.reifyThenCall0(
				interpreter.runtime().unassignedVariableReadFunction(),
				reificationForFailedVariableGetStat);
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
	 * @return A {@link StackReifier} to reify the stack if an observed variable
	 *         is assigned while tracing is off, otherwise null.
	 */
	private @Nullable StackReifier setVariable (
		final A_Variable variable,
		final AvailObject value)
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

			final A_Function savedFunction = stripNull(interpreter.function);
			final AvailObject[] savedPointers = pointers;
			final int savedOffset = interpreter.offset;
			final int savedPc = pc.value;
			final int savedStackp = stackp;

			final A_Function implicitObserveFunction =
				interpreter.runtime().implicitObserveFunction();
			interpreter.argsBuffer.clear();
			interpreter.argsBuffer.add((AvailObject) assignmentFunction());
			interpreter.argsBuffer.add((AvailObject) tuple(variable, value));
			final @Nullable StackReifier reifier =
				interpreter.invokeFunction(implicitObserveFunction);
			if (reifier != null)
			{
				if (reifier.actuallyReify())
				{
					final A_RawFunction code = savedFunction.code();
					final A_Continuation continuation =
						createContinuationExceptFrame(
							savedFunction,
							nil,
							savedPc,   // Right after the set-variable.
							savedStackp,
							unoptimizedChunk,
							TO_RESUME.offsetInDefaultChunk);
					for (int i = code.numSlots(); i > 0; i--)
					{
						continuation.argOrLocalOrStackAtPut(i, pointerAt(i));
					}
					if (Interpreter.debugL2)
					{
						Interpreter.log(
							Interpreter.loggerDebugL2,
							Level.FINER,
							"{0}Push reified continuation "
								+ "for L1 setVar failure: {1}",
							interpreter.debugModeString,
							continuation.function().code().methodName());
					}
					reifier.pushContinuation(continuation);
				}
				pointers = savedPointers;
				interpreter.chunk = unoptimizedChunk;
				interpreter.offset = savedOffset;
				interpreter.function = savedFunction;
				pc.value = savedPc;
				stackp = savedStackp;
				return reifier;
			}
		}
		return null;
	}

	/**
	 * Check that the matching definition is a method definition, then invoke
	 * its body function.  If reification is requested, construct a suitable
	 * continuation for the current frame on the way out.
	 *
	 * @param matching
	 *        The {@link A_Definition} that was already looked up.
	 * @return Either {@code null} to indicate successful return from the called
	 *         function, or a {@link StackReifier} to indicate reification is in
	 *         progress.
	 */
	private @Nullable StackReifier callMethodAfterLookup (
		final A_Definition matching,
		final A_Type expectedReturnType)
	{
		// At this point, the frame information is still the same, but we've set
		// up argsBuffer.
		if (matching.isForwardDefinition())
		{
			return reifyAndReportFailedLookup(
				matching.definitionMethod(), E_FORWARD_METHOD_DEFINITION);
		}
		if (matching.isAbstractDefinition())
		{
			return reifyAndReportFailedLookup(
				matching.definitionMethod(), E_ABSTRACT_METHOD_DEFINITION);
		}

		final A_Function savedFunction = stripNull(interpreter.function);
		assert interpreter.chunk == unoptimizedChunk;
		final int savedOffset = interpreter.offset;
		final AvailObject[] savedPointers = pointers;
		final int savedPc = pc.value;
		final int savedStackp = stackp;

		final A_Function functionToInvoke = matching.bodyBlock();
		final @Nullable StackReifier reifier =
			interpreter.invokeFunction(functionToInvoke);
		try
		{
			if (reifier != null)
			{
				if (Interpreter.debugL2)
				{
					Interpreter.log(
						Interpreter.loggerDebugL2,
						Level.FINER,
						"{0}Reifying call from L1 ({1})",
						interpreter.debugModeString,
						reifier.actuallyReify());
				}
				if (reifier.actuallyReify())
				{
					// At some point during the call, reification was
					// requested.  Add this frame and rethrow.
					pointers = savedPointers;
					final A_Continuation continuation =
						createContinuationExceptFrame(
							savedFunction,
							nil,
							savedPc,
							savedStackp,
							unoptimizedChunk,
							TO_RETURN_INTO.offsetInDefaultChunk);
					for (
						int i = savedFunction.code().numSlots();
						i >= 1;
						i--)
					{
						continuation.argOrLocalOrStackAtPut(i, pointerAt(i));
					}
					if (Interpreter.debugL2)
					{
						Interpreter.log(
							Interpreter.loggerDebugL2,
							Level.FINER,
							"{0}Push reified continuation for L1: {1}",
							interpreter.debugModeString,
							continuation.function().code().methodName());
					}
					reifier.pushContinuation(continuation);
				}
				return reifier;
			}
		}
		finally
		{
			pointers = savedPointers;
			interpreter.chunk = unoptimizedChunk;
			interpreter.offset = savedOffset;
			interpreter.function = savedFunction;
			pc.value = savedPc;
			stackp = savedStackp;
		}
		return null;
	}

	/**
	 * Return a {@link StackReifier} to reify the Java stack into {@link
	 * A_Continuation}s, then invoke the {@link
	 * AvailRuntime#invalidMessageSendFunction()} with appropriate arguments.
	 * An {@link AvailErrorCode} is also provided to indicate what the lookup
	 * problem was.
	 *
	 * @param method
	 *        The method that failed lookup.
	 * @param errorCode
	 *        The {@link AvailErrorCode} indicating the lookup problem.
	 * @return A {@link StackReifier} to cause reification.
	 */
	private StackReifier reifyAndReportFailedLookup (
		final A_Method method,
		final AvailErrorCode errorCode)
	{
		return interpreter.reifyThenCall3(
			interpreter.runtime().invalidMessageSendFunction(),
			reificationForFailedLookupStat,
			errorCode.numericCode(),
			method,
			tupleFromList(interpreter.argsBuffer));
	}
}
