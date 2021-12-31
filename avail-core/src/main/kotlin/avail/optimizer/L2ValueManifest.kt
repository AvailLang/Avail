/*
 * L2ValueManifest.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import avail.descriptor.types.A_Type
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.bottomRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.IMMUTABLE_FLAG
import avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE
import avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.L2Register.RegisterKind
import avail.interpreter.levelTwo.register.L2Register.RegisterKind.*
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.utility.Mutable
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.cast
import avail.utility.removeLast
import avail.utility.structures.EnumMap
import avail.utility.structures.EnumMap.Companion.enumMap
import java.util.Collections.singletonList
import java.util.EnumSet
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

/**
 * The `L2ValueManifest` maintains information about which [L2SemanticValue]s
 * hold equivalent values at this point, the [TypeRestriction]s for those
 * semantic values, and the list of [L2WriteOperand]s that are visible
 * definitions of those values.
 *
 * In order to avoid reevaluating primitives with the same values, a manifest
 * also tracks [L2Register]s hold values representing which [L2SemanticValue]s,
 * specifically using [L2Synonym]s as the binding mechanism.
 *
 * The mapping is keyed both ways (semantic value → synonym, and register →
 * synonym), so that registers can be efficiently removed.  This happens at
 * control flow merges, where only the intersection of the semantic values
 * available in each predecessor edge is kept, specifically via the introduction
 * of new registers defined by phi instructions.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2ValueManifest
{
	/**
	 * A utility type containing a mutable list of [L2Register]s that currently
	 * hold the same value, and a [TypeRestriction].
	 *
	 * @property definitions
	 *   An immutable [List] of [L2Register]s that hold the same value.  They
	 *   may be of differing [RegisterKind], which is useful for tracking
	 *   equivalent boxed and unboxed values.  The list may be replaced, but not
	 *   internally modified.  Also, the caller must not modify the list after
	 *   passing it to this constructor.
	 * @property restriction
	 *   The [TypeRestriction] that describes the types, constant values,
	 *   excluded types, excluded values, and [RegisterKind]s that constrain the
	 *   registers of some [L2Synonym].
	 */
	class Constraint(
		theDefinitions: List<L2Register>,
		var restriction: TypeRestriction)
	{
		var definitions: List<L2Register> = theDefinitions
			set(value)
			{
				assert(value.size == value.toSet().size)
				field = value
			}

		/**
		 * A copy constructor, producing an instance that will not change when
		 * the original instance changes.
		 */
		constructor(original: Constraint) :
			this(original.definitions, original.restriction)

		override fun toString(): String
		{
			return "${definitions.joinToString()}: $restriction"
		}
	}

	/** The synonyms keyed by semantic values. */
	private val semanticValueToSynonym: MutableMap<L2SemanticValue, L2Synonym>

	/**
	 * A map from each [L2Synonym] to the [Constraint] that tracks the current
	 * [TypeRestriction] and immutable list of [L2Register]s that hold the value
	 * represented by that synonym.
	 */
	private val constraints: MutableMap<L2Synonym, Constraint>

	/**
	 * A map from <[L2SemanticValue], [RegisterKind]> to source [L2Instruction]s
	 * from a previous [L2ControlFlowGraph], which can be translated as needed
	 * by an [L2Regenerator], as part of the instruction postponement
	 * optimization (perfect redundancy elimination). The same source
	 * instruction is stored under each semantic value that was written by that
	 * instruction.
	 */
	var postponedInstructions: EnumMap<
			RegisterKind,
			MutableMap<L2SemanticValue, MutableList<L2Instruction>>>? =
		null

	/** Create a new, empty manifest. */
	constructor()
	{
		semanticValueToSynonym = mutableMapOf()
		constraints = mutableMapOf()
	}

	/**
	 * Copy an existing manifest.
	 *
	 * @param originalManifest
	 *   The original manifest.
	 */
	constructor(originalManifest: L2ValueManifest)
	{
		semanticValueToSynonym =
			originalManifest.semanticValueToSynonym.toMutableMap()
		constraints = originalManifest.constraints.mapValuesTo(mutableMapOf()) {
			Constraint(it.value)
		}
		postponedInstructions = originalManifest.postponedInstructions
			?.mapValuesTo(enumMap()) { (_, submap) ->
				submap.mapValuesTo(mutableMapOf()) { (_, instructions) ->
					instructions.toMutableList()
				}
			}
	}

	/**
	 * Record a sourceInstruction, which can be translated by an [L2Regenerator]
	 * whenever its output values are needed.
	 */
	fun recordPostponedSourceInstruction(sourceInstruction: L2Instruction)
	{
		if (postponedInstructions == null)
		{
			postponedInstructions = enumMap()
		}
		for (write in sourceInstruction.writeOperands)
		{
			val submap =
				postponedInstructions!!.getOrPut(write.registerKind) {
					mutableMapOf()
				}
			write.semanticValues().forEach {
				submap.getOrPut(it, ::mutableListOf).add(sourceInstruction)
			}
		}
	}

	/**
	 * Record a sourceInstruction for just the given [RegisterKind] and
	 * [L2SemanticValue].  It can be translated as needed by an [L2Regenerator]
	 * whenever its output values are needed.
	 */
	private fun recordPostponedSourceInstruction(
		sourceInstruction: L2Instruction,
		kind: RegisterKind,
		semanticValue: L2SemanticValue)
	{
		if (postponedInstructions == null)
		{
			postponedInstructions = enumMap()
		}
		val submap = postponedInstructions!!.getOrPut(kind) { mutableMapOf() }
		submap.getOrPut(semanticValue, ::mutableListOf).add(sourceInstruction)
	}

	/**
	 * Remove a previously recorded sourceInstruction, which can be translated
	 * by an [L2Regenerator] whenever its output values are needed.  It will be
	 * stored under each [RegisterKind] and [L2SemanticValue] that the
	 * [L2Instruction] wrote in the prior [L2ControlFlowGraph], so it should be
	 * removed under each such key.  Answer the instruction.
	 *
	 * Fail if such an instruction is not found.
	 */
	fun removePostponedSourceInstruction(
		registerKind: RegisterKind,
		semanticValue: L2SemanticValue): L2Instruction
	{
		val sourceInstructions =
			postponedInstructions!![registerKind]!![semanticValue]!!
		val sourceInstruction = sourceInstructions.last()
		for (write in sourceInstruction.writeOperands)
		{
			val submap = postponedInstructions!![write.registerKind]!!
			write.semanticValues().forEach {
				val instructions = submap[it]!!
				val lastInstruction = instructions.removeLast()
				assert(lastInstruction == sourceInstruction)
				if (instructions.isEmpty())
				{
					submap.remove(it)
				}
			}
		}
		return sourceInstruction
	}

	/**
	 * Update the [Constraint] associated with the given [L2Synonym].
	 *
	 * @param synonym
	 *   The [L2Synonym] to look up.
	 * @param body
	 *   What to perform with the looked up [Constraint] as the receiver.
	 */
	fun updateConstraint(synonym: L2Synonym, body: Constraint.() -> Unit) =
		when (val constraint = constraints[synonym])
		{
			null ->
			{
				// The manifest doesn't know about this synonym yet.
				val newConstraint = Constraint(emptyList(), bottomRestriction)
				newConstraint.body()
				// The body should have replaced the restriction.
				assert(newConstraint.restriction != bottomRestriction)
				for (sv in synonym.semanticValues())
				{
					val priorSynonym = semanticValueToSynonym.put(sv, synonym)
					assert(priorSynonym === null)
				}
				constraints[synonym] = newConstraint
			}
			else ->
			{
				val newConstraint = Constraint(constraint)
				constraints[synonym] = newConstraint
				newConstraint.body()
			}
		}


	/**
	 * Update the [Constraint] associated with the [L2Synonym] containing the
	 * given [L2SemanticValue].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @param body
	 *   What to perform with the looked up [Constraint] as the receiver.
	 */
	private fun updateConstraint(
		semanticValue: L2SemanticValue,
		body: Constraint.() -> Unit
	) = updateConstraint(semanticValueToSynonym(semanticValue), body)

	/**
	 * Verify that this manifest is internally consistent.
	 */
	fun check()
	{
		if (deepManifestDebugCheck)
		{
			assert(semanticValueToSynonym.values.toSet() == constraints.keys)
			val registers = constraints.flatMap { it.value.definitions }
			assert(registers.size == registers.toSet().size)
		}
	}

	/**
	 * Answer a collection of [L2SemanticValue]s that are either live in
	 * registers or in the [postponedInstructions] multi-level [Map].
	 */
	private fun liveOrPostponedSemanticValues(): MutableSet<L2SemanticValue> {
		val set = mutableSetOf<L2SemanticValue>()
		set.addAll(semanticValueToSynonym.keys)
		postponedInstructions?.forEach { (_, submap) ->
			set.addAll(submap.keys)
		}
		return set
	}

	/**
	 * Look up the given [L2SemanticValue], answering the [L2Synonym] that's
	 * bound to it.  Fail if it's not found.
	 *
	 * @param semanticValue
	 *   The semantic value to look up.
	 * @return
	 *   The [L2Synonym] bound to that semantic value.
	 */
	fun semanticValueToSynonym(semanticValue: L2SemanticValue): L2Synonym =
		semanticValueToSynonym[semanticValue]!!

	/**
	 * Look up the given [L2SemanticValue], answering the [L2Synonym] that's
	 * bound to it.  If not found, evaluate the lambda to produce an
	 * optional `L2Synonym` or `null`.
	 *
	 * @param semanticValue
	 *   The semantic value to look up.
	 * @param elseSupplier
	 *   The code to run if the semantic value was not found.
	 * @return
	 *   The [L2Synonym] bound to that semantic value, or `null`.
	 */
	private fun semanticValueToSynonymOrElse(
		semanticValue: L2SemanticValue,
		elseSupplier: ()->L2Synonym
	): L2Synonym = semanticValueToSynonym[semanticValue] ?: elseSupplier()

	/**
	 * Capture information about a new [L2Synonym] and its [TypeRestriction].
	 * It's an error if any of the semantic values of the synonym are already
	 * bound to other synonyms in this manifest.
	 *
	 * @param freshSynonym
	 *   The new [L2Synonym] to record.
	 * @param restriction
	 *   The [TypeRestriction] to constrain the new synonym.
	 */
	fun introduceSynonym(freshSynonym: L2Synonym, restriction: TypeRestriction)
	{
		for (sv in freshSynonym.semanticValues())
		{
			val priorSynonym = semanticValueToSynonym.put(sv, freshSynonym)
			assert(priorSynonym === null)
		}
		constraints[freshSynonym] = Constraint(emptyList(), restriction)
		check()
	}

	/**
	 * Answer whether the [L2SemanticValue] is known to this manifest.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue].
	 * @return
	 *   Whether there is a register known to be holding this value.
	 */
	fun hasSemanticValue(semanticValue: L2SemanticValue): Boolean =
		semanticValueToSynonym.containsKey(semanticValue)

	/**
	 * Given an [L2SemanticValue], see if there's already an equivalent one in
	 * this manifest.  If an [L2SemanticPrimitiveInvocation] is supplied, look
	 * for a recursively synonymous one.
	 *
	 * Answer the extant [L2SemanticValue] if found, otherwise answer `null`.
	 * Note that there may be multiple [L2SemanticPrimitiveInvocation]s that are
	 * equivalent, in which case an arbitrary (and not necessarily stable) one
	 * is chosen.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @return
	 *   An [L2SemanticValue] from this manifest which is equivalent to the
	 *   given one, or `null` if no such value is in the manifest.
	 */
	fun equivalentSemanticValue(semanticValue: L2SemanticValue): L2SemanticValue?
	{
		if (semanticValueToSynonym.containsKey(semanticValue))
		{
			// It already exists in exactly the form given.
			return semanticValue
		}
		if (semanticValue !is L2SemanticPrimitiveInvocation)
		{
			// It's not present and it's not a primitive.
			return null
		}
		val newArgs = semanticValue.argumentSemanticValues
		val numArgs = newArgs.size
		nextEntry@ for ((existingPrimitive) in semanticValueToSynonym)
		{
			if (existingPrimitive !is L2SemanticPrimitiveInvocation)
			{
				continue
			}
			if (existingPrimitive.primitive !== semanticValue.primitive)
			{
				continue
			}
			// The actual primitives match here.
			val existingArgs = existingPrimitive.argumentSemanticValues
			assert(existingArgs.size == numArgs)
			for (i in 0 until numArgs)
			{
				val newArg = newArgs[i]
				val existingArg = existingArgs[i]
				if (newArg == existingArg)
				{
					// The corresponding arguments at this position are equal.
					continue
				}
				// Check if the arguments are still visible and match.
				if (!hasSemanticValue(newArg) || !hasSemanticValue(existingArg))
				{
					// One of the arguments is no longer extant in the manifest,
					// and also wasn't equal to its counterpart.
					continue@nextEntry
				}
				val existingSynonym = semanticValueToSynonym(existingArg)
				if (semanticValueToSynonym(newArg) == existingSynonym)
				{
					// They're known to be synonymous.
					continue
				}
				if (newArg !is L2SemanticPrimitiveInvocation)
				{
					// They're not synonyms, and the new arg isn't primitive.
					continue@nextEntry
				}
				val newArgEquivalent =
					equivalentSemanticValue(newArg) ?:
						// No equivalent was found in the manifest.
						continue@nextEntry
				// An equivalent was found, so check if the equivalent is
				// synonymous with the corresponding argument.
				if (semanticValueToSynonym(newArgEquivalent) != existingSynonym)
				{
					continue@nextEntry
				}
			}
			// The arguments of sv matched.
			return existingPrimitive
		}
		// No extant semantic values matched.
		return null
	}

	/**
	 * Merge a new [L2SemanticValue] into an existing [L2Synonym]. Update the
	 * manifest to reflect the merge.
	 *
	 * Note that because the [L2SemanticValue] is new, we don't have to check
	 * for existing [L2SemanticPrimitiveInvocation]s becoming synonyms of each
	 * other, which is much faster than the general case in
	 * [mergeExistingSemanticValues].
	 *
	 * @param existingSynonym
	 *   An [L2Synonym].
	 * @param semanticValue
	 *   Another [L2SemanticValue] representing the same value.
	 */
	fun extendSynonym(
		existingSynonym: L2Synonym,
		semanticValue: L2SemanticValue)
	{
		assert(!hasSemanticValue(semanticValue))
		val semanticValues = existingSynonym.semanticValues().toMutableSet()
		semanticValues.add(semanticValue)
		val merged = L2Synonym(semanticValues)
		semanticValues.forEach { sv ->
			semanticValueToSynonym[sv] = merged
		}
		constraints[merged] = constraints.remove(existingSynonym)!!
		check()
	}

	/**
	 * Given two [L2SemanticValue]s, merge their [L2Synonym]s together, if
	 * they're not already.  Update the manifest to reflect the merged synonyms.
	 *
	 * @param semanticValue1
	 *   An [L2SemanticValue].
	 * @param semanticValue2
	 *   Another [L2SemanticValue] representing what has just been shown to be
	 *   the same value.  It may already be a synonym of the first semantic
	 *   value.
	 */
	fun mergeExistingSemanticValues(
		semanticValue1: L2SemanticValue,
		semanticValue2: L2SemanticValue)
	{
		val synonym1 = semanticValueToSynonym(semanticValue1)
		val synonym2 = semanticValueToSynonym(semanticValue2)
		if (!privateMergeSynonyms(synonym1, synonym2))
		{
			return
		}

		// Figure out which L2SemanticPrimitiveInvocations have become
		// equivalent due to their arguments being merged into the same
		// synonyms.  Repeat as necessary, alternating collection of newly
		// matched pairs of synonyms with merging them.
		val allSemanticPrimitives =
			semanticValueToSynonym.keys
				.filter {
					L2SemanticPrimitiveInvocation::class.java.isInstance(it)
				}
				.map {  it as L2SemanticPrimitiveInvocation }
				.groupBy { it.primitive }
		if (allSemanticPrimitives.isEmpty())
		{
			// There are no primitive invocations visible.
			return
		}
		while (true)
		{
			val followupMerges =
				mutableListOf<Pair<L2SemanticValue, L2SemanticValue>>()
			for (invocations in allSemanticPrimitives.values)
			{
				// It takes at least two primitive invocations (of the same
				// primitive) for there to be a potential merge.
				if (invocations.size <= 1)
				{
					continue
				}
				// Create a map from each distinct input list of synonyms to the
				// set of invocation synonyms.
				val map =
					mutableMapOf<List<L2Synonym?>, MutableSet<L2Synonym>>()
				for (invocation in invocations)
				{
					// Note that sometimes an L2SemanticPrimitiveInvocation will
					// be in the manifest, even though some of its argument
					// semantic values are no longer accessible.  Create a
					// singleton synonym for such a semantic value, but don't
					// register it in the manifest.
					val argumentSynonyms: List<L2Synonym?> =
						invocation.argumentSemanticValues
							.map {
								semanticValueToSynonymOrElse(it)
								{ L2Synonym(setOf(it)) }
							}
					val primitiveSynonyms =
						map.computeIfAbsent(argumentSynonyms) { mutableSetOf() }
					val invocationSynonym = semanticValueToSynonym[invocation]!!
					if (primitiveSynonyms.isNotEmpty()
						&& !primitiveSynonyms.contains(invocationSynonym))
					{
						val sampleSynonym = primitiveSynonyms.first()
						val sampleInvocation = sampleSynonym.pickSemanticValue()
						followupMerges.add(invocation to sampleInvocation)
					}
					primitiveSynonyms.add(invocationSynonym)
				}
			}
			if (followupMerges.isEmpty())
			{
				break
			}
			followupMerges
				.forEach { pair: Pair<L2SemanticValue, L2SemanticValue> ->
					privateMergeSynonyms(
						semanticValueToSynonym(pair.first),
						semanticValueToSynonym(pair.second))
				}
		}
		check()
	}

	/**
	 * Given two [L2SemanticValue]s, merge their [L2Synonym]s together, if
	 * they're not already.  Update the manifest to reflect the merged synonyms.
	 * Do not yet merge synonyms of [L2SemanticPrimitiveInvocation]s whose
	 * arguments have just become equivalent.
	 *
	 * @param synonym1
	 *   An [L2Synonym].
	 * @param synonym2
	 *   Another [L2Synonym] representing what has just been shown to be the
	 *   same value.  It may already be equal to the first synonym.
	 * @return
	 *   Whether any change was made to the manifest.
	 */
	private fun privateMergeSynonyms(
		synonym1: L2Synonym,
		synonym2: L2Synonym): Boolean
	{
		if (synonym1 == synonym2)
		{
			return false
		}
		val semanticValues = synonym1.semanticValues().toMutableSet()
		semanticValues.addAll(synonym2.semanticValues())
		val merged = L2Synonym(semanticValues)
		semanticValues.forEach { semanticValueToSynonym[it] = merged }
		val constraint1 = constraints.remove(synonym1)!!
		val constraint2 = constraints.remove(synonym2)!!
		val restriction =
			constraint1.restriction.intersection(constraint2.restriction)
		// Just concatenate the input synonyms' lists, as this essentially
		// preserves earliest definition order.
		val list = constraint1.definitions.toMutableList()
		list.addAll(constraint2.definitions)
		constraints[merged] = Constraint(list, restriction)
		return true
	}

	/**
	 * Retrieve the oldest definition of the given [L2SemanticValue] or an
	 * equivalent, but having the given [RegisterKind].  Only consider
	 * registers whose definitions *all* include that semantic value.  This
	 * should work well in SSA or non-SSA, but not after register coloring.
	 *
	 * @param R
	 *   The kind of [L2Register] to return.
	 * @param semanticValue
	 *   The [L2SemanticValue] being examined.
	 * @param registerKind
	 *   The [RegisterKind] of the desired register.
	 * @return
	 *   The requested [L2Register].
	 */
	fun <R : L2Register> getDefinition(
		semanticValue: L2SemanticValue,
		registerKind: RegisterKind): R
	{
		val constraint = constraint(semanticValue)
		var definition = constraint.definitions.find { reg ->
			reg.registerKind === registerKind &&
				reg.definitions().all { write ->
					write.semanticValues().contains(semanticValue)
				}
		}
		if (definition == null)
		{
			// Fall back to any register of the requested kind, even if it
			// doesn't have the specified semanticValue in all of its
			// definitions.
			definition = constraint.definitions.find { reg ->
				reg.registerKind === registerKind
			}
		}
		assert(definition != null) {
			"Appropriate register for kind not found"
		}
		return definition.cast()
	}

	/**
	 * Retrieve all [L2Register]s known to contain the given [L2SemanticValue],
	 * but having the given [RegisterKind].  Narrow it to just those registers
	 * whose definitions *all* include that semantic value.
	 *
	 * @param <R>
	 *   The kind of [L2Register] to return.
	 * @param semanticValue
	 *   The [L2SemanticValue] being examined.
	 * @param registerKind
	 *   The [RegisterKind] of the desired register.
	 * @return
	 *   A [List] of the requested [L2Register]s.
	 */
	@Suppress("UNCHECKED_CAST")
	fun <R : L2Register> getDefinitions(
		semanticValue: L2SemanticValue,
		registerKind: RegisterKind
	): List<R> =
		constraint(semanticValue).definitions
			.filter { reg ->
				reg.registerKind === registerKind &&
					reg.definitions().all { write ->
						write.semanticValues().contains(semanticValue)
					}
			}
			.map { it as R }

	/**
	 * Replace the [TypeRestriction] associated with the given
	 * [L2SemanticValue], which must be known by this manifest.  Note that this
	 * also restricts any synonymous semantic values.
	 *
	 * @param semanticValue
	 *   The given [L2SemanticValue].
	 * @param restriction
	 *   The [TypeRestriction] to bound the synonym.
	 */
	fun setRestriction(
		semanticValue: L2SemanticValue,
		restriction: TypeRestriction)
	{
		constraint(semanticValue).restriction = restriction
		check()
	}

	/**
	 * Replace the [TypeRestriction] associated with the given
	 * [L2SemanticValue], which must be known by this manifest, with the
	 * intersection of its current restriction and the given restriction. Note
	 * that this also restricts any synonymous semantic values.
	 *
	 * @param semanticValue
	 *   The given [L2SemanticValue].
	 * @param type
	 *   The [A_Type] to intersect with the synonym.
	 */
	fun intersectType(semanticValue: L2SemanticValue, type: A_Type)
	{
		updateConstraint(semanticValue) {
			restriction = restriction.intersectionWithType(type)
		}
		check()
	}

	/**
	 * Replace the [TypeRestriction] associated with the given
	 * [L2SemanticValue], which must be known by this manifest, with the
	 * difference between its current restriction and the given restriction.
	 * Note that this also restricts any synonymous semantic values.
	 *
	 * @param semanticValue
	 *   The given [L2SemanticValue].
	 * @param type
	 *   The [A_Type] to exclude from the synonym's restriction.
	 */
	@Suppress("unused")
	fun subtractType(semanticValue: L2SemanticValue, type: A_Type)
	{
		updateConstraint(semanticValue) {
			restriction = restriction.minusType(type)
		}
		check()
	}

	/**
	 * Look up the [TypeRestriction] that currently bounds this
	 * [L2SemanticValue].  Fail if there is none.
	 *
	 * @param semanticValue
	 *   The given [L2SemanticValue].
	 * @return
	 *   The [TypeRestriction] that bounds the synonym.
	 */
	fun restrictionFor(semanticValue: L2SemanticValue) =
		constraint(semanticValue).restriction

	/**
	 * Answer the [Constraint] associated with the [L2Synonym] containing the
	 * given [L2SemanticValue].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] whose synonym is to be looked up.
	 * @return
	 *   The [Constraint] associated with the synonym.
	 */
	private fun constraint(semanticValue: L2SemanticValue) =
		constraints[semanticValueToSynonym(semanticValue)]!!

	/**
	 * Answer an arbitrarily ordered array of the [L2Synonym]s in this manifest.
	 *
	 * @return
	 *   An array of [L2Synonym]s.
	 */
	fun synonymsArray(): Array<L2Synonym> = constraints.keys.toTypedArray()

	/**
	 * Answer a [Set] of all [L2Register]s available in this manifest.
	 *
	 * @return
	 *   A [Set] of [L2Register]s.
	 */
	fun allRegisters(): Set<L2Register>
	{
		return constraints.values.flatMapTo(mutableSetOf()) { it.definitions }
	}

	/**
	 * Remove all information about registers and semantic values from this
	 * manifest.
	 */
	fun clear()
	{
		semanticValueToSynonym.clear()
		constraints.clear()
		postponedInstructions = null
	}

	/**
	 * Record the fact that an [L2Instruction] has been emitted, which writes to
	 * the given [L2WriteOperand].  Since this is the introduction of a new
	 * [L2SemanticValue], it must not yet be in this manifest.
	 *
	 * [L2Operation]s that move values between semantic values should customize
	 * their [L2Operation.instructionWasAdded] method to use
	 * [recordDefinitionForMove].
	 *
	 * @param writer
	 *   The operand that received the value.
	 */
	fun recordDefinition(writer: L2WriteOperand<*>)
	{
		recordDefinitionNoCheck(writer)
		check()
	}

	/**
	 * Record the fact that an [L2Instruction] has been emitted, which writes to
	 * the given [L2WriteOperand].  Since this is the introduction of a new
	 * [L2SemanticValue], it must not yet be in this manifest.
	 *
	 * [L2Operation]s that move values between semantic values should customize
	 * their [L2Operation.instructionWasAdded] method to use
	 * [recordDefinitionForMove].
	 *
	 * This form does not check consistency of the L2ValueManifest, to allow phi
	 * functions to be replaced by moves.
	 *
	 * @param writer
	 *   The operand that received the value.
	 */
	fun recordDefinitionNoCheck(writer: L2WriteOperand<*>)
	{
		assert(writer.instructionHasBeenEmitted)
		val pickSemanticValue = writer.pickSemanticValue()
		val synonym: L2Synonym
		if (hasSemanticValue(pickSemanticValue))
		{
			// This is a new RegisterKind for an existing semantic value.
			synonym = semanticValueToSynonym(pickSemanticValue)
			constraints[synonym]!!.run {
				// There shouldn't already be a register for this kind.
				//assert(!restriction.hasFlag(
				//	writer.registerKind.restrictionFlag))
				restriction = restriction.withFlag(
					writer.registerKind.restrictionFlag)
			}
		}
		else
		{
			// This is a write to a synonym that does not yet exist.
			assert(writer.semanticValues().none(
				semanticValueToSynonym::containsKey))
			synonym = L2Synonym(writer.semanticValues())
			introduceSynonym(synonym, writer.restriction())
		}
		updateConstraint(synonym) {
			definitions = definitions.append(writer.register())
		}
	}

	/**
	 * Record the fact that an [L2Instruction] has been emitted, which writes to
	 * the given [L2WriteOperand].  The source and destination should end up in
	 * the same synonym.
	 *
	 * @param writer
	 *   The operand that received the value.
	 * @param sourceSemanticValue
	 *   The [L2SemanticValue] that already holds the value.
	 */
	fun recordDefinitionForMove(
		writer: L2WriteOperand<*>,
		sourceSemanticValue: L2SemanticValue)
	{
		assert(writer.instructionHasBeenEmitted)
		for (semanticValue in writer.semanticValues())
		{
			@Suppress("ControlFlowWithEmptyBody")
			if (semanticValue == sourceSemanticValue)
			{
				// Introduce a definition of a new kind for a semantic value
				// that already has a value of a different kind.
				assert(hasSemanticValue(semanticValue))
				setRestriction(
					semanticValue,
					restrictionFor(sourceSemanticValue)
						.intersection(writer.restriction()))
			}
			else if (!hasSemanticValue(semanticValue))
			{
				// Introduce the newly written semantic value, synonymous to the
				// given one.
				val oldSynonym = semanticValueToSynonym(sourceSemanticValue)
				extendSynonym(oldSynonym, semanticValue)
				setRestriction(
					semanticValue,
					restrictionFor(sourceSemanticValue)
						.intersection(writer.restriction()))
			}
			else
			{
				// The write to an existing semantic value must be a consequence
				// of post-phi duplication into registers for synonymous
				// semantic values, but where there were multiple kinds leading
				// to multiple phis for the same target synonym.
			}
		}
		val synonym = semanticValueToSynonym(sourceSemanticValue)
		// Make sure to include the entire synonym inside the write operand, to
		// ensure each kind of register in the synonym has as complete a set of
		// semantic values as possible.
		synonym.semanticValues().forEach(
			writer::retroactivelyIncludeSemanticValue)
		updateConstraint(sourceSemanticValue) {
			definitions = definitions.append(writer.register())
		}
		check()
	}

	/**
	 * Record the fact that an [L2Instruction] has been emitted, which writes to
	 * the given [L2WriteOperand].
	 *
	 * @param writer
	 *   The operand that received the value.
	 * @param sourceSemanticValue
	 *   The [L2SemanticValue] that already holds the value.
	 */
	fun recordDefinitionForMakeImmutable(
		writer: L2WriteOperand<*>,
		sourceSemanticValue: L2SemanticValue)
	{
		val sourceSynonym = semanticValueToSynonym(sourceSemanticValue)
		// Erase all boxed registers that held this value.
		forgetBoxedRegistersFor(sourceSynonym)
		val sourceConstraint = Constraint(constraints[sourceSynonym]!!)
		assert(sourceConstraint.definitions
			.none { it.registerKind == BOXED_KIND }
		) { "This should not contain boxed registers" }
		// Rebuild the synonym based on the writer.
		forget(sourceSynonym)
		val targetSynonym = L2Synonym(writer.semanticValues())
		introduceSynonym(
			targetSynonym,
			writer.restriction().intersection(sourceConstraint.restriction)
				.withFlag(IMMUTABLE_FLAG))
		constraints[targetSynonym]!!.definitions =
			sourceConstraint.definitions.append(writer.register())
		check()
	}

	/**
	 * Given an [L2Register], find which [L2Synonym]s, if any, are
	 * mapped to it in this manifest.  The CFG does not have to be in SSA form.
	 *
	 * @param register
	 *   The [L2Register] to find in this manifest.
	 * @return
	 *   A [List] of [L2Synonym]s that are mapped to the given register within
	 *   this manifest.
	 */
	fun synonymsForRegister(register: L2Register): List<L2Synonym> =
		constraints.entries
			.filter { it.value.definitions.contains(register) }
			.map { it.key }

	/**
	 * Erase the information about all [L2SemanticValue]s that are part of the
	 * given [L2Synonym].
	 *
	 * @param synonym
	 *   The [L2Synonym] to forget.
	 */
	fun forget(synonym: L2Synonym)
	{
		semanticValueToSynonym.keys.removeAll(synonym.semanticValues())
		val previous = constraints.remove(synonym)
		assert(previous != null)
		check()
	}

	/**
	 * Create an [L2ReadBoxedOperand] for the [L2SemanticValue] of the earliest
	 * known boxed write for any semantic values in the same [L2Synonym] as the
	 * given semantic value.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to read as a boxed value.
	 * @return
	 *   An [L2ReadBoxedOperand] that reads the value.
	 */
	fun readBoxed(semanticValue: L2SemanticValue): L2ReadBoxedOperand
	{
		assert(semanticValue !is L2SemanticUnboxedInt)
		assert(semanticValue !is L2SemanticUnboxedFloat)
		val restriction = restrictionFor(semanticValue)
		assert(restriction.isBoxed)
		val register = getDefinition<L2Register>(semanticValue, BOXED_KIND)
		val allVisible = semanticValueToSynonym(semanticValue).semanticValues()
		val suitableSemanticValues = register.definitions()
			.map { def -> def.semanticValues().intersect(allVisible) }
			.reduce { a, b -> a.intersect(b) }
		assert(suitableSemanticValues.isNotEmpty())
		val suitableSemanticValue = when (semanticValue)
		{
			in suitableSemanticValues -> semanticValue
			else -> suitableSemanticValues.first()
		}
		assert(register.definitions().all { it.instructionHasBeenEmitted })
		return L2ReadBoxedOperand(suitableSemanticValue, restriction, this)
	}

	/**
	 * Create an [L2ReadIntOperand] for the [L2SemanticValue] of the earliest
	 * known unboxed int write for any semantic values in the same [L2Synonym]
	 * as the given semantic value.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to read as an unboxed int value.
	 * @return
	 *   An [L2ReadIntOperand] that reads the value.
	 */
	fun readInt(semanticValue: L2SemanticValue): L2ReadIntOperand
	{
		val restriction = restrictionFor(semanticValue)
		assert(restriction.isUnboxedInt)
		val register = getDefinition<L2Register>(semanticValue, INTEGER_KIND)
		val allVisible = semanticValueToSynonym(semanticValue).semanticValues()
		val suitableSemanticValues = register.definitions()
			.map { def -> def.semanticValues().intersect(allVisible) }
			.reduce { a, b -> a.intersect(b) }
		assert(suitableSemanticValues.isNotEmpty())
		val suitableSemanticValue = when (semanticValue)
		{
			in suitableSemanticValues -> semanticValue
			else -> suitableSemanticValues.first()
		}
		assert(register.definitions().all { it.instructionHasBeenEmitted })
		return L2ReadIntOperand(suitableSemanticValue, restriction, this)
	}

	/**
	 * Create an [L2ReadFloatOperand] for the [L2SemanticValue] of the earliest
	 * known unboxed float write for any semantic values in the same [L2Synonym]
	 * as the given semantic value.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to read as an unboxed int value.
	 * @return
	 *   An [L2ReadBoxedOperand] that reads from the synonym.
	 */
	fun readFloat(semanticValue: L2SemanticValue): L2ReadFloatOperand
	{
		val restriction = restrictionFor(semanticValue)
		assert(restriction.isUnboxedFloat)
		val register = getDefinition<L2Register>(semanticValue, FLOAT_KIND)
		val allVisible = semanticValueToSynonym(semanticValue).semanticValues()
		val suitableSemanticValues = register.definitions()
			.map { def -> def.semanticValues().intersect(allVisible) }
			.reduce { a, b -> a.intersect(b) }
		assert(suitableSemanticValues.isNotEmpty())
		val suitableSemanticValue = when (semanticValue)
		{
			in suitableSemanticValues -> semanticValue
			else -> suitableSemanticValues.first()
		}
		assert(register.definitions().all { it.instructionHasBeenEmitted })
		return L2ReadFloatOperand(suitableSemanticValue, restriction, this)
	}

	/**
	 * Populate the empty receiver with bindings from the incoming manifests.
	 * Only keep the bindings for [L2SemanticValue]s that occur in all incoming
	 * manifests.  Generate phi functions as needed on the provided
	 * [L2Generator]. The phi functions' source registers correspond
	 * positionally with the list of manifests.
	 *
	 * @param manifests
	 *   The list of manifests from which to populate the receiver.
	 * @param generator
	 *   The [L2Generator] on which to write any necessary phi  functions.
	 * @param generatePhis
	 *   Whether to automatically generate [L2_PHI_PSEUDO_OPERATION]s if there
	 *   are multiple incoming edges with different [L2Register]s associated
	 *   with the same [L2SemanticValue]s.
	 * @param forcePhis
	 *   Whether to force creation of every possible phi instruction at this
	 *   point, even if the values always come from the same source. This is
	 *   needed for loop heads, where the back-edges only show up after that
	 *   basic block has already produced instructions.  Must only be true if
	 *   [generatePhis] is also true.
	 * @param regenerator
	 *   The optional [L2Regenerator] used for translating postponed
	 *   instructions if needed.  If present (which it must be if there are any
	 *   postponed instructions), the regenerator's generator must be the same
	 *   as [generator].
	 */
	fun populateFromIntersection(
		manifests: List<L2ValueManifest>,
		generator: L2Generator,
		generatePhis: Boolean,
		forcePhis: Boolean,
		regenerator: L2Regenerator? = null)
	{
		assert(semanticValueToSynonym.isEmpty())
		assert(constraints.isEmpty())
		val manifestsSize = manifests.size
		if (manifestsSize == 0)
		{
			// Unreachable, or an entry point where no registers are set.
			return
		}
		if (manifestsSize == 1 && !forcePhis)
		{
			val soleManifest = manifests[0]
			semanticValueToSynonym.putAll(soleManifest.semanticValueToSynonym)
			soleManifest.constraints.mapValuesTo(constraints) {
				(_, constraint) -> Constraint(constraint)
			}
			soleManifest.postponedInstructions?.let { postponed ->
				postponedInstructions =
					postponed.mapValuesTo(enumMap()) { (_, submap) ->
						submap.mapValuesTo(mutableMapOf()) { (_, instructions) ->
							instructions.toMutableList()
						}
					}
			}
			return
		}
		val otherManifests = manifests.toMutableList()
		val firstManifest = otherManifests.removeAt(0)
		if (generatePhis)
		{
			// Find the semantic values which are present in all manifests.
			// Build phi instructions to move from the old definitions in each
			// input edge to a new definition (write) within the phi
			// instruction.  We expect to eliminate most of these by collapsing
			// moves during register coloring.
			val liveSemanticValues =
				firstManifest.liveOrPostponedSemanticValues()
			otherManifests.forEach {
				liveSemanticValues.retainAll(it.liveOrPostponedSemanticValues())
			}
			// For any live semantic values that are not all postponed from the
			// same original instruction (in the previous version of the control
			// flow graph), cause them to be generated in the incoming edges
			// (which will be in edge-split SSA).
			liveSemanticValues.forEach { semanticValue ->
				skip@for (kind in RegisterKind.all) {
					val keep = manifests.all { manifest ->
						when
						{
							manifest.hasSemanticValue(semanticValue)
								&& manifest.getDefinitions<L2Register>(
									semanticValue, kind).isNotEmpty() -> true
							else -> manifest.postponedInstructions
								?.get(kind)?.get(semanticValue) != null
						}
					}
					if (!keep) continue@skip
					// This combination of semantic value and register kind is
					// backed by either a register or a postponed instruction in
					// each incoming edge.
					val origins = manifests.map { manifest ->
						manifest.postponedInstructions
							?.get(kind)
							?.get(semanticValue)
					}
					if (origins.all { it == null })
					{
						// There are no postponed instruction for this purpose.
						continue@skip
					}
					if (!origins.contains(null) && origins.distinct().size == 1)
					{
						// The source of this semantic value (and kind) is the
						// same original instruction(s) for each incoming edge.
						// Simply include it to the new manifest as a postponed
						// value.
						origins[0]!!.forEach {
							recordPostponedSourceInstruction(
								it, kind, semanticValue)
						}
						continue@skip
					}
					// The same postponed instruction is *not* the source of the
					// value in each incoming edge.  Force the relevant
					// postponed instructions to generate just before each
					// incoming edge for which there isn't already a register
					// with the value.  The actual phi creation happens further
					// down. This is safe, because the graph is in edge-split
					// SSA in the presence of postponements.

					assert(regenerator != null)
					val edges = generator.currentBlock().predecessorEdges()
					origins.zip(edges).forEach { (sourceInstructions, edge) ->
						if (sourceInstructions != null)
						{
							regenerator!!.forcePostponedTranslationBeforeEdge(
								edge, kind, semanticValue)
						}
					}
				}
			}

			val phiMap =
				mutableMapOf<List<L2Synonym>, MutableList<L2SemanticValue>>()
			liveSemanticValues.forEach { sv ->
				// Be careful not to build phis for pass-through postponed
				// instructions (they've already been carried forward above).
				if (manifests.all { it.hasSemanticValue(sv) })
				{
					val key = manifests.map { it.semanticValueToSynonym(sv) }
					phiMap.getOrPut(key, ::mutableListOf).add(sv)
				}
			}
			// The phiMap is now populated, but we still need to figure out the
			// appropriate TypeRestrictions, including the available register
			// types.
			phiMap.values.forEach { relatedSemanticValues ->
				// Consume the first related semantic value to construct a phi
				// instruction, and populate the others via moves.
				val firstSemanticValue = relatedSemanticValues[0]
				val restriction = manifests
					.map { it.restrictionFor(firstSemanticValue) }
					.reduce(TypeRestriction::union)
				// Implicitly discard it if there were no common register kinds
				// between all the inputs.
				L2_PHI_PSEUDO_OPERATION.allPhiOperations.forEach { phiOp ->
					val kind = phiOp.moveOperation.kind
					if (restriction.hasFlag(kind.restrictionFlag))
					{
						// Generate a phi instruction of this kind.
						phiOp.generatePhi(
							generator,
							relatedSemanticValues,
							forcePhis,
							restriction.restrictingKindsTo(EnumSet.of(kind)),
							manifests)
					}
				}
			}
		}
		else
		{
			// The control flow graph is not in SSA form, which means a register
			// may have multiple definitions.  The control flow graph will only
			// contain semantic temporaries, one per transformed register,
			// although moves may attach a register to more than one temporary.
			// Using the registers as keys, figure out which semantic temps are
			// represented by those registers in all incoming edges, and limit
			// the manifest to that intersection, setting the restriction to the
			// union of the inputs.
			assert(manifests.all { it.postponedInstructions == null })
			val liveRegisters = mutableMapOf<
				L2Register,
				Pair<MutableSet<L2SemanticValue>, Mutable<TypeRestriction>>>()
			firstManifest.constraints.forEach { (synonym, constraint) ->
				constraint.definitions.forEach { register ->
					liveRegisters[register] = Pair(
						synonym.semanticValues().toMutableSet(),
						Mutable(constraint.restriction))
				}
			}
			otherManifests.forEach { manifest ->
				val registersToKeep = mutableSetOf<L2Register>()
				manifest.constraints.forEach { (synonym, constraint) ->
					constraint.definitions.forEach { register ->
						registersToKeep.add(register)
						liveRegisters[register]?.let {
							(semanticValues, mutableRestriction) ->
								semanticValues.retainAll(
									synonym.semanticValues())
								mutableRestriction.value =
									mutableRestriction.value.union(
										constraint.restriction)
								if (semanticValues.isEmpty())
								{
									// The register was present in all incoming
									// edges, but without a semantic value
									// common to all of them.  Exclude it from
									// the manifest.
									liveRegisters.remove(register)
									//TODO: This should not be possible,since
									// the register definitions *must* use the
									// same semantic temporary, and nothing can
									// remove that.
								}
						}
					}
				}
				liveRegisters.keys.retainAll(registersToKeep)
			}
			// We now have just the registers that are live in all incoming
			// edges, and *only* those that have at least one semantic value in
			// common.  It should be safe to create maximally large synonyms
			// from the unions of overlapping sets of semantic values, since the
			// registers holding these values must all be equal anyhow.
			liveRegisters.forEach { register, (semanticValues, restriction) ->
				val newSemanticValues = mutableSetOf<L2SemanticValue>()
				val existingSynonyms = mutableSetOf<L2Synonym>()
				semanticValues.forEach { sv ->
					val syn = semanticValueToSynonym[sv]
					if (syn == null) newSemanticValues.add(sv)
					else existingSynonyms.add(syn)
				}
				if (newSemanticValues.isNotEmpty())
				{
					val syn = L2Synonym(newSemanticValues)
					introduceSynonym(syn, restriction.value)
					existingSynonyms.add(syn)
				}
				val synonymsIterator = existingSynonyms.iterator()
				val sampleSynonym = synonymsIterator.next()
				val sampleSemanticValue = sampleSynonym.pickSemanticValue()
				synonymsIterator.forEachRemaining { nextSynonym ->
					mergeExistingSemanticValues(
						sampleSemanticValue,
						nextSynonym.pickSemanticValue())
				}
				// All the relevant synonyms and semantic values are merged.
				updateConstraint(sampleSemanticValue) {
					definitions = definitions.append(register)
				}
			}
		}
		check()
	}

	/**
	 * Transform this manifest by mapping its [L2SemanticValue]s.
	 *
	 * @param semanticValueTransformer
	 *   The transformation for [L2SemanticValue]s.
	 * @return
	 *   The transformed manifest.
	 */
	fun transform(
		semanticValueTransformer: (L2SemanticValue) -> L2SemanticValue
	): L2ValueManifest
	{
		val newManifest = L2ValueManifest()
		for (oldSynonym in synonymsArray())
		{
			val restriction = restrictionFor(oldSynonym.pickSemanticValue())
			newManifest.introduceSynonym(
				oldSynonym.transform(semanticValueTransformer),
				restriction)
		}
		newManifest.check()
		return newManifest
	}

	/**
	 * Produce all live definitions of the synonym.
	 *
	 * @param synonym
	 *   The [L2Synonym] for which to look up all visible [L2WriteOperand]s
	 *   which supply the value for that synonym.
	 * @return
	 *   A sequence of [L2Register]s that have visible definitions of the
	 *   synonym.
	 */
	fun definitionsForDescribing(
		synonym: L2Synonym
	): Iterable<L2Register> = constraints[synonym]!!.definitions

	/**
	 * Retain as definitions only those [L2Register]s that are in the given
	 * [Set], removing the rest.
	 *
	 * @param registersToRetain
	 *   The [L2Register]s that can be retained by the list of definitions as
	 *   all others are removed.
	 * @return
	 *   Whether the manifest was modified.
	 */
	fun retainRegisters(registersToRetain: Set<L2Register>): Boolean
	{
		var changed = false
		// Iterate over a copy of the map, so we can remove from it.
		for ((synonym, constraint) in constraints.entries.toList())
		{
			val definitionList = constraint.definitions.toMutableList()
			if (definitionList.retainAll(registersToRetain))
			{
				changed = true
				if (definitionList.isEmpty())
				{
					// Remove this synonym and any semantic values within it.
					constraints.remove(synonym)
					semanticValueToSynonym.keys.removeAll(
						synonym.semanticValues())
				}
				else
				{
					constraint.definitions = definitionList
					// Check if the last register of a RegisterKind has
					// disappeared.
					val oldKinds = constraint.restriction.kinds()
					val newKinds = EnumSet.noneOf(RegisterKind::class.java)
					definitionList.mapTo(newKinds) { it.registerKind }
					if (oldKinds != newKinds)
					{
						constraint.restriction =
							constraint.restriction.restrictingKindsTo(newKinds)
					}
				}
			}
		}
		check()
		return changed
	}

	/**
	 * Remove all definitions related to this [L2WriteOperand]'s
	 * [L2SemanticValue]s, then add this write operand as the sole definition in
	 * this manifest.
	 *
	 * @param writeOperand
	 *   The [L2WriteOperand] to add and replace all other relevant definitions.
	 */
	fun replaceDefinitions(writeOperand: L2WriteOperand<*>)
	{
		updateConstraint(writeOperand.pickSemanticValue()) {
			definitions = singletonList(writeOperand.register())
		}
		check()
	}

	/**
	 * Retain information only about the [L2SemanticValue]s that are present in
	 * the given [Set], removing the rest.
	 *
	 * @param semanticValuesToRetain
	 *   The [L2SemanticValue]s that can be retained in the manifest.
	 */
	fun retainSemanticValues(semanticValuesToRetain: Set<L2SemanticValue>)
	{
		for (syn in synonymsArray())
		{
			val values = syn.semanticValues()
			val toKeep = values.toMutableSet()
			val keepAll = toKeep.retainAll(semanticValuesToRetain)
			if (!keepAll)
			{
				val toRemove = values.toMutableSet()
				toRemove.removeAll(semanticValuesToRetain)
				val constraint = constraints.remove(syn)!!
				semanticValueToSynonym.keys.removeAll(toRemove)
				if (toKeep.isNotEmpty())
				{
					val newSynonym = L2Synonym(toKeep)
					toKeep.forEach { semanticValueToSynonym[it] = newSynonym }
					constraints[newSynonym] = constraint
				}
			}
		}
		check()
	}

	/**
	 * Forget only the [BOXED_KIND] register definitions for this synonym.  Keep
	 * the synonym around in all circumstances, even if there are no remaining
	 * definitions.  Also, do not alter the synonym's restriction.
	 *
	 * This mechanism is used by [L2_MAKE_IMMUTABLE] to ensure registers holding
	 * the mutable form aren't still accessible, which under code motion could
	 * lead to the makeImmutable being bypassed, or a value being incorrectly
	 * modified in place prior to becoming immutable.
	 *
	 * @param synonym
	 *   The [L2Synonym] for which to forget all boxed definitions.
	 */
	fun forgetBoxedRegistersFor(synonym: L2Synonym)
	{
		val constraint = constraints[synonym]
			?: throw RuntimeException("Synonym not found")
		// Even if there are no unboxed definitions, keep the synonym, but make
		// the boxed definitions inaccessible.
		constraint.definitions = constraint.definitions.filter {
			it.registerKind !== BOXED_KIND
		}
		check()
	}

	companion object
	{
		/** Perform deep, slow checks every time a manifest changes. */
		var deepManifestDebugCheck = false
	}
}
