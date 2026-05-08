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
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwoSimple.L2SimpleTranslator
import avail.interpreter.levelTwoSimple.L2Simple_MoveConstant
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.Private
import avail.interpreter.primitive.Primitive.Flag.SpecialForm
import avail.interpreter.primitive.PrimitiveN
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator

/**
 * **Primitive:** The sole outer value is being returned.
 */
@Suppress("unused")
object P_PushLastOuter : PrimitiveN(
	-1, SpecialForm, Private, CanInline, CannotFail)
{
	override fun Interpreter.attemptN(
		args: Array<AvailObject>
	): A_BasicObject?
	{
		val function = function!!
		assert(function.code().codePrimitive() === P_PushLastOuter)
		return function.outerVarAt(1)
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type = rawFunction!!.outerTypeAt(1)

	/**
	 * This primitive is suitable for any block signature, although really the
	 * primitive could only be applied if the function returns any.
	 */
	override fun privateBlockTypeRestriction(): A_Type = bottom

	override fun checkSpecialForm(
		numArgs: Int,
		literals: A_Tuple
	): Boolean = true

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val constantFunction = functionToCallReg.constantOrNull

		// Check for the rare case that the exact function is known (noting that
		// it has an outer).
		if (constantFunction !== null)
		{
			callSiteHelper.useAnswer(
				boxedConstant(constantFunction.outerVarAt(1)),
				false)
			return true
		}

		// See if we can find the instruction that created the function, using
		// the original register that provided the value for the outer.  This
		// should allow us to skip the creation of the function.
		val functionCreationInstruction =
			functionToCallReg.definitionSkippingMoves(currentManifest)
		val returnType = functionToCallReg.type().returnType
		val outerReg = functionCreationInstruction.run {
			extractFunctionOuter(functionToCallReg, 1, returnType)
		}
		callSiteHelper.useAnswer(outerReg, false)
		return true
	}

	override fun L2SimpleTranslator.attemptToGenerateSimpleInvocation(
		functionIfKnown: A_Function?,
		rawFunction: A_RawFunction,
		argRestrictions: List<TypeRestriction>,
		expectedType: A_Type
	): TypeRestriction?
	{
		if (functionIfKnown === null)
			return null
		val constant = functionIfKnown.outerVarAt(1)
		+L2Simple_MoveConstant(constant, stackp)
		return boxedRestrictionForConstant(constant)
	}
}
