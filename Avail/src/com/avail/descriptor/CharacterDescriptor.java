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
@IntegerSlots("codePoint")
public class CharacterDescriptor
extends Descriptor
{
	/** The first 256 Unicode characters. */
	static AvailObject[] byteCharacters;

	/** The hashes of the first 256 Unicode characters. */
	static final @NotNull int[] hashesOfByteCharacters =
	{
		0x00C89042, 0x00063AA1, 0x00D084A4, 0x000627AD, 0x0009607D, 0x004D7174,
		0x00CB2FEF, 0x008BCD19, 0x00355EE6, 0x004E5E8B, 0x00860760, 0x0074A2CF,
		0x00029A48, 0x005F39A4, 0x00291151, 0x0007E26C, 0x00997711, 0x00FE9E29,
		0x004D64A3, 0x00ED9CF2, 0x00C42F9F, 0x00CF0AF2, 0x00E3E6E9, 0x00AD311F,
		0x0062353D, 0x000638A2, 0x00B6F913, 0x0009961F, 0x005293BB, 0x0002A714,
		0x00E4037D, 0x00FE1D14, 0x00E82308, 0x00B6F0E6, 0x0087A0BB, 0x00E89B55,
		0x00073F08, 0x00FEB8A1, 0x00FA8A6C, 0x0035F77C, 0x003C26FC, 0x00928694,
		0x00AD9763, 0x0067B10C, 0x00E84E51, 0x006A0FF3, 0x0070939E, 0x00A1ED7D,
		0x003502AD, 0x00298E1F, 0x00AD8759, 0x00E1ABF5, 0x00352807, 0x00FF50FF,
		0x005EBD5B, 0x00866CA2, 0x00625F1F, 0x009758F2, 0x009986F2, 0x00821560,
		0x0044569C, 0x00990448, 0x00CB666C, 0x000EE114, 0x000791BF, 0x005FEC44,
		0x00707B7C, 0x00367BAD, 0x005F980C, 0x00828DAE, 0x00E00BA2, 0x0097070C,
		0x00E0B93D, 0x00079F94, 0x00E0E251, 0x00871A2A, 0x00104029, 0x00FEA2F2,
		0x00CB2359, 0x001027D1, 0x0022679F, 0x00222448, 0x005F6E9E, 0x009740F3,
		0x007BCD76, 0x00026A7D, 0x00BD9894, 0x004E92A1, 0x00CD5854, 0x0002C9A4,
		0x00CBA549, 0x0062E2DA, 0x0052F307, 0x005FDF39, 0x00362F19, 0x006ACD7C,
		0x005851D1, 0x001028CC, 0x0002E8F6, 0x00A34D1F, 0x00020F13, 0x00AD2FE6,
		0x004C04AD, 0x00AFB674, 0x005F6F63, 0x00979A86, 0x002B7051, 0x002BC9C8,
		0x003639A4, 0x0070E611, 0x00024C86, 0x00226E50, 0x00ADB185, 0x004DAAA2,
		0x004EEA44, 0x004491E6, 0x00029E51, 0x004D8B84, 0x00354D14, 0x00352DC8,
		0x00F0829F, 0x00973FE6, 0x00A09266, 0x0007C760, 0x009D07CF, 0x0052432A,
		0x00A0856C, 0x005E73A1, 0x00B61B8A, 0x000E51C8, 0x006DCCF2, 0x00F8BA9F,
		0x0097E144, 0x00CD4BA2, 0x00CD575B, 0x00067EAD, 0x007F0429, 0x0007A4BB,
		0x00F16429, 0x006ADEA1, 0x00C8A586, 0x00C8E49F, 0x00E8412A, 0x002B514F,
		0x00E1487C, 0x000709BD, 0x007086A4, 0x000F016E, 0x00093289, 0x00FF2DB4,
		0x008D6363, 0x00090A48, 0x000976A3, 0x00A0FBF2, 0x004E28DA, 0x00CD5D0C,
		0x0052A6F2, 0x004D6160, 0x000799FC, 0x009D3BF2, 0x00229659, 0x000EBEBB,
		0x000935F2, 0x00C82DFC, 0x000933F2, 0x004D5F55, 0x00587A49, 0x00529B51,
		0x00CD7D0C, 0x005FA063, 0x00220407, 0x0029827D, 0x00620174, 0x00C1A148,
		0x0062A5B4, 0x00E064A4, 0x006D1C6E, 0x001675E6, 0x00062D9C, 0x009F519E,
		0x00022144, 0x000774DA, 0x0097B91F, 0x00E0CA66, 0x00628886, 0x0002EC7D,
		0x0007F039, 0x00A3CB5B, 0x00E0A956, 0x0044476B, 0x00AD4363, 0x007F132E,
		0x009711E9, 0x008FBA55, 0x005271F5, 0x00ED149F, 0x006288F2, 0x00025D2E,
		0x000911FF, 0x0092BD2A, 0x00A04CF3, 0x00028A94, 0x004EC4AB, 0x0079D3F1,
		0x00C85008, 0x00FF84A1, 0x009908A1, 0x0007E511, 0x0007826E, 0x006A0811,
		0x004ED507, 0x00624A6E, 0x0070176B, 0x009912F2, 0x00F09BB4, 0x00E00E56,
		0x00224894, 0x00228B74, 0x00A0A8BB, 0x00AF06E9, 0x00CDE9A1, 0x003545A3,
		0x004EC73C, 0x00C83139, 0x007B4CFF, 0x004E232A, 0x00620759, 0x00DC8919,
		0x0027026C, 0x008F7BAD, 0x004DCCF6, 0x00511F11, 0x008F97AE, 0x009979C9,
		0x00C82FF2, 0x00CDF12C, 0x007C0BC8, 0x001605EF, 0x00A0E4F2, 0x009DAC48,
		0x00F12939, 0x00E0A5F1, 0x0002209E, 0x00ED09E9, 0x00861DA1, 0x003CC2BB,
		0x0010569E, 0x008F565B, 0x004D3839, 0x00524AAD, 0x0095929F, 0x00FE02F5,
		0x00033B6E, 0x0048A4C9, 0x006208BF, 0x00F13CB2
	};

	/**
	 * Create the first 256 Unicode characters.
	 */
	static void createWellKnownObjects ()
	{
		byteCharacters = new AvailObject[256];
		for (int i = 0; i <= 255; i++)
		{
			AvailObject object = AvailObject.newIndexedDescriptor(
				0,
				CharacterDescriptor.mutableDescriptor());
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

		AvailObject result = AvailObject.newIndexedDescriptor(
			0, CharacterDescriptor.mutableDescriptor());
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

	
	final static CharacterDescriptor mutableDescriptor = new CharacterDescriptor(true);

	/**
	 * Answer a mutable {@link CharacterDescriptor}.
	 * 
	 * @return A mutable {@link CharacterDescriptor}.
	 */
	@ThreadSafe
	public static CharacterDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	final static CharacterDescriptor immutableDescriptor = new CharacterDescriptor(false);

	/**
	 * Answer an immutable {@link CharacterDescriptor}.
	 * 
	 * @return An immutable {@link CharacterDescriptor}.
	 */
	@ThreadSafe
	public static CharacterDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}


	@Override
	public int ObjectCodePoint (final @NotNull AvailObject object)
	{
		return object.integerSlotAtByteIndex(4);
	}

	@Override
	public void ObjectCodePoint (
		final @NotNull AvailObject object, 
		final int value)
	{
		object.integerSlotAtByteIndexPut(4, value);
	}

	@Override
	@ThreadSafe
	public boolean ObjectEquals (
		final @NotNull AvailObject object, 
		final @NotNull AvailObject another)
	{
		return another.equalsCharacterWithCodePoint(object.codePoint());
	}

	@Override
	@ThreadSafe
	public boolean ObjectEqualsCharacterWithCodePoint (
		final @NotNull AvailObject object, 
		final int otherCodePoint)
	{
		return (object.codePoint() == otherCodePoint);
	}

	@Override
	@ThreadSafe
	public @NotNull AvailObject ObjectExactType (final @NotNull AvailObject object)
	{
		return Types.character.object();
	}

	@Override
	@ThreadSafe
	public int ObjectHash (final @NotNull AvailObject object)
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
	public boolean ObjectIsCharacter (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override
	@ThreadSafe
	public AvailObject ObjectType (final @NotNull AvailObject object)
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
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected CharacterDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}
}
