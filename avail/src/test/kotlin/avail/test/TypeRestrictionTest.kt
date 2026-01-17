/*
 * TypeRestrictionTest.kt
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

import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.SetTypeDescriptor.Companion.mostGeneralSetType
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [TypeRestriction].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class TypeRestrictionTest
{
	/**
	 * Test: tuple ∪ tuple = tuple.
	 */
	@Test
	fun testSimpleSelfUnion()
	{
		val t1 = restrictionForType(mostGeneralTupleType, BOXED_FLAG)
		val union = t1.union(t1)
		assertEquals(t1, union)
	}

	/**
	 * Test: tuple ∪ token = nontype (conservatively).
	 */
	@Test
	fun testSimpleDisjointUnion()
	{
		val t1 = restrictionForType(mostGeneralTupleType, BOXED_FLAG)
		val t2 = restrictionForType(Types.TOKEN(), BOXED_FLAG)
		val union = t1.union(t2)
		// The union has to be conservative to fit inside the type lattice.
		val expectedUnion = restrictionForType(Types.NONTYPE(), BOXED_FLAG)
		assertEquals(expectedUnion, union)
	}


	/**
	 * Test: (any - token) ∪ token = any.
	 */
	@Test
	fun testUnionWithExcludedTypes()
	{
		val t1 = restrictionForType(Types.ANY(), BOXED_FLAG)
			.minusType(Types.TOKEN())
		val t2 = restrictionForType(Types.TOKEN(), BOXED_FLAG)
		val union = t1.union(t2)
		val expectedUnion = restrictionForType(Types.ANY(), BOXED_FLAG)
		assertEquals(expectedUnion, union)
	}

	/**
	 * Test a complex regression case that failed in the optimizer, specifically
	 * in Types.avail, at the tuple stringify () method, at the recursive call
	 * to stringify an element.  It produced a 64-way branch on some bits of the
	 * hash to identify ⊥, ∅, month atoms, and weekday atoms, falling back into
	 * a tag test.  In particular, there was a union at a control flow merge
	 * between a TypeRestrictions whose excluded types set included {token}ᵀ,
	 * and another TypeRestriction containing the type {token}ᵀ.
	 *
	 * ```
	 * t1 = restriction:
	 *          t={any}ᵀ
	 *          ex.t:
	 *              {atom}ᵀ,
	 *              {set}ᵀ,
	 *              {java.lang.Object}ᵀ,
	 *              {number}ᵀ,
	 *              {phrase⇒⊤}ᵀ,
	 *              {read ⊤/write ⊥}ᵀ,
	 *              {[…]→⊤}ᵀ,
	 *              {object}ᵀ,
	 *              {map}ᵀ,
	 *              {token}ᵀ,
	 *              {tuple}ᵀ
	 *          flags=box
	 * t2 = restriction(
	 *          t={token}ᵀ,
	 *          flags=⊥+box)
	 * expectedUnion = restriction:
	 *          t={any}ᵀ
	 *          ex.t:
	 *              {atom}ᵀ,
	 *              {set}ᵀ,
	 *              {java.lang.Object}ᵀ,
	 *              {number}ᵀ,
	 *              {phrase⇒⊤}ᵀ,
	 *              {read ⊤/write ⊥}ᵀ,
	 *              {[…]→⊤}ᵀ,
	 *              {object}ᵀ,
	 *              {map}ᵀ,
	 *              // {token}ᵀ, -- no longer excluded
	 *              {tuple}ᵀ
	 *          flags=⊥+box
	 *```
	 */
	@Test
	fun testComplexRegression()
	{
		var t1 = boxedRestrictionForType(instanceMeta(Types.ANY()))
			.minusType(instanceMeta(Types.ATOM()))
			.minusType(instanceMeta(mostGeneralSetType()))
			.minusType(instanceMeta(Types.TOKEN()))
			// Other exclusions elided for brevity.
			.withCanBeBottom(false)
		var t2 = boxedRestrictionForType(instanceMeta(Types.TOKEN()))
		val union = t1.union(t2)
		val expectedUnion = boxedRestrictionForType(instanceMeta(Types.ANY()))
			.minusType(instanceMeta(Types.ATOM()))
			.minusType(instanceMeta(mostGeneralSetType()))
		assertEquals(expectedUnion, union)
	}

	/**
	 * Compute the union of the type restrictions for `[-5..-1]` and `[1..5]`,
	 * and ensure that the resulting restriction does not contain zero.
	 */
	@Test
	fun testAccurateIntegerRangeUnion()
	{
		var t1 = boxedRestrictionForType(inclusive(-5, -1))
		var t2 = boxedRestrictionForType(inclusive(1, 5))
		var union = t1.union(t2)
		assert(!union.containsValue(zero))
	}

	/**
	 * Compute the union of the type restrictions for `[0..∞)` and `[∞..∞]`,
	 * and ensure that the resulting restriction's type is `[0..∞]`.
	 */
	@Test
	fun testWholeNumbersUnionInfinity()
	{
		var t1 = boxedRestrictionForType(wholeNumbers)
		var t2 = boxedRestrictionForType(
			inclusive(positiveInfinity, positiveInfinity))
		var union = t1.union(t2)
		assertEquals(union.type, inclusive(zero, positiveInfinity))
		assertEquals(union.type, t2.union(t1).type)

		// Also verify that it works if the second restriction is to the
		// constant ∞, not the technically broader [∞..∞].
		var t2Constant = boxedRestrictionForConstant(positiveInfinity)
		var union2 = t1.union(t2Constant)
		assertEquals(union.type, inclusive(zero, positiveInfinity))
		assertEquals(union2.type, t2Constant.union(t1).type)
	}

	/**
	 * Compute the union of the type restrictions for `(-∞..0]` and `[-∞..-∞]`,
	 * and ensure that the resulting restriction's type is `[-∞..0]`.
	 */
	@Test
	fun testNegativeWholeNumbersUnionNegativeInfinity()
	{
		var t1 = boxedRestrictionForType(
			integerRangeType(negativeInfinity, false, zero, true))
		var t2 = boxedRestrictionForType(
			inclusive(negativeInfinity, negativeInfinity))
		var union = t1.union(t2)
		assertEquals(union.type, inclusive(negativeInfinity, zero))
		assertEquals(union.type, t2.union(t1).type)

		// Also verify that it works if the second restriction is to the
		// constant -∞, not the technically broader [-∞..-∞].
		var t2Constant = boxedRestrictionForConstant(negativeInfinity)
		var union2 = t1.union(t2Constant)
		assertEquals(union.type, inclusive(negativeInfinity, zero))
		assertEquals(union2.type, t2Constant.union(t1).type)
	}
}
