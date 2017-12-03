/**
 * TokenDescriptor.java
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

import com.avail.annotations.AvailMethod;
import com.avail.annotations.EnumField;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.compiler.CompilationContext;
import com.avail.compiler.scanning.LexingState;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TokenDescriptor.IntegerSlots.*;
import static com.avail.descriptor.TokenDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.TOKEN;


/**
 * I represent a token scanned from Avail source code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class TokenDescriptor
extends Descriptor
{
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
		static final BitField START =
			bitField(TOKEN_TYPE_AND_START_AND_LINE, 32, 32);
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
		NEXT_LEXING_STATE_POJO;
	}

	/**
	 * An enumeration that lists the basic kinds of tokens that can be
	 * encountered.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public enum TokenType
	implements IntegerEnumSlotDescriptionEnum
	{
		/**
		 * A special type of token that is appended to the actual tokens of the
		 * file to simplify end-of-file processing.
		 */
		END_OF_FILE,

		/**
		 * A sequence of characters suitable for an Avail identifier, which
		 * roughly corresponds to characters in a Java identifier.
		 */
		KEYWORD,

		/**
		 * A literal token, detected at lexical scanning time. At the moment
		 * this includes non-negative numeric tokens and strings. Only
		 * applicable for a {@link LiteralTokenDescriptor}.
		 */
		LITERAL,

		/**
		 * A single operator character, which is anything that isn't whitespace,
		 * a keyword character, or an Avail reserved character.
		 */
		OPERATOR,

		/**
		 * A synthetic literal token. Such a token does not occur in the source
		 * text. Only applicable for a {@link LiteralTokenDescriptor}.
		 */
		SYNTHETIC_LITERAL,

		/**
		 * A token that is the entirety of an Avail method/class comment.  This
		 * is text contained between slash-asterisk and asterisk-slash.  Only
		 * applicable for {@link CommentTokenDescriptor}.
		 */
		COMMENT,

		/**
		 * A token representing one or more whitespace characters separating
		 * other tokens.  These tokens are skipped by the normal parsing
		 * machinery, although some day we'll provide a notation within method
		 * names to capture whitespace tokens as arguments.
		 */
		WHITESPACE;

		/** An array of all {@link TokenType} enumeration values. */
		private static final TokenType[] all = values();

		/**
		 * Answer an array of all {@code TokenType} enumeration values.
		 *
		 * @return An array of all {@code TokenType} enum values.  Do not
		 *         modify the array.
		 */
		public static TokenType[] all ()
		{
			return all;
		}

	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return e == LOWER_CASE_STRING
			|| e == TRAILING_WHITESPACE
			|| e == NEXT_LEXING_STATE_POJO;
	}

	/**
	 * Lazily compute and install the lowercase variant of the specified token's
	 * lexeme.  The caller must handle locking as needed.  Cache the lowercase
	 * variant within the object.
	 *
	 * @param token A token.
	 * @return The lowercase lexeme (an Avail string).
	 */
	private A_String lowerCaseStringFrom (final AvailObject token)
	{
		A_String lowerCase = token.slot(LOWER_CASE_STRING);
		if (lowerCase.equalsNil())
		{
			final String nativeOriginal = token.slot(STRING).asNativeString();
			final String nativeLowerCase = nativeOriginal.toLowerCase();
			lowerCase = stringFrom(nativeLowerCase);
			if (isShared())
			{
				lowerCase = lowerCase.traversed().makeShared();
			}
			token.setSlot(LOWER_CASE_STRING, lowerCase);
		}
		return lowerCase;
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsToken(object);
	}

	@Override @AvailMethod
	boolean o_EqualsToken (final AvailObject object, final A_Token aToken)
	{
		return object.string().equals(aToken.string())
			&& object.start() == aToken.start()
			&& object.tokenType() == aToken.tokenType()
			&& object.isLiteralToken() == aToken.isLiteralToken()
			&& (!object.isLiteralToken()
				|| object.literal().equals(aToken.literal()));
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			(object.string().hash() * multiplier
				+ object.start()) * multiplier
				+ object.tokenType().ordinal()
			^ 0x62CE7BA2;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return TOKEN.o();
	}

	@Override @AvailMethod
	A_String o_LeadingWhitespace (final AvailObject object)
	{
		return object.slot(LEADING_WHITESPACE);
	}

	@Override @AvailMethod
	int o_LineNumber (final AvailObject object)
	{
		return object.slot(LINE_NUMBER);
	}

	@Override @AvailMethod
	A_String o_LowerCaseString (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return lowerCaseStringFrom(object);
			}
		}
		return lowerCaseStringFrom(object);
	}

	@Override
	LexingState o_NextLexingStateIn (
		final AvailObject object,
		final CompilationContext compilationContext)
	{
		final AvailObject pojo = object.slot(NEXT_LEXING_STATE_POJO);
		if (pojo.equalsNil())
		{
			// First, figure out where the token ends.
			final A_Tuple string = object.slot(STRING);
			final int stringSize = string.tupleSize();
			final int positionAfter = object.slot(START) + stringSize;
			int line = object.slot(LINE_NUMBER);
			for (int i = 1; i <= stringSize; i++)
			{
				if (string.tupleCodePointAt(i) == '\n')
				{
					line++;
				}
			}
			// Now lookup/capture the next state.
			final LexingState state =
				compilationContext.lexingStateAt(positionAfter, line);
			// Cache it for faster access next time.
			object.setSlot(
				NEXT_LEXING_STATE_POJO, identityPojo(state).makeShared());
			return state;
		}
		return pojo.javaObjectNotNull();
	}

	@Override
	void o_SetNextLexingState (
		final AvailObject object,
		final @Nullable LexingState lexingState)
	{
		final AvailObject pojo = lexingState == null
			? nil
			: identityPojo(lexingState).makeShared();
		object.setSlot(NEXT_LEXING_STATE_POJO, pojo);
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.TOKEN;
	}

	@Override @AvailMethod
	int o_Start (final AvailObject object)
	{
		return object.slot(START);
	}

	@Override @AvailMethod
	A_String o_String (final AvailObject object)
	{
		return object.slot(STRING);
	}

	@Override @AvailMethod
	A_String o_TrailingWhitespace (final AvailObject object)
	{
		return object.slot(TRAILING_WHITESPACE);
	}

	@Override @AvailMethod
	void o_TrailingWhitespace (
		final AvailObject object,
		final A_String trailingWhitespace)
	{
		object.setSlot(TRAILING_WHITESPACE, trailingWhitespace);
	}

	@Override @AvailMethod
	TokenType o_TokenType (final AvailObject object)
	{
		final int index = object.slot(TOKEN_TYPE_CODE);
		return TokenType.all()[index];
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
		writer.endObject();
	}

	/**
	 * Create and initialize a new {@link A_Token}.  The {@link
	 * ObjectSlots#NEXT_LEXING_STATE_POJO} is initially set to {@link
	 * NilDescriptor#nil}.  This indicates that the next lexing state can be
	 * found by examining the {@link CompilationContext#lexingStateAt(int,
	 * int)}, adding the length of the string to {@link IntegerSlots#START} for
	 * the next lexing position, and adding the number of line feeds ({@code
	 * '\n'}) within the {@link ObjectSlots#STRING} to the initial {@link
	 * IntegerSlots#LINE_NUMBER} to calculate the final line number.
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
	 * @param tokenType
	 *        The type of token to create.
	 * @return The new token.
	 */
	public static A_Token newToken (
		final A_String string,
		final A_String leadingWhitespace,
		final A_String trailingWhitespace,
		final int start,
		final int lineNumber,
		final TokenType tokenType)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(STRING, string);
		instance.setSlot(LEADING_WHITESPACE, leadingWhitespace);
		instance.setSlot(TRAILING_WHITESPACE, trailingWhitespace);
		instance.setSlot(LOWER_CASE_STRING, nil);
		instance.setSlot(START, start);
		instance.setSlot(LINE_NUMBER, lineNumber);
		instance.setSlot(TOKEN_TYPE_CODE, tokenType.ordinal());
		instance.setSlot(NEXT_LEXING_STATE_POJO, nil);
		return instance;
	}

	/**
	 * Construct a new {@code TokenDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param typeTag
	 *            The {@link TypeTag} to embed in the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	protected TokenDescriptor (
		final Mutability mutability,
		final TypeTag typeTag,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	/** The mutable {@link TokenDescriptor}. */
	private static final TokenDescriptor mutable =
		new TokenDescriptor(
			Mutability.MUTABLE,
			TypeTag.TOKEN_TAG,
			ObjectSlots.class,
			IntegerSlots.class);

	@Override
	TokenDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link TokenDescriptor}. */
	private static final TokenDescriptor shared =
		new TokenDescriptor(
			Mutability.SHARED,
			TypeTag.TOKEN_TAG,
			ObjectSlots.class,
			IntegerSlots.class);

	@Override
	TokenDescriptor immutable ()
	{
		// Answer the shared descriptor, since there isn't an immutable one.
		return shared;
	}

	@Override
	TokenDescriptor shared ()
	{
		return shared;
	}
}
