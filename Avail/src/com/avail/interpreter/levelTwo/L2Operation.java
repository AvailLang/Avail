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
	},

	L2_doDecrementCounterAndReoptimizeOnZero ()
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDecrementCounterAndReoptimizeOnZero();
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
	},

	L2_doClearVariable_ (
		READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doClearVariable_();
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
	},

	L2_doAddObject_destObject_ (
		READ_POINTER, READWRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddObject_destObject_();
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
	},

	L2_doAddIntegerImmediate_destInteger_ifFail_ (
		IMMEDIATE, READWRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddIntegerImmediate_destInteger_ifFail_();
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
	},

	L2_doSubtractObject_destObject_ (
		READ_POINTER, READWRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractObject_destObject_();
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
	},

	L2_doSubtractIntegerImmediate_destInteger_ifFail_ (
		IMMEDIATE, READWRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractIntegerImmediate_destInteger_ifFail_();
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
	},

	L2_doMultiplyIntegerConstant_destInteger_ifFail_ (
		CONSTANT, READWRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyIntegerConstant_destInteger_ifFail_();
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
	},

	L2_doMultiplyInteger_destInteger_ifFail_ (
		READ_INT, READWRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyInteger_destInteger_ifFail_();
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
	},

	L2_doDivideInteger_byIntegerConstant_destQuotientInteger_destRemainderInteger_ifFail_ (
		READ_INT, CONSTANT, WRITE_INT, WRITE_INT, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDivideInteger_byIntegerConstant_destQuotientInteger_destRemainderInteger_ifFail_();
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
	},

	L2_doDivideObject_byObject_destQuotientObject_destRemainderObject_ifZeroDivisor_ (
		READ_POINTER, READ_POINTER, WRITE_POINTER, WRITE_POINTER, PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDivideObject_byObject_destQuotientObject_destRemainderObject_ifZeroDivisor_();
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
	},

	L2_doJump_ (
		PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_();
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
	},

	L2_doJump_ifObject_equalsConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_equalsConstant_();
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
	},

	L2_doJump_ifObject_notEqualsConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_notEqualsConstant_();
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
	},

	L2_doJump_ifObject_lessThanConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_lessThanConstant_();
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
	},

	L2_doJump_ifObject_lessOrEqualConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_lessOrEqualConstant_();
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
	},

	L2_doJump_ifObject_greaterConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_greaterConstant_();
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
	},

	L2_doJump_ifObject_greaterOrEqualConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_greaterOrEqualConstant_();
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
	},

	L2_doJump_ifObject_isKindOfConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_isKindOfConstant_();
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
	},

	L2_doJump_ifObject_isNotKindOfConstant_ (
		PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_isNotKindOfConstant_();
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
	},

	L2_doJumpIfNotInterrupt_ (
		PC)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJumpIfNotInterrupt_();
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
				final AvailObject meta = type.kind();
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
			translator.removeTypeForRegister(failureValue.register);
			translator.removeConstantForRegister(failureValue.register);
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

			// We can at least believe what the basic primitive signature says
			// it returns.
			translator.registerTypeAtPut(
				destinationOperand.register,
				primitiveOperand.primitive.blockTypeRestriction().returnType());
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

	L2_doCreateSetOfSizeImmediate_valuesVector_destObject_ (
		IMMEDIATE, READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateSetOfSizeImmediate_valuesVector_destObject_();
		}
	},

	L2_doCreateMapOfSizeImmediate_keysVector_valuesVector_destObject_ (
		IMMEDIATE, READ_VECTOR, READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateMapOfSizeImmediate_keysVector_valuesVector_destObject_();
		}
	},

	L2_doCreateObjectOfSizeImmediate_keysVector_valuesVector_destObject_ (
		IMMEDIATE, READ_VECTOR, READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateObjectOfSizeImmediate_keysVector_valuesVector_destObject_();
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
	},

	L2_doExitContinuationObject_valueObject_ (
		READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doExitContinuationObject_valueObject_();
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
	},

	L2_doMakeImmutableObject_ (
		READ_POINTER)
	{
		@Override
		void dispatch (final @NotNull L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMakeImmutableObject_();
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
		// Do nothing by default.
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
}
