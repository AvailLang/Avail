/*
 * GuaranteedTypeTest.kt
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

package avail.test.optimizer

import avail.AvailThread
import avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instanceCount
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i64
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive
import avail.interpreter.primitive.doubles.P_DoubleToLongBits
import avail.interpreter.primitive.functions.P_ParamsType
import avail.interpreter.primitive.integers.P_BitShiftWithTruncation
import avail.interpreter.primitive.numbers.P_Addition
import avail.interpreter.primitive.numbers.P_Division
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/**
 * Tests of [Primitive.guaranteedType], which narrows a foldable primitive's
 * guaranteed result type to the enumeration of its results when every
 * argument type is a small enumeration.
 *
 * Folding runs the primitive, which needs the current [Interpreter], so each
 * test that expects narrowing runs its computation on an [AvailThread].
 */
class GuaranteedTypeTest
{
	/** The helper that supplies the runtime whose threads fold primitives. */
	private lateinit var helper: OptimizerTestHelper

	/**
	 * Create the runtime for a test.
	 *
	 * @param testInfo
	 *   The test about to run, which names the helper.
	 */
	@BeforeEach
	fun setUp(testInfo: TestInfo)
	{
		helper = OptimizerTestHelper(testInfo.displayName)
	}

	/** Destroy the runtime after a test. */
	@AfterEach
	fun tearDown()
	{
		helper.runtime.destroy()
	}

	/**
	 * Answer the result of running [body] on one of the runtime's
	 * [AvailThread]s, rethrowing whatever it throws.
	 *
	 * @param body
	 *   The computation to run.
	 * @return
	 *   The computation's result.
	 */
	private fun <T> onAvailThread(body: () -> T): T
	{
		val future = CompletableFuture<T>()
		helper.runtime.execute(0) {
			try
			{
				future.complete(body())
			}
			catch (e: Throwable)
			{
				future.completeExceptionally(e)
			}
		}
		return try
		{
			future.get()
		}
		catch (e: ExecutionException)
		{
			throw e.cause ?: e
		}
	}

	/**
	 * Answer the enumeration of the given [Int]s.
	 *
	 * @param values
	 *   The integers.
	 * @return
	 *   Their enumeration type.
	 */
	private fun intEnumeration(values: Iterable<Int>): A_Type =
		enumerationWith(setFromCollection(values.map(::fromInt)))

	/**
	 * Reinterpreting an enumeration of doubles as their bits is guaranteed to
	 * answer the enumeration of those bit patterns, not the whole [i64] range
	 * that the primitive alone promises.
	 */
	@Test
	fun foldableResultNarrowsToEnumeration()
	{
		val doubles = enumerationWith(set(fromDouble(1.0), fromDouble(-0.5)))
		val guaranteed = onAvailThread {
			P_DoubleToLongBits.guaranteedType(null, listOf(doubles))
		}
		assertEquals(
			enumerationWith(
				set(
					fromLong(1.0.toRawBits()),
					fromLong((-0.5).toRawBits()))),
			guaranteed)
	}

	/**
	 * Without a current [Interpreter], nothing can be folded, so the
	 * guarantee is the primitive's own, unnarrowed.
	 */
	@Test
	fun noInterpreterMeansNoNarrowing()
	{
		assertNull(Interpreter.currentOrNull())
		val doubles = enumerationWith(set(fromDouble(1.0), fromDouble(-0.5)))
		assertEquals(
			i64,
			P_DoubleToLongBits.guaranteedType(null, listOf(doubles)))
	}

	/**
	 * A combination on which a fallible primitive fails contributes nothing,
	 * since the guarantee describes only successful calls: dividing 6 by 0,
	 * 2, or 4 is guaranteed to answer 3 or 1, and not the 2 that the range of
	 * quotients would include.
	 */
	@Test
	fun failingCombinationsAreExcluded()
	{
		val guaranteed = onAvailThread {
			P_Division.guaranteedType(
				null,
				listOf(
					intEnumeration(listOf(6)),
					intEnumeration(listOf(0, 2, 4))))
		}
		assertEquals(intEnumeration(listOf(1, 3)), guaranteed)
	}

	/**
	 * A metatype is not narrowed on, since metatypes are covariant: the
	 * metatype of `[i64]→⊤` also admits `[any]→⊤`, whose parameters `<any>`
	 * are not among the parameters of `[i64]→⊤`.
	 */
	@Test
	fun metatypesAreNotNarrowed()
	{
		val functionMeta = instanceMeta(functionType(tuple(i64), TOP()))
		val wider = functionType(tuple(ANY()), TOP())
		assertTrue(functionMeta.isEnumeration && functionMeta.isInstanceMeta)
		assertTrue(wider.isInstanceOf(functionMeta))
		val guaranteed = onAvailThread {
			P_ParamsType.guaranteedType(null, listOf(functionMeta))
		}
		assertTrue(tupleTypeForTypes(ANY()).isInstanceOf(guaranteed))
	}

	/**
	 * Narrowing happens only up to the limit of 256 combinations: 16 × 16
	 * distinct sums narrow to the enumeration of all 256, while 16 × 17 sums
	 * are left to the primitive's own, wider answer.
	 */
	@Test
	fun narrowingStopsAtTheCombinationLimit()
	{
		val units = intEnumeration(0 .. 15)
		val sixteens = intEnumeration((0 .. 15).map { it * 16 })
		val moreSixteens = intEnumeration((0 .. 16).map { it * 16 })
		assertTrue(units.instanceCount.equals(fromInt(16)))
		assertTrue(sixteens.instanceCount.equals(fromInt(16)))
		assertTrue(moreSixteens.instanceCount.equals(fromInt(17)))
		val (atLimit, aboveLimit) = onAvailThread {
			P_Addition.guaranteedType(null, listOf(units, sixteens)) to
				P_Addition.guaranteedType(null, listOf(units, moreSixteens))
		}
		assertEquals(intEnumeration(0 .. 255), atLimit)
		assertFalse(aboveLimit.isEnumeration)
	}

	/**
	 * A three-argument primitive narrows the same way: shifting 1 or 3 left
	 * by 0 or 4 bits and keeping the low 3 bits is guaranteed to answer 0, 1,
	 * or 3.
	 */
	@Test
	fun threeArgumentFoldNarrows()
	{
		val guaranteed = onAvailThread {
			P_BitShiftWithTruncation.guaranteedType(
				null,
				listOf(
					intEnumeration(listOf(1, 3)),
					intEnumeration(listOf(0, 4)),
					intEnumeration(listOf(3))))
		}
		assertEquals(intEnumeration(listOf(0, 1, 3)), guaranteed)
	}
}
