/*
 * ObservableCollection.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package org.availlang.ide.anvil.utilities

/**
 * Interface for holding an immutable value.
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 */
interface ValueHolder<V>
{
	/**
	 * The wrapped immutable value
	 */
	val value: V
}

/**
 * Interface for holding an immutable key-value pair.
 *
 * @author Richard Arriaga
 *
 * @param K
 *   The type of the key.
 * @param V
 *   The type of the wrapped value.
 */
interface KeyValueHolder<K, V>: ValueHolder<V>
{
	/**
	 * The wrapped immutable value
	 */
	val key: K
}

/**
 * `ListValueHolder` is the interface for holding a value from a list, providing
 * a [value] from the list and the [index][key] where that value is stored in
 * the list.
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 */
interface ListValueHolder<V>: KeyValueHolder<Int, V>

/**
 * Represents a change to a [List].
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 */
sealed class ListStateChange<V>
{
	/**
	 * The list that has changed.
	 */
	abstract val list: List<V>
}

/**
 * `ListClear` is a [ListStateChange] that represents clearing the entire list
 * resulting in list size of zero.
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 *
 * @constructor
 * Construct a new [ListClear].
 *
 * @param list
 *   The list that has changed.
 */
data class ListClear<V> constructor(override val list: List<V>)
	: ListStateChange<V>()

/**
 * `ListAdd` is a [ListStateChange] that represents adding the contained [value]
 * at the contained [index][key]. This always results in the [list] increasing
 * in size by one element.
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 *
 * @constructor
 * Construct a new [ListAdd].
 *
 * @param list
 *   The list that has changed.
 * @param key
 *   The index where the [value] was added.
 * @param value
 *   The added value.
 */
data class ListAdd<V> constructor(
	override val list: List<V>,
	override val key: Int,
	override val value: V): ListValueHolder<V>, ListStateChange<V>()

/**
 * `ListReplace` is a [ListStateChange] that represents replacing the contained
 * [value] at the contained [index][key]. This always results in the [list]
 * remaining the same size.
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 *
 * @constructor
 * Construct a new [ListReplace].
 *
 * @param list
 *   The list that has changed.
 * @param key
 *   The index where the [value] was replaced.
 * @param value
 *   The added value.
 */
data class ListReplace<V> constructor(
	override val list: List<V>,
	override val key: Int,
	override val value: V): ListValueHolder<V>, ListStateChange<V>()

/**
 * `ListRemove` is a [ListStateChange] that represents removing the contained
 * [value] at the contained [index][key]. This always results in the [list]
 * decreasing in size by one element.
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 *
 * @constructor
 * Construct a new [ListReplace].
 *
 * @param list
 *   The list that has changed.
 * @param key
 *   The index where the [value] was replaced.
 * @param value
 *   The added value.
 */
data class ListRemove<V> constructor(
	override val list: List<V>,
	override val key: Int,
	override val value: V): ListValueHolder<V>, ListStateChange<V>()
