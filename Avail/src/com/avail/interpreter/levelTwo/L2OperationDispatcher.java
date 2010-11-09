/**
 * interpreter/levelTwo/L2OperationDispatcher.java
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


interface L2OperationDispatcher
{

	void L2_unknownWordcode ();
	void L2_doCreateSimpleContinuationIn_ ();
	void L2_doInterpretOneInstruction ();
	void L2_doDecrementCounterAndReoptimizeOnZero ();
	void L2_doTranslateCode ();
	void L2_doMoveFromObject_destObject_ ();
	void L2_doMoveFromConstant_destObject_ ();
	void L2_doMoveFromOuterVariable_ofClosureObject_destObject_ ();
	void L2_doCreateVariableTypeConstant_destObject_ ();
	void L2_doGetVariable_destObject_ ();
	void L2_doGetVariableClearing_destObject_ ();
	void L2_doSetVariable_sourceObject_ ();
	void L2_doClearVariable_ ();
	void L2_doClearVariablesVector_ ();
	void L2_doClearObject_ ();
	void L2_doAddIntegerConstant_destObject_ ();
	void L2_doAddIntegerConstant_destInteger_ifFail_ ();
	void L2_doAddObject_destObject_ ();
	void L2_doAddInteger_destInteger_ifFail_ ();
	void L2_doAddIntegerImmediate_destInteger_ifFail_ ();
	void L2_doAddModThirtyTwoBitInteger_destInteger_ ();
	void L2_doSubtractIntegerConstant_destObject_ ();
	void L2_doSubtractIntegerConstant_destInteger_ifFail_ ();
	void L2_doSubtractObject_destObject_ ();
	void L2_doSubtractInteger_destInteger_ifFail_ ();
	void L2_doSubtractIntegerImmediate_destInteger_ifFail_ ();
	void L2_doSubtractModThirtyTwoBitInteger_destInteger_ ();
	void L2_doMultiplyIntegerConstant_destObject_ ();
	void L2_doMultiplyIntegerConstant_destInteger_ifFail_ ();
	void L2_doMultiplyObject_destObject_ ();
	void L2_doMultiplyInteger_destInteger_ifFail_ ();
	void L2_doMultiplyIntegerImmediate_destInteger_ifFail_ ();
	void L2_doMultiplyModThirtyTwoBitInteger_destInteger_ ();
	void L2_doDivideObject_byIntegerConstant_destQuotientObject_destRemainderInteger_ifFail_ ();
	void L2_doDivideInteger_byIntegerConstant_destQuotientInteger_destRemainderInteger_ifFail_ ();
	void L2_doDivideInteger_byIntegerImmediate_destQuotientInteger_destRemainderInteger_ifFail_ ();
	void L2_doDivideObject_byObject_destQuotientObject_destRemainderObject_ifZeroDivisor_ ();
	void L2_doDivideInteger_byInteger_destQuotientInteger_destRemainderInteger_ifFail_ifZeroDivisor_ ();
	void L2_doJump_ ();
	void L2_doJump_ifObject_equalsObject_ ();
	void L2_doJump_ifObject_equalsConstant_ ();
	void L2_doJump_ifObject_notEqualsObject_ ();
	void L2_doJump_ifObject_notEqualsConstant_ ();
	void L2_doJump_ifObject_lessThanObject_ ();
	void L2_doJump_ifObject_lessThanConstant_ ();
	void L2_doJump_ifObject_lessOrEqualObject_ ();
	void L2_doJump_ifObject_lessOrEqualConstant_ ();
	void L2_doJump_ifObject_greaterThanObject_ ();
	void L2_doJump_ifObject_greaterConstant_ ();
	void L2_doJump_ifObject_greaterOrEqualObject_ ();
	void L2_doJump_ifObject_greaterOrEqualConstant_ ();
	void L2_doJump_ifObject_isKindOfObject_ ();
	void L2_doJump_ifObject_isKindOfConstant_ ();
	void L2_doJump_ifObject_isNotKindOfObject_ ();
	void L2_doJump_ifObject_isNotKindOfConstant_ ();
	void L2_doJumpIfInterrupt_ ();
	void L2_doJumpIfNotInterrupt_ ();
	void L2_doProcessInterruptNowWithContinuationObject_ ();
	void L2_doCreateContinuationWithSenderObject_closureObject_pcInteger_stackpInteger_sizeImmediate_slotsVector_wordcodeOffset_destObject_ ();
	void L2_doSetContinuationObject_slotIndexImmediate_valueObject_ ();
	void L2_doSetContinuationObject_newPcImmediate_newStackpImmediate_ ();
	void L2_doExplodeContinuationObject_senderDestObject_closureDestObject_slotsDestVector_ ();
	void L2_doSend_argumentsVector_ ();
	void L2_doGetType_destObject_ ();
	void L2_doSuperSend_argumentsVector_argumentTypesVector_ ();
	void L2_doCreateTupleOfSizeImmediate_valuesVector_destObject_ ();
	void L2_doConvertTupleObject_toListObject_ ();
	void L2_doConcatenateTuplesVector_destObject_ ();
	void L2_doCreateSetOfSizeImmediate_valuesVector_destObject_ ();
	void L2_doCreateMapOfSizeImmediate_keysVector_valuesVector_destObject_ ();
	void L2_doCreateObjectOfSizeImmediate_keysVector_valuesVector_destObject_ ();
	void L2_doCreateClosureFromCodeObject_outersVector_destObject_ ();
	void L2_doReturnToContinuationObject_valueObject_ ();
	void L2_doExitContinuationObject_valueObject_ ();
	void L2_doResumeContinuationObject_ ();
	void L2_doMakeImmutableObject_ ();
	void L2_doMakeSubobjectsImmutableInObject_ ();
	void L2_doBreakpoint ();
	void L2_doAttemptPrimitive_withArguments_result_ifFail_ ();

}
