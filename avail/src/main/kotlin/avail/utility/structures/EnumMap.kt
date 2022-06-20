/*
 * EnumMap.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
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

package avail.utility.structures

import avail.utility.mapToSet

/**
 * An `EnumMap` is a [Map] implementation for use with enum type keys only.  All
 * of the keys in an `EnumMap` must come from a single enum that is specified,
 * explicitly as the first parameterized type. `EnumMap`s are represented
 * internally as arrays. Enum maps are maintained in the natural order of their
 * keys ([Enum.ordinal]s).
 *
 * This map does not support nulls as valid values. A key-value pair where the
 * value is `null` is indicative that the "key" is not present.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 *
 * @param K
 *   The type of Enum keys maintained by this [EnumMap]
 * @param V
 *   The type of the values stored in this [EnumMap].
 * @property enums
 *   The [Array] of [Enum]s, in [Enum.ordinal] order of all the `Enum` values
 *   represented in the `Enum`. It is expected that **all** enum values will
 *   be present.
 * @property values
 *   The [Array] of values stored in this `Enum`. `null` values are not allowed
 *   as a null value indicates the absence of a value.
 *
 * @constructor
 * Construct an [EnumMap].
 *
 * @param enums
 *   The [Array] of [Enum]s, in [Enum.ordinal] order of all the `Enum` values
 *   represented in the `Enum`. It is expected that **all** enum values will
 *   be present.
 * @param sourceValues
 *   The [Array] of values stored in this `Enum`.
 */
@Suppress("UNCHECKED_CAST", "unused")
class EnumMap<K: Enum<K>, V : Any> constructor(
	private val enums: Array<K>,
	private val sourceValues: Array<V?>) : MutableMap<K, V>
{
	init
	{
		assert(enums.size == sourceValues.size)
	}

	override val size: Int get() = sourceValues.count { it !== null }

	override val keys: MutableSet<K> get() =
		sourceValues.indices
			.filter { sourceValues[it] !== null }
			.mapToSet { enums[it] }

	override val values: MutableCollection<V>
		get() = sourceValues.filterNotNull().toMutableList()

	/**
	 * Answer the value at the provided key. If no value is available (`null`
	 * is at [Enum.ordinal] in the [sourceValues] array), evaluate the lambda,
	 * place the resulting value in this map at that key, then answer the value.
	 *
	 * @param key
	 *   The key to look up.
	 * @param mappingFunction
	 *   The function to run to produce a value if none found.
	 * @return The stored value or the result of the [mappingFunction].
	 */
	fun getOrPut (key: K, mappingFunction: (K) -> V): V
	{
		var value = sourceValues[key.ordinal]
		if (value === null)
		{
			value = mappingFunction(key)
			sourceValues[key.ordinal] = value
		}
		return value
	}

	/**
	 * Update the map with a new value.
	 *
	 * @param key
	 *   The location to set the value.
	 * @param value
	 *   The value to set.
	 */
	operator fun set(key: K, value: V)
	{
		sourceValues[key.ordinal] = value
	}

	override fun put(key: K, value: V): V?
	{
		val old = sourceValues[key.ordinal]
		sourceValues[key.ordinal] = value
		return old
	}

	override fun putAll(from: Map<out K, V>)
	{
		from.forEach { (k, v) -> sourceValues[k.ordinal] = v }
	}

	/**
	 * Removes the specified key and its corresponding value from this
	 * [EnumMap].
	 *
	 * @return
	 *   The previous value associated with the key, or `null` if the key was
	 *   not present in the map.
	 */
	override fun remove(key: K): V?
	{
		val value = sourceValues[key.ordinal]
		sourceValues[key.ordinal] = null
		return value
	}

	/**
	 * Removes all elements from this [EnumMap].
	 */
	override fun clear()
	{
		sourceValues.fill(null)
	}

	override fun containsKey(key: K): Boolean = true

	override fun containsValue(value: V): Boolean =
		sourceValues.any { value == it }

	override operator fun get(key: K): V? = sourceValues[key.ordinal]

	/** Answer the value if present, otherwise `null`. */
	fun getOrNull(key: K): V? = sourceValues[key.ordinal]

	override fun isEmpty(): Boolean = false

	override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
		get() = enums.filter {
			sourceValues[it.ordinal] !== null
		}.map {
			object : MutableMap.MutableEntry<K, V>
			{
				override val key: K = it
				override val value: V = sourceValues[it.ordinal]!!
				override fun setValue (newValue: V): V
				{
					val old = value
					sourceValues[key.ordinal] = newValue
					return old
				}
			}
		}.toMutableSet()

	/**
	 * Construct an [EnumMap].
	 *
	 * @param enums
	 *   The [Array] of [Enum]s, in [Enum.ordinal] order of all the `Enum`
	 *   values represented in the `Enum`. It is expected that **all** enum
	 *   values will be present.
	 */
	constructor (enums: Array<K>) :
		this(enums, Array<Any?>(enums.size) { null } as Array<V?>)

	companion object
	{
		/**
		 * Create an empty [EnumMap] for the provided reified key type.  This
		 * form of creation avoids the need for the client to extract the enum's
		 * array of constants.
		 */
		inline fun <reified K : Enum<K>, V : Any> enumMap () : EnumMap<K, V> =
			EnumMap(K::class.java.enumConstants)

		/**
		 * Answer a new [EnumMap].  The key type must be known statically at the
		 * call site, since this inline function is reified for the key type
		 * parameter.
		 *
		 * @param values
		 *   The vararg [Array] of values stored in this `Enum`.
		 */
		inline fun <reified K : Enum<K>, V : Any> enumMap (vararg values: V?)
				: EnumMap<K, V> =
			EnumMap(K::class.java.enumConstants, values as Array<V?>)

		/**
		 * Answer a new [EnumMap].
		 *
		 * @param populator
		 *   A lambda that accepts a value of the Enum and answers a value to be
		 *   stored at the enum value key.
		 */
		inline fun <reified K : Enum<K>, V : Any> enumMap (
			populator: (K) -> V?
		): EnumMap<K, V>
		{
			val keys = K::class.java.enumConstants
			val map = EnumMap<K, V>(keys)
			keys.forEach { key ->
				populator(key)?.let { value -> map[key] = value }
			}
			return map
		}
	}
}
