/*
 * L2ValueManifestTest.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.test.optimizer

import avail.AvailRuntime.HookType.BASE_FRAME
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.primitive.general.P_ExtractObjectVariantId
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.values.Frame
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticSlot
import avail.optimizer.values.L2SemanticValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests of how an [L2ValueManifest] merges and narrows the classes of values
 * it knows (doc/Optimization/Level_Two/Value_manifest.md).
 */
class L2ValueManifestTest
{
	/** The [Frame] that the test's semantic values belong to. */
	private val frame = Frame(
		null,
		BASE_FRAME.functionSupplier().code(),
		-1,
		"top",
		"top frame")

	/** A value, `x`. */
	private val x: L2SemanticValue = L2SemanticSlot(frame, 1, 1, "x")

	/** Another value, `y`. */
	private val y: L2SemanticValue = L2SemanticSlot(frame, 2, 2, "y")

	/** The layout variant id of `x`. */
	private val variantOfX = L2SemanticPrimitiveInvocation(
		P_ExtractObjectVariantId, listOf(x))

	/** The layout variant id of `y`. */
	private val variantOfY = L2SemanticPrimitiveInvocation(
		P_ExtractObjectVariantId, listOf(y))

	/**
	 * Answer a manifest that knows `x`, `y`, and the variant ids of each, all
	 * in distinct classes.
	 *
	 * @return
	 *   The new [L2ValueManifest].
	 */
	private fun manifestWithVariants(): L2ValueManifest =
		L2ValueManifest(BySemanticValue).apply {
			agglomerateSynonym(listOf(x), restrictionForType(Types.ANY()))
			agglomerateSynonym(listOf(y), restrictionForType(Types.ANY()))
			agglomerateSynonym(
				listOf(variantOfX), restrictionForType(wholeNumbers))
			agglomerateSynonym(
				listOf(variantOfY), restrictionForType(wholeNumbers))
		}

	/**
	 * Narrowing a value keeps the record of the primitive invocations computed
	 * from it.  Rebuilding a value's state once dropped that record, so a value
	 * that had been narrowed or given a register could no longer narrow its
	 * users, nor make them congruent when it merged.
	 */
	@Test
	fun narrowingKeepsUsers()
	{
		val manifest = manifestWithVariants()
		assertTrue(manifest.stateOrNull(x)!!.usingClasses.isNotEmpty())
		manifest.setRestriction(x, restrictionForType(mostGeneralObjectType))
		assertEquals(
			mostGeneralObjectType,
			manifest.restrictionFor(x).type)
		assertTrue(manifest.stateOrNull(x)!!.usingClasses.isNotEmpty())
	}

	/**
	 * Merging two values merges the invocations of one primitive that read
	 * them (congruence), and the merged class keeps the members of both, even
	 * after the values were narrowed first.
	 */
	@Test
	fun mergingArgumentsMergesCongruentInvocations()
	{
		val manifest = manifestWithVariants()
		manifest.setRestriction(x, restrictionForType(mostGeneralObjectType))
		manifest.setRestriction(y, restrictionForType(mostGeneralObjectType))
		assertNotEquals(
			manifest.semanticValueToSynonym(variantOfX),
			manifest.semanticValueToSynonym(variantOfY))
		manifest.mergeExistingSemanticValues(x, y)
		val members = manifest.stateOrNull(variantOfX)!!.members
		assertTrue(variantOfX in members)
		assertTrue(variantOfY in members)
		assertEquals(
			manifest.semanticValueToSynonym(variantOfX),
			manifest.semanticValueToSynonym(variantOfY))
	}

	/**
	 * Agglomerating two values merges their congruent invocations too, and the
	 * merged class keeps the members of both.  Merging the invocations once
	 * kept the state of only one of them, forwarding the survivor to the class
	 * being merged away.
	 */
	@Test
	fun agglomeratingArgumentsMergesCongruentInvocations()
	{
		val manifest = manifestWithVariants()
		assertNotEquals(
			manifest.semanticValueToSynonym(variantOfX),
			manifest.semanticValueToSynonym(variantOfY))
		manifest.agglomerateSynonym(
			listOf(x, y), restrictionForType(mostGeneralObjectType))
		listOf(variantOfX, variantOfY).forEach { variant ->
			val members = manifest.stateOrNull(variant)!!.members
			assertTrue(variantOfX in members)
			assertTrue(variantOfY in members)
		}
	}

	/**
	 * Merging two invocations never merges their arguments, since a primitive
	 * is not injective: two objects can share a layout variant.
	 */
	@Test
	fun mergingInvocationsDoesNotMergeArguments()
	{
		val manifest = manifestWithVariants()
		manifest.mergeExistingSemanticValues(variantOfX, variantOfY)
		assertEquals(
			manifest.semanticValueToSynonym(variantOfX),
			manifest.semanticValueToSynonym(variantOfY))
		assertNotEquals(
			manifest.semanticValueToSynonym(x),
			manifest.semanticValueToSynonym(y))
	}

	/**
	 * Agglomerating two existing values whose restrictions are disjoint makes
	 * the manifest report an impossible restriction, so the generator can
	 * treat the position as unreachable.
	 */
	@Test
	fun agglomeratingDisjointValuesIsImpossible()
	{
		val manifest = L2ValueManifest(BySemanticValue)
		manifest.agglomerateSynonym(
			listOf(x), restrictionForType(wholeNumbers))
		manifest.agglomerateSynonym(
			listOf(y), restrictionForType(mostGeneralTupleType))
		assertFalse(manifest.hasImpossibleRestriction)
		manifest.agglomerateSynonym(
			listOf(x, y), restrictionForType(Types.ANY()))
		assertTrue(manifest.hasImpossibleRestriction)
		assertTrue(manifest.restrictionFor(x).isImpossible)
	}
}
