/*
 * PrefixSharingList.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this 
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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
package com.avail.utility

import java.util.AbstractList
import java.util.ArrayList
import java.util.NoSuchElementException

/**
 * This is an implementation of an immutable [List] for which [append], the
 * non-destructive append operation, takes constant time.  There is also a
 * [withoutLast] operation for producing a list with its rightmost element
 * removed.  Iterating over the entire list takes linear time, and does not use
 * recursion.
 *
 * The implementation should be thread-safe if the lists that are supplied as
 * prefixes are themselves thread-safe.  Do not change any of those lists after
 * constructing `PrefixSharingList`s from them.
 *
 * @param E
 *   The type of elements in the list.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class PrefixSharingList<E> : AbstractList<E>
{
	/** The size of this list  */
	override val size: Int

	/** A list containing the first size-1 elements.  Do not modify it.  */
	private val allButLast: List<E>?

	/** The last element of this list.  */
	private val lastElement: E

	/**
	 * A lazily computed ArrayList containing (at least) all the elements of
	 * this [PrefixSharingList].
	 */
	@Volatile
	private var cachedFlatListOrMore: List<E>? = null

	// Always at least two elements.
	override fun isEmpty(): Boolean = false

	override operator fun contains(element: @UnsafeVariance E): Boolean =
		cacheFlatListOrMore().subList(0, size).contains(element)

	override fun get(index: Int): E
	{
		if (index < 0 || index >= size)
		{
			throw IndexOutOfBoundsException()
		}
		return if (index == size - 1)
		{
			lastElement
		}
		else cacheFlatListOrMore()[index]
	}

	override fun indexOf(element: E): Int =
		cacheFlatListOrMore().subList(0, size).indexOf(element)

	override fun lastIndexOf(element: E): Int =
		cacheFlatListOrMore().subList(0, size).lastIndexOf(element)

	/**
	 * If the flattened form of this list isn't already cached in
	 * [cachedFlatListOrMore] then compute it and cache it.  Also record this
	 * flattened list in any prefixes that are themselves [PrefixSharingList]s.
	 * That implies the prefixes may end up with more elements in their
	 * flattened representations than they really represent, so operations like
	 * [iterator] must compensate for this by ignoring the extra elements.
	 *
	 * Note: This could be considered a reference leak, but it shouldn't be
	 * significant for Avail's purposes.  It can be fixed by storing weak
	 * references within the flat list (the strong references from [lastElement]
	 * will prevent useful elements from disappearing).
	 *
	 * An invariant of the class is that either the [allButLast] must be
	 * non-null or the [cachedFlatListOrMore] must be non-null (or both).
	 *
	 * @return
	 *   The flattened list, containing *at least* [size] elements, perhaps
	 *   more.
	 */
	private fun cacheFlatListOrMore(): List<E>
	{
		val temp = cachedFlatListOrMore
		if (temp !== null)
		{
			return temp
		}
		val flatList = ArrayList<E>(size)
		var pointer: PrefixSharingList<E> = this
		while (true)
		{
			val pointerFlatList = pointer.cachedFlatListOrMore
			if (pointerFlatList !== null)
			{
				flatList.addAll(0, pointerFlatList.subList(0, pointer.size))
				break
			}
			flatList.add(0, pointer.lastElement)
			assert(pointer.allButLast != null)
			if (pointer.allButLast !is PrefixSharingList<*>)
			{
				flatList.addAll(0, pointer.allButLast!!)
				break
			}
			pointer = pointer.allButLast as PrefixSharingList<E>
		}
		// Replace the cached flat lists until we hit a non-PrefixSharingList
		// or a PrefixSharingList with its flat list already set.
		pointer = this
		assert(flatList.size >= size)
		while (pointer.cachedFlatListOrMore === null)
		{
			pointer.cachedFlatListOrMore = flatList
			pointer = if (pointer.allButLast is PrefixSharingList<*>)
			{
				pointer.allButLast as PrefixSharingList<E>
			}
			else
			{
				break
			}
		}
		return flatList
	}

	override fun iterator(): MutableIterator<E>
	{
		val flatList = cacheFlatListOrMore()
		val mySize = size
		return object : MutableIterator<E>
		{
			/** The current position.  */
			var position = 0
			override fun hasNext(): Boolean
			{
				return position < mySize
			}

			override fun next(): E
			{
				if (position >= mySize)
				{
					throw NoSuchElementException()
				}
				return flatList[position++]
			}

			override fun remove()
			{
				throw UnsupportedOperationException()
			}
		}
	}

	/**
	 * Check that the receiver is properly constructed. This is the class
	 * invariant.  Fail if it is invalid.
	 */
	private fun validCheck()
	{
		assert(allButLast !== null || cachedFlatListOrMore !== null)
	}

	/**
	 * Construct a new instance.
	 *
	 * @param allButLast
	 *   All but the last element of the new list.
	 * @param lastElement
	 *   The last element of the new list.
	 */
	private constructor(
		allButLast: List<E>,
		lastElement: E)
	{
		size = allButLast.size + 1
		this.allButLast = allButLast
		this.lastElement = lastElement
		validCheck()
	}

	/**
	 * Construct a new instance truncated to the specified size.
	 *
	 * @param originalList
	 *   An immutable list.
	 * @param size
	 *   The size of the resulting list.
	 */
	private constructor(
		originalList: List<E>,
		size: Int)
	{
		assert(originalList !is PrefixSharingList<*>)
		assert(size <= originalList.size)
		this.size = size
		allButLast = null
		lastElement = originalList[size - 1]
		cachedFlatListOrMore = originalList
		validCheck()
	}

	override fun addAll(
		index: Int,
		c: Collection<E>): Boolean
	{
		throw UnsupportedOperationException()
	}

	override fun listIterator(): MutableListIterator<E> =
		subList(0, size).listIterator()

	override fun listIterator(index: Int): MutableListIterator<E> =
		subList(0, size).listIterator(index)

	override fun subList(fromIndex: Int, toIndex: Int): MutableList<E>
	{
		if (fromIndex < 0)
		{
			throw IndexOutOfBoundsException("fromIndex = $fromIndex")
		}
		if (toIndex > size)
		{
			throw IndexOutOfBoundsException("toIndex = $toIndex")
		}
		require(fromIndex <= toIndex) {
			"fromIndex(" + fromIndex +
				") > toIndex(" + toIndex + ')'
		}
		return cacheFlatListOrMore().subList(fromIndex, toIndex).toMutableList()
	}

	companion object
	{
		/**
		 * Produce a new immutable list based on the prefix [List] (which must
		 * not be modified after this operation) and the new element to follow
		 * them.
		 *
		 * @param allButLast
		 *   The leading elements of the list.
		 * @param lastElement
		 *   The value by which to extend the list.
		 * @return
		 *   A new immutable list with all those elements.
		 * @param E2
		 *   The list's element type.
		 */
		@JvmStatic
		fun <E2> append(
			allButLast: List<E2>,
			lastElement: E2): List<E2> =
				if (allButLast.isEmpty())
				{
					listOf(lastElement)
				}
				else PrefixSharingList(allButLast, lastElement)

		/**
		 * Produce a new immutable list based on the given list, but with the
		 * last element excluded.  Try to avoid creating new objects if
		 * possible.
		 *
		 * @param originalList
		 *   The original list.
		 * @return
		 *   An immutable list containing all but the last element of the
		 *   original.
		 * @param E2
		 *   The list's element type.
		 */
		@JvmStatic
		fun <E2> withoutLast(originalList: List<E2>): List<E2>
		{
			assert(originalList.isNotEmpty())
			if (originalList.size == 1)
			{
				return emptyList()
			}
			if (originalList is PrefixSharingList<*>)
			{
				val strongOriginal = originalList as PrefixSharingList<E2>
				val butLast = strongOriginal.allButLast
				if (butLast != null)
				{
					return butLast
				}
				val flat = Nulls.stripNull(
					strongOriginal.cachedFlatListOrMore
				)
				return PrefixSharingList(flat, originalList.size - 1)
			}
			return PrefixSharingList(originalList, originalList.size - 1)
		}

		/**
		 * Answer the last element of the given non-empty list.
		 *
		 * @param list
		 * The list.
		 * @return
		 * The last element of that list.
		 * @param E2
		 * The list's element type.
		 */
		@JvmStatic
		fun <E2> last(
			list: List<E2>
		): E2
		{
			return list[list.size - 1]
		}
	}
}