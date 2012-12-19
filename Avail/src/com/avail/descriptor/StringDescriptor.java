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
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.Generator;

/**
 * {@code StringDescriptor} has Avail strings as its instances. The actual
 * representation of Avail strings is determined by subclasses.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see ByteStringDescriptor
 * @see TwoByteStringDescriptor
 */
public abstract class StringDescriptor
extends TupleDescriptor
{
	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		final int size = object.tupleSize();
		for (int i = 1; i <= size; i++)
		{
			final int codePoint = object.rawShortForCharacterAt(i);
			if (codePoint >= 256)
			{
				return SerializerOperation.SHORT_STRING;
			}
		}
		return SerializerOperation.BYTE_STRING;
	}

	/**
	 * A tuple containing just the underscore character.
	 */
	private static AvailObject underscore;

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
	 * A tuple containing just the open-guillemet character.
	 */
	private static AvailObject openGuillemet;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the open-guillemet character ("«").
	 *
	 * @return A tuple containing just the open-guillemet character.
	 */
	public static AvailObject openGuillemet ()
	{
		return openGuillemet;
	}

	/**
	 * A tuple containing just the close-guillemet character.
	 */
	private static AvailObject closeGuillemet;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the close-guillemet character ("»").
	 *
	 * @return A tuple containing just the close-guillemet character.
	 */
	public static AvailObject closeGuillemet ()
	{
		return closeGuillemet;
	}

	/**
	 * A tuple containing just the double-dagger character.
	 */
	private static AvailObject doubleDagger;

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
	private static AvailObject backQuote;

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
	private static AvailObject ellipsis;

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
	 * A tuple containing just the octothorp character.
	 */
	private static AvailObject octothorp;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the octothorp character ("#").
	 *
	 * @return A tuple containing just the octothorp character.
	 */
	public static AvailObject octothorp ()
	{
		return octothorp;
	}

	/**
	 * A tuple containing just the question mark character.
	 */
	private static AvailObject questionMark;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the question mark character ("?").
	 *
	 * @return A tuple containing just the question mark character.
	 */
	public static AvailObject questionMark ()
	{
		return questionMark;
	}

	/**
	 * A tuple containing just the double question mark character.
	 */
	private static AvailObject doubleQuestionMark;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the double question mark character ("⁇").
	 *
	 * @return A tuple containing just the question mark character.
	 */
	public static AvailObject doubleQuestionMark ()
	{
		return doubleQuestionMark;
	}

	/**
	 * A tuple containing just the exclamation mark character.
	 */
	private static AvailObject exclamationMark;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the exclamation mark character ("!").
	 *
	 * @return A tuple containing just the exclamation mark character.
	 */
	public static AvailObject exclamationMark ()
	{
		return exclamationMark;
	}

	/**
	 * A tuple containing just the tilde character.
	 */
	private static AvailObject tilde;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the tilde character ("~").
	 *
	 * @return A tuple containing just the question mark character.
	 */
	public static AvailObject tilde ()
	{
		return tilde;
	}

	/**
	 * A tuple containing just the vertical bar character.
	 */
	private static AvailObject verticalBar;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the vertical bar character ("|").
	 *
	 * @return A tuple containing just the vertical bar character.
	 */
	public static AvailObject verticalBar ()
	{
		return verticalBar;
	}

	/**
	 * A tuple containing just the single-dagger (†) character.
	 */
	private static AvailObject singleDagger;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the (single-)dagger character ("†").
	 *
	 * @return A tuple containing just the single dagger character.
	 */
	public static AvailObject singleDagger ()
	{
		return singleDagger;
	}

	/**
	 * A tuple containing just the section sign character.
	 */
	private static AvailObject sectionSign;

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the section sign character ("§").
	 *
	 * @return A tuple containing just the section sign character.
	 */
	public static AvailObject sectionSign ()
	{
		return sectionSign;
	}

	/**
	 * Create any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	static void createWellKnownObjects ()
	{
		underscore = from("_").makeImmutable();
		openGuillemet = from("«").makeImmutable();
		closeGuillemet = from("»").makeImmutable();
		doubleDagger = from("‡").makeImmutable();
		backQuote = from("`").makeImmutable();
		ellipsis = from("…").makeImmutable();
		octothorp = from("#").makeImmutable();
		questionMark = from("?").makeImmutable();
		doubleQuestionMark = from("⁇").makeImmutable();
		exclamationMark = from("!").makeImmutable();
		tilde = from("~").makeImmutable();
		verticalBar = from("|").makeImmutable();
		singleDagger = from("†").makeImmutable();
		sectionSign = from("§").makeImmutable();
	}

	/**
	 * Destroy or reset any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	static void clearWellKnownObjects ()
	{
		underscore = null;
		openGuillemet = null;
		closeGuillemet = null;
		doubleDagger = null;
		backQuote = null;
		ellipsis = null;
		octothorp = null;
		questionMark = null;
		doubleQuestionMark = null;
		exclamationMark = null;
		tilde = null;
		verticalBar = null;
		singleDagger = null;
		sectionSign = null;
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
	public static AvailObject from (
		final String aNativeString)
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

	/**
	 * Create an object of the appropriate size, whose descriptor is an instance
	 * of {@link ByteStringDescriptor}.  Note that it can only store Latin-1
	 * characters (i.e., those having Unicode code points 0..255).  Run the
	 * descriptor for each position in ascending order to produce the code
	 * points with which to populate the string.
	 *
	 * @param size The size of byte string to create.
	 * @param generator A generator to provide code points to store.
	 * @return The new Avail {@linkplain ByteStringDescriptor string}.
	 */
	public static AvailObject mutableByteStringFromGenerator(
		final int size,
		final Generator<Integer> generator)
	{
		return ByteStringDescriptor.generateByteString(
			size,
			generator);
	}

	/**
	 * Create an object of the appropriate size, whose descriptor is an instance
	 * of {@link TwoByteStringDescriptor}.  Note that it can only store Unicode
	 * characters from the Basic Multilingual Plane (i.e., those having Unicode
	 * code points 0..65535).  Run the generator for each position in ascending
	 * order to produce the code points with which to populate the string.
	 *
	 * @param size The size of two-byte string to create.
	 * @param generator A generator to provide code points to store.
	 * @return The new Avail {@linkplain TwoByteStringDescriptor string}.
	 */
	public static AvailObject mutableTwoByteStringFromGenerator(
		final int size,
		final Generator<Integer> generator)
	{
		return TwoByteStringDescriptor.generateTwoByteString(
			size,
			generator);
	}
}
