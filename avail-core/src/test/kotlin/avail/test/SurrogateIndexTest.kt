/*
 * SurrogateIndexTest.kt
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
package avail.test

import avail.descriptor.tuples.A_String.SurrogateIndexConverter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A test of [SurrogateIndexConverter].  This converts between zero-based
 * indices into Avail strings, where code points can be 0..0x10FFFF, and Java
 * strings, where a surrogate pair is used for each code point over 0xFFFF.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class SurrogateIndexTest
{
	/** Check the string "x". */
	@Test
	fun testX()
	{
		val con = SurrogateIndexConverter("x")
		assertEquals(0, con.availIndexToJavaIndex(0))
		assertEquals(0, con.javaIndexToAvailIndex(0))
	}

	/**
	 * Check a one-character treble clef, which requires surrogate pairs to
	 * appear in a Java string.
	 */
	@Test
	fun testSurrogatePair()
	{
		val string = "𝄞"
		assertEquals(string, "\uD834\uDD1E")
		val con = SurrogateIndexConverter(string)
		assertEquals(0, con.availIndexToJavaIndex(0))
		assertEquals(2, con.availIndexToJavaIndex(1))
		assertEquals(0, con.javaIndexToAvailIndex(0))
		assertEquals(1, con.javaIndexToAvailIndex(2))
	}

	/** Check the string "cat". */
	@Test
	fun testCat()
	{
		val con = SurrogateIndexConverter("cat")
		assertEquals(0, con.availIndexToJavaIndex(0))
		assertEquals(1, con.availIndexToJavaIndex(1))
		assertEquals(2, con.availIndexToJavaIndex(2))
		assertEquals(0, con.javaIndexToAvailIndex(0))
		assertEquals(1, con.javaIndexToAvailIndex(1))
		assertEquals(2, con.javaIndexToAvailIndex(2))
	}

	/** Check the string "𝄞𝄞x𝄞y", which has several non-BMP characters. */
	@Test
	fun testComplex()
	{
		val string = "𝄞𝄞x𝄞y"
		assertEquals(string, "\uD834\uDD1E\uD834\uDD1Ex\uD834\uDD1Ey")
		val con = SurrogateIndexConverter(string)
		assertEquals(0, con.availIndexToJavaIndex(0))
		assertEquals(2, con.availIndexToJavaIndex(1))
		assertEquals(4, con.availIndexToJavaIndex(2))
		assertEquals(5, con.availIndexToJavaIndex(3))
		assertEquals(7, con.availIndexToJavaIndex(4))

		assertEquals(0, con.javaIndexToAvailIndex(0))
		assertEquals(1, con.javaIndexToAvailIndex(2))
		assertEquals(2, con.javaIndexToAvailIndex(4))
		assertEquals(3, con.javaIndexToAvailIndex(5))
		assertEquals(4, con.javaIndexToAvailIndex(7))
	}
}
