/**
 * descriptor/CharacterDescriptor.java
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

import com.avail.annotations.NotNull;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;

import java.util.Formatter;
import java.util.List;

/**
 * {@code CharacterDescriptor} implements an Avail character. Avail code points
 * correspond to 32-bit Unicode code points.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class CharacterDescriptor
extends Descriptor
{

	public enum IntegerSlots
	{
		CODE_POINT
	}
	/** The first 256 Unicode characters. */
	private static AvailObject[] byteCharacters;

	/** The hashes of the first 256 Unicode characters. */
	private static final @NotNull int[] hashesOfByteCharacters = new int[256];
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
			AvailObject object = mutable().create();
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
	public static @NotNull AvailObject newImmutableCharacterWithCodePoint (
		final int codePoint)
	{
		if (codePoint >= 0 && codePoint <= 255)
		{
			return byteCharacters[codePoint];
		}

		AvailObject result = mutable().create();
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
	public static @NotNull AvailObject newImmutableCharacterWithByteCodePoint (
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
		// Provided separately so it can return more efficiently by constant
		// reference.
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
	/**
	 * Answer the mutable {@link CharacterDescriptor}.
	 *
	 * @return The mutable {@link CharacterDescriptor}.
	 */
	public static CharacterDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link CharacterDescriptor}.
	 */
	final static CharacterDescriptor immutable =
		new CharacterDescriptor(false);

	/**
	 * Answer an immutable {@link CharacterDescriptor}.
	 *
	 * @return An immutable {@link CharacterDescriptor}.
	 */
	@ThreadSafe
	/**
	 * Answer the immutable {@link CharacterDescriptor}.
	 *
	 * @return The immutable {@link CharacterDescriptor}.
	 */
	public static CharacterDescriptor immutable ()
	{
		return immutable;
	}


	@Override
	public int o_CodePoint (final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.CODE_POINT);
	}

	@Override
	public void o_CodePoint (
		final @NotNull AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.CODE_POINT, value);
	}

	@Override
	@ThreadSafe
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsCharacterWithCodePoint(object.codePoint());
	}

	@Override
	@ThreadSafe
	public boolean o_EqualsCharacterWithCodePoint (
		final @NotNull AvailObject object,
		final int otherCodePoint)
	{
		return (object.codePoint() == otherCodePoint);
	}

	@Override
	@ThreadSafe
	public @NotNull AvailObject o_ExactType (final @NotNull AvailObject object)
	{
		return Types.character.object();
	}

	@Override
	@ThreadSafe
	public int o_Hash (final @NotNull AvailObject object)
	{
		int codePoint = object.codePoint();
		if (codePoint >= 0 && codePoint <= 255)
		{
			return hashesOfByteCharacters[codePoint];
		}
		return computeHashOfCharacterWithCodePoint(object.codePoint());
	}

	@Override
	@ThreadSafe
	public boolean o_IsCharacter (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override
	@ThreadSafe
	public AvailObject o_Type (final @NotNull AvailObject object)
	{
		return Types.character.object();
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	@ThreadSafe
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		int codePoint = object.codePoint();
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
				new Formatter(aStream).format("'\\u%04x'", codePoint);
				break;
			default:
				aStream.append('\'');
				aStream.appendCodePoint(codePoint);
				aStream.append('\'');
		}
	};

	/**
	 * Construct a new {@link CharacterDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected CharacterDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}
}
