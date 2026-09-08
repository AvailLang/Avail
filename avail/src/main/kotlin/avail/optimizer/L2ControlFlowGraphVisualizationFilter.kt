/*
 * L2ControlFlowGraphVisualizationFilter.kt
 * Copyright © 1993-2025, The Avail Foundation, LLC.
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

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.optimizer.values.L2SemanticValue

/**
 * A filter that controls what information is displayed in an
 * [L2ControlFlowGraphVisualizer]. Implementations can choose to show everything
 * ([NoFilter]) or focus on specific data flow ([FocusFilter]).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface L2ControlFlowGraphVisualizationFilter
{
	/**
	 * Whether this block should be shown in full detail (with all instructions)
	 * or as a small circle placeholder.
	 *
	 * @param block
	 *   The [L2BasicBlock] to check.
	 * @return
	 *   `true` if the block should show full detail, `false` for minimal
	 *   rendering.
	 */
	fun isBlockInteresting(block: L2BasicBlock): Boolean

	/**
	 * Whether this instruction should be shown in full detail (with operands)
	 * or just its short operation name.
	 *
	 * @param instruction
	 *   The [L2Instruction] to check.
	 * @return
	 *   `true` if the instruction should show full detail, `false` for just the
	 *   operation name.
	 */
	fun isInstructionInteresting(instruction: L2Instruction): Boolean

	/**
	 * Whether this edge should display its manifest label, considering both
	 * whether the edge carries interesting information and whether it's a
	 * sibling of an interesting edge.
	 *
	 * @param edge
	 *   The [L2PcOperand] edge to check.
	 * @return
	 *   `true` if the edge should show its manifest, `false` to omit the label
	 *   (but input numbering is still shown).
	 */
	fun shouldShowEdgeManifest(edge: L2PcOperand): Boolean

	/**
	 * Get the set of synonyms that should be displayed for this edge's
	 * manifest. May return an empty set if no synonyms are interesting, or
	 * `null` to indicate all synonyms should be shown.
	 *
	 * @param edge
	 *   The [L2PcOperand] edge.
	 * @return
	 *   The [Set] of interesting [L2Synonym]s for this edge, or `null` for all.
	 */
	fun interestingSynonymsFor(edge: L2PcOperand): Set<L2Synonym>?
}

/**
 * A [L2ControlFlowGraphVisualizationFilter] that shows everything without
 * filtering. This is the default behavior when no focus value is specified.
 */
class NoFilter : L2ControlFlowGraphVisualizationFilter
{
	override fun isBlockInteresting(block: L2BasicBlock) = true

	override fun isInstructionInteresting(instruction: L2Instruction) = true

	override fun shouldShowEdgeManifest(edge: L2PcOperand) = true

	override fun interestingSynonymsFor(edge: L2PcOperand): Set<L2Synonym>? =
		null // null means show all
}

/**
 * A [L2ControlFlowGraphVisualizationFilter] that traces backward from a
 * [focusValue] to show only the data and control flow that contributes to that
 * value.
 *
 * When [currentBlock] is provided, only traces ancestry to the focus value in
 * that specific block. When [currentBlock] is `null`, traces from all exit
 * blocks (blocks with no successors except backward edges).
 *
 * Sibling edges (edges from the same source block as an interesting edge) have
 * their manifests shown but filtered to only the semantic values being traced.
 *
 * @property focusValue
 *   The [L2SemanticValue] to trace backward from.
 * @property currentBlock
 *   The specific block containing the focus value, or `null` to trace from all
 *   exit blocks.
 * @property controlFlowGraph
 *   The [L2ControlFlowGraph] being visualized.
 *
 * @constructor
 * Construct a focus filter and compute the set of interesting entities.
 *
 * @param focusValue
 *   The semantic value to trace.
 * @param currentBlock
 *   Optional specific block to trace from.
 * @param controlFlowGraph
 *   The control flow graph.
 */
class FocusFilter constructor(
	private val focusValue: L2SemanticValue,
	private val currentBlock: L2BasicBlock?,
	private val controlFlowGraph: L2ControlFlowGraph
) : L2ControlFlowGraphVisualizationFilter
{
	/**
	 * All semantic values being traced backward. Starts with [focusValue] and
	 * expands as we find instructions that write to traced values.
	 */
	private val interestingSemanticValues = mutableSetOf<L2SemanticValue>()

	/**
	 * Blocks that contain at least one instruction that reads or writes an
	 * interesting semantic value.
	 */
	private val interestingBlocks = mutableSetOf<L2BasicBlock>()

	/**
	 * Instructions that read or write interesting semantic values. These are
	 * shown in full detail.
	 */
	private val interestingInstructions = mutableSetOf<L2Instruction>()

	/**
	 * Edges whose manifests contain interesting semantic values. These are part
	 * of the traced data flow path.
	 */
	private val interestingEdges = mutableSetOf<L2PcOperand>()

	/**
	 * Edges that are siblings of interesting edges (same source block). These
	 * show their manifests but filtered to interesting synonyms.
	 */
	private val siblingEdgesOfInterestingEdges = mutableSetOf<L2PcOperand>()

	/**
	 * Map from each edge to the subset of its manifest's synonyms that contain
	 * interesting semantic values. Used for filtering manifest display.
	 */
	private val interestingSynonymsPerEdge =
		mutableMapOf<L2PcOperand, Set<L2Synonym>>()

	init
	{
		computeInterestingEntities()
	}

	/**
	 * Compute the set of interesting blocks, instructions, edges, and synonyms
	 * by tracing backward from [focusValue] using the control flow graph's
	 * [L2ControlFlowGraph.backwardVisit] method.
	 *
	 * Algorithm:
	 * 1. Initialize exit blocks with focusValue as needed.
	 * 2. Use backwardVisit to process blocks (successors before predecessors).
	 * 3. For each block, scan instructions in reverse to find writes to
	 *    needed values.
	 * 4. When a write is found, augment it with the instruction's reads.
	 * 5. Propagate needed values backward through predecessor edges.
	 * 6. Mark sibling edges for filtered manifest display.
	 *
	 * Also mark non-jump branch instructions as interesting, but only if at
	 * least one outbound edge is interesting.
	 */
	private fun computeInterestingEntities()
	{
		// Map from block to the set of values its successors need from it.
		val valuesNeededByBlock =
			mutableMapOf<L2BasicBlock, MutableSet<L2SemanticValue>>()

		// Initialize starting blocks with focusValue.
		if (currentBlock != null)
		{
			// Start from specific currentBlock with focusValue.
			valuesNeededByBlock[currentBlock] = mutableSetOf(focusValue)
		}
		else
		{
			// Start from all exit blocks (blocks with no forward successors)
			// Note: This includes both blocks with NO successors (returns,
			// unreachable) and blocks whose only successors are backward edges
			// (loop back-edges). The latter have strip-manifest instructions
			// before them, and we show complete synonyms for backward jumps.
			controlFlowGraph.basicBlockOrder
				.filter { block ->
					block.successorEdges().all(L2PcOperand::isBackward)
				}
				.forEach { exitBlock ->
					valuesNeededByBlock[exitBlock] = mutableSetOf(focusValue)
				}
		}

		// Process blocks in reverse order using backwardVisit.
		controlFlowGraph.backwardVisit(handlePartialGraph = true) { block ->
			// Get the set of values this block needs to provide to successors.
			val neededValues =
				valuesNeededByBlock[block] ?: return@backwardVisit
			if (neededValues.isEmpty()) return@backwardVisit

			val currentlyNeeded = neededValues.toMutableSet()
			interestingSemanticValues.addAll(currentlyNeeded)
			interestingBlocks.add(block)

			// Process instructions in reverse to find which ones provide needed
			// values. Flow control instructions should be described if the
			// edges out have any interesting values. Treat the last one
			// specially, considering every read in it to be an interesting
			// semantic value.
			block.instructions().asReversed().forEach { instruction ->
				// if the final instruction isn't a simple jump, and has at
				// least one output edge that's interesting, then that final
				// instruction is treated as interesting, even though we don't
				// add semantic values that it reads to the trace set.
				if (instruction.altersControlFlow
					&& instruction !is L2_JUMP
					&& instruction.targetEdges.any(interestingEdges::contains))
				{
					interestingInstructions.add(instruction)
				}
				// Check if this instruction writes to any value we need.
				// Also consider reads of the value, since they may be of
				// interest.
				val writes = instruction.writeOperands.flatMapTo(
					mutableSetOf(), L2WriteOperand<*>::semanticValues)
				val reads = instruction.readOperands.mapTo(
					mutableSetOf(), L2ReadOperand<*>::semanticValue)

				if (writes.any(currentlyNeeded::contains)
					|| reads.any(currentlyNeeded::contains))
				{
					// This instruction provides a needed value - mark it.
					interestingInstructions.add(instruction)

					// Include the read values in the backward search.
					currentlyNeeded.addAll(reads)
					interestingSemanticValues.addAll(reads)
				}
			}

			// Propagate needed values backward through predecessor edges
			block.predecessorEdges()
				.filterNot(L2PcOperand::isBackward)
				.forEach { incomingEdge ->
					val manifest = incomingEdge.manifestOrNull()
					if (manifest != null)
					{
						// Find which synonyms in this edge's manifest contain
						// values we need.
						val interestingSyns = manifest.synonymsArray()
							.filter { synonym ->
								synonym.semanticValues()
									.any(currentlyNeeded::contains)
							}

						if (interestingSyns.isNotEmpty())
						{
							// This edge carries interesting data.
							interestingEdges.add(incomingEdge)
							interestingSynonymsPerEdge[incomingEdge] =
								interestingSyns.toSet()

							// Propagate needed values to the predecessor block,
							val sourceBlock = incomingEdge.sourceBlock()
							valuesNeededByBlock
								.getOrPut(sourceBlock, ::mutableSetOf)
								.addAll(currentlyNeeded)
						}
					}
				}
		}

		// Mark sibling edges (same source as interesting edges)
		markSiblingEdges()
	}

	/**
	 * Mark edges that are siblings of interesting edges (share the same source
	 * block). These edges will show their manifests but filtered to only the
	 * synonyms that were interesting in their interesting siblings.
	 *
	 * This helps show alternate control flow paths from decision points by
	 * presenting the same relevant information that the interesting path cares
	 * about, making it easier to understand why one path was taken over another.
	 */
	private fun markSiblingEdges()
	{
		// Group interesting edges by their source block
		val interestingEdgesBySourceBlock = interestingEdges
			.groupBy { it.sourceBlock() }

		// For each source block with interesting outgoing edges
		interestingEdgesBySourceBlock.forEach { (sourceBlock, interestingOnes) ->
			// Collect all synonyms that were interesting in ANY interesting
			// sibling from this block
			val allInterestingSynonyms = interestingOnes
				.flatMapTo(mutableSetOf()) { edge ->
					interestingSynonymsPerEdge[edge] ?: emptySet()
				}

			// Mark all sibling edges from this block
			sourceBlock.successorEdges().forEach { edge ->
				if (edge !in interestingEdges)
				{
					// This is a sibling of an interesting edge
					siblingEdgesOfInterestingEdges.add(edge)

					// Show only synonyms that appear in both:
					// 1. This sibling edge's manifest.
					// 2. The set of interesting synonyms from interesting
					//    siblings.
					edge.manifestOrNull()?.let { manifest ->
						val siblingManifestSynonyms = manifest.synonymsArray()
						val filteredSyns = siblingManifestSynonyms
							.intersect(allInterestingSynonyms)
						if (filteredSyns.isNotEmpty())
						{
							interestingSynonymsPerEdge[edge] = filteredSyns
						}
					}
				}
			}
		}
	}

	override fun isBlockInteresting(block: L2BasicBlock) =
		block in interestingBlocks

	override fun isInstructionInteresting(instruction: L2Instruction) =
		instruction in interestingInstructions

	override fun shouldShowEdgeManifest(edge: L2PcOperand) =
		edge in interestingEdges || edge in siblingEdgesOfInterestingEdges

	override fun interestingSynonymsFor(edge: L2PcOperand) =
		interestingSynonymsPerEdge[edge]
}
