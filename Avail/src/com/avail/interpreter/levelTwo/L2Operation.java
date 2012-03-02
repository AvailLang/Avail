/**
 * L2Operation.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.*;

public enum L2Operation
{
	L2_unknownWordcode ()
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_unknownWordcode();
		}

		@Override
		public boolean shouldEmit ()
		{
			assert false
			: "An instruction with this operation should not be created";
			return false;
		}
	},

	L2_doLabel ()
	{
		@Override
		void dispatch (final L2OperationDispatcher operationDispatcher)
		{
			// This operation should not actually be emitted.
			operationDispatcher.L2_label();
		}

		@Override
		public boolean shouldEmit ()
		{
			return false;
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Not really, but this is an easy way to keep labels from being
			// removed from the instruction stream.  They don't get emitted
			// anyhow.
			return true;
		}
	},

	L2_doPrepareNewFrame ()
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doPrepareNewFrame();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
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

	L2_doInterpretOneInstructionAndBranchBackIfNoInterrupt
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doInterpretOneInstructionAndBranchBackIfNoInterrupt();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
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

	L2_doDecrementCounterAndReoptimizeOnZero ()
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDecrementCounterAndReoptimizeOnZero();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			return true;
		}
	},

	L2_doMoveFromObject_destObject_ (
		READ_POINTER, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMoveFromObject_destObject_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			final L2ReadPointerOperand sourceOperand =
				(L2ReadPointerOperand)instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand)instruction.operands[1];
			final L2Register sourceRegister = sourceOperand.register;
			final L2Register destinationRegister = destinationOperand.register;

			if (translator.registerHasTypeAt(sourceRegister))
			{
				translator.registerTypeAtPut(
					destinationRegister,
					translator.registerTypeAt(sourceRegister));
			}
			else
			{
				translator.removeTypeForRegister(destinationRegister);
			}
			if (translator.registerHasConstantAt(sourceRegister))
			{
				translator.registerConstantAtPut(
					destinationRegister,
					translator.registerConstantAt(sourceRegister));
			}
			else
			{
				translator.removeConstantForRegister(destinationRegister);
			}

			translator.propagateMove(sourceRegister, destinationRegister);
		}
	},

	L2_doMoveFromConstant_destObject_ (
		CONSTANT, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMoveFromConstant_destObject_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			final L2ConstantOperand constantOperand =
				(L2ConstantOperand)instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand)instruction.operands[1];
			translator.propagateWriteTo(destinationOperand.register);
			translator.registerTypeAtPut(
				destinationOperand.register,
				constantOperand.object.kind());
			translator.registerConstantAtPut(
				destinationOperand.register,
				constantOperand.object);
		}
	},

	L2_doMoveFromOuterVariable_ofFunctionObject_destObject_ (
		IMMEDIATE, READ_POINTER, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMoveFromOuterVariable_ofFunctionObject_destObject_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand)instruction.operands[2];
			translator.removeTypeForRegister(destinationOperand.register);
			translator.removeConstantForRegister(destinationOperand.register);
			translator.propagateWriteTo(destinationOperand.register);
		}
	},

	L2_doCreateVariableTypeConstant_destObject_ (
		CONSTANT, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateVariableTypeConstant_destObject_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			final L2ConstantOperand constantOperand =
				(L2ConstantOperand)instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand)instruction.operands[1];
			//  We know the type...
			translator.registerTypeAtPut(
				destinationOperand.register,
				constantOperand.object);
			//  ...but the instance is new so it can't be a constant.
			translator.removeConstantForRegister(destinationOperand.register);
			translator.propagateWriteTo(destinationOperand.register);
		}
	},

	L2_doGetVariable_destObject_ (
		READ_POINTER, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doGetVariable_destObject_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			final L2ReadPointerOperand sourceOperand =
				(L2ReadPointerOperand)instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand)instruction.operands[1];
			if (translator.registerHasTypeAt(sourceOperand.register))
			{
				final AvailObject oldType =
					translator.registerTypeAt(sourceOperand.register);
				final AvailObject varType = oldType.typeIntersection(
					VariableTypeDescriptor.mostGeneralType());
				translator.registerTypeAtPut(sourceOperand.register, varType);
				translator.registerTypeAtPut(
					destinationOperand.register,
					varType.readType());
			}
			else
			{
				translator.removeTypeForRegister(destinationOperand.register);
			}
			translator.removeConstantForRegister(destinationOperand.register);
			translator.propagateWriteTo(destinationOperand.register);
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Subtle.  Reading from a variable can fail, so don't remove this.
			return true;
		}
	},

	L2_doGetVariableClearing_destObject_ (
		READ_POINTER, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doGetVariableClearing_destObject_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			final L2ReadPointerOperand sourceOperand =
				(L2ReadPointerOperand)instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand)instruction.operands[1];
			if (translator.registerHasTypeAt(sourceOperand.register))
			{
				final AvailObject varType =
					translator.registerTypeAt(sourceOperand.register);
				translator.registerTypeAtPut(
					destinationOperand.register, varType.readType());
			}
			else
			{
				translator.removeTypeForRegister(destinationOperand.register);
			}
			translator.removeConstantForRegister(destinationOperand.register);
			translator.propagateWriteTo(destinationOperand.register);
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Subtle.  Reading from a variable can fail, so don't remove this.
			// Also it clears the variable.
			return true;
		}
	},

	L2_doSetVariable_sourceObject_ (
		READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSetVariable_sourceObject_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			// This is kind of strange.  Because of the way outer variables can
			// lose all type information, we use the fact that the compiler set
			// up an assignment to a variable to indicate that the variable
			// really is a variable.
			final L2ReadPointerOperand variableOperand =
				(L2ReadPointerOperand)instruction.operands[0];
			final AvailObject varType;
			if (translator.registerHasTypeAt(variableOperand.register))
			{
				final AvailObject oldType =
					translator.registerTypeAt(variableOperand.register);
				varType = oldType.typeIntersection(
					VariableTypeDescriptor.mostGeneralType());
			}
			else
			{
				varType = VariableTypeDescriptor.mostGeneralType();
			}
			translator.registerTypeAtPut(variableOperand.register, varType);
		}

		@Override
		protected boolean hasSideEffect ()
		{
			return true;
		}
	},

	L2_doClearVariable_ (
		READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doClearVariable_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			return true;
		}
	},

	L2_doClearVariablesVector_ (
		READ_VECTOR)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doClearVariablesVector_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			return true;
		}
	},

	L2_doAddIntegerConstant_destObject_ (
		CONSTANT, READWRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddIntegerConstant_destObject_();
		}
	},

	L2_doAddIntegerConstant_destInteger_ifFail_ (
		CONSTANT, READWRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddIntegerConstant_destInteger_ifFail_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_doAddObject_destObject_ (
		READ_POINTER, READWRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddObject_destObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It can fail if adding unlike infinities.
			return true;
		}
	},

	L2_doAddInteger_destInteger_ifFail_ (
		READ_INT, READWRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddInteger_destInteger_ifFail_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_doAddIntegerImmediate_destInteger_ifFail_ (
		IMMEDIATE, READWRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddIntegerImmediate_destInteger_ifFail_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_doAddModThirtyTwoBitInteger_destInteger_ (
		READ_INT, READWRITE_INT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddModThirtyTwoBitInteger_destInteger_();
		}
	},

	L2_doSubtractIntegerConstant_destObject_ (
		CONSTANT, READWRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractIntegerConstant_destObject_();
		}
	},

	L2_doSubtractIntegerConstant_destInteger_ifFail_ (
		CONSTANT, READWRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractIntegerConstant_destInteger_ifFail_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_doSubtractObject_destObject_ (
		READ_POINTER, READWRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractObject_destObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It can fail if subtracting like infinites.
			return true;
		}
	},

	L2_doSubtractInteger_destInteger_ifFail_ (
		READ_INT, READWRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractInteger_destInteger_ifFail_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_doSubtractIntegerImmediate_destInteger_ifFail_ (
		IMMEDIATE, READWRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractIntegerImmediate_destInteger_ifFail_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_doSubtractModThirtyTwoBitInteger_destInteger_ (
		READ_INT, READWRITE_INT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractModThirtyTwoBitInteger_destInteger_();
		}
	},

	L2_doMultiplyIntegerConstant_destObject_ (
		CONSTANT, READWRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyIntegerConstant_destObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It can fail if multiplying zero by infinity.
			return true;
		}
	},

	L2_doMultiplyIntegerConstant_destInteger_ifFail_ (
		CONSTANT, READWRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyIntegerConstant_destInteger_ifFail_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_doMultiplyObject_destObject_ (
		READ_POINTER, READWRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyObject_destObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It can fail when multiplying zero by infinity.
			return true;
		}
	},

	L2_doMultiplyInteger_destInteger_ifFail_ (
		READ_INT, READWRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyInteger_destInteger_ifFail_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_doMultiplyIntegerImmediate_destInteger_ifFail_ (
		IMMEDIATE, READWRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyIntegerImmediate_destInteger_ifFail_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the result doesn't fit in an int.
			return true;
		}
	},

	L2_doMultiplyModThirtyTwoBitInteger_destInteger_ (
		READ_INT, READWRITE_INT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyModThirtyTwoBitInteger_destInteger_();
		}
	},

	L2_doDivideObject_byIntegerConstant_destQuotientObject_destRemainderInteger_ifFail_ (
		READ_POINTER, CONSTANT, WRITE_INT, WRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDivideObject_byIntegerConstant_destQuotientObject_destRemainderInteger_ifFail_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the results don't fit in an int.
			return true;
		}
	},

	L2_doDivideInteger_byIntegerConstant_destQuotientInteger_destRemainderInteger_ifFail_ (
		READ_INT, CONSTANT, WRITE_INT, WRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDivideInteger_byIntegerConstant_destQuotientInteger_destRemainderInteger_ifFail_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the results don't fit in an int.
			return true;
		}
	},

	L2_doDivideInteger_byIntegerImmediate_destQuotientInteger_destRemainderInteger_ifFail_ (
		READ_INT, IMMEDIATE, WRITE_INT, WRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDivideInteger_byIntegerImmediate_destQuotientInteger_destRemainderInteger_ifFail_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps if the results don't fit in an int.
			return true;
		}
	},

	L2_doDivideObject_byObject_destQuotientObject_destRemainderObject_ifZeroDivisor_ (
		READ_POINTER, READ_POINTER, WRITE_POINTER, WRITE_POINTER, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDivideObject_byObject_destQuotientObject_destRemainderObject_ifZeroDivisor_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps for division by zero.
			return true;
		}
	},

	L2_doDivideInteger_byInteger_destQuotientInteger_destRemainderInteger_ifFail_ifZeroDivisor_ (
		READ_INT, READ_INT, WRITE_INT, WRITE_INT, PC, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDivideInteger_byInteger_destQuotientInteger_destRemainderInteger_ifFail_ifZeroDivisor_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps for division by zero.
			return true;
		}
	},

	L2_doJump_ (
		PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_equalsObject_ (
		PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_equalsObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_equalsConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_equalsConstant_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_notEqualsObject_ (
		PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_notEqualsObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_notEqualsConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_notEqualsConstant_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_lessThanObject_ (
		PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_lessThanObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_lessThanConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_lessThanConstant_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_lessOrEqualObject_ (
		PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_lessOrEqualObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_lessOrEqualConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_lessOrEqualConstant_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_greaterThanObject_ (
		PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_greaterThanObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_greaterConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_greaterConstant_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_greaterOrEqualObject_ (
		PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_greaterOrEqualObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_greaterOrEqualConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_greaterOrEqualConstant_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_isKindOfObject_ (
		PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_isKindOfObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_isKindOfConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_isKindOfConstant_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_isNotKindOfObject_ (
		PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_isNotKindOfObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJump_ifObject_isNotKindOfConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_isNotKindOfConstant_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJumpIfInterrupt_ (
		PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJumpIfInterrupt_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doJumpIfNotInterrupt_ (
		PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJumpIfNotInterrupt_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It jumps, which counts as a side effect.
			return true;
		}
	},

	L2_doProcessInterruptNowWithContinuationObject_ (
		READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doProcessInterruptNowWithContinuationObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Don't remove this kind of instruction.
			return true;
		}
	},

	L2_doCreateContinuationSender_function_pc_stackp_size_slots_offset_dest_ (
		READ_POINTER, READ_POINTER, IMMEDIATE, IMMEDIATE, IMMEDIATE, READ_VECTOR, PC, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateContinuationSender_function_pc_stackp_size_slots_offset_dest_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand)instruction.operands[7];
			final L2ObjectRegister destinationRegister =
				destinationOperand.register;
			translator.registerTypeAtPut(
				destinationRegister,
				ContinuationTypeDescriptor.forFunctionType(
					translator.code().functionType()));
			translator.removeConstantForRegister(destinationRegister);
			translator.propagateWriteTo(destinationRegister);
		}
	},

	L2_doSetContinuationObject_slotIndexImmediate_valueObject_ (
		READWRITE_POINTER, IMMEDIATE, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSetContinuationObject_slotIndexImmediate_valueObject_();
		}
	},

	L2_doSetContinuationObject_newPcImmediate_newStackpImmediate_ (
		READWRITE_POINTER, IMMEDIATE, IMMEDIATE)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSetContinuationObject_newPcImmediate_newStackpImmediate_();
		}
	},

	L2_doSend_argumentsVector_ (
		SELECTOR, READ_VECTOR)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSend_argumentsVector_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			translator.restrictPropagationInformationToArchitecturalRegisters();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Never remove a send -- but inlining it might make it go away.
			return true;
		}
	},

	L2_doSendAfterFailedPrimitive_arguments_failureValue_ (
		SELECTOR, READ_VECTOR, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSendAfterFailedPrimitive_arguments_failureValue_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			translator.restrictPropagationInformationToArchitecturalRegisters();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Never remove this send, since it's due to a failed primitive.
			return true;
		}
	},

	L2_doGetType_destObject_ (
		READ_POINTER, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doGetType_destObject_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			final L2ReadPointerOperand sourceOperand =
				(L2ReadPointerOperand)instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand)instruction.operands[1];

			final L2ObjectRegister sourceRegister = sourceOperand.register;
			final L2ObjectRegister destinationRegister =
				destinationOperand.register;
			if (translator.registerHasTypeAt(sourceRegister))
			{
				final AvailObject type =
					translator.registerTypeAt(sourceRegister);
				// Apply the rule of metacovariance. It says that given types T1
				// and T2, T1 <= T2 implies T1 type <= T2 type. It is guaranteed
				// true for all types in Avail.
				final AvailObject meta = InstanceTypeDescriptor.on(type);
				translator.registerTypeAtPut(destinationRegister, meta);
			}
			else
			{
				translator.registerTypeAtPut(destinationRegister, TYPE.o());
			}
			if (translator.registerHasConstantAt(sourceRegister))
			{
				translator.registerConstantAtPut(
					destinationRegister,
					translator.registerConstantAt(sourceRegister).kind());
			}
			else
			{
				translator.removeConstantForRegister(destinationRegister);
			}
			translator.propagateWriteTo(destinationRegister);
		}
	},

	L2_doSuperSend_argumentsVector_argumentTypesVector_ (
		SELECTOR, READ_VECTOR, READ_VECTOR)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSuperSend_argumentsVector_argumentTypesVector_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			translator.restrictPropagationInformationToArchitecturalRegisters();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Never remove a send -- but inlining it might make it go away.
			return true;
		}
	},

	L2_doCreateTupleFromValues_destObject_ (
		READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateTupleFromValues_destObject_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			final L2ReadVectorOperand sourcesOperand =
				(L2ReadVectorOperand)instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand)instruction.operands[1];

			final L2RegisterVector sourceVector = sourcesOperand.vector;
			final int size = sourceVector.registers().size();
			final AvailObject sizeRange =
				IntegerDescriptor.fromInt(size).kind();
			List<AvailObject> types;
			types = new ArrayList<AvailObject>(sourceVector.registers().size());
			for (final L2Register register : sourceVector.registers())
			{
				if (translator.registerHasTypeAt(register))
				{
					types.add(translator.registerTypeAt(register));
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
			translator.registerTypeAtPut(
				destinationOperand.register,
				tupleType);
			translator.propagateWriteTo(destinationOperand.register);
			if (sourceVector.allRegistersAreConstantsIn(translator))
			{
				final List<AvailObject> constants = new ArrayList<AvailObject>(
					sourceVector.registers().size());
				for (final L2Register register : sourceVector.registers())
				{
					constants.add(translator.registerConstantAt(register));
				}
				final AvailObject tuple = TupleDescriptor.fromCollection(constants);
				tuple.makeImmutable();
				assert tuple.isInstanceOf(tupleType);
				translator.registerConstantAtPut(
					destinationOperand.register,
					tuple);
			}
			else
			{
				translator.removeConstantForRegister(
					destinationOperand.register);
			}
		}
	},

	L2_doAttemptPrimitive_arguments_result_failure_ifSuccess_ (
		PRIMITIVE, READ_VECTOR, WRITE_POINTER, WRITE_POINTER, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAttemptPrimitive_arguments_result_failure_ifSuccess_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			final L2WritePointerOperand result =
				(L2WritePointerOperand)instruction.operands[2];
			final L2WritePointerOperand failureValue =
				(L2WritePointerOperand)instruction.operands[3];
			translator.removeTypeForRegister(result.register);
			translator.removeConstantForRegister(result.register);
			translator.propagateWriteTo(result.register);
			translator.removeTypeForRegister(failureValue.register);
			translator.removeConstantForRegister(failureValue.register);
			translator.propagateWriteTo(failureValue.register);
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// It could fail and jump.
			return true;
		}
	},

	L2_doNoFailPrimitive_withArguments_result_ (
		PRIMITIVE, READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doNoFailPrimitive_withArguments_result_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			final L2PrimitiveOperand primitiveOperand =
				(L2PrimitiveOperand)instruction.operands[0];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand)instruction.operands[2];
			translator.removeTypeForRegister(destinationOperand.register);
			translator.removeConstantForRegister(destinationOperand.register);
			translator.propagateWriteTo(destinationOperand.register);

			// We can at least believe what the basic primitive signature says
			// it returns.
			translator.registerTypeAtPut(
				destinationOperand.register,
				primitiveOperand.primitive.blockTypeRestriction().returnType());
		}

		@Override
		public boolean hasSideEffect (final L2Instruction instruction)
		{
			// It depends on the primitive.
			assert instruction.operation == this;
			final L2PrimitiveOperand primitiveOperand =
				(L2PrimitiveOperand)instruction.operands[0];
			final Primitive primitive = primitiveOperand.primitive;
			assert primitive.hasFlag(Flag.CannotFail);
			final boolean mustKeep =
				primitive.hasFlag(Flag.HasSideEffect)
				|| primitive.hasFlag(Flag.CatchException)
				|| primitive.hasFlag(Flag.Invokes)
				|| primitive.hasFlag(Flag.SwitchesContinuation)
				|| primitive.hasFlag(Flag.Unknown);
			return mustKeep;
		}
	},

	L2_doConcatenateTuplesVector_destObject_ (
		READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doConcatenateTuplesVector_destObject_();
		}
	},

	L2_doCreateSetFromValues_destObject_ (
		READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateSetFromValues_destObject_();
		}
	},

	L2_doCreateMapFromKeysVector_valuesVector_destObject_ (
		READ_VECTOR, READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateMapFromKeysVector_valuesVector_destObject_();
		}
	},

	L2_doCreateObjectFromKeysVector_valuesVector_destObject_ (
		READ_VECTOR, READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateObjectFromKeysVector_valuesVector_destObject_();
		}
	},

	L2_doCreateFunctionFromCodeObject_outersVector_destObject_ (
		CONSTANT, READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateFunctionFromCodeObject_outersVector_destObject_();
		}

		@Override
		public void propagateTypesInFor (
			final L2Instruction instruction,
			final L2Translator translator)
		{
			final L2ConstantOperand codeOperand =
				(L2ConstantOperand)instruction.operands[0];
			final L2ReadVectorOperand outersOperand =
				(L2ReadVectorOperand)instruction.operands[1];
			final L2WritePointerOperand destinationOperand =
				(L2WritePointerOperand)instruction.operands[2];
			translator.registerTypeAtPut(
				destinationOperand.register,
				codeOperand.object.functionType());
			translator.propagateWriteTo(destinationOperand.register);
			if (outersOperand.vector.allRegistersAreConstantsIn(translator))
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
						translator.registerConstantAt(outer));
				}
			}
			else
			{
				translator.removeConstantForRegister(
					destinationOperand.register);
			}
		}
	},

	L2_doReturnToContinuationObject_valueObject_ (
		READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doReturnToContinuationObject_valueObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Never remove this.
			return true;
		}
	},

	L2_doExitContinuationObject_valueObject_ (
		READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doExitContinuationObject_valueObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Never remove this.
			return true;
		}
	},

	L2_doResumeContinuationObject_ (
		READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doResumeContinuationObject_();
		}

		@Override
		protected boolean hasSideEffect ()
		{
			// Never remove this.
			return true;
		}
	},

	L2_doMakeImmutableObject_ (
		READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMakeImmutableObject_();
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

	L2_doMakeSubobjectsImmutableInObject_ (
		READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMakeSubobjectsImmutableInObject_();
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
	 * The {@linkplain L2OperandType operand types} that this {@linkplain
	 * L2Operation operation} expects.
	 */
	public final L2OperandType[] operandTypes;

	/**
	 * Answer the {@linkplain L2OperandType operand types} that this {@linkplain
	 * L2Operation operation} expects.
	 *
	 * @return The {@linkplain L2OperandType operand types} that this
	 *         {@linkplain L2Operation operation} expects.
	 */
	public L2OperandType[] operandTypes ()
	{
		return operandTypes;
	}

	/**
	 * Construct a new {@link L2Operation}.
	 * @param operandTypes
	 *        The operand types that this operation expects.
	 */
	private L2Operation (
		final @NotNull L2OperandType ... operandTypes)
	{
		this.operandTypes = operandTypes;
		final String s = this.name();
		int underscoreCount = 0;
		for (int i = 0; i < s.length(); i++)
		{
			if (s.charAt(i) == '_')
			{
				underscoreCount++;
			}
		}
		assert operandTypes.length == underscoreCount - 1
		: "Wrong number of underscores/operands in L2Operation \""
			+ name() + "\"";
	}

	/**
	 * Dispatch to my {@linkplain L2Operation operation}'s implementation within
	 * an {@linkplain L2OperationDispatcher}.
	 *
	 * @param operationDispatcher
	 *        The {@linkplain L2OperationDispatcher dispatcher} to which to
	 *        redirect this message.
	 */
	abstract void dispatch (
		final @NotNull L2OperationDispatcher operationDispatcher);

	/**
	 * @param instruction
	 * @param translator
	 */
	public void propagateTypesInFor (
		final @NotNull L2Instruction instruction,
		final @NotNull L2Translator translator)
	{
		// By default just record that the destinations have been overwritten.
		for (final L2Register destinationRegister
			: instruction.destinationRegisters())
		{
			translator.removeConstantForRegister(destinationRegister);
			translator.removeTypeForRegister(destinationRegister);
			translator.propagateWriteTo(destinationRegister);
		}
	}

	/**
	 * Answer whether an instruction using this operation should be emitted.
	 * For example, labels are place holders and produce no code.
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
	 * registers.  Most operations are computational and don't have side
	 * effects.
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
	 * by writing to its destination registers.  Most operations are
	 * computational and don't have side effects.
	 *
	 * <p>
	 * Most enum instances can override {@link #hasSideEffect()} if {@code
	 * false} isn't good enough, but some might need to know details of the
	 * actual {@link L2Instruction} – in which case they should override this
	 * method instead.
	 *
	 * @param instruction
	 *            The {@code L2Instruction} for which a side effect test is
	 *            being performed.
	 * @return
	 *            Whether that L2Instruction has any side effect.
	 */
	public boolean hasSideEffect (final @NotNull L2Instruction instruction)
	{
		assert instruction.operation == this;
		return hasSideEffect();
	}
}
