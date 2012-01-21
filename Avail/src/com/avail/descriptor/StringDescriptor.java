/**
 * StringDescriptor.java
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

import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;

/**
 * {@code StringDescriptor} has Avail strings as its instances. The actual
 * representation of Avail strings is determined by subclasses.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 * @see ByteStringDescriptor
 * @see TwoByteStringDescriptor
 */
public abstract class StringDescriptor
extends TupleDescriptor
{
	/**
	 * A tuple containing just the underscore character.
	 */
	static AvailObject underscore;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the underscore character ("_").
	 *
	 * @return A tuple containing just the underscore character.
	 */
	public static AvailObject underscore ()
	{
		return underscore;
	}

	/**
	 * A tuple containing just the open-chevron character.
	 */
	static AvailObject openChevron;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the open-chevron character ("«").
	 *
	 * @return A tuple containing just the open-chevron character.
	 */
	public static AvailObject openChevron ()
	{
		return openChevron;
	}

	/**
	 * A tuple containing just the close-chevron character.
	 */
	static AvailObject closeChevron;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the close-chevron character ("»").
	 *
	 * @return A tuple containing just the close-chevron character.
	 */
	public static AvailObject closeChevron ()
	{
		return closeChevron;
	}

	/**
	 * A tuple containing just the double-dagger character.
	 */
	static AvailObject doubleDagger;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the double dagger character ("‡").
	 *
	 * @return A tuple containing just the double dagger character.
	 */
	public static AvailObject doubleDagger ()
	{
		return doubleDagger;
	}

	/**
	 * A tuple containing just the back-quote character.
	 */
	static AvailObject backQuote;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the back-quote character ("`").
	 *
	 * @return A tuple containing just the back-quote character.
	 */
	public static AvailObject backQuote ()
	{
		return backQuote;
	}

	/**
	 * A tuple containing just the ellipsis character.
	 */
	static AvailObject ellipsis;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the ellipsis character ("…").
	 *
	 * @return A tuple containing just the ellipsis character.
	 */
	public static AvailObject ellipsis ()
	{
		return ellipsis;
	}

	/**
	 * Create any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	static void createWellKnownObjects ()
	{
		underscore = StringDescriptor.from("_").makeImmutable();
		openChevron = StringDescriptor.from("«").makeImmutable();
		closeChevron = StringDescriptor.from("»").makeImmutable();
		doubleDagger = StringDescriptor.from("‡").makeImmutable();
		backQuote = StringDescriptor.from("`").makeImmutable();
		ellipsis = StringDescriptor.from("…").makeImmutable();
	}

	/**
	 * Destroy or reset any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	static void clearWellKnownObjects ()
	{
		underscore = null;
		openChevron = null;
		closeChevron = null;
		doubleDagger = null;
		backQuote = null;
		ellipsis = null;
	}

	/**
	 * Construct a new {@link StringDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected StringDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * Convert the specified Java {@link String} to an Avail {@linkplain
	 * StringDescriptor string}.
	 *
	 * <p>NB: The {@linkplain AbstractDescriptor descriptor} type of the actual
	 * instance returned varies with the contents of the Java {@code String}. If
	 * the Java {@code String} contains only Latin-1 characters, then the
	 * descriptor will be {@link ByteStringDescriptor}; otherwise it will be
	 * {@link TwoByteStringDescriptor}.</p>
	 *
	 * @param aNativeString A Java {@link String}.
	 * @return A corresponding Avail {@linkplain StringDescriptor string}.
	 */
	public static @NotNull AvailObject from (
		final @NotNull String aNativeString)
	{
		final int charCount = aNativeString.length();
		if (charCount == 0)
		{
			return empty();
		}
		int maxCodePoint = 0;
		int count = 0;
		int index = 0;
		while (index < charCount)
		{
			final int codePoint = aNativeString.codePointAt(index);
			maxCodePoint = Math.max(maxCodePoint, codePoint);
			count++;
			index += Character.charCount(codePoint);
		}
		if (maxCodePoint <= 255)
		{
			return ByteStringDescriptor.mutableObjectFromNativeByteString(
				aNativeString);
		}
		else if (maxCodePoint <= 65535)
		{
			return TwoByteStringDescriptor.mutableObjectFromNativeTwoByteString(
				aNativeString);
		}
		// Fall back to building a general object tuple containing Avail
		// character objects.
		final AvailObject tuple = ObjectTupleDescriptor.mutable().create(count);
		// Make it pointer-safe first, since we'll be allocating character
		// objects.
		for (int i = 1; i <= count; i++)
		{
			tuple.tupleAtPut(i, NullDescriptor.nullObject());
		}
		index = 0;
		count = 1;  // One-based tuple index
		while (index < charCount)
		{
			final int codePoint = aNativeString.codePointAt(index);
			tuple.tupleAtPut(
				count,
				CharacterDescriptor.fromCodePoint(
					codePoint));
			count++;
			index += Character.charCount(codePoint);
		}
		assert count == tuple.tupleSize() + 1;
		return tuple;
	}
}
