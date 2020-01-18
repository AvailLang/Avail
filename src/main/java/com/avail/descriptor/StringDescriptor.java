/*
 * StringDescriptor.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
import com.avail.descriptor.tuples.A_String;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.MutableInt;
import com.avail.utility.json.JSONWriter;
import javax.annotation.Nullable;
import static com.avail.descriptor.ByteStringDescriptor.mutableObjectFromNativeByteString;
import static com.avail.descriptor.CharacterDescriptor.fromCodePoint;
import static com.avail.descriptor.ObjectTupleDescriptor.generateObjectTupleFrom;
import static com.avail.descriptor.TwoByteStringDescriptor.generateTwoByteString;
import static com.avail.descriptor.TwoByteStringDescriptor.mutableObjectFromNativeTwoByteString;
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
	protected boolean o_IsString (final AvailObject object)
	{
		return true;
	}
	@Override @AvailMethod @ThreadSafe
	protected SerializerOperation o_SerializerOperation (
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
	protected abstract int o_TupleCodePointAt (
		final AvailObject object, final int index);
	@Override
	protected boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		return Types.CHARACTER.o().isSubtypeOf(type)
			|| super.o_TupleElementsInRangeAreInstancesOf(
			object, startIndex, endIndex, type);
	}
	@Override @AvailMethod
	protected final int o_TupleIntAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}
	@Override
	protected final void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.write(object.asNativeString());
	}
	/**
	 * Convert the specified Java {@link String} to an Avail {@link A_String},
	 * but keeping any Java surrogate pairs as two distinct values in the Avail
	 * string.  Note that such a string is semantically different from what
	 * would be produced by {@link #stringFrom(String)}, and isn't even
	 * necessarily the same length.  This operation is intended for
	 * compatibility with Java (and JavaScript) strings.
	 *
	 * <p>NB: The {@linkplain AbstractDescriptor descriptor} type of the actual
	 * instance returned varies with the contents of the Java {@code String}. If
	 * the Java {@code String} contains only Latin-1 characters, then the
	 * descriptor will be {@link ByteStringDescriptor}; otherwise it will be
	 * {@link TwoByteStringDescriptor}.</p>
	 *
	 * @param aNativeString
	 *        A Java {@link String}.
	 * @return An Avail {@code StringDescriptor string} having the same length,
	 *         but with surrogate pairs (D800-DBFF and DC00-DFFF) preserved in
	 *         the Avail string.
	 */
	public static A_String stringWithSurrogatesFrom (final String aNativeString)
	{
		final int charCount = aNativeString.length();
		if (charCount == 0)
		{
			return emptyTuple();
		}
		int maxChar = 0;
		int index = 0;
		while (index < charCount)
		{
			final char aChar = aNativeString.charAt(index);
			maxChar = Math.max(maxChar, aChar);
			index ++;
		}
		if (maxChar <= 255)
		{
			return mutableObjectFromNativeByteString(aNativeString);
		}
		// Pack it into a TwoByteString, preserving surrogates.
		return generateTwoByteString(
			aNativeString.length(),
			i -> aNativeString.charAt(i - 1));
	}

	/**
	 * Convert the specified Java {@link String} to an Avail {@link A_String}.
	 *
	 * <p>NB: The {@linkplain AbstractDescriptor descriptor} type of the actual
	 * instance returned varies with the contents of the Java {@code String}. If
	 * the Java {@code String} contains only Latin-1 characters, then the
	 * descriptor will be {@link ByteStringDescriptor}; otherwise it will be
	 * {@link TwoByteStringDescriptor}.</p>
	 *
	 * @param aNativeString A Java {@link String}.
	 * @return A corresponding Avail {@code StringDescriptor string}.
	 */
	public static A_String stringFrom (final String aNativeString)
	{
		final int charCount = aNativeString.length();
		if (charCount == 0)
		{
			return emptyTuple();
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
			return mutableObjectFromNativeByteString(aNativeString);
		}
		if (maxCodePoint <= 65535)
		{
			return mutableObjectFromNativeTwoByteString(aNativeString);
		}
		// Fall back to building a general object tuple containing Avail
		// character objects.
		final MutableInt charIndex = new MutableInt(0);
		return generateObjectTupleFrom(
			count,
			ignored -> {
				final int codePoint =
					aNativeString.codePointAt(charIndex.value);
				charIndex.value += Character.charCount(codePoint);
				return fromCodePoint(codePoint);
			});
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
	public static A_String formatString (
		final String pattern,
		final Object... args)
	{
		return stringFrom(String.format(pattern, args));
	}
	/**
	 * Construct a new {@code StringDescriptor}.
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
