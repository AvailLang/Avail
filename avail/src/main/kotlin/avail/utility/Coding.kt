/*
 * Coding.kt
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

import java.io.DataInputStream
import java.io.DataOutputStream

////////////////////////////////////////////////////////////////////////////////
//                        Nonnegative integer coding.                         //
////////////////////////////////////////////////////////////////////////////////

/**
 * Apply a variable-length universal coding strategy to the supplied value,
 * encoding it onto the receiver using MIDI VLQ. The receiver is treated as
 * unsigned.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param value
 *   The value to encode.
 * @see <a href="https://en.wikipedia.org/wiki/Variable-length_quantity">
 *   Variable-length quantity</a>
 */
internal fun DataOutputStream.vlq(value: Int)
{
	var residue = value
	while (residue >= 128)
	{
		val b = (residue and 0x7F) or 0x80
		write(b)
		residue = residue ushr 7
	}
	write(residue)
}


/**
 * Apply a variable-length universal coding strategy to the supplied value,
 * encoding it onto the receiver using MIDI VLQ. The receiver is treated as
 * unsigned.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param value
 *   The value to encode.
 * @see <a href="https://en.wikipedia.org/wiki/Variable-length_quantity">
 *   Variable-length quantity</a>
 */
internal fun DataOutputStream.vlq(value: Long)
{
	var residue = value
	while (residue >= 128)
	{
		val b = (residue and 0x7F) or 0x80
		write(b.toInt())
		residue = residue ushr 7
	}
	write(residue.toInt())
}

/**
 * Unapply the variable-length universal coding strategy applied by
 * [`vlq`][DataOutputStream.vlq] to decode a nonnegative integer from the
 * receiver.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @return
 *   The requested integer.
 * @see <a href="https://en.wikipedia.org/wiki/Variable-length_quantity">
 *   Variable-length quantity</a>
 */
internal fun DataInputStream.unvlqInt(): Int
{
	var n = 0
	var k = 0
	while (true)
	{
		val b = read()
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
				return n or (b shl k)
			}
		}
	}
}

/**
 * Unapply the variable-length universal coding strategy applied by
 * [`vlq`][DataOutputStream.vlq] to decode a nonnegative integer from the
 * receiver.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @return
 *   The requested integer.
 * @see <a href="https://en.wikipedia.org/wiki/Variable-length_quantity">
 *   Variable-length quantity</a>
 */
internal fun DataInputStream.unvlqLong(): Long
{
	var n = 0L
	var k = 0
	while (true)
	{
		val b = read()
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
				return n or (b shl k).toLong()
			}
		}
	}
}

////////////////////////////////////////////////////////////////////////////////
//                              Integer coding.                               //
////////////////////////////////////////////////////////////////////////////////

/**
 * Apply a variable-length universal coding strategy to the supplied value,
 * encoding it onto the receiver using zigzag.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param value
 *   The value to encode.
 * @see <a href="https://gist.github.com/mfuerstenau/ba870a29e16536fdbaba">
 *   Zigzag</a>
 */
internal fun DataOutputStream.zigzag(value: Int) =
	vlq((value shr Int.SIZE_BITS-1) xor (value shl 1))

/**
 * Apply a variable-length universal coding strategy to the supplied value,
 * encoding it onto the receiver using zigzag.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param value
 *   The value to encode.
 * @see <a href="https://gist.github.com/mfuerstenau/ba870a29e16536fdbaba">
 *   Zigzag</a>
 */
internal fun DataOutputStream.zigzag(value: Long) =
	vlq((value shr Long.SIZE_BITS-1) xor (value shl 1))

/**
 * Unapply the variable-length universal coding strategy applied by
 * [`zigzag`][DataOutputStream.zigzag] to decode a nonnegative integer from the
 * receiver.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @return
 *   The requested integer.
 * @see <a href="https://gist.github.com/mfuerstenau/ba870a29e16536fdbaba">
 *   Zigzag</a>
 */
internal fun DataInputStream.unzigzagInt(): Int
{
	val n = unvlqInt()
	val u = n.toUInt()
	val l = u shr 1
	val r = (-(n and 1)).toUInt()
	return (l xor r).toInt()
}

/**
 * Unapply the variable-length universal coding strategy applied by
 * [`zigzag`][DataOutputStream.zigzag] to decode a nonnegative integer from the
 * receiver.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @return
 *   The requested integer.
 * @see <a href="https://gist.github.com/mfuerstenau/ba870a29e16536fdbaba">
 *   Zigzag</a>
 */
internal fun DataInputStream.unzigzagLong(): Long
{
	val n = unvlqLong()
	val u = n.toULong()
	val l = u shr 1
	val r = (-(n and 1)).toULong()
	return (l xor r).toLong()
}

////////////////////////////////////////////////////////////////////////////////
//                             Byte array coding.                             //
////////////////////////////////////////////////////////////////////////////////

/**
 * Encode the supplied byte array onto the receiver, starting with the
 * [VLQ][DataOutputStream.vlq] encoding of the size prefix, then followed by the
 * bytes.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param bytes
 *   The bytes to write.
 */
internal fun DataOutputStream.sizedByteArray(bytes: ByteArray)
{
	vlq(bytes.size)
	write(bytes)
}

/**
 * Unapply the encoding strategy applied by [sizedByteArray] to decode a byte
 * array from the receiver.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @return
 *   The decoded bytes.
 */
internal fun DataInputStream.decodeByteArray(): ByteArray
{
	val size = unvlqInt()
	val bytes = ByteArray(size)
	readFully(bytes)
	return bytes
}

////////////////////////////////////////////////////////////////////////////////
//                               String coding.                               //
////////////////////////////////////////////////////////////////////////////////

/**
 * Encode the supplied string onto the receiver, starting with the
 * [VLQ][DataOutputStream.vlq] encoding of the UTF-8 byte count prefix, then
 * followed by the UTF-8 bytes.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param s
 *   The string to write.
 */
internal fun DataOutputStream.sizedString(s: String) =
	sizedByteArray(s.encodeToByteArray())

/**
 * Unapply the encoding strategy applied by [sizedString] to decode a string
 * from the receiver.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @return
 *   The decoded string.
 */
internal fun DataInputStream.decodeString() = decodeByteArray().decodeToString()
