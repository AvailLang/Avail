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

import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.objects.ObjectTypeDescriptor
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.optimizer.manifest.L2ValueManifest

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
	 * Adjust the original value that the tag was extracted from.  The provided
	 * [manifest] is the manifest that the edges' manifests were just cloned
	 * from.
	 */
	override fun populateEdgeManifests(
		readInt: L2ReadIntOperand,
		edges: List<L2PcOperand>,
		manifest: L2ValueManifest)
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
		val sourceInstruction = readInt.definitionSkippingMoves(manifest)
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
		// The manifest already automatically updates the semantic value for the
		// int tag and the semantic value for the source of that tag in
		// lockstep, so there's nothing more to do here.
	}
}
