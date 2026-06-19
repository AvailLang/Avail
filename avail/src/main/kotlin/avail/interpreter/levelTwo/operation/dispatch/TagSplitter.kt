/*
 * TagSplitter.kt
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

import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.typeIntersection
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TypeTag
import avail.descriptor.types.TypeTag.BOTTOM_TYPE_TAG
import avail.descriptor.types.TypeTag.Companion.tagFromOrdinal
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionConditions
import avail.optimizer.L2ValueManifest
import kotlin.math.max
import kotlin.math.min

/**
 * Used for splitting control flow based on some value's tag.
 *
 * @constructor
 *   Create a [TagSplitter] with the given N-1 split points and N optional
 *   [TypeTag]s used to re-strengthen the outbound edges on regeneration.
 * @param splitPoints
 *   The ascending [Int]s used to look up the tag ordinal.
 * @param edgeTags The edge tags.
 */
class TagSplitter(
	splitPoints: List<Int>,
	private val edgeTags: List<TypeTag?>
) : AbstractMultiWaySplitter(splitPoints)
{
	init
	{
		assert(splitPoints.size == edgeTags.size - 1)
	}

	override fun toString(): String = "tag splits: $splitPoints"

	/**
	 * In addition to the [L2SplitCondition]s related to preserving which
	 * integer values or ranges can or cannot be taken, we also add conditions
	 * that check if the supremum of the tags or collections of tags apply to
	 * the value whose tag is being branched on.  This allows splitting on the
	 * tag to happen prior to the tag extraction itself, in the event that there
	 * are more specific types upstream for the original value.
	 *
	 * As with the int values, we include splits for both the positive and
	 * negative cases of type membership, giving the opportunity to avoid some
	 * paths if some types can be eliminated.
	 */
	override fun interestingConditions(
		read: L2ReadIntOperand,
		edges: List<L2PcOperand>
	): List<L2SplitCondition?> = buildList {
		addAll(super.interestingConditions(read, edges))
		val sourceInstructionOfInt = read.definitionSkippingMoves(null)
		if (sourceInstructionOfInt is L2_EXTRACT_TAG_ORDINAL)
		{
			val originalSource = sourceInstructionOfInt.value
			val originalSourceRegister = originalSource.register()
			val intSemanticValue = read.semanticValue()
			edges.forEach { edge ->
				val intRestriction =
					edge.manifest().restrictionFor(intSemanticValue)
				intRestriction.constantOrNull?.let { tagOrdinal ->
					val tag = tagFromOrdinal(tagOrdinal.extractInt)
					val supremum = tag.supremum
					if (!supremum.equals(Types.TOP()))
					{
						// It would be profitable to know that this tag's
						// supremum is always satisfied somewhere upstream.
						addAll(
							typeRestrictionConditions(
								listOf(originalSourceRegister),
								boxedRestrictionForType(supremum)))
						// Allow splitting if there's an upstream point that can
						// guarantee that the supremum is *not* satisfied.
						addAll(
							typeRestrictionConditions(
								listOf(originalSourceRegister),
								boxedRestrictionForType(Types.ANY())
									.minusType(supremum)))
					}
				}
			}
		}
	}

	/**
	 * Also update the [edgeTags] in the new instance to have the same structure
	 * as [newEdges].
	 */
	override fun cloneForReducedEdges(
		oldEdges: List<L2PcOperand>,
		newEdges: List<L2PcOperand>,
		newSplitPoints: List<Int>
	): TagSplitter
	{
		assert(oldEdges.size == edgeTags.size)
		val newTags = (oldEdges zip edgeTags)
			.filter { (edge, _) -> edge in newEdges }
			.map(Pair<*, TypeTag?>::second)
		return TagSplitter(newSplitPoints, newTags)
	}

	/**
	 * Adjust the original value that the tag was extracted from.
	 */
	override fun populateEdgeManifests(
		readInt: L2ReadIntOperand,
		edges: List<L2PcOperand>,
		manifest: L2ValueManifest)
	{
		val intValue = readInt.semanticValue()
		val bottomOrdinal = BOTTOM_TYPE_TAG.ordinal
		(edges zip edgeTags).forEachIndexed { i, (edge, tag) ->
			val edgeManifest = edge.manifest()
			var low = if (i > 0) splitPoints[i - 1] else 0
			var high =
				if (i < splitPoints.size) splitPoints[i] - 1 else bottomOrdinal
			tag?.let {
				low = max(low, tag.ordinal)
				high = min(high, tag.highOrdinal)
			}
			edgeManifest.updateRestriction(intValue) {
				intersectionWithType(inclusive(low, high))
			}
		}
		// The semantic value for the int tag and the semantic value for the
		// value that's the source of that tag are already automatically updated
		// in lockstep by the manifest, so there's nothing more to do here.
	}

	override fun leftAndRightTargetNames(
		restriction: TypeRestriction,
		splitValue: Int
	): Pair<String, String>
	{
		var (leftName, rightName) =
			super.leftAndRightTargetNames(restriction, splitValue)

		// This is a tag dispatch, so be descriptive.
		val range = restriction
			.type
			.typeIntersection(inclusive(0, TypeTag.entries.size - 1))
		val low = range.lowerBound.extractInt
		val high = range.upperBound.extractInt
		leftName += " ${tagFromOrdinal(low).shorterName}"
		if (low < splitValue - 1)
		{
			leftName +=
				"..${tagFromOrdinal(splitValue - 1).shorterName}"
		}
		rightName += " ${tagFromOrdinal(splitValue).shorterName}"
		if (splitValue < high)
		{
			rightName += "..${tagFromOrdinal(high).shorterName}"
		}
		return leftName to rightName
	}
}
