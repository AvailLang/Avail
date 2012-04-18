/**
 * L2Operation.java Copyright © 1993-2012, Mark van Gulik and Todd L Smith. All
 * rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.Primitive.Result.*;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import static com.avail.interpreter.levelTwo.L2Interpreter.*;
import java.util.*;
import java.util.logging.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.optimizer.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * The instruction set for the {@linkplain L2Interpreter level two Avail
 * interpreter}.  Avail programs can only see as far down as the level one
 * nybblecode representation.  Level two translations are invisibly created as
 * necessary to boost performance of frequently executed code.  Technically
 * level two is an optional part of the implementation, but modern hardware
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public enum L2Operation
{
	/**
	 * A place holder for invalid wordcode instructions.
	 */
	L2_UNKNOWN_WORDCODE ()
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			error("Unknown wordcode\n");
		}

		@Override
		public boolean shouldEmit ()
		{
			assert false
				: "An instruction with this operation should not be created";
			return false;
		}
	},

	/**
	 * A label can be the target of a branching instruction.  It is not actually
	 * emitted in the instruction stream, but it acts as a place holder during
	 * code generation and optimization.
	 */
	L2_LABEL (COMMENT.is("Name of label"))
	{
		@Override
		void step (final L2Interpreter interpreter)
		{
			error("Label wordcode is not executable\n");
		}

		@Override
		public boolean shouldEmit ()
		{
			return false;
		}
	},

	/**
	 * This operation is only used when entering a function that uses the
	 * default chunk.  A new function has been set up for execution.  Its
	 * arguments have been written to the architectural registers.  If this is a
	 * primitive, then the primitive has already been attempted and failed,
	 * writing the failure value into the failureValueRegister().  Set up the pc
	 * and stackp, as well as local variables.  Also transfer the primitive
	 * failure value into the first local variable if this is a primitive (and
	 * therefore failed).
	 */
	L2_PREPARE_NEW_FRAME ()
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final AvailObject function = interpreter.pointerAt(FUNCTION);
			final AvailObject code = function.code();
			final int numArgs = code.numArgs();
			final int numLocals = code.numLocals();
			final int numSlots = code.numArgsAndLocalsAndStack();
			// Create locals...
			int dest = argumentOrLocalRegister(numArgs + 1);
			for (int i = 1; i <= numLocals; i++)
			{
				interpreter.pointerAtPut(
					dest,
					VariableDescriptor.forOuterType(code.localTypeAt(i)));
				dest++;
			}
			// Write the null object into the remaining stack slots.  These
			// values should not encounter any kind of ordinary use, but they
			// must still be transferred into a continuation during reification.
			// Therefore don't use Java nulls here.
			for (int i = numArgs + numLocals + 1; i <= numSlots; i++)
			{
				interpreter.pointerAtPut(dest, NullDescriptor.nullObject());
				dest++;
			}
			interpreter.integerAtPut(pcRegister(), 1);
			interpreter.integerAtPut(
				stackpRegister(),
				argumentOrLocalRegister(numSlots + 1));
			if (code.primitiveNumber() != 0)
			{
				// A failed primitive.
				assert !Primitive.byPrimitiveNumber(code.primitiveNumber())
					.hasFlag(Flag.CannotFail);
				final AvailObject primitiveFailureValue =
					interpreter.pointerAt(PRIMITIVE_FAILURE);
				final AvailObject primitiveFailureVariable =
					interpreter.pointerAt(argumentOrLocalRegister(numArgs + 1));
				primitiveFailureVariable.setValue(primitiveFailureValue);
			}
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			// No real optimization should ever be done near this wordcode.
			// Do nothing.
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Keep this instruction from being removed, since it's only used
			// by the default chunk.
			return true;
		}
	},

	/**
	 * Execute a single nybblecode of the current continuation, found in {@link
	 * FixedRegister#CALLER caller register}.  If no interrupt is indicated,
	 * move the L2 {@link L2Interpreter#offset()} back to the same instruction
	 * (which always occupies a single word, so the address is implicit).
	 */
	L2_INTERPRET_UNTIL_INTERRUPT (
		PC.is("return here after a call"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final AvailObject function = interpreter.pointerAt(FUNCTION);
			final AvailObject code = function.code();
			final AvailObject nybbles = code.nybbles();
			final int pc = interpreter.integerAt(pcRegister());

			if (!interpreter.isInterruptRequested())
			{
				// Branch back to this (operandless) instruction by default.
				interpreter.offset(interpreter.offset() - 1);
			}

			int depth = 0;
			if (debugL1)
			{
				for (
					AvailObject c = interpreter.pointerAt(CALLER);
					!c.equalsNull();
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
				if (Interpreter.logger.isLoggable(Level.FINEST))
				{
					Interpreter.logger.finest(String.format(
						"simulating %s (pc = %d)",
						L1Operation.L1Implied_Return,
						pc));
				}
				if (debugL1)
				{
					System.out.printf("%n%d  Step L1: return", depth);
				}
				interpreter.levelOneStepper.L1Implied_doReturn();
				return;
			}
			final int nybble = nybbles.extractNybbleFromTupleAt(pc);
			interpreter.integerAtPut(pcRegister(), (pc + 1));

			final L1Operation operation = L1Operation.values()[nybble];
			if (Interpreter.logger.isLoggable(Level.FINEST))
			{
				Interpreter.logger.finest(String.format(
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
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			// No real optimization should ever be done near this wordcode.
			// Do nothing.
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Keep this instruction from being removed, since it's only used
			// by the default chunk.
			return true;
		}
	},

	/**
	 * Arrive here by returning from a called method into unoptimized (level
	 * one) code.  Explode the current continuation's slots into the registers
	 * that level one expects.
	 */
	L2_REENTER_L1_CHUNK ()
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final AvailObject continuation = interpreter.pointerAt(CALLER);
			final int numSlots = continuation.numArgsAndLocalsAndStack();
			for (int i = 1; i <= numSlots; i++)
			{
				interpreter.pointerAtPut(
					argumentOrLocalRegister(i),
					continuation.stackAt(i));
			}
			interpreter.integerAtPut(pcRegister(), continuation.pc());
			interpreter.integerAtPut(
				stackpRegister(),
				argumentOrLocalRegister(continuation.stackp()));
			interpreter.pointerAtPut(FUNCTION, continuation.function());
			interpreter.pointerAtPut(CALLER, continuation.caller());
		}

		@Override
		protected boolean hasSideEffect ()
		{
			return true;
		}
	},

	/**
	 * Explicitly decrement the current compiled code's countdown via {@link
	 * AvailObject#countdownToReoptimize(int)}.  If it reaches zero then
	 * re-optimize the code.
	 */
	L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO ()
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final AvailObject theFunction = interpreter.pointerAt(FUNCTION);
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
				interpreter.argsBuffer.clear();
				final int nArgs = theCode.numArgs();
				for (int i = 1; i <= nArgs; i++)
				{
					interpreter.argsBuffer.add(
						interpreter.pointerAt(argumentOrLocalRegister(i)));
				}
				interpreter.invokeFunctionArguments(
					theFunction,
					interpreter.argsBuffer);
			}
		}

		@Override
		protected boolean hasSideEffect ()
		{
			return true;
		}
	},

	/**
	 * This marks the entry point into optimized (level two) code.  At entry,
	 * the arguments are expected to be in the specified architectural
	 * registers.  This operation is a place-holder and is not actually emitted.
	 */
	L2_ENTER_L2_CHUNK (
		WRITE_VECTOR.is("fixed and arguments"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			error("Enter chunk wordcode is not executable\n");
		}

		@Override
		public boolean shouldEmit ()
		{
			return false;
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			// Don't wipe out my arguments.
		}

		@Override
		protected boolean hasSideEffect ()
		{
			return true;
		}
	},

	/**
	 * This marks a re-entry point into optimized (level two) code.  At
	 * re-entry, only the architectural {@link #CALLER} register has a value.
	 * This mechanism is used for a re-entry point to which a return should
	 * arrive, as well as when restarting a continuation created from a
	 * {@link L1Operation#L1Ext_doPushLabel push-label L1 instruction}.  In the
	 * former, the return value has already been written into the continuation,
	 * and in the latter only the continuation's argument slots are non-nil.
	 */
	L2_REENTER_L2_CHUNK (
		WRITE_POINTER.is("continuation"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			error("Re-enter chunk wordcode is not executable\n");
		}

		@Override
		public boolean shouldEmit ()
		{
			return false;
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Don't eliminate, even though no wordcodes would be generated.
			return true;
		}
	},


	/**
	 * Move an {@link AvailObject} from the source to the destination.  The
	 * {@link L2Translator} creates more moves than are strictly necessary, but
	 * various mechanisms cooperate to remove redundant inter-register moves.
	 *
	 * <p>
	 * The object being moved is not made immutable by this operation, as that
	 * is the responsibility of the {@link #L2_MAKE_IMMUTABLE} operation.
	 * </p>
	 */
	L2_MOVE (
		READ_POINTER.is("source"),
		WRITE_POINTER.is("destination"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int fromIndex = interpreter.nextWord();
			final int destIndex = interpreter.nextWord();
			interpreter.pointerAtPut(
				destIndex,
				interpreter.pointerAt(fromIndex));
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2ReadPointerOperand sourceOperand =
				(L2ReadPointerOperand) instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand) instruction.operands[1];
			final L2Register sourceRegister = sourceOperand.register;
			final L2Register destinationRegister = destinationOperand.register;

			assert sourceRegister != destinationRegister;

			if (registers.hasTypeAt(sourceRegister))
			{
				registers.typeAtPut(
					destinationRegister,
					registers.typeAt(sourceRegister));
			}
			else
			{
				registers.removeTypeAt(destinationRegister);
			}
			if (registers.hasConstantAt(sourceRegister))
			{
				registers.constantAtPut(
					destinationRegister,
					registers.constantAt(sourceRegister));
			}
			else
			{
				registers.removeConstantAt(destinationRegister);
			}

		registers.propagateMove(sourceRegister, destinationRegister);
		}
	},

	/**
	 * Move a constant {@link AvailObject} into an object register.
	 */
	L2_MOVE_CONSTANT (
		CONSTANT.is("constant"),
		WRITE_POINTER.is("destination"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int fromIndex = interpreter.nextWord();
			final int destIndex = interpreter.nextWord();
			interpreter.pointerAtPut(
				destIndex,
				interpreter.chunk().literalAt(fromIndex));
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2ConstantOperand constantOperand =
				(L2ConstantOperand) instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand) instruction.operands[1];
			registers.constantAtPut(
				destinationOperand.register,
				constantOperand.object);
		}
	},

	/**
	 * Extract a captured "outer" variable from a function.  If the outer
	 * variable is an actual {@linkplain VariableDescriptor variable}, then the
	 * variable itself is what gets moved into the destination register.
	 */
	L2_MOVE_OUTER_VARIABLE (
		IMMEDIATE.is("outer index"),
		READ_POINTER.is("function"),
		WRITE_POINTER.is("destination"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int outerIndex = interpreter.nextWord();
			final int fromIndex = interpreter.nextWord();
			final int destIndex = interpreter.nextWord();
			interpreter.pointerAtPut(
				destIndex,
				interpreter.pointerAt(fromIndex).outerVarAt(outerIndex));
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2ImmediateOperand outerIndexOperand =
				(L2ImmediateOperand) instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand) instruction.operands[2];
			final L2Register destination = destinationOperand.register;
			registers.removeTypeAt(destination);
			registers.removeConstantAt(destination);
			registers.propagateWriteTo(destination);
			registers.typeAtPut(
				destination,
				registers.code().outerTypeAt(outerIndexOperand.value));
		}
	},

	/**
	 * Create a new {@linkplain VariableDescriptor variable object} of the
	 * specified {@link VariableTypeDescriptor variable type}.
	 */
	L2_CREATE_VARIABLE (
		CONSTANT.is("type"),
		WRITE_POINTER.is("variable"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int typeIndex = interpreter.nextWord();
			final int destIndex = interpreter.nextWord();
			interpreter.pointerAtPut(
				destIndex,
				VariableDescriptor.forOuterType(
					interpreter.chunk().literalAt(typeIndex)));
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2ConstantOperand constantOperand =
				(L2ConstantOperand) instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand) instruction.operands[1];
			// We know the type...
			registers.typeAtPut(
				destinationOperand.register,
				constantOperand.object);
			// ...but the instance is new so it can't be a constant.
			registers.removeConstantAt(destinationOperand.register);
			registers.propagateWriteTo(destinationOperand.register);
		}
	},

	/**
	 * Extract the value of a variable.
	 *
	 * <p>
	 * TODO [MvG] - Currently stops the VM if the variable does not have a
	 * value assigned.  This needs a better mechanism.
	 * </p>
	 */
	L2_GET_VARIABLE (
		READ_POINTER.is("variable"),
		WRITE_POINTER.is("extracted value"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int getIndex = interpreter.nextWord();
			final int destIndex = interpreter.nextWord();
			interpreter.pointerAtPut(
				destIndex,
				interpreter.pointerAt(getIndex).getValue().makeImmutable());
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2ReadPointerOperand sourceOperand =
				(L2ReadPointerOperand) instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand) instruction.operands[1];
			if (registers.hasTypeAt(sourceOperand.register))
			{
				final AvailObject oldType = registers.typeAt(
					sourceOperand.register);
				final AvailObject varType = oldType.typeIntersection(
					VariableTypeDescriptor.mostGeneralType());
				registers.typeAtPut(sourceOperand.register, varType);
				registers.typeAtPut(
					destinationOperand.register,
					varType.readType());
			}
			else
			{
				registers.removeTypeAt(destinationOperand.register);
			}
			registers.removeConstantAt(destinationOperand.register);
			registers.propagateWriteTo(destinationOperand.register);
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Subtle. Reading from a variable can fail, so don't remove this.
			return true;
		}
	},

	/**
	 * Extract the value of a variable, while simultaneously clearing it.
	 *
	 * <p>
	 * TODO [MvG] - Currently stops the VM if the variable did not have a value
	 * assigned.  This needs a better mechanism.
	 * </p>
	 */
	L2_GET_VARIABLE_CLEARING (
		READ_POINTER.is("variable"),
		WRITE_POINTER.is("extracted value"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int getIndex = interpreter.nextWord();
			final int destIndex = interpreter.nextWord();
			final AvailObject var = interpreter.pointerAt(getIndex);
			final AvailObject value = var.getValue();
			if (var.traversed().descriptor().isMutable())
			{
				var.clearValue();
			}
			else
			{
				value.makeImmutable();
			}
			interpreter.pointerAtPut(destIndex, value);
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2ReadPointerOperand variableOperand =
				(L2ReadPointerOperand) instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand) instruction.operands[1];

			// If we haven't already guaranteed that this is a variable then we
			// are probably not doing things right.
			assert registers.hasTypeAt(variableOperand.register);
			final AvailObject varType = registers.typeAt(
				variableOperand.register);
			assert varType.isSubtypeOf(
				VariableTypeDescriptor.mostGeneralType());
			registers.typeAtPut(
				destinationOperand.register,
				varType.readType());
			registers.removeConstantAt(destinationOperand.register);
			registers.propagateWriteTo(destinationOperand.register);
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Subtle. Reading from a variable can fail, so don't remove this.
			// Also it clears the variable.
			return true;
		}
	},

	/**
	 * Assign a value to a {@linkplain VariableDescriptor variable}.
	 */
	L2_SET_VARIABLE (
		READ_POINTER.is("variable"),
		READ_POINTER.is("value to write"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int setIndex = interpreter.nextWord();
			final int sourceIndex = interpreter.nextWord();
			interpreter.pointerAt(setIndex).setValue(
				interpreter.pointerAt(sourceIndex));
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2ReadPointerOperand variableOperand =
				(L2ReadPointerOperand) instruction.operands[0];
			// If we haven't already guaranteed that this is a variable then we
			// are probably not doing things right.
			assert registers.hasTypeAt(variableOperand.register);
			final AvailObject varType = registers.typeAt(
				variableOperand.register);
			assert varType.isSubtypeOf(
				VariableTypeDescriptor.mostGeneralType());
		}

		@Override
		protected boolean hasSideEffect ()
		{
			return true;
		}
	},

	/**
	 * Clear a variable; i.e., make it have no assigned value.
	 */
	L2_CLEAR_VARIABLE (
		READ_POINTER.is("variable"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int clearIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2ReadPointerOperand variableOperand =
				(L2ReadPointerOperand) instruction.operands[0];
			// If we haven't already guaranteed that this is a variable then we
			// are probably not doing things right.
			assert registers.hasTypeAt(variableOperand.register);
			final AvailObject varType = registers.typeAt(
				variableOperand.register);
			assert varType.isSubtypeOf(
				VariableTypeDescriptor.mostGeneralType());
		}

		@Override
		protected boolean hasSideEffect ()
		{
			return true;
		}
	},

	L2_CLEAR_VARIABLES (
		READ_VECTOR.is("variables to clear"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int variablesIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			return true;
		}
	},

	L2_ADD_INTEGER_CONSTANT_TO_OBJECT (
		CONSTANT.is("addend"),
		READWRITE_POINTER.is("augend"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int integerIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			error("not implemented");
		}
	},

	L2_ADD_INTEGER_CONSTANT_TO_INT (
		CONSTANT.is("addend"),
		READWRITE_INT.is("augend"),
		PC.is("if out of range"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int integerIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_ADD_OBJECT_TO_OBJECT (
		READ_POINTER.is("addend"),
		READWRITE_POINTER.is("augend"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int addIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It can fail if adding unlike infinities.
			return true;
		}
	},

	L2_ADD_INT_TO_INT (
		READ_INT.is("addend"),
		READWRITE_INT.is("augend"),
		PC.is("if out of range"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int addIndex = interpreter.nextWord();
			final int destIndex = interpreter.nextWord();
			final int failOffset = interpreter.nextWord();
			final long add = interpreter.integerAt(addIndex);
			final long dest = interpreter.integerAt(destIndex);
			final long result = dest + add;
			final int resultInt = (int) result;
			if (result == resultInt)
			{
				interpreter.integerAtPut(destIndex, resultInt);
			}
			else
			{
				interpreter.offset(failOffset);
			}
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_ADD_INT_TO_INT_MOD_32_BITS (
		READ_INT.is("addend"),
		READWRITE_INT.is("augend"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int bitIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			error("not implemented");
		}
	},

	L2_SUBTRACT_CONSTANT_INTEGER_FROM_OBJECT (
		CONSTANT.is("subtrahend"),
		READWRITE_POINTER.is("minuend"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int integerIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			error("not implemented");
		}
	},

	L2_SUBTRACT_CONSTANT_INTEGER_FROM_INT (
		CONSTANT.is("subtrahend"),
		READWRITE_INT.is("minuend"),
		PC.is("if out of range"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int integerIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_SUBTRACT_OBJECT_FROM_OBJECT (
		READ_POINTER.is("subtrahend"),
		READWRITE_POINTER.is("minuend"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int subtractIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It can fail if subtracting like infinites.
			return true;
		}
	},

	L2_SUBTRACT_INT_FROM_INT (
		READ_INT.is("subtrahend"),
		READWRITE_INT.is("minuend"),
		PC.is("if out of range"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int subtractIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_SUBTRACT_INT_FROM_INT_MOD_32_BITS (
		READ_INT.is("subtrahend"),
		READWRITE_INT.is("minuend"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int integerIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			error("not implemented");
		}
	},

	L2_MULTIPLY_CONSTANT_OBJECT_BY_OBJECT (
		CONSTANT.is("multiplier"),
		READWRITE_POINTER.is("multiplicand"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int integerIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It can fail if multiplying zero by infinity.
			return true;
		}
	},

	L2_MULTIPLY_CONSTANT_OBJECT_BY_INT (
		CONSTANT.is("multiplier"),
		READWRITE_INT.is("multiplicand"),
		PC.is("if out of range"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int integerIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_MULTIPLY_OBJECT_BY_OBJECT (
		READ_POINTER.is("multiplier"),
		READWRITE_POINTER.is("multiplicand"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int multiplyIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It can fail when multiplying zero by infinity.
			return true;
		}
	},

	L2_MULTIPLY_INT_BY_INT (
		READ_INT.is("multiplier"),
		READWRITE_INT.is("multiplicand"),
		PC.is("if out of range"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int multiplyIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_MULTIPLY_INT_BY_INT_MOD_32_BITS (
		READ_INT.is("multiplier"),
		READWRITE_INT.is("multiplicand"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int integerIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			error("not implemented");
		}
	},

	L2_DIVIDE_OBJECT_BY_CONSTANT_INT (
		READ_POINTER.is("dividend"),
		CONSTANT.is("divisor"),
		WRITE_INT.is("quotient"),
		WRITE_INT.is("remainder"),
		PC.is("if out of range"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int divideIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int integerIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int quotientIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int remainderIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the results don't fit in an int.
			return true;
		}
	},

	L2_DIVIDE_INT_BY_CONSTANT_INT (
		READ_INT.is("dividend"),
		CONSTANT.is("divisor"),
		WRITE_INT.is("quotiont"),
		WRITE_INT.is("remainder"),
		PC.is("if out of range"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int divideIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int integerIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int quotientIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int remainderIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the results don't fit in an int.
			return true;
		}
	},

	L2_DIVIDE_OBJECT_BY_OBJECT (
		READ_POINTER.is("dividend"),
		READ_POINTER.is("divisor"),
		WRITE_POINTER.is("quotient"),
		WRITE_POINTER.is("remainder"),
		PC.is("if out of range"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int divideIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int byIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int quotientIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int remainderIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int zeroIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps for division by zero.
			return true;
		}
	},

	L2_DIVIDE_INT_BY_INT (
		READ_INT.is("dividend"),
		READ_INT.is("divisor"),
		WRITE_INT.is("quotient"),
		WRITE_INT.is("remainder"),
		PC.is("if out of range"),
		PC.is("if zero divisor"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int divideIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int byIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int quotientIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int remainderIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int zeroIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps for division by zero.
			return true;
		}
	},

	L2_JUMP (
		PC.is("target"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int doIndex = interpreter.nextWord();
			interpreter.offset(doIndex);
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}

		@Override
		public boolean reachesNextInstruction ()
		{
			return false;
		}
	},

	L2_JUMP_IF_OBJECTS_EQUAL (
		PC.is("target"),
		READ_POINTER.is("first value"),
		READ_POINTER.is("second value"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int equalsIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_EQUALS_CONSTANT (
		PC.is("target"),
		READ_POINTER.is("value"),
		CONSTANT.is("constant"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int equalsIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_OBJECTS_NOT_EQUAL (
		PC.is("target"),
		READ_POINTER.is("first value"),
		READ_POINTER.is("second value"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int equalsIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_DOES_NOT_EQUAL_CONSTANT (
		PC.is("target"),
		READ_POINTER.is("value"),
		CONSTANT.is("constant"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int equalsIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_LESS_THAN_OBJECT (
		PC.is("target"),
		READ_POINTER.is("first value"),
		READ_POINTER.is("second value"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int thanIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_LESS_THAN_CONSTANT (
		PC.is("target"),
		READ_POINTER.is("value"),
		CONSTANT.is("constant"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int thanIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_LESS_THAN_OR_EQUAL_TO_OBJECT
	(
		PC.is("target"),
		READ_POINTER.is("first value"),
		READ_POINTER.is("second value"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int equalIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_LESS_THAN_OR_EQUAL_TO_CONSTANT (
		PC.is("target"),
		READ_POINTER.is("value"),
		CONSTANT.is("constant"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int equalIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_GREATER_THAN_OBJECT (
		PC.is("target"),
		READ_POINTER.is("first value"),
		READ_POINTER.is("second value"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int thanIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_GREATER_THAN_CONSTANT (
		PC.is("target"),
		READ_POINTER.is("value"),
		CONSTANT.is("constant"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int greaterIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_GREATER_THAN_OR_EQUAL_TO_OBJECT (
		PC.is("target"),
		READ_POINTER.is("first value"),
		READ_POINTER.is("second value"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int equalIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_GREATER_THAN_OR_EQUAL_TO_CONSTANT (
		PC.is("target"),
		READ_POINTER.is("value"),
		CONSTANT.is("constant"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int equalIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_KIND_OF_OBJECT (
		PC.is("target"),
		READ_POINTER.is("object"),
		READ_POINTER.is("type"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ofIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_KIND_OF_CONSTANT (
		PC.is("target"),
		READ_POINTER.is("object"),
		CONSTANT.is("constant type"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int doIndex = interpreter.nextWord();
			final int valueIndex = interpreter.nextWord();
			final int typeConstIndex = interpreter.nextWord();
			final AvailObject value = interpreter.pointerAt(valueIndex);
			final AvailObject type =
				interpreter.chunk().literalAt(typeConstIndex);
			if (value.isInstanceOf(type))
			{
				interpreter.offset(doIndex);
			}
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_IS_NOT_KIND_OF_OBJECT (
		PC.is("target"),
		READ_POINTER.is("object"),
		READ_POINTER.is("type"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ofIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_JUMP_IF_IS_NOT_KIND_OF_CONSTANT (
		PC.is("target"),
		READ_POINTER.is("object"),
		CONSTANT.is("constant type"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int doIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ifIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int ofIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	/**
	 * Jump to the specified level two program counter if an interrupt has been
	 * requested but not yet serviced.  Otherwise do nothing.
	 */
	L2_JUMP_IF_INTERRUPT (
		PC.is("target if interrupt"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int ifIndex = interpreter.nextWord();
			if (interpreter.isInterruptRequested())
			{
				interpreter.offset(ifIndex);
			}
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	/**
	 * Jump to the specified level two program counter if no interrupt has been
	 * requested since last serviced.  Otherwise an interrupt has been requested
	 * and we should do nothing then proceed to the next instruction.
	 */
	L2_JUMP_IF_NOT_INTERRUPT (
		PC.is("target if not interrupt"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int ifNotIndex = interpreter.nextWord();
			if (!interpreter.isInterruptRequested())
			{
				interpreter.offset(ifNotIndex);
			}
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	/**
	 * Handle an interrupt that has been requested.
	 */
	L2_PROCESS_INTERRUPT (
		READ_POINTER.is("continuation"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			interpreter.interruptProcess();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Don't remove this kind of instruction.
			return true;
		}

		@Override
		public boolean reachesNextInstruction ()
		{
			// Process will resume with the given continuation.
			return false;
		}
	},

	/**
	 * Create a continuation from scratch, using the specified caller, function,
	 * constant level one program counter, constant stack pointer, continuation
	 * slot values, and level two program counter.  Write the new continuation
	 * into the specified register.
	 */
	L2_CREATE_CONTINUATION (
		READ_POINTER.is("caller"),
		READ_POINTER.is("function"),
		IMMEDIATE.is("level one pc"),
		IMMEDIATE.is("stack pointer"),
		READ_VECTOR.is("slot values"),
		PC.is("level two pc"),
		WRITE_POINTER.is("destination"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int senderIndex = interpreter.nextWord();
			final int functionIndex = interpreter.nextWord();
			final int pcIndex = interpreter.nextWord();
			final int stackpIndex = interpreter.nextWord();
			final int slotsIndex = interpreter.nextWord();
			final int wordcodeOffset = interpreter.nextWord();
			final int destIndex = interpreter.nextWord();
			final AvailObject function = interpreter.pointerAt(functionIndex);
			final AvailObject code = function.code();
			final int frameSize = code.numArgsAndLocalsAndStack();
			final AvailObject continuation =
				ContinuationDescriptor.mutable().create(frameSize);
			continuation.caller(interpreter.pointerAt(senderIndex));
			continuation.function(function);
			continuation.pc(pcIndex);
			continuation.stackp(frameSize - code.maxStackDepth() + stackpIndex);
			continuation.levelTwoChunkOffset(
				interpreter.chunk(),
				wordcodeOffset);
			final AvailObject slots = interpreter.vectorAt(slotsIndex);
			final int size = slots.tupleSize();
			for (int i = 1; i <= size; i++)
			{
				continuation.argOrLocalOrStackAtPut(
					i,
					interpreter.pointerAt(slots.tupleIntAt(i)));
			}
			interpreter.pointerAtPut(destIndex, continuation);
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2ReadPointerOperand functionOperand =
				(L2ReadPointerOperand) instruction.operands[1];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand) instruction.operands[6];
			final L2ObjectRegister destinationRegister =
				destinationOperand.register;
			final AvailObject functionType = registers.typeAt(
				functionOperand.register);
			assert functionType.isSubtypeOf(
				FunctionTypeDescriptor.mostGeneralType());
			registers.typeAtPut(
				destinationRegister,
				ContinuationTypeDescriptor.forFunctionType(functionType));
			registers.removeConstantAt(destinationRegister);
			registers.propagateWriteTo(destinationRegister);
		}
	},

	/**
	 * Update a slot of an existing continuation.  If the continuation is
	 * mutable then change it in place, otherwise use a mutable copy.  Write
	 * the resulting continuation back to the register that provided the
	 * original.
	 */
	L2_UPDATE_CONTINUATION_SLOT (
		READWRITE_POINTER.is("continuation"),
		IMMEDIATE.is("slot index"),
		READ_POINTER.is("replacement value"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			// TODO [MvG] Implement.
			@SuppressWarnings("unused")
			final int continuationIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int indexIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int valueIndex = interpreter.nextWord();
			error("not implemented");
		}
	},

	/**
	 * Update an existing continuation's level one program counter and stack
	 * pointer to the provided immediate integers.  If the continuation is
	 * mutable then change it in place, otherwise use a mutable copy.  Write
	 * the resulting continuation back to the register that provided the
	 * original.
	 */
	L2_UPDATE_CONTINUATION_PC_AND_STACKP (
		READWRITE_POINTER.is("continuation"),
		IMMEDIATE.is("new pc"),
		IMMEDIATE.is("new stack pointer"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			// TODO [MvG] Implement.
			@SuppressWarnings("unused")
			final int continuationIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int pcIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int stackpIndex = interpreter.nextWord();
			error("not implemented");
		}
	},

	/**
	 * Look up the method to invoke.  Use the provided vector of arguments to
	 * perform a polymorphic lookup.  Write the resulting function into the
	 * specified destination register.
	 */
	L2_LOOKUP_BY_VALUES (
		SELECTOR.is("method"),
		READ_VECTOR.is("arguments"),
		WRITE_POINTER.is("looked up function"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int selectorIndex = interpreter.nextWord();
			final int argumentsIndex = interpreter.nextWord();
			final int resultingFunctionIndex = interpreter.nextWord();
			final AvailObject vect = interpreter.vectorAt(argumentsIndex);
			interpreter.argsBuffer.clear();
			final int numArgs = vect.tupleSize();
			for (int i = 1; i <= numArgs; i++)
			{
				interpreter.argsBuffer.add(
					interpreter.pointerAt(vect.tupleIntAt(i)));
			}
			final AvailObject selector =
				interpreter.chunk().literalAt(selectorIndex);
			if (debugL1)
			{
				System.out.printf(
					"  --- looking up: %s%n",
					selector.name().name());
			}
			final AvailObject signatureToCall =
				selector.lookupByValuesFromList(interpreter.argsBuffer);
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
			interpreter.pointerAtPut(
				resultingFunctionIndex,
				signatureToCall.bodyBlock());
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			// Find all possible implementations (taking into account the types
			// of the argument registers).  Then build an enumeration type over
			// those functions.
			final L2SelectorOperand selectorOperand =
				(L2SelectorOperand) instruction.operands[0];
			final L2ReadVectorOperand argsOperand =
				(L2ReadVectorOperand) instruction.operands[1];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand) instruction.operands[2];
			final List<L2ObjectRegister> argRegisters =
				argsOperand.vector.registers();
			final int numArgs = argRegisters.size();
			final List<AvailObject> argTypeBounds =
				new ArrayList<AvailObject>(numArgs);
			for (final L2ObjectRegister argRegister : argRegisters)
			{
				final AvailObject type = registers.hasTypeAt(argRegister)
					? registers.typeAt(argRegister)
					: TOP.o();
				argTypeBounds.add(type);
			}
			// Figure out what could be invoked at runtime given these argument
			// type constraints.
			final List<AvailObject> possibleFunctions =
				new ArrayList<AvailObject>();
			final List<AvailObject> possibleSignatures =
				selectorOperand.method.implementationsAtOrBelow(argTypeBounds);
			for (final AvailObject signature : possibleSignatures)
			{
				if (signature.isMethod())
				{
					possibleFunctions.add(signature.bodyBlock());
				}
			}
			if (possibleFunctions.size() == 1)
			{
				// Only one function could be looked up (it's monomorphic for
				// this call site).  Therefore we know strongly what the
				// function is.
				registers.constantAtPut(
					destinationOperand.register,
					possibleFunctions.get(0));
			}
			else
			{
				final AvailObject enumType =
					AbstractEnumerationTypeDescriptor.withInstances(
						SetDescriptor.fromCollection(possibleFunctions));
				registers.typeAtPut(destinationOperand.register, enumType);
			}
		}
	},


	/**
	 * Look up the method to invoke.  Use the provided vector of argument
	 * <em>types</em> to perform a polymorphic lookup.  Write the resulting
	 * function into the specified destination register.
	 */
	L2_LOOKUP_BY_TYPES (
		SELECTOR.is("method"),
		READ_VECTOR.is("argument types"),
		WRITE_POINTER.is("looked up function"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int selectorIndex = interpreter.nextWord();
			final int argumentTypesIndex = interpreter.nextWord();
			final int resultingFunctionIndex = interpreter.nextWord();
			final AvailObject vect = interpreter.vectorAt(argumentTypesIndex);
			interpreter.argsBuffer.clear();
			final int numArgs = vect.tupleSize();
			for (int i = 1; i <= numArgs; i++)
			{
				interpreter.argsBuffer.add(
					interpreter.pointerAt(vect.tupleIntAt(i)));
			}
			final AvailObject selector =
				interpreter.chunk().literalAt(selectorIndex);
			if (debugL1)
			{
				System.out.printf(
					"  --- looking up: %s%n",
					selector.name().name());
			}
			final AvailObject signatureToCall =
				selector.lookupByTypesFromList(interpreter.argsBuffer);
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
			interpreter.pointerAtPut(
				resultingFunctionIndex,
				signatureToCall.bodyBlock());
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			// Find all possible implementations (taking into account the types
			// of the argument registers).  Then build an enumeration type over
			// those functions.
			final L2SelectorOperand selectorOperand =
				(L2SelectorOperand) instruction.operands[0];
			final L2ReadVectorOperand argsTypesOperand =
				(L2ReadVectorOperand) instruction.operands[1];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand) instruction.operands[2];
			final List<L2ObjectRegister> argTypeRegisters =
				argsTypesOperand.vector.registers();
			final int numArgs = argTypeRegisters.size();
			final List<AvailObject> argTypeBounds =
				new ArrayList<AvailObject>(numArgs);
			for (final L2ObjectRegister argTypeRegister : argTypeRegisters)
			{
				AvailObject type = registers.constantAt(argTypeRegister);
				if (type == null)
				{
					final AvailObject meta = registers.typeAt(argTypeRegister);
					if (meta != null && !meta.equals(TYPE.o()))
					{
						assert meta.instanceCount().equals(
							IntegerDescriptor.one());
						type = meta.instances().asTuple().tupleAt(1);
					}
					else
					{
						type = TOP.o();
					}
				}
				argTypeBounds.add(type);
			}
			// Figure out what could be invoked at runtime given these argument
			// type constraints.
			final List<AvailObject> possibleFunctions =
				new ArrayList<AvailObject>();
			final List<AvailObject> possibleSignatures =
				selectorOperand.method.implementationsAtOrBelow(argTypeBounds);
			for (final AvailObject signature : possibleSignatures)
			{
				if (signature.isMethod())
				{
					possibleFunctions.add(signature.bodyBlock());
				}
			}
			if (possibleFunctions.size() == 1)
			{
				// Only one function could be looked up (it's monomorphic for
				// this call site).  Therefore we know strongly what the
				// function is.
				registers.constantAtPut(
					destinationOperand.register,
					possibleFunctions.get(0));
			}
			else
			{
				final AvailObject enumType =
					AbstractEnumerationTypeDescriptor.withInstances(
						SetDescriptor.fromCollection(possibleFunctions));
				registers.typeAtPut(destinationOperand.register, enumType);
			}
		}
	},

	/**
	 * Send the specified method and arguments.  The calling continuation is
	 * provided, which allows this operation to act more like a non-local jump
	 * than a call.  The continuation has the arguments popped already, with the
	 * expected return type pushed instead.
	 *
	 * <p>
	 * The appropriate function is looked up and invoked.  The function may be a
	 * primitive, and the primitive may succeed, fail, or change the current
	 * continuation.
	 * </p>
	 */
	L2_INVOKE (
		READ_POINTER.is("continuation"),
		READ_POINTER.is("function"),
		READ_VECTOR.is("arguments"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			// Assume the current continuation is already reified.
			final int callerIndex = interpreter.nextWord();
			final int functionIndex = interpreter.nextWord();
			final int argumentsIndex = interpreter.nextWord();
			final AvailObject caller = interpreter.pointerAt(callerIndex);
			final AvailObject function = interpreter.pointerAt(functionIndex);
			final AvailObject vect = interpreter.vectorAt(argumentsIndex);
			interpreter.argsBuffer.clear();
			for (int i = 1; i <= vect.tupleSize(); i++)
			{
				interpreter.argsBuffer.add(
					interpreter.pointerAt(vect.tupleIntAt(i)));
			}
			interpreter.invokePossiblePrimitiveWithReifiedCaller(
				function,
				caller);
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			// Restriction happens elsewhere.
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Never remove invocations -- but inlining might make them go away.
			return true;
		}

		@Override
		public boolean reachesNextInstruction ()
		{
			// Returns to the pc saved in the continuation.
			return false;
		}
	},

	/**
	 * Invoke the specified <em>primitive</em> function with the supplied
	 * arguments, ignoring the primitive designation.  The calling continuation
	 * is provided, which allows this operation to act more like a non-local
	 * jump than a call.  The continuation has the arguments popped already,
	 * with the expected return type pushed instead.
	 *
	 * <p>
	 * The function must be a primitive which has already failed at this point,
	 * so set up the failure code of the function without trying the primitive.
	 * The failure value from the failed primitive attempt is provided and will
	 * be saved in the architectural {@link FixedRegister#PRIMITIVE_FAILURE}
	 * register for use by subsequent L1 or L2 code.
	 * </p>
	 */
	L2_INVOKE_AFTER_FAILED_PRIMITIVE (
		READ_POINTER.is("continuation"),
		READ_POINTER.is("function"),
		READ_VECTOR.is("arguments"),
		READ_POINTER.is("primitive failure value"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			// The continuation is required to have already been reified.
			final int callerIndex = interpreter.nextWord();
			final int functionIndex = interpreter.nextWord();
			final int argumentsIndex = interpreter.nextWord();
			final int failureValueIndex = interpreter.nextWord();
			final AvailObject caller = interpreter.pointerAt(callerIndex);
			final AvailObject function = interpreter.pointerAt(functionIndex);
			final AvailObject failureValue =
				interpreter.pointerAt(failureValueIndex);
			final AvailObject vect = interpreter.vectorAt(argumentsIndex);
			interpreter.argsBuffer.clear();
			for (int i = 1; i <= vect.tupleSize(); i++)
			{
				interpreter.argsBuffer.add(
					interpreter.pointerAt(vect.tupleIntAt(i)));
			}
			final AvailObject codeToCall = function.code();
			final int primNum = codeToCall.primitiveNumber();
			assert primNum != 0;
			assert !Primitive.byPrimitiveNumber(primNum).hasFlag(
				Flag.CannotFail);
			interpreter.invokeWithoutPrimitiveFunctionArguments(
				function,
				interpreter.argsBuffer,
				caller);
			// Put the primitive failure value somewhere that both L1 and L2
			// will find it.
			interpreter.pointerAtPut(PRIMITIVE_FAILURE, failureValue);
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			// Restriction happens elsewhere.
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Never remove this send, since it's due to a failed primitive.
			return true;
		}

		@Override
		public boolean reachesNextInstruction ()
		{
			// Returns to the pc saved in the continuation.
			return false;
		}
	},

	/**
	 * Given a continuation, extract its caller, function, and all of its slots
	 * into the specified registers.  The level one program counter and stack
	 * pointer are ignored, since they're always implicitly correlated with the
	 * level two program counter.
	 */
	L2_EXPLODE_CONTINUATION (
		READ_POINTER.is("continuation to explode"),
		WRITE_VECTOR.is("exploded continuation slots"),
		WRITE_POINTER.is("exploded caller"),
		WRITE_POINTER.is("exploded function"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			// Expand the current continuation's slots into the specified vector
			// of destination registers.  Also explode the level one pc, stack
			// pointer, the current function and the caller.
			final int continuationToExplodeIndex = interpreter.nextWord();
			final int explodedSlotsVectorIndex = interpreter.nextWord();
			final int explodedCallerIndex = interpreter.nextWord();
			final int explodedFunctionIndex = interpreter.nextWord();

			final AvailObject slots =
				interpreter.vectorAt(explodedSlotsVectorIndex);
			final int slotsCount = slots.tupleSize();
			final AvailObject continuation =
				interpreter.pointerAt(continuationToExplodeIndex);
			assert continuation.numArgsAndLocalsAndStack() == slotsCount;
			for (int i = 1; i <= slotsCount; i++)
			{
				final AvailObject slotValue =
					continuation.argOrLocalOrStackAt(i);
				interpreter.pointerAtPut(slots.tupleIntAt(i), slotValue);
			}
			interpreter.pointerAtPut(
				explodedCallerIndex,
				continuation.caller());
			interpreter.pointerAtPut(
				explodedFunctionIndex,
				continuation.function());
		}

		@Override
		protected boolean hasSideEffect ()
		{
			return true;
		}
	},

	/**
	 * Extract the {@link InstanceTypeDescriptor exact type} of an object in a
	 * register, writing the type to another register.
	 */
	L2_GET_TYPE (
		READ_POINTER.is("value"),
		WRITE_POINTER.is("value's type"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int srcIndex = interpreter.nextWord();
			final int destIndex = interpreter.nextWord();
			interpreter.pointerAtPut(
				destIndex,
				InstanceTypeDescriptor.on(interpreter.pointerAt(srcIndex)));
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2ReadPointerOperand sourceOperand =
				(L2ReadPointerOperand) instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand) instruction.operands[1];

			final L2ObjectRegister sourceRegister = sourceOperand.register;
			final L2ObjectRegister destinationRegister =
				destinationOperand.register;
			if (registers.hasTypeAt(sourceRegister))
			{
				final AvailObject type =
					registers.typeAt(sourceRegister);
				// Apply the rule of metacovariance. It says that given types T1
				// and T2, T1 <= T2 implies T1 type <= T2 type. It is guaranteed
				// true for all types in Avail.
				final AvailObject meta = InstanceTypeDescriptor.on(type);
				registers.typeAtPut(destinationRegister, meta);
			}
			else
			{
				registers.typeAtPut(destinationRegister, TYPE.o());
			}

		if (registers.hasConstantAt(sourceRegister))
			{
				registers.constantAtPut(
					destinationRegister,
					registers.constantAt(sourceRegister).kind());
			}
			else
			{
				registers.removeConstantAt(destinationRegister);
			}
			registers.propagateWriteTo(destinationRegister);
		}
	},

	/**
	 * Create a {@link TupleDescriptor tuple} from the {@linkplain AvailObject
	 * objects} in the specified registers.
	 */
	L2_CREATE_TUPLE (
		READ_VECTOR.is("elements"),
		WRITE_POINTER.is("tuple"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int valuesIndex = interpreter.nextWord();
			final int destIndex = interpreter.nextWord();
			final AvailObject indices = interpreter.vectorAt(valuesIndex);
			final int size = indices.tupleSize();
			final AvailObject tuple =
				ObjectTupleDescriptor.mutable().create(size);
			for (int i = 1; i <= size; i++)
			{
				tuple.tupleAtPut(
					i,
					interpreter.pointerAt(indices.tupleIntAt(i)));
			}
			interpreter.pointerAtPut(destIndex, tuple);
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2ReadVectorOperand sourcesOperand =
				(L2ReadVectorOperand) instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand) instruction.operands[1];

			final L2RegisterVector sourceVector = sourcesOperand.vector;
			final int size = sourceVector.registers().size();
			final AvailObject sizeRange =
				IntegerDescriptor.fromInt(size).kind();
			List<AvailObject> types;
			types = new ArrayList<AvailObject>(sourceVector.registers().size());
			for (final L2Register register : sourceVector.registers())
			{
				if (registers.hasTypeAt(register))
				{
					types.add(registers.typeAt(register));
				}
				else
				{
					types.add(ANY.o());
				}
			}
			final AvailObject tupleType =
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					sizeRange,
					TupleDescriptor.fromCollection(types),
					BottomTypeDescriptor.bottom());
			tupleType.makeImmutable();
			registers.typeAtPut(destinationOperand.register, tupleType);
			registers.propagateWriteTo(destinationOperand.register);
			if (sourceVector.allRegistersAreConstantsIn(registers))
			{
				final List<AvailObject> constants = new ArrayList<AvailObject>(
					sourceVector.registers().size());
				for (final L2Register register : sourceVector.registers())
				{
					constants.add(registers.constantAt(register));
				}
				final AvailObject tuple = TupleDescriptor.fromCollection(
					constants);
				tuple.makeImmutable();
				assert tuple.isInstanceOf(tupleType);
				registers.constantAtPut(
					destinationOperand.register,
					tuple);
			}
			else
			{
				registers.removeConstantAt(
					destinationOperand.register);
			}
		}
	},

	/**
	 * Attempt to perform the specified primitive, using the provided arguments.
	 * If successful, check that the resulting object's type agrees with the
	 * provided expected type (TODO [MvG] currently stopping the VM if not),
	 * writing the result to some register and then jumping to the success
	 * label.  If the primitive fails, capture the primitive failure value in
	 * some register then continue to the next instruction.
	 *
	 * <p>
	 * Unlike for {@link #L2_SEND} and related operations, we do not provide the
	 * calling continuation here.  That's because by inlining the primitive
	 * attempt we have avoided (or at worst postponed) construction of the
	 * continuation that reifies the current function execution.  This is a Good
	 * Thing, performance-wise.
	 * </p>
	 *
	 * <p>
	 * A collection of preserved fields is provided.  Since tampering with a
	 * continuation switches it to use the default level one interpreting chunk,
	 * we can rest assured that anything written to a continuation by optimized
	 * level two code will continue to be free from tampering.  The preserved
	 * fields lists any such registers whose values are preserved across both
	 * successful primitive invocations and failed invocations.
	 * </p>
	 */
	L2_ATTEMPT_INLINE_PRIMITIVE (
		PRIMITIVE.is("primitive to attempt"),
		READ_VECTOR.is("arguments"),
		READ_POINTER.is("expected type"),
		WRITE_POINTER.is("primitive result"),
		WRITE_POINTER.is("primitive failure value"),
		READWRITE_VECTOR.is("preserved fields"),
		PC.is("if primitive succeeds"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int primNumber = interpreter.nextWord();
			final int argsVector = interpreter.nextWord();
			final int expectedTypeRegister = interpreter.nextWord();
			final int resultRegister = interpreter.nextWord();
			final int failureValueRegister = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int unusedPreservedVector = interpreter.nextWord();
			final int successOffset = interpreter.nextWord();

			final AvailObject argsVect = interpreter.vectorAt(argsVector);
			interpreter.argsBuffer.clear();
			for (int i1 = 1; i1 <= argsVect.tupleSize(); i1++)
			{
				interpreter.argsBuffer.add(
					interpreter.pointerAt(argsVect.tupleIntAt(i1)));
			}
			// Only primitive 340 needs the compiledCode argument, and it's
			// infallible.  Thus, we can pass null.
			final Result res = interpreter.attemptPrimitive(
				primNumber,
				null,
				interpreter.argsBuffer);
			if (res == SUCCESS)
			{
				final AvailObject expectedType =
					interpreter.pointerAt(expectedTypeRegister);
				if (!interpreter.primitiveResult.isInstanceOf(expectedType))
				{
					// TODO [MvG] This will have to be handled better some day.
					error(
						"primitive %s's result (%s) did not agree with"
						+ " semantic restriction's expected type (%s)",
						Primitive.byPrimitiveNumber(primNumber).name(),
						interpreter.primitiveResult,
						expectedType);
				}
				interpreter.pointerAtPut(
					resultRegister,
					interpreter.primitiveResult);
				interpreter.offset(successOffset);
			}
			else if (res == FAILURE)
			{
				interpreter.pointerAtPut(
					failureValueRegister,
					interpreter.primitiveResult);
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

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2WritePointerOperand result =
				(L2WritePointerOperand) instruction.operands[3];
			final L2WritePointerOperand failureValue =
				(L2WritePointerOperand) instruction.operands[4];
			registers.removeTypeAt(result.register);
			registers.removeConstantAt(result.register);
			registers.propagateWriteTo(result.register);
			registers.removeTypeAt(failureValue.register);
			registers.removeConstantAt(failureValue.register);
			registers.propagateWriteTo(failureValue.register);
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It could fail and jump.
			return true;
		}
	},

	/**
	 * Execute a primitive with the provided arguments, writing the result into
	 * the specified register.  The primitive must not fail.  Check that the
	 * resulting object's type agrees with the provided expected type
	 * (TODO [MvG] currently stopping the VM if not).
	 *
	 * <p>
	 * Unlike for {@link #L2_SEND} and related operations, we do not provide the
	 * calling continuation here.  That's because by inlining the primitive
	 * attempt we have avoided (or at worst postponed) construction of the
	 * continuation that reifies the current function execution.  This is a Good
	 * Thing, performance-wise.
	 * </p>
	 */
	L2_RUN_INFALLIBLE_PRIMITIVE (
		PRIMITIVE.is("primitive to run"),
		READ_VECTOR.is("arguments"),
		READ_POINTER.is("expected type"),
		WRITE_POINTER.is("primitive result"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int primNumber = interpreter.nextWord();
			final int argsVector = interpreter.nextWord();
			final int expectedTypeRegister = interpreter.nextWord();
			final int resultRegister = interpreter.nextWord();
			final AvailObject argsVect = interpreter.vectorAt(argsVector);
			interpreter.argsBuffer.clear();
			for (int i1 = 1; i1 <= argsVect.tupleSize(); i1++)
			{
				interpreter.argsBuffer.add(
					interpreter.pointerAt(argsVect.tupleIntAt(i1)));
			}
			// Only primitive 340 needs the compiledCode argument, and it's
			// always folded.  In the case that primitive 340 is known to
			// produce the wrong type at some site (potentially dead code due to
			// inlining of an unreachable branch), it is converted to an
			// explicit failure instruction.  Thus we can pass null.
			final Result res = interpreter.attemptPrimitive(
				primNumber,
				null,
				interpreter.argsBuffer);
			assert res == SUCCESS;
			final AvailObject expectedType =
				interpreter.pointerAt(expectedTypeRegister);
			if (!interpreter.primitiveResult.isInstanceOf(expectedType))
			{
				// TODO [MvG] - This will have to be handled better some day.
				error(
					"primitive %s's result (%s) did not agree with"
					+ " semantic restriction's expected type (%s)",
					Primitive.byPrimitiveNumber(primNumber).name(),
					interpreter.primitiveResult,
					expectedType);
			}
			interpreter.pointerAtPut(
				resultRegister,
				interpreter.primitiveResult);
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2PrimitiveOperand primitiveOperand =
				(L2PrimitiveOperand) instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand) instruction.operands[3];
			registers.removeTypeAt(destinationOperand.register);
			registers.removeConstantAt(destinationOperand.register);
			registers.propagateWriteTo(destinationOperand.register);

			// We can at least believe what the basic primitive signature says
			// it returns.
			registers.typeAtPut(
				destinationOperand.register,
				primitiveOperand.primitive.blockTypeRestriction().returnType());
		}

		@Override
		public boolean hasSideEffect (final L2Instruction instruction)
		{
			// It depends on the primitive.
			assert instruction.operation == this;
			final L2PrimitiveOperand primitiveOperand =
				(L2PrimitiveOperand) instruction.operands[0];
			final Primitive primitive = primitiveOperand.primitive;
			assert primitive.hasFlag(Flag.CannotFail);
			final boolean mustKeep = primitive.hasFlag(Flag.HasSideEffect)
				|| primitive.hasFlag(Flag.CatchException)
				|| primitive.hasFlag(Flag.Invokes)
				|| primitive.hasFlag(Flag.SwitchesContinuation)
				|| primitive.hasFlag(Flag.Unknown);
			return mustKeep;
		}
	},

	/**
	 * A value is known at this point to disagree with the type that it is
	 * expected to be.  Report this problem and stop execution.  Note that this
	 * instruction might be in a branch of (potentially inlined) code that
	 * happens to be unreachable in actuality, despite the compiler being unable
	 * to prove this.
	 *
	 * <p>
	 * TODO [MvG] - Of course, this will ultimately need to be handled in a much
	 * better way than just stopping the whole VM.
	 * </p>
	 */
	L2_REPORT_INVALID_RETURN_TYPE (
		PRIMITIVE.is("failed primitive"),
		READ_POINTER.is("actual value"),
		CONSTANT.is("expected type"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int primitiveNumber = interpreter.nextWord();
			final int actualValueRegister = interpreter.nextWord();
			final int expectedTypeIndex = interpreter.nextWord();
			final AvailObject actualValue =
				interpreter.pointerAt(actualValueRegister);
			final AvailObject expectedType =
				interpreter.chunk().literalAt(expectedTypeIndex);
			assert !interpreter.primitiveResult.isInstanceOf(expectedType);
			error(
				"primitive %s's result (%s) did not agree with"
				+ " semantic restriction's expected type (%s)",
				Primitive.byPrimitiveNumber(primitiveNumber).name(),
				actualValue,
				expectedType);
		}

		@Override
		public boolean hasSideEffect (final L2Instruction instruction)
		{
			return true;
		}

		@Override
		public boolean reachesNextInstruction ()
		{
			return false;
		}
	},

	L2_CONCATENATE_TUPLES (
		READ_VECTOR.is("tuples to concatenate"),
		WRITE_POINTER.is("concatenated tuple"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int subtupleIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			error("not implemented");
		}
	},

	L2_CREATE_SET (
		READ_VECTOR.is("values"),
		WRITE_POINTER.is("new set"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int valuesIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			error("not implemented");
		}
	},

	L2_CREATE_MAP (
		READ_VECTOR.is("keys"),
		READ_VECTOR.is("values"),
		WRITE_POINTER.is("new map"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int keysIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int valuesIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			error("not implemented");
		}
	},

	L2_CREATE_OBJECT (
		READ_VECTOR.is("field keys"),
		READ_VECTOR.is("field values"),
		WRITE_POINTER.is("new object"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int keysIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int valuesIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int destIndex = interpreter.nextWord();
			error("not implemented");
		}
	},

	/**
	 * Synthesize a new {@link FunctionDescriptor function} from the provided
	 * constant compiled code and the vector of captured ("outer") variables.
	 */
	L2_CREATE_FUNCTION (
		CONSTANT.is("compiled code"),
		READ_VECTOR.is("captured variables"),
		WRITE_POINTER.is("new function"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int codeIndex = interpreter.nextWord();
			final int outersIndex = interpreter.nextWord();
			final int destIndex = interpreter.nextWord();
			final AvailObject outers = interpreter.vectorAt(outersIndex);
			final AvailObject clos = FunctionDescriptor.mutable().create(
				outers.tupleSize());
			clos.code(interpreter.chunk().literalAt(codeIndex));
			for (int i = 1, end = outers.tupleSize(); i <= end; i++)
			{
				clos.outerVarAtPut(
					i,
					interpreter.pointerAt(outers.tupleIntAt(i)));
			}
			interpreter.pointerAtPut(destIndex, clos);
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final RegisterSet registers)
		{
			final L2ConstantOperand codeOperand =
				(L2ConstantOperand) instruction.operands[0];
			final L2ReadVectorOperand outersOperand =
				(L2ReadVectorOperand) instruction.operands[1];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand) instruction.operands[2];
			registers.typeAtPut(
				destinationOperand.register,
				codeOperand.object.functionType());
			registers.propagateWriteTo(destinationOperand.register);
			if (outersOperand.vector.allRegistersAreConstantsIn(registers))
			{
				final AvailObject function =
					FunctionDescriptor.mutable().create(
						outersOperand.vector.registers().size());
				function.code(codeOperand.object);
				int index = 1;
				for (final L2ObjectRegister outer : outersOperand.vector)
				{
					function.outerVarAtPut(
						index++,
						registers.constantAt(outer));
				}
			}
			else
			{
				registers.removeConstantAt(destinationOperand.register);
			}
		}
	},

	/**
	 * Return into the provided continuation with the given return value.  The
	 * continuation may be running as either level one or level two.
	 */
	L2_RETURN (
		READ_POINTER.is("continuation"),
		READ_POINTER.is("return value"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			// Return to the calling continuation with the given value.
			final int continuationIndex = interpreter.nextWord();
			final int valueIndex = interpreter.nextWord();
			assert continuationIndex == CALLER.ordinal();

			final AvailObject caller = interpreter.pointerAt(continuationIndex);
			final AvailObject valueObject = interpreter.pointerAt(valueIndex);
			interpreter.returnToCaller(caller, valueObject);
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Never remove this.
			return true;
		}

		@Override
		public boolean reachesNextInstruction ()
		{
			return false;
		}
	},

	L2_EXIT_CONTINUATION (
		READ_POINTER.is("continuation to exit"),
		READ_POINTER.is("value with which to exit"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int continuationIndex = interpreter.nextWord();
			@SuppressWarnings("unused")
			final int valueIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Never remove this.
			return true;
		}

		@Override
		public boolean reachesNextInstruction ()
		{
			return false;
		}
	},

	L2_RESUME_CONTINUATION (
		READ_POINTER.is("continuation to resume"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			@SuppressWarnings("unused")
			final int continuationIndex = interpreter.nextWord();
			error("not implemented");
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Never remove this.
			return true;
		}

		@Override
		public boolean reachesNextInstruction ()
		{
			return false;
		}
	},

	/**
	 * Force the specified object to be immutable.  Maintenance of
	 * conservative sticky-bit reference counts is mostly separated out into
	 * this operation to allow code transformations to obviate the need for it
	 * in certain non-obvious circumstances.
	 */
	L2_MAKE_IMMUTABLE (
		READ_POINTER.is("object"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int objectIndex = interpreter.nextWord();
			interpreter.pointerAt(objectIndex).makeImmutable();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Marking the object immutable is a side effect, but unfortunately
			// this could keep extra instructions around to create an object
			// that nobody wants.
			// TODO[MvG] - maybe a pseudo-copy operation from linear languages?
			return true;
		}
	},

	/**
	 * Mark as immutable all objects referred to from the specified object.
	 * Copying a continuation as part of the {@link
	 * L1Operation#L1Ext_doPushLabel} can make good use of this peculiar
	 * instruction.
	 */
	L2_MAKE_SUBOBJECTS_IMMUTABLE (
		READ_POINTER.is("object"))
	{
		@Override
		void step (final @NotNull L2Interpreter interpreter)
		{
			final int objectIndex = interpreter.nextWord();
			interpreter.pointerAt(objectIndex).makeSubobjectsImmutable();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Marking the object immutable is a side effect, but unfortunately
			// this could keep extra instructions around to create an object
			// that nobody wants.
			// [MvG] - maybe use a pseudo-copy operation from linear languages?
			return true;
		}
	};


	/**
	 * The {@linkplain L2NamedOperandType named operand types} that this
	 * {@linkplain L2Operation operation} expects.
	 */
	public final L2NamedOperandType[] namedOperandTypes;

	/**
	 * Answer the {@linkplain L2NamedOperandType named operand types} that this
	 * {@linkplain L2Operation operation} expects.
	 *
	 * @return The named operand types that this operation expects.
	 */
	public L2NamedOperandType[] operandTypes ()
	{
		return namedOperandTypes;
	}

	/**
	 * Construct a new {@link L2Operation}.
	 *
	 * @param namedOperandTypes
	 *            The named operand types that this operation expects.
	 */
	private L2Operation (final @NotNull L2NamedOperandType... namedOperandTypes)
	{
		this.namedOperandTypes = namedOperandTypes;
	}

	/**
	 * Execute this {@link L2Operation} within an {@link L2Interpreter}.  The
	 * {@linkplain L2Operand operands} are encoded as integers in the wordcode
	 * stream, extracted with {@link L2Interpreter#nextWord()}.
	 *
	 * @param interpreter
	 *            The {@linkplain L2Interpreter interpreter} on behalf of which
	 *            to perform this operation.
	 */
	abstract void step (
		final @NotNull L2Interpreter interpreter);

	/**
	 * @param instruction
	 * @param registers
	 */
	public void propagateTypesInFor (
		final @NotNull L2Instruction instruction,
		final @NotNull RegisterSet registers)
	{
		// By default just record that the destinations have been overwritten.
		for (final L2Register destinationRegister
			: instruction.destinationRegisters())
		{
			registers.removeConstantAt(destinationRegister);
			registers.removeTypeAt(destinationRegister);
			registers.propagateWriteTo(destinationRegister);
		}
	}

	/**
	 * Answer whether an instruction using this operation should be emitted. For
	 * example, labels are place holders and produce no code.  By default an
	 * instruction should be emitted, so non-emitting operations should override
	 * to return false.
	 *
	 * @return A {@code boolean} indicating if this operation should be emitted.
	 */
	public boolean shouldEmit ()
	{
		return true;
	}

	/**
	 * Answer whether this {@link L2Operation} changes the state of the
	 * interpreter in any way other than by writing to its destination
	 * registers. Most operations are computational and don't have side effects.
	 *
	 * @return Whether this operation has any side effect.
	 */
	protected boolean hasSideEffect ()
	{
		return false;
	}

	/**
	 * Answer whether the given {@link L2Instruction} (whose operation must be
	 * the receiver) changes the state of the interpreter in any way other than
	 * by writing to its destination registers. Most operations are
	 * computational and don't have side effects.
	 *
	 * <p>
	 * Most enum instances can override {@link #hasSideEffect()} if
	 * {@code false} isn't good enough, but some might need to know details of
	 * the actual {@link L2Instruction} – in which case they should override
	 * this method instead.
	 * </p>
	 *
	 * @param instruction
	 *            The {@code L2Instruction} for which a side effect test is
	 *            being performed.
	 * @return Whether that L2Instruction has any side effect.
	 */
	public boolean hasSideEffect (final @NotNull L2Instruction instruction)
	{
		assert instruction.operation == this;
		return hasSideEffect();
	}

	/**
	 * Answer whether execution of this instruction can lead to the next
	 * instruction in the sequence being reached.  Most instructions are of this
	 * form, but some might not be (return, unconditional branches, continuation
	 * resumption, etc).
	 *
	 * @return Whether the next instruction is potentially reachable from here.
	 */
	public boolean reachesNextInstruction ()
	{
		return true;
	}
}
