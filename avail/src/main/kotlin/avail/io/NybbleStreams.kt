/*
 * NybbleStreams.kt
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

package avail.io

import avail.io.NybbleArray.Companion.nybbleStrings
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * `NybbleOutputStream` writes a dense stream of nybbles, i.e., values in
 * [0,15], backed by an underlying [ByteArrayOutputStream].
 *
 * @param initialCapacity
 *   The initial capacity of the underlying data storage, in nybbles.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class NybbleOutputStream constructor(initialCapacity: Int = 0)
{
	/**
	 * The underlying [ByteArrayOutputStream], which directly exposes its
	 * underlying [ByteArray].
	 */
	private val bytes = object : ByteArrayOutputStream(
		(initialCapacity ushr 1) + (initialCapacity and 1))
	{
		/**
		 * The backing buffer, _not_ a copy.
		 */
		val buffer get() = buf
	}

	/**
	 * The zero-based nybble index of the next write. Even indices correspond to
	 * low nybbles of underlying bytes, whereas odd indices correspond to high
	 * nybbles.
	 */
	private var nybbleIndex = 0

	/** The number of nybbles presently stored. */
	val size get() = nybbleIndex

	/** The zero-based index of the next byte to be written. */
	private inline val byteIndex get() = nybbleIndex ushr 1

	/** The last byte to be written. */
	private inline val lastByte get() = bytes.buffer[byteIndex].toInt()

	/**
	 * Append the given nybble.
	 *
	 * @param nybble
	 *   The nybble to append, Must be in [0..15].
	 */
	fun write(nybble: Int)
	{
		assert(nybble in 0 .. 15) { "b must be in [0..15]" }
		if (nybbleIndex and 1 == 1)
		{
			bytes.buffer[byteIndex] = (lastByte or (nybble shl 4)).toByte()
		}
		else
		{
			bytes.write(nybble)
		}
		nybbleIndex++
	}

	/**
	 * Answer a [NybbleArray] that contains the contents of the
	 * [receiver][NybbleOutputStream]. The answered array is disjoint from the
	 * receiver's underlying storage, and may be safely modified by the caller.
	 *
	 * @return
	 *   The requested array.
	 */
	fun toNybbleArray(): NybbleArray = NybbleArray(bytes.toByteArray(), size)

	override fun toString() = toNybbleArray().toString()
}

/**
 * A [NybbleArray] offers dense storage of nybbles, i.e., values in [0,15]. Even
 * indices correspond to low nybbles of bytes, whereas odd indices correspond to
 * high nybbles of bytes.
 *
 * @property bytes
 *   The underlying [ByteArray] that supports primitive data storage.
 * @property size
 *   The number of nybbles available, corresponding to the first [size] nybbles
 *   of [bytes].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class NybbleArray constructor(
	val bytes: ByteArray,
	val size: Int
): Iterable<Byte>
{
	/**
	 * Answer the nybble at the given zero-based [index].
	 *
	 * @param index
	 *   The zero-based nybble index. Even indices correspond to low nybbles,
	 *   and odd indices correspond to high nybbles.
	 * @return
	 *   The requested nybble, guaranteed to be in [0,15].
	 * @throws IndexOutOfBoundsException
	 *   If [index] is out of bounds.
	 */
	operator fun get(index: Int): Byte
	{
		if (index !in 0 until size) throw IndexOutOfBoundsException(index)
		val byte = bytes[index ushr 1].toInt()
		val nybble = (if (index and 1 == 1) byte ushr 4 else byte) and 0x0F
		return nybble.toByte()
	}

	/**
	 * Sets the nybble at the given [index] to the given [value].
	 *
	 * @param index
	 *   The zero-based nybble index. Even indices correspond to low nybbles,
	 *   and odd indices correspond to high nybbles.
	 * @param value
	 *   The value, guaranteed to be in [0,15].
	 * @throws IndexOutOfBoundsException
	 *   If [index] is out of bounds.
	 */
	operator fun set(index: Int, value: Byte)
	{
		assert(value in 0 .. 15) { "value must be in [0..15]" }
		if (index !in 0 until size) throw IndexOutOfBoundsException(index)
		val byteIndex = index ushr 1
		val byte = bytes[byteIndex].toInt()
		val newByte =
			if (index and 1 == 1) (byte and 0x0F) or (value.toInt() ushr 4)
			else (byte and 0xF0) or value.toInt()
		bytes[byteIndex] = newByte.toByte()
	}

	/**
	 * Answer a [NybbleInputStream] positioned at the specified index.
	 *
	 * @param index
	 *   The zero-based nybble index.
	 */
	fun inputStream(index: Int = 0) = NybbleInputStream(this, index)

	/**
	 * Answer an [iterator][Iterator] over the nybbles in the
	 * [receiver][NybbleArray].
	 *
	 * @return
	 *   The requested iterator.
	 */
	override operator fun iterator(): Iterator<Byte> = NybbleIterator(this)

	override fun toString() = buildString {
		this@NybbleArray.forEach { append(nybbleStrings[it.toInt()]) }
	}

	companion object
	{
		/**
		 * The canonical one-character nybble strings, for efficient printing
		 * without resorting to [String.format].
		 */
		val nybbleStrings = (('0' .. '9') + ('A' .. 'F')).map { it.toString() }

		/**
		 * An iterator over the nybbles of a [NybbleArray].
		 *
		 * @property nybbles
		 *   The backing [NybbleArray].
		 * @property nybbleIndex
		 *   The zero-based index of the next nybble to produce.
		 * @author Todd L Smith &lt;todd@availlang.org&gt;
		 */
		private class NybbleIterator constructor(
			val nybbles: NybbleArray,
			private var nybbleIndex: Int = 0
		): Iterator<Byte>
		{
			override fun hasNext() = nybbleIndex < nybbles.size
			override fun next() = nybbles[nybbleIndex++]
		}
	}
}

/**
 * `NybbleInputStream` reads from a [NybbleArray].
 *
 * @param nybbles
 *   The [NybbleArray] that serves as the nybble source.
 * @param nybbleIndex
 *   The zero-based index of the first nybble to read.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class NybbleInputStream constructor(
	private val nybbles: NybbleArray,
	private var nybbleIndex: Int = 0
): InputStream(), Iterable<Int>
{
	/** Whether the receiver is exhausted. */
	val atEnd get() = nybbleIndex == nybbles.size

	/**
	 * The exact number of nybbles that can be read before reaching the
	 * end-of-stream. Reading never blocks.
	 *
	 * @return
	 *   The exact number of nybbles available.
	 */
	override fun available() = nybbles.size - nybbleIndex

	override fun skip(n: Long): Long
	{
		val delta = n.toInt()
		val size = nybbles.size
		if (nybbleIndex + delta > size)
		{
			val skipped = size - nybbleIndex
			nybbleIndex = size
			return skipped.toLong()
		}
		nybbleIndex += delta
		return n
	}

	override fun read() =
		if (nybbleIndex == nybbles.size) -1
		else nybbles[nybbleIndex++].toInt()

	/**
	 * Mark is supported.
	 *
	 * @return
	 *   `true`, because position marking is always supported.
	 */
	override fun markSupported() = true

	/**
	 * The previously recorded mark. Defaults to the beginning of the stream, so
	 * that [reset] is always valid.
	 */
	private var mark = 0

	/**
	 * Set the mark. The read limit is ignored; any number of nybbles may be
	 * read without invalidating [reset].
	 *
	 * @param readLimit
	 *   The read limit; ignored.
	 */
	override fun mark(readLimit: Int)
	{
		mark = nybbleIndex
	}

	/**
	 * Reset the read position to the previous mark. If [mark] was never called
	 * on the [receiver][NybbleInputStream], then reset the read position to the
	 * beginning of the stream.
	 */
	override fun reset()
	{
		nybbleIndex = mark
	}

	/** The read position. */
	val position get() = nybbleIndex

	/**
	 * Move the read position to the specified index.
	 *
	 * @param index
	 *   The zero-based nybble index.
	 * @throws IndexOutOfBoundsException
	 *   If [index] is out of bounds for the underlying [NybbleArray].
	 */
	fun goTo(index: Int)
	{
		if (index !in 0 until nybbles.size)
		{
			throw IndexOutOfBoundsException("$index ∉ [0,${nybbles.size - 1}]")
		}
		nybbleIndex = index
	}

	/**
	 * Answer an [iterator][Iterator] that consumes nybbles from the
	 * [receiver][NybbleInputStream].
	 *
	 * @return
	 *   The requested iterator.
	 */
	override fun iterator() = object : Iterator<Int>
	{
		override fun hasNext() = nybbleIndex != nybbles.size
		override fun next() = read()
	}

	override fun toString() = buildString {
		append('[')
		nybbles.forEachIndexed { index, nybble ->
			val s = nybbleStrings[nybble.toInt()]
			when (index)
			{
				nybbleIndex ->
				{
					append('@')
					append(s)
				}
				mark ->
				{
					append('!')
					append(s)
				}
				else -> append(s)
			}
			append(", ")
		}
		if (nybbles.size > 0) setLength(length - 2)
		append(']')
	}
}
