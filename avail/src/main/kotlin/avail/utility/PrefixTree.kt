/*
 * PrefixTree.kt
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

package avail.utility

import avail.utility.PrefixSharingList.Companion.append
import avail.utility.Strings.tabs
import kotlin.streams.toList

/**
 * A prefix tree, with `O(1)` access time and `O(n)` insertion time. Thread-safe
 * iff the internal [transition&#32;tables][Map], supplied by the
 * [factory][mapFactory], are thread-safe.
 *
 * @property mapFactory
 *   How to produce an internal [map][Map], for dispatching on the next element
 *   of a sequence.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class PrefixTree<K, V> constructor(
	private val mapFactory: ()->MutableMap<K, PrefixTree<K, V>> =
		{ mutableMapOf() }
)
{
	/** The dispatch table for chasing the next element of an input sequence. */
	private val next = mapFactory()

	/** The payload. */
	private var payload: V? = null

	/**
	 * Traverse the tree to locate the terminus of the key, if any.
	 *
	 * @param key
	 *   The search key.
	 * @return
	 *   The tree at the end of the traversal, or `null` if the key could not
	 *   be traversed to the end.
	 */
	@Suppress("RedundantNullableReturnType")
	private fun traverse(key: Iterable<K>): PrefixTree<K, V>?
	{
		// RedundantNullableReturnType is required because a Kotlin bug prevents
		// the compiler from deducing that null is a possible return type for
		// the function.
		var tree = this
		key.forEach { tree = tree.next.getOrElse(it) { return@traverse null } }
		return tree
	}

	/**
	 * Traverse the tree to locate the terminus of the key, creating missing
	 * intermediate nodes along the way.
	 *
	 * @param key
	 *   The search key.
	 * @return
	 *   The tree at the end of the traversal.
	 */
	private fun mutableTraverse(key: Iterable<K>): PrefixTree<K, V>
	{
		var tree = this
		key.forEach {
			tree = tree.next.getOrPut(it) { PrefixTree(tree.mapFactory) }
		}
		return tree
	}

	/**
	 * Fetch the value associated with the specified key, if any.
	 *
	 * @param key
	 *   The search key.
	 * @return
	 *   The value associated with [key], if any.
	 */
	operator fun get(key: Iterable<K>): V? = traverse(key)?.payload

	/**
	 * Fetch every payload stored under an improper suffix of the specified key,
	 * ordered by length and then by code point.
	 *
	 * @param key
	 *   The search key.
	 * @return
	 *   The reachable payloads.
	 */
	fun payloads(key: Iterable<K>): List<V>
	{
		val payloads = mutableListOf<V>()
		val tree = traverse(key)
		if (tree === null) return payloads
		tree.payload?.let { payloads.add(it) }
		// Use a PrefixSharingList to track the prefix.
		val list = key.fold(listOf<K>()) { acc, k -> acc.append(k) }
		tree.privatePayloads(list, payloads)
		return payloads
	}

	/**
	 * Fetch every payload stored under an improper suffix of the specified key,
	 * ordered by length and then by code point.
	 *
	 * @param key
	 *   The search key.
	 * @param payloads
	 *   The payloads accumulated so far (by the recursive algorithm rooted at
	 *   [payloads]).
	 */
	private fun privatePayloads(key: List<K>, payloads: MutableList<V>)
	{
		next.forEach { (_, tree) -> tree.payload?.let { payloads.add(it) } }
		next.forEach { (x, tree) ->
			tree.privatePayloads(key.append(x), payloads)
		}
	}

	/**
	 * Insert a new key-value pair into the receiver, replacing any existing
	 * value associated with the specified key.
	 *
	 * @param key
	 *   The search key.
	 * @param value
	 *   The payload to associate with the key.
	 * @return
	 *   The value previously associated with [key], if any.
	 */
	operator fun set(key: Iterable<K>, value: V): V?
	{
		val tree = mutableTraverse(key)
		val old = tree.payload
		tree.payload = value
		return old
	}

	/**
	 * Remove the specified key and any associated value from the receiver.
	 *
	 * @param key
	 *   The search key.
	 * @return
	 *   The value previously associated with [key], if any.
	 */
	fun remove(key: Iterable<K>): V?
	{
		val tree = traverse(key)
		return if (tree !== null)
		{
			val old = tree.payload
			tree.payload = null
			old
		}
		else null
	}

	/**
	 * Remove all paths from the receiver that do not lead to payloads.
	 */
	@Suppress("unused")
	fun prune() = next.forEach { (x, tree) -> tree.privatePrune(this, x) }

	/**
	 * Remove all paths from the receiver that do not lead to payloads.
	 *
	 * @param parent
	 *   The parent [tree][PrefixTree].
	 * @param x
	 *   The path component that leads from the parent to the receiver, for
	 *   removal from the parent.
	 */
	private fun privatePrune(parent: PrefixTree<K, V>, x: K)
	{
		next.forEach { (x, tree) -> tree.privatePrune(this, x) }
		if (payload === null && next.isEmpty())
		{
			assert(x !== null)
			parent.next.remove(x)
			return
		}
	}

	override fun toString(): String = buildString {
		privateToString(this, 0)
		toString()
	}

	/**
	 * Recursively render the receiver to the specified [StringBuilder].
	 *
	 * @param builder
	 *   The destination [StringBuilder].
	 * @param indent
	 *   The current indentation level.
	 */
	private fun privateToString(builder: StringBuilder, indent: Int): Unit =
		builder.run {
			append(tabs(indent))
			append("* ${payload ?: "∅"}\n")
			next.forEach { (x, tree) ->
				append(tabs(indent + 1))
				append("- $x\n")
				tree.privateToString(builder, indent + 1)
			}
		}

	companion object
	{
		/**
		 * Convenience getter for using string-keyed
		 * [prefix&#32;trees][PrefixTree].
		 *
		 * @param key
		 *   The search key.
		 * @return
		 *   The value associated with [key], if any.
		 */
		@Suppress("NOTHING_TO_INLINE")
		inline operator fun <V> PrefixTree<Int, V>.get(
			key: String
		): V? = get(key.codePoints().toList())

		/**
		 * Convenience accumulator for using string-keyed
		 * [prefix&#32;trees][PrefixTree].
		 *
		 * @param key
		 *   The search key.
		 * @return
		 *   The reachable payloads.
		 */
		@Suppress("NOTHING_TO_INLINE")
		inline fun <V> PrefixTree<Int, V>.payloads(
			key: String
		): List<V> = payloads(key.codePoints().toList())

		/**
		 * Convenience setter for using string-keyed
		 * [prefix&#32;trees][PrefixTree].
		 *
		 * @param key
		 *   The search key.
		 * @param value
		 *   The payload to associate with the key.
		 * @return
		 *   The value previously associated with [key], if any.
		 */
		@Suppress("NOTHING_TO_INLINE")
		inline operator fun <V> PrefixTree<Int, V>.set(
			key: String,
			value: V
		): V? = set(key.codePoints().toList(), value)
	}
}
