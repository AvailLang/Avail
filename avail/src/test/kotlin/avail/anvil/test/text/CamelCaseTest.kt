/*
 * CamelCaseTest.kt
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

import avail.anvil.text.toCamelCase
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Test text transformation [toCamelCase].
 *
 * @author Richard Arriaga
 */
class CamelCaseTest
{
	@Test
	@DisplayName("Snake Case to Camel Case")
	fun testSnakeToCamel ()
	{
		val orig = "all_the_horses"
		val expected = "allTheHorses"
		val transformed = toCamelCase(orig)
		assertEquals(expected, transformed)

		val orig2 = "aLl_tHe_Horses"
		val expected2 = "aLlTheHorses"
		val transformed2 = toCamelCase(orig2)
		assertEquals(expected2, transformed2)
	}

	@Test
	@DisplayName("Kebab Case to Camel Case")
	fun testKebabToCamel ()
	{
		val orig = "all-the-horses"
		val expected = "allTheHorses"
		val transformed = toCamelCase(orig)
		assertEquals(expected, transformed)

		val orig2 = "aLl-tHe-Horses"
		val expected2 = "aLlTheHorses" // aLlThEHorses
		val transformed2 = toCamelCase(orig2)
		assertEquals(expected2, transformed2)
	}

	@Test
	@DisplayName("Camel Case to Camel Case - No Change")
	fun testCamelToCamel ()
	{
		val orig = "allTheHorses"
		val transformed = toCamelCase(orig)
		assertEquals(orig, transformed)

		val orig2 = "AllTheHorses"
		val transformed2 = toCamelCase(orig2)
		assertEquals("allTheHorses", transformed2)
	}

	@Test
	@DisplayName("Does not transform to Camel Case No Spaces")
	fun testNoTransformToCamelNoSpaces ()
	{
		val orig = "allthehorses"
		val transformed = toCamelCase(orig)
		assertEquals(orig, transformed)
	}

	@Test
	@DisplayName("Does not transform to Camel Case Spaces")
	fun testNoTransformToCamelSpaces ()
	{
		val orig = "all the horses"
		val transformed = toCamelCase(orig)
		assertEquals(orig, transformed)
	}

	@Test
	@DisplayName("Does not transform to Camel Case Non-Transformable Delimiters")
	fun testNoTransformToCamelNoTransformableDelimiter ()
	{
		val orig = "all.the/horses"
		val transformed = toCamelCase(orig)
		assertEquals(orig, transformed)
	}

	@Test
	@DisplayName("Consecutive Capital Case to Camel Case")
	fun testMultipleCapitalsToCamel ()
	{
		val orig = "TNTexplosions"
		val expected = "tNtexplosions"
		val transformed = toCamelCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Complex string to Camel Case")
	fun testComplexMixture ()
	{
		val orig = "TN.T.explosions4_five"
		val expected = "tN.t.explosions4Five"
		val transformed = toCamelCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Compound string to Camel Case")
	fun testCompoundToCamelCase ()
	{
		val orig = "moo-meta.GOO-GETA"
		val expected = "mooMeta.gOoGeta"
		val transformed = toCamelCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Mixed Snake & Kebab to Camel Case")
	fun testMixedSnakeKebab ()
	{
		val orig = "Foo-bar_baz"
		val expected = "fooBarBaz"
		val transformed = toCamelCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Foo-bar_baz-moMaz to Camel Case")
	fun testMixedSnakeKebabCapital ()
	{
		val orig = "Foo-bar_baz-moMaz"
		val expected = "fooBarBazMoMaz"
		val transformed = toCamelCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("GRoo-bar_baz to Camel Case")
	fun testMixedCapitalSnakeKebabCapital ()
	{
		val orig = "GRoo-bar_baz"
		val expected = "gRooBarBaz"
		val transformed = toCamelCase(orig)
		assertEquals(expected, transformed)
	}
}
