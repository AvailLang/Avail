/*
 * P_BootstrapPrefixLabelDeclaration.kt
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

package com.avail.interpreter.primitive.bootstrap.syntax

import com.avail.compiler.AvailRejectedParseException
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import com.avail.descriptor.phrases.A_Phrase.Companion.lastExpression
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newLabel
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.tokens.TokenDescriptor.TokenType
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationTypeForFunctionType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import com.avail.descriptor.types.TypeDescriptor.Types.TOKEN
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter

/**
 * The `P_BootstrapPrefixLabelDeclaration` primitive is used for bootstrapping
 * declaration of a [label][DeclarationKind.LABEL]. The label indicates a way to
 * restart or exit a block, so it's probably best if Avail's block syntax
 * continues to constrain this to occur at the start of a block.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapPrefixLabelDeclaration : Primitive(3, CanInline, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val optionalBlockArgumentsList = interpreter.argument(0)
		//		final A_Phrase optionalPrimFailurePhrase = interpreter.argument(1);
		val optionalLabelPhrase = interpreter.argument(2)

		interpreter.availLoaderOrNull() ?:
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)

		// Note that because the section marker occurs inside the optionality
		// of the label declaration, this function will only be invoked when
		// there truly is a label declaration.
		assert(optionalLabelPhrase.expressionsSize() == 1)
		val labelPairPhrase = optionalLabelPhrase.lastExpression()
		assert(labelPairPhrase.expressionsSize() == 2)
		val labelNamePhrase = labelPairPhrase.expressionAt(1)
		val labelName = labelNamePhrase.token().literal()
		if (labelName.tokenType() != TokenType.KEYWORD)
		{
			throw AvailRejectedParseException(
				STRONG, "label name to be alphanumeric")
		}
		val optionalLabelReturnTypePhrase =
			labelPairPhrase.expressionAt(2)
		val labelReturnTypePhrase: A_Phrase
		val labelReturnType: A_Type =
			if (optionalLabelReturnTypePhrase.expressionsSize() == 1)
			{
				labelReturnTypePhrase =
					optionalLabelReturnTypePhrase.expressionAt(1)
				assert(labelReturnTypePhrase.phraseKindIsUnder(LITERAL_PHRASE))
				labelReturnTypePhrase.token().literal()
			}
			else
			{
				// If the label doesn't specify a return type, use bottom.
				// Because of continuation return type contravariance, this is
				// the most general answer.
				labelReturnTypePhrase = nil
				bottom
			}

		// Re-extract all the argument types so we can specify the exact type of
		// the continuation.
		val blockArgumentTypes = mutableListOf<A_Type>()
		if (optionalBlockArgumentsList.expressionsSize() > 0)
		{
			assert(optionalBlockArgumentsList.expressionsSize() == 1)
			val blockArgumentsList =
				optionalBlockArgumentsList.lastExpression()
			assert(blockArgumentsList.expressionsSize() >= 1)
			for (argumentPair in blockArgumentsList.expressionsTuple())
			{
				assert(argumentPair.expressionsSize() == 2)
				val typePhrase = argumentPair.lastExpression()
				assert(typePhrase.isInstanceOfKind(
					LITERAL_PHRASE.create(anyMeta())))
				val argType = typePhrase.token().literal()
				assert(argType.isType)
				blockArgumentTypes.add(argType)
			}
		}
		val functionType =
			functionType(tupleFromList(blockArgumentTypes), labelReturnType)
		val continuationType =
			continuationTypeForFunctionType(functionType)
		val labelDeclaration =
			newLabel(labelName, labelReturnTypePhrase, continuationType)
		val conflictingDeclaration =
			FiberDescriptor.addDeclaration(labelDeclaration)
		if (conflictingDeclaration !== null)
		{
			throw AvailRejectedParseException(
				STRONG,
				"label declaration ${labelName.string()} to have a name that "
					+ "doesn't shadow an existing "
					+ conflictingDeclaration.declarationKind().nativeKindName()
					+ " (from line ${conflictingDeclaration.token().lineNumber()})")
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional arguments section. */
					zeroOrOneOf(
						/* Arguments are present. */
						oneOrMoreOf(
							/* An argument. */
							tupleTypeForTypes(
								/* Argument name, a token. */
								TOKEN.o,
								/* Argument type. */
								anyMeta())))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional primitive declaration. */
					zeroOrOneOf(
						/* Primitive declaration */
						tupleTypeForTypes(
							/* Primitive name. */
							TOKEN.o,
							/* Optional failure variable declaration. */
							zeroOrOneOf(
								/* Primitive failure variable parts. */
								tupleTypeForTypes(
									/* Primitive failure variable name token */
									TOKEN.o,
									/* Primitive failure variable type */
									anyMeta()))))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional label declaration. */
					zeroOrOneOf(
						/* Label parts. */
						tupleTypeForTypes(
							/* Label name */
							TOKEN.o,
							/* Optional label return type. */
							zeroOrOneOf(
								/* Label return type. */
								topMeta()))))),
			TOP.o
		)
}
