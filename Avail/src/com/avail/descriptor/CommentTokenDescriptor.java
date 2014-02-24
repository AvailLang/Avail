/**
 * CommentTokenDescriptor.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import com.avail.annotations.*;
import static com.avail.descriptor.CommentTokenDescriptor.IntegerSlots.*;
import static com.avail.descriptor.CommentTokenDescriptor.ObjectSlots.*;

/**
 * This is a token of an Avail method/class comment.  More specifically, this
 * is text contained between forward slash-asterisk-asterisk and
 * asterisk-forward slash.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class CommentTokenDescriptor extends TokenDescriptor
{
	//this.string().asNativeString() - gets at the string contents.
	/**
	 * My class's slots of type int.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The starting position in the source file. Currently signed 32 bits,
		 * but this may change at some point -- not that we really need to parse
		 * 2GB of <em>Avail</em> source in one file, due to its deeply flexible
		 * syntax.
		 */
		START,

		/**
		 * The line number in the source file. Currently signed 32 bits, which
		 * should be plenty.
		 */
		LINE_NUMBER,

		/**
		 * The {@link Enum#ordinal() ordinal} of the {@link
		 * TokenDescriptor.TokenType} that
		 * indicates what basic kind of token this is.
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

		/**
		 * The module where this comment token appears.
		 */
		MODULE_NAME;

		static
		{
			assert TokenDescriptor.ObjectSlots.STRING.ordinal()
				== STRING.ordinal();
			assert TokenDescriptor.ObjectSlots.LOWER_CASE_STRING.ordinal()
				== LOWER_CASE_STRING.ordinal();
		}
	}

	/**
	 * Create and initialize a new {@linkplain TokenDescriptor token}.
	 *
	 * @param string The token text.
	 * @param start The token's starting character position in the file.
	 * @param lineNumber The line number on which the token occurred.
	 * @param moduleName The name of the module the comment appears in.
	 * @return The new token.
	 */
	public static A_Token create (
		final A_String string,
		final int start,
		final int lineNumber,
		final A_String moduleName)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(STRING, string);
		instance.setSlot(LOWER_CASE_STRING, NilDescriptor.nil());
		instance.setSlot(START, start);
		instance.setSlot(LINE_NUMBER, lineNumber);
		instance.setSlot(TOKEN_TYPE_CODE, TokenType.COMMENT.ordinal());
		instance.setSlot(MODULE_NAME, moduleName);
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

	@Override @AvailMethod
	A_String o_ModuleName (final AvailObject object)
	{
		return object.slot(MODULE_NAME);
	}
}
