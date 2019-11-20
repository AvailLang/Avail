/*
 * P_BootstrapDefineSpecialObjectMacro.kt
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

import com.avail.descriptor.A_Type
import com.avail.descriptor.ExpressionAsStatementPhraseDescriptor.newExpressionAsStatement
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerDescriptor.fromInt
import com.avail.descriptor.ListPhraseDescriptor.emptyListNode
import com.avail.descriptor.ListPhraseDescriptor.newListNode
import com.avail.descriptor.LiteralPhraseDescriptor.syntheticLiteralNodeFor
import com.avail.descriptor.LiteralTokenTypeDescriptor.literalTokenType
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom.*
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.SEQUENCE_PHRASE
import com.avail.descriptor.SendPhraseDescriptor.newSendNode
import com.avail.descriptor.SequencePhraseDescriptor.newSequence
import com.avail.descriptor.SetDescriptor.emptySet
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.nonemptyStringType
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.parsing.BlockPhraseDescriptor.newBlockNode
import com.avail.exceptions.AmbiguousNameException
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanInline

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
		val loader = fiber.availLoader()
		if (loader === null || loader.module().equalsNil())
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		}
		val bundle: A_Bundle =
			try
			{
				loader.lookupName(nameLiteral.token().literal()).bundleOrCreate()
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
		val defineMethod = newSendNode(
			emptyTuple(),
			METHOD_DEFINER.bundle,
			newListNode(
				tuple(
					nameLiteral,
					newBlockNode(
						emptyTuple(),
						0,
						tuple(specialObjectLiteral),
						specialObjectLiteral.expressionType(),
						emptySet(),
						0,
						emptyTuple()))),
			TOP.o())
		// Create a send of the bootstrap macro definer that, when actually
		// sent, will produce a method that literalizes the special object.
		val getValue =
			newSendNode(
				emptyTuple(),
				bundle,
				newListNode(emptyTuple()),
				specialObjectLiteral.expressionType())
		val createLiteralToken =
			newSendNode(
				emptyTuple(),
				CREATE_LITERAL_TOKEN.bundle,
				newListNode(
					tuple(
						getValue,
						syntheticLiteralNodeFor(
							specialObjectLiteral.token().string()),
						syntheticLiteralNodeFor(
							fromInt(0)),
						syntheticLiteralNodeFor(
							fromInt(0)))),
				literalTokenType(specialObjectLiteral.expressionType()))
		val createLiteralNode =
			newSendNode(
				emptyTuple(),
				CREATE_LITERAL_PHRASE.bundle,
				newListNode(tuple(createLiteralToken)),
				LITERAL_PHRASE.create(specialObjectLiteral.expressionType()))
		val defineMacro =
			newSendNode(
				emptyTuple(),
				MACRO_DEFINER.bundle,
				newListNode(
					tuple(
						nameLiteral,
						emptyListNode(),
						newBlockNode(
							emptyTuple(),
							0,
							tuple(createLiteralNode),
							LITERAL_PHRASE.create(
								specialObjectLiteral.expressionType()),
							emptySet(),
							0,
							emptyTuple()))),
				TOP.o())
		return interpreter.primitiveSuccess(
			newSequence(
				tuple(
					newExpressionAsStatement(defineMethod),
					newExpressionAsStatement(defineMacro))))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				LITERAL_PHRASE.create(nonemptyStringType()),
				LITERAL_PHRASE.create(ANY.o())),
			SEQUENCE_PHRASE.mostGeneralType())
}
