/*
 * DeadCodeAnalyzer.kt
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

import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import com.avail.optimizer.values.L2SemanticValue
import com.avail.utility.Casts
import java.util.*

/**
 * A mechanism for determining which instructions are dead versus live.
 *
 * @property dataCouplingMode
 *   The policy about which kinds of [L2Entity] to consider.
 * @property controlFlowGraph
 *   The [L2ControlFlowGraph] to analyze.
 *
 * @constructor
 * Construct a `DeadCodeAnalyzer`.
 *
 * @param dataCouplingMode
 *   The policy about what kinds of [L2Entity] should be traced.
 * @param controlFlowGraph
 *   The [L2ControlFlowGraph] being analyzed.
 */
internal class DeadCodeAnalyzer constructor(
	private val dataCouplingMode: DataCouplingMode,
	private val controlFlowGraph: L2ControlFlowGraph)
{
	/**
	 * A [Map] from each [L2PcOperand] to the [Set] of
	 * [entities][L2Entity] that might be consumed after this edge.
	 */
	private val edgeNeeds = mutableMapOf<L2PcOperand, MutableSet<L2Entity>>()

	/** The [L2Instruction]s that have been marked as live so far.  */
	private val liveInstructions = mutableSetOf<L2Instruction>()

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
		for (block in controlFlowGraph.basicBlockOrder)
		{
			for (i in block.predecessorEdgesCount() - 1 downTo 0)
			{
				val edge = block.predecessorEdgeAt(i)
				if (edge.forcedClampedEntities === null)
				{
					continue
				}
				val needs = mutableSetOf<L2Entity>()
				for (entity in edge.forcedClampedEntities!!)
				{
					if (entity is L2SemanticValue)
					{
						if (dataCouplingMode.considersSemanticValues())
						{
							needs.add(entity)
						}
					}
					else if (dataCouplingMode.considersRegisters())
					{
						needs.add(entity)
					}
				}
				edgeNeeds[edge] = needs
			}
		}

		// Start with each block that has no successors, or which has only
		// backward successor edges, collected above.
		val toVisit: Deque<L2BasicBlock> =
			ArrayDeque(controlFlowGraph.basicBlockOrder
				.filter { block: L2BasicBlock ->
					block.successorEdgesCopy().stream()
						.allMatch { edgeNeeds.containsKey(it) }
				})

		// Visit the blocks in reverse dependency order, ignoring back-edges.
		// Collect all instructions that have side effects or produce values
		// consumed by a later non-dead instruction.
		while (!toVisit.isEmpty())
		{
			val block = toVisit.removeFirst()
			// All of its successors have already been processed.
			val neededEntities = mutableSetOf<L2Entity>()
			block.successorEdgesIterator()
				.forEachRemaining { neededEntities.addAll(edgeNeeds[it]!!) }
			val predecessorCount = block.predecessorEdgesCount()
			val instructions = block.instructions()
			var index: Int = instructions.size
			while (--index >= 0)
			{
				val instruction = instructions[index]
				if (instruction.operation().isPhi)
				{
					break
				}
				// As a simplifying assumption, pretend an altersControlFlow
				// instruction at the end of the block populates *all* of the
				// entities that are visible along any of its successor edges.
				if (neededEntities.removeAll(
						dataCouplingMode.writeEntitiesOf(instruction))
					|| instruction.hasSideEffect())
				{
					liveInstructions.add(instruction)
					neededEntities.addAll(
						dataCouplingMode.readEntitiesOf(instruction))
				}
			}
			val entitiesByPredecessor: List<MutableSet<L2Entity>>
			if (index >= 0)
			{
				// At least one phi is present in the block.  Compute a separate
				// set of needs per predecessor.
				entitiesByPredecessor = (0..predecessorCount)
					.map { neededEntities.toMutableSet() }.toList()
				while (index >= 0)
				{
					val phiInstruction = instructions[index]
					val phiOperation: L2_PHI_PSEUDO_OPERATION<*, *, *> =
						Casts.cast(phiInstruction.operation())
					val readOperands =
						phiOperation.sourceRegisterReads(phiInstruction)
					for (predecessorIndex in 0 until predecessorCount)
					{
						val entities = entitiesByPredecessor[predecessorIndex]
						if (entities.removeAll(
								dataCouplingMode.writeEntitiesOf(phiInstruction))
							|| phiInstruction.hasSideEffect())
						{
							liveInstructions.add(phiInstruction)
							val readOperand =
								readOperands[predecessorIndex]
							dataCouplingMode.addEntitiesFromRead(
								readOperand, entities)
							entities.addAll(
								dataCouplingMode.readEntitiesOf(readOperand))
						}
					}
					index--
				}
			}
			else
			{
				// There were no phi instructions, so we need the same thing
				// from each predecessor.
				entitiesByPredecessor =
					Collections.nCopies(predecessorCount, neededEntities)
			}
			assert(block.predecessorEdgesCount() != 0
			   || neededEntities.isEmpty())
			{
				("Instruction consumes $neededEntities but a preceding "
					+ "definition was not found")
			}
			val entitySetIterator =
				entitiesByPredecessor.iterator()
			block.predecessorEdgesDo { predecessor: L2PcOperand ->
				// No need to copy it, as it won't be modified again.
				val entities = entitySetIterator.next()
				assert(edgeNeeds.containsKey(predecessor)
				   == predecessor.isBackward)
				if (!predecessor.isBackward)
				{
					edgeNeeds[predecessor] = entities
					val predecessorBlock =
						predecessor.instruction().basicBlock()
					if (predecessorBlock.successorEdgesCopy()
						.all { edgeNeeds.containsKey(it) })
					{
						toVisit.add(predecessorBlock)
					}
				}
			}
		}
	}

	/**
	 * Answer the [L2Instruction]s that were found to be live by a prior call to
	 * [analyzeReads].
	 *
	 * @return
	 *   An immutable [Set] of live [L2Instruction]s.
	 */
	fun liveInstructions(): Set<L2Instruction>
	{
		assert(liveInstructions.isNotEmpty())
		return Collections.unmodifiableSet(liveInstructions)
	}
}
