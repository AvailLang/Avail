/*
 * A_MapBin.kt
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
package com.avail.descriptor.maps

import com.avail.descriptor.maps.MapDescriptor.Entry
import com.avail.descriptor.maps.MapDescriptor.MapIterable
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.dispatch
import com.avail.descriptor.representation.AvailObject

/**
 * `A_MapBin` is a collection of keys and their associated values, which makes
 * up some or part of a [map][A_Map].
 *
 * Bins below a particular scale ([LinearMapBinDescriptor.thresholdToHash]) are
 * usually represented via [LinearMapBinDescriptor], which is primarily an
 * arbitrarily ordered alternating sequence of keys and their associated values.
 * The hashes of the keys are also stored for performance, among other things.
 *
 * Above that threshold, a [HashedMapBinDescriptor] is used, which organizes the
 * key-value pairs into a tree based on their hash values.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_MapBin : A_BasicObject {
	companion object
	{
		/**
		 * Execute the given action with each key and associated value in this
		 * map bin.
		 *
		 * @param action
		 *   The action to execute with each key/value pair.
		 */
		fun A_MapBin.forEachInMapBin(
			action: (AvailObject, AvailObject) -> Unit
		) = dispatch { o_ForEachInMapBin(it, action) }

		/**
		 * Look up the key in this [map&#32;bin][MapBinDescriptor].  If not
		 * found, answer `null`.  Use the provided hash of the key.
		 *
		 * @param key
		 *   The key to look up in this map.
		 * @param keyHash
		 *   The conveniently already computed hash of the key.
		 * @return
		 *   The value under that key in the map, or `null` if not found.
		 */
		fun A_MapBin.mapBinAtHash(
			key: A_BasicObject,
			keyHash: Int
		): AvailObject? = dispatch { o_MapBinAtHash(it, key, keyHash) }

		/**
		 * Answer whether this map bin is hashed (versus linear).
		 *
		 * @return
		 *   A [Boolean] indicating whether this map bin is hashed.
		 */
		fun A_MapBin.isHashedMapBin() = dispatch { o_IsHashedMapBin(it) }

		/**
		 * Answer how many key/value pairs are in this map bin.
		 *
		 * @return
		 *   The map bin's size.
		 */
		fun A_MapBin.mapBinSize() = dispatch { o_MapBinSize(it) }

		/**
		 * Create a map bin like the receiver, but with the given [key]
		 * associated with the given [value].  If [canDestroy] is true and the
		 * receiver is mutable, the receiver may be modified, and possibly act
		 * as the return value of this method.
		 *
		 * @param key
		 *   The key to associated with the [value].
		 * @param keyHash
		 *   The precomputed hash value of the [key].
		 * @param value
		 *   The value to store under the [key] in the resulting map bin.
		 * @param myLevel
		 *   The level number associated with this bin.  Each level uses a
		 *   different range of bits of the key's hash to determine which
		 *   sub-bin to access.
		 * @param canDestroy
		 *   Whether the receiver can be destroyed/reused if it's also mutable.
		 * @return
		 *   The value stored under the given [key] in this map bin.
		 */
		fun A_MapBin.mapBinAtHashPutLevelCanDestroy(
			key: A_BasicObject,
			keyHash: Int,
			value: A_BasicObject,
			myLevel: Int,
			canDestroy: Boolean
		) = dispatch {
			o_MapBinAtHashPutLevelCanDestroy(
				it, key, keyHash, value, myLevel, canDestroy)
		}

		/**
		 * Answer a map bin like the receiver, but with the given key excluded.
		 * If the key does not occur in the receiver, answer the same map bin,
		 * or an equivalent. If canDestroy is true and the receiver is mutable,
		 * the receiver can be modified and/or returned as the result.
		 *
		 * @param key
		 *   The key to exclude.
		 * @param keyHash
		 *   The precomputed hash value of the [key].
		 * @param canDestroy
		 *   Whether the receiver can be destroyed/reused if it's also mutable.
		 * @return
		 *   The new map bin, or the updated receiver.
		 */
		fun A_MapBin.mapBinRemoveKeyHashCanDestroy(
			key: A_BasicObject,
			keyHash: Int,
			canDestroy: Boolean
		) = dispatch {
			o_MapBinRemoveKeyHashCanDestroy(it, key, keyHash, canDestroy)
		}

		/**
		 * Transform an element of this map bin.  If there is an entry for the
		 * key, use the corresponding value as the second argument to the
		 * transformer, otherwise pass the notFoundValue.  Write the result back
		 * to the bin, potentially recycling it if canDestroy is true.
		 *
		 * @param key
		 *   The key to look up.
		 * @param keyHash
		 *   The already computed hash of that key, to avoid rehashing while
		 *   traversing the tree structure.
		 * @param notFoundValue
		 *   What to pass the transformer if the key was not found.
		 * @param transformer
		 *   A binary operator that takes the key and its value, or the
		 *   `notFoundValue`, and produces a replacement value to associate with
		 *   the key.
		 * @param myLevel
		 *   The level of the map bin.
		 * @param canDestroy
		 *   Whether the original bin can be destroyed, if it's also mutable.
		 * @return
		 *   A replacement bin.
		 */
		fun A_MapBin.mapBinAtHashReplacingLevelCanDestroy(
			key: A_BasicObject,
			keyHash: Int,
			notFoundValue: A_BasicObject,
			transformer: (AvailObject, AvailObject) -> A_BasicObject,
			myLevel: Int,
			canDestroy: Boolean
		): A_MapBin = dispatch {
			o_MapBinAtHashReplacingLevelCanDestroy(
				it,
				key,
				keyHash,
				notFoundValue,
				transformer,
				myLevel,
				canDestroy)
		}

		/**
		 * Answer the union of the kinds of each key in this bin.  A value's
		 * exact type is always an instance type (an enumeration type of size
		 * 1), and the value's kind is the nearest supertype of that instance
		 * type that isn't itself an enumeration type.
		 *
		 * If a value is itself a type, the resulting kind is an instance
		 * metatype.
		 *
		 * @return
		 *   The union of the keys' kinds.
		 */
		fun A_MapBin.mapBinKeyUnionKind() =
			dispatch { o_MapBinKeyUnionKind(it) }

		/**
		 * Answer the union of the kinds of each value in this bin.  A value's
		 * exact type is always an instance type (an enumeration type of size
		 * 1), and the value's kind is the nearest supertype of that instance
		 * type that isn't itself an enumeration type.
		 *
		 * If a value is itself a type, the resulting kind is an instance
		 * metatype.
		 *
		 * @return
		 *   The union of the values' kinds.
		 */
		fun A_MapBin.mapBinValueUnionKind() =
			dispatch { o_MapBinValueUnionKind(it) }

		/**
		 * Answer a combined hash of all the keys in this map bin.
		 *
		 * @return
		 *   A hash of this bin's keys.
		 */
		fun A_MapBin.mapBinKeysHash() = dispatch { o_MapBinKeysHash(it) }

		/**
		 * Answer a combined hash of all the values in this map bin.
		 *
		 * @return
		 *   A hash of this bin's values.
		 */
		fun A_MapBin.mapBinValuesHash() = dispatch { o_MapBinValuesHash(it) }

		/**
		 * Create a [MapIterable] that produces each [Entry] of the [A_Map].
		 *
		 * @return
		 *   A [MapIterable] that visits each [Entry] once, in arbitrary order.
		 */
		fun A_MapBin.mapBinIterable() = dispatch { o_MapBinIterable(it) }
	}
}
