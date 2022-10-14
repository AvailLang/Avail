/*
 * LeftistHeap.kt
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

package avail.utility.structures

/**
 * A [LeftistHeap] is a persistent (immutable) priority queue.  The [first]
 * operation is O(1), and [with] and [withoutFirst] are O(log(n)).  The latter
 * two also produce a new [LeftistHeap] without modifying the original.
 *
 * A [LeftistHeap] is either a [leftistLeaf] having no elements, or a
 * [LeftistInternal] containing the heap's minimum (the values of the heap must
 * be [Comparable]), a left subtree, and a right subtree.  The [rank] of a heap
 * is the length of the shortest path to a leaf.  The rank of a heap's right
 * subtree is always less than or equal to the rank of the heap's left subtree.
 * Therefore, the shortest path to a leaf can always be found along the right
 * spine.  In fact, the rank can be *defined* as zero for a leaf, or the right
 * subtree's rank + 1.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
sealed class LeftistHeap<Value : Comparable<Value>>
constructor (
	val rank: Int,
	val size: Int)
{
	val isEmpty: Boolean get() = this === TheLeftistLeaf
	abstract val first: Value
	abstract val withoutFirst: LeftistHeap<Value>
	abstract fun with(newValue: Value): LeftistInternal<Value>
	abstract fun merge(another: LeftistHeap<Value>): LeftistHeap<Value>
	abstract fun without(value: Value): LeftistHeap<Value>
	fun toList(): List<Value>
	{
		val list = mutableListOf<Value>()
		var residue = this
		while (!residue.isEmpty)
		{
			list.add(residue.first)
			residue = residue.withoutFirst
		}
		return list
	}
}

private open class LeftistLeaf<Value : Comparable<Value>>
	: LeftistHeap<Value>(0, 0)
{
	override val first get() =
		throw IllegalArgumentException("Heap is empty")
	override val withoutFirst get() =
		throw IllegalAccessError("Heap is empty")
	override fun with(newValue: Value) = LeftistInternal(newValue, this, this)
	override fun merge(another: LeftistHeap<Value>) = another
	override fun without(value: Value) = this
}

private object TheLeftistLeaf : LeftistLeaf<Nothing>()

@Suppress("UNCHECKED_CAST")
fun <Value: Comparable<Value>> leftistLeaf(): LeftistHeap<Value> =
	TheLeftistLeaf as LeftistHeap<Value>

class LeftistInternal<Value : Comparable<Value>>
constructor(
	override val first: Value,
	val left: LeftistHeap<Value>,
	val right: LeftistHeap<Value>
) : LeftistHeap<Value>(right.rank + 1, left.size + right.size + 1)
{
	override val withoutFirst get() = left.merge(right)
	override fun with(newValue: Value) =
		merge(LeftistInternal(newValue, leftistLeaf(), leftistLeaf()))
	override fun merge(another: LeftistHeap<Value>): LeftistInternal<Value> =
		when
		{
			another !is LeftistInternal -> this
			first <= another.first -> join(first, left, right.merge(another))
			else -> join(another.first, another.left, merge(another.right))
		}
	override fun without(value: Value): LeftistHeap<Value>
	{
		if (value == first) return left.merge(right)
		if (value < first) return this
		val leftWithout = left.without(value)
		val rightWithout = right.without(value)
		if (leftWithout == left && rightWithout == right) return this
		return join(first, leftWithout, rightWithout)
	}
}

private fun <Value : Comparable<Value>> join(
	value: Value,
	a: LeftistHeap<Value>,
	b: LeftistHeap<Value>
) = when
{
	a.rank >= b.rank -> LeftistInternal(value, a, b)
	else -> LeftistInternal(value, b, a)
}
