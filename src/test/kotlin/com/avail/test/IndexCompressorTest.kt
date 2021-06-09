/*
 * IndexCompressorTest.kt
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
package com.avail.test

import com.avail.serialization.FourStreamIndexCompressor
import com.avail.serialization.IndexCompressor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Basic functionality test of [IndexCompressor]'s implementations.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class IndexCompressorTest
{
	/**
	 * Test: Check that an exponentially skewed distribution of values can make
	 * a round-trip.
	 */
	@Test
	fun compressRandomStream()
	{
		val random = Random(478393)
		val compressor = FourStreamIndexCompressor()
		val decompressor = FourStreamIndexCompressor()
		repeat(10_000) {
			if (random.nextFloat() < 0.1)
			{
				compressor.incrementIndex()
				decompressor.incrementIndex()
			}
			val r = random.nextDouble()
			val deltaBack = Math.min(
				kotlin.math.floor(Math.pow(2.0, r * 32.0)).toInt(),
				Int.MAX_VALUE - 128)
			val index = compressor.currentIndex() - deltaBack
			val compressed = compressor.compress(index)
			val decompressed = decompressor.decompress(compressed)
			Assertions.assertEquals(index, decompressed)
		}
	}
}
