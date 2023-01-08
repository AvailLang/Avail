/*
 * StringCaseTransformQueueTest.kt
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

package avail.anvil.test.text

import avail.anvil.text.StringCaseTransformQueue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class StringCaseTransformQueueTest
{
	@Test
	@DisplayName("Source string has inner delimiters")
	fun innerDelimiters ()
	{
		val orig = "all.theQueue///horses-morses"
		val t = StringCaseTransformQueue(orig)
		assertEquals(
			listOf("all", "theQueue", "horses-morses"),
			t.transformStringQueue)
		assertEquals(
			listOf(".", "///"),
			t.delimiters)
	}

	@Test
	@DisplayName("Source string has outer delimiters")
	fun outerDelimiters ()
	{
		val orig = "++all.theQueue///horses-morses=="
		val t = StringCaseTransformQueue(orig)
		assertEquals(
			listOf("", "all", "theQueue", "horses-morses"),
			t.transformStringQueue)
		assertEquals(
			listOf("++", ".", "///", "=="),
			t.delimiters)
	}

	@Test
	@DisplayName("Source string has only delimiters")
	fun onlyDelimiters ()
	{
		val orig = "++.///=="
		val t = StringCaseTransformQueue(orig)
		assertEquals(listOf(""), t.transformStringQueue)
		assertEquals(listOf("++.///=="), t.delimiters)
	}

	@Test
	@DisplayName("Source string has no delimiters")
	fun noDelimiters ()
	{
		val orig = "catMan"
		val t = StringCaseTransformQueue(orig)
		assertEquals(listOf("catMan"), t.transformStringQueue)
		assertEquals(emptyList<String>(), t.delimiters)
	}

	@Test
	@DisplayName("Test interleave reconstructs string")
	fun interleaveTest ()
	{
		val orig1 = "all.theQueue///horses-morses"
		assertEquals(orig1, StringCaseTransformQueue(orig1).interleave { it })

		val orig2 = "++all.theQueue///horses-morses=="
		assertEquals(orig2, StringCaseTransformQueue(orig2).interleave { it })

		val orig3 = "++.///=="
		assertEquals(orig3, StringCaseTransformQueue(orig3).interleave { it })

		val orig4 = "catMan"
		assertEquals(orig4, StringCaseTransformQueue(orig4).interleave { it })
	}
}
