/*
 * P_BootstrapDefineSpecialObjectMacro.kt
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

package avail.interpreter.primitive.bootstrap.syntax

import avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.CREATE_LITERAL_PHRASE
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.CREATE_LITERAL_TOKEN
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.MACRO_DEFINER
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.METHOD_DEFINER
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.BlockPhraseDescriptor.Companion.newBlockNode
import avail.descriptor.phrases.ExpressionAsStatementPhraseDescriptor.Companion.newExpressionAsStatement
import avail.descriptor.phrases.ListPhraseDescriptor.Companion.emptyListNode
import avail.descriptor.phrases.ListPhraseDescriptor.Companion.newListNode
import avail.descriptor.phrases.LiteralPhraseDescriptor.Companion.syntheticLiteralNodeFor
import avail.descriptor.phrases.SendPhraseDescriptor.Companion.newSendNode
import avail.descriptor.phrases.SequencePhraseDescriptor.Companion.newSequence
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.literalTokenType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEQUENCE_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.nonemptyStringType
import avail.exceptions.AmbiguousNameException
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.exceptions.MalformedMessageException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.style.P_BootstrapDefineSpecialObjectMacroStyler

/**
 * **Primitive**: Construct a method and an accompanying literalizing macro that
 * provide access to the specified special object.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapDefineSpecialObjectMacro
	: Primitive(2, Bootstrap, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val nameLiteral = interpreter.argument(0)
		val specialObjectLiteral = interpreter.argument(1)
		val fiber = interpreter.fiber()
		val loader = fiber.availLoader
		if (loader === null || loader.module.isNil)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		}
		val bundle: A_Bundle =
			try
			{
				loader.lookupName(nameLiteral.token.literal()).bundleOrCreate()
			}
			catch (e: AmbiguousNameException)
			{
				return interpreter.primitiveFailure(e)
			}
			catch (e: MalformedMessageException)
			{
				return interpreter.primitiveFailure(e)
			}

		// Create a send of the bootstrap method definer that, when actually
		// sent, will produce a method that answers the special object.
		val literalType = specialObjectLiteral.phraseExpressionType
		val defineMethod = newSendNode(
			emptyTuple,
			emptyTuple,
			METHOD_DEFINER.bundle,
			newListNode(
				tuple(
					nameLiteral,
					newBlockNode(
						emptyTuple,
						null,
						tuple(specialObjectLiteral),
						literalType,
						emptySet,
						0),
					emptyListNode())),
			TOP.o)
		// Create a send of the bootstrap macro definer that, when actually
		// sent, will produce a method that literalizes the special object.
		val getValue =
			newSendNode(
				emptyTuple,
				emptyTuple,
				bundle,
				newListNode(emptyTuple),
				literalType)
		val createLiteralToken =
			newSendNode(
				emptyTuple,
				emptyTuple,
				CREATE_LITERAL_TOKEN.bundle,
				newListNode(
					tuple(
						getValue,
						syntheticLiteralNodeFor(
							specialObjectLiteral.token.string()),
						syntheticLiteralNodeFor(
							fromInt(0)),
						syntheticLiteralNodeFor(
							fromInt(0)),
						emptyListNode())),
				literalTokenType(literalType))
		val createLiteralNode =
			newSendNode(
				emptyTuple,
				emptyTuple,
				CREATE_LITERAL_PHRASE.bundle,
				newListNode(tuple(createLiteralToken)),
				LITERAL_PHRASE.create(literalType))
		val defineMacro =
			newSendNode(
				emptyTuple,
				emptyTuple,
				MACRO_DEFINER.bundle,
				newListNode(
					tuple(
						nameLiteral,
						emptyListNode(),
						newBlockNode(
							emptyTuple,
							null,
							tuple(createLiteralNode),
							LITERAL_PHRASE.create(
								literalType),
							emptySet,
							0),
						emptyListNode())),
				TOP.o)
		return interpreter.primitiveSuccess(
			newSequence(
				tuple(
					newExpressionAsStatement(defineMethod),
					newExpressionAsStatement(defineMacro))))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				LITERAL_PHRASE.create(nonemptyStringType),
				LITERAL_PHRASE.create(ANY.o)),
			SEQUENCE_PHRASE.mostGeneralType)

	override fun bootstrapStyler() = P_BootstrapDefineSpecialObjectMacroStyler
}
