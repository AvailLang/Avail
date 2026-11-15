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
import avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instanceTag
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TypeTag
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operation.tuples.L2_TUPLE_SIZE
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.RegisterKind
import avail.interpreter.primitive.general.P_ExtractObjectVariantId
import avail.interpreter.primitive.general.P_ExtractTagOrdinal
import avail.interpreter.primitive.numbers.P_Addition
import avail.interpreter.primitive.tuples.P_TupleSize
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.values.Frame
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticSlot
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.constant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
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

	/** The [TypeTag] ordinal of `x`. */
	private val tagOfX = L2SemanticPrimitiveInvocation(
		P_ExtractTagOrdinal, listOf(x))

	/** The [TypeTag] ordinal of `y`. */
	private val tagOfY = L2SemanticPrimitiveInvocation(
		P_ExtractTagOrdinal, listOf(y))

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

	/**
	 * A primitive invocation keeps the value it reads known, even when nothing
	 * else mentions that value: binding `Tag(x)` alone introduces `x`, with no
	 * definition, and records `Tag(x)` as one of its users.  That is what lets
	 * a later discovery that `x = y` make `Tag(x)` and `Tag(y)` synonyms.
	 */
	@Test
	fun invocationKeepsItsArgumentKnown()
	{
		val manifest = L2ValueManifest(BySemanticValue)
		assertFalse(manifest.hasSemanticValue(x))
		assertFalse(manifest.hasSemanticValue(y))
		val tagRange = restrictionForType(Types.ANY().instanceTag.tagRangeType)
		manifest.agglomerateSynonym(listOf(tagOfX), tagRange)
		manifest.agglomerateSynonym(listOf(tagOfY), tagRange)
		listOf(x, y).forEach { argument ->
			assertTrue(manifest.hasSemanticValue(argument))
			assertTrue(
				manifest.stateOrNull(argument)!!.usingClasses.isNotEmpty())
		}
		assertNotEquals(
			manifest.semanticValueToSynonym(tagOfX),
			manifest.semanticValueToSynonym(tagOfY))
		manifest.agglomerateSynonym(
			listOf(x, y), restrictionForType(Types.ANY()))
		assertEquals(
			manifest.semanticValueToSynonym(tagOfX),
			manifest.semanticValueToSynonym(tagOfY))
	}

	/**
	 * A name that joins a class without being written is live wherever the
	 * class is, and is held by the class's registers, even though no write
	 * names it (doc/Optimization/Level_Two/Value_manifest.md §Data model.Known
	 * and live).
	 */
	@Test
	fun joiningALiveClassMakesANameLive()
	{
		val register = L2BoxedRegister(1)
		val manifest = L2ValueManifest(BySemanticValue)
		manifest.agglomerateSynonym(listOf(x), restrictionForType(Types.ANY()))
		manifest.updateDefinitions(x, BOXED_KIND) { plus(register) }
		assertTrue(manifest.hasLiveSemanticValue(x, BOXED_KIND))
		assertFalse(manifest.hasSemanticValue(y))
		manifest.agglomerateSynonym(
			listOf(x, y), restrictionForType(Types.ANY()))
		assertTrue(manifest.hasLiveSemanticValue(y, BOXED_KIND))
		assertTrue(manifest.hasLiveSemanticValueInAnyKind(y))
		assertEquals(listOf(register), manifest.getDefinitions(y, BOXED_KIND))
		assertEquals(register, manifest.getDefinitionOrNull(y, BOXED_KIND))
		assertEquals(y, manifest.equivalentLiveSemanticValue(y, BOXED_KIND))
	}

	/**
	 * The search for a live equivalent finds a congruent invocation held in a
	 * register, through its arguments' synonymy, and finds nothing when the
	 * congruent invocation is known but held in no register.
	 */
	@Test
	fun equivalentLiveSemanticValueRequiresARegister()
	{
		val manifest = L2ValueManifest(BySemanticValue)
		manifest.agglomerateSynonym(
			listOf(x, y), restrictionForType(Types.ANY()))
		manifest.agglomerateSynonym(
			listOf(tagOfX),
			restrictionForType(Types.ANY().instanceTag.tagRangeType))
		// The probe, Tag(y), is not itself in the manifest.
		assertFalse(manifest.hasSemanticValue(tagOfY))
		assertEquals(tagOfX, manifest.equivalentSemanticValue(tagOfY))
		assertEquals(
			null, manifest.equivalentLiveSemanticValue(tagOfY, BOXED_KIND))
		manifest.updateDefinitions(tagOfX, BOXED_KIND) {
			plus(L2BoxedRegister(1))
		}
		assertEquals(
			tagOfX, manifest.equivalentLiveSemanticValue(tagOfY, BOXED_KIND))
	}

	/**
	 * Answer a manifest that knows the given values, each in a class of its
	 * own with the given type, and holds each of them in a register of the
	 * given kind when one is supplied.
	 *
	 * @param values
	 *   Triples of a value, its type, and the [RegisterKind] of the register
	 *   that holds it, or `null` for none.
	 * @return
	 *   The new [L2ValueManifest].
	 */
	private fun manifestHolding(
		vararg values: Triple<L2SemanticValue, A_Type, RegisterKind<*>?>
	): L2ValueManifest
	{
		val manifest = L2ValueManifest(BySemanticValue)
		values.forEachIndexed { index, (value, type, kind) ->
			manifest.agglomerateSynonym(listOf(value), restrictionForType(type))
			when (kind)
			{
				null -> Unit
				BOXED_KIND -> manifest.updateDefinitions(value, BOXED_KIND) {
					plus(L2BoxedRegister(index))
				}
				INTEGER_KIND ->
					manifest.updateDefinitions(value, INTEGER_KIND) {
						plus(L2IntRegister(index))
					}
				FLOAT_KIND -> manifest.updateDefinitions(value, FLOAT_KIND) {
					plus(L2FloatRegister(index))
				}
			}
		}
		return manifest
	}

	/**
	 * A value held in a register is producible, and so is every other member
	 * of its synonym.
	 */
	@Test
	fun liveValueIsProducible()
	{
		val manifest = manifestHolding(Triple(x, Types.ANY(), BOXED_KIND))
		manifest.agglomerateSynonym(
			listOf(x, y), restrictionForType(Types.ANY()))
		assertTrue(manifest.hasLiveSemanticValue(y, BOXED_KIND))
		assertTrue(manifest.isProducible(x))
		assertTrue(manifest.isProducible(y))
	}

	/**
	 * A value held only in an `int` or `float` register is producible, since
	 * boxing it cannot fail.
	 */
	@Test
	fun valueHeldUnboxedIsProducible()
	{
		val manifest = manifestHolding(
			Triple(x, i32, INTEGER_KIND),
			Triple(y, Types.DOUBLE(), FLOAT_KIND))
		assertFalse(manifest.hasLiveSemanticValue(x, BOXED_KIND))
		assertFalse(manifest.hasLiveSemanticValue(y, BOXED_KIND))
		assertTrue(manifest.isProducible(x))
		assertTrue(manifest.isProducible(y))
	}

	/**
	 * A value the manifest knows, with nothing to produce it, is not
	 * producible: knowing a value is not the same as being able to produce it.
	 */
	@Test
	fun knownValueWithNothingToProduceItIsNotProducible()
	{
		val manifest = manifestHolding(Triple(x, Types.ANY(), null))
		assertTrue(manifest.hasSemanticValue(x))
		assertFalse(manifest.isProducible(x))
	}

	/**
	 * A semantic constant is producible, even when the manifest does not know
	 * it.
	 */
	@Test
	fun semanticConstantIsProducible()
	{
		val manifest = L2ValueManifest(BySemanticValue)
		val constants = listOf(
			constant(fromInt(5)),
			constant(stringFrom("five")),
			constant(fromDouble(0.5)))
		constants.forEach { value ->
			assertFalse(manifest.hasSemanticValue(value))
			assertTrue(manifest.isProducible(value))
		}
	}

	/**
	 * A value restricted to a single value is producible as that constant,
	 * though no register holds it.
	 */
	@Test
	fun valueRestrictedToAConstantIsProducible()
	{
		val manifest =
			manifestHolding(Triple(x, instanceType(fromInt(5)), null))
		assertFalse(manifest.hasLiveSemanticValueInAnyKind(x))
		assertTrue(manifest.isProducible(x))
	}

	/**
	 * A value with a postponed instruction of any kind is producible, by
	 * emitting the instruction.
	 */
	@Test
	fun postponedValueIsProducible()
	{
		val manifest = manifestHolding(
			Triple(x, mostGeneralTupleType, BOXED_KIND),
			Triple(y, i32, null))
		manifest.recordPostponedInstruction(
			y,
			L2_TUPLE_SIZE(
				L2ReadBoxedOperand(x),
				L2WriteIntOperand(emptySet(), restrictionForType(i32))))
		assertFalse(manifest.hasLiveSemanticValueInAnyKind(y))
		assertTrue(manifest.postponedInstructionFor(y, INTEGER_KIND) !== null)
		assertTrue(manifest.isProducible(y))
	}

	/**
	 * A primitive invocation is producible, even when the manifest does not
	 * know it, exactly when its argument is.
	 */
	@Test
	fun invocationIsProducibleExactlyWhenItsArgumentIs()
	{
		val sizeOfX = L2SemanticPrimitiveInvocation(P_TupleSize, listOf(x))
		val live = manifestHolding(
			Triple(x, mostGeneralTupleType, BOXED_KIND))
		assertFalse(live.hasSemanticValue(sizeOfX))
		assertTrue(live.isProducible(sizeOfX))
		val known = manifestHolding(Triple(x, mostGeneralTupleType, null))
		assertFalse(known.isProducible(sizeOfX))
	}

	/**
	 * Producibility is recursive: nested invocations are producible when the
	 * values at their leaves are, and an argument appearing several times is
	 * no obstacle.
	 */
	@Test
	fun nestedInvocationIsProducible()
	{
		fun sum(a: L2SemanticValue, b: L2SemanticValue) =
			L2SemanticPrimitiveInvocation(P_Addition, listOf(a, b))
		val sumOfX = sum(x, x)
		val nested = sum(sum(sumOfX, sumOfX), sum(sumOfX, sumOfX))
		val live = manifestHolding(Triple(x, i32, BOXED_KIND))
		assertFalse(live.hasSemanticValue(nested))
		assertTrue(live.isProducible(nested))
		val known = manifestHolding(Triple(x, i32, null))
		assertFalse(known.isProducible(nested))
	}

	/**
	 * A value whose synonym holds a producible invocation is producible, though
	 * it is not an invocation itself: every member of a synonym names the same
	 * value.
	 */
	@Test
	fun valueSynonymousWithAProducibleInvocationIsProducible()
	{
		val sizeOfX = L2SemanticPrimitiveInvocation(P_TupleSize, listOf(x))
		val manifest = manifestHolding(
			Triple(x, mostGeneralTupleType, BOXED_KIND))
		manifest.agglomerateSynonym(
			listOf(y, sizeOfX), restrictionForType(wholeNumbers))
		assertFalse(manifest.hasLiveSemanticValueInAnyKind(y))
		assertTrue(manifest.isProducible(y))
		assertEquals(sizeOfX, manifest.producibleInvocationFor(y))
	}

	/**
	 * An invocation that reads its own synonym, as in `{x, x + 0}`, does not
	 * make the synonym producible, and checking it terminates.
	 */
	@Test
	fun invocationReadingItsOwnSynonymIsNotProducible()
	{
		val xPlusZero = L2SemanticPrimitiveInvocation(
			P_Addition, listOf(x, constant(fromInt(0))))
		val manifest = manifestHolding(Triple(x, i32, null))
		manifest.agglomerateSynonym(
			listOf(x, xPlusZero), restrictionForType(i32))
		assertTrue(xPlusZero in manifest.stateOrNull(x)!!.members)
		assertFalse(manifest.isProducible(x))
		assertFalse(manifest.isProducible(xPlusZero))
	}


	/**
	 * A value held in an `int` register whose restriction does not lie within
	 * `i64` is illegal, since unboxing is not checked at run time, and the
	 * manifest's consistency check rejects it.
	 */
	@Test
	fun intRegisterOutsideI64IsIllegal()
	{
		val manifest = manifestHolding(Triple(x, integers, INTEGER_KIND))
		assertTrue(manifest.hasLiveSemanticValue(x, INTEGER_KIND))
		val wasChecking = L2ValueManifest.deepManifestDebugCheck
		L2ValueManifest.deepManifestDebugCheck = true
		try
		{
			assertThrows(AssertionError::class.java) { manifest.check() }
		}
		finally
		{
			L2ValueManifest.deepManifestDebugCheck = wasChecking
		}
	}
}
