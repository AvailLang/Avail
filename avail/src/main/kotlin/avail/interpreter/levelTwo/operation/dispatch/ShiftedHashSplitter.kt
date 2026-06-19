/*
 * ShiftedHashSplitter.kt
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
import avail.descriptor.representation.A_Type.Companion.instances
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.dispatch.LookupTree
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.And
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Ushr
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.existsCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionConditions
import avail.optimizer.L2ValueManifest
import avail.utility.cast

/**
 * Used for splitting control flow based on the value's hash, right-shifted some
 * amount, and masked to include a limited number of low bits.  This is an
 * efficient scheme when there are many specific enumeration values in a
 * [LookupTree].
 *
 * @constructor
 *   Create a [ShiftedHashSplitter] with the given N-1 split points and N
 *   outbound edges, some of which may lead to a fallback case.
 * @param rightShift
 *   How much to right-shift the hash values, chosen to maximize the spread of
 *   the hashes of the values being tested for.
 * @param lowMask
 *   A mask for the right-shifted values.
 * @param splitPoints
 *   The list of N-1 positions at which "≥" tests can be performed to figure out
 *   which of the N edges to take.
 * @param activeInts
 *   A list of optional [Int]s that correspond to the instruction's edges,
 *   indicating the guaranteed [Int] value if that edge is taken.  If an entry
 *   is `null`, the corresponding edge cannot deduce anything about the value.
 *   This is useful for collapsing adjacent "fallback" paths together.
 */
class ShiftedHashSplitter constructor(
	private val rightShift: Int,
	private val lowMask: Int,
	splitPoints: List<Int> = (1..lowMask).toList(),
	private val activeInts: List<Int?>
) : AbstractMultiWaySplitter(splitPoints)
{
	override fun toString(): String = "hashed split (>> $rightShift & $lowMask)"

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
		// It would be nice to have already extracted the variant.
		add(existsCondition(listOf(read.semanticValue())))
		originalValueRead(read)?.let { sourceRead ->
			val sourceType = sourceRead.restriction().type
			if (sourceType.isEnumeration && !sourceType.isInstanceMeta)
			{
				sourceType.instances.forEach { instance ->
					// Split if there's a point upstream that knows the
					// exact constant that we're looking up.
					addAll(
						typeRestrictionConditions(
							listOf(sourceRead.register()),
							boxedRestrictionForConstant(instance)))
					// Split if there's a point upstream that knows that
					// one or more of the constants will not be possible
					// here.
					addAll(
						typeRestrictionConditions(
							listOf(sourceRead.register()),
							boxedRestrictionForType(Types.ANY())
								.minusValue(instance)))
				}
			}
		}
	}

	/**
	 * Answer the [L2ReadBoxedOperand] that was fed to an [L2_HASH] operation,
	 * then shifted and masked to provide the given [read], which is dispatched
	 * by this multi-way jump operation.  Reach across moves.  If such a source
	 * cannot be found, answer `null`.
	 *
	 * @param read
	 *   The [L2ReadIntOperand] providing the masked, shifted, hash of some
	 *   value.
	 * @return
	 *   The [L2ReadBoxedOperand] providing the value that was hashed, shifted,
	 *   and masked.
	 */
	private fun originalValueRead(
		read: L2ReadIntOperand
	): L2ReadBoxedOperand?
	{
		val sourceInstructionOfMasked = read.definitionSkippingMoves(null)
		if (sourceInstructionOfMasked.isBitLogicOperation(And))
		{
			return null
		}
		val sourceInstructionOfShifted = sourceInstructionOfMasked
			.readOperands.first()  // value & mask
			.definitionSkippingMoves(null)
		val sourceInstructionOfHash: L2Instruction = when
		{
			sourceInstructionOfShifted.isBitLogicOperation(Ushr) ->
			{
				sourceInstructionOfShifted
					.readOperands.first() // value >>> shift
					.definitionSkippingMoves(null)
			}
			// No shift was needed in this case.
			else -> sourceInstructionOfShifted
		}
		if (sourceInstructionOfHash is L2_HASH)
			return null
		return sourceInstructionOfHash.readOperands.single().cast()
	}

	override fun cloneForReducedEdges(
		oldEdges: List<L2PcOperand>,
		newEdges: List<L2PcOperand>,
		newSplitPoints: List<Int>
	): ShiftedHashSplitter
	{
		assert(newEdges.size == newSplitPoints.size + 1)
		val newActiveInts = (oldEdges zip activeInts)
			.filter { (edge, _) -> edge in newEdges }
			.map(Pair<*, Int?>::second)
		return ShiftedHashSplitter(
			rightShift, lowMask, newSplitPoints, newActiveInts)
	}

	/**
	 * Adjust the original value that the hash was extracted from.  The actual
	 * masked, shifted hash of the value has already been strengthened in each
	 * edge.
	 */
	override fun populateEdgeManifests(
		readInt: L2ReadIntOperand,
		edges: List<L2PcOperand>,
		manifest: L2ValueManifest)
	{
		val active = activeInts.filterNotNull()
		assert(edges.size == active.size)
		edges.zip(active).forEach { (edge, activeInt) ->
			edge.manifest().intersectType(
				readInt.semanticValue(),
				instanceType(fromInt(activeInt)))
		}
	}
}
