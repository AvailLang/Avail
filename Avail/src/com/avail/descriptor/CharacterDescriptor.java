/**
 * CharacterDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.CHARACTER;
import java.util.*;
import com.avail.annotations.*;
import com.avail.exceptions.MarshalingException;
import com.avail.serialization.SerializerOperation;

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
public class CharacterDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/** The Unicode code point. */
		CODE_POINT
	}

	/** The first 256 Unicode characters. */
	private static AvailObject[] byteCharacters;

	/** The hashes of the first 256 Unicode characters. */
	private static final int[] hashesOfByteCharacters = new int[256];

	/**
	 * The maximum code point value as an {@code int}.
	 */
	public static final int maxCodePointInt = Character.MAX_CODE_POINT;

	static
	{
		for (int i = 0; i <= 255; i++)
		{
			hashesOfByteCharacters[i] = computeHashOfCharacterWithCodePoint(i);
		}
	}

	/**
	 * Create the first 256 Unicode characters.
	 */
	static void createWellKnownObjects ()
	{
		byteCharacters = new AvailObject[256];
		for (int i = 0; i <= 255; i++)
		{
			final AvailObject object = mutable().create();
			object.codePoint(i);
			object.makeImmutable();
			byteCharacters[i] = object;
		}
	}

	/**
	 * Discard the first 256 Unicode characters.
	 */
	static void clearWellKnownObjects ()
	{
		byteCharacters = null;
	}

	/**
	 * Answer an immutable Avail {@linkplain CharacterDescriptor character} for
	 * the specified Unicode code point.
	 *
	 * @param codePoint A Unicode code point.
	 * @return An {@link AvailObject}.
	 */
	@ThreadSafe
	public static AvailObject fromCodePoint (final int codePoint)
	{
		if (codePoint >= 0 && codePoint <= 255)
		{
			return byteCharacters[codePoint];
		}

		final AvailObject result = mutable().create();
		result.codePoint(codePoint);
		result.makeImmutable();
		return result;
	}

	/**
	 * Answer an already instantiated Avail {@linkplain CharacterDescriptor
	 * character} for the specified unsigned 8-bit Unicode code point.
	 *
	 * @param codePoint An unsigned 8-bit Unicode code point.
	 * @return An {@link AvailObject}.
	 */
	@ThreadSafe
	public static AvailObject fromByteCodePoint (
		final short codePoint)
	{
		// Provided separately so it can return more efficiently by constant
		// reference.
		assert codePoint >= 0 && codePoint <= 255;
		return byteCharacters[codePoint];
	}

	/**
	 * Answer the hash of the Avail {@linkplain CharacterDescriptor character}
	 * with the specified Unicode code point.
	 *
	 * @param codePoint A Unicode code point.
	 * @return A hash.
	 */
	@ThreadSafe
	static int computeHashOfCharacterWithCodePoint (final int codePoint)
	{
		return IntegerDescriptor.computeHashOfInt(codePoint ^ 0x068E9947);
	}

	/**
	 * Answer the hash of the Avail {@linkplain CharacterDescriptor character}
	 * with the specified unsigned 8-bit Unicode code point.
	 *
	 * @param codePoint An unsigned 8-bit Unicode code point.
	 * @return A hash.
	 */
	@ThreadSafe
	static int hashOfByteCharacterWithCodePoint (final short codePoint)
	{
		assert codePoint >= 0 && codePoint <= 255;
		return hashesOfByteCharacters[codePoint];
	}


	/**
	 * The mutable {@link CharacterDescriptor}.
	 */
	final private static CharacterDescriptor mutable =
		new CharacterDescriptor(true);

	/**
	 * Answer a mutable {@link CharacterDescriptor}.
	 *
	 * @return A mutable {@link CharacterDescriptor}.
	 */
	@ThreadSafe
	public static CharacterDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link CharacterDescriptor}.
	 */
	static final CharacterDescriptor immutable =
		new CharacterDescriptor(false);

	/**
	 * Answer an immutable {@link CharacterDescriptor}.
	 *
	 * @return An immutable {@link CharacterDescriptor}.
	 */
	@ThreadSafe
	public static CharacterDescriptor immutable ()
	{
		return immutable;
	}

	@Override @AvailMethod
	int o_CodePoint (final AvailObject object)
	{
		return object.slot(IntegerSlots.CODE_POINT);
	}

	@Override @AvailMethod
	void o_CodePoint (
		final AvailObject object,
		final int value)
	{
		object.setSlot(IntegerSlots.CODE_POINT, value);
	}

	@Override
	@AvailMethod @ThreadSafe
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return another.equalsCharacterWithCodePoint(object.codePoint());
	}

	@Override
	@AvailMethod @ThreadSafe
	boolean o_EqualsCharacterWithCodePoint (
		final AvailObject object,
		final int otherCodePoint)
	{
		return object.codePoint() == otherCodePoint;
	}

	@Override
	@AvailMethod @ThreadSafe
	int o_Hash (final AvailObject object)
	{
		final int codePoint = object.codePoint();
		if (codePoint >= 0 && codePoint <= 255)
		{
			return hashesOfByteCharacters[codePoint];
		}
		return computeHashOfCharacterWithCodePoint(object.codePoint());
	}

	@Override
	@AvailMethod @ThreadSafe
	boolean o_IsCharacter (final AvailObject object)
	{
		return true;
	}

	@Override
	@AvailMethod @ThreadSafe
	AvailObject o_Kind (final AvailObject object)
	{
		return CHARACTER.o();
	}

	@Override
	@AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		final int codePoint = object.slot(IntegerSlots.CODE_POINT);
		if (codePoint < 256)
		{
			return SerializerOperation.BYTE_CHARACTER;
		}
		else if (codePoint < 65536)
		{
			return SerializerOperation.SHORT_CHARACTER;
		}
		return SerializerOperation.LARGE_CHARACTER;
	}

	@Override
	Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> classHint)
	{
		final int codePoint = object.slot(IntegerSlots.CODE_POINT);
		// Force marshaling to Java's primitive int type.
		if (Integer.TYPE.equals(classHint))
		{
			return Integer.valueOf(codePoint);
		}
		// Force marshaling to Java's primitive char type, throwing an exception
		// if the code point is out of range.
		if (Character.TYPE.equals(classHint))
		{
			if (codePoint > 65535)
			{
				throw new MarshalingException();
			}
			return Character.valueOf((char) codePoint);
		}
		assert classHint == null;
		// Only understand Unicode code points in the basic multilingual plane
		// (BMP) as marshaling to Java's primitive char type.
		if (codePoint < 65536)
		{
			return Character.valueOf((char) codePoint);
		}
		// Use Java's primitive int type for all others.
		return Integer.valueOf(codePoint);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append("¢");
		final int codePoint = object.codePoint();
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
				new Formatter(aStream).format("\"\\(%x)\"", codePoint);
				break;
			default:
				// Check for double quote (") and backslash (\).
				boolean mustCloseQuote = false;
				if (codePoint == 0x22 || codePoint == 0x5C)
				{
					aStream.append("\"\\");
					mustCloseQuote = true;
				}
				aStream.appendCodePoint(codePoint);
				if (mustCloseQuote)
				{
					aStream.append('"');
				}
		}
	}

	/**
	 * Construct a new {@link CharacterDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	private CharacterDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}
}
