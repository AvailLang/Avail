/*
 * NumericTupleDescriptor.kt
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
package avail.descriptor.tuples

import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleLongAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ByteTupleDescriptor.Companion.generateByteTupleFrom
import avail.descriptor.tuples.IntTupleDescriptor.Companion.generateIntTupleFrom
import avail.descriptor.tuples.LongTupleDescriptor.Companion.generateLongTupleFrom
import avail.descriptor.tuples.NybbleTupleDescriptor.Companion.generateNybbleTupleFrom
import kotlin.math.max
import kotlin.math.min

/**
 * `NumericTupleDescriptor` has Avail tuples of integers as its instances. The
 * actual representation of these tuples is determined by subclasses.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new [TupleDescriptor].
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's object slots
 * @param minimumSupportedValue
 *   The smallest [Long] that can be stored in an object using this descriptor.
 * @param maximumSupportedValue
 *   The largest [Long] that can be stored in an object using this descriptor.
 */
abstract class NumericTupleDescriptor
protected constructor(
	mutability: Mutability,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>,
	val minimumSupportedValue: Long,
	val maximumSupportedValue: Long
) : TupleDescriptor(
	mutability,
	objectSlotsEnumClass,
	integerSlotsEnumClass)
{
	abstract override fun o_TupleIntAt(self: AvailObject, index: Int): Int

	abstract override fun o_TupleLongAt(self: AvailObject, index: Int): Long

	override fun o_DummyElement(self: AvailObject): AvailObject = zero

	/** Subclasses need to implement this. */
	abstract override fun o_AppendCanDestroy (
		self: AvailObject,
		newElement: A_BasicObject,
		canPad: Boolean,
		canDestroy: Boolean
	): A_Tuple

	/**
	 * This is the fallback mechanism for appending a long to a numeric tuple.
	 * The subclass has already failed to extend `self` while maintaining the
	 * same representation because the value is out of range, so broaden it to
	 * use a descriptor that allows the new value as well.
	 *
	 * @param self
	 *   The numeric tuple whose descriptor is the receiver.
	 * @param newLong
	 *   The [Long] to be append to the tuple.
	 * @return
	 *   A tuple with suitable representation, containing the original elements
	 *   of [self] and one additional element, [newLong].
	 */
	protected fun appendLongByBroadening(
		self: AvailObject,
		newLong: Long
	): A_Tuple
	{
		val min = min(minimumSupportedValue, newLong)
		val max = max(maximumSupportedValue, newLong)
		val newSize = self.tupleSize + 1
		return when
		{
			min >= 0 && max <= 0xF -> generateNybbleTupleFrom(newSize) {
				if (it == newSize) newLong.toInt()
				else self.tupleIntAt(it)
			}
			min >= 0 && max <= 0xFF -> generateByteTupleFrom(newSize) {
				if (it == newSize) newLong.toInt()
				else self.tupleIntAt(it)
			}
			min >= -0x8000_0000 && max <= 0x7FFF_FFFF ->
				generateIntTupleFrom(newSize) {
					if (it == newSize) newLong.toInt()
					else self.tupleIntAt(it)
				}
			else -> generateLongTupleFrom(newSize) {
				if (it == newSize) newLong
				else self.tupleLongAt(it)
			}
		}
	}
}
