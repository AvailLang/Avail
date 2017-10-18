/**
 * L2RegisterColorer.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.utility.Graph;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Used to compute which registers can use the same storage due to not being
 * active at the same time.  This is called register coloring.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2RegisterColorer
{
	/**
	 * The {@link List} of all {@link L2Register}s that occur in the control
	 * flow graph.
	 */
	private final List<L2Register> allRegisters;

	/**
	 * The unique number of the register being traced from its uses back to its
	 * definition.
	 */
	private @Nullable L2Register registerBeingTraced;

	/**
	 * A map from registers to sets of registers that are connected via edges
	 * that occur as &#123;source, destination&#125; unordered pairs.  The sets
	 * are mutable, and each member of such a set is a key in this map that
	 * points to that set.  So encountering a "move d1 ← s1" ensures that the
	 * map contains an entry for d1 and an entry for s1, and that each
	 * associated value is the same set, containing at least s1 and d1.
	 */
	private final Map<L2Register, Set<L2Register>> moveCliques = new HashMap<>();

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
	private final Graph<L2Register> interferences;

	/**
	 * Construct a new register colorer for the given control flow graph.
	 *
	 * @param controlFlowGraph The given {@link L2ControlFlowGraph}.
	 */
	public L2RegisterColorer (final L2ControlFlowGraph controlFlowGraph)
	{
		this.allRegisters = controlFlowGraph.allRegisters();
		this.interferences = new Graph<>();
		allRegisters.forEach(reg -> {
			moveCliques.put(reg, new HashSet<>(Collections.singleton(reg)));
			interferences.addVertex(reg);
		});
	}

	/**
	 * Calculate colors for each register, and assign them.  By color, I mean
	 * setting the {@link L2Register#finalIndex()} in such a way that multiple
	 * registers with the same finalIndex aren't live simultaneously.
	 */
	public void colorRegisters ()
	{
		computeMoveCliques();
		computeInterferenceGraph();
		computeColors();
	}

	/**
	 * Populate {@link #moveCliques} by visiting every move instruction, merging
	 * the equivalence set for the source and the equivalence set for the
	 * destination.
	 *
	 * <p>Note that this is <em>only</em> valid while the control flow graph is
	 * in SSA form.  Consider the simple example:</p>
	 *
	 * <p>{@code [a ::= min(x,y); b ::= max(x,y); <a,b>]}</p>
	 *
	 * <p>The conditional moves of min and max would connect (a,x), (a,y),
	 * (b,x), and (b,y).  All four would end up in the same clique, although
	 * it's obvious that we can't actually color it that way.</p>
	 *
	 * <p>In SSA, those conditional moves would be isolated by a subsequent
	 * phi-function, which would turn into a move only <em>after</em>
	 * coloring.</p>
	 */
	private void computeMoveCliques ()
	{
		for (final L2Register reg : allRegisters)
		{
			final L2Instruction instruction = reg.definition();
			if (instruction.operation instanceof L2_MOVE)
			{
				mergeCliques(reg, instruction.sourceRegisters().get(0));
			}
		}
	}

	/**
	 * Merge together two sets of registers that must always contain the same
	 * value as each other.
	 *
	 * @param reg1 A register from the first set to merge.
	 * @param reg2 A register from the second set to merge.
	 */
	private void mergeCliques(final L2Register reg1, final L2Register reg2)
	{
		final Set<L2Register> set1 = moveCliques.get(reg1);
		final Set<L2Register> set2 = moveCliques.get(reg2);
		final Set<L2Register> smallSet;
		final Set<L2Register> largeSet;
		if (set1.size() < set2.size())
		{
			smallSet = set1;
			largeSet = set2;
		}
		else
		{
			smallSet = set2;
			largeSet = set1;
		}
		// Merge the smallSet into the largeSet.
		for (final L2Register r : smallSet)
		{
			largeSet.add(r);
			moveCliques.put(r, largeSet);
		}
	}

	/**
	 * Calculate the register interference graph.  Assume the move-cliques have
	 * already been determined by {@link #computeMoveCliques()}
	 */
	private void computeInterferenceGraph ()
	{
		for (final L2Register reg : allRegisters)
		{
			// Trace the register from each of its uses along all paths back to
			// its definition.  This is the lifetime of the register.  While
			// tracing this, the register is considered to interfere with any
			// encountered register use.  Deal with moves specially, avoiding
			// the addition of an interference edge due to the move, but
			// ensuring interference between a register and its move-buddy's
			// interfering neighbors.
			registerBeingTraced = reg;
			reachedBlocks.clear();

			for (final L2Instruction instruction : reg.uses())
			{
				if (instruction.operation.isPhi())
				{
					for (final L2BasicBlock predBlock :
						L2_PHI_PSEUDO_OPERATION.predecessorBlocksForUseOf(
							instruction, reg))
					{
						if (!reachedBlocks.contains(predBlock))
						{
							reachedBlocks.add(predBlock);
							queueLiveOutBlock(predBlock);
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
						blockToTrace,
						blockToTrace.instructions().size());
				}
			}
		}
	}

	/**
	 * Queue the given block to be traced for liveness of the current register.
	 * If the block has ever been queued for this variable, do nothing.
	 *
	 * @param block
	 *        The {@link L2BasicBlock} in which to trace variable liveness.
	 */
	private void queueLiveOutBlock (final L2BasicBlock block)
	{
		if (reachedBlocks.add(block))
		{
			blocksToTrace.add(block);
		}
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
			final List<L2Register> definitions =
				instruction.destinationRegisters();
			boolean definesCurrentRegister = false;
			for (final L2Register definition : definitions)
			{
				if (definition == registerBeingTraced)
				{
					definesCurrentRegister = true;
				}
				else if (!moveCliques.get(registerBeingTraced).contains(
					definition))
				{
					// Register banks are numbered independently, so the notion
					// of interference between registers in different banks is
					// moot (i.e., they don't interfere).
					if (registerBeingTraced.registerKind()
						== definition.registerKind())
					{
						interferences.addEdge(registerBeingTraced, definition);
						interferences.addEdge(definition, registerBeingTraced);
					}
				}
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
		for (final L2PcOperand predecessor : block.predecessorEdges())
		{
			queueLiveOutBlock(predecessor.sourceBlock());
		}
	}

	/**
	 * Determine colors for all registers.  We use a trivial coloring algorithm
	 * here, since both L2 and the JVM have an effectively unbounded number of
	 * virtual registers, and we're merely interested in keeping the register
	 * sets as reasonably close to minimal as we can.
	 *
	 * <p>The algorithm repeatedly chooses the registers having the least
	 * number of interfering edges, pushing them on a stack and removing the
	 * vertex (register) and its edges.  We then repeatedly pop registers from
	 * the stack, choosing the lowest available color (finalIndex) that doesn't
	 * conflict with the coloring of a neighbor in the original graph.</p>
	 */
	private void computeColors ()
	{
		final Deque<L2Register> stack = new ArrayDeque<>(allRegisters.size());
		final Graph<L2Register> graphCopy = new Graph<>(interferences);
		while (!graphCopy.isEmpty())
		{
			// Find the nodes having the fewest neighbors.
			int fewestCount = Integer.MAX_VALUE;
			final List<L2Register> withFewest = new ArrayList<>();
			for (final L2Register reg : graphCopy.vertices())
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
			withFewest.forEach(graphCopy::removeVertex);
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
			final L2Register reg = stack.removeLast();
			neighbors.clear();
			interferences.successorsOf(reg).stream()
				.mapToInt(L2Register::finalIndex)
				.filter(i -> i != -1)
				.forEach(neighbors::set);
			reg.setFinalIndex(neighbors.nextClearBit(0));
		}
	}
}
