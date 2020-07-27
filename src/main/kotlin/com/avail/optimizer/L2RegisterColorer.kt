/*
 * L2RegisterColorer.kt
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
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.utility.Graph
import com.avail.utility.cast
import java.util.ArrayDeque
import java.util.BitSet
import java.util.Deque
import java.util.HashSet

/**
 * Used to compute which registers can use the same storage due to not being
 * active at the same time.  This is called register coloring.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new register colorer for the given control flow graph.
 *
 * @param controlFlowGraph
 *   The given [L2ControlFlowGraph].
 */
class L2RegisterColorer constructor(controlFlowGraph: L2ControlFlowGraph)
{
	/**
	 * A collection of registers that should be colored the same.
	 */
	internal class RegisterGroup
	{
		/**
		 * The [Set] of [L2Register]s that may have the same color because they
		 * don't carry values at the same time.
		 */
		val registers: MutableSet<L2Register?> = HashSet()

		/** This group's final coloring, or -1 during calculation.  */
		var finalIndex = -1
			set(value)
			{
				field = value
				registers.forEach { it!!.setFinalIndex(value) }
			}

		override fun toString(): String = buildString {
			append("RegisterGroup: ")
			append(registers.joinToString(", ", "(", ")"))
		}
	}

	/**
	 * The [List] of all [L2Register]s that occur in the control flow graph.
	 */
	private val allRegisters: List<L2Register> = controlFlowGraph.allRegisters()

	/**
	 * The unique number of the register being traced from its uses back to its
	 * definition(s).
	 */
	private var registerBeingTraced: L2Register? = null

	/**
	 * A map from registers to sets of registers that can be colored the same
	 * due to coalesced moves.  The sets are mutable, and each member of such a
	 * set is a key in this map that points to that set.  Note that we can only
	 * bring together register sets that have no interference edge between them.
	 *
	 * Since interference edges prevent merging, we have to calculate the
	 * interference graph first.  We populate the registerSets with singleton
	 * sets before this.
	 */
	private val registerGroups = mutableMapOf<L2Register, RegisterGroup>()

	/**
	 * The set of blocks that so far have been reached, but not necessarily
	 * processed, while tracing the current register.
	 */
	private val reachedBlocks = mutableSetOf<L2BasicBlock>()

	/**
	 * The collection of blocks that may still need to be traced.  Ignore any
	 * that are in visitedBlocks.
	 */
	private val blocksToTrace: Deque<L2BasicBlock> = ArrayDeque()

	/**
	 * The interference graph, as it's being built.
	 */
	private val interferences: Graph<RegisterGroup> = Graph()

	/**
	 * Calculate the register interference graph.
	 */
	fun computeInterferenceGraph()
	{
		for (reg in allRegisters)
		{
			// Trace the register from each of its uses along all paths back to
			// its definition(s).  This is the lifetime of the register.  While
			// tracing this, the register is considered to interfere with any
			// encountered register use.  Deal with moves specially, avoiding
			// the addition of an interference edge due to the move, but
			// ensuring interference between a register and its move-buddy's
			// interfering neighbors.
			registerBeingTraced = reg
			for (read in reg.uses())
			{
				val instruction = read.instruction()
				if (instruction.operation().isPhi)
				{
					val phiOperation = instruction.operation()
						.cast<L2Operation?, L2_PHI_PSEUDO_OPERATION<*, *, *>>()
					for (predBlock in
						phiOperation.predecessorBlocksForUseOf(instruction, reg))
					{
						if (reachedBlocks.add(predBlock))
						{
							blocksToTrace.add(predBlock)
						}
					}
				}
				else
				{
					processLiveInAtStatement(
						instruction.basicBlock(),
						instruction.basicBlock().instructions().indexOf(
							instruction))
				}
				// Process the queue until empty.
				while (!blocksToTrace.isEmpty())
				{
					val blockToTrace = blocksToTrace.removeLast()
					// This actually does a live-out trace starting at the last
					// instruction.
					processLiveInAtStatement(
						blockToTrace, blockToTrace.instructions().size)
				}
			}
			reachedBlocks.clear()
		}
		registerBeingTraced = null
	}

	/**
	 * Trace the register uses starting at the specified instruction index in
	 * the given block.  If the index equals the number of instructions (which
	 * would be past the end in its zero-based numbering), start with a live-out
	 * processing of the last instruction.
	 *
	 * @param block
	 *   The [L2BasicBlock] in which to trace variable liveness.
	 * @param statementIndex
	 *   The zero-based index of the [L2Instruction] at which to begin tracing
	 *   for live-in.  If this equals the number of instructions in the block,
	 *   begin with a live-out trace at the last instruction.
	 */
	private fun processLiveInAtStatement(
		block: L2BasicBlock,
		statementIndex: Int)
	{
		assert(registerBeingTraced !== null)
		val instructions = block.instructions()
		for (index in statementIndex - 1 downTo 0)
		{
			// Process live-out for this instruction.
			val instruction = instructions[index]
			var definesCurrentRegister = false
			for (written in instruction.destinationRegisters())
			{
				if (written === registerBeingTraced)
				{
					definesCurrentRegister = true
					continue
				}
				if (registerGroups[registerBeingTraced]!!.registers.contains(
						written))
				{
					continue
				}
				// Register banks are numbered independently, so the notion
				// of interference between registers in different banks is
				// moot (i.e., they don't interfere).
				if (registerBeingTraced!!.registerKind()
					!== written.registerKind())
				{
					continue
				}
				// Moves count as interference between the live-out variable of
				// interest (registerBeingTraced) and the destination of the
				// move, but only if the live-out variable isn't also the source
				// of the move.
				if (instruction.operation().isMove
					&& instruction.sourceRegisters()[0] === registerBeingTraced)
				{
					continue
				}
				val group1 = registerGroups[registerBeingTraced]!!
				val group2 = registerGroups[written]!!
				interferences.includeEdge(group1, group2)
				interferences.includeEdge(group2, group1)
			}
			if (definesCurrentRegister)
			{
				// We reached the definition of the register.  Don't trace any
				// more of this block.
				return
			}
		}
		// We reached the start of the block without hitting the defining phi.
		// Continue tracing in each predecessor block.
		block.predecessorEdgesDo { sourceEdge: L2PcOperand ->
			val sourceBlock = sourceEdge.sourceBlock()
			if (reachedBlocks.add(sourceBlock))
			{
				blocksToTrace.add(sourceBlock)
			}
		}
	}

	/**
	 * Now that the interference graph has been constructed, merge together any
	 * non-interfering nodes that are connected by a move.
	 */
	fun coalesceNoninterferingMoves()
	{
		for (reg in allRegisters)
		{
			for (write in reg.definitions())
			{
				val instruction = write.instruction()
				if (instruction.operation().isMove)
				{
					// The source and destination registers shouldn't be
					// considered interfering if they'll hold the same value.
					val group1 = registerGroups[reg]
					val group2 =
						registerGroups[instruction.sourceRegisters()[0]]
					if (group1 !== group2)
					{
						if (!interferences.includesEdge(group1!!, group2!!))
						{
							// Merge the non-interfering move-related register
							// sets.
							val smallSet: RegisterGroup?
							val largeSet: RegisterGroup?
							if (group1.registers.size < group2.registers.size)
							{
								smallSet = group1
								largeSet = group2
							}
							else
							{
								smallSet = group2
								largeSet = group1
							}
							for (neighborOfSmall in
								interferences.successorsOf(smallSet))
							{
								assert(neighborOfSmall !== largeSet)
								interferences.includeEdge(
									largeSet, neighborOfSmall)
								interferences.includeEdge(
									neighborOfSmall, largeSet)
							}
							interferences.exciseVertex(smallSet)
							// Merge the smallSet elements into the largeSet.
							for (r in smallSet.registers)
							{
								registerGroups[r!!] = largeSet
							}
							largeSet.registers.addAll(smallSet.registers)
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
	 * The algorithm repeatedly chooses the registerSets having the least
	 * number of interfering edges, pushing them on a stack and removing the
	 * vertex (registerSet) and its edges.  We then repeatedly pop registers
	 * from the stack, choosing the lowest available color (finalIndex) that
	 * doesn't conflict with the coloring of a neighbor in the original graph.
	 */
	fun computeColors()
	{
		val stack: Deque<RegisterGroup> = ArrayDeque(allRegisters.size)
		val graphCopy = Graph(interferences)
		while (!graphCopy.isEmpty)
		{
			// Find the nodes having the fewest neighbors.
			var fewestCount = Int.MAX_VALUE
			val withFewest = mutableListOf<RegisterGroup>()
			for (reg in graphCopy.vertices)
			{
				val neighborCount = graphCopy.successorsOf(reg).size
				if (neighborCount < fewestCount)
				{
					fewestCount = neighborCount
					withFewest.clear()
				}
				if (fewestCount == neighborCount)
				{
					withFewest.add(reg)
				}
			}
			// We now have the collection of registers which tie for having the
			// fewest remaining neighbours interfering with them.  Push them,
			// removing them from the graphCopy, along with their connected
			// edges.  This reduces the cardinality of connected nodes.
			stack.addAll(withFewest)
			for (registerGroup in withFewest)
			{
				graphCopy.exciseVertex(registerGroup)
			}
		}
		// We've now stacked all nodes in a pretty good order for assigning
		// colors as we pop them.  In particular, if during the pushing phase we
		// never pushed a vertex with cardinality over K, then as we pop we'll
		// never encounter a situation where there are more than K neighbors
		// that already have colors, so there will be no more than K colors in
		// use for those neighbors.  So we can color the whole graph with at
		// most K+1 colors.
		val neighbors = BitSet()
		while (!stack.isEmpty())
		{
			val group = stack.removeLast()!!
			neighbors.clear()
			for (registerGroup in interferences.successorsOf(group))
			{
				val index = registerGroup.finalIndex
				if (index != -1)
				{
					neighbors.set(index)
				}
			}
			val color = neighbors.nextClearBit(0)
			group.finalIndex = color
		}
		for (register in registerGroups.keys)
		{
			assert(register.finalIndex() != -1)
		}
	}

	override fun toString(): String = buildString {
		append("Colorer:\n\tGroups:")
		val groups: Collection<RegisterGroup> =
			registerGroups.values.distinct().toList()
		groups.forEach {
			append("\n\t\t")
			append(it)
		}
		append("\n\tInterferences:")
		for (group in interferences.vertices)
		{
			val neighbors =
				interferences.successorsOf(group)
			if (neighbors.isNotEmpty())
			{
				append("\n\t\t")
				append(group)
				append(" ≠ ")
				append(neighbors)
			}
		}
	}

	init
	{
		allRegisters.forEach {
			val singleton = RegisterGroup()
			singleton.registers.add(it)
			registerGroups[it] = singleton
			interferences.addVertex(singleton)
		}
	}
}
