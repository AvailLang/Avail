/*
 * A_Map.kt
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

import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.MapDescriptor.Entry
import avail.descriptor.maps.MapDescriptor.MapIterable
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set
import avail.descriptor.tuples.A_Tuple
import avail.exceptions.AvailErrorCode
import avail.exceptions.MapException
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.utility.cast

/**
 * `A_Map` is an interface that specifies the map-specific operations that an
 * [AvailObject] must implement.  It's a sub-interface of [A_BasicObject], the
 * interface that defines the behavior that all [AvailObject]s are required to
 * support.
 *
 * The purpose for [A_BasicObject] and its sub-interfaces is to allow sincere
 * type annotations about the basic kinds of objects that support or may be
 * passed as arguments to various operations.  The VM is free to always declare
 * objects as [AvailObject], but in cases where it's clear that a particular
 * object must always be a map, a declaration of `A_Map` ensures that only the
 * basic object capabilities plus map-like capabilities are to be allowed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Map : A_BasicObject
{
	companion object
	{
		/**
		 * Execute the given action with each key and value.
		 *
		 * @param action
		 *   The action to perform for each key and value pair.
		 */
		fun A_Map.forEach(action: (AvailObject, AvailObject)->Unit) =
			dispatch { o_ForEach(it, action) }

		/**
		 * Answer whether the argument is one of the keys of this map.
		 *
		 * @param keyObject
		 *   The potential key to look for in this map's keys.
		 * @return
		 *   Whether the potential key was found in this map.
		 */
		fun A_Map.hasKey(keyObject: A_BasicObject): Boolean =
			mapAtOrNull(keyObject) !== null

		/**
		 * Answer the [set][A_Set] of keys in this map.  Since keys of maps and
		 * set elements cannot have duplicates, it follows that the size of the
		 * resulting set is the same as the size of this map.
		 *
		 * @return
		 *   The set of keys.
		 */
		val A_Map.keysAsSet: A_Set get() = dispatch { o_KeysAsSet(it) }

		/**
		 * Find the key/value pair in this map which has the specified key and
		 * answer the value.  Fail if the specified key is not present in the
		 * map. The result is *not* forced to be immutable, as it's up to the
		 * caller whether the new reference would leak beyond a usage that
		 * conserves its reference count.
		 *
		 * @param keyObject
		 *   The key to look up.
		 * @return
		 *   The value associated with that key in the map.
		 * @throws MapException
		 *   If the key was not found.
		 */
		fun A_Map.mapAt(keyObject: A_BasicObject): AvailObject =
			mapAtOrNull(keyObject) ?:
				throw MapException(AvailErrorCode.E_KEY_NOT_FOUND)

		/**
		 * Find the key/value pair in this map which has the specified key and
		 * answer the value.  Answer null if the specified key is not present in
		 * the map. The result is *not* forced to be immutable, as it's up to
		 * the caller whether the new reference would leak beyond a usage that
		 * conserves its reference count.
		 *
		 * @param keyObject
		 *   The key to look up.
		 * @return
		 *   The value associated with that key in the map, or null if it was
		 *   not present.
		 */
		fun A_Map.mapAtOrNull(keyObject: A_BasicObject): AvailObject? =
			dispatch { o_MapAtOrNull(it, keyObject) }

		/**
		 * Create a new map like this map, but with a new key/value pair as
		 * specified.  If there was an existing key/oldValue pair, then it is
		 * replaced by the new key/value pair.  The original map can be modified
		 * in place (and then returned) if [canDestroy] is true and the map is
		 * mutable.
		 *
		 * @param keyObject
		 *   The key to add or replace.
		 * @param newValueObject
		 *   The value to associate with the key in the new map.
		 * @param canDestroy
		 *   Whether the map can be modified in place if mutable.
		 * @return
		 *   The new map containing the specified key/value pair.
		 */
		fun A_Map.mapAtPuttingCanDestroy(
			keyObject: A_BasicObject,
			newValueObject: A_BasicObject,
			canDestroy: Boolean
		): A_Map = dispatch {
			o_MapAtPuttingCanDestroy(it, keyObject, newValueObject, canDestroy)
		}

		/**
		 * Look up the `key` in the map.  If present, use the key and the looked
		 * up value as arguments to a call to the `transformer`. Otherwise, use
		 * the key and the `notFoundValue` ([nil] is default for this) as
		 * arguments to the transformer.  Store the transformer's result in the
		 * map under the key, destroying the original if [canDestroy] is true.
		 * Answer the resulting map.
		 *
		 * The map must not change during evaluation of the transformer.
		 *
		 * @param key
		 *   The key to look up.
		 * @param notFoundValue
		 *   The value to use as the second argument to the transformer if the
		 *   key was not found.
		 * @param canDestroy
		 *   Whether the map can be modified by this call, if it's also mutable.
		 * @param transformer
		 *   The binary operator that produces a replacement value to store into
		 *   the map.
		 * @return
		 *   The new map, possibly the mutated original map itself, if
		 *   canDestroy is true.
		 */
		fun A_Map.mapAtReplacingCanDestroy(
			key: A_BasicObject,
			notFoundValue: A_BasicObject = nil,
			canDestroy: Boolean,
			transformer: (AvailObject, AvailObject)->A_BasicObject
		): A_Map = dispatch {
			o_MapAtReplacingCanDestroy(
				it, key, notFoundValue, canDestroy, transformer)
		}

		/**
		 * Answer a suitable [Iterable] for iterating over this map's key/value
		 * pairs (made available in an [Entry]).  This allows the Java (and
		 * Kotlin) for-each syntax hack to be used.
		 *
		 * @return
		 *   A [MapIterable].
		 */
		val A_Map.mapIterable: MapIterable
			get() = dispatch { o_MapIterable(it) }

		/**
		 * Answer the number of key/value pairs in the map.
		 *
		 * @return
		 *   The size of the map.
		 */
		val A_Map.mapSize: Int get() = dispatch { o_MapSize(it) }

		/**
		 * Create a new map like this map, but without the key/value pair having
		 * the specified key.  If the key was not present, then answer the
		 * original map. The original map can be modified in place (and then
		 * returned) if [canDestroy] is true and the map is mutable.
		 *
		 * @param keyObject
		 *   The key to remove.
		 * @param canDestroy
		 *   Whether a mutable map can be modified in place.
		 * @return
		 *   The new map not having the specified key.
		 */
		fun A_Map.mapWithoutKeyCanDestroy(
			keyObject: A_BasicObject,
			canDestroy: Boolean
		): A_Map =
			dispatch { o_MapWithoutKeyCanDestroy(it, keyObject, canDestroy) }

		/**
		 * Answer a [tuple][A_Tuple] of values from this map in arbitrary order.
		 * A tuple is used instead of a set, since the values are not
		 * necessarily unique.  The order of elements in the tuple is arbitrary
		 * and meaningless.
		 *
		 * @return
		 *   The map's values in an arbitrarily ordered tuple.
		 */
		val A_Map.valuesAsTuple: A_Tuple
			get() = dispatch { o_ValuesAsTuple(it) }

		/**
		 * Given an [A_Map], [self], produce a similar map, but with [key]
		 * mapped to [value].  The original may be destroyed or recycled if it's
		 * mutable.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun mapAtPuttingStatic(
			self: A_Map,
			key: A_BasicObject,
			value: A_BasicObject
		): AvailObject = self.mapAtPuttingCanDestroy(key, value, true).cast()

		/** The [CheckedMethod] for [mapAtPuttingCanDestroy]. */
		val mapAtPuttingStaticMethod = staticMethod(
			A_Map::class.java,
			::mapAtPuttingStatic.name,
			A_Map::class.java,
			A_Map::class.java,
			A_BasicObject::class.java,
			A_BasicObject::class.java)
	}
}
