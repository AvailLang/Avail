/*
 * P_BootstrapDefineSpecialObjectMacro.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.bootstrap.syntax;

import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.exceptions.AmbiguousNameException;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;

import static com.avail.descriptor.BlockPhraseDescriptor.newBlockNode;
import static com.avail.descriptor.ExpressionAsStatementPhraseDescriptor.newExpressionAsStatement;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.ListPhraseDescriptor.emptyListNode;
import static com.avail.descriptor.ListPhraseDescriptor.newListNode;
import static com.avail.descriptor.LiteralPhraseDescriptor.syntheticLiteralNodeFor;
import static com.avail.descriptor.LiteralTokenTypeDescriptor.literalTokenType;
import static com.avail.descriptor.MethodDescriptor.SpecialMethodAtom.*;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.SEQUENCE_PHRASE;
import static com.avail.descriptor.SendPhraseDescriptor.newSendNode;
import static com.avail.descriptor.SequencePhraseDescriptor.newSequence;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.oneOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER;
import static com.avail.interpreter.Primitive.Flag.Bootstrap;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive</strong>: Construct a method and an accompanying
 * literalizing macro that provide access to the specified special object.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@SuppressWarnings("unused")
public final class P_BootstrapDefineSpecialObjectMacro
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_BootstrapDefineSpecialObjectMacro().init(
			2, Bootstrap, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Phrase nameLiteral = interpreter.argument(0);
		final A_Phrase specialObjectLiteral = interpreter.argument(1);
		final A_Fiber fiber = interpreter.fiber();
		final @Nullable AvailLoader loader = fiber.availLoader();
		if (loader == null || loader.module().equalsNil())
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		final A_Bundle bundle;
		try
		{
			final A_Atom trueName = loader.lookupName(
				nameLiteral.token().literal());
			bundle = trueName.bundleOrCreate();
		}
		catch (final AmbiguousNameException|MalformedMessageException e)
		{
			return interpreter.primitiveFailure(e);
		}
		// Create a send of the bootstrap method definer that, when actually
		// sent, will produce a method that answers the special object.
		final A_Phrase defineMethod = newSendNode(
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
			TOP.o());
		// Create a send of the bootstrap macro definer that, when actually
		// sent, will produce a method that literalizes the special object.
		final A_Phrase getValue = newSendNode(
			emptyTuple(),
			bundle,
			newListNode(emptyTuple()),
			specialObjectLiteral.expressionType());
		final A_Phrase createLiteralToken = newSendNode(
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
			literalTokenType(specialObjectLiteral.expressionType()));
		final A_Phrase createLiteralNode = newSendNode(
			emptyTuple(),
			CREATE_LITERAL_PHRASE.bundle,
			newListNode(tuple(createLiteralToken)),
			LITERAL_PHRASE.create(specialObjectLiteral.expressionType()));
		final A_Phrase defineMacro = newSendNode(
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
			TOP.o());
		return interpreter.primitiveSuccess(
			newSequence(
				tuple(
					newExpressionAsStatement(defineMethod),
					newExpressionAsStatement(defineMacro))));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				LITERAL_PHRASE.create(oneOrMoreOf(CHARACTER.o())),
				LITERAL_PHRASE.create(ANY.o())),
			SEQUENCE_PHRASE.mostGeneralType());
	}
}
