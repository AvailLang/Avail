/*
 * PrefixSharingListTest.kt
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

import avail.utility.PrefixSharingList
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.PrefixSharingList.Companion.withoutLast
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import java.util.Random

/**
 * Unit tests for the [PrefixSharingList].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class PrefixSharingListTest
{
	/** A pseudo-random generator for the tests. */
	private val rnd = Random()

	/**
	 * Create a [PrefixSharingList] of the desired size, containing the numbers
	 * from 0 to count-1.
	 *
	 * During assembly, randomly prod the list with queries that cause it to
	 * have a form that may differ on successive calls.
	 *
	 * @param count
	 *   How many elements to put in the list.
	 * @return
	 *   A list of the requested size, with integers running from 0 to
	 *   count - 1.
	 */
	private fun randomlyAssemble(count: Int): List<Int>
	{
		assert(count > 0)
		val flatCount = rnd.nextInt(count)
		// Start with a flat list with flatCount elements.
		var result = (0 until flatCount).toList()
		// Now do non-destructive appends.
		for (i in flatCount until count)
		{
			result = result.append(i)
			if (rnd.nextInt(4) == 0)
			{
				// Try popping and appending.
				result = result.withoutLast().append(i)
			}
			if (rnd.nextInt(4) == 0)
			{
				// Read an early element, forcing construction of the flat list.
				val zero = result[0]
				assert(zero == 0)
			}
		}
		return result
	}

	/**
	 * Test a random assembly process to find corner case bugs.
	 *
	 * @param repetition
	 *   The current test's [RepetitionInfo].
	 */
	@RepeatedTest(20)
	fun testRandomAssembly(repetition: RepetitionInfo)
	{
		val count = repetition.currentRepetition
		assert(count > 0)
		val list1 = randomlyAssemble(count)
		assert(list1.size == count)
		val list2 = randomlyAssemble(count)
		assert(list2.size == count)
		Assertions.assertEquals(list1, list2)
		Assertions.assertEquals(list1.toList(), list2)
		Assertions.assertEquals(list1, list2.toList())
		// Check iterator().
		val list3 = mutableListOf<Int>()
		list2.iterator().forEachRemaining { e -> list3.add(e) }
		Assertions.assertEquals(list1, list3)
		// Check listIterator().
		val list4 = mutableListOf<Int>()
		list2.listIterator().forEachRemaining { e -> list4.add(e) }
		Assertions.assertEquals(list1, list4)
		// Check indexOf(), lastIndexOf(), and last().
		val i = rnd.nextInt(count)
		assert(list1.indexOf(i) == i)
		assert(list2.lastIndexOf(i) == i)
		assert(list2.last() == count - 1)
	}
}
