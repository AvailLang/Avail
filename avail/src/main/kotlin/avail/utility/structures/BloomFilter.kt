/*
 * BloomFilter.kt
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

import avail.descriptor.representation.AvailObject
import avail.utility.unvlqInt
import avail.utility.vlq
import java.io.DataInputStream
import java.io.DataOutputStream

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
 * @param T
 *   The type of objects hashed into the filter.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 */
class BloomFilter<T>
{
	/**
	 * Construct a new instance that initially reports `false` for every
	 * membership test.  If a non-null `existingFilter` is provided, it is
	 * copied instead.
	 *
	 * @param bitCount
	 *   The number of bits that can be set independently in the table.  This
	 *   number may be rounded up to a convenient size (currently up to a
	 *   multiple of 64).
	 * @param hashCount
	 *   The number of hash functions to apply for each element.  Each hash
	 *   function rehashes the given value (an [Int]), and requires the
	 *   corresponding bit position to contain a `1` bit if the element is
	 *   present (but they may all happen to be `1` even if the element is not
	 *   present).
	 */
	constructor(bitCount: Int, hashCount: Int)
	{
		assert(hashCount < hashSalts.size)
		this.hashCount = hashCount
		this.array = LongArray((bitCount - 1 shr 6) + 1)
		this.size = array.size.toULong()
	}

	/**
	 * Copy an existing [BloomFilter].
	 *
	 * @param existingFilter
	 *   The [BloomFilter] to copy.
	 */
	constructor(existingFilter: BloomFilter<T>)
	{
		this.hashCount = existingFilter.hashCount
		this.array = existingFilter.array.copyOf()
		this.size = array.size.toULong()
	}

	/** The number of bit positions in the filter. */
	val bitCount: Int get() = array.size shl 6

	/** The number of hash functions to use. */
	private val hashCount: Int

	/**
	 * The bits, guaranteed to be 1 if a hash function produced that index for
	 * any element that has been added.
	 */
	private val array: LongArray

	/**
	 * The size of the [array], computed during construction.
	 */
	private val size: ULong

	/**
	 * Add an [Int], the [hashCode] of some element, to the [BloomFilter].
	 *
	 * @param elementHash
	 *   The [hashCode] of the element to add to the [BloomFilter].
	 */
	fun addHash(elementHash: Int) =
		(1..hashCount).forEach {
			setBit(computeHash(elementHash, it))
		}

	/**
	 * Add an element ([T]) to the filter.
	 *
	 * @param element
	 *   The value to [hash][hashCode] and add to the filter.
	 */
	fun add(element: T) = addHash(element.hashCode())

	/**
	 * Determine whether an element with the given [hashCode] is probably
	 * present in the filter. If the element is present, this always returns
	 * true, but it may sometimes return true even if the element is not
	 * present.
	 *
	 * @param elementHash
	 *   The [Int] to test for probable membership in the [BloomFilter].
	 * @return
	 *   Always `true` if the element is present in the filter, or *usually*
	 *   `false` if it is not in the filter.
	 */
	fun getByHash(elementHash: Int): Boolean =
		(1..hashCount).all { testBit(computeHash(elementHash, it)) }

	/**
	 * Determine whether the given element is probably present in the filter.
	 * If the element is present, this always returns true, but it may sometimes
	 * return true even if the element is not present.
	 *
	 * @param element
	 *   The [Int] to test for probable membership in the [BloomFilter].
	 * @return
	 *   Always `true` if the element is present in the filter, or *usually*
	 *   `false` if it is not in the filter.
	 */
	operator fun get(element: T): Boolean = getByHash(element.hashCode())

	/**
	 * Hash the element with the Nth hash function.
	 *
	 * @param element
	 *   The element to hash.
	 * @param hashIndex
	 *   Which hash function to compute.
	 */
	private fun computeHash(element: Int, hashIndex: Int): Int =
		AvailObject.combine2(element, hashSalts[hashIndex])

	/**
	 * Set one bit in the table.
	 *
	 * @param hash
	 *   The hash value for which to set a bit in the table.  The value will be
	 *   wrapped to fit the table.
	 */
	private fun setBit(hash: Int)
	{
		// Use the upper bits to avoid division.  The hash values should be of
		// high enough quality in the upper bits to justify this.
		val inRange = (hash.toUInt().toULong() * size shr (32 - 6)).toInt()
		val index = inRange ushr 6
		val mask = 1L shl (inRange and 63)
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
		// Use the upper bits to avoid division.  The hash values should be of
		// high enough quality in the upper bits to justify this.
		val inRange = (hash.toUInt().toULong() * size shr (32 - 6)).toInt()
		val index = inRange ushr 6
		val mask = 1L shl (inRange and 63)
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
	fun addAll(filter: BloomFilter<T>)
	{
		assert (size == filter.size)
		assert (hashCount == filter.hashCount)
		for (i in array.indices)
		{
			array[i] = array[i] or filter.array[i]
		}
	}

	/**
	 * Write this [BloomFilter] onto the given stream, such that the constructor
	 * taking a [DataInputStream] can reconstruct it.
	 *
	 * @param binaryStream
	 *   The [DataOutputStream] on which to serialize this [BloomFilter].
	 */
	fun write(binaryStream: DataOutputStream)
	{
		binaryStream.vlq(bitCount)
		binaryStream.vlq(hashCount)
		array.forEach(binaryStream::writeLong)
	}

	/**
	 * Reconstruct a [BloomFilter] previously written by [write].
	 *
	 * @param binaryStream
	 *   The [DataInputStream] from which to perform the reconstruction.
	 */
	constructor(
		binaryStream: DataInputStream
	): this(
		binaryStream.unvlqInt(),
		binaryStream.unvlqInt())
	{
		array.indices.forEach {
			array[it] = binaryStream.readLong()
		}
	}

	companion object
	{
		/**
		 * A collection of random, permanent salts for producing successive hash
		 * values to index the filter's bit vector.  Don't use more than this
		 * number of hash values in any [BloomFilter] (or extend this list).
		 * Note that adding entries (and using them) will break backward
		 * compatibility, where newer hashes cannot be recognized or reproduced
		 * by older code.
		 */
		private val hashSalts = intArrayOf(
			0x106A0E28,
			-0x75F627F4,
			-0x51C4CBC8,
			-0x34F95F19,
			0x596F6ACD,
			-0x6A6FB895,
			0x3173A4D9,
			-0x771EA1ED,
		)
	}
}
