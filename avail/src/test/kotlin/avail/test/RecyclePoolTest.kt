/*
 * RecyclePoolTest.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
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

import avail.AvailRuntime
import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.smallIntegerLimit
import avail.descriptor.numbers.IntegerDescriptor.RecyclePool
import avail.descriptor.representation.A_Number.Companion.extractInt
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle

/**
 * Unit tests for [RecyclePool].  Each test first checks its own preconditions,
 * so a passing result can't come from an empty pool or from a shared integer.
 */
@TestInstance(Lifecycle.PER_CLASS)
class RecyclePoolTest
{
	/**
	 * Bootstraps the [AvailRuntime], which [IntegerDescriptor] needs before any
	 * integer can be created.
	 */
	private val helper = AvailRuntimeTestHelper(false)

	/** Shut down the [AvailRuntime] after the tests. */
	@AfterAll
	fun tearDownRuntime()
	{
		helper.tearDownRuntime()
	}

	/**
	 * Precondition for the other tests: 100_000 is beyond the small-integer
	 * range, so each [fromInt] call makes a fresh mutable object.  A small value
	 * is shared, so repeated calls return the same object, which shows the
	 * contrast.
	 */
	@Test
	fun largeIntegersAreBeyondSmallIntegerRange()
	{
		assertTrue(100_000 >= smallIntegerLimit)
		assertSame(fromInt(5), fromInt(5))
		assertNotSame(fromInt(100_000), fromInt(100_000))
		assertTrue(fromInt(100_000).descriptor.isMutable)
	}

	/** The capacity must be a power of two, checked by an assertion. */
	@Test
	fun capacityMustBePowerOfTwo()
	{
		assertThrows(AssertionError::class.java) {
			RecyclePool(3)
		}
	}

	/**
	 * With four slots, pushing five objects drops the oldest.  The four newest
	 * come back last-in first-out, carrying their new values, and then the pool
	 * is empty.
	 */
	@Test
	fun oldestEntryIsDroppedWhenFull()
	{
		val pool = RecyclePool(4)
		// Values above the small-integer limit are fresh and mutable, so each
		// one is a real candidate for the pool.
		val objects = (1..5).map { fromInt(100_000 + it) }
		assertTrue(objects.all { it.descriptor.isMutable })
		objects.forEach(pool::recycleIfMutable)
		for (k in 0..3)
		{
			val box = pool.boxInt(200_000 + k)
			assertSame(objects[4 - k], box)
			assertEquals(200_000 + k, box.extractInt)
		}
		val fresh = pool.boxInt(200_004)
		assertEquals(200_004, fresh.extractInt)
		assertFalse(objects.any { it === fresh })
	}

	/**
	 * Push, pop, and push again past the capacity, so the top index wraps.  The
	 * oldest surviving entry is overwritten, and the rest pop in order.
	 */
	@Test
	fun wrapAroundKeepsNewestInLastInFirstOutOrder()
	{
		val pool = RecyclePool(4)
		val objects = (1..6).map { fromInt(100_000 + it) }
		assertTrue(objects.all { it.descriptor.isMutable })
		val a = objects[0]
		val b = objects[1]
		val c = objects[2]
		val d = objects[3]
		val e = objects[4]
		val f = objects[5]
		pool.recycleIfMutable(a)
		pool.recycleIfMutable(b)
		pool.recycleIfMutable(c)
		assertSame(c, pool.boxInt(200_000))
		pool.recycleIfMutable(d)
		pool.recycleIfMutable(e)
		pool.recycleIfMutable(f)
		assertSame(f, pool.boxInt(200_001))
		assertSame(e, pool.boxInt(200_002))
		assertSame(d, pool.boxInt(200_003))
		assertSame(b, pool.boxInt(200_004))
		val fresh = pool.boxInt(200_005)
		assertFalse(objects.any { it === fresh })
	}

	/** An empty pool allocates a fresh mutable integer each time. */
	@Test
	fun emptyPoolAllocatesFreshIntegers()
	{
		val pool = RecyclePool(4)
		val first = pool.boxInt(200_000)
		val second = pool.boxInt(200_001)
		assertEquals(200_000, first.extractInt)
		assertEquals(200_001, second.extractInt)
		assertTrue(first.descriptor.isMutable)
		assertNotSame(first, second)
	}

	/** An immutable integer must never be pushed, so it's never reused. */
	@Test
	fun immutableIntegersAreNotRecycled()
	{
		val pool = RecyclePool(4)
		val immutable = fromInt(100_000).makeImmutable()
		assertFalse(immutable.descriptor.isMutable)
		pool.recycleIfMutable(immutable)
		assertNotSame(immutable, pool.boxInt(200_000))
	}

	/**
	 * A small integer is served from the shared table and must not consume a
	 * pooled entry, which stays available for the next large integer.
	 */
	@Test
	fun smallIntegersDoNotConsumeEntries()
	{
		val pool = RecyclePool(4)
		val pooled = fromInt(100_000)
		assertTrue(pooled.descriptor.isMutable)
		pool.recycleIfMutable(pooled)
		val small = pool.boxInt(5)
		assertEquals(5, small.extractInt)
		assertFalse(small.descriptor.isMutable)
		assertSame(pooled, pool.boxInt(200_000))
	}
}
