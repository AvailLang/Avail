/*
 * FoldableTest.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

import avail.interpreter.primitive.Foldable
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.PrimitiveHolder
import avail.interpreter.primitive.controlflow.P_InvokeWithTuple
import avail.interpreter.primitive.numbers.P_Addition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests of [Foldable], the interface that marks a primitive as foldable and
 * from which its [CanFold] flag is derived.
 */
class FoldableTest
{
	/**
	 * A foldable primitive has the [CanFold] flag, and its
	 * [Foldable.thisPrimitive] is the primitive itself, reached through the
	 * member it inherits from its Java arity class.
	 */
	@Test
	fun foldablePrimitiveIsItselfAndCanFold()
	{
		val foldable: Foldable = P_Addition
		assertSame(P_Addition, foldable.thisPrimitive)
		assertTrue(P_Addition.hasFlag(CanFold))
	}

	/**
	 * A primitive that does not implement [Foldable] lacks the [CanFold] flag.
	 */
	@Test
	fun nonFoldablePrimitiveCannotFold()
	{
		assertFalse((P_InvokeWithTuple as Any) is Foldable)
		assertFalse(P_InvokeWithTuple.hasFlag(CanFold))
	}

	/**
	 * Across every primitive, the [CanFold] flag is set exactly when the
	 * primitive implements [Foldable], and every foldable primitive can be
	 * inlined.  Some primitives must be foldable, or the test proves nothing.
	 */
	@Test
	fun canFoldIsExactlyFoldable()
	{
		val primitives = PrimitiveHolder.holdersByName.values.map {
			it.primitive
		}
		val foldable = primitives.filterIsInstance<Foldable>()
		assertTrue(foldable.isNotEmpty())
		primitives.forEach { primitive ->
			assertEquals(
				primitive is Foldable,
				primitive.hasFlag(CanFold),
				"CanFold disagrees with Foldable for ${primitive.name}")
		}
		foldable.forEach {
			assertTrue(
				it.thisPrimitive.hasFlag(CanInline),
				"${it.thisPrimitive.name} is Foldable without CanInline")
		}
	}
}
