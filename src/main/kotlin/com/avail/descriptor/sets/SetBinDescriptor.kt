/*
 * SetBinDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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
package com.avail.descriptor.sets

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.Descriptor
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.HashedSetBinDescriptor.Companion.generateHashedSetBinFrom
import com.avail.descriptor.sets.LinearSetBinDescriptor.Companion.generateLinearSetBinFrom
import com.avail.descriptor.sets.SetBinDescriptor.IntegerSlots.Companion.BIN_HASH
import com.avail.descriptor.sets.SetDescriptor.SetIterator
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.TypeTag

/**
 * This abstract class organizes the idea of nodes in a Bagwell Ideal Hash Tree
 * used to implement hashed maps.
 *
 * @constructor
 *
 * @param mutability
 *   The [Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] to embed in the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's integer slots layout, or null if there are no integer slots.
 * @property level
 *   The level of my objects in their enclosing bin trees. The top node is level
 *   0 (using hash bits 0..5), and the bottom hashed node is level 5 (using hash
 *   bits 30..34, the top three of which are always zero). There can be a level
 *   6 [linear&#32;bin][LinearSetBinDescriptor], but it represents elements
 *   which all have the same hash value, so it should never be hashed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class SetBinDescriptor protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?,
	internal val level: Int
) : Descriptor(
	mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass
) {
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * The low 32 bits are used for the [.BIN_HASH], but the upper 32 can be
		 * used by other [BitField]s in subclasses.
		 */
		BIN_HASH_AND_MORE;

		companion object {
			/**
			 * A slot to hold the bin's hash value, or zero if it has not been
			 * computed.
			 */
			val BIN_HASH = BitField(BIN_HASH_AND_MORE, 0, 32)
		}
	}

	override fun o_SetBinHash(self: AvailObject): Int =
		self.slot(BIN_HASH)

	override fun o_IsSetBin(self: AvailObject) = true

	/**
	 * Asked of the top bin of a set.  If the set is large enough to be
	 * hashed then compute/cache the union's nearest kind, otherwise just
	 * answer nil.
	 */
	abstract override fun o_BinElementsAreAllInstancesOfKind(
		self: AvailObject,
		kind: A_Type
	): Boolean

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	abstract override fun o_SetBinIterator(self: AvailObject): SetIterator

	companion object {
		/**
		 * Generate a bin at the requested level with values produced by [size]
		 * invocations of the [generator].
		 *
		 * @param level
		 *   The level to create.
		 * @param size
		 *   The number of elements to generate.  There may be duplicates, which
		 *   can lead to a bin with fewer elements than this number.
		 * @param generator
		 *   The generator.
		 * @return
		 *   A set bin.
		 */
		fun generateSetBinFrom(
			level: Int,
			size: Int,
			generator: (Int)->A_BasicObject
		): A_SetBin {
			if (size == 1) {
				// Special case, exactly one value occurs, so return it.
				return generator(1) as A_SetBin
			}
			return if (size < LinearSetBinDescriptor.thresholdToHash
				|| level >= HashedSetBinDescriptor.numberOfLevels - 1) {
				// Use a linear bin.
				generateLinearSetBinFrom(level, size, generator)
			} else generateHashedSetBinFrom(level, size, generator)
		}
	}
}
