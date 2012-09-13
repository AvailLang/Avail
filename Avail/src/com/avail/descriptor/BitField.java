/**
 * RuntimeBitField.java
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

import java.util.List;
import com.avail.annotations.*;

/**
 * A {@code RuntimeBitField} is constructed at class loading time and contains
 * any cached information needed to access a range of bits from an {@linkplain
 * IntegerSlotsEnum integer slot}.  It is instantiated from a {@link BitField}
 * annotation.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
final class BitField
implements Comparable<BitField>
{
	/**
	 * The {@link IntegerSlotsEnum integer slot} within which this bit field
	 * occurs.
	 */
	final IntegerSlotsEnum integerSlot;

	/**
	 * The zero-based {@link IntegerSlotsEnum integer slot} within which this
	 * bit field occurs.
	 */
	final int integerSlotIndex;

	/**
	 * The lowest bit position that this BitField occupies.  Zero ({@code 0}) is
	 * the rightmost or lowest order bit.
	 */
	final int shift;

	/**
	 * The number of bits that this BitField occupies within an int.
	 */
	final int bits;

	/**
	 * A string of 1's of length {@link #bits}, right aligned in the {@code
	 * int}.
	 */
	final int lowMask;

	/**
	 * An integer containing 1-bits in exactly the positions reserved for this
	 * bit field.
	 */
	final int mask;

	/**
	 * An integer containing 0-bits in exactly the positions reserved for this
	 * bit field.
	 */
	final int invertedMask;

	/**
	 * The name of this {@code BitField}.  This is filled in as needed by the
	 * default {@linkplain AbstractDescriptor#printObjectOnAvoidingIndent(
	 * AvailObject, StringBuilder, List, int) object printing} mechanism.
	 */
	String name;

	/**
	 * Construct a new {@link BitField}.
	 *
	 * @param integerSlot
	 *            The {@link IntegerSlotsEnum integer slot} in which this bit
	 *            field occurs.
	 * @param shift
	 *            The bit position of the rightmost (lowest order) bit occupied
	 *            by this {@code BitField}.
	 * @param bits
	 *            The number of bits this {@code BitField} occupies.
	 */
	public BitField (
		final IntegerSlotsEnum integerSlot,
		final int shift,
		final int bits)
	{
		assert shift == (shift & 31);
		assert bits > 0;
		assert shift + bits <= 32;

		this.integerSlot = integerSlot;
		this.shift = shift;
		this.bits = bits;

		integerSlotIndex = integerSlot.ordinal();
		lowMask = (1 << bits) - 1;
		mask = lowMask << shift;
		invertedMask = ~mask;
	}

	@Override
	public int compareTo (final @Nullable BitField bitField)
	{
		assert bitField != null;
		assert integerSlot == bitField.integerSlot
			: "Bit fields of different slots are incomparable";
		// Order by descending shift values.
		return bitField.shift - shift;
	}
}
