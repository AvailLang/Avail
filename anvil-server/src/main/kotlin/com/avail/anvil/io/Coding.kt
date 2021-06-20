/*
 * Coding.kt
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

package com.avail.anvil.io

import com.avail.utility.evaluation.Combinator.recurse
import java.nio.ByteBuffer
import kotlin.math.min

////////////////////////////////////////////////////////////////////////////////
//                         Simplifying type aliases.                          //
////////////////////////////////////////////////////////////////////////////////

/**
 * A function that applies to (1) a filled up [ByteBuffer] and (2) a function
 * for how to continue writing to a new [ByteBuffer].
 */
internal typealias WriteMore = (ByteBuffer, (ByteBuffer) -> Unit) -> Unit

/**
 * A function that applies to (1) the last buffer to receive the encoding of a
 * value.
 */
internal typealias DoneWriting = (ByteBuffer) -> Unit

/**
 * A function that applies to (1) a function that receives the next [ByteBuffer]
 * from which to decode a value.
 */
internal typealias ReadMore = ((ByteBuffer) -> Unit) -> Unit

/**
 * A function that applies to (1) a fully decoded value and (2) the final
 * [ByteBuffer] of the encoded value.
 */
internal typealias DoneReading<T> = (T, ByteBuffer) -> Unit

/**
 * A function that applies to (1) an exception that describes why decoding
 * failed and (2) the latest buffer.
 */
internal typealias FailedReading = (Throwable, ByteBuffer) -> Unit

////////////////////////////////////////////////////////////////////////////////
//                        Nonnegative integer coding.                         //
////////////////////////////////////////////////////////////////////////////////

/**
 * Apply a variable-length universal coding strategy to the receiver, encoding
 * it onto the specified buffer using MIDI VLQ. The receiver is treated as
 * unsigned.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The target buffer.
 * @param writeMore
 *   How to keep writing if the target buffer fills up prematurely.
 * @param done
 *   What to do with the final buffer after encoding completes fully.
 */
internal fun Int.vlq (
	bytes: ByteBuffer,
	writeMore: WriteMore,
	done: DoneWriting)
{
	var residue = this
	while (bytes.hasRemaining())
	{
		// Write 7 LSB bits at a time, setting the MSB for nonfinal bytes.
		val b = residue and 0x7F
		residue = residue ushr 7
		if (residue == 0)
		{
			bytes.put(b.toByte())
			return done(bytes)
		}
		bytes.put((b or 0x80).toByte())
	}
	// There wasn't enough room to fully encode the value.
	writeMore(bytes) { bytes1 -> residue.vlq(bytes1, writeMore, done)}
}

/**
 * Apply a variable-length universal coding strategy to the receiver, encoding
 * it onto the specified buffer using MIDI VLQ. The receiver is treated as
 * unsigned.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The target buffer.
 * @param writeMore
 *   How to keep writing if the target buffer fills up prematurely.
 * @param done
 *   What to do with the final buffer after encoding completes fully.
 */
internal fun Long.vlq (
	bytes: ByteBuffer,
	writeMore: WriteMore,
	done: DoneWriting)
{
	var residue = this
	while (bytes.hasRemaining())
	{
		// Write 7 LSB bits at a time, setting the MSB for nonfinal bytes.
		val b = residue and 0x7F
		residue = residue ushr 7
		if (residue == 0L)
		{
			bytes.put(b.toByte())
			return done(bytes)
		}
		bytes.put((b or 0x80).toByte())
	}
	// There wasn't enough room to fully encode the value.
	writeMore(bytes) { bytes1 -> residue.vlq(bytes1, writeMore, done)}
}

/**
 * Unapply the variable-length universal coding strategy applied by
 * [`vlq`][Int.vlq] to decode a nonnegative integer from the specified buffer.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The source buffer.
 * @param partial
 *   A partially decoded integer. Defaults to `0`.
 * @param shift
 *   The shift factor so far. Defaults to `0`.
 * @param readMore
 *   How to keep reading if the source buffer exhausts prematurely.
 * @param done
 *   What to do with the fully decoded integer.
 */
internal fun unvlqInt (
	bytes: ByteBuffer,
	partial: Int = 0,
	shift: Int = 0,
	readMore: ReadMore,
	done: DoneReading<Int>)
{
	var n = partial
	var k = shift
	while (bytes.hasRemaining())
	{
		val b = bytes.get().toInt()
		when
		{
			b and 0x80 == 0x80 ->
			{
				n = n or ((b and 0x7F) shl k)
				k += 7
			}
			else ->
			{
				// The MSB is clear, so we're done decoding.
				return done(n or (b shl k), bytes)
			}
		}
	}
	// There weren't enough bytes to fully decode the value.
	readMore { bytes1 -> unvlqInt(bytes1, n, k, readMore, done) }
}

/**
 * Unapply the variable-length universal coding strategy applied by
 * [`vlq`][Long.vlq] to decode a nonnegative integer from the specified buffer.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The source buffer.
 * @param partial
 *   A partially decoded integer. Defaults to `0`.
 * @param shift
 *   The shift factor so far. Defaults to `0`.
 * @param readMore
 *   How to keep reading if the source buffer exhausts prematurely.
 * @param done
 *   What to do with the fully decoded integer.
 */
internal fun unvlqLong (
	bytes: ByteBuffer,
	partial: Long = 0L,
	shift: Int = 0,
	readMore: ReadMore,
	done: DoneReading<Long>)
{
	var n = partial
	var k = shift
	while (bytes.hasRemaining())
	{
		val b = bytes.get().toInt()
		when
		{
			b and 0x80 == 0x80 ->
			{
				n = n or ((b and 0x7F).toLong() shl k)
				k += 7
			}
			else ->
			{
				// The MSB is clear, so we're done decoding.
				return done(n or (b shl k).toLong(), bytes)
			}
		}
	}
	// There weren't enough bytes to fully decode the value.
	readMore { bytes1 -> unvlqLong(bytes1, n, k, readMore, done) }
}

////////////////////////////////////////////////////////////////////////////////
//                              Integer coding.                               //
////////////////////////////////////////////////////////////////////////////////

/**
 * Apply a variable-length universal coding strategy to the receiver, encoding
 * it onto the specified buffer using zigzag.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The target buffer.
 * @param writeMore
 *   How to keep writing if the target buffer fills up prematurely.
 * @param done
 *   What to do with the final buffer after encoding completes fully.
 */
internal fun Int.zigzag (
	bytes: ByteBuffer,
	writeMore: WriteMore,
	done: DoneWriting
) = ((this shr Int.SIZE_BITS-1) xor (this shl 1)).vlq(bytes, writeMore, done)

/**
 * Apply a variable-length universal coding strategy to the receiver, encoding
 * it onto the specified buffer using zigzag.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The target buffer.
 * @param writeMore
 *   How to keep writing if the target buffer fills up prematurely.
 * @param done
 *   What to do with the final buffer after encoding completes fully.
 */
internal fun Long.zigzag (
	bytes: ByteBuffer,
	writeMore: WriteMore,
	done: DoneWriting
) = ((this shr Long.SIZE_BITS-1) xor (this shl 1)).vlq(bytes, writeMore, done)

/**
 * Unapply the variable-length universal coding strategy applied by
 * [`zigzag`][Int.zigzag] to decode a nonnegative integer from the specified
 * buffer.
 *
 * @param bytes
 *   The source buffer.
 * @param readMore
 *   How to keep reading if the source buffer exhausts prematurely.
 * @param done
 *   What to do with the fully decoded integer.
 */
internal fun unzigzagInt (
	bytes: ByteBuffer,
	readMore: ReadMore,
	done: DoneReading<Int>
) = unvlqInt(bytes, readMore = readMore) { n, bytes1 ->
	val u = n.toUInt()
	val l = u shr 1
	val r = (-(n and 1)).toUInt()
	done((l xor r).toInt(), bytes1)
}

/**
 * Unapply the variable-length universal coding strategy applied by
 * [`zigzag`][Long.zigzag] to decode a nonnegative integer from the specified
 * buffer.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The source buffer.
 * @param readMore
 *   How to keep reading if the source buffer exhausts prematurely.
 * @param done
 *   What to do with the fully decoded integer.
 */
internal fun unzigzagLong (
	bytes: ByteBuffer,
	readMore: ReadMore,
	done: DoneReading<Long>
) = unvlqLong(bytes, readMore = readMore) { n, bytes1 ->
	val u = n.toULong()
	val l = u shr 1
	val r = (-(n and 1)).toULong()
	done((l xor r).toLong(), bytes1)
}

////////////////////////////////////////////////////////////////////////////////
//                             Byte array coding.                             //
////////////////////////////////////////////////////////////////////////////////

/**
 * Encode the receiver onto the specified buffer, starting with the
 * [VLQ][Int.vlq] encoding of the size prefix, then followed by the bytes.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The target buffer.
 * @param writeMore
 *   How to keep writing if the target buffer fills up prematurely.
 * @param done
 *   What to do with the final buffer after encoding completes fully.
 */
internal fun ByteArray.encode (
	bytes: ByteBuffer,
	writeMore: WriteMore,
	done: DoneWriting
) = this.size.vlq(bytes, writeMore) { bytes1 ->
	if (bytes1.remaining() < size)
	{
		// There isn't enough room to transfer the entire string.
		var i = 0
		var count = size
		return@vlq recurse(bytes1) { bytes2, again ->
			val min = min(count, bytes2.remaining())
			bytes2.put(this, i, min)
			i += min
			count -= min
			if (count == 0)
			{
				// All data has been written.
				return@recurse done(bytes2)
			}
			writeMore(bytes2) { bytes3 -> again(bytes3) }
		}
	}
	// There is enough room to accommodate the entire string.
	bytes1.put(this)
	done(bytes1)
}

/**
 * Unapply the encoding strategy applied by [encode] to decode a byte array from
 * the specified buffer.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The source buffer.
 * @param readMore
 *   How to keep reading if the source buffer exhausts prematurely.
 * @param done
 *   What to do with the fully decoded list.
 */
internal fun decodeByteArray (
	bytes: ByteBuffer,
	readMore: ReadMore,
	done: DoneReading<ByteArray>
) = unvlqInt(bytes, readMore = readMore) { size, bytes1 ->
	val result = ByteArray(size)
	if (bytes1.remaining() < size)
	{
		// Only some of the bytes are available.
		var i = 0
		var count = size
		return@unvlqInt recurse(bytes1) { bytes2, again ->
			val min = min(count, bytes2.remaining())
			bytes2.get(result, i, min)
			i += min
			count -= min
			if (count == 0)
			{
				// All data has been read.
				return@recurse done(result, bytes2)
			}
			readMore { bytes3 -> again(bytes3) }
		}
	}
	// All the bytes are available.
	bytes1.get(result)
	done(result, bytes1)
}

////////////////////////////////////////////////////////////////////////////////
//                               String coding.                               //
////////////////////////////////////////////////////////////////////////////////

/**
 * Encode the receiver onto the specified buffer, starting with the
 * [VLQ][Int.vlq] encoding of the UTF-8 byte count prefix, then followed by the
 * UTF-8 bytes.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The target buffer.
 * @param writeMore
 *   How to keep writing if the target buffer fills up prematurely.
 * @param done
 *   What to do with the final buffer after encoding completes fully.
 */
internal fun String.encode (
	bytes: ByteBuffer,
	writeMore: WriteMore,
	done: DoneWriting
) = this.encodeToByteArray().encode(bytes, writeMore, done)

/**
 * Unapply the encoding strategy applied by [encode] to decode a string from the
 * specified buffer.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The source buffer.
 * @param readMore
 *   How to keep reading if the source buffer exhausts prematurely.
 * @param done
 *   What to do with the fully decoded list.
 */
internal fun decodeString (
	bytes: ByteBuffer,
	readMore: ReadMore,
	done: DoneReading<String>
) = decodeByteArray(bytes, readMore) { data: ByteArray, bytes1: ByteBuffer ->
	done(data.decodeToString(), bytes1)
}

////////////////////////////////////////////////////////////////////////////////
//                                List coding.                                //
////////////////////////////////////////////////////////////////////////////////

/**
 * Encode the receiver onto the specified buffer, starting with the
 * [VLQ][Int.vlq] encoding of the count prefix, then followed by an appropriate
 * representation of each item (in sequential order).
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The target buffer.
 * @param encodeOne
 *   How to encode a single item of the list.
 * @param writeMore
 *   How to keep writing if the target buffer fills up prematurely.
 * @param done
 *   What to do with the final buffer after encoding completes fully.
 */
internal fun <T> List<T>.encode (
	bytes: ByteBuffer,
	encodeOne: (T, ByteBuffer, WriteMore, DoneWriting)->Unit,
	writeMore: WriteMore,
	done: DoneWriting
) = size.vlq(bytes, writeMore) { bytes1 ->
	val items = iterator()
	recurse(bytes1) { bytes2, again ->
		if (!items.hasNext())
		{
			// All items have been written, so the list is fully encoded.
			return@recurse done(bytes2)
		}
		// Encode the next item.
		encodeOne(items.next(), bytes2, writeMore, again)
	}
}

/**
 * Unapply the encoding strategy applied by [encode] to decode a list from the
 * specified buffer.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The source buffer.
 * @param decodeOne
 *   How to decode a single item of the list.
 * @param readMore
 *   How to keep reading if the source buffer exhausts prematurely.
 * @param failed
 *   What to do if decoding fails.
 * @param done
 *   What to do with the fully decoded list.
 */
internal fun <T> decodeList (
	bytes: ByteBuffer,
	decodeOne: (ByteBuffer, ReadMore, FailedReading, DoneReading<T>)->Unit,
	readMore: ReadMore,
	failed: FailedReading,
	done: DoneReading<List<T>>
) = unvlqInt(bytes, readMore = readMore) { size, bytes1 ->
	if (size == 0)
	{
		// The list is empty, so return early.
		return@unvlqInt done(emptyList(), bytes1)
	}
	val result = mutableListOf<T>()
	var remaining = size
	var abort: Throwable? = null
	recurse(bytes1) { bytes2, again ->
		val aborted = abort
		if (aborted !== null)
		{
			// Decoding an item failed, so abort immediately.
			return@recurse failed(aborted, bytes2)
		}
		if (remaining == 0)
		{
			// All items have been read, so the list is fully decoded.
			return@recurse done(result, bytes2)
		}
		// Decode the next item.
		decodeOne(
			bytes2,
			readMore,
			{ e, bytes3 ->
				abort = e
				again(bytes3)
			}
		) { item, bytes3 ->
			result.add(item)
			remaining--
			again(bytes3)
		}
	}
}
