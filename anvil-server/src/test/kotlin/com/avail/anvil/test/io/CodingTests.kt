/*
 * CodingTests.kt
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

package com.avail.anvil.test.io

import avail.anvil.io.decodeList
import avail.anvil.io.decodeString
import avail.anvil.io.encode
import avail.anvil.io.unvlqInt
import avail.anvil.io.unvlqLong
import avail.anvil.io.unzigzagInt
import avail.anvil.io.unzigzagLong
import avail.anvil.io.vlq
import avail.anvil.io.zigzag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * Test the low-level coding utilities.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
class CodingTests
{
	/**
	 * Test [Int.vlq] and [unvlqInt].
	 */
	@Test
	fun testIntVlq ()
	{
		val buffers = mutableListOf(ByteBuffer.allocate(BUFFER_SIZE))
		var buffer = buffers[0]
		(0..1024).forEach { i ->
			i.vlq(
				buffer,
				writeMore = { full, proceed ->
					assertTrue(!full.hasRemaining())
					buffer = ByteBuffer.allocate(BUFFER_SIZE)
					buffers.add(buffer)
					proceed(buffer)
				}
			) {
				// No action required.
			}
		}
		buffer = buffers.removeAt(0)
		buffer.flip()
		(0..1024).forEach { i ->
			unvlqInt(
				buffer,
				readMore = { proceed ->
					buffer = buffers.removeAt(0)
					buffer.flip()
					proceed(buffer)
				}
			) { n, _ ->
				assertEquals(i, n)
			}
		}
	}

	/**
	 * Test [Long.vlq] and [unvlqLong].
	 */
	@Test
	fun testLongVlq ()
	{
		val buffers = mutableListOf(ByteBuffer.allocate(BUFFER_SIZE))
		var buffer = buffers[0]
		(0L..1024L).forEach { i ->
			i.vlq(
				buffer,
				writeMore = { full, proceed ->
					assertTrue(!full.hasRemaining())
					buffer = ByteBuffer.allocate(BUFFER_SIZE)
					buffers.add(buffer)
					proceed(buffer)
				}
			) {
				// No action required.
			}
		}
		buffer = buffers.removeAt(0)
		buffer.flip()
		(0L..1024L).forEach { i ->
			unvlqLong(
				buffer,
				readMore = { proceed ->
					buffer = buffers.removeAt(0)
					buffer.flip()
					proceed(buffer)
				}
			) { n, _ ->
				assertEquals(i, n)
			}
		}
	}

	/**
	 * Test [Int.zigzag] and [unzigzagInt].
	 */
	@Test
	fun testIntZigzag ()
	{
		val buffers = mutableListOf(ByteBuffer.allocate(BUFFER_SIZE))
		var buffer = buffers[0]
		(-512..512).forEach { i ->
			i.zigzag(
				buffer,
				writeMore = { full, proceed ->
					assertTrue(!full.hasRemaining())
					buffer = ByteBuffer.allocate(BUFFER_SIZE)
					buffers.add(buffer)
					proceed(buffer)
				}
			) {
				// No action required.
			}
		}
		buffer = buffers.removeAt(0)
		buffer.flip()
		(-512..512).forEach { i ->
			unzigzagInt(
				buffer,
				readMore = { proceed ->
					buffer = buffers.removeAt(0)
					buffer.flip()
					proceed(buffer)
				}
			) { n, _ ->
				assertEquals(i, n)
			}
		}
	}

	/**
	 * Test [Long.zigzag] and [unzigzagLong].
	 */
	@Test
	fun testLongZigzag ()
	{
		val buffers = mutableListOf(ByteBuffer.allocate(BUFFER_SIZE))
		var buffer = buffers[0]
		(-512L..512L).forEach { i ->
			i.zigzag(
				buffer,
				writeMore = { full, proceed ->
					assertTrue(!full.hasRemaining())
					buffer = ByteBuffer.allocate(BUFFER_SIZE)
					buffers.add(buffer)
					proceed(buffer)
				}
			) {
				// No action required.
			}
		}
		buffer = buffers.removeAt(0)
		buffer.flip()
		(-512L..512L).forEach { i ->
			unzigzagLong(
				buffer,
				readMore = { proceed ->
					buffer = buffers.removeAt(0)
					buffer.flip()
					proceed(buffer)
				}
			) { n, _ ->
				assertEquals(i, n)
			}
		}
	}

	/**
	 * Test [String.encode] and [decodeString].
	 */
	@Test
	fun testString ()
	{
		// Shrink the buffer since we have less data to encode.
		val buffers = mutableListOf(ByteBuffer.allocate(SMALL_BUFFER_SIZE))
		var buffer = buffers[0]
		@Suppress("SpellCheckingInspection")
		val strings = listOf(
			"hello",
			"world",
			"日本語",
			"カタカナ",
			"職業",
			"Кожаный пиджак",
			"\uD83D\uDE31\uD83D\uDC7B\uD83D\uDC7A")
		strings.forEach { s ->
			s.encode(
				buffer,
				writeMore = { full, proceed ->
					assertTrue(!full.hasRemaining())
					buffer = ByteBuffer.allocate(SMALL_BUFFER_SIZE)
					buffers.add(buffer)
					proceed(buffer)
				}
			) {
				// No action required.
			}
		}
		buffer = buffers.removeAt(0)
		buffer.flip()
		strings.forEach { s ->
			decodeString(
				buffer,
				readMore = { proceed ->
					buffer = buffers.removeAt(0)
					buffer.flip()
					proceed(buffer)
				}
			) { d, _ ->
				assertEquals(d, s)
			}
		}
	}

	/**
	 * Test [List.encode] and [decodeList] for a list of integers.
	 */
	@Test
	fun testListOfInts ()
	{
		val buffers = mutableListOf(ByteBuffer.allocate(BUFFER_SIZE))
		var buffer = buffers[0]
		val list = List(20) { Random.nextInt() }
		list.encode(
			buffer,
			encodeOne = Int::zigzag,
			writeMore = { full, proceed ->
				assertTrue(!full.hasRemaining())
				buffer = ByteBuffer.allocate(BUFFER_SIZE)
				buffers.add(buffer)
				proceed(buffer)
			}
		) {
			// No action required.
		}
		buffer = buffers.removeAt(0)
		buffer.flip()
		decodeList<Int>(
			buffer,
			decodeOne = { bytes1, readMore, _, again ->
				unzigzagInt(bytes1, readMore = readMore, done = again)
			},
			readMore = { proceed ->
				buffer = buffers.removeAt(0)
				buffer.flip()
				proceed(buffer)
			},
			failed = { _, _ -> fail() }
		) { result, _ ->
			assertEquals(result, list)
		}
	}


	/**
	 * Test [List.encode] and [decodeList] for a list of strings.
	 */
	@Test
	fun testListOfStrings ()
	{
		val buffers = mutableListOf(ByteBuffer.allocate(SMALL_BUFFER_SIZE))
		var buffer = buffers[0]
		@Suppress("SpellCheckingInspection")
		val list = listOf(
			"hello",
			"world",
			"日本語",
			"カタカナ",
			"職業",
			"Кожаный пиджак",
			"\uD83D\uDE31\uD83D\uDC7B\uD83D\uDC7A")
		list.encode(
			buffer,
			encodeOne = String::encode,
			writeMore = { full, proceed ->
				assertTrue(!full.hasRemaining())
				buffer = ByteBuffer.allocate(SMALL_BUFFER_SIZE)
				buffers.add(buffer)
				proceed(buffer)
			}
		) {
			// No action required.
		}
		buffer = buffers.removeAt(0)
		buffer.flip()
		decodeList<String>(
			buffer,
			decodeOne = { bytes1, readMore, _, again ->
				decodeString(bytes1, readMore, again)
			},
			readMore = { proceed ->
				buffer = buffers.removeAt(0)
				buffer.flip()
				proceed(buffer)
			},
			failed = { _, _ -> fail() }
		) { result, _ ->
			assertEquals(result, list)
		}
	}

	companion object
	{
		/**
		 * Make the buffers deliberately too small to accommodate the tests, to
		 * force buffer train logic to activate.
		 */
		private const val BUFFER_SIZE: Int = 256

		/**
		 * A significantly smaller buffer size than [BUFFER_SIZE].
		 */
		private const val SMALL_BUFFER_SIZE: Int = 16
	}
}
