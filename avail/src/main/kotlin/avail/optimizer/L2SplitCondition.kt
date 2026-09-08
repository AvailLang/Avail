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
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.anyRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.floatRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.i32Restriction
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.IMMUTABLE_FLAG
import avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2Synonym.Companion.appendSemanticValues
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticValue
import avail.utility.Strings.truncateTo
import avail.utility.intersects
import avail.utility.mapToSet

/**
 * An [L2SplitCondition] is a predicate on an [L2ValueManifest] which would be
 * profitable to sustain through portions of the [L2ControlFlowGraph] by
 * duplication of some vertices.
 *
 * Sets of these conditions are used by [L2Optimizer.doCodeSplitting] to control
 * how an [L2Regenerator] is to avoid prematurely merging control flow and
 * destroying actionable information.
 */
//@Suppress("EqualsOrHashCode")
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

	/**
	 * Whether this condition should be excluded if it is already known to hold
	 * after the instruction suggesting it, which is captured in the given
	 * [L2ValueManifest].
	 */
	open fun excludeIfAlreadyHolds(manifest: L2ValueManifest): Boolean =
		true

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
	private class L2ExistsCondition
	constructor (
		private val semanticValues: Set<L2SemanticValue>
	): L2SplitCondition()
	{
		override fun equals(other: Any?): Boolean =
			other is L2ExistsCondition &&
				other.semanticValues == semanticValues

		override val hash = semanticValues.hashCode() + 0x7F38A734

		override fun holdsFor(manifest: L2ValueManifest): Boolean =
			semanticValues.any { manifest.hasSemanticValue(it) }

		override fun toString(): String =
			"Exists: ${semanticValues.sorted()}}".take(50)

		override fun impliedBy(otherCondition: L2SplitCondition): Boolean =
			otherCondition is L2ExistsCondition &&
				semanticValues.containsAll(otherCondition.semanticValues)
	}

	/**
	 * A condition that holds if a register having one of the given
	 * [semanticValues] is guaranteed to satisfy the provided [TypeRestriction].
	 */
	private class L2MeetsRestrictionCondition
	constructor (
		val semanticValues: Set<L2SemanticValue>,
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

		override fun toString(): String = buildString {
			append("Restrict: ")
			append(requiredRestriction.toString(isTag = false, bare = true))
			append(" for ")
			appendSemanticValues(semanticValues, false)
		}.truncateTo(200)

		override fun impliedBy(otherCondition: L2SplitCondition): Boolean =
			otherCondition is L2MeetsRestrictionCondition &&
				otherCondition.requiredRestriction
					.isStrongerThan(requiredRestriction) &&
				semanticValues.containsAll(otherCondition.semanticValues)
	}

	/**
	 * A helper class for tracing backward through instructions, collecting the
	 * registers and semantic values, and simultaneously transforming what
	 * [TypeRestriction] they should be tested against during code splitting.
	 */
	class RestrictionTracer
	constructor (
		initialRegisters: Collection<L2Register<*>>,
		initialRestriction: TypeRestriction,
		val traceVariants: Boolean = true,
		val traceTags: Boolean = true,
		val traceArithmetic: Boolean = true)
	{
		private val registersToVisit = initialRegisters.mapTo(ArrayDeque()) {
			it to initialRestriction
		}

		val visitedRegisters = mutableSetOf<L2Register<*>>()

		private val restrictionToSemanticValue =
			mutableMapOf<TypeRestriction, MutableSet<L2SemanticValue>>()

		fun traceAll(): Set<L2SplitCondition?>
		{
			while (registersToVisit.isNotEmpty())
			{
				val (register, restriction) = registersToVisit.removeFirst()
				if (!visitedRegisters.add(register)) continue
				val write = register.definition()
				val values = write.semanticValues()
					.filterNot(L2SemanticValue::isConstant)
				if (values.isEmpty())
				{
					// Ignore if only constants were written.
					continue
				}
				write.instruction.traceCandidateSplitConditions(
					write, restriction, this)
				restrictionToSemanticValue
					.getOrPut(restriction, ::mutableSetOf)
					.addAll(values)
			}
			return restrictionToSemanticValue.mapTo(mutableSetOf()) {
					(restriction, values) ->
				L2MeetsRestrictionCondition(values, restriction)
			}
		}

		/**
		 * An instruction has asked to propagate a restriction to an ancestor
		 * register.  It may have been transformed by the instruction, such as
		 * switching the [RegisterKind] to reflect the instruction's source
		 * value, or switching from a tag id (and [Int]) to its supremum type.
		 *
		 * @param register
		 *   The [L2Register] to trace back to its definition (and perhaps
		 *   beyond).
		 * @param restriction
		 *   The [TypeRestriction] for which a condition should be added if the
		 *   register has not yet been visited.
		 */
		fun continueTracing(
			register: L2Register<*>,
			restriction: TypeRestriction)
		{
			if (visitedRegisters.contains(register)) return
			if (restriction.isImpossible) return
			registersToVisit.add(register to restriction)
		}
	}

	/**
	 * A condition that holds if any value from the first set is in the same
	 * [L2Synonym] as a value from the second set.
	 */
	private class L2SameSynonymCondition constructor (
		private val semanticValues1: Set<L2SemanticValue>,
		private val semanticValues2: Set<L2SemanticValue>
	) : L2SplitCondition()
	{
		override fun equals(other: Any?): Boolean =
			other is L2SameSynonymCondition &&
				other.semanticValues1 == semanticValues1 &&
				other.semanticValues2 == semanticValues2

		override val hash = combine3(
			semanticValues1.hashCode(),
			semanticValues2.hashCode(),
			0x52DBE34A)

		override fun holdsFor(manifest: L2ValueManifest): Boolean
		{
			val synonyms1 = semanticValues1
				.filter(manifest::hasSemanticValue)
				.mapToSet { manifest.semanticValueToSynonym(it) }
			val synonyms2 = semanticValues2
				.filter(manifest::hasSemanticValue)
				.mapToSet { manifest.semanticValueToSynonym(it) }
			return synonyms1.intersects(synonyms2)
		}

		override fun toString(): String =
			"Synonymous: ${semanticValues1.sorted()} " +
				"and ${semanticValues2.sorted()}}"

		override fun impliedBy(otherCondition: L2SplitCondition): Boolean =
			otherCondition is L2SameSynonymCondition &&
				semanticValues1.containsAll(otherCondition.semanticValues1) &&
				semanticValues2.containsAll(otherCondition.semanticValues2)
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
		 * Create [L2ExistsCondition]s that are true at a point where any of
		 * the ancestors of the given registers were in an unboxed [Int] form.
		 *
		 * @param startingRegisters
		 *   The list of registers from which to search for ancestors.
		 * @return
		 *   The [L2ExistsCondition], or `null` if only semantic constants were
		 *   provided.
		 */
		fun <K: RegisterKind<K>> unboxedIntConditions(
			startingRegisters: List<L2Register<K>>
		): Set<L2SplitCondition?> = buildSet {
			// First do a trace to collect relevant ancestors.
			val tracer = RestrictionTracer(
				initialRegisters = startingRegisters,
				initialRestriction = i32Restriction,
				traceVariants = false,
				traceTags = false,
				traceArithmetic = false)
			// Run for side-effect, to collect all ancestor registers.
			val intRegisters =
				tracer.visitedRegisters.filterIsInstance<L2IntRegister>()
			addAll(tracer.traceAll())
			val intValues = intRegisters
				.flatMapTo(mutableSetOf()) { it.definition().semanticValues() }
			if (intValues.isNotEmpty())
				add(L2ExistsCondition(intValues))
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
			semanticValues: Collection<L2SemanticValue>
		): L2SplitCondition?
		{
			val nonConstants =
				semanticValues.filterNot(L2SemanticValue::isConstant)
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
		fun <K: RegisterKind<K>> constantConditions(
			startingRegisters: Collection<L2Register<K>>
		): Set<L2SplitCondition> = buildSet {
			// First do a trace to collect relevant ancestors.
			val startingRestriction = when (startingRegisters.first().kind)
			{
				BOXED_KIND -> anyRestriction
				INTEGER_KIND -> i32Restriction
				FLOAT_KIND -> floatRestriction
			}
			val tracer = RestrictionTracer(startingRegisters, startingRestriction)
			// Run for side-effect, to collect all ancestor registers.
			tracer.traceAll()
			tracer.visitedRegisters.forEach { register ->
				val definition = register.definition()
				val instruction = definition.instruction
				if (instruction !is L2_PHI<*>) return@forEach
				// This is an ancestral phi, which is where constantness could
				// be lost.
				instruction.basicBlock().predecessorEdges()
					.zip(instruction.sources.elements)
					.forEach { (edge, read) ->
						val restriction =
							edge.manifest().restrictionFor(read.semanticValue())
						if (restriction.isConstant)
						{
							// The value arriving on this edge to the phi is a
							// constant.
							add(
								L2MeetsRestrictionCondition(
									setOf(read.semanticValue()), restriction))
						}
					}
			}
		}

		/**
		 * Create a set of [L2SplitCondition]s that are true when any of the
		 * ancestors of the given registers satisfies the given
		 * [TypeRestriction], or a suitable translation thereof.
		 *
		 * @param startingRegisters
		 *   The registers from which to search for ancestors.
		 * @param requiredRestriction
		 *   The [TypeRestriction] that will be applied to the ancestor
		 *   [L2SemanticValue]s when determining if the condition holds at
		 *   some point in the [L2ControlFlowGraph].
		 * @return
		 *   The [Set] of [L2SplitCondition]s, or `null` if only constant
		 *   semantic values were present.
		 */
		fun <K: RegisterKind<K>> typeRestrictionConditions(
			startingRegisters: Collection<L2Register<K>>,
			requiredRestriction: TypeRestriction
		): Set<L2SplitCondition?>
		{
			val tracer =
				RestrictionTracer(startingRegisters, requiredRestriction)
			return tracer.traceAll()
		}

		/**
		 * Create a split condition that determines when two values are in the
		 * same synonym.
		 */
		fun sameSynonymCondition(
			registers1: Collection<L2Register<*>>,
			registers2: Collection<L2Register<*>>
		): L2SplitCondition?
		{
			val tracer1 = RestrictionTracer(
				registers1,
				anyRestriction,
				traceVariants = false,
				traceTags = false,
				traceArithmetic = false)
			val semanticValues1 = tracer1.traceAll()
				.filterIsInstance<L2MeetsRestrictionCondition>()
				.flatMapTo(mutableSetOf()) { it.semanticValues }
			val tracer2 = RestrictionTracer(
				registers2,
				anyRestriction,
				traceVariants = false,
				traceTags = false,
				traceArithmetic = false)
			val semanticValues2 = tracer2.traceAll()
				.filterIsInstance<L2MeetsRestrictionCondition>()
				.flatMapTo(mutableSetOf()) { it.semanticValues }
			// If either set of semantic values is empty, or if *both* sets only
			// contain constants, there's no meaningful condition to create.
			if (semanticValues1.isEmpty()
				|| semanticValues2.isEmpty()
				|| (semanticValues1.all { it.isConstant }
					&& semanticValues2.all { it.isConstant }))
			{
				return null
			}
			return L2SameSynonymCondition(semanticValues1, semanticValues2)
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
