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
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.methods.A_Styler.Companion.stylerFunctionType
import avail.descriptor.methods.StylerDescriptor.SystemStyle
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.allTokens
import avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.phrases.BlockPhraseDescriptor
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.tuples.A_Tuple.Companion.component1
import avail.descriptor.tuples.A_Tuple.Companion.component2
import avail.descriptor.tuples.A_Tuple.Companion.component3
import avail.descriptor.tuples.A_Tuple.Companion.component4
import avail.descriptor.tuples.A_Tuple.Companion.component5
import avail.descriptor.tuples.A_Tuple.Companion.component6
import avail.descriptor.tuples.A_Tuple.Companion.component7
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.systemStyleForType
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.ReadsFromHiddenGlobalState
import avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.execution.AvailLoader.Companion.overrideMethodSendStyle
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
		1,
		CanInline,
		Bootstrap,
		ReadsFromHiddenGlobalState,
		WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val sendPhrase: A_Phrase = interpreter.argument(0)

		val loader = interpreter.fiber().availLoader
			?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}

		val (
			optArgs,
			optPrim,
			optLabel,
			@Suppress("UNUSED_VARIABLE") statements,
			optReturn,
			optReturnType,
			optExceptions
		) = sendPhrase.argumentsListNode.expressionsTuple

		// Give an initial default style to all the fixed keyword and operator
		// tokens.
		for (token in sendPhrase.tokens)
		{
			val style = when (token.tokenType())
			{
				TokenType.KEYWORD -> SystemStyle.METHOD_SEND
				TokenType.OPERATOR -> SystemStyle.METHOD_SEND
				// Skip other tokens... although they won't actually occur here.
				else -> continue
			}
			loader.styleToken(token, style)
		}

		// Add styles specific to blocks.  Start with the arguments...
		if (optArgs.expressionsSize > 0)
		{
			optArgs.expressionAt(1).expressionsTuple.forEach { argPart ->
				assert(argPart.phraseKindIsUnder(LIST_PHRASE))
				val argNameLiteral = argPart.expressionAt(1)
				loader.styleToken(
					argNameLiteral.token.literal(),
					SystemStyle.PARAMETER_DEFINITION)
				val argType = argPart.expressionAt(2)
				val argTypeStyle =
					argType.phraseExpressionType.systemStyleForType
				argType.allTokens.forEach { token ->
					loader.styleToken(
						token,
						argTypeStyle,
						false,
						overrideMethodSendStyle(argTypeStyle))
				}
			}
		}

		// Deal with the primitive declaration if present.
		if (optPrim.expressionsSize > 0)
		{
			val primNamePhrase = optPrim.expressionAt(1).expressionAt(1)
			assert(primNamePhrase.phraseKindIsUnder(LITERAL_PHRASE))
			loader.styleToken(
				primNamePhrase.token.literal(),
				SystemStyle.PRIMITIVE_NAME)
		}

		// Deal with the label if present.
		if (optLabel.expressionsSize > 0)
		{
			val labelPhrase = optLabel.expressionAt(1).expressionAt(1)
			assert(labelPhrase.phraseKindIsUnder(LITERAL_PHRASE))
			loader.styleToken(
				labelPhrase.token.literal(),
				SystemStyle.LABEL_DEFINITION)
		}

		// Deal with the return expression.
		if (optReturn.expressionsSize > 0)
		{
			val returnPhrase = optReturn.expressionAt(1).token.literal()
			assert(returnPhrase.phraseKindIsUnder(EXPRESSION_PHRASE))
			returnPhrase.allTokens.forEach { token ->
				loader.styleToken(token, SystemStyle.RETURN_VALUE)
			}
		}

		// Deal with the return type if present.
		if (optReturnType.expressionsSize > 0)
		{
			val returnTypePhrase = optReturnType.expressionAt(1)
			assert(returnTypePhrase.phraseKindIsUnder(EXPRESSION_PHRASE))
			val returnTypeStyle =
				returnTypePhrase.phraseExpressionType.systemStyleForType
			returnTypePhrase.allTokens.forEach { token ->
				loader.styleToken(
					token,
					returnTypeStyle,
					false,
					overrideMethodSendStyle(returnTypeStyle))
			}
		}

		// Deal with exception types if present.
		if (optExceptions.expressionsSize > 0)
		{
			optExceptions.expressionAt(1).expressionsTuple.forEach { exPart ->
				assert(exPart.phraseKindIsUnder(LIST_PHRASE))
				val exTypePhrase = exPart.expressionAt(1)
				exTypePhrase.allTokens.forEach { token ->
					loader.styleToken(
						token,
						SystemStyle.TYPE,
						false,
						overrideMethodSendStyle(SystemStyle.TYPE))
				}
			}
		}

		return interpreter.primitiveSuccess(nil)
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION))

	override fun privateBlockTypeRestriction(): A_Type = stylerFunctionType
}
