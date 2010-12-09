/**
 * interpreter/levelTwo/L2Operation.java
 * Copyright (c) 2010, Mark van Gulik.
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

import java.lang.Enum;
import static com.avail.interpreter.levelTwo.L2OperandType.*;

public enum L2Operation
{

	L2_unknownWordcode (0)
	{
		@Override
		void dispatch (L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_unknownWordcode();
		}
	},


	L2_doCreateSimpleContinuationIn_(1, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateSimpleContinuationIn_();
		}
	},


	L2_doInterpretOneInstruction(2)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doInterpretOneInstruction();
		}
	},


	L2_doDecrementCounterAndReoptimizeOnZero(3)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDecrementCounterAndReoptimizeOnZero();
		}
	},


	L2_doTranslateCode(4)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doTranslateCode();
		}
	},


	L2_doMoveFromObject_destObject_(5, READ_POINTER, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMoveFromObject_destObject_();
		}
	},


	L2_doMoveFromConstant_destObject_(6, CONSTANT, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMoveFromConstant_destObject_();
		}
	},


	L2_doMoveFromOuterVariable_ofClosureObject_destObject_(7, IMMEDIATE, READ_POINTER, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMoveFromOuterVariable_ofClosureObject_destObject_();
		}
	},


	L2_doCreateVariableTypeConstant_destObject_(8, IMMEDIATE, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateVariableTypeConstant_destObject_();
		}
	},


	L2_doGetVariable_destObject_(9, READ_POINTER, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doGetVariable_destObject_();
		}
	},


	L2_doGetVariableClearing_destObject_(10, READ_POINTER, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doGetVariableClearing_destObject_();
		}
	},


	L2_doSetVariable_sourceObject_(11, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSetVariable_sourceObject_();
		}
	},


	L2_doClearVariable_(12, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doClearVariable_();
		}
	},


	L2_doClearVariablesVector_(13, READ_VECTOR)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doClearVariablesVector_();
		}
	},


	L2_doClearObject_(14, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doClearObject_();
		}
	},


	L2_doAddIntegerConstant_destObject_(15, CONSTANT, READWRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddIntegerConstant_destObject_();
		}
	},


	L2_doAddIntegerConstant_destInteger_ifFail_(16, CONSTANT, READWRITE_INT, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddIntegerConstant_destInteger_ifFail_();
		}
	},


	L2_doAddObject_destObject_(17, READ_POINTER, READWRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddObject_destObject_();
		}
	},


	L2_doAddInteger_destInteger_ifFail_(18, READ_INT, READWRITE_INT, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddInteger_destInteger_ifFail_();
		}
	},


	L2_doAddIntegerImmediate_destInteger_ifFail_(19, IMMEDIATE, READWRITE_INT, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddIntegerImmediate_destInteger_ifFail_();
		}
	},


	L2_doAddModThirtyTwoBitInteger_destInteger_(20, READ_INT, READWRITE_INT)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAddModThirtyTwoBitInteger_destInteger_();
		}
	},


	L2_doSubtractIntegerConstant_destObject_(21, CONSTANT, READWRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractIntegerConstant_destObject_();
		}
	},


	L2_doSubtractIntegerConstant_destInteger_ifFail_(22, CONSTANT, READWRITE_INT, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractIntegerConstant_destInteger_ifFail_();
		}
	},


	L2_doSubtractObject_destObject_(23, READ_POINTER, READWRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractObject_destObject_();
		}
	},


	L2_doSubtractInteger_destInteger_ifFail_(24, READ_INT, READWRITE_INT, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractInteger_destInteger_ifFail_();
		}
	},


	L2_doSubtractIntegerImmediate_destInteger_ifFail_(25, IMMEDIATE, READWRITE_INT, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractIntegerImmediate_destInteger_ifFail_();
		}
	},


	L2_doSubtractModThirtyTwoBitInteger_destInteger_(26, READ_INT, READWRITE_INT)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSubtractModThirtyTwoBitInteger_destInteger_();
		}
	},


	L2_doMultiplyIntegerConstant_destObject_(27, CONSTANT, READWRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyIntegerConstant_destObject_();
		}
	},


	L2_doMultiplyIntegerConstant_destInteger_ifFail_(28, CONSTANT, READWRITE_INT, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyIntegerConstant_destInteger_ifFail_();
		}
	},


	L2_doMultiplyObject_destObject_(29, READ_POINTER, READWRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyObject_destObject_();
		}
	},


	L2_doMultiplyInteger_destInteger_ifFail_(30, READ_INT, READWRITE_INT, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyInteger_destInteger_ifFail_();
		}
	},


	L2_doMultiplyIntegerImmediate_destInteger_ifFail_(31, IMMEDIATE, READWRITE_INT, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyIntegerImmediate_destInteger_ifFail_();
		}
	},


	L2_doMultiplyModThirtyTwoBitInteger_destInteger_(32, READ_INT, READWRITE_INT)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMultiplyModThirtyTwoBitInteger_destInteger_();
		}
	},


	L2_doDivideObject_byIntegerConstant_destQuotientObject_destRemainderInteger_ifFail_(33, READ_POINTER, CONSTANT, WRITE_INT, WRITE_INT, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDivideObject_byIntegerConstant_destQuotientObject_destRemainderInteger_ifFail_();
		}
	},


	L2_doDivideInteger_byIntegerConstant_destQuotientInteger_destRemainderInteger_ifFail_(34, READ_INT, CONSTANT, WRITE_INT, WRITE_INT, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDivideInteger_byIntegerConstant_destQuotientInteger_destRemainderInteger_ifFail_();
		}
	},


	L2_doDivideInteger_byIntegerImmediate_destQuotientInteger_destRemainderInteger_ifFail_(35, READ_INT, IMMEDIATE, WRITE_INT, WRITE_INT, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDivideInteger_byIntegerImmediate_destQuotientInteger_destRemainderInteger_ifFail_();
		}
	},


	L2_doDivideObject_byObject_destQuotientObject_destRemainderObject_ifZeroDivisor_(36, READ_POINTER, READ_POINTER, WRITE_POINTER, WRITE_POINTER, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDivideObject_byObject_destQuotientObject_destRemainderObject_ifZeroDivisor_();
		}
	},


	L2_doDivideInteger_byInteger_destQuotientInteger_destRemainderInteger_ifFail_ifZeroDivisor_(37, READ_INT, READ_INT, WRITE_INT, WRITE_INT, PC, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doDivideInteger_byInteger_destQuotientInteger_destRemainderInteger_ifFail_ifZeroDivisor_();
		}
	},


	L2_doJump_(38, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_();
		}
	},


	L2_doJump_ifObject_equalsObject_(39, PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_equalsObject_();
		}
	},


	L2_doJump_ifObject_equalsConstant_(40, PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_equalsConstant_();
		}
	},


	L2_doJump_ifObject_notEqualsObject_(41, PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_notEqualsObject_();
		}
	},


	L2_doJump_ifObject_notEqualsConstant_(42, PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_notEqualsConstant_();
		}
	},


	L2_doJump_ifObject_lessThanObject_(43, PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_lessThanObject_();
		}
	},


	L2_doJump_ifObject_lessThanConstant_(44, PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_lessThanConstant_();
		}
	},


	L2_doJump_ifObject_lessOrEqualObject_(45, PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_lessOrEqualObject_();
		}
	},


	L2_doJump_ifObject_lessOrEqualConstant_(46, PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_lessOrEqualConstant_();
		}
	},


	L2_doJump_ifObject_greaterThanObject_(47, PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_greaterThanObject_();
		}
	},


	L2_doJump_ifObject_greaterConstant_(48, PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_greaterConstant_();
		}
	},


	L2_doJump_ifObject_greaterOrEqualObject_(49, PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_greaterOrEqualObject_();
		}
	},


	L2_doJump_ifObject_greaterOrEqualConstant_(50, PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_greaterOrEqualConstant_();
		}
	},


	L2_doJump_ifObject_isKindOfObject_(51, PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_isKindOfObject_();
		}
	},


	L2_doJump_ifObject_isKindOfConstant_(52, PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_isKindOfConstant_();
		}
	},


	L2_doJump_ifObject_isNotKindOfObject_(53, PC, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_isNotKindOfObject_();
		}
	},


	L2_doJump_ifObject_isNotKindOfConstant_(54, PC, READ_POINTER, CONSTANT)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJump_ifObject_isNotKindOfConstant_();
		}
	},


	L2_doJumpIfInterrupt_(55, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJumpIfInterrupt_();
		}
	},


	L2_doJumpIfNotInterrupt_(56, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doJumpIfNotInterrupt_();
		}
	},


	L2_doProcessInterruptNowWithContinuationObject_(57, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doProcessInterruptNowWithContinuationObject_();
		}
	},


	L2_doCreateContinuationWithSenderObject_closureObject_pcInteger_stackpInteger_sizeImmediate_slotsVector_wordcodeOffset_destObject_(58, READ_POINTER, READ_POINTER, IMMEDIATE, IMMEDIATE, IMMEDIATE, READ_VECTOR, PC, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateContinuationWithSenderObject_closureObject_pcInteger_stackpInteger_sizeImmediate_slotsVector_wordcodeOffset_destObject_();
		}
	},


	L2_doSetContinuationObject_slotIndexImmediate_valueObject_(59, READWRITE_POINTER, IMMEDIATE, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSetContinuationObject_slotIndexImmediate_valueObject_();
		}
	},


	L2_doSetContinuationObject_newPcImmediate_newStackpImmediate_(60, READWRITE_POINTER, IMMEDIATE, IMMEDIATE)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSetContinuationObject_newPcImmediate_newStackpImmediate_();
		}
	},


	L2_doExplodeContinuationObject_senderDestObject_closureDestObject_slotsDestVector_(61, READ_POINTER, WRITE_POINTER, WRITE_POINTER, WRITE_VECTOR)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doExplodeContinuationObject_senderDestObject_closureDestObject_slotsDestVector_();
		}
	},


	L2_doSend_argumentsVector_(62, SELECTOR, IMMEDIATE, READ_VECTOR)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSend_argumentsVector_();
		}
	},


	L2_doGetType_destObject_(63, READ_POINTER, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doGetType_destObject_();
		}
	},


	L2_doSuperSend_argumentsVector_argumentTypesVector_(64, SELECTOR, IMMEDIATE, READ_VECTOR, READ_VECTOR)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doSuperSend_argumentsVector_argumentTypesVector_();
		}
	},


	L2_doCreateTupleOfSizeImmediate_valuesVector_destObject_(65, IMMEDIATE, READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateTupleOfSizeImmediate_valuesVector_destObject_();
		}
	},


	L2_doAttemptPrimitive_withArguments_result_ifFail_(66, PRIMITIVE, READ_VECTOR, WRITE_POINTER, PC)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doAttemptPrimitive_withArguments_result_ifFail_();
		}
	},


	L2_doConcatenateTuplesVector_destObject_(67, READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doConcatenateTuplesVector_destObject_();
		}
	},


	L2_doCreateSetOfSizeImmediate_valuesVector_destObject_(68, IMMEDIATE, READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateSetOfSizeImmediate_valuesVector_destObject_();
		}
	},


	L2_doCreateMapOfSizeImmediate_keysVector_valuesVector_destObject_(69, IMMEDIATE, READ_VECTOR, READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateMapOfSizeImmediate_keysVector_valuesVector_destObject_();
		}
	},


	L2_doCreateObjectOfSizeImmediate_keysVector_valuesVector_destObject_(70, IMMEDIATE, READ_VECTOR, READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateObjectOfSizeImmediate_keysVector_valuesVector_destObject_();
		}
	},


	L2_doCreateClosureFromCodeObject_outersVector_destObject_(71, CONSTANT, READ_VECTOR, WRITE_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doCreateClosureFromCodeObject_outersVector_destObject_();
		}
	},


	L2_doReturnToContinuationObject_valueObject_(72, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doReturnToContinuationObject_valueObject_();
		}
	},


	L2_doExitContinuationObject_valueObject_(73, READ_POINTER, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doExitContinuationObject_valueObject_();
		}
	},


	L2_doResumeContinuationObject_(74, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doResumeContinuationObject_();
		}
	},


	L2_doMakeImmutableObject_(75, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMakeImmutableObject_();
		}
	},


	L2_doMakeSubobjectsImmutableInObject_(76, READ_POINTER)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doMakeSubobjectsImmutableInObject_();
		}
	},


	L2_doBreakpoint(77)
	{
		@Override
		void dispatch(L2OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L2_doBreakpoint();
		}
	};


	public final L2OperandType [] operandTypes;

	public L2OperandType [] operandTypes ()
	{
		return operandTypes;
	};


	/**
	 * Construct a new {@link L2Operation}.
	 *
	 * @param ordinalCheck The number that should be this {@linkplain
	 *                     Enum} value's {@linkplain Enum#ordinal() ordinal};
	 *                     fail if it is incorrect.
	 * @param operandTypes The operand types that this operation expects.
	 */
	L2Operation (int ordinalCheck, L2OperandType ... operandTypes)
	{
		assert ordinalCheck == ordinal();
		this.operandTypes = operandTypes;
	};


	/**
	 * Dispatch to my operation's implementation within an {@linkplain
	 * L2OperationDispatcher}.
	 * 
	 * @param operationDispatcher The dispatcher to which to redirect this
	 *                            message.
	 */
	abstract void dispatch (L2OperationDispatcher operationDispatcher);
};
