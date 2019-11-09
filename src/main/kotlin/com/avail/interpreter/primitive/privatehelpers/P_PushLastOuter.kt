/*
 * P_PushLastOuter.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.privatehelpers

import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Type
import com.avail.descriptor.BottomTypeDescriptor.bottom
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import com.avail.utility.Nulls.stripNull

/**
 * **Primitive:** The sole outer value is being returned.
 */
object P_PushLastOuter : Primitive(
	-1, SpecialForm, Private, CanInline, CannotFail)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		val function = stripNull(interpreter.function)
		assert(function.code().primitive() === this)
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
	override fun privateBlockTypeRestriction(): A_Type = bottom()

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val constantFunction = functionToCallReg.constantOrNull()

		// Check for the rare case that the exact function is known (noting that
		// it has an outer).
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
			functionToCallReg.definitionSkippingMoves(true)
		val returnType = functionToCallReg.type().returnType()
		val outerReg = functionCreationInstruction.operation()
			.extractFunctionOuter(
				functionCreationInstruction,
				functionToCallReg,
				1,
				returnType,
				translator.generator)
		callSiteHelper.useAnswer(outerReg)
		return true
	}
}
