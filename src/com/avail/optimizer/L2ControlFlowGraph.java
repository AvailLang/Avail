/**
 * L2ControlFlowGraph.java
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
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.utility.Mutable;
import com.avail.utility.evaluation.Continuation1NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static java.util.stream.Collectors.toCollection;

/**
 * This is a control graph.  The vertices are {@link L2BasicBlock}s, which are
 * connected via their successor and predecessor lists.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2ControlFlowGraph
{
	/** The basic blocks, in their original creation order. */
	private final List<L2BasicBlock> basicBlockOrder = new ArrayList<>();

	/**
	 * Create a new {@link L2BasicBlock} in this control flow graph.  Don't
	 * connect it to anything yet.
	 *
	 * @param blockName The descriptive name of the basic block.
	 * @return The new basic block.
	 */
	public L2BasicBlock createBasicBlock (final String blockName)
	{
		final L2BasicBlock newBlock = new L2BasicBlock(blockName);
		basicBlockOrder.add(newBlock);
		return newBlock;
	}

	/**
	 * Find the {@link L2BasicBlock} that are actually reachable recursively
	 * from the blocks marked as {@link L2BasicBlock#isIrremovable()}.
	 *
	 * @return {@code true} if any blocks were removed, otherwise {@code false}.
	 */
	private boolean removeUnreachableBlocks ()
	{
		final Deque<L2BasicBlock> blocksToVisit =
			basicBlockOrder.stream()
				.filter(L2BasicBlock::isIrremovable)
				.collect(toCollection(ArrayDeque::new));
		final Set<L2BasicBlock> reachableBlocks = new HashSet<>();
		while (!blocksToVisit.isEmpty())
		{
			final L2BasicBlock block = blocksToVisit.removeLast();
			if (!reachableBlocks.contains(block))
			{
				reachableBlocks.add(block);
				for (final L2PcOperand edge : block.successorEdges())
				{
					blocksToVisit.add(edge.targetBlock());
				}
			}
		}

		final Set<L2BasicBlock> unreachableBlocks =
			new HashSet<>(basicBlockOrder);
		unreachableBlocks.removeAll(reachableBlocks);
		for (final L2BasicBlock block : unreachableBlocks)
		{
			block.instructions().forEach(L2Instruction::justRemoved);
			block.instructions().clear();
		}
		return basicBlockOrder.retainAll(reachableBlocks);
	}

	/**
	 * Given the set of instructions which are reachable, compute the needed
	 * subset, which consists of those which have side-effect or produce a value
	 * consumed by other needed instructions.
	 *
	 * @return The instructions that are needed and should be kept.
	 */
	private Set<L2Instruction> findNeededInstructions ()
	{
		final Deque<L2Instruction> instructionsToVisit = new ArrayDeque<>();
		for (final L2BasicBlock block : basicBlockOrder)
		{
			for (final L2Instruction instruction : block.instructions())
			{
				if (instruction.hasSideEffect())
				{
					instructionsToVisit.add(instruction);
				}
			}
		}
		// Recursively mark as needed all instructions that produce values
		// consumed by another needed instruction.
		final Set<L2Instruction> neededInstructions = new HashSet<>();
		while (!instructionsToVisit.isEmpty())
		{
			final L2Instruction instruction = instructionsToVisit.removeLast();
			if (!neededInstructions.contains(instruction))
			{
				neededInstructions.add(instruction);
				for (final L2Register sourceRegister
					: instruction.sourceRegisters())
				{
					final L2Instruction definingInstruction =
						sourceRegister.definition();
					instructionsToVisit.add(definingInstruction);
				}
			}
		}
		return neededInstructions;
	}

	/**
	 * Remove any unnecessary instructions.  Answer true if any were removed.
	 *
	 * @return Whether any dead instructions were removed or changed.
	 */
	private boolean removeDeadInstructions ()
	{
		boolean anyRemoved = false;
		final Set<L2Instruction> neededInstructions = findNeededInstructions();
		for (final L2BasicBlock block : basicBlockOrder)
		{
			final Iterator<L2Instruction> iterator =
				block.instructions().iterator();
			while (iterator.hasNext())
			{
				final L2Instruction instruction = iterator.next();
				if (!neededInstructions.contains(instruction))
				{
					anyRemoved = true;
					iterator.remove();
					instruction.justRemoved();
				}
			}
		}
		return anyRemoved;
	}

	/**
	 * Collect the list of all {@link L2Register} assigned anywhere within this
	 * control flow graph.
	 *
	 * @return A {@link List} of {@link L2Register}s.
	 */
	public List<L2Register> allRegisters ()
	{
		final List<L2Register> allRegisters = new ArrayList<>();
		for (final L2BasicBlock block : basicBlockOrder)
		{
			for (final L2Instruction instruction : block.instructions())
			{
				allRegisters.addAll(instruction.destinationRegisters());
			}
		}
		// This should only be used when the control flow graph is in SSA form,
		// so there should be no duplicates.
		assert allRegisters.stream().distinct().count() == allRegisters.size();
		return new ArrayList<>(allRegisters);
	}

	/**
	 * Every edge leading from a multiple-out block to a block with a phi
	 * function should have a new blank block inserted along it.  Normally this
	 * is done only along edges from multiple-out to multiple-in blocks, but the
	 * L2 instruction set has multi-way instructions that do more than just jump
	 * or branch based on the content of a register, so we need a place to
	 * insert the phi move <em>after</em> whatever computation has happened, but
	 * before arriving at the target block.
	 */
	private void transformToEdgeSplitSSA ()
	{
		// Copy the list of blocks to safely visit existing blocks while new
		// ones are added inside the loop.
		for (final L2BasicBlock sourceBlock : new ArrayList<>(basicBlockOrder))
		{
			// Copy the successor edges list to safely visit them with edges are
			// added and modified inside the loop.
			for (final L2PcOperand outEdge :
				new ArrayList<>(sourceBlock.successorEdges()))
			{
				final L2BasicBlock targetBlock = outEdge.targetBlock();
				final boolean hasPhis =
					!targetBlock.instructions().isEmpty()
						&& targetBlock.instructions().get(0).operation.isPhi();
				if (hasPhis && targetBlock.predecessorEdges().size() > 1)
				{
					outEdge.splitEdgeWith(this);
				}
			}
		}
	}

	/**
	 * For every phi operation, insert a move at the end of the block that leads
	 * to it.  Because of our version of edge splitting, that block always
	 * contains just a jump.
	 *
	 * <p>Also eliminate the phi functions.</p>
	 */
	private void insertPhiMoves ()
	{
		for (final L2BasicBlock block : basicBlockOrder)
		{
			final Iterator<L2Instruction> instructionIterator =
				block.instructions().iterator();
			while (instructionIterator.hasNext())
			{
				final L2Instruction instruction = instructionIterator.next();
				if (!(instruction.operation.isPhi()))
				{
					// Phi functions are always at the start, so we must be past
					// them, if any.
					break;
				}
				// Insert a non-SSA move in each previous block.
				final int targetRegIndex =
					instruction.destinationRegisters().get(0).finalIndex();
				final List<L2PcOperand> predecessors = block.predecessorEdges();
				final List<L2Register> phiSources =
					instruction.sourceRegisters();
				final int fanIn = predecessors.size();
				assert fanIn == phiSources.size();
				for (int i = 0; i < fanIn; i++)
				{
					final L2BasicBlock predecessor =
						predecessors.get(i).sourceBlock();
					final List<L2Instruction> instructions =
						predecessor.instructions();
					assert predecessor.finalInstruction().operation
						instanceof L2_JUMP;
					final L2ObjectRegister sourceReg =
						L2ObjectRegister.class.cast(phiSources.get(i));
					// Skip the move if it's already in a register with the same
					// finalIndex.  Otherwise assign it to a fake register with
					// the same finalIndex as the target.
					if (sourceReg.finalIndex() != targetRegIndex)
					{
						// TODO MvG - Eventually we'll need phis for int and
						// float registers.  We'll move responsibility for
						// constructing the move into the specific L2Operation
						// subclasses.
						final L2Instruction move =
							new L2Instruction(
								predecessor,
								L2_MOVE.instance,
								new L2ReadPointerOperand(sourceReg, null),
								new L2WritePointerOperand(-999, TOP.o(), null));
						instructions.add(instructions.size() - 2, move);
						instruction.destinationRegisters().get(0).setFinalIndex(
							targetRegIndex);
					}
				}
				// Eliminate the phi function itself.
				instructionIterator.remove();
			}
		}
	}

	/**
	 * Any control flow edges that land on jumps should be redirected to the
	 * ultimate target of the jump, taking into account chains of jumps.
	 */
	private void adjustEdgesToJumps ()
	{
		boolean changed;
		do
		{
			changed = false;
			final Iterator<L2BasicBlock> blockIterator =
				basicBlockOrder.iterator();
			while (blockIterator.hasNext())
			{
				final L2BasicBlock block = blockIterator.next();
				if (block.instructions().size() == 1
					&& block.finalInstruction().operation instanceof L2_JUMP)
				{
					// Redirect all predecessors through the jump.
					final L2BasicBlock jumpTarget =
						block.finalInstruction().targetEdges().get(0)
							.targetBlock();
					for (final L2PcOperand inEdge
						: new ArrayList<>(block.predecessorEdges()))
					{
						changed = true;
						inEdge.switchTargetBlockNonSSA(jumpTarget);
					}
					// Eliminate the block, unless it has to be there for
					// external reasons (i.e., it's an L2 entry point).
					assert block.predecessorEdges().isEmpty();
					if (!block.isIrremovable())
					{
						blockIterator.remove();
					}
				}
			}
		}
		while (changed);
	}

	/**
	 * Re-order the blocks to minimize the number of pointless jumps.  When we
	 * start generating JVM code, this should also try to make one of the paths
	 * from conditional branches come after the branch, otherwise an extra jump
	 * instruction has to be generated.
	 *
	 * <p>The initial block should always come first.</p>
	 *
	 * <p>For now, use the simple heuristic of only placing a block if all its
	 * predecessors have been placed (or if there are only cycle unplaced, pick
	 * one arbitrarily).</p>
	 */
	private void orderBlocks ()
	{
		final Map<L2BasicBlock, Mutable<Integer>> countdowns = new HashMap<>();
		for (final L2BasicBlock block : basicBlockOrder)
		{
			countdowns.put(
				block, new Mutable<>(block.predecessorEdges().size()));
		}
		final List<L2BasicBlock> order =
			new ArrayList<>(basicBlockOrder.size());
		final Deque<L2BasicBlock> zeroed = new ArrayDeque<>();
		final Continuation1NotNull<L2BasicBlock> select = block ->
		{
			order.add(block);
			block.successorEdges().forEach(
				edge ->
				{
					final Mutable<Integer> countdown =
						countdowns.get(edge.targetBlock());
					if (--countdown.value == 0)
					{
						countdowns.remove(edge.targetBlock());
						zeroed.add(edge.targetBlock());
					}
				});
		};
		assert basicBlockOrder.get(0).predecessorEdges().isEmpty();
		for (int i = basicBlockOrder.size() - 1; i >= 0; i--)
		{
			if (basicBlockOrder.get(i).predecessorEdges().isEmpty())
			{
				zeroed.add(basicBlockOrder.get(i));
			}
		}
		assert zeroed.getLast() == basicBlockOrder.get(0);
		while (!countdowns.isEmpty())
		{
			if (!zeroed.isEmpty())
			{
				select.value(zeroed.removeLast());
			}
			else
			{
				// Should only happen when there are cycles.  Pick a node at
				// random for now.
				final L2BasicBlock victim =
					countdowns.keySet().iterator().next();
				countdowns.remove(victim);
				zeroed.add(victim);
			}
		}
		assert order.get(0) == basicBlockOrder.get(0);
		basicBlockOrder.clear();
		basicBlockOrder.addAll(order);
	}

	/**
	 * Remove all unreachable blocks and all instructions that don't either have
	 * a side-effect or produce a value ultimately used by an instruction that
	 * has a side-effect.
	 */
	private void removeDeadCode ()
	{
		//noinspection StatementWithEmptyBody
		while (removeUnreachableBlocks() || removeDeadInstructions()) { }
	}

	/**
	 * Optimize the graph of instructions.
	 */
	public void optimize ()
	{
		removeDeadCode();

		// Color all registers.  This creates a dense finalIndex numbering for
		// the registers in such a way that no two registers that have to
		// maintain distinct values at the same time will have the same number.
		final L2RegisterColorer colorer = new L2RegisterColorer(this);
		colorer.colorRegisters();

		// Transform into SSA edge-split form, to avoid inserting redundant
		// phi-moves.
		transformToEdgeSplitSSA();

		// Insert phi moves, which makes it no longer in SSA form.  Note that we
		// don't insert a phi move if the source and target registers have the
		// same color (finalIndex).
		insertPhiMoves();

		// Every L2PcOperand that leads to an L2_JUMP should now be redirected
		// to the target of the jump (transitively, if the jump leads to another
		// jump).  We specifically do this after inserting phi moves to ensure
		// we don't jump past irremovable phi moves.
		adjustEdgesToJumps();

		// Rewiring jumps through target jumps may have caused some code to be
		// unreachable.
		removeDeadCode();

		// Choose an order for the blocks.  This isn't important while we're
		// interpreting L2Chunks, but it will ultimately affect the quality of
		// JVM translation.  Prefer to have the target block of an unconditional
		// jump to follow the jump, since final code generation elides the jump.
		orderBlocks();

		// MvG TODO - Optimizations ideas:
		//    -Strengthen the types of all registers and register uses.
		//    -Ask instructions to regenerate if they want.
		//    -When optimizing, keep track of when a TypeRestriction on a phi
		//     register is too weak to qualify, but the types of some of the phi
		//     source registers would qualify it for a reasonable expectation of
		//     better performance.  Write a hint into such phis.  If we have a
		//     high enough requested optimization level, apply code-splitting.
		//     The block that defines that phi can be duplicated for each
		//     interesting incoming edge.  That way the duplicated blocks will
		//     get more specific types to work with.
		//    -Splitting for int32s.
		//    -Leverage more inter-primitive identities.
		//    -JVM target.
	}

	public void generateOn (final List<L2Instruction> instructions)
	{
		for (final L2BasicBlock block : basicBlockOrder)
		{
			block.generateOn(instructions);
		}
	}
}
