/*
 * P_BootstrapStatementStyler.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.interpreter.primitive.style

import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.methods.A_Styler.Companion.stylerFunctionType
import avail.descriptor.methods.StylerDescriptor.SystemStyle
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.ReadsFromHiddenGlobalState
import avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Apply bootstrap styling to a phrase responsible for some sort
 * of statement.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapStatementStyler :
	Primitive(
		2,
		CanInline,
		Bootstrap,
		ReadsFromHiddenGlobalState,
		WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val optionalSendPhrase: A_Tuple = interpreter.argument(0)
		val transformedPhrase: A_Phrase = interpreter.argument(1)

		val loader = interpreter.fiber().availLoader
			?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}

		val sendPhrase = when (optionalSendPhrase.tupleSize)
		{
			0 -> transformedPhrase
			else -> optionalSendPhrase.tupleAt(1)
		}

		loader.styleTokens(sendPhrase.tokens, SystemStyle.STATEMENT)
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION))

	override fun privateBlockTypeRestriction(): A_Type = stylerFunctionType
}
