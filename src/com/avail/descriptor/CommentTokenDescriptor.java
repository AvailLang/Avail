/**
 * CommentTokenDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import com.avail.annotations.EnumField;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.utility.json.JSONWriter;
import org.jetbrains.annotations.Nullable;

import static com.avail.descriptor.CommentTokenDescriptor.IntegerSlots.*;
import static com.avail.descriptor.CommentTokenDescriptor.ObjectSlots.*;

/**
 * This is a token of an Avail method/class comment.  More specifically, this
 * is text contained between forward slash-asterisk-asterisk and
 * asterisk-forward slash.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class CommentTokenDescriptor
extends TokenDescriptor
{
	//this.string().asNativeString() - gets at the string contents.
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
	 * My class's slots of type AvailObject.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain StringDescriptor string}, exactly as it appeared in
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
		TRAILING_WHITESPACE
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
		writer.write("");
		writer.write("trailing whitespace");
		writer.write("");
		writer.endObject();
	}

	/**
	 * Create and initialize a new {@linkplain CommentTokenDescriptor comment
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
	 * @return The new comment token.
	 */
	public static AvailObject create (
		final A_String string,
		final A_String leadingWhitespace,
		final A_String trailingWhitespace,
		final int start,
		final int lineNumber,
		final int tokenIndex)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(STRING, string);
		instance.setSlot(LEADING_WHITESPACE, leadingWhitespace);
		instance.setSlot(TRAILING_WHITESPACE, trailingWhitespace);
		instance.setSlot(LOWER_CASE_STRING, NilDescriptor.nil());
		instance.setSlot(START, start);
		instance.setSlot(LINE_NUMBER, lineNumber);
		instance.setSlot(TOKEN_INDEX, tokenIndex);
		instance.setSlot(TOKEN_TYPE_CODE, TokenType.COMMENT.ordinal());
		return instance;
	}

	/**
	 * Construct a new {@link CommentTokenDescriptor}.
	 *
	 * @param mutability
	 * @param objectSlotsEnumClass
	 * @param integerSlotsEnumClass
	 */
	public CommentTokenDescriptor (
		final Mutability mutability,
		@Nullable final Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		@Nullable final Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	/**
	 * Construct a new {@link CommentTokenDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private CommentTokenDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link LiteralTokenDescriptor}. */
	private static final CommentTokenDescriptor mutable =
		new CommentTokenDescriptor(Mutability.MUTABLE);

	@Override
	CommentTokenDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link LiteralTokenDescriptor}. */
	private static final CommentTokenDescriptor shared =
		new CommentTokenDescriptor(Mutability.SHARED);

	@Override
	CommentTokenDescriptor immutable ()
	{
		// Answer the shared descriptor, since there isn't an immutable one.
		return shared;
	}

	@Override
	CommentTokenDescriptor shared ()
	{
		return shared;
	}
}
