/**
 * CommentTokenDescriptor.java
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

import com.avail.annotations.EnumField;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.HideFieldJustForPrinting;
import com.avail.compiler.CompilationContext;
import com.avail.compiler.scanning.LexingState;
import com.avail.utility.json.JSONWriter;

import static com.avail.descriptor.CommentTokenDescriptor.IntegerSlots.*;
import static com.avail.descriptor.CommentTokenDescriptor.ObjectSlots.*;
import static com.avail.descriptor.NilDescriptor.nil;

/**
 * This is a token of an Avail method/class comment.  More specifically, this
 * is text contained between forward slash-asterisk-asterisk and
 * asterisk-forward slash.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public final class CommentTokenDescriptor
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
		 * {@link BitField}s for the token type code, the starting byte
		 * position, and the line number.
		 */
		TOKEN_TYPE_AND_START_AND_LINE;

		/**
		 * The {@link Enum#ordinal() ordinal} of the {@link TokenType} that
		 * indicates what basic kind of token this is.  Currently four bits are
		 * reserved for this purpose.
		 */
		@EnumField(describedBy = TokenType.class)
		static final BitField TOKEN_TYPE_CODE =
			bitField(TOKEN_TYPE_AND_START_AND_LINE, 0, 4);

		/**
		 * The line number in the source file. Currently signed 28 bits, which
		 * should be plenty.
		 */
		static final BitField LINE_NUMBER =
			bitField(TOKEN_TYPE_AND_START_AND_LINE, 4, 28);

		/**
		 * The starting position in the source file. Currently signed 32 bits,
		 * but this may change at some point -- not that we really need to parse
		 * 2GB of <em>Avail</em> source in one file, due to its deeply flexible
		 * syntax.
		 */
		@HideFieldInDebugger
		static final BitField START =
			bitField(TOKEN_TYPE_AND_START_AND_LINE, 32, 32);

		static
		{
			assert TokenDescriptor.IntegerSlots.TOKEN_TYPE_AND_START_AND_LINE
				.ordinal()
				== TOKEN_TYPE_AND_START_AND_LINE.ordinal();
			assert TokenDescriptor.IntegerSlots.TOKEN_TYPE_CODE.isSamePlaceAs(
				TOKEN_TYPE_CODE);
			assert TokenDescriptor.IntegerSlots.START.isSamePlaceAs(
				START);
			assert TokenDescriptor.IntegerSlots.LINE_NUMBER.isSamePlaceAs(
				LINE_NUMBER);
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
		TRAILING_WHITESPACE,

		/**
		 * A {@link RawPojoDescriptor raw pojo} holding the {@link LexingState}
		 * after this token.
		 *
		 * <p>The field is typically {@link NilDescriptor#nil nil}, to
		 * indicate the {@link LexingState} should be looked up by position (and
		 * line number) via {@link CompilationContext#lexingStateAt(int, int)}.
		 * </p>
		 */
		@HideFieldJustForPrinting
		NEXT_LEXING_STATE_POJO;

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
			assert TokenDescriptor.ObjectSlots.NEXT_LEXING_STATE_POJO.ordinal()
				== NEXT_LEXING_STATE_POJO.ordinal();
		}
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return e == LOWER_CASE_STRING
			|| e == TRAILING_WHITESPACE
			|| e == NEXT_LEXING_STATE_POJO
			|| super.allowsImmutableToMutableReferenceInField(e);
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
	 * Create and initialize a new comment token.
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
	 * @return The new comment token.
	 */
	public static A_Token newCommentToken (
		final A_String string,
		final A_String leadingWhitespace,
		final A_String trailingWhitespace,
		final int start,
		final int lineNumber)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(STRING, string);
		instance.setSlot(LEADING_WHITESPACE, leadingWhitespace);
		instance.setSlot(TRAILING_WHITESPACE, trailingWhitespace);
		instance.setSlot(LOWER_CASE_STRING, nil);
		instance.setSlot(START, start);
		instance.setSlot(LINE_NUMBER, lineNumber);
		instance.setSlot(TOKEN_TYPE_CODE, TokenType.COMMENT.ordinal());
		instance.setSlot(NEXT_LEXING_STATE_POJO, nil);
		return instance;
	}

	/**
	 * Construct a new {@code CommentTokenDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private CommentTokenDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.TOKEN_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
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
