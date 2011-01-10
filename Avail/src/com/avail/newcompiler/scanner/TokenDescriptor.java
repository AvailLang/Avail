/**
 * newcompiler/TokenDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.newcompiler.scanner;

import static com.avail.descriptor.AvailObject.Multiplier;
import com.avail.annotations.EnumField;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;


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
	{
		/**
		 * The {@link ByteStringDescriptor string}, exactly as I appeared in the
		 * source.
		 */
		STRING
	}

	/**
	 * My class's slots of type int.
	 */
	public enum IntegerSlots
	{
		/**
		 * The starting position in the source file.  Currently signed 32 bits,
		 * but this may change at some point -- not that we really need to parse
		 * 2GB of <em>Avail</em> source in one file, due to its deeply flexible
		 * syntax.
		 */
		START,

		/**
		 * The {@link Enum#ordinal() ordinal} of the {@link TokenType} that
		 * indicates what basic kind of token this is.
		 */
		@EnumField(describedBy=TokenType.class)
		TOKEN_TYPE_CODE
	}


	/**
	 * Setter for field string.
	 */
	@Override
	public void o_String (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.STRING, value);
	}

	/**
	 * Getter for field string.
	 */
	@Override
	public AvailObject o_String (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.STRING);
	}

	/**
	 * Setter for field start.
	 */
	@Override
	public void o_Start (
		final AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.START, value);
	}

	/**
	 * Getter for field start.
	 */
	@Override
	public int o_Start (
		final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.START);
	}

	/**
	 * Setter for field tokenTypeCode.
	 */
	@Override
	public void o_TokenType (
		final AvailObject object,
		final TokenDescriptor.TokenType value)
	{
		object.integerSlotPut(IntegerSlots.TOKEN_TYPE_CODE, value.ordinal());
	}

	/**
	 * Getter for field tokenTypeCode.
	 */
	@Override
	public TokenDescriptor.TokenType o_TokenType (
		final AvailObject object)
	{
		final int index = object.integerSlot(IntegerSlots.TOKEN_TYPE_CODE);
		return TokenDescriptor.TokenType.values()[index];
	}

	@Override
	public AvailObject o_Type (final AvailObject object)
	{
		return Types.token.object();
	}

	@Override
	public AvailObject o_ExactType (final AvailObject object)
	{
		return Types.token.object();
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return
			(object.string().hash() * Multiplier
				+ object.start()) * Multiplier
				+ object.tokenType().ordinal()
			^ 0x62CE7BA2;
	}

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return object.type().equals(another.type())
			&& object.string().equals(another.string())
			&& object.start() == another.start()
			&& object.tokenType() == another.tokenType();
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
	private final static TokenDescriptor mutable = new TokenDescriptor(true);

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
	private final static TokenDescriptor immutable = new TokenDescriptor(false);

	/**
	 * Answer the immutable {@link TokenDescriptor}.
	 *
	 * @return The immutable {@link TokenDescriptor}.
	 */
	public static TokenDescriptor immutable ()
	{
		return immutable;
	}



	/**
	 * An enumeration that lists the basic kinds of tokens that can be
	 * encountered.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum TokenType
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
		 * A literal token, detected at lexical scanning time.  At the moment
		 * this includes non-negative numeric tokens and strings.
		 */
		LITERAL,

		/**
		 * A single operator character, which is anything that isn't whitespace,
		 * a keyword character, or an Avail reserved character such as
		 * semicolon.
		 */
		OPERATOR;
	}

}
