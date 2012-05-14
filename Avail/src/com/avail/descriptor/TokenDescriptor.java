/**
 * TokenDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.AvailObject.Multiplier;
import static com.avail.descriptor.TokenDescriptor.IntegerSlots.*;
import static com.avail.descriptor.TokenDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.TOKEN;
import com.avail.annotations.*;
import com.avail.descriptor.Descriptor;


/**
 * I represent a token scanned from Avail source code.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class TokenDescriptor
extends Descriptor
{
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
		LOWER_CASE_STRING
	}

	/**
	 * My class's slots of type int.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The starting position in the source file.  Currently signed 32 bits,
		 * but this may change at some point -- not that we really need to parse
		 * 2GB of <em>Avail</em> source in one file, due to its deeply flexible
		 * syntax.
		 */
		START,

		/**
		 * The line number in the source file.  Currently signed 32 bits, which
		 * should be plenty.
		 */
		LINE_NUMBER,

		/**
		 * The {@link Enum#ordinal() ordinal} of the {@link TokenType} that
		 * indicates what basic kind of token this is.
		 */
		@EnumField(describedBy=TokenType.class)
		TOKEN_TYPE_CODE
	}

	/**
	 * An enumeration that lists the basic kinds of tokens that can be
	 * encountered.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
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
		 * The semicolon character may not (at least on 2010.12.28) be used as
		 * an operator character.  This token type is used to prevent seeing a
		 * semicolon as an operator.
		 */
		END_OF_STATEMENT,

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
		 * A power string token, detected at lexical scanning time. Actually,
		 * that "scanning time" distinction is a bit vacuous, since lexical
		 * scanning is fully incremental and on-demand, specifically to support
		 * power strings. This token type is only applicable for a {@link
		 * PowerStringTokenDescriptor power string token}.
		 */
		POWER_STRING,

		/**
		 * A single operator character, which is anything that isn't whitespace,
		 * a keyword character, or an Avail reserved character such as
		 * semicolon.
		 */
		OPERATOR,

		/**
		 * A synthetic literal token. Such a token does not occur in the source
		 * text. Only applicable for a {@link LiteralTokenDescriptor}.
		 */
		SYNTHETIC_LITERAL;
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == LOWER_CASE_STRING;
	}

	@Override @AvailMethod
	void o_String (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(STRING, value);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_String (final @NotNull AvailObject object)
	{
		return object.slot(STRING);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_LowerCaseString (final @NotNull AvailObject object)
	{
		AvailObject lowerCase = object.slot(LOWER_CASE_STRING);
		if (lowerCase.equalsNull())
		{
			final String nativeOriginal = object.slot(STRING).asNativeString();
			final String nativeLowerCase = nativeOriginal.toLowerCase();
			lowerCase = StringDescriptor.from(nativeLowerCase);
			object.setSlot(LOWER_CASE_STRING, lowerCase);
		}
		return lowerCase;
	}

	@Override @AvailMethod
	void o_Start (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(START, value);
	}

	@Override @AvailMethod
	int o_Start (final @NotNull AvailObject object)
	{
		return object.slot(START);
	}

	/**
	 * Setter for field lineNumber.
	 */
	@Override @AvailMethod
	void o_LineNumber (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(LINE_NUMBER, value);
	}

	/**
	 * Getter for field lineNumber.
	 */
	@Override @AvailMethod
	int o_LineNumber (final @NotNull AvailObject object)
	{
		return object.slot(LINE_NUMBER);
	}

	/**
	 * Setter for field tokenTypeCode.
	 */
	@Override @AvailMethod
	void o_TokenType (
		final @NotNull AvailObject object,
		final @NotNull TokenType value)
	{
		object.setSlot(TOKEN_TYPE_CODE, value.ordinal());
	}

	/**
	 * Getter for field tokenTypeCode.
	 */
	@Override @AvailMethod
	@NotNull TokenType o_TokenType (final @NotNull AvailObject object)
	{
		final int index = object.slot(TOKEN_TYPE_CODE);
		return TokenType.values()[index];
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (final @NotNull AvailObject object)
	{
		return TOKEN.o();
	}

	@Override @AvailMethod
	int o_Hash (final @NotNull AvailObject object)
	{
		return
			(object.string().hash() * Multiplier
				+ object.start()) * Multiplier
				+ object.tokenType().ordinal()
			^ 0x62CE7BA2;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return object.kind().equals(another.kind())
			&& object.string().equals(another.string())
			&& object.start() == another.start()
			&& object.tokenType() == another.tokenType();
	}


	/**
	 * Create and initialize a new {@linkplain TokenDescriptor token}.
	 *
	 * @param string The token text.
	 * @param start The token's starting character position in the file.
	 * @param lineNumber The line number on which the token occurred.
	 * @param tokenType The type of token to create.
	 * @return The new token.
	 */
	public static @NotNull AvailObject create (
		final @NotNull AvailObject string,
		final int start,
		final int lineNumber,
		final @NotNull TokenType tokenType)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(STRING, string);
		instance.setSlot(LOWER_CASE_STRING, NullDescriptor.nullObject());
		instance.setSlot(START, start);
		instance.setSlot(LINE_NUMBER, lineNumber);
		instance.setSlot(TOKEN_TYPE_CODE, tokenType.ordinal());
		return instance;
	}

	/**
	 * Construct a new {@link TokenDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected TokenDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link TokenDescriptor}.
	 */
	private static final TokenDescriptor mutable = new TokenDescriptor(true);

	/**
	 * Answer the mutable {@link TokenDescriptor}.
	 *
	 * @return The mutable {@link TokenDescriptor}.
	 */
	public static TokenDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link TokenDescriptor}.
	 */
	private static final TokenDescriptor immutable = new TokenDescriptor(false);

	/**
	 * Answer the immutable {@link TokenDescriptor}.
	 *
	 * @return The immutable {@link TokenDescriptor}.
	 */
	public static TokenDescriptor immutable ()
	{
		return immutable;
	}
}
