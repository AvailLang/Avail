/*
 * VariantSplitter.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
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

package avail.interpreter.levelTwo.operation.dispatch

import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.objects.ObjectTypeDescriptor
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i31
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionConditions

/**
 * Used for splitting control flow based on some object or object type's
 * [ObjectLayoutVariant].
 *
 * @constructor
 *   Create a [VariantSplitter] with the given N-1 split points and N optional
 *   [ObjectLayoutVariant]s used to re-strengthen the outbound edges on
 *   regeneration.
 * @param isInstance
 *   `true` if the value whose variant is being dispatched is an
 *   [object][ObjectDescriptor], or `false` if it's an
 *   [object&#32;type][ObjectTypeDescriptor].
 * @param splitPoints
 *   The ascending [Int]s used to look up the [ObjectLayoutVariant] number.
 * @param edgeVariants The [ObjectLayoutVariant].
 */
class VariantSplitter(
	private val isInstance: Boolean,
	splitPoints: List<Int>,
	private val edgeVariants: List<ObjectLayoutVariant?>,
) : AbstractMultiWaySplitter(splitPoints)
{
	init
	{
		assert(splitPoints.size == edgeVariants.size - 1)
	}

	override fun toString(): String = "variant splits: $splitPoints"

	/**
	 * In addition to the [L2SplitCondition]s related to preserving which
	 * integer values or ranges can or cannot be taken, we also add conditions
	 * that narrow the original object's restriction, both to check for the
	 * variants being known upstream and for a type constraint based on the
	 * most general type of that variant.
	 *
	 * As with the int values, we include splits for both the positive and
	 * negative cases of restriction membership, giving the opportunity to avoid
	 * some paths if some cases can be eliminated as impossible.
	 */
	override fun interestingConditions(
		read: L2ReadIntOperand,
		edges: List<L2PcOperand>
	): List<L2SplitCondition?> = buildList {
		addAll(super.interestingConditions(read, edges))
		val sourceInstructionOfInt = read.definitionSkippingMoves()
		val intVariantRegister = read.register()
		edgeVariants.filterNotNull().forEach { variant ->
			// Split if this variant id is a match upstream.
			addAll(
				typeRestrictionConditions(
					listOf(intVariantRegister),
					intRestrictionForConstant(variant.variantId)))
			// Split if this variant id is excluded upstream.
			addAll(
				typeRestrictionConditions(
					listOf(intVariantRegister),
					intRestrictionForType(i31)
						.minusValue(fromInt(variant.variantId))))
		}
		when (sourceInstructionOfInt)
		{
			is L2_EXTRACT_OBJECT_VARIANT_ID ->
			{
				// It's a variant dispatch on an object instance.
				assert(isInstance)
				val originalSourceRegister =
					sourceInstructionOfInt.sourceObject.register()
				val baseRestriction =
					boxedRestrictionForType(mostGeneralObjectType)
				edgeVariants.filterNotNull().forEach { variant ->
					// It would be profitable to know that this
					// variant's most general object type is always
					// satisfied somewhere upstream.
					addAll(
						typeRestrictionConditions(
							listOf(originalSourceRegister),
							baseRestriction
								.intersectionWithObjectVariant(variant)
						)
					)
					// Allow splitting if there's an upstream point that
					// can guarantee that the most general type for this
					// variant is *not* satisfied.
					addAll(
						typeRestrictionConditions(
							listOf(originalSourceRegister),
							baseRestriction.minusObjectVariant(variant)
						)
					)
				}
			}
			is L2_EXTRACT_OBJECT_TYPE_VARIANT_ID ->
			{
				// It's a variant dispatch on an object *type*.
				assert(!isInstance)
				val originalSourceRegister =
					sourceInstructionOfInt.objectType.register()
				val baseRestriction =
					boxedRestrictionForType(mostGeneralObjectMeta)
				edgeVariants.filterNotNull().forEach { variant ->
					// It would be profitable to know that this variant's most
					// general object meta is always satisfied somewhere
					// upstream.
					addAll(
						typeRestrictionConditions(
							listOf(originalSourceRegister),
							baseRestriction
								.intersectionWithObjectTypeVariant(variant)
						)
					)
					// Allow splitting if there's an upstream point that can
					// guarantee that the most general meta for this variant is
					// *not* satisfied.
					addAll(
						typeRestrictionConditions(
							listOf(originalSourceRegister),
							baseRestriction.minusObjectTypeVariant(variant)
						)
					)
				}
			}
		}
	}

	/**
	 * Also update the [edgeVariants] in the new instance to have the same
	 * structure as [newEdges].
	 */
	override fun cloneForReducedEdges(
		oldEdges: List<L2PcOperand>,
		newEdges: List<L2PcOperand>,
		newSplitPoints: List<Int>
	): VariantSplitter
	{
		val newVariants = (oldEdges zip edgeVariants)
			.filter { (edge, _) -> edge in newEdges}
			.map(Pair<*, ObjectLayoutVariant?>::second)
		return VariantSplitter(isInstance, newSplitPoints, newVariants)
	}

	/**
	 * Adjust the original value that the tag was extracted from.
	 */
	override fun populateEdgeManifests(
		readInt: L2ReadIntOperand,
		edges: List<L2PcOperand>)
	{
		val intValue = readInt.semanticValue()
		(edges zip edgeVariants).forEachIndexed { i, (edge, _) ->
			edge.manifest().updateRestriction(intValue) {
				val low = if (i > 0) splitPoints[i - 1] else 0
				val high =
					if (i < splitPoints.size) splitPoints[i] - 1
					else Int.MAX_VALUE
				intersectionWithType(inclusive(low, high))
			}
		}
		val sourceInstruction = readInt.definitionSkippingMoves()
		when (sourceInstruction)
		{
			is L2_EXTRACT_OBJECT_VARIANT_ID ->
			{
				// It's a variant dispatch on an object instance.
				assert(isInstance)
				val sourceValue = sourceInstruction.sourceObject.semanticValue()
				(edges zip edgeVariants).forEach { (edge, variant) ->
					if (variant === null) return@forEach
					edge.manifest().updateRestriction(sourceValue)
					{
						intersectionWithObjectVariant(variant)
					}
				}
			}
			is L2_EXTRACT_OBJECT_TYPE_VARIANT_ID ->
			{
				// It's a variant dispatch on an object *type*.
				assert(!isInstance)
				val sourceValue = sourceInstruction.objectType.semanticValue()
				(edges zip edgeVariants).forEach { (edge, variant) ->
					if (variant === null) return@forEach
					edge.manifest().updateRestriction(sourceValue)
					{
						intersectionWithObjectTypeVariant(variant)
					}
				}
			}
		}
		// The semantic value for the int tag and the semantic value for the
		// value that's the source of that tag are already automatically updated
		// in lockstep by the manifest, so there's nothing more to do here.
	}
}
