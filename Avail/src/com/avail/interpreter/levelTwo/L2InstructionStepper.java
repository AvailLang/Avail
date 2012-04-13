/**
 * L2InstructionStepper.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.Primitive.Result.*;
import java.util.*;
import java.util.logging.*;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.*;
import com.avail.optimizer.L2Translator;
import com.avail.interpreter.levelTwo.register.*;

/**
 * This class is used to execute individual {@linkplain L2ChunkDescriptor level
 * two} {@linkplain L2Instruction}s, which are a translation of the level one
 * nybblecodes found in {@linkplain CompiledCodeDescriptor compiled code}.  It
 * is invoked by its {@linkplain L2Interpreter}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
final class L2InstructionStepper
implements L2OperationDispatcher
{
	/** A {@linkplain Logger logger}. */
	protected static final @NotNull Logger logger =
		Logger.getLogger(L2InstructionStepper.class.getCanonicalName());

	final static boolean debugL1 = false;

	/**
	 * Answer the current index into the current chunk's wordcode instructions.
	 *
	 * @return The current zero-based offset.
	 */
	private final int offset ()
	{
		return interpreter.offset();
	}

	/**
	 * Set the current index into the current chunk's wordcode instructions.
	 *
	 * @param index The new zero-based offset.
	 */
	private void offset (final int index)
	{
		interpreter.offset(index);
	}

	/**
	 * The {@link L2Interpreter} on behalf of which to execute {@link
	 * L2Instruction}s.
	 */
	L2Interpreter interpreter;

	/**
	 * A reusable buffer for holding method arguments.
	 */
	private final List<AvailObject> argsBuffer;

	/**
	 * Construct a new {@link L2InstructionStepper}.
	 *
	 * @param interpreter The interpreter for which stepping happens.
	 */
	public L2InstructionStepper (final @NotNull L2Interpreter interpreter)
	{
		this.interpreter = interpreter;
		argsBuffer = interpreter.argsBuffer;
	}

	/**
	 * Answer the index of the {@link L2IntegerRegister} used to hold the
	 * adjusted stack pointer.
	 *
	 * @return The index into the interpreter's {@link
	 *         L2Interpreter#integerAt(int) list of integer register values}
	 *         at which the stack pointer resides.
	 */
	private static int stackpRegister ()
	{
		return L2Interpreter.stackpRegister();
	}

	/**
	 * Answer the index of the {@link L2IntegerRegister} used to hold the level
	 * one (nybblecode) program counter.
	 *
	 * @return The index into the interpreter's {@link
	 *         L2Interpreter#integerAt(int) list of integer register values}
	 *         at which one finds the current level one program counter, an
	 *         index into the {@linkplain CompiledCodeDescriptor compiled
	 *         code}'s {@linkplain CompiledCodeDescriptor.ObjectSlots#NYBBLES
	 *         tuple of nybblecodes}.
	 */
	private static int pcRegister ()
	{
		return L2Interpreter.pcRegister();
	}

	/**
	 * Answer the index of the {@link L2ObjectRegister} used to hold the calling
	 * {@linkplain ContinuationDescriptor continuation}.
	 *
	 * @return The index into the interpreter's {@link
	 *         L2Interpreter#pointerAt(int) list of object register values}
	 *         at which the calling continuation resides.
	 */
	private static int callerRegister ()
	{
		return L2Interpreter.callerRegister();
	}

	/**
	 * The index of the {@link L2ObjectRegister} used to hold the current
	 * {@linkplain FunctionDescriptor function}.
	 *
	 * @return The index into the interpreter's {@link
	 *         L2Interpreter#pointerAt(int) list of object register values}
	 *         at which the calling continuation resides.
	 */
	private static int functionRegister ()
	{
		return L2Interpreter.functionRegister();
	}

	/**
	 * Answer the index of the {@link L2ObjectRegister} used to hold the most
	 * recent failure value produced by a failed primitive.
	 *
	 * @return The index into the interpreter's {@link
	 *         L2Interpreter#pointerAt(int) list of object register values}
	 *         at which primitive failure values are written.
	 */
	private static int primitiveFailureValueRegister ()
	{
		return L2Interpreter.primitiveFailureValueRegister();
	}

	/**
	 * Answer the index of the {@link L2ObjectRegister} used to hold the
	 * architectural continuation slot which has the given index.
	 *
	 * @param argumentOrLocalNumber The slot number within a continuation.
	 * @return The index of that slot in the {@link L2ObjectRegister} list.
	 */
	private static int argumentOrLocalRegister (
		final int argumentOrLocalNumber)
	{
		return L2Interpreter.argumentOrLocalRegister(argumentOrLocalNumber);
	}

	/**
	 * Answer the value of the {@link L2IntegerRegister} with the given index.
	 *
	 * @param index The index of the register.
	 * @return The {@code int} value of the register.
	 */
	private int integerAt (final int index)
	{
		return interpreter.integerAt(index);
	}

	/**
	 * Set the value of the {@link L2IntegerRegister} with the given index.
	 *
	 * @param index The index of the integer register to write.
	 * @param value The value to write to the register.
	 */
	private void integerAtPut (final int index, final int value)
	{
		interpreter.integerAtPut(index, value);
	}

	/**
	 * Answer the value of the {@link L2ObjectRegister} with the given index.
	 *
	 * @param index The index of the object register to write.
	 * @return The value of the object register.
	 */
	private @NotNull AvailObject pointerAt (final int index)
	{
		return interpreter.pointerAt(index);
	}

	/**
	 * Set the value of the {@link L2ObjectRegister} with the given index.
	 *
	 * @param index The index of the object register to write.
	 * @param value The value to write to the register.
	 */
	private void pointerAtPut (
		final int index,
		final @NotNull AvailObject value)
	{
		interpreter.pointerAtPut(index, value);
	}

	/**
	 * Answer the {@link L2ChunkDescriptor level two chunk} being executed.
	 *
	 * @return The current chunk.
	 */
	private AvailObject chunk ()
	{
		return interpreter.chunk();
	}

	/**
	 * Extract the next integer from the current chunk's wordcodes.
	 *
	 * @return The next integer.
	 */
	private int nextWord ()
	{
		return interpreter.nextWord();
	}

	/**
	 * Answer the {@link L2RegisterVector} with the given index.
	 *
	 * @param index The vector number.
	 * @return A {@link TupleDescriptor tuple} of indices of {@link
	 *         L2ObjectRegister object registers}.
	 */
	private AvailObject vectorAt (final int index)
	{
		return interpreter.vectorAt(index);
	}

	@Override
	public void L2_UNKNOWN_WORDCODE ()
	{
		error("Unknown wordcode\n");
	}

	@Override
	public void L2_ENTER_L2_CHUNK ()
	{
		error("Enter chunk wordcode is not executable\n");
	}

	@Override
	public void L2_REENTER_L2_CHUNK ()
	{
		error("Re-enter chunk wordcode is not executable\n");
	}

	@Override
	public void L2_LABEL ()
	{
		error("Label wordcode should is not executable\n");
	}

	@Override
	public void L2_PREPARE_NEW_FRAME ()
	{
		// This operation is only used when entering a function that uses the
		// default chunk.

		// A new function has been set up for execution.  Its arguments have
		// been written to the architectural registers.  If this is a primitive,
		// then the primitive has already been attempted and failed, writing the
		// failure value into the failureValueRegister().  Set up the pc and
		// stackp, as well as local variables.  Also transfer the primitive
		// failure value into the first local variable if this is a primitive
		// (and therefore failed).

		final AvailObject function = pointerAt(functionRegister());
		final AvailObject code = function.code();
		final int numArgs = code.numArgs();
		final int numLocals = code.numLocals();
		final int numArgsAndLocalsAndStack = code.numArgsAndLocalsAndStack();
		// Create locals...
		int dest = argumentOrLocalRegister(numArgs + 1);
		for (int i = 1; i <= numLocals; i++)
		{
			pointerAtPut(
				dest,
				VariableDescriptor.forOuterType(code.localTypeAt(i)));
			dest++;
		}
		// Write the null object into the remaining stack slots.  These values
		// should not encounter any kind of ordinary use, but they must still be
		// transferred into a continuation during reification.  Therefore don't
		// use Java nulls here.
		for (
			int i = numArgs + numLocals + 1;
			i <= numArgsAndLocalsAndStack;
			i++)
		{
			interpreter.pointerAtPut(dest, NullDescriptor.nullObject());
			dest++;
		}
		integerAtPut(pcRegister(), 1);
		integerAtPut(
			stackpRegister(),
			argumentOrLocalRegister(numArgsAndLocalsAndStack + 1));
		if (code.primitiveNumber() != 0)
		{
			// A failed primitive.
			assert !Primitive.byPrimitiveNumber(code.primitiveNumber()).hasFlag(
				Flag.CannotFail);
			final AvailObject primitiveFailureValue =
				pointerAt(primitiveFailureValueRegister());
			final AvailObject primitiveFailureVariable =
				pointerAt(argumentOrLocalRegister(numArgs + 1));
			primitiveFailureVariable.setValue(primitiveFailureValue);
		}
	}

	/**
	 * Execute a single nybblecode of the current continuation, found in
	 * {@link #callerRegister() callerRegister}.  If no interrupt is indicated,
	 * move the L2 {@link #offset()} back to the same instruction (which always
	 * occupies a single word, so the address is implicit).
	 */
	@Override
	public void L2_INTERPRET_UNTIL_INTERRUPT ()
	{
		final AvailObject function = pointerAt(functionRegister());
		final AvailObject code = function.code();
		final AvailObject nybbles = code.nybbles();
		final int pc = integerAt(pcRegister());

		if (!interpreter.isInterruptRequested())
		{
			// Branch back to this (operandless) instruction by default.
			offset(offset() - 1);
		}

		// TODO Debug only.
		int depth = 0;
		for (
			AvailObject c = pointerAt(callerRegister());
			!c.equalsNull();
			c = c.caller())
		{
			depth++;
		}

		// Before we extract the nybblecode, make sure that the PC hasn't passed
		// the end of the instruction sequence. If we have, then execute an
		// L1Implied_doReturn.
		if (pc > nybbles.tupleSize())
		{
			assert pc == nybbles.tupleSize() + 1;
			if (logger.isLoggable(Level.FINEST))
			{
				logger.finest(String.format(
					"simulating %s (pc = %d)",
					L1Operation.L1Implied_Return,
					pc));
			}
			if (debugL1)
			{
				System.out.printf("%d  Step L1: return\n", depth);
			}
			interpreter.levelOneStepper.L1Implied_doReturn();
			return;
		}
		final int nybble = nybbles.extractNybbleFromTupleAt(pc);
		integerAtPut(pcRegister(), pc + 1);

		final L1Operation operation = L1Operation.values()[nybble];
		if (logger.isLoggable(Level.FINEST))
		{
			logger.finest(String.format(
				"simulating %s (pc = %d)",
				operation,
				pc));
		}
		if (debugL1)
		{
			System.out.printf("%n%d  Step L1: %s", depth, operation);
		}
		operation.dispatch(interpreter.levelOneStepper);
	}

	@Override
	public void L2_REENTER_L1_CHUNK ()
	{
		// Arrive here by returning from a called method.  Explode the current
		// continuation's slots into the registers that level one uses.
		final AvailObject continuation = pointerAt(callerRegister());
		final int numSlots = continuation.numArgsAndLocalsAndStack();
		for (int i = 1; i <= numSlots; i++)
		{
			pointerAtPut(
				argumentOrLocalRegister(i),
				continuation.stackAt(i));
		}
		integerAtPut(pcRegister(), continuation.pc());
		integerAtPut(
			stackpRegister(),
			argumentOrLocalRegister(continuation.stackp()));
		pointerAtPut(functionRegister(), continuation.function());
		pointerAtPut(callerRegister(), continuation.caller());
	}

	@Override
	public void L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO ()
	{
		// Decrement the counter in the current code object. If it reaches zero,
		// re-optimize the current code.

		final AvailObject theFunction = pointerAt(functionRegister());
		final AvailObject theCode = theFunction.code();
		final int newCount = theCode.invocationCount() - 1;
		assert newCount >= 0;
		if (newCount != 0)
		{
			theCode.countdownToReoptimize(newCount);
		}
		else
		{
			theCode.countdownToReoptimize(
				L2ChunkDescriptor.countdownForNewlyOptimizedCode());
			final L2Translator translator = new L2Translator(theCode);
			translator.translateOptimizationFor(
				3,
				interpreter);
			argsBuffer.clear();
			final int nArgs = theCode.numArgs();
			for (int i = 1; i <= nArgs; i++)
			{
				argsBuffer.add(pointerAt(argumentOrLocalRegister(i)));
			}
			interpreter.invokeFunctionArguments(theFunction, argsBuffer);
		}
	}

	@Override
	public void L2_MOVE ()
	{
		final int fromIndex = nextWord();
		final int destIndex = nextWord();
		pointerAtPut(destIndex, pointerAt(fromIndex));
	}

	@Override
	public void L2_MOVE_CONSTANT ()
	{
		final int fromIndex = nextWord();
		final int destIndex = nextWord();
		pointerAtPut(destIndex, chunk().literalAt(fromIndex));
	}

	@Override
	public void L2_MOVE_OUTER_VARIABLE ()
	{
		final int outerIndex = nextWord();
		final int fromIndex = nextWord();
		final int destIndex = nextWord();
		pointerAtPut(destIndex, pointerAt(fromIndex).outerVarAt(outerIndex));
	}

	@Override
	public void L2_CREATE_VARIABLE ()
	{
		final int typeIndex = nextWord();
		final int destIndex = nextWord();
		pointerAtPut(
			destIndex,
			VariableDescriptor.forOuterType(chunk().literalAt(typeIndex)));
	}

	@Override
	public void L2_GET_VARIABLE ()
	{
		final int getIndex = nextWord();
		final int destIndex = nextWord();
		pointerAtPut(destIndex, pointerAt(getIndex).getValue().makeImmutable());
	}

	@Override
	public void L2_GET_VARIABLE_CLEARING ()
	{
		final int getIndex = nextWord();
		final int destIndex = nextWord();
		final AvailObject var = pointerAt(getIndex);
		final AvailObject value = var.getValue();
		if (var.traversed().descriptor().isMutable())
		{
			var.clearValue();
		}
		else
		{
			value.makeImmutable();
		}
		pointerAtPut(destIndex, value);
	}

	@Override
	public void L2_SET_VARIABLE ()
	{
		final int setIndex = nextWord();
		final int sourceIndex = nextWord();
		pointerAt(setIndex).setValue(pointerAt(sourceIndex));
	}

	@Override
	public void L2_CLEAR_VARIABLE ()
	{
		@SuppressWarnings("unused")
		final int clearIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_CLEAR_VARIABLES ()
	{
		@SuppressWarnings("unused")
		final int variablesIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_ADD_INTEGER_CONSTANT_TO_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_ADD_INTEGER_CONSTANT_TO_INT ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_ADD_OBJECT_TO_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int addIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_ADD_INT_TO_INT ()
	{
		// Note that failOffset is an absolute position in the chunk.

		final int addIndex = nextWord();
		final int destIndex = nextWord();
		final int failOffset = nextWord();
		final long add = integerAt(addIndex);
		final long dest = integerAt(destIndex);
		final long result = dest + add;
		final int resultInt = (int) result;
		if (result == resultInt)
		{
			integerAtPut(destIndex, resultInt);
		}
		else
		{
			offset(failOffset);
		}
	}

	@Override
	public void L2_ADD_INT_TO_INT_MOD_32_BITS ()
	{
		@SuppressWarnings("unused")
		final int bitIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_SUBTRACT_CONSTANT_INTEGER_FROM_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_SUBTRACT_CONSTANT_INTEGER_FROM_INT ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_SUBTRACT_OBJECT_FROM_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int subtractIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_SUBTRACT_INT_FROM_INT ()
	{
		@SuppressWarnings("unused")
		final int subtractIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_SUBTRACT_INT_FROM_INT_MOD_32_BITS ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_MULTIPLY_CONSTANT_OBJECT_BY_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_MULTIPLY_CONSTANT_OBJECT_BY_INT ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_MULTIPLY_OBJECT_BY_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int multiplyIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_MULTIPLY_INT_BY_INT ()
	{
		@SuppressWarnings("unused")
		final int multiplyIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_MULTIPLY_INT_BY_INT_MOD_32_BITS ()
	{
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_DIVIDE_OBJECT_BY_CONSTANT_INT ()
	{
		@SuppressWarnings("unused")
		final int divideIndex = nextWord();
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int quotientIndex = nextWord();
		@SuppressWarnings("unused")
		final int remainderIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_DIVIDE_INT_BY_CONSTANT_INT ()
	{
		@SuppressWarnings("unused")
		final int divideIndex = nextWord();
		@SuppressWarnings("unused")
		final int integerIndex = nextWord();
		@SuppressWarnings("unused")
		final int quotientIndex = nextWord();
		@SuppressWarnings("unused")
		final int remainderIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_DIVIDE_OBJECT_BY_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int divideIndex = nextWord();
		@SuppressWarnings("unused")
		final int byIndex = nextWord();
		@SuppressWarnings("unused")
		final int quotientIndex = nextWord();
		@SuppressWarnings("unused")
		final int remainderIndex = nextWord();
		@SuppressWarnings("unused")
		final int zeroIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_DIVIDE_INT_BY_INT ()
	{
		@SuppressWarnings("unused")
		final int divideIndex = nextWord();
		@SuppressWarnings("unused")
		final int byIndex = nextWord();
		@SuppressWarnings("unused")
		final int quotientIndex = nextWord();
		@SuppressWarnings("unused")
		final int remainderIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int zeroIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP ()
	{
		final int doIndex = nextWord();
		offset(doIndex);
	}

	@Override
	public void L2_JUMP_IF_OBJECTS_EQUAL ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalsIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_EQUALS_CONSTANT ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalsIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_OBJECTS_NOT_EQUAL ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalsIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_DOES_NOT_EQUAL_CONSTANT ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalsIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_LESS_THAN_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int thanIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_LESS_THAN_CONSTANT ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int thanIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_LESS_THAN_OR_EQUAL_TO_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_LESS_THAN_OR_EQUAL_TO_CONSTANT ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_GREATER_THAN_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int thanIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_GREATER_THAN_CONSTANT ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int greaterIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_GREATER_THAN_OR_EQUAL_TO_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_GREATER_THAN_OR_EQUAL_TO_CONSTANT ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int equalIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_KIND_OF_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int ofIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_KIND_OF_CONSTANT ()
	{
		final int doIndex = nextWord();
		final int valueIndex = nextWord();
		final int typeConstIndex = nextWord();
		final AvailObject value = pointerAt(valueIndex);
		final AvailObject type = chunk().literalAt(typeConstIndex);
		if (value.isInstanceOf(type))
		{
			offset(doIndex);
		}
	}

	@Override
	public void L2_JUMP_IF_IS_NOT_KIND_OF_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int ofIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_IS_NOT_KIND_OF_CONSTANT ()
	{
		@SuppressWarnings("unused")
		final int doIndex = nextWord();
		@SuppressWarnings("unused")
		final int ifIndex = nextWord();
		@SuppressWarnings("unused")
		final int ofIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_JUMP_IF_INTERRUPT ()
	{
		final int ifIndex = nextWord();
		if (interpreter.isInterruptRequested())
		{
			offset(ifIndex);
		}
	}

	@Override
	public void L2_JUMP_IF_NOT_INTERRUPT ()
	{
		final int ifNotIndex = nextWord();
		if (!interpreter.isInterruptRequested())
		{
			offset(ifNotIndex);
		}
	}

	@Override
	public void L2_PROCESS_INTERRUPT ()
	{
		interpreter.interruptProcess();
	}

	@Override
	public void L2_CREATE_CONTINUATION ()
	{
		final int senderIndex = nextWord();
		final int functionIndex = nextWord();
		final int pcIndex = nextWord();
		final int stackpIndex = nextWord();
		final int slotsIndex = nextWord();
		final int wordcodeOffset = nextWord();
		final int destIndex = nextWord();
		final AvailObject function = pointerAt(functionIndex);
		final AvailObject code = function.code();
		final int frameSize = code.numArgsAndLocalsAndStack();
		final AvailObject continuation =
			ContinuationDescriptor.mutable().create(frameSize);
		continuation.caller(pointerAt(senderIndex));
		continuation.function(function);
		continuation.pc(pcIndex);
		continuation.stackp(frameSize - code.maxStackDepth() + stackpIndex);
		continuation.levelTwoChunkOffset(chunk(), wordcodeOffset);
		final AvailObject slots = vectorAt(slotsIndex);
		final int size = slots.tupleSize();
		for (int i = 1; i <= size; i++)
		{
			continuation.argOrLocalOrStackAtPut(
				i,
				pointerAt(slots.tupleIntAt(i)));
		}
		pointerAtPut(destIndex, continuation);
	}

	@Override
	public void L2_UPDATE_CONTINUATION_SLOT ()
	{
		@SuppressWarnings("unused")
		final int continuationIndex = nextWord();
		@SuppressWarnings("unused")
		final int indexIndex = nextWord();
		@SuppressWarnings("unused")
		final int valueIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_UPDATE_CONTINUATION_PC_AND_STACKP_ ()
	{
		@SuppressWarnings("unused")
		final int continuationIndex = nextWord();
		@SuppressWarnings("unused")
		final int pcIndex = nextWord();
		@SuppressWarnings("unused")
		final int stackpIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_SEND ()
	{
		// Assume the current continuation is already reified.
		final int callerIndex = nextWord();
		final int selectorIndex = nextWord();
		final int argumentsIndex = nextWord();
		final AvailObject caller = pointerAt(callerIndex);
		final AvailObject vect = vectorAt(argumentsIndex);
		argsBuffer.clear();
		for (int i = 1; i <= vect.tupleSize(); i++)
		{
			argsBuffer.add(pointerAt(vect.tupleIntAt(i)));
		}
		final AvailObject selector = chunk().literalAt(selectorIndex);
		if (debugL1)
		{
			System.out.printf("  --- calling: %s%n", selector.name().name());
		}
		final AvailObject signatureToCall =
			selector.lookupByValuesFromList(argsBuffer);
		if (signatureToCall.equalsNull())
		{
			error("Unable to find unique implementation for call");
			return;
		}
		if (!signatureToCall.isMethod())
		{
			error("Attempted to call a non-implementation signature");
			return;
		}
		interpreter.invokePossiblePrimitiveWithReifiedCaller(
			signatureToCall.bodyBlock(),
			caller);
	}

	@Override
	public void L2_SEND_AFTER_FAILED_PRIMITIVE_ ()
	{
		// The continuation is required to have already been reified.
		final int callerIndex = nextWord();
		final int selectorIndex = nextWord();
		final int argumentsIndex = nextWord();
		final int failureValueIndex = nextWord();
		final AvailObject caller = pointerAt(callerIndex);
		final AvailObject failureValue = pointerAt(failureValueIndex);
		final AvailObject vect = vectorAt(argumentsIndex);
		argsBuffer.clear();
		for (int i = 1; i <= vect.tupleSize(); i++)
		{
			argsBuffer.add(pointerAt(vect.tupleIntAt(i)));
		}
		final AvailObject selector = chunk().literalAt(selectorIndex);
		if (debugL1)
		{
			System.out.printf(
				"  --- calling after fail: %s%n",
				selector.name().name());
		}
		final AvailObject signatureToCall =
			selector.lookupByValuesFromList(argsBuffer);
		if (signatureToCall.equalsNull())
		{
			error("Unable to find unique implementation for call");
			return;
		}
		if (!signatureToCall.isMethod())
		{
			error("Attempted to call a non-implementation signature");
			return;
		}
		final AvailObject functionToCall = signatureToCall.bodyBlock();
		final AvailObject codeToCall = functionToCall.code();
		final int primNum = codeToCall.primitiveNumber();
		assert primNum != 0;
		assert !Primitive.byPrimitiveNumber(primNum).hasFlag(Flag.CannotFail);
		interpreter.invokeWithoutPrimitiveFunctionArguments(
			functionToCall,
			argsBuffer,
			caller);
		// Put the primitive failure value somewhere both L1 and L2 will find
		// it.
		pointerAtPut(primitiveFailureValueRegister(), failureValue);
	}

	@Override
	public void L2_SUPER_SEND ()
	{
		// Assume the current continuation is already reified.
		final int callerIndex = nextWord();
		final int selectorIndex = nextWord();
		final int argumentsIndex = nextWord();
		final int typesIndex = nextWord();
		final AvailObject caller = pointerAt(callerIndex);
		AvailObject vect = vectorAt(typesIndex);
		if (true)
		{
			argsBuffer.clear();
			for (int i = 1; i < vect.tupleSize(); i++)
			{
				argsBuffer.add(pointerAt(vect.tupleIntAt(i)));
			}
		}
		final AvailObject selector = chunk().literalAt(selectorIndex);
		final AvailObject signatureToCall =
			selector.lookupByTypesFromList(argsBuffer);
		if (signatureToCall.equalsNull())
		{
			error("Unable to find unique implementation for call");
			return;
		}
		if (!signatureToCall.isMethod())
		{
			error("Attempted to call a non-implementation signature");
			return;
		}
		vect = vectorAt(argumentsIndex);
		argsBuffer.clear();
		for (int i = 1; i < vect.tupleSize(); i++)
		{
			argsBuffer.add(pointerAt(vect.tupleIntAt(i)));
		}
		final AvailObject functionToCall = signatureToCall.bodyBlock();
		interpreter.invokePossiblePrimitiveWithReifiedCaller(
			functionToCall,
			caller);
	}

	@Override
	public void L2_EXPLODE_CONTINUATION ()
	{
		// Expand the current continuation's slots into the specified vector
		// of destination registers.  Also explode the level one pc, stack
		// pointer, the current function and the caller.
		final int continuationToExplodeIndex = nextWord();
		final int explodedSlotsVectorIndex = nextWord();
		final int explodedCallerIndex = nextWord();
		final int explodedFunctionIndex = nextWord();

		final AvailObject slots = vectorAt(explodedSlotsVectorIndex);
		final int slotsCount = slots.tupleSize();
		final AvailObject continuation = pointerAt(continuationToExplodeIndex);
		assert continuation.numArgsAndLocalsAndStack() == slotsCount;
		for (int i = 1; i <= slotsCount; i++)
		{
			final AvailObject slotValue = continuation.argOrLocalOrStackAt(i);
			pointerAtPut(slots.tupleIntAt(i), slotValue);
		}
		pointerAtPut(explodedCallerIndex, continuation.caller());
		pointerAtPut(explodedFunctionIndex, continuation.function());
	}

	@Override
	public void L2_GET_TYPE ()
	{
		final int srcIndex = nextWord();
		final int destIndex = nextWord();
		pointerAtPut(destIndex, pointerAt(srcIndex).kind());
	}

	@Override
	public void L2_CREATE_TUPLE ()
	{
		final int valuesIndex = nextWord();
		final int destIndex = nextWord();
		final AvailObject indices = vectorAt(valuesIndex);
		final int size = indices.tupleSize();
		final AvailObject tuple = ObjectTupleDescriptor.mutable().create(size);
		for (int i = 1; i <= size; i++)
		{
			tuple.tupleAtPut(i, pointerAt(indices.tupleIntAt(i)));
		}
		pointerAtPut(destIndex, tuple);
	}

	@Override
	public void L2_CONCATENATE_TUPLES ()
	{
		@SuppressWarnings("unused")
		final int subtupleIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_CREATE_SET ()
	{
		@SuppressWarnings("unused")
		final int valuesIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_CREATE_MAP ()
	{
		@SuppressWarnings("unused")
		final int keysIndex = nextWord();
		@SuppressWarnings("unused")
		final int valuesIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_CREATE_OBJECT ()
	{
		@SuppressWarnings("unused")
		final int keysIndex = nextWord();
		@SuppressWarnings("unused")
		final int valuesIndex = nextWord();
		@SuppressWarnings("unused")
		final int destIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_CREATE_FUNCTION ()
	{
		final int codeIndex = nextWord();
		final int outersIndex = nextWord();
		final int destIndex = nextWord();
		final AvailObject outers = vectorAt(outersIndex);
		final AvailObject clos = FunctionDescriptor.mutable().create(
			outers.tupleSize());
		clos.code(chunk().literalAt(codeIndex));
		for (int i = 1, end = outers.tupleSize(); i <= end; i++)
		{
			clos.outerVarAtPut(i, pointerAt(outers.tupleIntAt(i)));
		}
		pointerAtPut(destIndex, clos);
	}

	@Override
	public void L2_RETURN ()
	{
		// Return to the calling continuation with the given value.
		final int continuationIndex = nextWord();
		final int valueIndex = nextWord();
		assert continuationIndex == callerRegister();

		final AvailObject caller = pointerAt(continuationIndex);
		final AvailObject valueObject = pointerAt(valueIndex);
		interpreter.returnToCaller(caller, valueObject);
	}

	@Override
	public void L2_EXIT_CONTINUATION ()
	{
		@SuppressWarnings("unused")
		final int continuationIndex = nextWord();
		@SuppressWarnings("unused")
		final int valueIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_RESUME_CONTINUATION ()
	{
		@SuppressWarnings("unused")
		final int continuationIndex = nextWord();
		error("not implemented");
		return;
	}

	@Override
	public void L2_MAKE_IMMUTABLE ()
	{
		final int objectIndex = nextWord();
		pointerAt(objectIndex).makeImmutable();
	}

	@Override
	public void L2_MAKE_SUBOBJECTS_IMMUTABLE ()
	{
		final int objectIndex = nextWord();
		pointerAt(objectIndex).makeSubobjectsImmutable();
	}

	/**
	 * Attempt the specified primitive with the given arguments. If the
	 * primitive fails, continue at the next instruction after storing the
	 * primitive failure result in the specified register.  If it succeeds,
	 * store the primitive result in the specified register. Note that some
	 * primitives should never be inlined (indicated by an absence of the flag
	 * {@link Primitive.Flag#CanInline}.  For example, block invocation assumes
	 * the callerRegister has been set up to hold the context that is calling
	 * the primitive. This is not the case for an <em>inlined</em> primitive.
	 */
	@Override
	public void L2_ATTEMPT_INLINE_PRIMITIVE ()
	{
		final int primNumber = nextWord();
		final int argsVector = nextWord();
		final int resultRegister = nextWord();
		final int failureValueRegister = nextWord();
		@SuppressWarnings("unused")
		final int unusedPreservedVector = nextWord();
		final int successOffset = nextWord();

		final AvailObject argsVect = vectorAt(argsVector);
		argsBuffer.clear();
		for (int i1 = 1; i1 <= argsVect.tupleSize(); i1++)
		{
			argsBuffer.add(pointerAt(argsVect.tupleIntAt(i1)));
		}
		// Only primitive 340 needs the compiledCode argument, and it's always
		// folded.
		final Result res = interpreter.attemptPrimitive(
			primNumber,
			null,
			argsBuffer);
		if (res == SUCCESS)
		{
			pointerAtPut(resultRegister, interpreter.primitiveResult);
			offset(successOffset);
		}
		else if (res == FAILURE)
		{
			pointerAtPut(failureValueRegister, interpreter.primitiveResult);
		}
		else if (res == CONTINUATION_CHANGED)
		{
			error(
				"attemptPrimitive wordcode should never set up "
				+ "a new continuation",
				primNumber);
		}
		else
		{
			error("Unrecognized return type from attemptPrimitive()");
		}
	}

	/**
	 * Run the specified no-fail primitive with the given arguments, writing the
	 * result into the specified destination.
	 */
	@Override
	public void L2_RUN_INFALLIBLE_PRIMITIVE ()
	{
		final int primNumber = nextWord();
		final int argsVector = nextWord();
		final int resultRegister = nextWord();
		final AvailObject argsVect = vectorAt(argsVector);
		argsBuffer.clear();
		for (int i1 = 1; i1 <= argsVect.tupleSize(); i1++)
		{
			argsBuffer.add(pointerAt(argsVect.tupleIntAt(i1)));
		}
		// Only primitive 340 needs the compiledCode argument, and it's always
		// folded.
		final Result res = interpreter.attemptPrimitive(
			primNumber,
			null,
			argsBuffer);
		assert res == SUCCESS;
		pointerAtPut(resultRegister, interpreter.primitiveResult);
	}
}
