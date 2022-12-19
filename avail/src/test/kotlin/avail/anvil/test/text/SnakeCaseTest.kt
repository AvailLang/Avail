/*
 * SnakeCaseTest.kt
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

import avail.anvil.text.toSnakeCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SnakeCaseTest
{
	@Test
	@DisplayName("Camel Case to Snake Case")
	fun testCamelToSnake ()
	{
		val orig = "allTheHorses"
		val expected = "all_the_horses"
		val transformed = toSnakeCase(orig)
		assertEquals(expected, transformed)

		val orig2 = "aLl_tHe_Horses"
		val expected2 = "a_ll_t_he_horses"
		val transformed2 = toSnakeCase(orig2)
		assertEquals(expected2, transformed2)
	}

	@Test
	@DisplayName("Snake-ish Case to Snake Case")
	fun testAlmostSnakeToSnake ()
	{
		val orig2 = "aLl_tHe_Horses"
		val expected2 = "a_ll_t_he_horses"
		val transformed2 = toSnakeCase(orig2)
		assertEquals(expected2, transformed2)
	}


	@Test
	@DisplayName("Kebab Case to Snake Case")
	fun testKebabToSnake ()
	{
		val orig = "all-the-horses"
		val expected = "all_the_horses"
		val transformed = toSnakeCase(orig)
		assertEquals(expected, transformed)

		val orig2 = "aLl-tHe-Horses"
		val expected2 = "a_ll_t_he_horses"
		val transformed2 = toSnakeCase(orig2)
		assertEquals(expected2, transformed2)
	}

	@Test
	@DisplayName("Snake Case to Snake Case - No Change")
	fun testSnakeToSnake ()
	{
		val orig = "all_the_horses"
		val transformed = toSnakeCase(orig)
		assertEquals(orig, transformed)
	}

	@Test
	@DisplayName("Does not transform to Snake Case No Spaces")
	fun testNoTransformToSnakeNoSpaces ()
	{
		val orig = "allthehorses"
		val transformed = toSnakeCase(orig)
		assertEquals(orig, transformed)
	}

	@Test
	@DisplayName("Does not transform to Snake Case Spaces")
	fun testNoTransformToSnakeSpaces ()
	{
		val orig = "all the horses"
		val transformed = toSnakeCase(orig)
		assertEquals(orig, transformed)
	}

	@Test
	@DisplayName("Does not transform to Snake Case Non-Transformable Delimiters")
	fun testNoTransformToSnakeNoTransformableDelimiter ()
	{
		val orig = "all.the/horses"
		val transformed = toSnakeCase(orig)
		assertEquals(orig, transformed)
	}

	@Test
	@DisplayName("Consecutive Capital Case to Snake Case")
	fun testMultipleCapitalsToSnake ()
	{
		val orig = "TNTexplosions"
		val expected = "t_n_texplosions"
		val transformed = toSnakeCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Complex string to Snake Case")
	fun testComplexMixture ()
	{
		val orig = "TN.T.explosions4Five"
		val expected = "t_n.t.explosions4_five"
		val transformed = toSnakeCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Compound string to Snake Case")
	fun testCompoundtoSnakeCase ()
	{
		val orig = "moo-meta.GOO-Geta"
		val expected = "moo_meta.g_o_o_geta"
		val transformed = toSnakeCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Mixed Snake & Kebab to Snake Case")
	fun testMixedSnakeKebab ()
	{
		val orig = "Foo-bar_baz"
		val expected = "foo_bar_baz"
		val transformed = toSnakeCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Foo-bar_baz-moMaz to Snake Case")
	fun testMixedSnakeKebabCapital ()
	{
		val orig = "Foo-bar_baz-moMaz"
		val expected = "foo_bar_baz_mo_maz"
		val transformed = toSnakeCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("GRoo-bar_baz to Snake Case")
	fun testMixedCapitalSnakeKebabCapital ()
	{
		val orig = "GRoo-bar_baz"
		val expected = "g_roo_bar_baz"
		val transformed = toSnakeCase(orig)
		assertEquals(expected, transformed)
	}
}
