/*
 * P_PushArgument2.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.types.A_Type
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.Flag.Private
import avail.interpreter.Primitive.Flag.SpecialForm
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.optimizer.L1Translator
import avail.optimizer.L1Translator.CallSiteHelper

/**
 * **Primitive:** The second argument is being returned.
 */
@Suppress("unused")
object P_PushArgument2 : Primitive(
	-1, SpecialForm, Private, CanInline, CannotFail)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		val argument = interpreter.argument(1)
		return interpreter.primitiveSuccess(argument)
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		assert(argumentTypes.size >= 2)
		return argumentTypes[1]
	}

	/**
	 * This primitive is suitable for any two-or-more-argument function. It may
	 * seem strange that ⊥ is the return type, but that's to allow functions
	 * like `[x : set, y : map | y]` to type-check correctly!  The argument y
	 * has type map, so the last expression does also.  If we made this
	 * primitive say the result must be any, we would be illegally strengthening
	 * it by appending a return type declaration like ": map". However, the L2
	 * translator will have to ignore the primitive block type restriction for
	 * this particular primitive.
	 */
	override fun privateBlockTypeRestriction(): A_Type = bottom

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		// The value is available in the second argument register.  Doesn't even
		// need a move. The translator deals with strengthening separately,
		// through the call return type checks.
		callSiteHelper.useAnswer(arguments[1])
		return true
	}
}
