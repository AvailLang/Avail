/*
 * P_ExitContinuationIf.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
*
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.interpreter.primitive.controlflow

import com.avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import com.avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.ContinuationDescriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.ContinuationTypeDescriptor.continuationTypeForFunctionType
import com.avail.descriptor.types.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.FunctionTypeDescriptor.functionTypeReturning
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_CONTINUATION_EXPECTED_STRONGER_TYPE
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CanSwitchContinuations
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.Primitive.Result.CONTINUATION_CHANGED
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operation.L2_RETURN
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper

/**
 * **Primitive:** Exit the given [continuation][ContinuationDescriptor]
 * (returning nil to its caller), but only if the provided boolean is true.
 * Otherwise do nothing.
 */
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

		if (!condition.extractBoolean())
		{
			return interpreter.primitiveSuccess(nil)
		}

		interpreter.setReifiedContinuation(continuation.caller())
		interpreter.function = null
		interpreter.chunk = null
		interpreter.offset = Integer.MAX_VALUE
		interpreter.returnNow = true
		interpreter.setLatestResult(nil)
		return CONTINUATION_CHANGED
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				continuationTypeForFunctionType(
					functionTypeReturning(TOP.o())),
				booleanType()),
			TOP.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_CONTINUATION_EXPECTED_STRONGER_TYPE))

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val (continuationReg, conditionReg) = arguments

		// Check for the common case that the continuation was created for this
		// very frame.
		val generator = translator.generator
		val manifest = generator.currentManifest()
		val synonym = manifest.semanticValueToSynonym(
			continuationReg.semanticValue())
		val label = generator.topFrame.label()
		if (manifest.hasSemanticValue(label) &&
			manifest.semanticValueToSynonym(label) == synonym)
		{
			// We're conditionally exiting the current frame.
			val exit = generator.createBasicBlock("Exit")
			val dontExit = generator.createBasicBlock("Don't exit")
			translator.jumpIfEqualsConstant(
				generator.readBoxed(
					conditionReg.originalBoxedWriteSkippingMoves(true)),
				trueObject(),
				exit,
				dontExit)
			generator.startBlock(exit)
			generator.addInstruction(
				L2_RETURN,
				generator.boxedConstant(nil))
			generator.startBlock(dontExit)
			callSiteHelper.useAnswer(translator.generator.boxedConstant(nil))
			return true
		}
		return false
	}
}
