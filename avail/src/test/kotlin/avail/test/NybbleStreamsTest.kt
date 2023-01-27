/*
 * NybbleStreamsTest.kt
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

package avail.test

import avail.anvil.opcode
import avail.anvil.unvlq
import avail.anvil.vlq
import avail.io.NybbleArray
import avail.io.NybbleInputStream
import avail.io.NybbleOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Exhaustive tests for [NybbleOutputStream], [NybbleArray], and
 * [NybbleInputStream].
 *
 * @author Todd Smith &lt;todd@availlang.org&gt;
 */
class NybbleStreamsTest
{
	/**
	 * Ensure that:
	 *
	 * * Each possible nybble can occupy the low and high position of
	 *   a [NybbleOutputStream],
	 * * A [NybbleOutputStream] correctly sequences its elements,
	 * * A [NybbleOutputStream] correctly reports its size,
	 * * A [NybbleArray] correctly reports its size.
	 * * A [NybbleArray] correctly accesses its elements,
	 * * A [NybbleInputStream] correctly reports its available elements,
	 * * A [NybbleInputStream] correctly sequences its elements,
	 * * A [NybbleInputStream] correctly reports its end-of-stream condition.
	 */
	@Test
	fun testNybbles()
	{
		val output = NybbleOutputStream()
		allNybbles.forEach { output.write(it) }
		assertEquals(16, output.size)
		val evens = allNybbles.filter { it and 1 == 0 }
		val odds = allNybbles.filter { it and 1 == 1 }
		odds.zip(evens).forEach { (odd, even) ->
			output.write(odd)
			output.write(even)
		}
		assertEquals(32, output.size)
		val array = output.toNybbleArray()
		assertEquals(32, array.size)
		val input = NybbleInputStream(array)
		assertEquals(32, input.available())
		allNybbles.forEach { assertEquals(it, input.read()) }
		assertEquals(16, input.available())
		odds.zip(evens).forEach { (odd, even) ->
			assertEquals(odd, input.read())
			assertEquals(even, input.read())
		}
		assertEquals(0, input.available())
		assertEquals(-1, input.read())
		assertEquals(-1, input.read())
	}

	/**
	 * Ensure that [NybbleOutputStream] and [NybbleInputStream] correctly
	 * support opcode coding.
	 */
	@Test
	fun testOpcodes()
	{
		val output = NybbleOutputStream()
		sampleOpcodes.forEach { output.opcode(it) }
		val input = NybbleInputStream(output.toNybbleArray())
		sampleOpcodes.forEach { assertEquals(it, input.opcode()) }
	}

	/**
	 * Ensure that [NybbleOutputStream] and [NybbleInputStream] correctly
	 * support variable-width coding.
	 */
	@Test
	fun testData()
	{
		val output = NybbleOutputStream()
		sampleData.forEach { output.vlq(it) }
		val input = NybbleInputStream(output.toNybbleArray())
		sampleData.forEach { assertEquals(it, input.unvlq()) }
	}

	companion object
	{
		/** Every possible (unsigned) nybble. */
		val allNybbles = 0 .. 15

		/** The sample values for opcode testing. */
		val sampleOpcodes = 0 .. 128

		/** The sample values for VLQ testing. */
		val sampleData = 0 .. 16384
	}
}
