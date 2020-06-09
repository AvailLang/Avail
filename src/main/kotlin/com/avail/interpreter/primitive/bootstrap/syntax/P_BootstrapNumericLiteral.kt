/*
 * P_BootstrapNumericLiteral.kt
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
package com.avail.interpreter.primitive.bootstrap.syntax

import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.phrases.LiteralPhraseDescriptor.Companion.literalNodeFromToken
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.literalTokenType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.descriptor.types.TypeDescriptor.Types.NUMBER
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Create a non-negative numeric literal phrase from a
 * non-negative numeric literal constant token (already wrapped as a literal
 * phrase).  This is a bootstrapped macro because not all subsets of the core
 * Avail syntax should allow non-negative numeric literal phrases.
 */
@Suppress("unused")
object P_BootstrapNumericLiteral :
	Primitive(1, CanInline, CannotFail, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val numericTokenLiteral = interpreter.argument(0)

		val outerToken = numericTokenLiteral.token()
		val innerToken = outerToken.literal()
		val numericLiteral = literalNodeFromToken(innerToken)
		return interpreter.primitiveSuccess(numericLiteral)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				LITERAL_PHRASE.create(literalTokenType(NUMBER.o()))),
			LITERAL_PHRASE.create(NUMBER.o()))
}
