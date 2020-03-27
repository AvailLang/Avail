/*
 * CharacterDescriptor.java
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
import com.avail.descriptor.numbers.IntegerDescriptor;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.TypeTag;
import com.avail.exceptions.MarshalingException;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.CharacterDescriptor.IntegerSlots.CODE_POINT;
import static com.avail.descriptor.numbers.IntegerDescriptor.computeHashOfInt;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.tuples.StringDescriptor.stringFrom;
import static com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.types.TupleTypeDescriptor.oneOrMoreOf;
import static com.avail.descriptor.types.TypeDescriptor.Types.CHARACTER;

/**
 * {@code CharacterDescriptor} implements an Avail character. Avail characters
 * are Unicode characters, and their code points fall in the range 0..0x10FFFF,
 * which includes the supplementary multilingual planes.
 *
 * <p>
 * Unlike their use in some languages, characters in Avail are not themselves
 * considered numeric.  They are not a subrange of {@linkplain IntegerDescriptor
 * integers}, and are intended to be treated as different sorts of entities than
 * integers, despite there being simple ways to translate between characters and
 * integers.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class CharacterDescriptor
extends Descriptor
{
	/** The layout of integer slots for my instances. */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The Unicode code point.  Don't bother with a {@link BitField}, as all
		 * uses should restrict this to a valid Unicode range, which fits in 21
		 * bits.
		 */
		CODE_POINT
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append("¢");
		final int codePoint = (int) object.slot(CODE_POINT);
		// Check for linefeed, carriage return, tab, double quote ("), and
		// backslash (\).  These have pretty escape forms inside string
		// literals.
		final int escapeIndex = "\n\r\t\\\"".indexOf(codePoint);
		if (escapeIndex != -1)
		{
			aStream.append("\"\\");
			aStream.append("nrt\\\"".charAt(escapeIndex));
			aStream.append('"');
			return;
		}
		switch (Character.getType(codePoint))
		{
			case Character.COMBINING_SPACING_MARK:
			case Character.CONTROL:
			case Character.ENCLOSING_MARK:
			case Character.FORMAT:
			case Character.NON_SPACING_MARK:
			case Character.PARAGRAPH_SEPARATOR:
			case Character.PRIVATE_USE:
			case Character.SPACE_SEPARATOR:
			case Character.SURROGATE:
			case Character.UNASSIGNED:
			{
				aStream.append(String.format("\"\\(%x)\"", codePoint));
				break;
			}
			default:
			{
				aStream.appendCodePoint(codePoint);
			}
		}
	}

	/**
	 * Answer the hash of the Avail {@link A_Character} with the specified
	 * Unicode code point.
	 *
	 * @param codePoint A Unicode code point.
	 * @return A hash.
	 */
	public static int computeHashOfCharacterWithCodePoint (final int codePoint)
	{
		return computeHashOfInt(codePoint ^ 0xD68E9947);
	}

	/**
	 * Answer the hash of the Avail {@link A_Character} with the specified
	 * unsigned 8-bit Unicode code point.
	 *
	 * @param codePoint An unsigned 8-bit Unicode code point.
	 * @return A hash.
	 */
	public static int hashOfByteCharacterWithCodePoint (final short codePoint)
	{
		assert codePoint >= 0 && codePoint <= 255;
		return hashesOfByteCharacters[codePoint];
	}

	@Override @AvailMethod
	protected int o_CodePoint (final AvailObject object)
	{
		return (int) object.slot(CODE_POINT);
	}

	@Override @AvailMethod
	protected boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsCharacterWithCodePoint(
			(int) object.slot(CODE_POINT));
	}

	@Override @AvailMethod
	protected boolean o_EqualsCharacterWithCodePoint (
		final AvailObject object,
		final int otherCodePoint)
	{
		return object.slot(CODE_POINT) == otherCodePoint;
	}

	@Override @AvailMethod
	protected int o_Hash (final AvailObject object)
	{
		final int codePoint = (int) object.slot(CODE_POINT);
		if (codePoint >= 0 && codePoint <= 255)
		{
			return hashesOfByteCharacters[codePoint];
		}
		return computeHashOfCharacterWithCodePoint(codePoint);
	}

	@Override @AvailMethod
	protected boolean o_IsCharacter (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	protected AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Make the object shared instead.
			object.setDescriptor(shared);
		}
		return object;
	}

	@Override @AvailMethod
	protected AvailObject o_MakeShared (final AvailObject object)
	{
		if (!isShared())
		{
			object.setDescriptor(shared);
		}
		return object;
	}

	@Override @AvailMethod
	protected A_Type o_Kind (final AvailObject object)
	{
		return CHARACTER.o();
	}

	@Override
	protected Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> classHint)
	{
		final int codePoint = (int) object.slot(CODE_POINT);
		// Force marshaling to Java's primitive int type.
		if (int.class.equals(classHint) || Integer.class.equals(classHint))
		{
			return codePoint;
		}
		// Force marshaling to Java's primitive char type, throwing an exception
		// if the code point is out of range.
		if (char.class.equals(classHint)
			|| Character.class.equals(classHint))
		{
			if (codePoint > 65535)
			{
				throw new MarshalingException();
			}
			return (char) codePoint;
		}
		assert classHint == null;
		// Only understand Unicode code points in the basic multilingual plane
		// (BMP) as marshaling to Java's primitive char type.
		if (codePoint < 65536)
		{
			return (char) codePoint;
		}
		// Use Java's primitive int type for all others.
		return codePoint;
	}

	@Override @AvailMethod
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		final int codePoint = (int) object.slot(CODE_POINT);
		if (codePoint < 256)
		{
			return SerializerOperation.BYTE_CHARACTER;
		}
		if (codePoint < 65536)
		{
			return SerializerOperation.SHORT_CHARACTER;
		}
		return SerializerOperation.LARGE_CHARACTER;
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.write(tuple(object));
	}

	/**
	 * Construct a new {@code CharacterDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private CharacterDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.CHARACTER_TAG, null, IntegerSlots.class);
	}

	/** The mutable {@link CharacterDescriptor}. */
	private static final CharacterDescriptor mutable =
		new CharacterDescriptor(Mutability.MUTABLE);

	@Override
	public CharacterDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link CharacterDescriptor}. */
	private static final CharacterDescriptor shared =
		new CharacterDescriptor(Mutability.SHARED);

	@Override
	public CharacterDescriptor immutable ()
	{
		// There is no immutable variant; answer the shared descriptor.
		return shared;
	}

	@Override
	public CharacterDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Answer a shared Avail {@link A_Character character} for the specified
	 * Unicode code point.
	 *
	 * @param codePoint A Unicode code point.
	 * @return An {@link AvailObject}.
	 */
	public static A_Character fromCodePoint (final int codePoint)
	{
		if (codePoint >= 0 && codePoint <= 255)
		{
			return byteCharacters[codePoint];
		}
		final AvailObject result = mutable.create();
		result.setSlot(CODE_POINT, codePoint);
		result.makeShared();
		return result;
	}

	/**
	 * Answer an already instantiated Avail {@link A_Character character} for
	 * the specified unsigned 8-bit Unicode code point.
	 *
	 * @param codePoint An unsigned 8-bit Unicode code point.
	 * @return An {@link AvailObject}.
	 */
	public static A_Character fromByteCodePoint (final short codePoint)
	{
		assert codePoint >= 0 && codePoint <= 255;
		return byteCharacters[codePoint];
	}

	/** The first 256 Unicode characters. */
	private static final AvailObject[] byteCharacters;

	static
	{
		byteCharacters = new AvailObject[256];
		for (int i = 0; i < byteCharacters.length; i++)
		{
			final AvailObject object = mutable.create();
			object.setSlot(CODE_POINT, i);
			byteCharacters[i] = object.makeShared();
		}
	}

	/** The hashes of the first 256 Unicode characters. */
	private static final int[] hashesOfByteCharacters = new int[256];

	static
	{
		for (int i = 0; i <= 255; i++)
		{
			hashesOfByteCharacters[i] = computeHashOfCharacterWithCodePoint(i);
		}
	}

	/** The maximum code point value as an {@code int}. */
	public static final int maxCodePointInt = Character.MAX_CODE_POINT;

	/** A type that contains all ASCII decimal digit characters. */
	public static final A_Type digitsType =
		enumerationWith(stringFrom("0123456789").asSet()).makeShared();

	/** The type for non-empty strings of ASCII decimal digits. */
	public static final A_Type nonemptyStringOfDigitsType =
		oneOrMoreOf(digitsType);
}
