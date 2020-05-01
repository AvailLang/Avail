/*
 * MapBinDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *    list of conditions and the following disclaimer in the documentation
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
package com.avail.descriptor.maps

import com.avail.annotations.AvailMethod
import com.avail.descriptor.Descriptor
import com.avail.descriptor.maps.MapDescriptor.MapIterable
import com.avail.descriptor.representation.*
import com.avail.descriptor.types.TypeTag
import java.util.function.BiConsumer

/**
 * This abstract class organizes the idea of nodes in a Bagwell Ideal Hash Tree
 * used to implement hashed maps.
 *
 * @property level
 *   The level of my objects in their enclosing bin trees. The top node is level
 *   0 (using hash bits 0..5), and the bottom hashed node is level 5 (using hash
 *   bits 30..35, the top four of which are always zero). There can be a level 6
 *   [linear bin][LinearMapBinDescriptor], but it represents elements which all
 *   have the same hash value, so it should never be hashed.
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
 * @param level
 *   The depth of the bin in the hash tree.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class MapBinDescriptor protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?,
	val level: Byte
) : Descriptor(
	mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * A [Long] holding [BitField]s containing the combined keys hash and
		 * the combined values hash or zero.
		 */
		COMBINED_HASHES;

		companion object {
			/**
			 * The sum of the hashes of the elements recursively within this
			 * bin.
			 */
			@JvmField
			val KEYS_HASH = BitField(COMBINED_HASHES, 0, 32)

			/**
			 * The sum of the hashes of the elements recursively within this
			 * bin, or zero if not computed.
			 */
			@JvmField
			val VALUES_HASH_OR_ZERO = BitField(COMBINED_HASHES, 32, 32)
		}
	}

	@AvailMethod
	override fun o_MapBinKeysHash(self: AvailObject) =
		self.slot(IntegerSlots.KEYS_HASH)

	@AvailMethod
	abstract override fun o_ForEachInMapBin(
		self: AvailObject,
		action: BiConsumer<in AvailObject, in AvailObject>)

	@AvailMethod
	abstract override fun o_MapBinValuesHash(self: AvailObject): Int

	override fun o_IsHashedMapBin(self: AvailObject) = false

	abstract override fun o_MapBinAtHash(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int
	): AvailObject?

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	abstract override fun o_MapBinIterable(self: AvailObject): MapIterable
}