/*
 * DeadCodeAnalyzer.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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

package com.avail.optimizer;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadOperand;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.optimizer.values.L2SemanticValue;

import java.util.*;
import java.util.stream.IntStream;

import static com.avail.utility.Casts.cast;
import static java.util.Collections.nCopies;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

/**
 * A mechanism for determining which instructions are dead versus live.
 */
class DeadCodeAnalyzer
{
	/** The policy about which kinds of {@link L2Entity} to consider. */
	private final DataCouplingMode dataCouplingMode;

	/** The {@link L2ControlFlowGraph} to analyze. */
	private final L2ControlFlowGraph controlFlowGraph;

	/**
	 * A {@link Map} from each {@link L2PcOperand} to the {@link Set} of
	 * {@linkplain L2Entity entities} that might be consumed after this edge.
	 */
	private final Map<L2PcOperand, Set<L2Entity>> edgeNeeds = new HashMap<>();

	/** The {@link L2Instruction}s that have been marked as live so far. */
	private final Set<L2Instruction> liveInstructions = new HashSet<>();

	/**
	 * Create an instance.
	 *
	 * @param dataCouplingMode
	 *        The policy about what kinds of {@link L2Entity} should be traced.
	 * @param controlFlowGraph
	 *        The {@link L2ControlFlowGraph} being analyzed.
	 */
	DeadCodeAnalyzer (
		final DataCouplingMode dataCouplingMode,
		final L2ControlFlowGraph controlFlowGraph)
	{
		this.dataCouplingMode = dataCouplingMode;
		this.controlFlowGraph = controlFlowGraph;
	}

	/**
	 * Calculate which operations are live, either because they have a side
	 * effect, or they produce a value consumed (recursively) by a live
	 * instruction.
	 */
	void analyzeReads ()
	{
		assert liveInstructions.isEmpty();
		// First seed all edges that have forcedClampedEntities.  In theory, we
		// could do better by determining liveness by iterating, but since the
		// clamped entities are pretty minimal, we're not likely to eliminate a
		// lot of dead code.
		for (final L2BasicBlock block : controlFlowGraph.basicBlockOrder)
		{
			for (int i = block.predecessorEdgesCount() - 1; i >= 0; i--)
			{
				final L2PcOperand edge = block.predecessorEdgeAt(i);
				if (edge.forcedClampedEntities == null)
				{
					continue;
				}
				final Set<L2Entity> needs = new HashSet<>();
				for (final L2Entity entity : edge.forcedClampedEntities)
				{
					if (entity instanceof L2SemanticValue)
					{
						if (dataCouplingMode.considersSemanticValues())
						{
							needs.add(entity);
						}
					}
					else if (dataCouplingMode.considersRegisters())
					{
						needs.add(entity);
					}
				}
				edgeNeeds.put(edge, needs);
			}
		}

		// Start with each block that has no successors, or which has only
		// backward successor edges, collected above.
		final Deque<L2BasicBlock> toVisit =
			controlFlowGraph.basicBlockOrder.stream()
				.filter(block -> block.successorEdgesCopy().stream()
					.allMatch(edgeNeeds::containsKey))
				.collect(toCollection(ArrayDeque::new));
		// Visit the blocks in reverse dependency order, ignoring back-edges.
		// Collect all instructions that have side effects or produce values
		// consumed by a later non-dead instruction.
		while (!toVisit.isEmpty())
		{
			final L2BasicBlock block = toVisit.removeFirst();
			// All of its successors have already been processed.
			final Set<L2Entity> neededEntities = new HashSet<>();
			block.successorEdgesIterator().forEachRemaining(successor ->
				neededEntities.addAll(edgeNeeds.get(successor)));
			final int predecessorCount = block.predecessorEdgesCount();
			final List<L2Instruction> instructions = block.instructions();
			int i;
			for (i = instructions.size() - 1; i >= 0; i--)
			{
				final L2Instruction instruction = instructions.get(i);
				if (instruction.operation().isPhi())
				{
					break;
				}
				// As a simplifying assumption, pretend an altersControlFlow
				// instruction at the end of the block populates *all* of the
				// entities that are visible along any of its successor edges.
				if (neededEntities.removeAll(
						dataCouplingMode.writeEntitiesOf(instruction))
					|| instruction.hasSideEffect())
				{
					liveInstructions.add(instruction);
					neededEntities.addAll(
						dataCouplingMode.readEntitiesOf(instruction));
				}
			}
			final List<Set<L2Entity>> entitiesByPredecessor;
			if (i >= 0)
			{
				// At least one phi is present in the block.  Compute a separate
				// set of needs per predecessor.
				entitiesByPredecessor = IntStream.range(0, predecessorCount)
					.mapToObj(n -> new HashSet<>(neededEntities))
					.collect(toList());
				while (i >= 0)
				{
					final L2Instruction phiInstruction = instructions.get(i);
					final L2_PHI_PSEUDO_OPERATION<?, ?, ?> phiOperation =
						cast(phiInstruction.operation());
					final List<? extends L2ReadOperand<?>> readOperands =
						phiOperation.sourceRegisterReads(phiInstruction);
					for (
						int predecessorIndex = 0;
						predecessorIndex < predecessorCount;
						predecessorIndex++)
					{
						final Set<L2Entity> entities =
							entitiesByPredecessor.get(predecessorIndex);
						if (entities.removeAll(
							dataCouplingMode.writeEntitiesOf(phiInstruction))
							|| phiInstruction.hasSideEffect())
						{
							liveInstructions.add(phiInstruction);
							final L2ReadOperand<?> readOperand =
								readOperands.get(predecessorIndex);
							dataCouplingMode.addEntitiesFromRead(
								readOperand, entities);
							entities.addAll(
								dataCouplingMode.readEntitiesOf(readOperand));
						}
					}
					i--;
				}
			}
			else
			{
				// There were no phi instructions, so we need the same thing
				// from each predecessor.
				entitiesByPredecessor =
					nCopies(predecessorCount, neededEntities);
			}
			// Propagate the remaining needs to predecessor edges.
			if (block.predecessorEdgesCount() == 0 && !neededEntities.isEmpty())
			{
				assert false :
					"Instruction consumes "
						+ neededEntities
						+ " but a preceding definition was not found";
			}
			final Iterator<Set<L2Entity>> entitySetIterator =
				entitiesByPredecessor.iterator();
			block.predecessorEdgesIterator().forEachRemaining(predecessor ->
			{
				// No need to copy it, as it won't be modified again.
				final Set<L2Entity> entities = entitySetIterator.next();
				assert edgeNeeds.containsKey(predecessor)
					== predecessor.isBackward();
				if (!predecessor.isBackward())
				{
					edgeNeeds.put(predecessor, entities);
					final L2BasicBlock predecessorBlock =
						predecessor.instruction().basicBlock();
					if (predecessorBlock.successorEdgesCopy().stream().allMatch(
						edgeNeeds::containsKey))
					{
						toVisit.add(predecessorBlock);
					}
				}
			});
		}
	}

	/**
	 * Answer the {@link L2Instruction}s that were found to be live by a prior
	 * call to {@link #analyzeReads()}.
	 *
	 * @return An immutable {@link Set} of live {@link L2Instruction}s.
	 */
	public Set<L2Instruction> liveInstructions ()
	{
		assert !liveInstructions.isEmpty();
		return unmodifiableSet(liveInstructions);
	}
}
