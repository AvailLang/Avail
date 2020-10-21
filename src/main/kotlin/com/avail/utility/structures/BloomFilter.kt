/*
 * BloomFilter.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.utility.structures

import com.avail.descriptor.numbers.IntegerDescriptor

/**
 * A `BloomFilter` is a conservative, probabilistic set.  It can report that an
 * element is *probably* in the set, or *definitely* not in the set.  This can
 * be a useful, fast prefilter to avoid computing or fetching authoritative data
 * in a large majority of cases, effectively reducing the number of spurious
 * occurrences of the slower activity by orders of magnitude.
 *
 * Since the filter is probabilistic, the interface is defined entirely in terms
 * of [Int]s, under the assumption that the actual entity being tested for
 * membership has already been hashed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 */
class BloomFilter
{
	/**
	 * @constructor
	 *   Construct a new instance that initially reports `false` for every
	 *   membership test.  If a non-null `existingFilter` is provided, it is
	 *   copied instead.
	 *
	 * @param bitCount
	 *   The number of bits that can be set independently in the table.  This
	 *   number may be rounded up to a convenient size.
	 * @param hashCount
	 *   The number of hash functions to apply for each element.  Each hash
	 *   function rehashes the given value (an [Int]), and requires the
	 *   corresponding bit position to contain a `1` bit if the element is
	 *   present (but they may all happen to be `1` even if the element is not
	 *   present).
	 */
	constructor(bitCount: Int, hashCount: Int)
	{
		this.hashCount = hashCount
		this.array = LongArray((bitCount + 63) shr 64)
	}

	/**
	 * Copy an existing [BloomFilter].
	 *
	 * @param existingFilter
	 *   The [BloomFilter] to copy.
	 */
	constructor(existingFilter: BloomFilter)
	{
		this.hashCount = existingFilter.hashCount
		this.array = existingFilter.array.clone()
	}

	/** The number of hash functions to use. */
	private val hashCount: Int

	/**
	 * The bits, guaranteed to be 1 if a hash function produced that index for
	 * any element that has been added.
	 */
	private val array: LongArray

	/**
	 * Add an [Int] element to the [BloomFilter].
	 *
	 * @param element
	 *   The [Int] to add to the [BloomFilter].
	 */
	fun add(element: Int) =
		(1..hashCount).forEach { setBit(computeHash(element, it)) }

	/**
	 * Determine whether the given element is probably present in the filter.
	 * If the element is present, this always returns true, but it may sometimes
	 * return true even if the element is not present.
	 *
	 * @param element
	 *   The [Int] to test for probable membership in the [BloomFilter].
	 * @return
	 *   Always `true` if the element is present in the filter, or usually
	 *   `false` if it is not in the filter.
	 */
	operator fun get(element: Int): Boolean =
		(1..hashCount).all { testBit(computeHash(element, it)) }

	/**
	 * Hash the element with the Nth hash function.
	 *
	 * @param element
	 *   The element to hash.
	 * @param hashIndex
	 *   Which hash function to compute.
	 */
	private fun computeHash(element: Int, hashIndex: Int): Int =
		IntegerDescriptor.computeHashOfLong(
			(element.toLong() shl 32) + hashIndex)

	/**
	 * Set one bit in the table.
	 *
	 * @param hash
	 *   The hash value for which to set a bit in the table.  The value will be
	 *   wrapped to fit the table.
	 */
	private fun setBit(hash: Int)
	{
		val index = (hash ushr 6) % array.size
		val mask = 1L shl (hash and 63)
		array[index] = array[index] or mask
	}

	/**
	 * Test if a particular bit is set in the table.
	 *
	 * @param hash
	 *   The hash value for which to test a bit in the table.  The value will be
	 *   wrapped to fit the table.
	 * @return
	 *   `true` if the corresponding bit of the table is set, otherwise `false`.
	 */
	private fun testBit(hash: Int): Boolean
	{
		val index = (hash ushr 6) % array.size
		val mask = 1L shl (hash and 63)
		return (array[index] and mask) != 0L
	}

	/**
	 * Add all elements of the given [BloomFilter] to the receiver.  The array
	 * size and `hashCount` of the given `filter` should match that of the
	 * receiver.
	 *
	 * @param filter
	 *   The other filter whose element should be included in the receiver.
	 */
	fun addAll(filter: BloomFilter)
	{
		assert (array.size == filter.array.size)
		assert (hashCount == filter.hashCount)
		for (i in array.indices)
		{
			array[i] = array[i] or filter.array[i]
		}
	}
}
