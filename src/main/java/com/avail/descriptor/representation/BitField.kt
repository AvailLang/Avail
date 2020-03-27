/*
 * BitField.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.representation

import com.avail.annotations.EnumField
import com.avail.descriptor.AbstractDescriptor

/**
 * A `RuntimeBitField` is constructed at class loading time and contains
 * any cached information needed to access a range of up to 32 contiguous bits
 * from an [integer slot][IntegerSlotsEnum].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class BitField(
	integerSlot: IntegerSlotsEnum,
	shift: Int,
	bits: Int) : Comparable<BitField?> {
	/**
	 * The [integer slot][IntegerSlotsEnum] within which this bit field
	 * occurs.
	 */
	@JvmField
	val integerSlot: IntegerSlotsEnum

	/**
	 * The zero-based [integer slot][IntegerSlotsEnum] within which this
	 * bit field occurs.
	 */
	val integerSlotIndex: Int

	/**
	 * The lowest bit position that this BitField occupies.  Zero (`0`) is
	 * the rightmost or lowest order bit.
	 */
	private val shift: Int

	/**
	 * The number of bits that this BitField occupies within a `long`.
	 */
	@JvmField
	val bits: Int

	/**
	 * A string of 1's of length [.bits], right aligned in the `long`.  Even though bit fields can't be more than 32 long, it's a long
	 * to avoid sign extension problems.
	 */
	private val lowMask: Long

	/**
	 * An integer containing 1-bits in exactly the positions reserved for this
	 * bit field.
	 */
	private val mask: Long

	/**
	 * An integer containing 0-bits in exactly the positions reserved for this
	 * bit field.
	 */
	private val invertedMask: Long

	/**
	 * The name of this `BitField`.  This is filled in as needed by the
	 * default [object printing][AbstractDescriptor.printObjectOnAvoidingIndent]
	 * mechanism.
	 */
	@JvmField
	var name: String? = null

	/**
	 * The [EnumField] with which this `BitField` is annotated, if
	 * any.  This is populated by the default [ ][AbstractDescriptor.printObjectOnAvoidingIndent] mechanism.
	 */
	@JvmField
	var enumField: EnumField? = null
	override fun compareTo(other: BitField?): Int {
		assert(other != null)
		assert(integerSlot === other!!.integerSlot) { "Bit fields of different slots are incomparable" }
		// Order by descending shift values.
		return Integer.compare(other!!.shift, shift)
	}

	/**
	 * Answer whether the receiver and the passed `BitField` occupy the
	 * same span of bits at the same integer slot number.
	 *
	 * @param bitField The other `BitField`.
	 * @return Whether the two BitFields have the same slot number, start bit,
	 * and end bit.
	 */
	fun isSamePlaceAs(bitField: BitField): Boolean {
		return integerSlotIndex == bitField.integerSlotIndex && shift == bitField.shift && bits == bitField.bits
	}

	override fun toString(): String {
		return String.format(
			"%s(%d:%d)",
			javaClass.simpleName,
			shift,
			bits)
	}

	/**
	 * Extract this `BitField` from the given `long`.
	 * @param longValue A long.
	 * @return An `int` extracted from the range of bits this `BitField` occupies.
	 */
	fun extractFromLong(longValue: Long): Int {
		return (longValue ushr shift and lowMask).toInt()
	}

	/**
	 * Given a `long` field value, replace the bits occupied by this
	 * `BitField` with the supplied `int` value, returning the
	 * resulting `long`.
	 * @param longValue A `long` into which to replace the bit field.
	 * @param bitFieldValue The `int` to be written into the bit field.
	 * @return The `long` with the bit field updated.
	 */
	fun replaceBits(longValue: Long, bitFieldValue: Int): Long {
		return (longValue and invertedMask
			or (bitFieldValue.toLong() shl shift and mask))
	}

	/**
	 * Construct a new `BitField`.
	 *
	 * @param integerSlot
	 * The [integer slot][IntegerSlotsEnum] in which this bit field
	 * occurs.
	 * @param shift
	 * The bit position of the rightmost (lowest order) bit occupied by
	 * this `BitField`.
	 * @param bits
	 * The number of bits this `BitField` occupies.
	 */
	init {
		assert(shift == shift and 63)
		assert(bits > 0)
		assert(bits <= 32 // Must use at most 32 bits of the long.
		)
		assert(shift + bits <= 64)
		this.integerSlot = integerSlot
		this.shift = shift
		this.bits = bits
		integerSlotIndex = integerSlot.ordinal()
		lowMask = (1L shl bits) - 1
		mask = lowMask shl shift
		invertedMask = mask.inv()
	}
}