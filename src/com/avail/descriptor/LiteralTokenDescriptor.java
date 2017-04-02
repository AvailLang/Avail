/**
 * LiteralTokenDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import static com.avail.descriptor.LiteralTokenDescriptor.IntegerSlots.*;
import static com.avail.descriptor.LiteralTokenDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.EnumField;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

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
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * {@link BitField}s for the token type code and the starting byte
		 * position.
		 */
		TOKEN_TYPE_AND_START,

		/** {@link BitField}s for the line number and token index. */
		LINE_AND_TOKEN_INDEX;

		/**
		 * The {@link Enum#ordinal() ordinal} of the {@link TokenType} that
		 * indicates what basic kind of token this is.
		 */
		@EnumField(describedBy=TokenType.class)
		final static BitField TOKEN_TYPE_CODE =
			bitField(TOKEN_TYPE_AND_START, 0, 32);

		/**
		 * The starting position in the source file. Currently signed 32 bits,
		 * but this may change at some point -- not that we really need to parse
		 * 2GB of <em>Avail</em> source in one file, due to its deeply flexible
		 * syntax.
		 */
		final static BitField START =
			bitField(TOKEN_TYPE_AND_START, 32, 32);

		/**
		 * The line number in the source file. Currently signed 32 bits, which
		 * should be plenty.
		 */
		final static BitField LINE_NUMBER =
			bitField(LINE_AND_TOKEN_INDEX, 0, 32);

		/**
		 * The zero-based token number within the source file's tokenization.
		 * Currently signed 32 bits, which should be plenty.
		 */
		final static BitField TOKEN_INDEX =
			bitField(LINE_AND_TOKEN_INDEX, 32, 32);

		static
		{
			assert TokenDescriptor.IntegerSlots.TOKEN_TYPE_AND_START.ordinal()
				== TOKEN_TYPE_AND_START.ordinal();
			assert TokenDescriptor.IntegerSlots.LINE_AND_TOKEN_INDEX.ordinal()
				== LINE_AND_TOKEN_INDEX.ordinal();
			assert TokenDescriptor.IntegerSlots.TOKEN_TYPE_CODE.isSamePlaceAs(
				TOKEN_TYPE_CODE);
			assert TokenDescriptor.IntegerSlots.START.isSamePlaceAs(
				START);
			assert TokenDescriptor.IntegerSlots.LINE_NUMBER.isSamePlaceAs(
				LINE_NUMBER);
			assert TokenDescriptor.IntegerSlots.TOKEN_INDEX.isSamePlaceAs(
				TOKEN_INDEX);
		}
	}

	/**
	 * My slots of type {@link AvailObject}. Note that they have to start the
	 * same as in my superclass {@link TokenDescriptor}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain StringDescriptor string}, exactly as I appeared in
		 * the source.
		 */
		STRING,

		/**
		 * The lower case {@linkplain StringDescriptor string}, cached as an
		 * optimization for case insensitive parsing.
		 */
		@HideFieldInDebugger
		LOWER_CASE_STRING,

		/** The {@linkplain A_String leading whitespace}. */
		@HideFieldInDebugger
		LEADING_WHITESPACE,

		/** The {@linkplain A_String trailing whitespace}. */
		@HideFieldInDebugger
		TRAILING_WHITESPACE,

		/**
		 * The actual {@link AvailObject} wrapped by this token.
		 */
		LITERAL;

		static
		{
			assert TokenDescriptor.ObjectSlots.STRING.ordinal()
				== STRING.ordinal();
			assert TokenDescriptor.ObjectSlots.LOWER_CASE_STRING.ordinal()
				== LOWER_CASE_STRING.ordinal();
			assert TokenDescriptor.ObjectSlots.LEADING_WHITESPACE.ordinal()
				== LEADING_WHITESPACE.ordinal();
			assert TokenDescriptor.ObjectSlots.TRAILING_WHITESPACE.ordinal()
				== TRAILING_WHITESPACE.ordinal();
		}
	}

	@Override @AvailMethod
	AvailObject o_Literal (final AvailObject object)
	{
		return object.slot(LITERAL);
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return LiteralTokenTypeDescriptor.create(
			AbstractEnumerationTypeDescriptor.withInstance(object));
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aTypeObject)
	{
		if (aTypeObject.isSupertypeOfPrimitiveTypeEnum(TOKEN))
		{
			return true;
		}
		if (!aTypeObject.isLiteralTokenType())
		{
			return false;
		}
		return object.slot(LITERAL).isInstanceOf(aTypeObject.literalType());
	}

	@Override
	boolean o_IsLiteralToken (final AvailObject object)
	{
		return true;
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.LITERAL_TOKEN;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
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
		writer.write("leading whitespace");
		object.slot(LEADING_WHITESPACE).writeTo(writer);
		writer.write("trailing whitespace");
		object.slot(TRAILING_WHITESPACE).writeTo(writer);
		writer.write("literal");
		object.slot(LITERAL).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
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
		writer.write("leading whitespace");
		object.slot(LEADING_WHITESPACE).writeTo(writer);
		writer.write("trailing whitespace");
		object.slot(TRAILING_WHITESPACE).writeTo(writer);
		writer.write("literal");
		object.slot(LITERAL).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create and initialize a new {@linkplain LiteralTokenDescriptor literal
	 * token}.
	 *
	 * @param string
	 *        The token text.
	 * @param leadingWhitespace
	 *        The leading whitespace.
	 * @param trailingWhitespace
	 *        The trailing whitespace.
	 * @param start
	 *        The token's starting character position in the file.
	 * @param lineNumber
	 *        The line number on which the token occurred.
	 * @param tokenIndex
	 *        The zero-based token number within the source file.  -1 for
	 *        synthetic tokens.
	 * @param tokenType
	 *        The type of token to create.
	 * @param literal The literal value.
	 * @return The new literal token.
	 */
	public static AvailObject create (
		final A_String string,
		final A_String leadingWhitespace,
		final A_String trailingWhitespace,
		final int start,
		final int lineNumber,
		final int tokenIndex,
		final TokenType tokenType,
		final A_BasicObject literal)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(STRING, string);
		instance.setSlot(LEADING_WHITESPACE, leadingWhitespace);
		instance.setSlot(TRAILING_WHITESPACE, trailingWhitespace);
		instance.setSlot(LOWER_CASE_STRING, NilDescriptor.nil());
		instance.setSlot(START, start);
		instance.setSlot(LINE_NUMBER, lineNumber);
		instance.setSlot(TOKEN_INDEX, tokenIndex);
		instance.setSlot(TOKEN_TYPE_CODE, tokenType.ordinal());
		instance.setSlot(LITERAL, literal);
		return instance;
	}

	/**
	 * Construct a new {@link LiteralTokenDescriptor}.
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
	LiteralTokenDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link LiteralTokenDescriptor}. */
	private static final LiteralTokenDescriptor shared =
		new LiteralTokenDescriptor(Mutability.SHARED);

	@Override
	LiteralTokenDescriptor immutable ()
	{
		// Answer the shared descriptor, since there isn't an immutable one.
		return shared;
	}

	@Override
	LiteralTokenDescriptor shared ()
	{
		return shared;
	}
}
