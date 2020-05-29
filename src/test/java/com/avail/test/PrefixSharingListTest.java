/*
 * PrefixSharingListTest.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.test;

import com.avail.utility.PrefixSharingList;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.avail.utility.PrefixSharingList.append;
import static com.avail.utility.PrefixSharingList.last;
import static com.avail.utility.PrefixSharingList.withoutLast;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the {@link PrefixSharingList}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class PrefixSharingListTest
{
	/** A pseudo-random generator for the tests. */
	private final Random rnd = new Random();

	/**
	 * Create a {@link PrefixSharingList} of the desired size, containing the
	 * numbers from 0 to count-1.
	 *
	 * <p>During assembly, randomly prod the list with queries that cause it to
	 * have a form that may differ on successive calls.</p>
	 *
	 * @param count How many elements to put in the list.
	 * @return A list of the requested size, with integers running from 0 to
	 *         count - 1.
	 */
	private List<Integer> randomlyAssemble (final int count)
	{
		assert count > 0;
		final int flatCount = rnd.nextInt(count);
		// Start with a flat list with flatCount elements.
		List<Integer> result = new ArrayList<>();
		for (int i = 0; i < flatCount; i++)
		{
			result.add(i);
		}
		// Now do non-destructive appends.
		for (int i = flatCount; i < count; i++)
		{
			result = append(result, i);
			if (rnd.nextInt(4) == 0)
			{
				// Try popping and appending.
				result = append(withoutLast(result), i);
			}
			if (rnd.nextInt(4) == 0)
			{
				// Read an early element, forcing construction of the flat list.
				final int zero = result.get(0);
				assert zero == 0;
			}
		}
		return result;
	}


	/**
	 * Test a random assembly process to find corner case bugs.
	 *
	 * @param repetition The current test's {@link RepetitionInfo}.
	 */
	@RepeatedTest(20)
	void testRandomAssembly (final RepetitionInfo repetition)
	{
		final int count = repetition.getCurrentRepetition();
		assert count > 0;

		final List<Integer> list1 = randomlyAssemble(count);
		assert list1.size() == count;
		final List<Integer> list2 = randomlyAssemble(count);
		assert list2.size() == count;

		assertEquals(list1, list2);
		assertEquals(new ArrayList<>(list1), list2);
		assertEquals(list1, new ArrayList<>(list2));
		// Check iterator().
		final List<Integer> list3 = new ArrayList<>();
		list2.iterator().forEachRemaining(list3::add);
		assertEquals(list1, list3);
		// Check listIterator().
		final List<Integer> list4 = new ArrayList<>();
		list2.listIterator().forEachRemaining(list4::add);
		assertEquals(list1, list4);
		// Check indexOf(), lastIndexOf(), and last().
		final int i = rnd.nextInt(count);
		assert list1.indexOf(i) == i;
		assert list2.lastIndexOf(i) == i;
		assert last(list2) == count - 1;
	}
}
