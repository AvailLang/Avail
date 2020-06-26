/*
 * L2ValueManifest.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.optimizer

import com.avail.descriptor.types.A_Type
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import com.avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import com.avail.interpreter.levelTwo.operand.L2ReadOperand
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind
import com.avail.optimizer.values.L2SemanticPrimitiveInvocation
import com.avail.optimizer.values.L2SemanticValue
import com.avail.utility.Casts
import java.util.*
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
	/** The synonyms keyed by semantic values.  */
	private val semanticValueToSynonym =
		mutableMapOf<L2SemanticValue, L2Synonym>()

	/**
	 * The [TypeRestriction]s currently in force on this manifest's [L2Synonym]s.
	 */
	private val synonymRestrictions =
		mutableMapOf<L2Synonym, TypeRestriction>()

	/**
	 * A map from each [L2Synonym] to a [List] of [L2Register]s that are in
	 * [L2WriteOperand]s of [L2Instruction]s that act as definitions of the
	 * [L2Synonym]'s [L2SemanticValue]s.
	 */
	private val definitions =
		mutableMapOf<L2Synonym, MutableList<L2Register>>()

	/** Create a new, empty manifest.  */
	constructor()

	/**
	 * Copy an existing manifest.
	 *
	 * @param originalManifest
	 *   The original manifest.
	 */
	constructor(originalManifest: L2ValueManifest)
	{
		semanticValueToSynonym.putAll(originalManifest.semanticValueToSynonym)
		synonymRestrictions.putAll(originalManifest.synonymRestrictions)
		originalManifest.definitions.forEach {
			(synonym: L2Synonym, list: List<L2Register>) ->
			definitions[synonym] = list.toMutableList()
		}
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
	): L2Synonym? =
			semanticValueToSynonym[semanticValue] ?: elseSupplier()


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
		synonymRestrictions[freshSynonym] = restriction
		definitions[freshSynonym] = mutableListOf()
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
	fun extendSynonym(existingSynonym: L2Synonym, semanticValue: L2SemanticValue)
	{
		assert(!hasSemanticValue(semanticValue))
		val semanticValues =
			existingSynonym.semanticValues().toMutableSet()
		semanticValues.add(semanticValue)
		val merged = L2Synonym(semanticValues)
		for (sv in semanticValues)
		{
			semanticValueToSynonym[sv] = merged
		}
		synonymRestrictions[merged] =
			synonymRestrictions.remove(existingSynonym)!!
		definitions[merged] = definitions.remove(existingSynonym)!!
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
					val invocationSynonym =
						semanticValueToSynonym[invocation]!!
					if (primitiveSynonyms.isNotEmpty()
						&& !primitiveSynonyms.contains(invocationSynonym))
					{
						val sampleSynonym =
							primitiveSynonyms.iterator().next()
						val sampleInvocation =
							sampleSynonym.pickSemanticValue()
						followupMerges.add(
							Pair(invocation, sampleInvocation))
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
		val semanticValues =
			synonym1.semanticValues().toMutableSet()
		semanticValues.addAll(synonym2.semanticValues())
		val merged = L2Synonym(semanticValues)
		semanticValues.forEach { semanticValueToSynonym[it] = merged }
		val restriction =
			synonymRestrictions.remove(synonym1)!!.intersection(
				synonymRestrictions.remove(synonym2)!!)
		synonymRestrictions[merged] = restriction

		// Update the definitions map.  Just concatenate the input synonyms'
		// lists, as this essentially preserves earliest definition order.
		val list =
			definitions.remove(synonym1)?.toMutableList() ?: mutableListOf()
		list.addAll(definitions.remove(synonym2)!!)
		definitions[merged] = list
		return true
	}

	/**
	 * Retrieve the oldest definition of the given [L2SemanticValue] or an
	 * equivalent, but having the given [RegisterKind].
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
		val registers: List<L2Register> =
			definitions[semanticValueToSynonym(semanticValue)]!!
		for (register in registers)
		{
			if (register.registerKind() === registerKind)
			{
				return Casts.cast(register)
			}
		}
		throw RuntimeException("Appropriate register for kind not found")
	}

	/**
	 * Retrieve all [L2Register]s known to contain the given [L2SemanticValue],
	 * but having the given [RegisterKind].
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
		registerKind: RegisterKind): List<R> =
			definitions[semanticValueToSynonym(semanticValue)]!!
				.filter { r: L2Register -> r.registerKind() === registerKind }
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
		synonymRestrictions[semanticValueToSynonym(semanticValue)] = restriction
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
		val synonym = semanticValueToSynonym(semanticValue)
		synonymRestrictions[synonym] =
			synonymRestrictions[synonym]!!.intersectionWithType(type)
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
	fun subtractType(semanticValue: L2SemanticValue, type: A_Type)
	{
		val synonym = semanticValueToSynonym(semanticValue)
		synonymRestrictions[synonym] =
			synonymRestrictions[synonym]!!.minusType(type)
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
	fun restrictionFor(semanticValue: L2SemanticValue): TypeRestriction =
		synonymRestrictions[semanticValueToSynonym(semanticValue)]!!

	/**
	 * Answer an arbitrarily ordered array of the [L2Synonym]s in this manifest.
	 *
	 * @return
	 *   An array of [L2Synonym]s.
	 */
	fun synonymsArray(): Array<L2Synonym> =
		synonymRestrictions.keys.toTypedArray()

	/**
	 * Answer a [Set] of all [L2Register]s available in this manifest.
	 *
	 * @return
	 *   A [Set] of [L2Register]s.
	 */
	fun allRegisters(): Set<L2Register>
	{
		val registers: MutableSet<L2Register> = HashSet()
		definitions.values.forEach { registers.addAll(it) }
		return registers
	}

	/**
	 * Remove all information about registers and semantic values from this
	 * manifest.
	 */
	fun clear()
	{
		semanticValueToSynonym.clear()
		synonymRestrictions.clear()
		definitions.clear()
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
		assert(writer.instructionHasBeenEmitted())
		for (semanticValue in writer.semanticValues())
		{
			if (hasSemanticValue(semanticValue))
			{
				// This is a new RegisterKind for an existing semantic value.
				val synonym = semanticValueToSynonym(semanticValue)
				val existingRestriction =
					restrictionFor(semanticValue)
				val writerRestrictionFlag =
					writer.registerKind().restrictionFlag
				// The restriction *might* know about this kind, if there were
				// multiple kinds that led to multiple phi instructions for the
				// same synonym.
				synonymRestrictions[synonym] =
					existingRestriction.withFlag(writerRestrictionFlag)
				definitions[synonym]!!.add(writer.register())
			}
			else
			{
				// This is a new semantic value.
				val synonym = L2Synonym(setOf(semanticValue))
				semanticValueToSynonym[semanticValue] = synonym
				synonymRestrictions[synonym] = writer.restriction()
				definitions[synonym] = mutableListOf(writer.register())
			}
		}
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
	fun recordDefinitionForMove(
		writer: L2WriteOperand<*>,
		sourceSemanticValue: L2SemanticValue)
	{
		assert(writer.instructionHasBeenEmitted())
		for (semanticValue in writer.semanticValues())
		{
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
				val oldSynonym =
					semanticValueToSynonym(sourceSemanticValue)
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
		val registers =
			definitions[semanticValueToSynonym(sourceSemanticValue)]!!
		registers.add(writer.register())
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
		val sourceSynonym =
			semanticValueToSynonym(sourceSemanticValue)

		// Make inaccessible all places holding the mutable boxed value.
		forgetBoxedRegistersFor(sourceSynonym)

		// Merge the new write target into the synonym.
		val pickSemanticValue = writer.pickSemanticValue()
		extendSynonym(sourceSynonym, pickSemanticValue)
		val newSynonym = semanticValueToSynonym(pickSemanticValue)
		definitions[newSynonym]!!.add(writer.register())
		setRestriction(pickSemanticValue, writer.restriction())
	}

	/**
	 * Record the fact that an [L2Instruction] has been emitted, which writes to
	 * the given [L2WriteOperand].  The write is for a [RegisterKind] which has
	 * not been written yet for this [L2SemanticValue], although there are
	 * definitions for other kinds for the semantic value.
	 *
	 * @param writer
	 *   The operand that received the value.
	 */
	private fun recordDefinitionForNewKind(writer: L2WriteOperand<*>)
	{
		assert(writer.instructionHasBeenEmitted())
		val semanticValue = writer.pickSemanticValue()
		assert(hasSemanticValue(semanticValue))
		val oldSynonym = semanticValueToSynonym(semanticValue)
		definitions[oldSynonym]!!.add(writer.register())
		setRestriction(
			semanticValue,
			restrictionFor(semanticValue).intersection(writer.restriction()))
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
		definitions.entries
			.filter { it.value.contains(register) }
			.map { it.key }

	/**
	 * Edit this manifest to include entries for the given [L2Register]. It
	 * should be associated with the given [L2SemanticValue]s, creating,
	 * extending, or merging [L2Synonym] as needed.  Also set its
	 * [TypeRestriction].
	 *
	 * @param register
	 *   The [L2Register] to make available in this manifest.
	 * @param semanticValues
	 *   The [L2SemanticValue]s that the register fulfills.
	 * @param restriction
	 *   The [TypeRestriction] that bounds the register here.
	 */
	fun recordSourceInformation(
		register: L2Register,
		semanticValues: Set<L2SemanticValue>,
		restriction: TypeRestriction)
	{
		val connectedSynonyms = mutableSetOf<L2Synonym>()
		val newSemanticValues =
			mutableSetOf<L2SemanticValue>()
		for (semanticValue in semanticValues)
		{
			if (hasSemanticValue(semanticValue))
			{
				setRestriction(semanticValue, restriction)
				connectedSynonyms.add(semanticValueToSynonym(semanticValue))
			}
			else
			{
				newSemanticValues.add(semanticValue)
			}
		}
		if (newSemanticValues.isNotEmpty())
		{
			val newSynonym = L2Synonym(newSemanticValues)
			for (newSemanticValue in newSemanticValues)
			{
				semanticValueToSynonym[newSemanticValue] = newSynonym
				synonymRestrictions[newSynonym] = restriction
				definitions[newSynonym] = mutableListOf(register)
			}
			connectedSynonyms.add(newSynonym)
		}
		else
		{
			definitions[connectedSynonyms.iterator().next()]!!.add(register)
		}
		// Merge the connectedSynonyms together.
		var mergedPick: L2SemanticValue? = null
		for (syn in connectedSynonyms)
		{
			if (mergedPick === null)
			{
				mergedPick = syn.pickSemanticValue()
			}
			else
			{
				mergeExistingSemanticValues(
					mergedPick, syn.pickSemanticValue())
			}
		}
	}

	/**
	 * Erase the information about all [L2SemanticValue]s that are part of the
	 * given [L2Synonym].
	 *
	 * @param synonym
	 *   The [L2Synonym] to forget.
	 */
	fun forget(synonym: L2Synonym)
	{
		assert(synonymRestrictions.containsKey(synonym))
		semanticValueToSynonym.keys.removeAll(synonym.semanticValues())
		synonymRestrictions.remove(synonym)
		definitions.remove(synonym)
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
		val restriction = restrictionFor(semanticValue)
		assert(restriction.isBoxed)
		val register =
			getDefinition<L2Register>(semanticValue, RegisterKind.BOXED)
		assert(register.definitions().all { it.instructionHasBeenEmitted() })
		return L2ReadBoxedOperand(semanticValue, restriction, this)
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
		val register =
			getDefinition<L2Register>(semanticValue, RegisterKind.INTEGER)
		assert(register.definitions().all { it.instructionHasBeenEmitted() })
		return L2ReadIntOperand(semanticValue, restriction, this)
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
		val register =
			getDefinition<L2Register>(semanticValue, RegisterKind.FLOAT)
		assert(register.definitions().all { it.instructionHasBeenEmitted() })
		return L2ReadFloatOperand(semanticValue, restriction, this)
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
	 * @param forcePhis
	 *   Whether to force creation of every possible phi instruction at this
	 *   point, even if the values always come from the same source. This is
	 *   needed for loop heads, where the back-edges only show up after that
	 *   basic block has already produced instructions.
	 * @param suppressAllPhis
	 *   Whether to suppress creation of phis.  This is only used during
	 *   splicing of new code into an existing graph via
	 *   [L2Generator.replaceInstructionByGenerating], which takes
	 *   responsibility for replaying existing phis to reconstitute the
	 *   `L2ValueManifest` at the position that the new code is being inserted.
	 *   This flag and `forcePhis` are mutually exclusive.
	 */
	fun populateFromIntersection(
		manifests: List<L2ValueManifest>,
		generator: L2Generator,
		forcePhis: Boolean,
		suppressAllPhis: Boolean)
	{
		assert(semanticValueToSynonym.isEmpty())
		assert(synonymRestrictions.isEmpty())
		assert(definitions.isEmpty())
		assert(!forcePhis || !suppressAllPhis)
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
			synonymRestrictions.putAll(soleManifest.synonymRestrictions)
			soleManifest.definitions.forEach { (syn, list) ->
				definitions[syn] = list.toMutableList()
			}
			return
		}
		val otherManifests = manifests.toMutableList()
		val firstManifest: L2ValueManifest = otherManifests.removeAt(0)
		// Find the semantic values which are present in all manifests.  Build
		// phi instructions to move from the old definitions in each input edge
		// to a new definition (write) within the phi instruction.  We expect to
		// eliminate most of these by collapsing moves during register coloring.
		val liveSemanticValues =
			firstManifest.semanticValueToSynonym.keys.toMutableSet()
		otherManifests.forEach {
			liveSemanticValues.retainAll(it.semanticValueToSynonym.keys)
		}
		val phiMap =
			mutableMapOf<List<L2Synonym>, MutableList<L2SemanticValue>>()
		liveSemanticValues.forEach { sv->
			phiMap.computeIfAbsent(manifests.map {
				it.semanticValueToSynonym(sv)
			}) { mutableListOf() }.add(sv)
		}
		// The phiMap is now populated, but we still need to figure out the
		// appropriate TypeRestrictions, including the available register types.
		phiMap.values.forEach { relatedSemanticValues ->
			// Consume the first related semantic value to construct a phi
			// instruction, and populate the others via moves.
			val firstSemanticValue = relatedSemanticValues[0]
			val restriction = manifests
				.map { it.restrictionFor(firstSemanticValue) }
				.reduce { obj, other -> obj.union(other) }
			// Implicitly discard it if there were no common register kinds
			// between all the inputs.
			if (restriction.isBoxed)
			{
				// Generate a boxed phi.
				val sources =
					manifests.map { it.readBoxed(firstSemanticValue) }

				generatePhi(
					generator,
					relatedSemanticValues,
					forcePhis,
					restriction.restrictingKindsTo(
						RegisterKind.BOXED.restrictionFlag.mask),
					L2ReadBoxedVectorOperand(sources),
					L2_PHI_PSEUDO_OPERATION.boxed)
					{ semanticValue, res ->
						generator.boxedWrite(semanticValue, res)
					}
			}
			if (restriction.isUnboxedInt)
			{
				// Generate an unboxed int phi.
				val sources =
					manifests.map { it.readInt(firstSemanticValue) }
				generatePhi(
					generator,
					relatedSemanticValues,
					forcePhis,
					restriction.restrictingKindsTo(
						RegisterKind.INTEGER.restrictionFlag.mask),
					L2ReadIntVectorOperand(sources),
					L2_PHI_PSEUDO_OPERATION.unboxedInt)
					{ semanticValue, res ->
						generator.intWrite(semanticValue, res)
					}
			}
			if (restriction.isUnboxedFloat)
			{
				// Generate an unboxed float phi.
				val sources =
					manifests.map { it.readFloat(firstSemanticValue) }
				generatePhi(
					generator,
					relatedSemanticValues,
					forcePhis,
					restriction.restrictingKindsTo(
						RegisterKind.FLOAT.restrictionFlag.mask),
					L2ReadFloatVectorOperand(sources),
					L2_PHI_PSEUDO_OPERATION.unboxedFloat)
					{ semanticValue, res ->
						generator.floatWrite(semanticValue, res)
					}
			}
		}
	}

	/**
	 * Generate an [L2_PHI_PSEUDO_OPERATION] and any additional moves to ensure
	 * the given set of related [L2SemanticValue]s are populated with values
	 * from the given sources.
	 *
	 * @param generator
	 *   The [L2Generator] on which to write instructions.
	 * @param relatedSemanticValues
	 *   The [L2SemanticValue]s that should constitute a synonym in the current
	 *   manifest, due to them being mutually connected to a synonym in each
	 *   predecessor manifest.  The synonyms may differ in the predecessor
	 *   manifests, but within each manifest there must be a synonym for that
	 *   manifest that contains all of these semantic values.
	 * @param forcePhiCreation
	 *   Whether to force creation of a phi instruction, even if all incoming
	 *   sources of the value are the same.
	 * @param restriction
	 *   The [TypeRestriction] to bound the synonym.
	 * @param sources
	 *   An [L2ReadVectorOperand] that reads from each
	 * @param phiOperation
	 *   The [L2_PHI_PSEUDO_OPERATION] instruction to generate.
	 * @param createWriter
	 *   A lambda taking an [L2SemanticValue] and a [TypeRestriction], and
	 *   producing a suitable [L2WriteOperand].
	 * @param <R>
	 *   The kind of [L2Register]s to merge.
	 * @param <RR>
	 *   The [L2ReadOperand] type supplying each value.
	 * @param <WR>
	 *   The kind of [L2WriteOperand] used to write the result.
	 * @param <RV>
	 *   The [L2ReadVectorOperand] supplying values.
	 */
	private fun <R : L2Register, RR : L2ReadOperand<R>, WR : L2WriteOperand<R>,
			RV : L2ReadVectorOperand<RR, R>>
	generatePhi(
		generator: L2Generator,
		relatedSemanticValues: Collection<L2SemanticValue>,
		forcePhiCreation: Boolean,
		restriction: TypeRestriction,
		sources: RV,
		phiOperation: L2_PHI_PSEUDO_OPERATION<R, RR, WR>,
		createWriter: (L2SemanticValue, TypeRestriction) -> L2WriteOperand<R>)
	{
		val distinctRegisters =
			sources.elements().map { it.register() }.distinct()

		val pickSemanticValue: L2SemanticValue
		val otherSemanticValues =
			relatedSemanticValues.toMutableList()
		if (!forcePhiCreation
			&& distinctRegisters.size == 1 && distinctRegisters[0].definitions()
				.any { wr ->
					wr.semanticValues().any { relatedSemanticValues.contains(it) }
				})
		{
			// All paths get the value from a common register, and at least one
			// definition for that register includes a semantic value that's in
			// relatedSemanticValues.
			val distinctRegister = distinctRegisters[0]
			// Under the assumption that all the writes of this variable had at
			// least some common purpose, find that purpose, in the form of the
			// set of semantic values that all definitions wrote, and give it to
			// the new synonym.
			val intersectedSemanticValues =
				relatedSemanticValues.toMutableSet()
			for (write in distinctRegister.definitions())
			{
				intersectedSemanticValues.retainAll(write.semanticValues())
			}
			pickSemanticValue = intersectedSemanticValues.iterator().next()
			if (semanticValueToSynonym.containsKey(pickSemanticValue))
			{
				// Already present due to another RegisterKind.  Just make sure
				// the common register shows up as a definition.
				recordDefinitionForNewKind(
					distinctRegister.definitions().iterator().next())
				return
			}
			// This is the first RegisterKind for this collection of related
			// semantic values.
			introduceSynonym(
				L2Synonym(intersectedSemanticValues), restriction)
			val list = definitions.computeIfAbsent(
				semanticValueToSynonym(pickSemanticValue)
			) { mutableListOf() }
			list.add(distinctRegister)
			otherSemanticValues.removeAll(intersectedSemanticValues)
		}
		else
		{
			pickSemanticValue = relatedSemanticValues.iterator().next()
			generator.addInstruction(
				phiOperation,
				sources,
				createWriter(pickSemanticValue, restriction))
			otherSemanticValues.remove(pickSemanticValue)
		}
		for (otherSemanticValue in otherSemanticValues)
		{
			// The other semantic value might already be in this synonym due to
			// a previous phi instruction for a different register kind.  If so,
			// don't try to add it again with another move.
			if (!generator.currentManifest.hasSemanticValue(otherSemanticValue))
			{
				generator.moveRegister(
					phiOperation.moveOperation,
					pickSemanticValue,
					otherSemanticValue)
			}
		}
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
		return newManifest
	}

	/**
	 * Produce all live definitions of the synonym having this semantic value.
	 *
	 * @param pickSemanticValue
	 *   The [L2SemanticValue] used to look up an [L2Synonym], which is then
	 *   used to look up all visible [L2WriteOperand]s that supply the value for
	 *   the synonym.
	 * @return
	 *   A sequence of [L2Register]s that have visible definitions of the
	 *   semantic value's synonym.
	 */
	fun definitionsForDescribing(
		pickSemanticValue: L2SemanticValue): Iterable<L2Register> =
			definitions[semanticValueToSynonym[pickSemanticValue]]!!

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
		for ((synonym, definitionList)
			in definitions.entries.toList())
		{
			if (definitionList.retainAll(registersToRetain))
			{
				changed = true
				if (definitionList.isEmpty())
				{
					// Remove this synonym and any semantic values within it.
					definitions.remove(synonym)
					synonymRestrictions.remove(synonym)
					semanticValueToSynonym.keys.removeAll(
						synonym.semanticValues())
				}
			}
		}
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
		val synonym =
			semanticValueToSynonym[writeOperand.pickSemanticValue()]
		val definitionList = definitions[synonym]!!
		definitionList.clear()
		definitionList.add(writeOperand.register())
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
			val values = syn.semanticValues().toSet()
			val toKeep = values.toMutableSet()
			val keepAll = toKeep.retainAll(semanticValuesToRetain)
			if (!keepAll)
			{
				val toRemove = values.toMutableSet()
				toRemove.removeAll(semanticValuesToRetain)
				val restriction =
					synonymRestrictions.remove(syn)!!
				val defs = definitions.remove(syn)!!
				semanticValueToSynonym.keys.removeAll(toRemove)
				if (toKeep.isNotEmpty())
				{
					val newSynonym = L2Synonym(toKeep)
					toKeep.forEach { semanticValueToSynonym[it] = newSynonym }
					synonymRestrictions[newSynonym] = restriction
					definitions[newSynonym] = defs
				}
			}
		}
	}

	/**
	 * Forget the given registers from my definitions.  If all registers for a
	 * synonym are removed, remove the entire synonym.  If all registers of a
	 * particular [RegisterKind] are removed from a synonym, remove that kind
	 * from its [TypeRestriction].
	 *
	 * @param registersToForget
	 *   The [Set] of [L2Register]s to remove knowledge about from this manifest.
	 * @return
	 *   Whether any registers were removed.
	 */
	fun forgetRegisters(registersToForget: Set<L2Register>): Boolean
	{
		var anyChanged = false
		// Iterate over a copy, because we're making changes.
		for ((synonym, registerList) in definitions.entries.toList())
		{
			if (registerList.removeAll(registersToForget))
			{
				anyChanged = true
				if (registerList.isEmpty())
				{
					forget(synonym)
				}
				else
				{
					val remainingKinds =
						EnumSet.noneOf(RegisterKind::class.java)
					remainingKinds.addAll(registerList.map { it.registerKind() })
					var restriction = synonymRestrictions[synonym]
					if (restriction!!.kinds() != remainingKinds)
					{
						for (unavailableKind in EnumSet.complementOf(remainingKinds))
						{
							restriction = restriction!!.withoutFlag(
								unavailableKind.restrictionFlag)
						}
					}
					synonymRestrictions[synonym] = restriction!!
				}
			}
		}
		return anyChanged
	}

	/**
	 * Forget only the [RegisterKind.BOXED] register definitions for this
	 * synonym.  Keep the synonym around in all circumstances, even if there are
	 * no remaining definitions.  Also, do not alter the synonym's restriction.
	 *
	 * This mechanism is used by [L2_MAKE_IMMUTABLE] to ensure registers holding
	 * the mutable form aren't still accessible, which under code motion could
	 * lead to the makeImmutable being bypassed, or a value being incorrectly
	 * modified in place prior to becoming immutable.
	 *
	 * @param synonym
	 *   The [L2Synonym] for which to forget all boxed definitions.
	 */
	private fun forgetBoxedRegistersFor(synonym: L2Synonym)
	{
		assert(definitions.containsKey(synonym))
		val defs = definitions[synonym]!!.filter {
			it.registerKind() !== RegisterKind.BOXED }.toMutableList()
		// There was at least one unboxed definition.  Keep the synonym,
		// but make the boxed definitions inaccessible, while also removing
		// the boxed flag from the restriction.
		definitions[synonym] = defs
	}
}
