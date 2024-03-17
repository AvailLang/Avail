/*
 * L2ValueManifest.kt
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
 * POSSIBILITY OF SUCH DAMAGE."
 */
package avail.optimizer

import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instanceTag
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.rangeIncludesLong
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i31
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.DOUBLE
import avail.descriptor.types.TypeTag
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.bottomRestriction
import avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2ValueManifest.Constraint
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticConstant
import avail.optimizer.values.L2SemanticExtractedTag
import avail.optimizer.values.L2SemanticObjectVariantId
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.utility.Mutable
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.cast
import avail.utility.isNullOr
import avail.utility.notNullAnd
import java.util.Collections.singletonList
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
 * also tracks [L2Register]s that hold values representing which
 * [L2SemanticValue]s, specifically using [L2Synonym]s as the binding mechanism.
 *
 * The basic structure of the manifest is two maps: One from semantic value to
 * synonym (kept exactly in sync with each synonym's membership), and  a mapping
 * from synonym to [Constraint], which contains a [TypeRestriction] that must
 * hold for the value, and the [L2Register]s that hold that value.  The
 * [Constraint]s are mutable, and are copied when cloning a manifest.
 *
 * During code [generation][L2Generator] or [regeneration][L2Regenerator],
 * control flow merges usually create [phi][L2_PHI_PSEUDO_OPERATION]
 * instructions in the destination block, partitioning incoming synonyms so that
 * only semantic values that are in the same synonyms in *all* incoming edges
 * will be in the same synonym at the destination.  The restriction for a
 * semantic value is the union of the incoming restrictions for that value.  The
 * set of registers is the intersection, which leads to phi creation only if it
 * is empty (otherwise there's already a common register available along all
 * edges, and a phi is unnecessary).  Phi instructions are eventually replaced
 * with moves to a common register at the end of each predecessor block.
 *
 * During some optimization passes, a manifest can also track postponed
 * instructions that have no side effects, allowing them to propagate to later
 * points in the code, or perhaps only to places where the values they produce
 * are actually needed.  When we're lucky, those places are along reification
 * off-ramps, which are relatively rarely reached.  Note that the two maps are
 * kept up-to-date with the effects of the postponed instructions, even though
 * the associated [Constraint] might list no registers as actually holding that
 * value.  Attempting to use such a synonym as a source will cause the needed
 * postponed instructions to be emitted (recursively, since those instructions
 * might require values from other postponed instructions), and removed from
 * the postponed map (it maps from semantic value to an ordered list of
 * instructions).
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
	class Constraint<K: RegisterKind<K>>(
		theDefinitions: List<L2Register<K>>,
		var restriction: TypeRestriction)
	{
		var definitions: List<L2Register<K>> = theDefinitions
			set(value)
			{
				assert(value.size == value.toSet().size)
				field = value
			}

		/**
		 * A copy constructor, producing an instance that will not change when
		 * the original instance changes.
		 */
		constructor(original: Constraint<K>) :
			this(original.definitions, original.restriction)

		override fun toString(): String = buildString {
			when
			{
				definitions.isEmpty() -> append("🛑POSTPONED")
				else -> definitions.joinTo(this)
			}
			append(": ")
			append(restriction)
		}

		/**
		 * Answer `true` iff this constraint is impossible to satisfy with any
		 * value.
		 */
		val isImpossible get() = restriction.isImpossible
	}

	/** The synonyms keyed by semantic values. */
	private val semanticValueToSynonym:
		MutableMap<L2SemanticValue<*>, L2Synonym<*>>

	/**
	 * A map from each [L2Synonym] to the [Constraint] that tracks the current
	 * [TypeRestriction] and immutable list of [L2Register]s that hold the value
	 * represented by that synonym.
	 */
	private val constraints: MutableMap<L2Synonym<*>, Constraint<*>>

	/**
	 * A map from [L2SemanticValue] to source [L2Instruction]s from a previous
	 * [L2ControlFlowGraph], which can be translated as needed by an
	 * [L2Regenerator], as part of the instruction postponement optimization
	 * (perfect redundancy elimination). The same source instruction is stored
	 * under each semantic value that was written by that instruction.
	 */
	private val postponedInstructions =
		mutableMapOf<L2SemanticValue<*>, MutableList<L2Instruction>>()

	/**
	 * The number of constraints in the manifest that are impossible, which is
	 * the case when the constraint's restriction is [bottomRestriction].
	 */
	private var impossibleRestrictionCount = 0

	/**
	 * Answer whether there are any impossible restrictions in this manifest.
	 */
	val hasImpossibleRestriction: Boolean get() = impossibleRestrictionCount > 0

	/** Answer a read-only map of postponed instruction lists. */
	fun postponedInstructions():
		Map<L2SemanticValue<*>, List<L2Instruction>> = postponedInstructions

	/** Remove all postponed instructions. */
	fun clearPostponedInstructions(): Unit = postponedInstructions.clear()

	/** Create a new, empty manifest. */
	constructor()
	{
		semanticValueToSynonym = mutableMapOf()
		constraints = mutableMapOf()
	}

	/**
	 * Copy an existing manifest.  Clone the maps, and also clone the mutable
	 * [Constraint] associated with each synonym.
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
		originalManifest.postponedInstructions()
			.mapValuesTo(postponedInstructions) { (_, instructions) ->
				instructions.toMutableList()
			}
		impossibleRestrictionCount = originalManifest.impossibleRestrictionCount
	}

	/**
	 * Record a sourceInstruction, which can be translated by an [L2Regenerator]
	 * whenever its output values are needed.
	 */
	fun recordPostponedSourceInstruction(sourceInstruction: L2Instruction)
	{
		for (write in sourceInstruction.writeOperands)
		{
			recordPostponedSourceInstructionFor(sourceInstruction, write)
		}
	}

	/**
	 * Having extracted a write operand from the given postponed instruction,
	 * record the instruction under that write's semantic values.  This is in a
	 * separate method because otherwise it can't be statically type-safe in
	 * Kotlin.
	 */
	private fun <K: RegisterKind<K>> recordPostponedSourceInstructionFor(
		sourceInstruction: L2Instruction,
		writeOperand: L2WriteOperand<K>)
	{
		check()
		val values = writeOperand.semanticValues()
		// Start by capturing the postponed instruction.
		values.forEach { value ->
			postponedInstructions.getOrPut(value, ::mutableListOf)
				.add(sourceInstruction)
		}
		// Now update the synonym and restriction information.
		val existingSynonyms =
			values.mapNotNullTo(mutableSetOf<L2Synonym<K>>()) {
				semanticValueToSynonym[it]?.cast()
			}
		if (existingSynonyms.isEmpty())
		{
			// All the destination semantic values are new.
			introduceSynonym(L2Synonym(values), writeOperand.restriction())
		}
		else
		{
			val iterator = existingSynonyms.iterator()
			val pick = iterator.next().pickSemanticValue()
			iterator.forEachRemaining {
				// Merge multiple synonyms.
				mergeExistingSemanticValues(pick, it.pickSemanticValue())
			}
			// Add in any new semantic values.
			values.forEach {
				if (!semanticValueToSynonym.containsKey(it))
				{
					extendSynonym(semanticValueToSynonym(pick), it)
				}
			}
			updateRestriction(pick) {
				intersection(writeOperand.restriction())
			}
		}
	}

	/**
	 * Remove this postponed instruction from the [postponedInstructions] map of
	 * lists.
	 *
	 * Fail if the instruction is not found.
	 *
	 * @param instruction
	 *   The [L2Instruction] to remove.
	 */
	fun removePostponedSourceInstruction(
		instruction: L2Instruction)
	{
		instruction.writeOperands.forEach { writeOperand ->
			writeOperand.semanticValues().forEach { semanticValue ->
				val list = postponedInstructions[semanticValue]!!
				val removed = list.remove(instruction)
				assert(removed)
				if (list.isEmpty())
				{
					postponedInstructions.remove(semanticValue)
				}
			}
		}
	}

	/**
	 * Update the [Constraint] associated with the given [L2Synonym].
	 *
	 * @param synonym
	 *   The [L2Synonym] to look up.
	 * @param body
	 *   What to perform with the looked up [Constraint] as the receiver.
	 */
	private fun <K: RegisterKind<K>, Result> updateConstraint(
		synonym: L2Synonym<K>,
		body: Constraint<K>.() -> Result
	): Result
	{
		var constraint = constraintOrNull(synonym.pickSemanticValue())
		if (constraint == null)
		{
			constraint = Constraint<K>(emptyList(), bottomRestriction)
			impossibleRestrictionCount++
			for (sv in synonym.semanticValues())
			{
				val priorSynonym = semanticValueToSynonym.put(sv, synonym)
				assert(priorSynonym === null)
			}
		}
		else
		{
			constraints[synonym] = Constraint(constraint)
		}
		constraints[synonym] = constraint
		val oldRestriction = constraint.restriction
		val result = constraint.body()
		val newRestriction = constraints[synonym]!!.restriction
		if (oldRestriction == bottomRestriction) impossibleRestrictionCount--
		if (newRestriction == bottomRestriction) impossibleRestrictionCount++
		if (newRestriction != oldRestriction)
		{
			propagateForRestrictionChange(synonym.pickSemanticValue())
		}
//		check()
		return result
	}

	/**
	 * Update the [TypeRestriction] in the [Constraint] associated with the
	 * [L2Synonym] containing the given [L2SemanticValue].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @param body
	 *   How to transform the restriction, taking the old one as the receiver
	 *   and answering the new one.
	 */
	fun <K: RegisterKind<K>> updateRestriction(
		semanticValue: L2SemanticValue<K>,
		body: TypeRestriction.() -> TypeRestriction
	): Unit
	{
		updateConstraint(semanticValueToSynonym(semanticValue)) {
			val oldRestriction = restriction
			restriction = restriction.body()
			restriction != oldRestriction
		}
	}

	/**
	 * The restriction for a [semanticValue] has just changed.  Find any related
	 * semantic values (boxed/unboxed, tags, object variants, primitive
	 * identities, covariant/contravariant relationships), and attempt to
	 * further constrain them.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] whose restriction was just changed.
	 */
	private fun <K: RegisterKind<K>> propagateForRestrictionChange(
		semanticValue: L2SemanticValue<K>)
	{
		check()
		val restriction = restrictionFor(semanticValue)
		when (semanticValue)
		{
			is L2SemanticBoxedValue ->
			{
				if (restriction.containedByType(i32))
				{
					// The boxed form was restricted, so similarly restrict the
					// int form.
					equivalentSemanticValue(
						L2SemanticUnboxedInt(semanticValue)
					)?.let { unboxedInt ->
						updateRestriction(unboxedInt) {
						check()
							intersection(restriction.forUnboxedInt())
						}
						check()
					}
				}
				if (restriction.containedByType(DOUBLE.o))
				{
					// The boxed form was restricted, so similarly restrict the
					// double form.  Floats/doubles don't have range types yet,
					// but we support instance types.
					equivalentSemanticValue(
						L2SemanticUnboxedInt(semanticValue)
					)?.let { unboxedFloat ->
						check()
						updateRestriction(unboxedFloat) {
							intersection(restriction.forUnboxedFloat())
						}
						check()
					}
				}
				equivalentSemanticValue(
					L2SemanticUnboxedInt(
						L2SemanticExtractedTag(semanticValue))
				)?.let { intTagValue ->
					// The boxed form was restricted, so see if we can prove a
					// stronger bound for the [TypeTag].
					check()
					updateRestriction(intTagValue) {
						val tagRangeType = when
						{
							// Original value is impossible, so the tag is also
							// impossible.
							restriction == bottomRestriction -> bottom
							else -> restriction.type.instanceTag.tagRangeType()
						}
						intersectionWithType(tagRangeType)
					}
					check()
				}
			}
			is L2SemanticUnboxedInt ->
			{
				// The int form was restricted, so similarly restrict the boxed
				// form.
				equivalentSemanticValue(semanticValue.base)?.let { base ->
					check()
					updateRestriction(base) {
						intersection(restriction.forBoxed())
					}
					check()
				}

				when (val baseOfInt = semanticValue.base)
				{
					is L2SemanticExtractedTag ->
					{
						// Propagate the tighter tag range to a tighter type
						// constraint on the source object, using the tag's
						// supremum type.
						val boxedSource = baseOfInt.base
						val boxedOrUnboxed =
							equivalentSemanticValue(boxedSource)
								?: equivalentSemanticValue(
									L2SemanticUnboxedInt(boxedSource))
								?: equivalentSemanticValue(
									L2SemanticUnboxedFloat(boxedSource))
						if (boxedOrUnboxed != null)
						{
							// The source value *or* unboxed is still visible.
							var supremum: A_Type = bottom
							val range = restriction.type
							if (!range.isBottom)
							{
								var tagOrd = range.lowerBound.extractInt
								val highOrd = range.upperBound.extractInt
								while (tagOrd <= highOrd)
								{
									var tag = TypeTag.tagFromOrdinal(tagOrd)
									// Move up to get a better (faster) supremum
									// if the entire parent tag is subsumed.
									while (tag.parent !== null
										&& tag.ordinal >= tagOrd
										&& tag.highOrdinal <= highOrd)
									{
										tag = tag.parent!!
									}
									supremum =
										supremum.typeUnion(tag.supremum)
									tagOrd = tag.highOrdinal + 1
								}
							}
							check()
							updateRestriction(boxedSource) {
								intersectionWithType(supremum)
							}
							check()
						}
					}
					is L2SemanticObjectVariantId ->
					{
						// The semantic value was populated from a variant id
						// (from an object or object type), but we can't go
						// backward to the variant itself in order to narrow the
						// source value whose variant was extracted.  We simply
						// don't keep that backward map from id to variant.  But
						// we can eliminate any explicitly mentioned variants
						// from the base value based on the strengthened variant
						// id.
						equivalentSemanticValue(baseOfInt.base)?.let {
								objectValue ->
							// The objectValue holds either an object or an
							// object type.
							val variantIdRange =
								restrictionFor(semanticValue).type
							assert(variantIdRange.isSubtypeOf(i31))
							var objectRestriction = restrictionFor(objectValue)
							if (objectRestriction.type.isSubtypeOf(
									mostGeneralObjectType))
							{
								// The value is an object, so filter down the
								// object variants.
								val variantsToExclude = objectRestriction
									.positiveGroup
									.objectVariants
									?.filterNot {
										variantIdRange.rangeIncludesLong(
											it.variantId.toLong())
									}
								if (!variantsToExclude.isNullOrEmpty())
								{
									check()
									updateRestriction(objectValue) {
										variantsToExclude.fold(
											this,
											TypeRestriction::minusObjectVariant)
									}
									check()
								}
							}
							else if (objectRestriction.type
									.isSubtypeOf(mostGeneralObjectMeta))
							{
								// The value is an object *type*, so filter down
								// the object type variants.
								val variantsToExclude = objectRestriction
									.positiveGroup
									.objectTypeVariants
									?.filterNot {
										variantIdRange.rangeIncludesLong(
											it.variantId.toLong())
									}
								if (!variantsToExclude.isNullOrEmpty())
								{
									check()
									updateRestriction(objectValue) {
										variantsToExclude.fold(
											this,
											TypeRestriction::
												minusObjectTypeVariant)
									}
									check()
								}
							}
						}
					}
				}
			}
			is L2SemanticUnboxedFloat ->
			{
				equivalentSemanticValue(semanticValue.base)?.let { base ->
					// The float form was just narrowed, so narrow the boxed
					// form correspondingly.
					check()
					updateRestriction(base) {
						intersection(restriction.forBoxed())
					}
					check()
				}
			}
		}
	}

	/**
	 * Update the [List] of [L2Register] definitions in the [Constraint]
	 * associated with the [L2Synonym] containing the given [L2SemanticValue].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @param body
	 *   How to transform the list of definitions, taking the old list as the
	 *   receiver and answering the new list.
	 */
	fun <K: RegisterKind<K>> updateDefinitions(
		semanticValue: L2SemanticValue<K>,
		body: List<L2Register<K>>.() -> List<L2Register<K>>
	): Unit = updateConstraint(semanticValueToSynonym(semanticValue)) {
		definitions = definitions.body()
	}

	/**
	 * Verify that this manifest is internally consistent.
	 */
	fun check()
	{
		if (deepManifestDebugCheck)
		{
			assert(semanticValueToSynonym.values.toSet() == constraints.keys)
			assert(postponedInstructions().keys
				.all(semanticValueToSynonym::contains)
			) {
				val residue =
					postponedInstructions().keys - semanticValueToSynonym.keys
				"Malformed manifest, postponed writes but no synonym: $residue"
			}
			val registers = constraints.flatMap { it.value.definitions }
			assert(registers.size == registers.toSet().size)
			for (synonym in constraints.keys)
			{
				assert(synonym.semanticValues()
					.distinctBy(L2SemanticValue<*>::kind)
					.size == 1)
				{
					"Semantic values in synonym are of mixed kind"
				}
			}
			val count = constraints.values.count { it.isImpossible }
			assert(count == impossibleRestrictionCount) {
				"Incorrect value for hasImpossibleRestriction."
			}
		}
	}

	/**
	 * Answer a new mutable set of [L2SemanticValue]s that are either live in
	 * registers or in the [postponedInstructions] multi-level [Map].
	 */
	fun liveOrPostponedSemanticValues(
	): MutableSet<L2SemanticValue<*>> {
		if (L2Optimizer.shouldSanityCheck)
		{
			assert(postponedInstructions().keys.all(
				semanticValueToSynonym::containsKey))
		}
		val set = mutableSetOf<L2SemanticValue<*>>()
		set.addAll(semanticValueToSynonym.keys)
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
	fun <K: RegisterKind<K>> semanticValueToSynonym(
		semanticValue: L2SemanticValue<K>
	): L2Synonym<K> = semanticValueToSynonym[semanticValue]!!.cast()

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
	private fun <K: RegisterKind<K>> semanticValueToSynonymOrElse(
		semanticValue: L2SemanticValue<K>,
		elseSupplier: ()->L2Synonym<K>
	): L2Synonym<K> =
		semanticValueToSynonym[semanticValue].cast() ?: elseSupplier()

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
	fun introduceSynonym(
		freshSynonym: L2Synonym<*>,
		restriction: TypeRestriction)
	{
		assert(freshSynonym.semanticValues().none(
			semanticValueToSynonym::containsKey))
		for (sv in freshSynonym.semanticValues())
		{
			semanticValueToSynonym[sv] = freshSynonym
		}
		constraints[freshSynonym] = Constraint(emptyList(), restriction)
		if (restriction.isImpossible) impossibleRestrictionCount++
	}

	/**
	 * Answer whether the [L2SemanticValue] is known to this manifest.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue].
	 * @return
	 *   Whether this semantic value is known to this manifest, due to a
	 *   previous instruction that wrote it.
	 */
	fun hasSemanticValue(semanticValue: L2SemanticValue<*>): Boolean =
		semanticValue in semanticValueToSynonym

	/**
	 * Answer whether the [L2SemanticValue] is known to this manifest AND the
	 * instructions that compute it have been emitted.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue].
	 * @return
	 *   Whether there is a register known to be holding this value, whether
	 *   it's already written by a previous instruction or it would be written
	 *   by a postponed instruction.
	 */
	private fun hasLiveSemanticValue(
		semanticValue: L2SemanticValue<*>
	): Boolean =
		semanticValueToSynonym[semanticValue].notNullAnd {
			constraints[this]!!.definitions.isNotEmpty()
		}

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
	fun <K: RegisterKind<K>> equivalentSemanticValue(
		semanticValue: L2SemanticValue<K>
	): L2SemanticValue<K>?
	{
		if (semanticValue in semanticValueToSynonym
			|| semanticValue in postponedInstructions)
		{
			// It already exists in exactly the form given, which is the vast
			// majority of cases.
			return semanticValue
		}
		// Try a slower, far less frequent search.
		return semanticValueToSynonym.keys.firstOrNull { other ->
			isEquivalentSemanticValue(semanticValue, other)
		}.cast()
	}

	/**
	 * Given an [L2SemanticValue], see if there's already an equivalent one in
	 * this manifest, but appearing in a definition (i.e., already bound to a
	 * register).  If an [L2SemanticPrimitiveInvocation] is supplied, look
	 * for a recursively synonymous one (that's bound to a register).
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
	 *   given one, and appearing in a defining write, or `null` if no such
	 *   value is in the manifest.
	 */
	fun <K: RegisterKind<K>> equivalentPopulatedSemanticValue(
		semanticValue: L2SemanticValue<K>
	): L2SemanticValue<K>?
	{
		if (isPopulated(semanticValue)) return semanticValue
		// Try a slower, far less frequent search.
		return semanticValueToSynonym.keys.firstOrNull { other ->
			isEquivalentSemanticValue(semanticValue, other) &&
				isPopulated(other)
		}.cast()
	}

	/**
	 * Answer whether the given [L2SemanticValue] is populated by having a
	 * visible defining write that wrote to that exact semantic value.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to test.
	 * @return
	 *   Whether that semantic value has a visible write to it.
	 */
	private fun <K : RegisterKind<K>> isPopulated(
		semanticValue: L2SemanticValue<K>
	): Boolean = (semanticValue in semanticValueToSynonym
		&& getDefinitions(semanticValue).any { register ->
			register.definitions().any { write ->
				semanticValue in write.semanticValues()
			}
		})

	/**
	 * Given two [L2SemanticValue]s, see if they represent the same value in
	 * this manifest.  Include checking covariant homomorphisms between
	 * boxed/unboxed forms and primitive invocations.
	 *
	 * @param semanticValue
	 *   The first [L2SemanticValue] to compare.
	 * @param otherSemanticValue
	 *   The second [L2SemanticValue] to compare.
	 * @return
	 *   True iff the two semantic values represent the same value in this
	 *   manifest.
	 */
	private tailrec fun isEquivalentSemanticValue(
		semanticValue: L2SemanticValue<*>,
		otherSemanticValue: L2SemanticValue<*>
	): Boolean
	{
		if (semanticValue == otherSemanticValue) return true
		if (semanticValue.kind != otherSemanticValue.kind) return false
		if (semanticValue in semanticValueToSynonym &&
			semanticValueToSynonym[semanticValue] ===
				semanticValueToSynonym[otherSemanticValue])
		{
			// They're already synonyms of each other.
			return true
		}
		when
		{
			semanticValue is L2SemanticUnboxedInt &&
				otherSemanticValue is L2SemanticUnboxedInt ->
			{
				return isEquivalentSemanticValue(
					semanticValue.base, otherSemanticValue.base)
			}
			semanticValue is L2SemanticUnboxedFloat &&
				otherSemanticValue is L2SemanticUnboxedFloat ->
			{
				return isEquivalentSemanticValue(
					semanticValue.base, otherSemanticValue.base)
			}
			semanticValue is L2SemanticConstant ->
			{
				if (!hasSemanticValue(otherSemanticValue)) return false
				val otherRestriction = restrictionFor(otherSemanticValue)
				if (otherRestriction.constantOrNull
					.notNullAnd { equals(semanticValue.value) })
				{
					return true
				}
			}
			otherSemanticValue is L2SemanticConstant ->
			{
				if (!hasSemanticValue(semanticValue)) return false
				val restriction = restrictionFor(semanticValue)
				if (restriction.constantOrNull
					.notNullAnd { equals(otherSemanticValue.value) })
				{
					return true
				}
			}
			semanticValue is L2SemanticPrimitiveInvocation &&
				otherSemanticValue is L2SemanticPrimitiveInvocation &&
				semanticValue.primitive == otherSemanticValue.primitive ->
			{
				// They're invocations of the same primitive, so see if the
				// arguments happen to correspond.
				return semanticValue.argumentSemanticValues
					.zip(otherSemanticValue.argumentSemanticValues)
					.all { (a, b) -> a.equalsSemanticValue(b) }
			}
			semanticValue is L2SemanticExtractedTag &&
				otherSemanticValue is L2SemanticExtractedTag ->
			{
				// Equivalent values have the same tag.
				return isEquivalentSemanticValue(
					semanticValue.base, otherSemanticValue.base)
			}
			semanticValue is L2SemanticObjectVariantId &&
				otherSemanticValue is L2SemanticObjectVariantId ->
			{
				// Equivalent values have the same object variant id.
				return isEquivalentSemanticValue(
					semanticValue.base, otherSemanticValue.base)
			}
		}
		// We couldn't find a way in which they're equal.
		return false
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
	fun <K: RegisterKind<K>> extendSynonym(
		existingSynonym: L2Synonym<K>,
		semanticValue: L2SemanticValue<K>)
	{
		assert(!hasSemanticValue(semanticValue))
		val semanticValues = existingSynonym.semanticValues().toMutableSet()
		semanticValues.add(semanticValue)
		val merged = L2Synonym(semanticValues)
		semanticValues.forEach { sv ->
			semanticValueToSynonym[sv] = merged
		}
		constraints[merged] = constraints.remove(existingSynonym)!!
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
	fun <K: RegisterKind<K>> mergeExistingSemanticValues(
		semanticValue1: L2SemanticValue<K>,
		semanticValue2: L2SemanticValue<K>)
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
		val allSemanticPrimitives = semanticValueToSynonym.keys
			.filterIsInstance<L2SemanticPrimitiveInvocation>()
			.groupBy(L2SemanticPrimitiveInvocation::primitive)
		if (allSemanticPrimitives.isEmpty())
		{
			// There are no primitive invocations visible.
			return
		}
		while (true)
		{
			val followupMerges = mutableListOf<
				Pair<
					L2SemanticValue<BOXED_KIND>,
					L2SemanticValue<BOXED_KIND>>>()
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
				val map = mutableMapOf<
					List<L2Synonym<BOXED_KIND>?>,
					MutableSet<L2Synonym<BOXED_KIND>>>()
				for (invocation in invocations)
				{
					// Note that sometimes an L2SemanticPrimitiveInvocation will
					// be in the manifest, even though some of its argument
					// semantic values are no longer accessible.  Create a
					// singleton synonym for such a semantic value, but don't
					// register it in the manifest.
					val argumentSynonyms: List<L2Synonym<BOXED_KIND>?> =
						invocation.argumentSemanticValues
							.map {
								semanticValueToSynonymOrElse(it) {
									L2Synonym(setOf(it))
								}
							}
					val primitiveSynonyms =
						map.computeIfAbsent(argumentSynonyms) { mutableSetOf() }
					val invocationSynonym: L2Synonym<BOXED_KIND> =
						semanticValueToSynonym[invocation].cast()
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
			followupMerges.forEach { (first, second) ->
				privateMergeSynonyms(
					semanticValueToSynonym(first),
					semanticValueToSynonym(second))
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
	private fun <K: RegisterKind<K>> privateMergeSynonyms(
		synonym1: L2Synonym<K>,
		synonym2: L2Synonym<K>): Boolean
	{
		if (synonym1 == synonym2) return false
		val semanticValues =
			synonym1.semanticValues() + synonym2.semanticValues()
		val merged = L2Synonym(semanticValues)
		semanticValues.forEach { semanticValueToSynonym[it] = merged }
		val constraint1: Constraint<K> = constraints.remove(synonym1).cast()
		val constraint2: Constraint<K> = constraints.remove(synonym2).cast()
		val restriction =
			constraint1.restriction.intersection(constraint2.restriction)
		// Just concatenate the input synonyms' lists, as this essentially
		// preserves earliest definition order.
		val list = constraint1.definitions + constraint2.definitions
		val newConstraint = Constraint(list, restriction)
		constraints[merged] = newConstraint
		if (constraint1.isImpossible) impossibleRestrictionCount--
		if (constraint2.isImpossible) impossibleRestrictionCount--
		if (newConstraint.isImpossible) impossibleRestrictionCount++
		return true
	}

	/**
	 * Retrieve the oldest definition of the given [L2SemanticValue] or an
	 * equivalent, but having the given [RegisterKind].  Only consider
	 * registers whose definitions *all* include that semantic value.  This
	 * should work well in SSA or non-SSA, but not after register coloring.
	 *
	 * @param K
	 *   The [RegisterKind] of the desired register.
	 * @param semanticValue
	 *   The [L2SemanticValue] being examined.
	 * @return
	 *   The requested [L2Register].
	 */
	fun <K: RegisterKind<K>> getDefinition(
		semanticValue: L2SemanticValue<K>
	): L2Register<K>
	{
		val constraint = constraint(semanticValue)
		var definition = constraint.definitions.find { reg ->
			reg.definitions().all { write ->
				semanticValue in write.semanticValues()
			}
		}
		if (definition == null)
		{
			// Fall back to any register of the requested kind, even if it
			// doesn't have the specified semanticValue in all of its
			// definitions.
			definition = constraint.definitions.firstOrNull()
		}
		assert(definition != null) {
			"Appropriate register for kind not found"
		}
		return definition!!
	}

	/**
	 * Retrieve all [L2Register]s known to contain the given [L2SemanticValue].
	 * Narrow it to just those registers whose definitions *all* include that
	 * semantic value.
	 *
	 * @param <R>
	 *   The kind of [L2Register] to return.
	 * @param semanticValue
	 *   The [L2SemanticValue] being examined.
	 * @return
	 *   A [List] of the requested [L2Register]s.
	 */
	fun <K: RegisterKind<K>> getDefinitions(
		semanticValue: L2SemanticValue<K>
	): List<L2Register<K>> =
		constraint(semanticValue).definitions.filter { reg ->
			reg.definitions().all { write ->
				semanticValue in write.semanticValues()
			}
		}

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
		semanticValue: L2SemanticValue<*>,
		restriction: TypeRestriction)
	{
		updateRestriction(semanticValue) { restriction }
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
	fun intersectType(semanticValue: L2SemanticValue<*>, type: A_Type)
	{
		updateRestriction(semanticValue) {
			intersectionWithType(type)
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
	fun subtractType(semanticValue: L2SemanticValue<*>, type: A_Type)
	{
		updateRestriction(semanticValue) { minusType(type) }
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
	fun restrictionFor(semanticValue: L2SemanticValue<*>): TypeRestriction
	{
		val equivalent = equivalentSemanticValue(semanticValue)
		semanticValueToSynonym[equivalent]?.let { synonym ->
			return constraints[synonym]!!.restriction
		}
		// The semantic value wasn't found directly in a synonym.  If it's
		// unboxed, see if we can synthesize an answer from the boxed form.
		return when (equivalent)
		{
			is L2SemanticUnboxedInt ->
				restrictionFor(equivalent.base).forUnboxedInt()
			is L2SemanticUnboxedFloat ->
				restrictionFor(equivalent.base).forUnboxedFloat()
			else -> throw AssertionError(
				"Cannot find restriction for: $semanticValue")
		}
	}

	/**
	 * Answer the [Constraint] associated with the [L2Synonym] containing the
	 * given [L2SemanticValue], or null if it doesn't exist.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] whose synonym is to be looked up.
	 * @return
	 *   The [Constraint] associated with the synonym, or `null`.
	 */
	private fun <K: RegisterKind<K>> constraintOrNull(
		semanticValue: L2SemanticValue<K>
	): Constraint<K>?
	{
		val equivalent = equivalentSemanticValue(semanticValue)!!
		return constraints[semanticValueToSynonym(equivalent)]?.cast()
	}

	/**
	 * Answer the [Constraint] associated with the [L2Synonym] containing the
	 * given [L2SemanticValue].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] whose synonym is to be looked up.
	 * @return
	 *   The [Constraint] associated with the synonym.
	 */
	private fun <K: RegisterKind<K>> constraint(
		semanticValue: L2SemanticValue<K>
	): Constraint<K> = constraintOrNull(semanticValue)!!

	/**
	 * Answer an arbitrarily ordered array of the [L2Synonym]s in this manifest.
	 *
	 * @return
	 *   An array of [L2Synonym]s.
	 */
	fun synonymsArray(): Array<L2Synonym<*>> = constraints.keys.toTypedArray()

	/**
	 * Remove all information about registers and semantic values from this
	 * manifest.
	 */
	fun clear()
	{
		semanticValueToSynonym.clear()
		constraints.clear()
		impossibleRestrictionCount = 0
		clearPostponedInstructions()
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
	fun <K: RegisterKind<K>> recordDefinitionNoCheck(writer: L2WriteOperand<K>)
	{
		assert(writer.instructionHasBeenEmitted)
		val synonym: L2Synonym<K>
		val pickSemanticValue =
			writer.semanticValues().firstOrNull(::hasSemanticValue)
		if (pickSemanticValue !== null)
		{
			// This is a new RegisterKind for an existing semantic value.
			writer.semanticValues()
				.filterNot(::hasSemanticValue)
				.forEach {
					extendSynonym(semanticValueToSynonym(pickSemanticValue), it)
				}
			synonym = semanticValueToSynonym(pickSemanticValue)
			updateRestriction(pickSemanticValue) {
				// There shouldn't already be a register for this kind.
				//assert(!restriction.hasFlag(
				//	writer.kind.restrictionFlag))
				withFlag(writer.kind.restrictionFlag)
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
		updateDefinitions(synonym.pickSemanticValue()) {
			append(writer.register())
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
	fun <K: RegisterKind<K>> recordDefinitionForMove(
		writer: L2WriteOperand<K>,
		sourceSemanticValue: L2SemanticValue<K>)
	{
		assert(writer.instructionHasBeenEmitted)
		val restriction = restrictionFor(sourceSemanticValue)
			.intersection(writer.restriction())
		val destinations = writer.semanticValues()
		for (semanticValue in destinations)
		{
			if (semanticValue != sourceSemanticValue
				&& !hasSemanticValue(semanticValue))
			{
				// Introduce the newly written semantic value, synonymous to the
				// given one.
				extendSynonym(
					semanticValueToSynonym(sourceSemanticValue),
					semanticValue)
			}
			setRestriction(semanticValue, restriction)
		}
		// Make sure to include the entire synonym inside the write operand, to
		// ensure each kind of register in the synonym has as complete a set of
		// semantic values as possible.
		semanticValueToSynonym(sourceSemanticValue).semanticValues().forEach(
			writer::retroactivelyIncludeSemanticValue)
		updateDefinitions(sourceSemanticValue) {
			when
			{
				contains(writer.register()) -> this
				else -> append(writer.register())
			}
		}
		check()
	}

	/**
	 * Given an [L2Register], find which [L2Synonym]s, if any, are
	 * mapped to it in this manifest.  The CFG does not have to be in SSA form.
	 *
	 * @param register
	 *   The [L2Register] to find in this manifest.
	 * @return
	 *   A [Set] of [L2Synonym]s that are mapped to the given register within
	 *   this manifest.
	 */
	fun <K: RegisterKind<K>> synonymsForRegister(
		register: L2Register<K>
	): Set<L2Synonym<K>> = synonymsForRegisters(setOf(register))

	/**
	 * Given a [Set] of [L2Register]s, find which [L2Synonym]s, if any, are
	 * mapped to it in this manifest.  The CFG does not have to be in SSA form.
	 *
	 * @param registers
	 *   The [L2Register]s to find in this manifest.
	 * @return
	 *   A [Set] of [L2Synonym]s that are mapped to any of the given registers
	 *   within this manifest.
	 */
	private fun <K: RegisterKind<K>> synonymsForRegisters(
		registers: Set<L2Register<K>>
	): Set<L2Synonym<K>> =
		constraints.entries
			.filter { it.value.definitions.any { def -> def in registers } }
			.map { it.key }
			.toSet()
			.cast()  // strengthen from * to K

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
	fun readBoxed(
		semanticValue: L2SemanticValue<BOXED_KIND>
	): L2ReadBoxedOperand
	{
		val restriction = restrictionFor(semanticValue)
		assert(restriction.isBoxed)
		val register = getDefinition(semanticValue)
		val allVisible = semanticValueToSynonym(semanticValue).semanticValues()
		val suitableSemanticValues = register.definitions()
			.map { def -> def.semanticValues().intersect(allVisible) }
			.reduce(Set<L2SemanticValue<BOXED_KIND>>::intersect)
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
	fun readInt(semanticValue: L2SemanticValue<INTEGER_KIND>): L2ReadIntOperand
	{
		val restriction = restrictionFor(semanticValue)
		assert(restriction.isUnboxedInt)
		val register = getDefinition(semanticValue)
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
	fun readFloat(semanticValue: L2SemanticUnboxedFloat): L2ReadFloatOperand
	{
		val restriction = restrictionFor(semanticValue)
		assert(restriction.isUnboxedFloat)
		val register = getDefinition(semanticValue)
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
	 *   The [L2Generator] on which to write any necessary phi functions.
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
			impossibleRestrictionCount = soleManifest.impossibleRestrictionCount
			assert(postponedInstructions().isEmpty())
			soleManifest.postponedInstructions()
				.mapValuesTo(postponedInstructions) { (_, instructions) ->
					instructions.toMutableList()
				}
			return
		}
		if (generatePhis)
		{
			// Find the semantic values which are present in all manifests.
			// Build phi instructions to move from the old definitions in each
			// input edge to a new definition (write) within the phi
			// instruction.  We expect to eliminate most of these by collapsing
			// moves during register coloring.
			val otherManifests = manifests.toMutableList()
			val firstManifest = otherManifests.removeAt(0)
			val liveSemanticValues =
				firstManifest.liveOrPostponedSemanticValues()
			otherManifests.forEach {
				liveSemanticValues.retainAll(it.liveOrPostponedSemanticValues())
			}
			// For any live semantic values that are not all postponed from the
			// same original instruction (in the previous version of the control
			// flow graph), cause them to be generated in the incoming edges
			// (which will be in edge-split SSA).
			skip@for (semanticValue in liveSemanticValues) {
				val keep = manifests.all { manifest ->
					when
					{
						manifest.hasSemanticValue(semanticValue)
							&& manifest.getDefinitions(semanticValue)
								.isNotEmpty() -> true
						else -> semanticValue in manifest.postponedInstructions
					}
				}
				if (!keep) continue@skip
				// This combination of semantic value and register kind is
				// backed by either a register or a postponed instruction in
				// each incoming edge.
				val origins = manifests.map { manifest ->
					manifest.postponedInstructions[semanticValue]
				}
				if (origins.all { it === null })
				{
					// There are no postponed instructions for this purpose.
					continue@skip
				}
				if (!origins.contains(null) && origins.distinct().size == 1)
				{
					// The source of this semantic value (and kind) is the
					// same original instruction(s) for each incoming edge.
					// Simply include it in the new manifest as a postponed
					// value.
					origins[0]!!.forEach { inst ->
						val sv = inst.writeOperands[0].pickSemanticValue()
						if (postponedInstructions[sv]
							.isNullOr { !contains(inst) })
						{
							recordPostponedSourceInstruction(inst)
						}
					}
					continue@skip
				}
				// The same postponed instruction is *not* the source of the
				// value in each incoming edge.  Force the relevant postponed
				// instructions to generate just before each incoming edge for
				// which there isn't already a register with the value.  The
				// actual phi creation happens further down.  This is safe,
				// because the graph is in edge-split SSA in the presence of
				// postponements.

				assert(regenerator != null)
				val edges = generator.currentBlock().predecessorEdges()
				origins.zip(edges).forEach { (sourceInstructions, edge) ->
					if (sourceInstructions != null)
					{
						regenerator!!.forcePostponedTranslationBeforeEdge(
							edge, semanticValue)
					}
				}
			}

			val phiMap = mutableMapOf<
				List<L2Synonym<*>>,
				MutableList<L2SemanticValue<*>>>()
			liveSemanticValues.forEach { sv ->
				// Be careful not to build phis for pass-through postponed
				// instructions (they've already been carried forward above).
				if (manifests.all { it.hasLiveSemanticValue(sv) })
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
							relatedSemanticValues.cast(),
							forcePhis,
							restriction.restrictingKindsTo(setOf(kind)),
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
			assert(manifests.all { it.postponedInstructions().isEmpty() })
			val otherManifests = manifests.toMutableList()
			val firstManifest = otherManifests.removeAt(0)
			val liveRegisters = mutableMapOf<
				L2Register<*>,
				Pair<MutableSet<L2SemanticValue<*>>, Mutable<TypeRestriction>>
				>()
			firstManifest.constraints.forEach { (synonym, constraint) ->
				constraint.definitions.forEach { register ->
					liveRegisters[register] = Pair(
						synonym.semanticValues().toMutableSet(),
						Mutable(constraint.restriction))
				}
			}
			otherManifests.forEach { manifest ->
				val registersToKeep = mutableSetOf<L2Register<*>>()
				manifest.constraints.forEach { (synonym, constraint) ->
					constraint.definitions.forEach { register ->
						registersToKeep.add(register)
						liveRegisters[register]?.let {
							(semanticValues, mutableRestriction) ->
								semanticValues.retainAll(
									synonym.semanticValues())
								mutableRestriction.update {
									union(constraint.restriction)
								}
								if (semanticValues.isEmpty())
								{
									// The register was present in all incoming
									// edges, but without a semantic value
									// common to all of them.  Exclude it from
									// the manifest.
									liveRegisters.remove(register)
									// Note: This is probably impossible, since
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
				updateForPhiRegister(
					register,
					semanticValues.cast(),
					restriction.value)
			}
		}
		impossibleRestrictionCount =
			constraints.values.count { it.isImpossible }
		check()
	}

	/**
	 * A helper that allows the [RegisterKind] to be correlated among values,
	 * which Kotlin can't do for lambdas or subexpressions.  Note that the
	 * Kotlin type deduction algorithm is *very* sensitive to the order of the
	 * arguments at the call site, and is likely to break as Kotlin's type
	 * deduction algorithm changes.
	 */
	private fun <K: RegisterKind<K>> updateForPhiRegister(
		register: L2Register<K>,
		semanticValues: Set<L2SemanticValue<K>>,
		restriction: TypeRestriction)
	{
		val newSemanticValues = mutableSetOf<L2SemanticValue<K>>()
		val existingSynonyms = mutableSetOf<L2Synonym<K>>()
		semanticValues.forEach { sv ->
			if (hasSemanticValue(sv))
				existingSynonyms.add(semanticValueToSynonym(sv))
			else newSemanticValues.add(sv)
		}
		if (newSemanticValues.isNotEmpty())
		{
			val syn = L2Synonym(newSemanticValues)
			introduceSynonym(syn, restriction)
			existingSynonyms.add(syn)
		}
		val synonymsIterator = existingSynonyms.iterator()
		val sampleSynonym = synonymsIterator.next()
		val sampleSemanticValue = sampleSynonym.pickSemanticValue()
		synonymsIterator.forEachRemaining { nextSynonym ->
			mergeExistingSemanticValues(
				sampleSemanticValue,
				nextSynonym.pickSemanticValue()
			)
		}
		// All the relevant synonyms and semantic values are merged.
		updateDefinitions(sampleSemanticValue) { append(register) }
	}

	/**
	 * Transform this manifest by mapping its [L2SemanticValue]s.
	 *
	 * @param semanticValueTransformer
	 *   The transformation for [L2SemanticValue]s.
	 * @return
	 *   The transformed manifest.
	 */
	fun <K: RegisterKind<K>> transform(
		semanticValueTransformer: (L2SemanticValue<K>) -> L2SemanticValue<K>
	): L2ValueManifest
	{
		val newManifest = L2ValueManifest()
		for (oldSynonym in synonymsArray())
		{
			val restriction = restrictionFor(oldSynonym.pickSemanticValue())
			newManifest.introduceSynonym(
				oldSynonym.transform(semanticValueTransformer.cast()),
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
	fun <K: RegisterKind<K>> definitionsForDescribing(
		synonym: L2Synonym<K>
	): Iterable<L2Register<K>> =
		constraint(synonym.pickSemanticValue()).definitions

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
	fun retainRegisters(registersToRetain: Set<L2Register<*>>): Boolean
	{
		// Iterate over a copy of the map, so we can remove from it.
		var changed = false
		constraints.keys.toList().forEach { synonym ->
			changed = changed ||
				retainRegistersHelper(registersToRetain, synonym)
		}
		// Removing constraints can only transition from impossible to
		// possible.
		impossibleRestrictionCount =
			constraints.values.count { it.isImpossible }
		check()
		return changed
	}

	/**
	 * A helper to allow the [RegisterKind] to be fixed within this scope.
	 * Answer whether any changes were made.
	 */
	private fun <K: RegisterKind<K>> retainRegistersHelper(
		registersToRetain: Set<L2Register<*>>,
		synonym: L2Synonym<K>
	): Boolean = updateConstraint(synonym) {
		val definitionList = definitions.toMutableList()
		val changed = definitionList.retainAll(registersToRetain)
		if (changed)
		{
			definitions = definitionList
			if (definitionList.isEmpty())
			{
				// Remove this synonym and any semantic values within it.
				constraints.remove(synonym)
				semanticValueToSynonym.keys.removeAll(
					synonym.semanticValues())
			}
		}
		changed
	}

	/**
	 * Remove all definitions related to this [L2WriteOperand]'s
	 * [L2SemanticValue]s, then add this write operand as the sole definition in
	 * this manifest.
	 *
	 * @param writeOperand
	 *   The [L2WriteOperand] to add and replace all other relevant definitions.
	 */
	fun <K: RegisterKind<K>> replaceDefinitions(writeOperand: L2WriteOperand<K>)
	{
		updateDefinitions(writeOperand.pickSemanticValue()) {
			singletonList(writeOperand.register())
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
	fun retainSemanticValues(
		semanticValuesToRetain: Set<L2SemanticValue<*>>)
	{
		check()
		for (syn in synonymsArray())
		{
			retainSemanticValuesInSynonym(syn, semanticValuesToRetain)
		}
		check()
	}

	/**
	 * Within the given [L2Synonym], retain information only about the
	 * [semanticValuesToRetain].
	 */
	private fun <K: RegisterKind<K>> retainSemanticValuesInSynonym(
		synonym: L2Synonym<K>,
		semanticValuesToRetain: Set<L2SemanticValue<*>>)
	{
		val values = synonym.semanticValues()
		val toKeep = values.toMutableSet()
		val anyRemoved = toKeep.retainAll(semanticValuesToRetain)
		if (anyRemoved)
		{
			val toRemove = values.toMutableSet()
			toRemove.removeAll(semanticValuesToRetain)
			val constraint = constraints.remove(synonym)!!
			semanticValueToSynonym.keys.removeAll(toRemove)
			if (toKeep.isNotEmpty())
			{
				val newSynonym = L2Synonym(toKeep)
				toKeep.forEach { semanticValueToSynonym[it] = newSynonym }
				constraints[newSynonym] = constraint
			}
			else if (constraint.isImpossible)
			{
				// The constraint was removed, and it was impossible.
				impossibleRestrictionCount--
			}
		}
	}

	/**
	 * Removes all entries from the manifest except for the specified live
	 * [L2SemanticValue]s and [L2Register]s.  Also remove all postponed
	 * instructions.
	 *
	 * @param liveSemanticValues a set of live semantic values
	 * @param liveRegisters a set of live registers
	 */
	fun stripManifest(
		liveSemanticValues: Set<L2SemanticValue<*>>,
		liveRegisters: Set<L2Register<*>>,
		regenerator: L2Regenerator)
	{
		// Read from each specified semantic value, to ensure any needed
		// postponed instructions get emitted.
		liveSemanticValues.forEach { regenerator.forceTranslationForRead(it) }
		clearPostponedInstructions()
		retainSemanticValues(liveSemanticValues)
		retainRegisters(liveRegisters)
	}

	companion object
	{
		/** Perform deep, slow checks every time a manifest changes. */
		var deepManifestDebugCheck = false
	}
}
