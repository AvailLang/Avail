/*
 * L2SplitCondition.kt
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
package avail.optimizer

import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.AvailObject.Companion.combine3
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.IMMUTABLE_FLAG
import avail.interpreter.levelTwo.operation.L2_BOX_FLOAT
import avail.interpreter.levelTwo.operation.L2_BOX_INT
import avail.interpreter.levelTwo.operation.L2_EXTRACT_OBJECT_TYPE_VARIANT_ID
import avail.interpreter.levelTwo.operation.L2_EXTRACT_OBJECT_VARIANT_ID
import avail.interpreter.levelTwo.operation.L2_EXTRACT_TAG_ORDINAL
import avail.interpreter.levelTwo.operation.L2_HASH
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_UNBOX_FLOAT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_UNBOX_INT
import avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.operation.L2_UNBOX_FLOAT
import avail.interpreter.levelTwo.operation.L2_UNBOX_INT
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticConstant
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue

/**
 * An [L2SplitCondition] is a predicate on an [L2ValueManifest] which would be
 * profitable to sustain through portions of the [L2ControlFlowGraph] by
 * duplication of some of the vertices.
 *
 * Sets of these conditions are used by [L2Optimizer.doCodeSplitting] to control
 * how an [L2Regenerator] is to avoid prematurely merging control flow and
 * destroying actionable information.
 */
@Suppress("EqualsOrHashCode")
sealed class L2SplitCondition
{
	/**
	 * Answer whether the condition is guaranteed to hold for values constrained
	 * by the given [L2ValueManifest].
	 *
	 * @param manifest
	 *   The current manifest used to check if the condition currently holds.
	 */
	abstract fun holdsFor(manifest: L2ValueManifest): Boolean

	abstract override fun equals(other: Any?): Boolean

	/** The pre-computed hash. */
	abstract val hash: Int

	final override fun hashCode(): Int = hash

	/** Answer whether this would hold whenever the argument would hold. */
	abstract fun impliedBy(otherCondition: L2SplitCondition): Boolean

	/**
	 * A condition that holds if some register backs at least one of the given
	 * [L2SemanticValue]s.
	 */
	private class L2ExistsCondition constructor (
		private val semanticValues: Set<L2SemanticValue<*>>
	): L2SplitCondition()
	{
		override fun equals(other: Any?): Boolean =
			other is L2ExistsCondition &&
				other.semanticValues == semanticValues

		override val hash = semanticValues.hashCode() + 0x7F38A734

		override fun holdsFor(manifest: L2ValueManifest): Boolean =
			semanticValues.any { manifest.hasSemanticValue(it) }

		override fun toString(): String =
			"Exists: ${semanticValues.sorted()}}"

		override fun impliedBy(otherCondition: L2SplitCondition): Boolean =
			otherCondition is L2ExistsCondition &&
				semanticValues.containsAll(otherCondition.semanticValues)
	}

	/**
	 * A condition that holds if some register (an [L2IntRegister]) holds the
	 * unboxed [Int] form of some value.
	 */
	private class L2IsUnboxedIntCondition constructor (
		private val semanticValues: Set<L2SemanticUnboxedInt>
	) : L2SplitCondition()
	{
		override fun equals(other: Any?): Boolean =
			other is L2IsUnboxedIntCondition &&
				other.semanticValues == semanticValues

		override val hash = semanticValues.hashCode() xor 0x4AB463DE

		override fun holdsFor(manifest: L2ValueManifest): Boolean =
			semanticValues.any { manifest.hasSemanticValue(it) }

		override fun toString(): String =
			"Unboxed int: ${semanticValues.sorted()}}"

		override fun impliedBy(otherCondition: L2SplitCondition): Boolean =
			otherCondition is L2IsUnboxedIntCondition &&
				semanticValues.containsAll(otherCondition.semanticValues)
	}

	/**
	 * A condition that holds if a register having one of the given
	 * [semanticValues] is guaranteed to satisfy the provided [TypeRestriction].
	 */
	private class L2MeetsRestrictionCondition constructor (
		private val semanticValues: Set<L2SemanticValue<*>>,
		requiredRestrictionRaw: TypeRestriction
	) : L2SplitCondition()
	{
		/**
		 * A restriction on (any of) the [semanticValues], that would be
		 * profitable to know is true somewhere, and which might lead to
		 * duplication of a subgraph instead of losing this information in a
		 * control flow merge upstream.
		 *
		 * We strip the immutability flag here, because it would impede some
		 * splitting situations, and we'll insert suitable [L2_MAKE_IMMUTABLE]
		 * instructions only where they're actually needed in a later pass.
		 */
		val requiredRestriction =
			requiredRestrictionRaw.withoutFlag(IMMUTABLE_FLAG)

		override fun equals(other: Any?): Boolean =
			other is L2MeetsRestrictionCondition &&
				other.semanticValues == semanticValues &&
				other.requiredRestriction == requiredRestriction

		override val hash = combine3(
			semanticValues.hashCode(),
			requiredRestriction.hashCode(),
			0x23AD2910)

		override fun holdsFor(manifest: L2ValueManifest) =
			semanticValues.any {
				manifest.hasSemanticValue(it) &&
					manifest.restrictionFor(it)
						.isStrongerThan(requiredRestriction)
			}

		override fun toString(): String =
			"Restrict: $requiredRestriction for ${semanticValues.sorted()}"


		override fun impliedBy(otherCondition: L2SplitCondition): Boolean =
			otherCondition is L2MeetsRestrictionCondition &&
				otherCondition.requiredRestriction
					.isStrongerThan(requiredRestriction) &&
				semanticValues.containsAll(otherCondition.semanticValues)
	}

	/**
	 * A condition that is used only to ensure entry point blocks don't end up
	 * being the target of multiple reification paths.  Instead, these fake
	 * conditions are explicitly created when such a situation is detected, to
	 * allow multiple versions of the target (entry point) block to exist, one
	 * per incoming edge.
	 */
	private class L2FakeCondition constructor (
		val debugId: Int
	) : L2SplitCondition()
	{
		override fun equals(other: Any?): Boolean =
			other is L2FakeCondition &&
				other.debugId == debugId

		override val hash = combine2(debugId, 0x0941039D)

		override fun holdsFor(manifest: L2ValueManifest) = false

		override fun toString(): String = "Forced split #$debugId"

		override fun impliedBy(otherCondition: L2SplitCondition): Boolean =
			this == otherCondition
	}

	companion object
	{
		/**
		 * Computes all ancestors registers of the given registers, following
		 * phis, moves, boxes, and unboxes.
		 *
		 * @param startingRegisters
		 *   The registers from which to search for ancestor registers.
		 * @return
		 *   The set of ancestor [L2Register]s of the given registers.
		 */
		private fun ancestorRegistersOf(
			startingRegisters: Iterable<L2Register<*>>
		): Set<L2Register<*>>
		{
			val allRegisters = mutableSetOf<L2Register<*>>()
			val moreRegisters = startingRegisters.toMutableSet()
			while (true)
			{
				moreRegisters.removeAll(allRegisters)
				if (moreRegisters.isEmpty()) break
				val moreRegistersCopy = moreRegisters.toList()
				allRegisters.addAll(moreRegisters)
				moreRegisters.clear()
				moreRegistersCopy.forEach { reg ->
					reg.definitions().forEach { defWrite ->
						val def = defWrite.instruction
						val readOperands = when
						{
							def is L2_PHI<*> ||
							def is L2_MOVE<*> ||
							def is L2_BOX_INT ||
							def is L2_BOX_FLOAT ||
							def is L2_UNBOX_INT ||
							def is L2_UNBOX_FLOAT ||
							def is L2_JUMP_IF_UNBOX_INT ||
							def is L2_JUMP_IF_UNBOX_FLOAT ||
							def is L2_HASH ||
							def is L2_EXTRACT_TAG_ORDINAL ||
							def is L2_EXTRACT_OBJECT_VARIANT_ID ||
							def is L2_EXTRACT_OBJECT_TYPE_VARIANT_ID
								-> def.readOperands
							else -> emptyList()
						}
						readOperands.mapTo(moreRegisters) { it.register() }
					}
				}
			}
			return allRegisters
		}

		/**
		 * Computes all ancestors of the given registers, following phis, moves,
		 * boxes, and unboxes.
		 *
		 * @param startingRegisters
		 *   The registers from which to search for ancestors.
		 * @return
		 *   The set of ancestor [L2SemanticValue]s of the given registers.
		 */
		private fun ancestorsOf(
			startingRegisters: Iterable<L2Register<*>>
		): Set<L2SemanticValue<*>> = ancestorRegistersOf(startingRegisters)
			.flatMapTo(mutableSetOf()) {
				it.definition().semanticValues()
			}

		/**
		 * Create an [L2ExistsCondition] that is true at a point where any of
		 * the ancestors of the given registers was in an unboxed [Int] form.
		 *
		 * If all the semantic values associated with these registers are
		 * constant, answer `null`.
		 *
		 * @param startingRegisters
		 *   The list of registers from which to search for ancestors.
		 * @return
		 *   The [L2ExistsCondition], or `null` if only semantic constants were
		 *   provided.
		 */
		fun unboxedIntCondition(
			startingRegisters: List<L2Register<*>>
		): L2SplitCondition?
		{
			val intValues = L2SplitCondition.ancestorsOf(startingRegisters)
				.mapNotNull { value ->
					when (value)
					{
						is L2SemanticConstant -> null
						is L2SemanticBoxedValue -> L2SemanticUnboxedInt(value)
						is L2SemanticUnboxedInt ->
							if (value.isConstant) null else value
						else -> null
					}
				}.toSet()
			if (intValues.isEmpty()) return null
			return L2ExistsCondition(intValues)
		}

		/**
		 * Create an [L2ExistsCondition] that is true at a point where any of
		 * the given [L2SemanticValue]s is backed by a register.
		 *
		 * If all the semantic values associated with these registers are
		 * constant, answer `null`.
		 *
		 * @param semanticValues
		 *   The list of [L2SemanticValue]s, any of which should be detected.
		 * @return
		 *   The [L2ExistsCondition], or `null` if only semantic constants were
		 *   provided.
		 */
		fun existsCondition(
			semanticValues: Iterable<L2SemanticValue<*>>
		): L2SplitCondition?
		{
			val nonConstants = semanticValues.filterNot {
				it.isConstant
					|| (it is L2SemanticUnboxedInt && it.base.isConstant)
					|| (it is L2SemanticUnboxedFloat && it.base.isConstant)
			}
			if (nonConstants.isEmpty()) return null
			return L2ExistsCondition(nonConstants.toSet())
		}

		/**
		 * Create a [Set] of [L2MeetsRestrictionCondition]s that are true
		 * whenever any of the ancestors of the given registers happens to be
		 * restricted to one of the constants that an ancestor knew it to be.
		 *
		 * If no ancestor [L2Register] was known to be a constant, answer the
		 * empty set.
		 *
		 * @param startingRegisters
		 *   The registers from which to search for ancestors.
		 * @return
		 *   The [Set] of relevant [L2MeetsRestrictionCondition]s, which may be
		 *   empty.
		 */
		fun constantConditions(
			startingRegisters: Iterable<L2Register<*>>
		): Set<L2SplitCondition>
		{
			val ancestorValues = ancestorsOf(startingRegisters)
			return ancestorRegistersOf(startingRegisters)
				.mapNotNull { reg ->
					reg.definition().restriction().constantOrNull?.let { it }
				}
				.mapTo(mutableSetOf()) { constant ->
					L2MeetsRestrictionCondition(
						ancestorValues, boxedRestrictionForConstant(constant))
				}
		}

		/**
		 * Create an [L2MeetsRestrictionCondition] that is true when any of
		 * the ancestors of the given registers satisfies the given
		 * [TypeRestriction].
		 *
		 * @param startingRegisters
		 *   The registers from which to search for ancestors.
		 * @param requiredRestriction
		 *   The [TypeRestriction] that will be applied to the ancestor
		 *   [L2SemanticValue]s when determining if the condition holds at
		 *   some point in the [L2ControlFlowGraph].
		 * @return
		 *   The [L2MeetsRestrictionCondition], or `null` if only constant
		 *   semantic values were present.
		 */
		fun typeRestrictionCondition(
			startingRegisters: Iterable<L2Register<*>>,
			requiredRestriction: TypeRestriction
		): L2SplitCondition?
		{
			val ancestorValues = ancestorsOf(startingRegisters)
				.mapNotNull { value ->
					when (value)
					{
						is L2SemanticUnboxedInt -> value.base
						is L2SemanticUnboxedFloat -> value.base
						is L2SemanticBoxedValue -> value
						else -> null
					}
				}.filterNotTo(mutableSetOf()) { it.isConstant }
			if (ancestorValues.isEmpty()) return null
			return L2MeetsRestrictionCondition(
				ancestorValues, requiredRestriction)
		}

		/**
		 * Create an [L2FakeCondition] with the given [debugId].  It's never
		 * actually satisfied, but is used as a key in the submap during
		 * code splitting when a basic block acting as an entry point has
		 * more than one incoming edge.
		 *
		 * @param debugId
		 *   The unique number that might make debugging easier.
		 * @return
		 *   The [L2FakeCondition].
		 */
		fun fakeCondition(
			debugId: Int
		): L2SplitCondition = L2FakeCondition(debugId)

		/**
		 * Given some [L2SplitCondition]s, return a list containing the ones
		 * that aren't implied by others in the list.
		 */
		fun reducedConditions(
			conditions: Iterable<L2SplitCondition>
		): Set<L2SplitCondition>
		{
			// Get rid of all conditioss implied by other ones.  This is
			// currently quadratic, but the number of split conditions should be
			// limited for other reasons anyhow (e.g., to avoid overexpansion
			// of split code paths).
			return conditions.filterTo(mutableSetOf()) { c1 ->
				conditions.none { c2 -> c1 !== c2 && c1.impliedBy(c2) }
			}
		}
	}
}
