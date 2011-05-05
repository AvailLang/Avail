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

import com.avail.interpreter.levelTwo.instruction.L2Instruction;

/**
 * {@code L2OperationDispatcher} enumerates the responsibilities that must be
 * satisfied in order to process the {@linkplain L2Instruction level two Avail
 * instruction set}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
interface L2OperationDispatcher
{
	/**
	 * Process an
	 * {@link L2Operation#L2_unknownWordcode}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_unknownWordcode ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doPrepareNewFrame}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doPrepareNewFrame ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doInterpretOneInstructionAndBranchBackIfNoInterrupt}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doInterpretOneInstructionAndBranchBackIfNoInterrupt ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doDecrementCounterAndReoptimizeOnZero}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doDecrementCounterAndReoptimizeOnZero ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doMoveFromObject_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doMoveFromObject_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doMoveFromConstant_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doMoveFromConstant_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doMoveFromOuterVariable_ofClosureObject_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doMoveFromOuterVariable_ofClosureObject_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doCreateVariableTypeConstant_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doCreateVariableTypeConstant_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doGetVariable_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doGetVariable_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doGetVariableClearing_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doGetVariableClearing_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doSetVariable_sourceObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doSetVariable_sourceObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doClearVariable_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doClearVariable_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doClearVariablesVector_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doClearVariablesVector_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doClearObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doClearObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doAddIntegerConstant_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doAddIntegerConstant_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doAddIntegerConstant_destInteger_ifFail_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doAddIntegerConstant_destInteger_ifFail_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doAddObject_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doAddObject_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doAddInteger_destInteger_ifFail_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doAddInteger_destInteger_ifFail_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doAddIntegerImmediate_destInteger_ifFail_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doAddIntegerImmediate_destInteger_ifFail_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doAddModThirtyTwoBitInteger_destInteger_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doAddModThirtyTwoBitInteger_destInteger_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doSubtractIntegerConstant_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doSubtractIntegerConstant_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doSubtractIntegerConstant_destInteger_ifFail_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doSubtractIntegerConstant_destInteger_ifFail_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doSubtractObject_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doSubtractObject_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doSubtractInteger_destInteger_ifFail_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doSubtractInteger_destInteger_ifFail_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doSubtractIntegerImmediate_destInteger_ifFail_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doSubtractIntegerImmediate_destInteger_ifFail_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doSubtractModThirtyTwoBitInteger_destInteger_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doSubtractModThirtyTwoBitInteger_destInteger_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doMultiplyIntegerConstant_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doMultiplyIntegerConstant_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doMultiplyIntegerConstant_destInteger_ifFail_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doMultiplyIntegerConstant_destInteger_ifFail_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doMultiplyObject_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doMultiplyObject_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doMultiplyInteger_destInteger_ifFail_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doMultiplyInteger_destInteger_ifFail_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doMultiplyIntegerImmediate_destInteger_ifFail_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doMultiplyIntegerImmediate_destInteger_ifFail_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doMultiplyModThirtyTwoBitInteger_destInteger_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doMultiplyModThirtyTwoBitInteger_destInteger_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doDivideObject_byIntegerConstant_destQuotientObject_destRemainderInteger_ifFail_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doDivideObject_byIntegerConstant_destQuotientObject_destRemainderInteger_ifFail_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doDivideInteger_byIntegerConstant_destQuotientInteger_destRemainderInteger_ifFail_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doDivideInteger_byIntegerConstant_destQuotientInteger_destRemainderInteger_ifFail_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doDivideInteger_byIntegerImmediate_destQuotientInteger_destRemainderInteger_ifFail_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doDivideInteger_byIntegerImmediate_destQuotientInteger_destRemainderInteger_ifFail_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doDivideObject_byObject_destQuotientObject_destRemainderObject_ifZeroDivisor_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doDivideObject_byObject_destQuotientObject_destRemainderObject_ifZeroDivisor_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doDivideInteger_byInteger_destQuotientInteger_destRemainderInteger_ifFail_ifZeroDivisor_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doDivideInteger_byInteger_destQuotientInteger_destRemainderInteger_ifFail_ifZeroDivisor_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_equalsObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_equalsObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_equalsConstant_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_equalsConstant_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_notEqualsObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_notEqualsObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_notEqualsConstant_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_notEqualsConstant_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_lessThanObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_lessThanObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_lessThanConstant_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_lessThanConstant_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_lessOrEqualObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_lessOrEqualObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_lessOrEqualConstant_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_lessOrEqualConstant_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_greaterThanObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_greaterThanObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_greaterConstant_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_greaterConstant_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_greaterOrEqualObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_greaterOrEqualObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_greaterOrEqualConstant_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_greaterOrEqualConstant_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_isKindOfObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_isKindOfObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_isKindOfConstant_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_isKindOfConstant_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_isNotKindOfObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_isNotKindOfObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJump_ifObject_isNotKindOfConstant_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJump_ifObject_isNotKindOfConstant_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJumpIfInterrupt_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJumpIfInterrupt_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doJumpIfNotInterrupt_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doJumpIfNotInterrupt_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doProcessInterruptNowWithContinuationObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doProcessInterruptNowWithContinuationObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doCreateContinuationWithSenderObject_closureObject_pcInteger_stackpInteger_sizeImmediate_slotsVector_wordcodeOffset_destObject_
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doCreateContinuationWithSenderObject_closureObject_pcInteger_stackpInteger_sizeImmediate_slotsVector_wordcodeOffset_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doSetContinuationObject_slotIndexImmediate_valueObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doSetContinuationObject_slotIndexImmediate_valueObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doSetContinuationObject_newPcImmediate_newStackpImmediate_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doSetContinuationObject_newPcImmediate_newStackpImmediate_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doExplodeContinuationObject}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doExplodeContinuationObject ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doSend_argumentsVector_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doSend_argumentsVector_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doSend_argumentsVector_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doSendAfterFailedPrimitive_argumentsVector_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doGetType_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doGetType_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doSuperSend_argumentsVector_argumentTypesVector_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doSuperSend_argumentsVector_argumentTypesVector_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doCreateTupleOfSizeImmediate_valuesVector_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doCreateTupleOfSizeImmediate_valuesVector_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doConcatenateTuplesVector_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doConcatenateTuplesVector_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doCreateSetOfSizeImmediate_valuesVector_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doCreateSetOfSizeImmediate_valuesVector_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doCreateMapOfSizeImmediate_keysVector_valuesVector_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doCreateMapOfSizeImmediate_keysVector_valuesVector_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doCreateObjectOfSizeImmediate_keysVector_valuesVector_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doCreateObjectOfSizeImmediate_keysVector_valuesVector_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doCreateClosureFromCodeObject_outersVector_destObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doCreateClosureFromCodeObject_outersVector_destObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doReturnToContinuationObject_valueObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doReturnToContinuationObject_valueObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doExitContinuationObject_valueObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doExitContinuationObject_valueObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doResumeContinuationObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doResumeContinuationObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doMakeImmutableObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doMakeImmutableObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doMakeSubobjectsImmutableInObject_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doMakeSubobjectsImmutableInObject_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_doAttemptPrimitive_withArguments_result_ifFail_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_doAttemptPrimitive_withArguments_result_failure_ifFail_ ();
}
