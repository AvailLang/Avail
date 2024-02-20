/*
 * P_ExitContinuationIf.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.interpreter.primitive.controlflow

import avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.functions.A_Continuation.Companion.caller
import avail.descriptor.functions.A_Continuation.Companion.function
import avail.descriptor.functions.A_Continuation.Companion.levelTwoChunk
import avail.descriptor.functions.A_Continuation.Companion.levelTwoOffset
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.ContinuationDescriptor
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationTypeForFunctionType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailErrorCode.E_CONTINUATION_EXPECTED_STRONGER_TYPE
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CanSwitchContinuations
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.Result.CONTINUATION_CHANGED
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.L2_RETURN
import avail.optimizer.L1Translator.CallSiteHelper

/**
 * **Primitive:** Exit the given [continuation][ContinuationDescriptor]
 * (returning nil to its caller), but only if the provided boolean is true.
 * Otherwise do nothing.
 */
@Suppress("unused")
object P_ExitContinuationIf : Primitive(
	2,
	CanInline,
	CanSwitchContinuations,
	CannotFail)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val (continuation, condition) = interpreter.argsBuffer

		if (!condition.extractBoolean)
		{
			return interpreter.primitiveSuccess(nil)
		}

		val caller = continuation.caller()
		interpreter.setReifiedContinuation(caller)
		if (caller.isNil)
		{
			interpreter.function = null
			interpreter.chunk = null
			interpreter.offset = Int.MAX_VALUE
			interpreter.returnNow = true
		}
		else
		{
			interpreter.function = caller.function()
			interpreter.chunk = caller.levelTwoChunk()
			interpreter.offset = caller.levelTwoOffset()
			interpreter.returnNow = false
		}
		interpreter.setLatestResult(nil)
		return CONTINUATION_CHANGED
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				continuationTypeForFunctionType(
					functionTypeReturning(TOP.o)),
				booleanType),
			TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_CONTINUATION_EXPECTED_STRONGER_TYPE))

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val (continuationReg, conditionReg) = arguments

		// Check for the common case that the continuation was created for this
		// very frame.
		val translator = callSiteHelper.translator
		val generator = translator.generator
		val manifest = generator.currentManifest
		val synonym = manifest.semanticValueToSynonym(
			continuationReg.semanticValue())
		val label = generator.topFrame.label()
		if (manifest.hasSemanticValue(label) &&
			manifest.semanticValueToSynonym(label) == synonym)
		{
			// We're conditionally exiting the current frame.
			val exit = generator.createBasicBlock("Exit")
			val noExit = generator.createBasicBlock("Don't exit")
			generator.jumpIfEqualsConstant(
				generator.readBoxed(
					conditionReg.originalBoxedWriteSkippingMoves()),
				trueObject,
				exit,
				noExit)
			generator.startBlock(exit)
			generator.addInstruction(
				L2_RETURN,
				generator.boxedConstant(nil))
			generator.startBlock(noExit)
			callSiteHelper.useAnswer(translator.generator.boxedConstant(nil))
			return true
		}
		return false
	}
}
