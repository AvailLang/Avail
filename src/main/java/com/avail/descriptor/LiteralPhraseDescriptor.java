/*
 * LiteralNodeDescriptor.java
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

package com.avail.descriptor;

import com.avail.annotations.AvailMethod;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn;
import static com.avail.descriptor.LiteralPhraseDescriptor.ObjectSlots.TOKEN;
import static com.avail.descriptor.LiteralTokenDescriptor.literalToken;
import static com.avail.descriptor.LiteralTokenTypeDescriptor.mostGeneralLiteralTokenType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TokenDescriptor.TokenType.LITERAL;

/**
 * My instances are occurrences of literals parsed from Avail source code.  At
 * the moment only strings and non-negative numbers are supported.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class LiteralPhraseDescriptor
extends PhraseDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The token that was transformed into this literal.
		 */
		TOKEN
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append(object.token().string().asNativeString());
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		// Do nothing.
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		// Do nothing.
	}

	@Override @AvailMethod
	void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		// Do nothing.
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.emitPushLiteral(
			tuple(object.token()), object.slot(TOKEN).literal());
	}

	@Override @AvailMethod
	boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return !aPhrase.isMacroSubstitutionNode()
			&& object.phraseKind().equals(aPhrase.phraseKind())
			&& object.slot(TOKEN).equals(aPhrase.token());
	}

	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		final A_Token token = object.slot(TOKEN);
		assert token.tokenType() == LITERAL;
		final AvailObject literal = token.literal();
		return instanceTypeOrMetaOn(literal).makeImmutable();
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.token().hash() ^ 0x9C860C0D;
	}

	@Override
	PhraseKind o_PhraseKind (final AvailObject object)
	{
		return PhraseKind.LITERAL_PHRASE;
	}

	@Override
	void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.LITERAL_PHRASE;
	}

	@Override @AvailMethod
	A_Token o_Token (final AvailObject object)
	{
		return object.slot(TOKEN);
	}

	@Override
	A_Tuple o_Tokens (final AvailObject object)
	{
		return tuple(object.slot(TOKEN));
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("literal phrase");
		object.slot(TOKEN).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("literal phrase");
		object.slot(TOKEN).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a literal phrase from a {@linkplain LiteralTokenDescriptor literal
	 * token}.
	 *
	 * @param token The token that describes the literal.
	 * @return The new literal phrase.
	 */
	public static A_Phrase fromTokenForDecompiler (final A_Token token)
	{
		final AvailObject phrase = mutable.create();
		phrase.setSlot(TOKEN, token);
		return phrase.makeShared();
	}

	/**
	 * Create a literal phrase from a {@linkplain LiteralTokenDescriptor literal
	 * token}.
	 *
	 * @param token The token that describes the literal.
	 * @return The new literal phrase.
	 */
	public static A_Phrase literalNodeFromToken (final A_Token token)
	{
		assert token.isInstanceOfKind(mostGeneralLiteralTokenType());
		final AvailObject phrase = mutable.create();
		phrase.setSlot(TOKEN, token);
		return phrase.makeShared();
	}

	/**
	 * Create a literal phrase from an {@link AvailObject}, the literal value
	 * itself.  Automatically wrap the value inside a synthetic literal token.
	 *
	 * @param literalValue The value that this literal phrase should produce.
	 * @return The new literal phrase.
	 */
	public static A_Phrase syntheticLiteralNodeFor (
		final A_BasicObject literalValue)
	{
		final AvailObject token = literalToken(
			literalValue.isString()
				? (A_String) literalValue
				: stringFrom(literalValue.toString()),
			0,
			0,
			literalValue);
		return literalNodeFromToken(token);
	}

	/**
	 * Construct a new {@code LiteralPhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public LiteralPhraseDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.LITERAL_PHRASE_TAG,
			ObjectSlots.class,
			null);
	}

	/** The mutable {@link LiteralPhraseDescriptor}. */
	private static final LiteralPhraseDescriptor mutable =
		new LiteralPhraseDescriptor(Mutability.MUTABLE);

	@Override
	LiteralPhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link LiteralPhraseDescriptor}. */
	private static final LiteralPhraseDescriptor shared =
		new LiteralPhraseDescriptor(Mutability.SHARED);

	@Override
	LiteralPhraseDescriptor shared ()
	{
		return shared;
	}
}
