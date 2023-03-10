/*
 * KebabCaseTest.kt
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

import avail.anvil.text.toKebabCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class KebabCaseTest
{
	@Test
	@DisplayName("Camel Case to Kebab Case")
	fun testCamelToKebab ()
	{
		val orig = "allTheHorses"
		val expected = "all-the-horses"
		val transformed = toKebabCase(orig)
		assertEquals(expected, transformed)

		val orig2 = "aLl_tHe_Horses"
		val expected2 = "a-ll-t-he-horses"
		val transformed2 = toKebabCase(orig2)
		assertEquals(expected2, transformed2)
	}

	@Test
	@DisplayName("Kebab-ish Case to Kebab Case")
	fun testAlmostKebabToKebab ()
	{
		val orig2 = "aLl_tHe_Horses"
		val expected2 = "a-ll-t-he-horses"
		val transformed2 = toKebabCase(orig2)
		assertEquals(expected2, transformed2)
	}


	@Test
	@DisplayName("Kebab Case to Kebab Case")
	fun testKebabToKebab ()
	{
		val orig = "all-the-horses"
		val expected = "all-the-horses"
		val transformed = toKebabCase(orig)
		assertEquals(expected, transformed)

		val orig2 = "aLl-tHe-Horses"
		val expected2 = "a-ll-t-he-horses"
		val transformed2 = toKebabCase(orig2)
		assertEquals(expected2, transformed2)
	}

	@Test
	@DisplayName("Does not transform to Kebab Case No Spaces")
	fun testNoTransformToKebabNoSpaces ()
	{
		val orig = "allthehorses"
		val transformed = toKebabCase(orig)
		assertEquals(orig, transformed)
	}

	@Test
	@DisplayName("Does not transform to Kebab Case Spaces")
	fun testNoTransformToKebabSpaces ()
	{
		val orig = "all the horses"
		val transformed = toKebabCase(orig)
		assertEquals(orig, transformed)
	}

	@Test
	@DisplayName("Does not transform to Kebab Case Non-Transformable Delimiters")
	fun testNoTransformToKebabNoTransformableDelimiter ()
	{
		val orig = "all.the/horses"
		val transformed = toKebabCase(orig)
		assertEquals(orig, transformed)
	}

	@Test
	@DisplayName("Consecutive Capital Case to Kebab Case")
	fun testMultipleCapitalsToKebab ()
	{
		val orig = "TNTexplosions"
		val expected = "t-n-texplosions"
		val transformed = toKebabCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Complex string to Kebab Case")
	fun testComplexMixture ()
	{
		val orig = "TN.T.explosions4Five"
		val expected = "t-n.t.explosions4-five"
		val transformed = toKebabCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Compound string to Kebab Case")
	fun testCompoundtoKebabCase ()
	{
		val orig = "moo-meta.GOO-Geta"
		val expected = "moo-meta.g-o-o-geta"
		val transformed = toKebabCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Mixed Kebab & Kebab to Kebab Case")
	fun testMixedKebabKebab ()
	{
		val orig = "Foo-bar_baz"
		val expected = "foo-bar-baz"
		val transformed = toKebabCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Foo-bar_baz-moMaz to Kebab Case")
	fun testMixedKebabKebabCapital ()
	{
		val orig = "Foo-bar_baz-moMaz"
		val expected = "foo-bar-baz-mo-maz"
		val transformed = toKebabCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("GRoo-bar_baz to Kebab Case")
	fun testMixedCapitalKebabKebabCapital ()
	{
		val orig = "GRoo-bar_baz"
		val expected = "g-roo-bar-baz"
		val transformed = toKebabCase(orig)
		assertEquals(expected, transformed)
	}
}
