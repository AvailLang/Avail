/*
 * P_PushConstant.kt
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
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn
import com.avail.descriptor.BottomTypeDescriptor.bottom
import com.avail.descriptor.CompiledCodeDescriptor
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.primitive.privatehelpers.P_PushConstant.tryToGenerateSpecialPrimitiveInvocation
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper

/**
 * **Primitive:** The first literal is being returned. Extract the first literal
 * from the [compiled&#32;code][CompiledCodeDescriptor] that the interpreter has
 * squirreled away for this purpose.
 *
 * This mechanism relies on [tryToGenerateSpecialPrimitiveInvocation] always
 * producing specialized L2 code – i.e., a constant move.  Note that [CanInline]
 * normally skips making the actual called function available, so we must be
 * careful to expose it for the customized code generator.
 */
object P_PushConstant : Primitive(
	-1, SpecialForm, Private, CanInline, CannotFail)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		val code = interpreter.function!!.code()
		assert(code.primitive() === this)
		return interpreter.primitiveSuccess(code.literalAt(1))
	}

	/** This primitive is suitable for any block signature. */
	override fun privateBlockTypeRestriction(): A_Type = bottom()

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val value = rawFunction.literalAt(1)
		return if (value.equalsNil()) TOP.o() else instanceTypeOrMetaOn(value)
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val constant = rawFunction.literalAt(1)
		callSiteHelper.useAnswer(translator.generator.boxedConstant(constant))
		return true
	}
}
