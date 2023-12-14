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

import avail.descriptor.representation.AvailObject.Companion.combine3
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operation.L2_BOX_INT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_UNBOX_INT
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import avail.interpreter.levelTwo.operation.L2_UNBOX_INT
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.L2Register.RegisterKind.*
import avail.optimizer.reoptimizer.L2Regenerator
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

	/**
	 * A condition that holds if some register (an [L2IntRegister]) holds the
	 * unboxed [Int] form of some value.
	 */
	class L2IsUnboxedIntCondition private constructor (
		private val semanticValues: Set<L2SemanticUnboxedInt>
	) : L2SplitCondition()
	{
		override fun equals(other: Any?): Boolean =
			other is L2IsUnboxedIntCondition &&
				other.semanticValues == semanticValues

		override val hash = semanticValues.hashCode() xor 0x4AB463DE

		override fun holdsFor(manifest: L2ValueManifest): Boolean =
			semanticValues.any { manifest.hasSemanticValue(it) }

		override fun toString(): String = "Unboxed int: $semanticValues"

		companion object
		{
			/**
			 * Create an [L2IsUnboxedIntCondition] that is true when any of the
			 * ancestors of the given registers was in an unboxed form.
			 *
			 * @param startingRegisters
			 *   The list of registers from which to search for ancestors.
			 * @return
			 *   The [L2IsUnboxedIntCondition].
			 */
			fun unboxedIntCondition(
				startingRegisters: List<L2Register>
			): L2IsUnboxedIntCondition
			{
				val intValues = ancestorsOf(startingRegisters)
					.mapNotNull { value ->
						when (value.kind)
						{
							INTEGER_KIND -> value as L2SemanticUnboxedInt
							BOXED_KIND -> L2SemanticUnboxedInt(value)
							else -> null
						}
					}.toSet()
				return L2IsUnboxedIntCondition(intValues)
			}
		}
	}

	/**
	 * A condition that holds if a register having one of the given
	 * [semanticValues] is guaranteed to satisfy the provided [TypeRestriction].
	 */
	class L2MeetsRestrictionCondition private constructor (
		private val semanticValues: Set<L2SemanticValue>,
		private val requiredRestriction: TypeRestriction
	) : L2SplitCondition()
	{
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
			"Restrict: $semanticValues, $requiredRestriction"

		companion object
		{
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
			 *   The [L2MeetsRestrictionCondition].
			 */
			fun typeRestrictionCondition(
				startingRegisters: Iterable<L2Register>,
				requiredRestriction: TypeRestriction
			): L2MeetsRestrictionCondition
			{
				val ancestorValues = ancestorsOf(startingRegisters)
					.mapNotNull { value ->
						when (value.kind)
						{
							INTEGER_KIND -> (value as L2SemanticUnboxedInt).base
							BOXED_KIND -> value
							else -> null
						}
					}.toSet()
				return L2MeetsRestrictionCondition(
					ancestorValues, requiredRestriction)
			}
		}
	}

	companion object
	{
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
			startingRegisters: Iterable<L2Register>
		): Set<L2SemanticValue>
		{
			// We're not just interested in whether the source or destination
			// register ever satisfied the type restriction, we also care
			// whether any register that led to these through a series of
			// phis/moves/boxes/unboxes was ever unboxed.
			val allRegisters = mutableListOf<L2Register>()
			val moreRegisters = startingRegisters.toMutableSet()
			while (moreRegisters.isNotEmpty())
			{
				allRegisters.addAll(moreRegisters)
				val moreRegistersCopy = moreRegisters.toList()
				moreRegisters.clear()
				moreRegistersCopy.forEach { reg ->
					reg.definitions().forEach { defWrite ->
						val def = defWrite.instruction
						val readOperands = when (def.operation)
						{
							is L2_PHI_PSEUDO_OPERATION<*, *, *, *> ->
								def.readOperands
							is L2_MOVE<*, *, *, *> -> def.readOperands
							is L2_BOX_INT -> def.readOperands
							is L2_UNBOX_INT -> def.readOperands
							is L2_JUMP_IF_UNBOX_INT -> def.readOperands
							else -> emptyList()
						}
						readOperands.mapTo(moreRegisters) { it.register() }
					}
				}
				// Ignore ones we've already visited.
				moreRegisters.removeAll(allRegisters)
			}
			val allValues = allRegisters.map {
				it.definition().semanticValues()
			}.fold(emptySet(), Set<L2SemanticValue>::union)
			return allValues
		}
	}
}
