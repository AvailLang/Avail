/*
 * BitField.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.representation

import avail.annotations.EnumField
import avail.descriptor.representation.AbstractSlotsEnum.Companion.fieldOrdinal

/**
 * A `BitField` is constructed at class loading time and contains any cached
 * information needed to efficiently access a range of up to 32 contiguous bits
 * from an [integer&#32;slot][IntegerSlotsEnum].
 *
 * @constructor
 * @property integerSlot
 *   The integer [slot][IntegerSlotsEnum] within which this [BitField] occurs.
 * @property shift
 *   The lowest bit position that this BitField occupies.  Zero (`0`) is the
 *   rightmost or lowest order bit.
 * @property bits
 *   The number of bits that this `BitField` occupies within a [Long].
 * @property presenter
 *   An optional function that converts an [Int] value for this field into a
 *   suitable [String] for presenting this [BitField] value.  If the function is
 *   present and produces `null`, don't show this value.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class BitField
constructor(
	val integerSlot: IntegerSlotsEnum,
	val shift: Int,
	val bits: Int,
	val presenter: ((Int) -> String?)? = null
) : Comparable<BitField?> {
	init {
		assert(shift == shift and 63)
		assert(bits > 0)
		assert(bits <= 32) // Must use at most 32 bits of the long.
		assert(shift + bits <= 64)
	}

	/**
	 * The zero-based [integer&#32;slot][IntegerSlotsEnum] within which this
	 * bit field occurs.
	 */
	val integerSlotIndex = integerSlot.fieldOrdinal

	/**
	 * A string of 1's of length [bits], right aligned in the `long`.  Even
	 * though bit fields can't be more than 32 long, it's a long to avoid sign
	 * extension problems.
	 */
	private val lowMask = (1L shl bits) - 1

	/**
	 * An integer containing 1-bits in exactly the positions reserved for this
	 * bit field.
	 */
	private val mask = lowMask shl shift

	/**
	 * An integer containing 0-bits in exactly the positions reserved for this
	 * bit field.
	 */
	private val invertedMask = mask.inv()

	/**
	 * The name of this `BitField`.  This is filled in as needed by the default
	 * [object&#32;printing][AbstractDescriptor.printObjectOnAvoidingIndent]
	 * mechanism.
	 */
	var name: String? = null

	/**
	 * The [EnumField] with which this `BitField` is annotated, if any.  This is
	 * populated by the default
	 * [printing][AbstractDescriptor.printObjectOnAvoidingIndent] mechanism.
	 */
	var enumField: EnumField? = null

	/**
	 * Compare two `BitFields` for sorting. They must be for the same slot.
	 * Order by descending shift values.
	 */
	override fun compareTo(other: BitField?): Int {
		assert(integerSlot === other!!.integerSlot) {
			"Bit fields of different slots are incomparable"
		}
		return other!!.shift.compareTo(shift)
	}

	/**
	 * Answer whether the receiver and the passed `BitField` occupy the
	 * same span of bits at the same integer slot number.
	 *
	 * @param bitField
	 *   The other `BitField`.
	 * @return
	 *   Whether the two BitFields have the same slot number, start bit, and end
	 *   bit.
	 */
	fun isSamePlaceAs(bitField: BitField) =
		integerSlotIndex == bitField.integerSlotIndex
			&& shift == bitField.shift
			&& bits == bitField.bits

	override fun toString() = "${javaClass.simpleName}($shift:$bits)"

	/**
	 * Extract this `BitField` from the given [Long].
	 *
	 * @param longValue
	 *   A [Long].
	 * @return
	 *   An [Int] extracted from the range of bits this `BitField` occupies.
	 */
	fun extractFromLong(longValue: Long) =
		(longValue ushr shift and lowMask).toInt()

	/**
	 * Given a [Long] field value, replace the bits occupied by this `BitField`
	 * with the supplied [Int] value, returning the resulting [Long].
	 *
	 * @param longValue
	 *   A [Long] into which to replace the bit field.
	 * @param bitFieldValue
	 *   The [Int] to be written into the bit field.
	 * @return
	 *   The [Long] with the bit field updated.
	 */
	fun replaceBits(longValue: Long, bitFieldValue: Int) =
		(longValue and invertedMask
			or (bitFieldValue.toLong() shl shift and mask))
}
