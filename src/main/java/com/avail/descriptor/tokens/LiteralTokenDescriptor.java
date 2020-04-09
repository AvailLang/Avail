/*
 * LiteralTokenDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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

package com.avail.descriptor.tokens;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.HideFieldJustForPrinting;
import com.avail.compiler.scanning.LexingState;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.pojos.RawPojoDescriptor;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.AbstractSlotsEnum;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.tuples.A_String;
import com.avail.descriptor.tuples.StringDescriptor;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.TypeTag;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;

import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.tokens.LiteralTokenDescriptor.IntegerSlots.LINE_NUMBER;
import static com.avail.descriptor.tokens.LiteralTokenDescriptor.IntegerSlots.START;
import static com.avail.descriptor.tokens.LiteralTokenDescriptor.ObjectSlots.*;
import static com.avail.descriptor.types.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.types.LiteralTokenTypeDescriptor.literalTokenType;
import static com.avail.descriptor.types.TypeDescriptor.Types.TOKEN;
import static com.avail.utility.Casts.cast;

/**
 * I represent a token that's a literal representation of some object.
 *
 * <p>In addition to the state inherited from {@link TokenDescriptor}, I add a
 * field to hold the literal value itself.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class LiteralTokenDescriptor
extends TokenDescriptor
{
	/**
	 * My class's slots of type int.
	 */
	public enum IntegerSlots implements IntegerSlotsEnumJava
	{
		/**
		 * {@link BitField}s for the token type code, the starting byte
		 * position, and the line number.
		 */
		START_AND_LINE;

		/**
		 * The line number in the source file. Currently signed 28 bits, which
		 * should be plenty.
		 */
		public static final BitField LINE_NUMBER =
			new BitField(START_AND_LINE, 4, 28);

		/**
		 * The starting position in the source file. Currently signed 32 bits,
		 * but this may change at some point -- not that we really need to parse
		 * 2GB of <em>Avail</em> source in one file, due to its deeply flexible
		 * syntax.
		 */
		@HideFieldInDebugger
		public static final BitField START =
			new BitField(START_AND_LINE, 32, 32);

		static
		{
			assert TokenDescriptor.IntegerSlots.TOKEN_TYPE_AND_START_AND_LINE
					.ordinal()
				== START_AND_LINE.ordinal();
			assert TokenDescriptor.IntegerSlots.START.isSamePlaceAs(
				START);
			assert TokenDescriptor.IntegerSlots.LINE_NUMBER.isSamePlaceAs(
				LINE_NUMBER);
		}
	}

	/**
	 * My slots of type {@link AvailObject}. Note that they have to start the
	 * same as in my superclass {@link TokenDescriptor}.
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/**
		 * The {@linkplain StringDescriptor string}, exactly as I appeared in
		 * the source.
		 */
		STRING,

		/**
		 * A {@link RawPojoDescriptor raw pojo} holding the {@link LexingState}
		 * after this token.
		 */
		@HideFieldJustForPrinting
		NEXT_LEXING_STATE_POJO,

		/** The actual {@link AvailObject} wrapped by this token. */
		LITERAL;

		static
		{
			assert TokenDescriptor.ObjectSlots.STRING.ordinal()
				== STRING.ordinal();
			assert TokenDescriptor.ObjectSlots.NEXT_LEXING_STATE_POJO.ordinal()
				== NEXT_LEXING_STATE_POJO.ordinal();
		}
	}

	@Override
	protected boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return e == NEXT_LEXING_STATE_POJO
			|| super.allowsImmutableToMutableReferenceInField(e);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append(String.format(
			"%s ",
			object.tokenType().name().toLowerCase().replace('_', ' ')));
		object.slot(LITERAL).printOnAvoidingIndent(
			aStream,
			recursionMap,
			indent + 1);
		aStream.append(String.format(
			" (%s) @ %d:%d",
			object.slot(STRING),
			object.slot(START),
			object.slot(LINE_NUMBER)));
	}

	@Override @AvailMethod
	protected TokenType o_TokenType (final AvailObject object)
	{
		return TokenType.LITERAL;
	}

	@Override @AvailMethod
	protected AvailObject o_Literal (final AvailObject object)
	{
		return object.slot(LITERAL);
	}

	@Override @AvailMethod
	protected A_Type o_Kind (final AvailObject object)
	{
		return literalTokenType(instanceType(object));
	}

	@Override @AvailMethod
	protected boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aTypeObject)
	{
		return aTypeObject.isSupertypeOfPrimitiveTypeEnum(TOKEN)
			|| aTypeObject.isLiteralTokenType()
				&& object.slot(LITERAL).isInstanceOf(aTypeObject.literalType());
	}

	@Override
	protected boolean o_IsLiteralToken (final AvailObject object)
	{
		return true;
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.LITERAL_TOKEN;
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("token");
		writer.write("token type");
		writer.write(object.tokenType().name().toLowerCase().replace(
			'_', ' '));
		writer.write("start");
		writer.write(object.slot(START));
		writer.write("line number");
		writer.write(object.slot(LINE_NUMBER));
		writer.write("lexeme");
		object.slot(STRING).writeTo(writer);
		writer.write("literal");
		object.slot(LITERAL).writeTo(writer);
		writer.endObject();
	}

	@Override
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("token");
		writer.write("start");
		writer.write(object.slot(START));
		writer.write("line number");
		writer.write(object.slot(LINE_NUMBER));
		writer.write("lexeme");
		object.slot(STRING).writeTo(writer);
		writer.write("literal");
		object.slot(LITERAL).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create and initialize a new {@code LiteralTokenDescriptor literal token}.
	 *
	 * @param string
	 *        The token text.
	 * @param start
	 *        The token's starting character position in the file.
	 * @param lineNumber
	 *        The line number on which the token occurred.
	 * @param literal The literal value.
	 * @return The new literal token.
	 */
	public static AvailObject literalToken (
		final A_String string,
		final int start,
		final int lineNumber,
		final A_BasicObject literal)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(STRING, string);
		instance.setSlot(START, start);
		instance.setSlot(LINE_NUMBER, lineNumber);
		instance.setSlot(LITERAL, literal);
		if (literal.isInstanceOfKind(TOKEN.o()))
		{
			// We're wrapping another token, so share that token's
			// nextLexingState pojo, if set.
			final AvailObject innerToken = cast(literal.traversed());
			final A_BasicObject pojo =
				innerToken.slot(NEXT_LEXING_STATE_POJO);
			instance.setSlot(NEXT_LEXING_STATE_POJO, pojo);
			// Also add this token to the same CompilationContext that the
			// inner token might also be inside.  Even if it isn't, the new
			// token will be cleanly disconnected from the CompilationContext
			// after finishing parsing of the current top-level statement.
			if (!pojo.equalsNil())
			{
				final LexingState nextState = pojo.javaObjectNotNull();
				nextState.getCompilationContext().recordToken(innerToken);
			}
		}
		else
		{
			instance.setSlot(NEXT_LEXING_STATE_POJO, nil);
		}
		return instance.makeShared();
	}

	/**
	 * Construct a new {@code LiteralTokenDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private LiteralTokenDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.LITERAL_TOKEN_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link LiteralTokenDescriptor}. */
	private static final LiteralTokenDescriptor mutable =
		new LiteralTokenDescriptor(Mutability.MUTABLE);

	@Override
	public LiteralTokenDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link LiteralTokenDescriptor}. */
	private static final LiteralTokenDescriptor shared =
		new LiteralTokenDescriptor(Mutability.SHARED);

	@Override
	public LiteralTokenDescriptor immutable ()
	{
		// Answer the shared descriptor, since there isn't an immutable one.
		return shared;
	}

	@Override
	public LiteralTokenDescriptor shared ()
	{
		return shared;
	}
}
