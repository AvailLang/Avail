/*
 * CollectionsTest.kt
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
package avail.test

import avail.utility.cartesianProductForEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for the functions in `CollectionExtensions.kt`.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class CollectionsTest
{
	private fun <X> checkCartesianProduct(
		inputLists: List<List<X>>,
		expected: List<List<X>>)
	{
		val accumulator = mutableListOf<List<X>>()
		inputLists.cartesianProductForEach { accumulator.add(it) }
		assertEquals(expected, accumulator)
	}

	@Test
	fun testCartesianProduct()
	{
		checkCartesianProduct(emptyList<List<Int>>(), listOf(emptyList()))

		checkCartesianProduct(
			listOf(listOf(10, 20)),
			listOf(listOf(10), listOf(20)))

		checkCartesianProduct(sampleList1, sampleList1Product)

		checkCartesianProduct(sampleList2, sampleList2Product)
	}

	companion object
	{
		/** A [List] of [List]s of [Int] for testing. */
		val sampleList1 = listOf(
			listOf(10, 20),
			listOf(3, 4, 5))

		/** The expected Cartesian product of the lists in sampleList1. */
		val sampleList1Product = listOf(
			listOf(10, 3),
			listOf(10, 4),
			listOf(10, 5),
			listOf(20, 3),
			listOf(20, 4),
			listOf(20, 5))

		/**
		 * A [List] of [List]s of [Int] for testing.
		 */
		val sampleList2 = listOf(
			listOf(10, 20),
			listOf(3, 4, 5),
			listOf(6,7))

		/** The expected Cartesian product of the lists in sampleList2. */
		val sampleList2Product = listOf(
			listOf(10, 3, 6),
			listOf(10, 3, 7),
			listOf(10, 4, 6),
			listOf(10, 4, 7),
			listOf(10, 5, 6),
			listOf(10, 5, 7),
			listOf(20, 3, 6),
			listOf(20, 3, 7),
			listOf(20, 4, 6),
			listOf(20, 4, 7),
			listOf(20, 5, 6),
			listOf(20, 5, 7))
	}
}
