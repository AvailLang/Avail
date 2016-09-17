/**
 * StringDescriptor.java
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

import com.avail.annotations.AvailMethod;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.*;
import com.avail.utility.json.JSONWriter;
import org.jetbrains.annotations.Nullable;

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
	@Override @AvailMethod
	boolean o_IsString (final AvailObject object)
	{
		return true;
	}

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

	@Override
	boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		if (Types.CHARACTER.o().isSubtypeOf(type))
		{
			return true;
		}
		return super.o_TupleElementsInRangeAreInstancesOf(
			object, startIndex, endIndex, type);
	}

	@Override
	final void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.write(object.asNativeString());
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
	public static A_String from (final String aNativeString)
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
		final A_String tuple = ObjectTupleDescriptor.generateFrom(
			count,
			new Generator<A_BasicObject>()
			{
				private int charIndex = 0;

				@Override
				public A_BasicObject value ()
				{
					final int codePoint = aNativeString.codePointAt(charIndex);
					charIndex += Character.charCount(codePoint);
					return CharacterDescriptor.fromCodePoint(codePoint);
				}
			});
		return tuple;
	}

	/**
	 * Given a Java {@link String} containing a {@linkplain String#format(
	 * String, Object...) substitution format} and its arguments, perform
	 * pattern substitution and produce the corresponding Avail {@link A_String
	 * string}.
	 *
	 * @param pattern A substitution pattern.
	 * @param args The arguments to substitute into the pattern.
	 * @return An Avail string.
	 */
	public static A_String format (final String pattern, final Object... args)
	{
		return from(String.format(pattern, args));
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
	public static A_String mutableByteStringFromGenerator(
		final int size,
		final Generator<Character> generator)
	{
		return ByteStringDescriptor.generateByteString(
			size, generator);
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
	public static A_String mutableTwoByteStringFromGenerator(
		final int size,
		final Generator<Character> generator)
	{
		return TwoByteStringDescriptor.generateTwoByteString(
			size, generator);
	}

	/** A tuple containing just the underscore character. */
	private static final A_String underscore = from("_").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the underscore character ("_").
	 *
	 * @return A tuple containing just the underscore character.
	 */
	public static A_String underscore ()
	{
		return underscore;
	}

	/** A tuple containing just the open-guillemet character. */
	private static final A_String openGuillemet = from("«").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the open-guillemet character ("«").
	 *
	 * @return A tuple containing just the open-guillemet character.
	 */
	public static A_String openGuillemet ()
	{
		return openGuillemet;
	}

	/** A tuple containing just the close-guillemet character. */
	private static final A_String closeGuillemet = from("»").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the close-guillemet character ("»").
	 *
	 * @return A tuple containing just the close-guillemet character.
	 */
	public static A_String closeGuillemet ()
	{
		return closeGuillemet;
	}

	/** A tuple containing just the open-single-guillemet character. */
	private static final A_String openSingleGuillemet = from("‹").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the open-single guillemet character ("‹").
	 *
	 * @return A tuple containing just the open-single-guillemet character.
	 */
	public static A_String openSingleGuillemet ()
	{
		return openSingleGuillemet;
	}

	/** A tuple containing just the close-single-guillemet character. */
	private static final A_String closeSingleGuillemet = from("›").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the close-single-guillemet character ("›").
	 *
	 * @return A tuple containing just the close-single-guillemet character.
	 */
	public static A_String closeSingleGuillemet ()
	{
		return closeSingleGuillemet;
	}

	/** A tuple containing just the double-dagger character. */
	private static final A_String doubleDagger = from("‡").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the double dagger character ("‡").
	 *
	 * @return A tuple containing just the double dagger character.
	 */
	public static A_String doubleDagger ()
	{
		return doubleDagger;
	}

	/** A tuple containing just the back-quote character. */
	private static final A_String backQuote = from("`").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the back-quote character ("`").
	 *
	 * @return A tuple containing just the back-quote character.
	 */
	public static A_String backQuote ()
	{
		return backQuote;
	}

	/** A tuple containing just the ellipsis character. */
	private static final A_String ellipsis = from("…").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the ellipsis character ("…").
	 *
	 * @return A tuple containing just the ellipsis character.
	 */
	public static A_String ellipsis ()
	{
		return ellipsis;
	}

	/** A tuple containing just the octothorp character. */
	private static final A_String octothorp = from("#").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the octothorp character ("#").
	 *
	 * @return A tuple containing just the octothorp character.
	 */
	public static A_String octothorp ()
	{
		return octothorp;
	}

	/** A tuple containing just the dollar sign character. */
	private static final A_String dollarSign = from("$").makeShared();

	/**
	 * Returns an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the dollar sign character ("$").
	 *
	 * @return A tuple containing just the dollar sign character.
	 */
	public static A_String dollarSign()
	{
		return dollarSign;
	}

	/** A tuple containing just the question mark character. */
	private static final A_String questionMark = from("?").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the question mark character ("?").
	 *
	 * @return A tuple containing just the question mark character.
	 */
	public static A_String questionMark ()
	{
		return questionMark;
	}

	/** A tuple containing just the double question mark character. */
	private static final A_String doubleQuestionMark = from("⁇").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the double question mark character ("⁇").
	 *
	 * @return A tuple containing just the question mark character.
	 */
	public static A_String doubleQuestionMark ()
	{
		return doubleQuestionMark;
	}

	/** A tuple containing just the exclamation mark character. */
	private static final A_String exclamationMark = from("!").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the exclamation mark character ("!").
	 *
	 * @return A tuple containing just the exclamation mark character.
	 */
	public static A_String exclamationMark ()
	{
		return exclamationMark;
	}

	/** A tuple containing just the up arrow character. */
	private static final A_String upArrow = from("↑").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the up arrow character ("↑").
	 *
	 * @return A tuple containing just the up arrow character.
	 */
	public static A_String upArrow ()
	{
		return upArrow;
	}

	/** A tuple containing just the tilde character. */
	private static final A_String tilde = from("~").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the tilde character ("~").
	 *
	 * @return A tuple containing just the question mark character.
	 */
	public static A_String tilde ()
	{
		return tilde;
	}

	/** A tuple containing just the vertical bar character. */
	private static final A_String verticalBar = from("|").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the vertical bar character ("|").
	 *
	 * @return A tuple containing just the vertical bar character.
	 */
	public static A_String verticalBar ()
	{
		return verticalBar;
	}

	/** A tuple containing just the single-dagger (†) character. */
	private static final A_String singleDagger = from("†").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the (single-)dagger character ("†").
	 *
	 * @return A tuple containing just the single dagger character.
	 */
	public static A_String singleDagger ()
	{
		return singleDagger;
	}

	/** A tuple containing just the section sign character. */
	private static final A_String sectionSign = from("§").makeShared();

	/**
	 * Return an Avail {@linkplain StringDescriptor string} of size one,
	 * consisting of just the section sign character ("§").
	 *
	 * @return A tuple containing just the section sign character.
	 */
	public static A_String sectionSign ()
	{
		return sectionSign;
	}

	/**
	 * Construct a new {@link StringDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	protected StringDescriptor (
		final Mutability mutability,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, objectSlotsEnumClass, integerSlotsEnumClass);
	}
}
