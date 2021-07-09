/*
 * SetTest.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.test

import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.A_Set.Companion.setIntersectionCanDestroy
import com.avail.descriptor.sets.A_Set.Companion.setIntersects
import com.avail.descriptor.sets.A_Set.Companion.setMinusCanDestroy
import com.avail.descriptor.sets.A_Set.Companion.setSize
import com.avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import com.avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.tuples.A_Tuple.Companion.asSet
import com.avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor.Companion.createSmallInterval
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A test of SetDescriptor.
 *
 * @author
 *   Mark van Gulik &lt;mark@availlang.org&gt;
 */
class SetTest
{
	/**
	 * Test: Check the usual simple operations.
	 */
	@Test
	fun testIntegerIntervalTupleDescriptorReverse()
	{
		val set1to20: A_Set = createSmallInterval(1, 20, 1)
			.asSet
			.makeShared()
		assertEquals(20, set1to20.setSize)
		val set11to25: A_Set = createSmallInterval(11, 25, 1)
			.asSet
			.makeShared()
		assertEquals(15, set11to25.setSize)

		// Add new element.
		assertEquals(
			21,
			set1to20
				.setWithElementCanDestroy(
					fromInt(99),
					true)
				.setSize)

		// Add existing element.
		assertEquals(
			20,
			set1to20
				.setWithElementCanDestroy(
					fromInt(10),
					true)
				.setSize)

		// Union
		assertEquals(25, set1to20.setUnionCanDestroy(set11to25, true).setSize)

		// Intersection
		assertEquals(
			10, set1to20.setIntersectionCanDestroy(set11to25, true).setSize)

		// Intersection test - a,b -> true
		assertTrue(set1to20.setIntersects(set11to25))

		// Intersection test - a,a -> true
		assertTrue(set1to20.setIntersects(set1to20))

		// Intersection test - a,empty -> false
		assertFalse(set1to20.setIntersects(emptySet))

		// Intersection test - empty,empty -> false
		assertFalse(emptySet.setIntersects(emptySet))

		// Asymmetric Difference
		assertEquals(10, set1to20.setMinusCanDestroy(set11to25, true).setSize)
	}
}
