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

import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.methods.A_Styler.Companion.stylerFunctionType
import avail.descriptor.methods.StylerDescriptor.SystemStyle
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.phrases.BlockPhraseDescriptor
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.tuples.A_Tuple.Companion.component1
import avail.descriptor.tuples.A_Tuple.Companion.component2
import avail.descriptor.tuples.A_Tuple.Companion.component3
import avail.descriptor.tuples.A_Tuple.Companion.component4
import avail.descriptor.tuples.A_Tuple.Companion.component5
import avail.descriptor.tuples.A_Tuple.Companion.component6
import avail.descriptor.tuples.A_Tuple.Companion.component7
import avail.descriptor.types.A_Type
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.descriptor.variables.A_Variable
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanInline
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
 */
@Suppress("unused")
object P_BootstrapBlockMacroStyler : Primitive(7, CanInline, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(4)
		val sendPhrase: A_Phrase = interpreter.argument(0)
		val phraseStyles: A_Variable = interpreter.argument(1)
		val tokenStyles: A_Variable = interpreter.argument(2)
		val tokenDefinitions: A_Variable = interpreter.argument(3)

		val (
			optArgs,
			optPrim,
			optLabel,
			statements,
			optReturn,
			optResult,
			optExceptions
		) = sendPhrase.argumentsListNode.expressionsTuple

		// Give an initial default style to all the fixed keyword and operator
		// tokens.
		for (token in sendPhrase.tokens)
		{
			val styleString = when (token.tokenType())
			{
				TokenType.KEYWORD -> SystemStyle.METHOD_SEND.string
				TokenType.OPERATOR -> SystemStyle.METHOD_SEND.string
				// Skip other tokens... although they won't actually occur here.
				else -> continue
			}
			tokenStyles.atomicAddToMap(token, styleString)
		}

		// Add styles specific to blocks.  Start with the arguments...
		if (optArgs.expressionsSize > 0)
		{
			optArgs.expressionAt(1).expressionsTuple.forEach { argPart ->
				assert(argPart.phraseKindIsUnder(LIST_PHRASE))
				val argNameLiteral = argPart.expressionAt(1)
				tokenStyles.atomicAddToMap(
					argNameLiteral.token.literal(),
					SystemStyle.PARAMETER_DEFINITION.string)
			}
		}

		// Deal with the primitive declaration if present.
		if (optPrim.expressionsSize > 0)
		{
			val primNamePhrase = optPrim.expressionAt(1).expressionAt(1)
			assert(primNamePhrase.phraseKindIsUnder(LITERAL_PHRASE))
			tokenStyles.atomicAddToMap(
				primNamePhrase.token.literal(), SystemStyle.PRIMITIVE_NAME.string)
		}

		// Deal with the label if present.
		if (optLabel.expressionsSize > 0)
		{
			val labelPhrase = optLabel.expressionAt(1).expressionAt(1)
			assert(labelPhrase.phraseKindIsUnder(LITERAL_PHRASE))
			tokenStyles.atomicAddToMap(
				labelPhrase.token.literal(), SystemStyle.LABEL_DEFINITION.string)
		}

		// TODO Do more block styling work.

		//// Deal with the statements.
		//for (statement in statements.expressionsTuple)
		//{
		//	allStatements.add(statement.token.literal())
		//}
		//assert(optionalReturnExpression.expressionsSize <= 1)
		//val deducedReturnType: A_Type = when
		//{
		//	optionalReturnExpression.expressionsSize == 1 ->
		//	{
		//		val returnLiteralPhrase =
		//			optionalReturnExpression.expressionAt(1)
		//		assert(returnLiteralPhrase.phraseKindIsUnder(LITERAL_PHRASE))
		//		val returnExpression = returnLiteralPhrase.token.literal()
		//		allStatements.add(returnExpression)
		//		if (labelReturnType === null)
		//		{
		//			returnExpression.phraseExpressionType
		//		}
		//		else
		//		{
		//			returnExpression.phraseExpressionType.typeUnion(
		//				labelReturnType)
		//		}
		//	}
		//	primitive !== null && primitive.hasFlag(CannotFail) ->
		//	{
		//		// An infallible primitive must have no statements.
		//		primitive.blockTypeRestriction().returnType
		//	}
		//	else -> TOP.o
		//}
		//
		//if (allStatements.size > 0 && !canHaveStatements)
		//{
		//	throw AvailRejectedParseException(
		//		STRONG,
		//		"infallible primitive function not to have statements")
		//}
		//
		//val declaredReturnType = when
		//{
		//	optionalReturnType.expressionsSize != 0 ->
		//		optionalReturnType.expressionAt(1).token.literal()
		//	else -> null
		//}
		//// Make sure the last expression's type ⊆ the declared return type, if
		//// applicable.  Also make sure the primitive's return type ⊆ the
		//// declared return type.  Finally, make sure that the label's return
		//// type ⊆ the block's effective return type.
		//if (declaredReturnType !== null)
		//{
		//	if (!deducedReturnType.isSubtypeOf(declaredReturnType))
		//	{
		//		throw AvailRejectedParseException(
		//			STRONG,
		//			labelReturnType?.let {
		//				"the union ($deducedReturnType) of the final " +
		//					"expression's type and the label's declared type " +
		//					"to agree with the declared return type (" +
		//					"$declaredReturnType)"
		//			} ?: ("final expression's type ($deducedReturnType) to " +
		//				"agree with the declared return type " +
		//				"($declaredReturnType)"))
		//	}
		//	if (primitiveReturnType !== null
		//		&& !primitiveReturnType.isSubtypeOf(declaredReturnType))
		//	{
		//		throw AvailRejectedParseException(
		//			STRONG,
		//			"primitive's intrinsic return type ("
		//				+ primitiveReturnType
		//				+ ") to agree with the declared return type ("
		//				+ declaredReturnType
		//				+ ")")
		//	}
		//	if (labelReturnType !== null
		//		&& !labelReturnType.isSubtypeOf(declaredReturnType))
		//	{
		//		throw AvailRejectedParseException(
		//			STRONG,
		//			"label's declared return type ($labelReturnType) to agree "
		//			+ "with the function's declared return type "
		//			+ "($declaredReturnType)")
		//	}
		//}
		//else if (primitiveReturnType !== null)
		//{
		//	// If it's a primitive, then the block must declare an explicit
		//	// return type.
		//	throw AvailRejectedParseException(
		//		// In case we're trying a parse that stops before the type.
		//		WEAK,
		//		"primitive function to declare its return type")
		//}
		//val returnType = declaredReturnType ?: deducedReturnType
		//var exceptionsSet: A_Set = emptySet
		//if (optionalExceptionTypes.expressionsSize == 1)
		//{
		//	val expressions =
		//		optionalExceptionTypes.lastExpression.expressionsTuple
		//	exceptionsSet = generateSetFrom(expressions) {
		//		it.token.literal()
		//	}.makeImmutable()
		//}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type = stylerFunctionType
}
