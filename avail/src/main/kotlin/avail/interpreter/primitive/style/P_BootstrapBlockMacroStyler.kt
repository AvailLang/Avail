/*
 * P_BootstrapBlockMacroStyler.kt
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

package avail.interpreter.primitive.style

import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.fiber.A_Fiber.Companion.canStyle
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.methods.A_Styler.Companion.stylerFunctionType
import avail.descriptor.methods.StylerDescriptor.SystemStyle
import avail.descriptor.methods.StylerDescriptor.SystemStyle.BLOCK
import avail.descriptor.phrases.A_Phrase.Companion.allTokens
import avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.phrases.BlockPhraseDescriptor
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.A_Token.Companion.pastEnd
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.component1
import avail.descriptor.tuples.A_Tuple.Companion.component2
import avail.descriptor.tuples.A_Tuple.Companion.component3
import avail.descriptor.tuples.A_Tuple.Companion.component4
import avail.descriptor.tuples.A_Tuple.Companion.component5
import avail.descriptor.tuples.A_Tuple.Companion.component6
import avail.descriptor.tuples.A_Tuple.Companion.component7
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.exceptions.AvailErrorCode.E_CANNOT_STYLE
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.ReadsFromHiddenGlobalState
import avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.bootstrap.syntax.P_BootstrapBlockMacro

/**
 * The [P_BootstrapBlockMacroStyler] primitive is used for bootstrapping the
 * styling of [block][BlockPhraseDescriptor] syntax, which Avail code uses to
 * produce [functions][FunctionDescriptor].
 *
 * See also [P_BootstrapBlockMacro], the primitive for creating blocks.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapBlockMacroStyler :
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
//		val transformedPhrase: A_Phrase = interpreter.argument(1)

		val fiber = interpreter.fiber()
		if (!fiber.canStyle) return interpreter.primitiveFailure(E_CANNOT_STYLE)
		val loader = fiber.availLoader!!

		if (optionalSendPhrase.tupleSize == 0)
		{
			return interpreter.primitiveSuccess(nil)
		}
		val sendPhrase = optionalSendPhrase.tupleAt(1)

		val (
			_, //optArgs,
			optPrim,
			_, //optLabel,
			_, //statements,
			optReturn,
			_, //optReturnType,
			_, //optExceptions
		) = sendPhrase.argumentsListNode.expressionsTuple

		// Style all the fixed keyword and operator tokens.
		loader.styleTokens(sendPhrase.tokens, BLOCK)

		// Style the primitive declaration, if present.
		if (optPrim.expressionsSize > 0)
		{
			val primDecl = optPrim.expressionAt(1)
			val primNamePhrase = primDecl.expressionAt(1)
			assert(primNamePhrase.phraseKindIsUnder(LITERAL_PHRASE))
			loader.styleToken(
				primNamePhrase.token.literal(),
				SystemStyle.PRIMITIVE_NAME)
		}

		// Style the return expression, if present.
		if (optReturn.expressionsSize > 0)
		{
			val returnPhrase = optReturn.expressionAt(1).token.literal()
			assert(returnPhrase.phraseKindIsUnder(EXPRESSION_PHRASE))
			val tokens = returnPhrase.allTokens
			loader.lockStyles {
				val lines = tokens.groupBy { it.lineNumber() }
				lines.forEach { (_, line) ->
					val start = line.first().start()
					val pastEnd = line.last().pastEnd()
					edit(start, pastEnd) { old ->
						val new = SystemStyle.RETURN_VALUE.kotlinString
						when (old)
						{
							null -> new
							else -> "$new,$old"
						}
					}
				}
			}
		}

		return interpreter.primitiveSuccess(nil)
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_CANNOT_STYLE))

	override fun privateBlockTypeRestriction(): A_Type = stylerFunctionType
}
