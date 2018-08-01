/*
 * L2RegisterColorer.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.optimizer;

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.utility.Graph;

import javax.annotation.Nullable;
import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * Used to compute which registers can use the same storage due to not being
 * active at the same time.  This is called register coloring.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2RegisterColorer
{
	/**
	 * A collection of registers that should be colored the same.
	 */
	private static class RegisterGroup
	{
		Set<L2Register<?>> registers = new HashSet<>();

		int finalIndex = -1;

		void setFinalIndex (final int newFinalIndex)
		{
			finalIndex = newFinalIndex;
			registers.forEach(reg -> reg.setFinalIndex(newFinalIndex));
		}

		int finalIndex ()
		{
			return finalIndex;
		}

		@Override
		public String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append("RegisterGroup: (");
			boolean first = true;
			for (final L2Register<?> r : registers)
			{
				if (!first)
				{
					builder.append(", ");
				}
				builder.append(r.uniqueValue);
				first = false;
			}
			builder.append(")");
			return builder.toString();
		}
	}

	/**
	 * The {@link List} of all {@link L2Register}s that occur in the control
	 * flow graph.
	 */
	private final List<L2Register<?>> allRegisters;

	/**
	 * The unique number of the register being traced from its uses back to its
	 * definition(s).
	 */
	private @Nullable L2Register<?> registerBeingTraced;

	/**
	 * A map from registers to sets of registers that can be colored the same
	 * due to coalesced moves.  The sets are mutable, and each member of such a
	 * set is a key in this map that points to that set.  Note that we can only
	 * bring together register sets that have no interference edge between them.
	 *
	 * <p>Since interference edges prevent merging, we have to calculate the
	 * interference graph first.  We populate the registerSets with singleton
	 * sets before this.</p>
	 */
	private final Map<L2Register<?>, RegisterGroup> registerGroups =
		new HashMap<>();

	/**
	 * The set of blocks that so far have been reached, but not necessarily
	 * processed, while tracing the current register.
	 */
	private final Set<L2BasicBlock> reachedBlocks = new HashSet<>();

	/**
	 * The collection of blocks that may still need to be traced.  Ignore any
	 * that are in visitedBlocks.
	 */
	private final Deque<L2BasicBlock> blocksToTrace = new ArrayDeque<>();

	/**
	 * The interference graph, as it's being built.
	 */
	private final Graph<RegisterGroup> interferences;

	/**
	 * Construct a new register colorer for the given control flow graph.
	 *
	 * @param controlFlowGraph The given {@link L2ControlFlowGraph}.
	 */
	public L2RegisterColorer (final L2ControlFlowGraph controlFlowGraph)
	{
		this.allRegisters = controlFlowGraph.allRegisters();
		this.interferences = new Graph<>();
		allRegisters.forEach(reg ->
		{
			final RegisterGroup singleton = new RegisterGroup();
			singleton.registers.add(reg);
			registerGroups.put(reg, singleton);
			interferences.addVertex(singleton);
		});
	}

	/**
	 * Calculate the register interference graph.
	 */
	void computeInterferenceGraph ()
	{
		for (final L2Register<?> reg : allRegisters)
		{
			// Trace the register from each of its uses along all paths back to
			// its definition(s).  This is the lifetime of the register.  While
			// tracing this, the register is considered to interfere with any
			// encountered register use.  Deal with moves specially, avoiding
			// the addition of an interference edge due to the move, but
			// ensuring interference between a register and its move-buddy's
			// interfering neighbors.
			registerBeingTraced = reg;
			for (final L2Instruction instruction : reg.uses())
			{
				if (instruction.operation().isPhi())
				{
					for (final L2BasicBlock predBlock :
						L2_PHI_PSEUDO_OPERATION.predecessorBlocksForUseOf(
							instruction, reg))
					{
						if (reachedBlocks.add(predBlock))
						{
							blocksToTrace.add(predBlock);
						}
					}
				}
				else
				{
					processLiveInAtStatement(
						instruction.basicBlock,
						instruction.basicBlock.instructions().indexOf(
							instruction));
				}
				// Process the queue until empty.
				while (!blocksToTrace.isEmpty())
				{
					final L2BasicBlock blockToTrace =
						blocksToTrace.removeLast();
					// This actually does a live-out trace starting at the last
					// instruction.
					processLiveInAtStatement(
						blockToTrace, blockToTrace.instructions().size());
				}
			}
			reachedBlocks.clear();
		}
		registerBeingTraced = null;
	}

	/**
	 * Trace the register uses starting at the specified instruction index in
	 * the given block.  If the index equals the number of instructions (which
	 * would be past the end in its zero-based numbering), start with a live-out
	 * processing of the last instruction.
	 *
	 * @param block
	 *        The {@link L2BasicBlock} in which to trace variable liveness.
	 * @param statementIndex
	 *        The zero-based index of the {@link L2Instruction} at which to
	 *        begin tracing for live-in.  If this equals the number of
	 *        instructions in the block, begin with a live-out trace at the
	 *        last instruction.
	 */
	private void processLiveInAtStatement (
		final L2BasicBlock block,
		final int statementIndex)
	{
		assert registerBeingTraced != null;
		final List<L2Instruction> instructions = block.instructions();
		for (int index = statementIndex - 1; index >= 0; index--)
		{
			// Process live-out for this instruction.
			final L2Instruction instruction = instructions.get(index);
			assert instruction.operation() != L2_ENTER_L2_CHUNK.instance
				: "Liveness trace must not reach an L2_ENTER_L2_CHUNK";
			boolean definesCurrentRegister = false;
			for (final L2Register<?> written
				: instruction.destinationRegisters())
			{
				if (written == registerBeingTraced)
				{
					definesCurrentRegister = true;
					continue;
				}
				if (registerGroups.get(registerBeingTraced).registers.contains(
					written))
				{
					continue;
				}
				// Register banks are numbered independently, so the notion
				// of interference between registers in different banks is
				// moot (i.e., they don't interfere).
				if (registerBeingTraced.registerKind()
					!= written.registerKind())
				{
					continue;
				}
				// Moves count as interference between the live-out variable of
				// interest (registerBeingTraced) and the destination of the
				// move, but only if the live-out variable isn't also the source
				// of the move.
				if (instruction.operation().isMove()
					&& instruction.sourceRegisters().get(0)
						== registerBeingTraced)
				{
					continue;
				}
				final RegisterGroup group1 =
					registerGroups.get(registerBeingTraced);
				final RegisterGroup group2 =
					registerGroups.get(written);
				interferences.includeEdge(group1, group2);
				interferences.includeEdge(group2, group1);
			}
			if (definesCurrentRegister)
			{
				// We reached the definition of the register.  Don't trace any
				// more of this block.
				return;
			}
		}
		// We reached the start of the block without hitting the defining phi.
		// Continue tracing in each predecessor block.
		final Iterator<L2PcOperand> iterator = block.predecessorEdgesIterator();
		while (iterator.hasNext())
		{
			final L2BasicBlock sourceBlock = iterator.next().sourceBlock();
			if (reachedBlocks.add(sourceBlock))
			{
				blocksToTrace.add(sourceBlock);
			}
		}
	}

	/**
	 * Now that the interference graph has been constructed, merge together any
	 * non-interfering nodes that are connected by a move.
	 */
	void coalesceNoninterferingMoves ()
	{
		for (final L2Register<?> reg : allRegisters)
		{
			for (final L2Instruction instruction : reg.definitions())
			{
				if (instruction.operation().isMove())
				{
					// The source and destination registers shouldn't be
					// considered interfering if they'll hold the same value.
					final RegisterGroup group1 = registerGroups.get(reg);
					final RegisterGroup group2 =
						registerGroups.get(
							instruction.sourceRegisters().get(0));
					if (group1 != group2)
					{
						if (!interferences.includesEdge(group1, group2))
						{
							// Merge the non-interfering move-related register
							// sets.
							final RegisterGroup smallSet;
							final RegisterGroup largeSet;
							if (group1.registers.size()
								< group2.registers.size())
							{
								smallSet = group1;
								largeSet = group2;
							}
							else
							{
								smallSet = group2;
								largeSet = group1;
							}
							for (final RegisterGroup neighborOfSmall :
								interferences.successorsOf(smallSet))
							{
								assert neighborOfSmall != largeSet;
								interferences.includeEdge(
									largeSet, neighborOfSmall);
								interferences.includeEdge(
									neighborOfSmall, largeSet);
							}
							interferences.exciseVertex(smallSet);
							// Merge the smallSet elements into the largeSet.
							for (final L2Register<?> r : smallSet.registers)
							{
								registerGroups.put(r, largeSet);
							}
							largeSet.registers.addAll(smallSet.registers);
						}
					}
				}
			}
		}
	}

	/**
	 * Determine colors for all registers.  We use a simple coloring algorithm
	 * here, since both L2 and the JVM have an effectively unbounded number of
	 * virtual registers, and we're merely interested in keeping the color count
	 * as reasonably close to minimal as we can.
	 *
	 * <p>The algorithm repeatedly chooses the registerSets having the least
	 * number of interfering edges, pushing them on a stack and removing the
	 * vertex (registerSet) and its edges.  We then repeatedly pop registers
	 * from the stack, choosing the lowest available color (finalIndex) that
	 * doesn't conflict with the coloring of a neighbor in the original graph.
	 * </p>
	 */
	void computeColors ()
	{
		final Deque<RegisterGroup> stack =
			new ArrayDeque<>(allRegisters.size());
		final Graph<RegisterGroup> graphCopy = new Graph<>(interferences);
		while (!graphCopy.isEmpty())
		{
			// Find the nodes having the fewest neighbors.
			int fewestCount = Integer.MAX_VALUE;
			final List<RegisterGroup> withFewest = new ArrayList<>();
			for (final RegisterGroup reg : graphCopy.vertices())
			{
				final int neighborCount = graphCopy.successorsOf(reg).size();
				if (neighborCount < fewestCount)
				{
					fewestCount = neighborCount;
					withFewest.clear();
				}
				if (fewestCount == neighborCount)
				{
					withFewest.add(reg);
				}
			}
			// We now have the collection of registers which tie for having the
			// fewest remaining neighbours interfering with them.  Push them,
			// removing them from the graphCopy, along with their connected
			// edges.  This reduces the cardinality of connected nodes.
			stack.addAll(withFewest);
			for (final RegisterGroup registerGroup : withFewest)
			{
				graphCopy.exciseVertex(registerGroup);
			}
		}
		// We've now stacked all nodes in a pretty good order for assigning
		// colors as we pop them.  In particular, if during the pushing phase we
		// never pushed a vertex with cardinality over K, then as we pop we'll
		// never encounter a situation where there are more than K neighbors
		// that already have colors, so there will be no more than K colors in
		// use for those neighbors.  So we can color the whole graph with at
		// most K+1 colors.
		final BitSet neighbors = new BitSet();
		while (!stack.isEmpty())
		{
			final RegisterGroup group = stack.removeLast();
			neighbors.clear();
			for (final RegisterGroup registerGroup
				: interferences.successorsOf(group))
			{
				final int index = registerGroup.finalIndex();
				if (index != -1)
				{
					neighbors.set(index);
				}
			}
			final int color = neighbors.nextClearBit(0);
			group.setFinalIndex(color);
		}
		for (final L2Register<?> register : registerGroups.keySet())
		{
			assert register.finalIndex() != -1;
		}
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("Colorer:\n\tGroups:");
		final Collection<RegisterGroup> groups =
			registerGroups.values().stream().distinct().collect(toList());
		groups.forEach(g ->
		{
			builder.append("\n\t\t");
			builder.append(g);
		});
		builder.append("\n\tInterferences:");
		for (final RegisterGroup group : interferences.vertices())
		{
			final Set<RegisterGroup> neighbors =
				interferences.successorsOf(group);
			if (!neighbors.isEmpty())
			{
				builder.append("\n\t\t");
				builder.append(group);
				builder.append(" ≠ ");
				builder.append(neighbors);
			}
		}
		return builder.toString();
	}
}
