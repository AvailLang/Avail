/*
 * P_BootstrapDefineSpecialObjectMacroStyler.kt
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
import avail.descriptor.methods.A_Styler
import avail.descriptor.methods.StylerDescriptor.SystemStyle
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.macroOriginalSendNode
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.representation.NilDescriptor
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.interpreter.Primitive
import avail.interpreter.execution.Interpreter

/**
 * **Primitive**: Apply bootstrap styling to a phrase responsible for special
 * object binding.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_BootstrapDefineSpecialObjectMacroStyler :
	Primitive(
		2,
		Flag.CanInline,
		Flag.Bootstrap,
		Flag.ReadsFromHiddenGlobalState,
		Flag.WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val sendPhrase: A_Phrase = interpreter.argument(0)
//		val transformedPhrase: A_Phrase = interpreter.argument(1)

		val loader = interpreter.fiber().availLoader
			?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}

		loader.styleTokens(sendPhrase.tokens, SystemStyle.SPECIAL_OBJECT)

		val namePhrase = sendPhrase.argumentsListNode.expressionAt(1)
		val nameLiteralSend = when
		{
			namePhrase.isMacroSubstitutionNode ->
				namePhrase.macroOriginalSendNode
			else -> namePhrase
		}
		val nameLiteralSendArgs = nameLiteralSend.argumentsListNode
		if (nameLiteralSendArgs.expressionsSize == 1)
		{
			// The first argument of the definition is a one-argument send.
			// Keep drilling to find the literal token to style, if any.
			var nameLiteralArg = nameLiteralSendArgs.expressionAt(1)
			if (nameLiteralArg.isMacroSubstitutionNode)
				nameLiteralArg = nameLiteralArg.macroOriginalSendNode
			if (nameLiteralArg.phraseKindIsUnder(LITERAL_PHRASE))
			{
				loader.styleMethodName(nameLiteralArg.token)
			}
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil)
	}

	override fun privateFailureVariableType(): A_Type =
		AbstractEnumerationTypeDescriptor.enumerationWith(
			SetDescriptor.set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION))

	override fun privateBlockTypeRestriction(): A_Type =
		A_Styler.stylerFunctionType
}
