/*
 * AbstractMultiWaySplitter.kt
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

import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.types.TypeTag
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForConstant
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionConditions
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticExtractedTag
import avail.optimizer.values.L2SemanticUnboxedInt.Companion.boxed
import avail.utility.cast

/**
 * An abstraction for factoring out the maintenance of multi-way dispatch logic
 * without polluting code with logic for special uses (dispatching by [TypeTag]
 * or [ObjectLayoutVariant]).
 */
abstract class AbstractMultiWaySplitter
constructor(
	val splitPoints: List<Int>)
{
	/**
	 * Determine conditions that would be nice to know statically, and that it
	 * might be profitable to avoid losing due to a control flow merge.  By
	 * default, those conditions include knowing that the input int register has
	 * some proper subset of the range of int ranges or values required along
	 * these edges.  This would allow at least one impossible case to be
	 * omitted, along with the corresponding check in a generated decision tree.
	 * We encode this as a set of conditions where the exact value (or range) is
	 * known to hold, plus a set of conditions where an exact value or range is
	 * known *not* to hold.
	 */
	open fun interestingConditions(
		read: L2ReadIntOperand,
		edges: List<L2PcOperand>
	): List<L2SplitCondition?>
	{
		val register = read.register()
		val semanticValue = read.semanticValue()
		val allConstantInts =
			edges.mapNotNull {
				it.manifest().restrictionFor(semanticValue).constantOrNull
			}
		val warmConstantInts = edges
			.filterNot { it.targetBlock().isCold }
			.mapNotNull {
				it.manifest().restrictionFor(semanticValue).constantOrNull
			}
		val conditions = mutableListOf<L2SplitCondition?>()
		allConstantInts.forEach { constant ->
			val exclude = read.restriction().minusValue(constant)
			conditions.addAll(
				typeRestrictionConditions(
					setOf(register),
					exclude))
		}
		warmConstantInts.forEach { constant ->
			val include =
				intRestrictionForConstant(constant.extractInt)
			conditions.addAll(
				typeRestrictionConditions(
					setOf(register),
					include))
		}
		return conditions
	}

	/**
	 * Produce an [L2_MULTIWAY_JUMP] instruction, or an equivalent, using the
	 * given [readValue] to produce an unboxed int value, with edges suitable
	 * for this [AbstractMultiWaySplitter].  Remove unreachable edges, and
	 * strengthen the manifests on the remaining edges to take into account the
	 * consequences of having matched the int value or range along that edge.
	 *
	 * The receiver can be destroyed by this operation, and must not be an
	 * instruction that has been emitted.
	 *
	 * @param readValue
	 *   The [L2ReadIntOperand] supplying the integer on which to dispatch.
	 * @param edges
	 *   The [List] of [L2PcOperand]s separated by the [splitPoints].
	 * @param manifest
	 *   The [L2ValueManifest] currently in effect.
	 * @return
	 *   An [L2Instruction] that can be emitted, or perhaps further processed if
	 *   it's an [L2_MULTIWAY_JUMP].
	 */
	fun reducedSplitterInstruction(
		readValue: L2ReadIntOperand,
		edges: List<L2PcOperand>,
		manifest: L2ValueManifest
	): L2Instruction
	{
		edges.forEach { edge -> edge.setManifestToCloneOf(manifest) }
		populateEdgeManifests(readValue, edges)
		val possibleEdges = edges.filterNot { edge ->
			edge.manifest().hasImpossibleRestriction
				|| edge.manifest().restrictionFor(readValue.semanticValue())
				.intersection(readValue.restriction())
				.isImpossible
		}.toSet()

		if (possibleEdges.isEmpty())
		{
			// Shouldn't happen, but play nice.
			return L2_UNREACHABLE_CODE()
		}
		if (possibleEdges.size == 1)
		{
			return L2_JUMP(possibleEdges.single())
		}

		// Let impossible edges share a target path with one of their neighbors,
		// since they can't actually be reached by the condition that the
		// impossible edge had.
		val newEdges = mutableListOf<L2PcOperand>()
		val newSplits = mutableListOf<Int>()
		edges.forEachIndexed { i, edge ->
			if (edge in possibleEdges)
			{
				if (newEdges.isEmpty() ||
					edge.targetBlockSkippingBareJumps() !=
					newEdges.last().targetBlockSkippingBareJumps())
				{
					// Preserve the edge.
					newEdges.add(edge)
					if (i < splitPoints.size)
					{
						newSplits.add(splitPoints[i])
					}
					else
					{
						//Dummy value, will be removed.
						newSplits.add(Int.MIN_VALUE)
					}
				}
			}
		}
		newSplits.removeLast()
		assert(newSplits.size == newEdges.size - 1)
		val newSplitter = cloneForReducedEdges(edges, newEdges, newSplits)
		return L2_MULTIWAY_JUMP(
			readValue,
			L2ArbitraryConstantOperand(newSplitter),
			L2PcVectorOperand(newEdges))
	}

	/**
	 * Emit an [L2_MULTIWAY_JUMP] instruction, or an equivalent, using the given
	 * [readValue] to produce an unboxed int value, with edges suitable for this
	 * [AbstractMultiWaySplitter].  Remove unreachable edges, and strengthen the
	 * manifests on the remaining edges to take into account the consequences of
	 * having matched the int value or range along that edge.
	 *
	 * @receiver
	 *   The [L2GeneratorInterface] on which to write the instruction.
	 * @param readValue
	 *   The [L2ReadIntOperand] supplying the integer on which to dispatch.
	 * @param edges
	 *   The [List] of [L2PcOperand]s separated by the [splitPoints].
	 */
	fun L2GeneratorInterface.emitSplitterInstruction(
		readValue: L2ReadIntOperand,
		edges: List<L2PcOperand>)
	{
		+reducedSplitterInstruction(readValue, edges, currentManifest)
	}

	/**
	 * Create a new instance like the receiver, but based on the possibly
	 * reduced list of edges and split points.
	 *
	 * @param oldEdges
	 *   The edges that match up with the receiver.
	 * @param newEdges
	 *   The possibly reduced list of edges for the new instance.
	 * @param newSplitPoints
	 *   The possible reduced sorted list of [Int]s at which to test "≥".
	 * @return
	 *   The new [AbstractMultiWaySplitter].
	 */
	abstract fun cloneForReducedEdges(
		oldEdges: List<L2PcOperand>,
		newEdges: List<L2PcOperand>,
		newSplitPoints: List<Int>
	): AbstractMultiWaySplitter

	/**
	 * Construct the manifest in each of the given edges, which correspond to
	 * the int regions delineated by this splitter's [splitPoints].  The edges
	 * at this point have all gotten manifests that are a clone of the
	 * generator's current manifest.
	 */
	abstract fun populateEdgeManifests(
		readInt: L2ReadIntOperand,
		edges: List<L2PcOperand>)

	fun originalValueSource(
		readInt: L2ReadIntOperand
	): L2SemanticBoxedValue?
	{
		val intTagValue = readInt.semanticValue()
		val tagValue = intTagValue.boxed
		if (tagValue !is L2SemanticExtractedTag) return null
		return tagValue.base.cast()
	}

	/** Provide meaningful names for the target blocks of a branch. */
	open fun leftAndRightTargetNames(
		restriction: TypeRestriction,
		splitValue: Int
	): Pair<String, String> = "< $splitValue" to "≥ $splitValue"
}
