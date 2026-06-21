/*
 * MapBinDescriptor.kt
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
package avail.descriptor.maps

import avail.annotations.HideFieldInDebugger
import avail.descriptor.maps.HashedMapBinDescriptor.Companion.HashedMapBinBuilder
import avail.descriptor.maps.LinearMapBinDescriptor.Companion.LinearMapBinBuilder
import avail.descriptor.maps.MapBinDescriptor.IntegerSlots.Companion.KEYS_HASH
import avail.descriptor.maps.MapDescriptor.Entry
import avail.descriptor.maps.MapDescriptor.MapIterator
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_MapBin
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.BitField
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.TypeTag

/**
 * This abstract class organizes the idea of nodes in a Bagwell Ideal Hash Tree
 * used to implement hashed maps.
 *
 * @property level
 *   The level of my objects in their enclosing bin trees. The top node is level
 *   0 (using hash bits 0..5), and the bottom hashed node is level 5 (using hash
 *   bits 30..35, the top four of which are always zero). There can be a level 6
 *   [linear&#32;bin][LinearMapBinDescriptor], but it represents elements which
 *   all have the same hash value, so it should never be hashed.
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
 *   The depth of the bin in the hash tree.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class MapBinDescriptor protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?,
	internal val level: Int
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
		@HideFieldInDebugger
		COMBINED_HASHES;

		companion object {
			/**
			 * The sum of the hashes of the elements recursively within this
			 * bin.
			 */
			val KEYS_HASH = BitField(COMBINED_HASHES, 0, 32) { null }

			/**
			 * The sum of the hashes of the elements recursively within this
			 * bin, or zero if not computed.
			 */
			val VALUES_HASH_OR_ZERO = BitField(COMBINED_HASHES, 32, 32) { null }
		}
	}

	override fun o_Kind(self: AvailObject): A_Type = ANY()

	override fun o_MapBinKeysHash(self: AvailObject) =
		self[KEYS_HASH]

	abstract override fun o_ForEachInMapBin(
		self: AvailObject,
		action: (AvailObject, AvailObject) -> Unit)

	abstract override fun o_MapBinValuesHash(self: AvailObject): Int

	override fun o_IsHashedMapBin(self: AvailObject) = false

	abstract override fun o_MapBinAtHash(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int
	): AvailObject?

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	abstract override fun o_MapBinIterator(self: AvailObject): MapIterator

	companion object
	{
		/**
		 * A builder for creating [A_MapBin]s.  The subclasses are specialized for
		 * the kind of bin to create (hashed or linear), and whether the client
		 * may produce the same key multiple times (in which case, value added with
		 * the last occurrence of the key is kept).
		 *
		 * @constructor
		 * @property size
		 *   The number of elements to generate.
		 * @property level
		 *   The level of bin to create.
		 */
		abstract class MapBinBuilder(
			val size: Int,
			val level: Int)
		{
			/**
			 * Add a key/value pair to the bin.  The key's hash is provided in
			 * [keyHash] to reduce duplicate hashing requests.
			 */
			abstract fun add(key: AvailObject, value: AvailObject, keyHash: Int)

			/**
			 * Construct the final [A_MapBin] from this builder.
			 */
			abstract fun build(): A_MapBin
		}

		/**
		 * Generate a bin at the requested level with values produced by [size]
		 * invocations of the [generator], each of which should update the given
		 * scratch [Entry].  If [checkForDuplicates] is true, then the generator
		 * will check for duplicate keys, keeping only the last occurring value
		 * for each key.  If it's false, the client has the responsibility to
		 * ensure duplicate keys do not exist.
		 *
		 * @param level
		 *   The level to create.
		 * @param size
		 *   The number of elements to generate.  There may be duplicates, which
		 *   can lead to a bin with fewer elements than this number.
		 * @param checkForDuplicates
		 *   Whether to check for duplicate keys.
		 * @param generator
		 *   The generator, which takes a one-based increasing index and the
		 *   reuseable [Entry] to update.
		 * @return
		 *   A map bin.
		 */
		fun generateMapBinFrom(
			level: Int,
			size: Int,
			checkForDuplicates: Boolean,
			generator: (Int, Entry)->Unit
		): A_MapBin
		{
			val builder = mapBinBuilder(size, level, checkForDuplicates)
			val entry = Entry()
			for (i in 1..size)
			{
				generator(i, entry)
				val (key, value, keyHash) = entry
				builder.add(key, value, keyHash)
			}
			return builder.build()
		}

		/**
		 * Create a suitable [MapBinBuilder] for the given [size] and [level].
		 */
		fun mapBinBuilder(
			size: Int,
			level: Int,
			checkForDuplicates: Boolean
		): MapBinBuilder = when
		{
			size < LinearMapBinDescriptor.thresholdToHash
				|| level >= HashedMapBinDescriptor.numberOfLevels - 1
					-> LinearMapBinBuilder(size, level, checkForDuplicates)
			else -> HashedMapBinBuilder(size, level, checkForDuplicates)
		}
	}
}
