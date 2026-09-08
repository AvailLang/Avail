/*
 * DeadCodeAnalyzer.kt
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

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.manifest.L2Liveness
import avail.optimizer.values.L2SemanticValue
import avail.utility.deepForEach

/**
 * A mechanism for determining which instructions are dead versus live.
 *
 * @property dataCouplingMode
 *   The policy about which kinds of entity to consider for liveness.
 * @property controlFlowGraph
 *   The [L2ControlFlowGraph] to analyze.
 *
 * @constructor
 * Construct a `DeadCodeAnalyzer`.
 *
 * @param dataCouplingMode
 *   The policy about whether [L2Register]s, [L2SemanticValue]s, or both, should
 *   be the basis for liveness.
 * @param controlFlowGraph
 *   The [L2ControlFlowGraph] being analyzed.
 */
internal class DeadCodeAnalyzer constructor(
	private val dataCouplingMode: DataCouplingMode,
	private val controlFlowGraph: L2ControlFlowGraph)
{
	/** The [L2Instruction]s that have been marked as live so far. */
	val liveInstructions: Set<L2Instruction>
		field = mutableSetOf<L2Instruction>()

	/**
	 * Calculate which operations are live, either because they have a side
	 * effect, or they produce a value consumed (recursively) by a live
	 * instruction.
	 */
	fun analyzeReads()
	{
		assert(liveInstructions.isEmpty())
		// First seed all edges that have forcedClampedEntities.  In theory, we
		// could do better by determining liveness by iterating, but since the
		// clamped entities are pretty minimal, we're not likely to eliminate a
		// lot of dead code.
		controlFlowGraph.basicBlockOrder.deepForEach(
			L2BasicBlock::predecessorEdges
		) { edge ->
			assert(edge.liveness == null)
			var liveness: L2Liveness? = null
			if (dataCouplingMode.considersRegisters)
			{
				edge.forcedClampedRegisters?.let { clamped ->
					/*if (liveness == null)*/ liveness = L2Liveness()
					clamped.forEach(liveness::add)
				}
			}
			if (dataCouplingMode.considersSemanticValues)
			{
				edge.forcedClampedSemanticValues?.let { clamped ->
					if (liveness == null) liveness = L2Liveness()
					clamped.forEach(liveness::add)
				}
			}
			edge.liveness = liveness
		}

		// Visit the blocks in reverse dependency order, ignoring back-edges.
		// Collect all instructions that have side effects or produce values
		// consumed by a later non-dead instruction.
		controlFlowGraph.backwardVisit { block ->
			// All of its successors must have already been processed, either
			// because it's a back-edge and therefore had clamped information,
			// or because it was a forward edge that was visited before this
			// block (possibly with clamped information as well).
			val liveness = L2Liveness(
				block.successorEdges().map { it.liveness!! })
			var livenessByPredecessor: List<L2Liveness>? = null
			val predecessorCount = block.predecessorEdges().size
			for (instruction in block.instructions().asReversed())
			{
				if (instruction is L2_PHI<*>)
				{
					if (livenessByPredecessor == null)
					{
						// Copy per predecessor (reusing the original for #0).
						livenessByPredecessor =
							block.predecessorEdges().indices.map {
								if (it == 0) liveness
								else L2Liveness(listOf(liveness))
							}
					}
					dataCouplingMode.visitPhi(
						instruction,
						livenessByPredecessor)
				}
				else
				{
					assert(livenessByPredecessor == null) {
						"Encountered a non-phi before a phi"
					}
					var keep =
						dataCouplingMode.visitInstruction(instruction, liveness)
					if (keep) liveInstructions.add(instruction)
				}
			}
			assert(predecessorCount > 0 || liveness.isEmpty())
			{
				"Instructions consume ${liveness.shortSummary()} but " +
					"preceding definitions were not found"
			}
			// Now write the liveness information to the predecessor edges.
			block.predecessorEdges().forEachIndexed { index, edge ->
				edge.liveness = when (livenessByPredecessor)
				{
					null -> L2Liveness(listOf(liveness))  // safety
					else -> livenessByPredecessor[index]
				}
			}
		}
	}
}
