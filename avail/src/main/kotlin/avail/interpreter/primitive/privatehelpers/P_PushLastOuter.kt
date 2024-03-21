/*
 * P_PushLastOuter.kt
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
package avail.interpreter.primitive.privatehelpers

import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.outerTypeAt
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.Flag.Private
import avail.interpreter.Primitive.Flag.SpecialForm
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwoSimple.L2SimpleTranslator
import avail.interpreter.levelTwoSimple.L2Simple_MoveConstant
import avail.optimizer.L1Translator.CallSiteHelper

/**
 * **Primitive:** The sole outer value is being returned.
 */
@Suppress("unused")
object P_PushLastOuter : Primitive(
	-1, SpecialForm, Private, CanInline, CannotFail)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		val function = interpreter.function!!
		assert(function.code().codePrimitive() === this)
		return interpreter.primitiveSuccess(function.outerVarAt(1))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>
	): A_Type = rawFunction.outerTypeAt(1)

	/**
	 * This primitive is suitable for any block signature, although really the
	 * primitive could only be applied if the function returns any.
	 */
	override fun privateBlockTypeRestriction(): A_Type = bottom

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val constantFunction = functionToCallReg.constantOrNull()

		// Check for the rare case that the exact function is known (noting that
		// it has an outer).
		val translator = callSiteHelper.translator
		if (constantFunction !== null)
		{
			callSiteHelper.useAnswer(
				translator.generator.boxedConstant(
					constantFunction.outerVarAt(1)))
			return true
		}

		// See if we can find the instruction that created the function, using
		// the original register that provided the value for the outer.  This
		// should allow us to skip the creation of the function.
		val functionCreationInstruction =
			functionToCallReg.definitionSkippingMoves()
		val returnType = functionToCallReg.type().returnType
		val outerReg = functionCreationInstruction.extractFunctionOuter(
			functionToCallReg,
			1,
			returnType,
			translator.generator)
		callSiteHelper.useAnswer(outerReg)
		return true
	}

	override fun attemptToGenerateSimpleInvocation(
		simpleTranslator: L2SimpleTranslator,
		functionIfKnown: A_Function?,
		rawFunction: A_RawFunction,
		argRestrictions: List<TypeRestriction>,
		expectedType: A_Type
	): TypeRestriction?
	{
		if (functionIfKnown === null)
		{
			return super.attemptToGenerateSimpleInvocation(
				simpleTranslator,
				null,
				rawFunction,
				argRestrictions,
				expectedType)
		}
		val constant = functionIfKnown.outerVarAt(1)
		simpleTranslator.add(
			L2Simple_MoveConstant(constant, simpleTranslator.stackp))
		return boxedRestrictionForConstant(constant)
	}
}
