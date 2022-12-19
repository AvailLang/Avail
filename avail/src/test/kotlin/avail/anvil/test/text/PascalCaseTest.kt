/*
 * PascalCaseTest.kt
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

import avail.anvil.text.toPascalCase
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Test text transformation [toPascalCase].
 *
 * @author Richard Arriaga
 */
class PascalCaseTest
{
	@Test
	@DisplayName("Snake Case to Pascal Case")
	fun testSnakeToPascal ()
	{
		val orig = "all_the_horses"
		val expected = "AllTheHorses"
		val transformed = toPascalCase(orig)
		assertEquals(expected, transformed)

		val orig2 = "aLl_tHe_Horses"
		val expected2 = "ALlTheHorses"
		val transformed2 = toPascalCase(orig2)
		assertEquals(expected2, transformed2)
	}

	@Test
	@DisplayName("Kebab Case to Pascal Case")
	fun testKebabToPascal ()
	{
		val orig = "all-the-horses"
		val expected = "AllTheHorses"
		val transformed = toPascalCase(orig)
		assertEquals(expected, transformed)

		val orig2 = "aLl-tHe-Horses"
		val expected2 = "ALlTheHorses" // aLlThEHorses
		val transformed2 = toPascalCase(orig2)
		assertEquals(expected2, transformed2)
	}

	@Test
	@DisplayName("Pascal Case to Pascal Case - No Change")
	fun testPascalToPascal ()
	{
		val orig = "AllTheHorses"
		val transformed = toPascalCase(orig)
		assertEquals(orig, transformed)
	}

	@Test
	@DisplayName("Does not transform to Pascal Case No Spaces")
	fun testNoTransformToPascalNoSpaces ()
	{
		val orig = "Allthehorses"
		val transformed = toPascalCase(orig)
		assertEquals(orig, transformed)
	}

	@Test
	@DisplayName("Does not transform to Pascal Case Spaces")
	fun testNoTransformToPascalSpaces ()
	{
		val orig = "all the horses"
		val transformed = toPascalCase(orig)
		assertEquals("All The Horses", transformed)
	}

	@Test
	@DisplayName("Consecutive Capital Case to Pascal Case")
	fun testMultipleCapitalsToPascal ()
	{
		val orig = "TNTexplosions"
		val expected = "TNtexplosions"
		val transformed = toPascalCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Complex string to Pascal Case")
	fun testComplexMixture ()
	{
		val orig = "TN.T.explosions4_five"
		val expected = "TN.T.Explosions4Five"
		val transformed = toPascalCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Compound string to Pascal Case")
	fun testCompoundToPascalCase ()
	{
		val orig = "moo-meta.GOO-GETA"
		val expected = "MooMeta.GOoGeta"
		val transformed = toPascalCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Mixed Snake & Kebab to Pascal Case")
	fun testMixedSnakeKebab ()
	{
		val orig = "Foo-bar_baz"
		val expected = "FooBarBaz"
		val transformed = toPascalCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("Foo-bar_baz-moMaz to Pascal Case")
	fun testMixedSnakeKebabCapital ()
	{
		val orig = "Foo-bar_baz-moMaz"
		val expected = "FooBarBazMoMaz"
		val transformed = toPascalCase(orig)
		assertEquals(expected, transformed)
	}

	@Test
	@DisplayName("GRoo-bar_baz to Pascal Case")
	fun testMixedCapitalSnakeKebabCapital ()
	{
		val orig = "GRoo-bar_baz"
		val expected = "GRooBarBaz"
		val transformed = toPascalCase(orig)
		assertEquals(expected, transformed)
	}
}
