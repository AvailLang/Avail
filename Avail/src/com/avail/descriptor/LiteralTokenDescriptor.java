/**
 * LiteralTokenDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.TypeDescriptor.Types.LITERAL_TOKEN;
import com.avail.annotations.*;

/**
 * I represent a token that's a literal representation of some object.
 *
 * <p>In addition to the state inherited from {@link TokenDescriptor}, I add a
 * field to hold the literal value itself.</p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class LiteralTokenDescriptor
extends TokenDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.  Note that they have to start the
	 * same as in my superclass {@link TokenDescriptor}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
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
		 * The actual {@link AvailObject} wrapped by this token.
		 */
		LITERAL;

		static
		{
			assert TokenDescriptor.ObjectSlots.STRING.ordinal()
				== STRING.ordinal();
		}
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
		 * The line number in the source file.  Currently signed 32bits, which
		 * should be plenty.
		 */
		LINE_NUMBER,

		/**
		 * The {@link Enum#ordinal() ordinal} of the {@link
		 * TokenDescriptor.TokenType} that indicates what basic kind of token
		 * this is.
		 */
		@EnumField(describedBy=TokenType.class)
		TOKEN_TYPE_CODE;

		static
		{
			assert TokenDescriptor.IntegerSlots.START.ordinal()
				== START.ordinal();
			assert TokenDescriptor.IntegerSlots.LINE_NUMBER.ordinal()
				== LINE_NUMBER.ordinal();
			assert TokenDescriptor.IntegerSlots.TOKEN_TYPE_CODE.ordinal()
				== TOKEN_TYPE_CODE.ordinal();
		}

	}


	@Override @AvailMethod
	void o_Literal (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		object.setSlot(ObjectSlots.LITERAL, value);
	}

	@Override @AvailMethod
	AvailObject o_Literal (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.LITERAL);
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		return LITERAL_TOKEN.o();
	}

	/**
	 * Construct a new {@link LiteralTokenDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected LiteralTokenDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link LiteralTokenDescriptor}.
	 */
	private final static LiteralTokenDescriptor mutable =
		new LiteralTokenDescriptor(true);

	/**
	 * Answer the mutable {@link LiteralTokenDescriptor}.
	 *
	 * @return The mutable {@link LiteralTokenDescriptor}.
	 */
	public static LiteralTokenDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link LiteralTokenDescriptor}.
	 */
	private final static LiteralTokenDescriptor immutable =
		new LiteralTokenDescriptor(false);

	/**
	 * Answer the immutable {@link LiteralTokenDescriptor}.
	 *
	 * @return The immutable {@link LiteralTokenDescriptor}.
	 */
	public static LiteralTokenDescriptor immutable ()
	{
		return immutable;
	}
}
