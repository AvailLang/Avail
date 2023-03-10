/*
 * RunTreeTest.kt
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

import avail.utility.Mutable
import avail.utility.structures.RunTree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * A test of [RunTree].
 *
 * @author
 *   Mark van Gulik &lt;mark@availlang.org&gt;
 */
class RunTreeTest
{
	/**
	 * Test a series of random actions, doing the equivalent thing to elements
	 * within a duplicate array, verifying that they produce the same answer.
	 */
	@Test
	fun testRandom()
	{
		val maxIndex = 20L
		val maxValue = 5
		for (seed in 1..100)
		{
			val rnd = Random(seed)
			val tree = RunTree<Byte>()
			assertEquals(tree.map { it }, emptyList<Triple<Long, Long, Byte>>())
			val array = arrayOfNulls<Byte>(maxIndex.toInt())
			check(tree, array)
			repeat(1000) {
				val (start, pastEnd) =
					listOf(rnd.nextLong(maxIndex), rnd.nextLong(maxIndex))
						.sorted()
				val changes = mutableMapOf<Byte?, Mutable<Byte?>>()
				if (pastEnd > start)
				{
					tree.edit(start, pastEnd) { old ->
						val new = changes.computeIfAbsent(old) {
							Mutable(
								if (rnd.nextInt(3) == 0) null
								else rnd.nextInt(maxValue).toByte())
						}
						new.value
					}
					// Make the same changes to the array.
					for (i in start.toInt() until pastEnd.toInt())
					{
						array[i] = changes[array[i]]!!.value
					}
				}
				check(tree, array)
			}

		}
	}

	/**
	 * Verify that the tree and the array have the same content, and that the
	 * tree is not degenerate (with uncombined contiguous regions having the
	 * same value).
	 */
	fun check(tree: RunTree<Byte>, array: Array<Byte?>)
	{
		val runsInArray = mutableListOf<Triple<Long, Long, Byte>>()
		var currentRunStart = -1
		var currentRunValue: Byte? = null
		for (i in array.indices)
		{
			if (array[i] != currentRunValue)
			{
				if (currentRunValue != null)
				{
					runsInArray.add(
						Triple(
							currentRunStart.toLong(),
							i.toLong(),
							currentRunValue))
				}
				currentRunValue = array[i]
				currentRunStart = i
			}
		}
		// Final run.
		if (currentRunValue != null)
		{
			runsInArray.add(
				Triple(
					currentRunStart.toLong(),
					array.size.toLong(),
					currentRunValue))
		}
		assert(runsInArray == tree.map { it })
	}
}
