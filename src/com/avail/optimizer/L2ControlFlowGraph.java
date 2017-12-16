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

import com.avail.annotations.InnerAccess;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteVectorOperand;
import com.avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.Mutable;
import com.avail.utility.Pair;
import com.avail.utility.evaluation.Continuation1;

import javax.annotation.Nullable;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.Strings.increaseIndentation;
import static java.util.Collections.disjoint;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

/**
 * This is a control graph.  The vertices are {@link L2BasicBlock}s, which are
 * connected via their successor and predecessor lists.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2ControlFlowGraph
{
	/** Whether to sanity-check the graph between optimization steps. */
	public static boolean shouldSanityCheck = false;

	/** The basic blocks, in their original creation order. */
	private final List<L2BasicBlock> basicBlockOrder = new ArrayList<>();

	private @Nullable L2RegisterColorer colorer = null;

	/**
	 * An {@link AtomicInteger} used to quickly generate unique integers which
	 * serve to visually distinguish new registers.
	 */
	private int uniqueCounter = 0;

	/**
	 * Answer the next value from the unique counter.  This is only used to
	 * distinguish registers for visual debugging.
	 *
	 * @return A int.
	 */
	int nextUnique ()
	{
		return uniqueCounter++;
	}

	/**
	 * Begin code generation in the given block.
	 *
	 * @param block
	 *        The {@link L2BasicBlock} in which to start generating {@link
	 *        L2Instruction}s.
	 */
	public void startBlock (final L2BasicBlock block)
	{
		assert block.instructions().isEmpty();
		assert !basicBlockOrder.contains(block);
		if (block.isIrremovable() || block.hasPredecessors())
		{
			basicBlockOrder.add(block);
		}
	}

	/**
	 * Find the {@link L2BasicBlock} that are actually reachable recursively
	 * from the blocks marked as {@link L2BasicBlock#isIrremovable()}.
	 *
	 * @return {@code true} if any blocks were removed, otherwise {@code false}.
	 */
	@InnerAccess boolean removeUnreachableBlocks ()
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
	 * consumed by other needed instructions.  Don't assume SSA.
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
					// Assume all definitions are needed, regardless of control
					// flow.
					instructionsToVisit.addAll(sourceRegister.definitions());
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
	 * Remove all unreachable blocks and all instructions that don't either have
	 * a side-effect or produce a value ultimately used by an instruction that
	 * has a side-effect.
	 */
	@InnerAccess void removeDeadCode ()
	{
		//noinspection StatementWithEmptyBody
		while (removeUnreachableBlocks() || removeDeadInstructions()) { }
	}

	/**
	 * For every edge leading from a multiple-out block to a multiple-in block,
	 * split it by inserting a new block along it.  Note that we do this
	 * regardless of whether the target block has any phi functions.
	 */
	@InnerAccess void transformToEdgeSplitSSA ()
	{
		// Copy the list of blocks, to safely visit existing blocks while new
		// ones are added inside the loop.
		for (final L2BasicBlock sourceBlock : new ArrayList<>(basicBlockOrder))
		{
			final List<L2PcOperand> successorEdges =
				sourceBlock.successorEdges();
			if (successorEdges.size() > 1)
			{
				for (final L2PcOperand edge : new ArrayList<>(successorEdges))
				{
					final L2BasicBlock targetBlock = edge.targetBlock();
					if (targetBlock.predecessorEdges().size() > 1)
					{
						final L2BasicBlock newBlock = edge.splitEdgeWith(this);
						// Add it somewhere that looks sensible for debugging,
						// although we'll order the blocks later.
						basicBlockOrder.add(
							basicBlockOrder.indexOf(targetBlock), newBlock);
					}
				}
			}
		}
	}

	/**
	 * Determine which registers are live-in for each block.  We distinguish
	 * between always-live-in, where all future paths from the start of a block
	 * lead to a use of the register, and sometimes-live-in, where at least one
	 * future path from the start of the block leads to a use of the register.
	 */
	@InnerAccess void computeLivenessAtEachEdge ()
	{
		for (final L2BasicBlock block : basicBlockOrder)
		{
			block.alwaysLiveInRegisters.clear();
			block.sometimesLiveInRegisters.clear();
		}

		// The deque and the set maintain the same membership.
		final Deque<L2BasicBlock> workQueue = new ArrayDeque<>(basicBlockOrder);
		final Set<L2BasicBlock> workSet = new HashSet<>(basicBlockOrder);
		while (!workQueue.isEmpty())
		{
			final L2BasicBlock block = workQueue.removeLast();
			workSet.remove(block);
			// Take the union of the outbound edges' sometimes-live registers.
			// Also find the intersection of those edges' always-live registers.
			final Set<L2Register> alwaysLive = new HashSet<>();
			final List<L2PcOperand> successorEdges = block.successorEdges();
			if (!successorEdges.isEmpty())
			{
				alwaysLive.addAll(
					successorEdges.get(0).targetBlock().alwaysLiveInRegisters);
			}
			final Set<L2Register> sometimesLive = new HashSet<>();
			for (final L2PcOperand edge : successorEdges)
			{
				sometimesLive.addAll(
					edge.targetBlock().sometimesLiveInRegisters);
				alwaysLive.retainAll(edge.targetBlock().alwaysLiveInRegisters);
			}
			// Now work backward through each instruction, removing registers
			// that it writes, and adding registers that it reads.
			final List<L2Instruction> instructions = block.instructions();
			for (int i = instructions.size() - 1; i >= 0; i--)
			{
				final L2Instruction instruction = instructions.get(i);
				sometimesLive.removeAll(instruction.destinationRegisters());
				sometimesLive.addAll(instruction.sourceRegisters());
				alwaysLive.removeAll(instruction.destinationRegisters());
				alwaysLive.addAll(instruction.sourceRegisters());
			}
			boolean changed =
				block.sometimesLiveInRegisters.addAll(sometimesLive);
			changed |= block.alwaysLiveInRegisters.addAll(alwaysLive);
			if (changed)
			{
				// We added to the known live registers of this block.
				// Continue propagating to its predecessors.
				for (final L2PcOperand edge : block.predecessorEdges())
				{
					final L2BasicBlock predecessor = edge.sourceBlock();
					if (!workSet.contains(predecessor))
					{
						workQueue.addFirst(predecessor);
						workSet.add(predecessor);
					}
				}
			}
		}
	}

	/**
	 * Determine which registers are written by instructions which have side
	 * effects.  Add all such registers to the live-in sets of <em>each</em>
	 * block.  This avoids having to consider later whether to attempt to move
	 * such instructions into successor blocks.
	 */
	@InnerAccess void considerSideEffectRegistersLive ()
	{
		final Set<L2Register> registers = new HashSet<>();
		for (final L2Register register : allRegisters())
		{
			if (register.definition().hasSideEffect())
			{
				registers.add(register);
			}
		}
		for (final L2BasicBlock block : basicBlockOrder)
		{
			block.sometimesLiveInRegisters.addAll(registers);
			block.alwaysLiveInRegisters.addAll(registers);
		}
	}

	/**
	 * Try to move any side-effectless defining instructions to later points in
	 * the control flow graph.  If such an instruction defines a register that's
	 * used in the same basic block, don't bother moving it.  Also don't attempt
	 * to move it if it's always-live-in at each successor block, since the
	 * point of moving it forward is to avoid inessential computations.
	 *
	 * <p>So in the remaining case that the register is used in only some
	 * future paths, we attempt to move copies of the instruction into each
	 * successor that may require it.  Note that this can be <em>all</em> of the
	 * successors, if some of them are only maybe-live-in.</p>
	 *
	 * <p>This process is repeated until no more instructions are eligible to
	 * move forward.</p>
	 *
	 * <p>This requires edge-split SSA form as input, but duplicated defining
	 * instructions break SSA.</p>
	 */
	@InnerAccess void postponeConditionallyUsedValues ()
	{
		boolean changed;
		do
		{
			changed = false;
			for (final L2BasicBlock block : basicBlockOrder)
			{
				// Copy the instructions list, since instructions may be removed
				// from it as we iterate.
				final List<L2Instruction> instructions =
					new ArrayList<>(block.instructions());
				for (int i = instructions.size() - 1; i >= 0; i--)
				{
					final L2Instruction instruction = instructions.get(i);
					final @Nullable List<L2BasicBlock> blocksToMoveTo =
						successorBlocksToMoveTo(instruction);
					if (blocksToMoveTo != null)
					{
						assert !blocksToMoveTo.isEmpty();
						changed = true;
						for (final L2BasicBlock destinationBlock
							: blocksToMoveTo)
						{
							final L2Instruction newInstruction =
								new L2Instruction(
									destinationBlock,
									instruction.operation,
									instruction.operands);
							destinationBlock.insertInstruction(
								0, newInstruction);
							// None of the registers defined by the instruction
							// should be live-in any more at the new block.
							destinationBlock.sometimesLiveInRegisters.removeAll(
								newInstruction.destinationRegisters());
							destinationBlock.alwaysLiveInRegisters.removeAll(
								newInstruction.destinationRegisters());
							destinationBlock.sometimesLiveInRegisters.addAll(
								newInstruction.sourceRegisters());
							destinationBlock.alwaysLiveInRegisters.addAll(
								newInstruction.sourceRegisters());
						}
						block.instructions().remove(instruction);
						instruction.justRemoved();
					}
				}
			}
		}
		while (changed);
	}

	/**
	 * If this instruction can be moved/duplicated into one or more successor
	 * blocks, answer a {@link List} of those blocks.  Otherwise answer {@code
	 * null}.
	 *
	 * @param instruction The instruction to analyze.
	 * @return The successor {@link L2BasicBlock}s to which the instruction can
	 *         be moved, or {@code null} if there is no such successor block.
	 */
	private static @Nullable List<L2BasicBlock> successorBlocksToMoveTo (
		final L2Instruction instruction)
	{
		if (instruction.hasSideEffect()
			|| instruction.altersControlFlow()
			|| instruction.operation.isPhi())
		{
			return null;
		}
		final List<L2Register> written = instruction.destinationRegisters();
		assert !written.isEmpty()
			: "Every instruction should either have side effects or write to "
			+ "at least one register";
		final L2BasicBlock block = instruction.basicBlock;
		final List<L2PcOperand> successorEdges = block.successorEdges();
		if (successorEdges.size() == 1)
		{
			// There's only one successor edge.  Since the CFG is in edge-split
			// form, the successor might have multiple predecessors.  Don't move
			// across the edge in that case, since it may cause the instruction
			// to run in situations that it doesn't need to.  When code
			// splitting is eventually implemented, it should clean up this case
			// by duplicating the successor block just for this edge.
			final L2BasicBlock successor = successorEdges.get(0).targetBlock();
			if (successor.predecessorEdges().size() > 1)
			{
				return null;
			}
		}
		final List<L2BasicBlock> destinations = new ArrayList<>();
		boolean shouldMoveInstruction = false;
		for (final L2PcOperand edge : successorEdges)
		{
			final L2BasicBlock targetBlock = edge.targetBlock();
			assert targetBlock.predecessorEdges().size() == 1
				: "CFG is not in edge-split form";
			if (!targetBlock.alwaysLiveInRegisters.containsAll(written))
			{
				shouldMoveInstruction = true;
			}
			if (!disjoint(targetBlock.sometimesLiveInRegisters, written))
			{
				destinations.add(targetBlock);
			}
		}
		if (!shouldMoveInstruction)
		{
			// It was always-live-in for every successor.
			return null;
		}

		// Now see if there are any intervening instructions that consume any of
		// the given instruction's outputs.  That would keep it from moving.
		final List<L2Instruction> instructions = block.instructions();
		final int instructionsSize = instructions.size();
		for (
			int i = instructions.indexOf(instruction) + 1;
			i < instructionsSize;
			i++)
		{
			final L2Instruction interveningInstruction = instructions.get(i);
			if (!disjoint(interveningInstruction.sourceRegisters(), written))
			{
				// We can't move the given instruction past an instruction that
				// uses one of its outputs.
				return null;
			}
		}
		assert !destinations.isEmpty();
		return destinations;
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
		return allRegisters.stream().distinct().collect(toList());
	}

	/**
	 * For every phi operation, insert a move at the end of the block that leads
	 * to it.  Because of our version of edge splitting, that block always
	 * contains just a jump.  The CFG will no longer be in SSA form, because the
	 * phi variables will have multiple defining instructions (the moves).
	 *
	 * <p>Also eliminate the phi functions.</p>
	 */
	@InnerAccess void insertPhiMoves ()
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
				final L2WritePointerOperand targetWriter =
					L2_PHI_PSEUDO_OPERATION.destinationRegisterWrite(
						instruction);
				final List<L2PcOperand> predecessors = block.predecessorEdges();
				final List<L2Register> phiSources =
					instruction.sourceRegisters();
				final int fanIn = predecessors.size();
				assert fanIn == phiSources.size();

				// Insert a non-SSA move in each predecessor block.
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

					// TODO MvG - Eventually we'll need phis for int and
					// float registers.  We'll move responsibility for
					// constructing the move into the specific L2Operation
					// subclasses.
					final L2Instruction move =
						new L2Instruction(
							predecessor,
							L2_MOVE.instance,
							new L2ReadPointerOperand(sourceReg, null),
							targetWriter);
					instructions.add(instructions.size() - 1, move);
					move.justAdded();
				}
				// Eliminate the phi function itself.
				instructionIterator.remove();
				instruction.justRemoved();
			}
		}
	}

	/**
	 * Determine which pairs of registers have to be simultaneously live and
	 * potentially holding distinct values.
	 */
	@InnerAccess void computeInterferenceGraph ()
	{
		colorer = new L2RegisterColorer(this);
		colorer.computeInterferenceGraph();
	}

	/**
	 * For each {@link L2_MOVE} instruction, if the register groups associated
	 * with the source and destination registers don't have an interference edge
	 * between them then merge the groups together.  The resulting merged group
	 * should have interferences with each group that the either the source
	 * register's group or the destination register's group had interferences
	 * with.
	 */
	@InnerAccess void coalesceNoninterferingMoves ()
	{
		stripNull(colorer).coalesceNoninterferingMoves();
	}

	/**
	 * Assign final coloring to each register based on the interference graph
	 * and coalescing map.
	 */
	@InnerAccess void computeColors ()
	{
		stripNull(colorer).computeColors();
		colorer = null;
	}

	/**
	 * Create a new register for every &lt;kind, finalIndex&gt; (i.e., color) of
	 * an existing register, then transform every instruction of this control
	 * flow graph to use the new registers.  The new registers have a
	 * {@link L2Register#uniqueValue} that's the same as its {@link
	 * L2Register#finalIndex}.
	 */
	@InnerAccess void replaceRegistersByColor ()
	{
		// Create new registers for each <kind, finalIndex> in the existing
		// registers.
		final EnumMap<RegisterKind, Map<Integer, L2Register>> byKindAndIndex
			= new EnumMap<>(RegisterKind.class);
		final Map<L2Register, L2Register> remap = new HashMap<>();
		// Also collect all the old registers.
		final HashSet<L2Register> oldRegisters = new HashSet<>();
		basicBlockOrder.forEach(
			block -> block.instructions().forEach(
				instruction ->
				{
					final Consumer<L2Register> action = reg ->
					{
						remap.put(
							reg,
							byKindAndIndex
								.computeIfAbsent(
									reg.registerKind(),
									k -> new HashMap<>())
								.computeIfAbsent(
									reg.finalIndex(),
									i -> reg.copyAfterColoring()));
						oldRegisters.add(reg);
					};
					instruction.sourceRegisters().forEach(action);
					instruction.destinationRegisters().forEach(action);
				}
			));
		// Actually remap every register.
		basicBlockOrder.forEach(
			block -> block.instructions().forEach(
				instruction -> instruction.replaceRegisters(remap)));
		// Check that the obsolete registers have no uses or definitions.
		oldRegisters.forEach(
			r ->
			{
				assert r.uses().isEmpty() && r.definitions().isEmpty()
					: "OBSOLETE register still refers to instructions";
			});
	}

	/**
	 * Eliminate any {@link L2_MOVE}s between registers of the same color.  The
	 * graph must have been colored already, and is not expected to be in SSA
	 * form, and is certainly not after this, since removed moves are the SSA
	 * definition points for their target registers.
	 */
	@InnerAccess void removeSameColorMoves ()
	{
		for (final L2BasicBlock block : basicBlockOrder)
		{
			final Iterator<L2Instruction> iterator =
				block.instructions().iterator();
			while (iterator.hasNext())
			{
				final L2Instruction instruction = iterator.next();
				if (instruction.operation.isMove()
					&& instruction.sourceRegisters().get(0).finalIndex()
					== instruction.destinationRegisters().get(0).finalIndex())
				{
					iterator.remove();
					instruction.justRemoved();
				}
			}
		}
	}

	/**
	 * Any control flow edges that land on jumps should be redirected to the
	 * ultimate target of the jump, taking into account chains of jumps.
	 */
	@InnerAccess void adjustEdgesLeadingToJumps ()
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
					final L2PcOperand jumpEdge =
						block.finalInstruction().targetEdges().get(0);
					final L2BasicBlock jumpTarget = jumpEdge.targetBlock();
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
						jumpTarget.predecessorEdges().remove(jumpEdge);
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
	@InnerAccess void orderBlocks ()
	{
		final Map<L2BasicBlock, Mutable<Integer>> countdowns = new HashMap<>();
		for (final L2BasicBlock block : basicBlockOrder)
		{
			countdowns.put(
				block, new Mutable<>(block.predecessorEdges().size()));
		}
		final List<L2BasicBlock> order =
			new ArrayList<>(basicBlockOrder.size());
		assert basicBlockOrder.get(0).predecessorEdges().isEmpty();
		final Deque<L2BasicBlock> zeroed = new ArrayDeque<>();
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
				final L2BasicBlock block = zeroed.removeLast();
				order.add(block);
				block.successorEdges().forEach(
					edge ->
					{
						final @Nullable Mutable<Integer> countdown =
							countdowns.get(edge.targetBlock());
						// Note that the entry may have been removed to break a
						// cycle.  See below.
						if (countdown != null && --countdown.value == 0)
						{
							countdowns.remove(edge.targetBlock());
							zeroed.add(edge.targetBlock());
						}
					});
			}
			else
			{
				// Only cycles and blocks reachable from cycles are left.  Pick
				// a node at random, preferring one that has had at least one
				// predecessor placed.
				@Nullable L2BasicBlock victim = null;
				for (final Entry<L2BasicBlock, Mutable<Integer>> entry
					: countdowns.entrySet())
				{
					if (entry.getValue().value
						< entry.getKey().predecessorEdges().size())
					{
						victim = entry.getKey();
						break;
					}
				}
				// No remaining block has had a predecessor placed.  Pick a
				// block at random.
				if (victim == null)
				{
					victim = countdowns.keySet().iterator().next();
				}
				countdowns.remove(victim);
				zeroed.add(victim);
			}
		}

		assert order.size() == basicBlockOrder.size();
		assert order.get(0) == basicBlockOrder.get(0);
		basicBlockOrder.clear();
		basicBlockOrder.addAll(order);
	}

	private static class UsedRegisters
	{
		BitSet liveObjectRegisters;
		BitSet liveIntRegisters;

		boolean restrictTo (final UsedRegisters another)
		{
			final int objectCount = liveObjectRegisters.cardinality();
			final int intCount = liveIntRegisters.cardinality();
			liveObjectRegisters.and(another.liveObjectRegisters);
			liveIntRegisters.and(another.liveIntRegisters);
			return liveObjectRegisters.cardinality() != objectCount
				|| liveIntRegisters.cardinality() != intCount;
		}

		void readRegister (
			final L2Register register,
			final Function<L2Register, Integer> registerIdFunction)
		{
			switch (register.registerKind())
			{
				case OBJECT:
					assert liveObjectRegisters.get(
						registerIdFunction.apply(register));
					break;
				case INTEGER:
					assert liveIntRegisters.get(
						registerIdFunction.apply(register));
					break;
				default:
					assert false : "Unsupported register type";
			}
		}

		void writeRegister (
			final L2Register register,
			final Function<L2Register, Integer> registerIdFunction)
		{
			switch (register.registerKind())
			{
				case OBJECT:
					liveObjectRegisters.set(
						registerIdFunction.apply(register));
					break;
				case INTEGER:
					liveIntRegisters.set(
						registerIdFunction.apply(register));
					break;
				default:
					assert false : "Unsupported register type";
			}
		}

		void clearAll ()
		{
			liveObjectRegisters.clear();
			liveIntRegisters.clear();
		}

		UsedRegisters (
			final BitSet liveObjectRegisters,
			final BitSet liveIntRegisters)
		{
			this.liveObjectRegisters = (BitSet) liveObjectRegisters.clone();
			this.liveIntRegisters = (BitSet) liveIntRegisters.clone();
		}
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		for (final L2BasicBlock block : basicBlockOrder)
		{
			builder.append(block.name());
			builder.append(":\n");
			for (final L2Instruction instruction : block.instructions())
			{
				builder.append("\t");
				builder.append(increaseIndentation(instruction.toString(), 1));
				builder.append("\n");
			}
			builder.append("\n");
		}
		return builder.toString();
	}

	/**
	 * Check that each instruction of each block has that block set for its
	 * {@link L2Instruction#basicBlock} field.  Also check that every
	 * instruction's applicable operands are listed as uses or definitions of
	 * the register that they access, and that there are no other uses or
	 * definitions.
	 */
	private void checkBlocksAndInstructions ()
	{
		final Map<L2Register, Set<L2Instruction>> uses = new HashMap<>();
		final Map<L2Register, Set<L2Instruction>> definitions = new HashMap<>();
		basicBlockOrder.forEach(
			block -> block.instructions().forEach(
				instruction ->
				{
					assert instruction.basicBlock == block;
					instruction.sourceRegisters().forEach(
						reg -> uses.computeIfAbsent(reg, r -> new HashSet<>())
							.add(instruction));
					instruction.destinationRegisters().forEach(
						reg -> definitions.computeIfAbsent(
								reg, r -> new HashSet<>())
							.add(instruction));
				}));
		final Set<L2Register> mentionedRegs = new HashSet<>(uses.keySet());
		mentionedRegs.addAll(definitions.keySet());
		for (final L2Register reg : mentionedRegs)
		{
			assert uses.getOrDefault(reg, emptySet()).equals(reg.uses());
			assert definitions.getOrDefault(reg, emptySet()).equals(
				reg.definitions());
		}
	}

	/**
	 * Ensure all instructions' operands occur only once, including within
	 * vector operands.
	 */
	private void checkUniqueOperands ()
	{
		final Set<L2Operand> allOperands = new HashSet<>();
		basicBlockOrder.forEach(
			block -> block.instructions().forEach(
				instruction ->
				{
					for (final L2Operand operand : instruction.operands)
					{
						final boolean added = allOperands.add(operand);
						assert added;
						if (L2ReadVectorOperand.class.isInstance(operand))
						{
							final L2ReadVectorOperand vector =
								L2ReadVectorOperand.class.cast(operand);
							vector.elements().forEach(
								read ->
								{
									final boolean ok = allOperands.add(read);
									assert ok;
								});
						}
						else if (L2WriteVectorOperand.class.isInstance(operand))
						{
							final L2WriteVectorOperand vector =
								L2WriteVectorOperand.class.cast(operand);
							vector.elements().forEach(
								write ->
								{
									final boolean ok = allOperands.add(write);
									assert ok;
								});
						}
					}
				}
			));
	}

	/**
	 * Check that all edges are correctly connected, and that phi functions have
	 * the right number of inputs.
	 */
	private void checkEdgesAndPhis ()
	{
		for (final L2BasicBlock block : basicBlockOrder)
		{
			for (final L2Instruction instruction : block.instructions())
			{
				assert !instruction.operation.isPhi()
					|| instruction.sourceRegisters().size()
					== block.predecessorEdges().size();
			}
			// Check edges going forward.
			final L2Instruction lastInstruction = block.finalInstruction();
			assert lastInstruction.targetEdges().equals(block.successorEdges());
			for (final L2PcOperand edge : block.successorEdges())
			{
				assert edge.sourceBlock() == block;
				final L2BasicBlock targetBlock = edge.targetBlock();
				assert basicBlockOrder.contains(targetBlock);
				assert targetBlock.predecessorEdges().contains(edge);
			}
			// Also check edges going backward.
			for (final L2PcOperand backEdge : block.predecessorEdges())
			{
				assert backEdge.targetBlock() == block;
				final L2BasicBlock predecessorBlock = backEdge.sourceBlock();
				assert basicBlockOrder.contains(predecessorBlock);
				assert predecessorBlock.successorEdges().contains(backEdge);
			}
		}
	}

	/**
	 * Perform a basic sanity check on the instruction graph, ensuring that each
	 * use of a register is preceded in all histories by a write to it.  Use the
	 * provided function to indicate what "the same" register means, so that
	 * this can be used for uncolored SSA and colored non-SSA graphs.
	 *
	 * @param registerIdFunction
	 *        A function that transforms a register into the index that should
	 *        be used to identify it.  This allows pre-colored and post-colored
	 *        register uses to be treated differently.
	 */
	private void checkRegistersAreInitialized (
		final Function<L2Register, Integer> registerIdFunction)
	{
		final Deque<Pair<L2BasicBlock, UsedRegisters>> blocksToCheck =
			new ArrayDeque<>();
		blocksToCheck.add(
			new Pair<>(
				basicBlockOrder.get(0),
				new UsedRegisters(new BitSet(), new BitSet())));
		final Map<L2BasicBlock, UsedRegisters> inSets = new HashMap<>();
		while (!blocksToCheck.isEmpty())
		{
			final Pair<L2BasicBlock, UsedRegisters> pair =
				blocksToCheck.removeLast();
			final L2BasicBlock block = pair.first();
			final UsedRegisters newUsed = pair.second();
			@Nullable UsedRegisters checked = inSets.get(block);
			if (checked == null)
			{
				checked = new UsedRegisters(
					newUsed.liveObjectRegisters, newUsed.liveIntRegisters);
				inSets.put(block, checked);
			}
			else
			{
				if (!checked.restrictTo(newUsed))
				{
					// We've already checked this block with this restricted set
					// of registers.  Ignore this path.
					continue;
				}
			}
			// Check the block (or check it again with fewer valid registers)
			final UsedRegisters workingSet = new UsedRegisters(
				checked.liveObjectRegisters, checked.liveIntRegisters);
			for (final L2Instruction instruction : block.instructions())
			{
				if (instruction.operation instanceof L2_ENTER_L2_CHUNK)
				{
					// Wipe all registers.
					workingSet.clearAll();
				}
				if (!instruction.operation.isPhi())
				{
					for (final L2Register register :
						instruction.sourceRegisters())
					{
						workingSet.readRegister(register, registerIdFunction);
					}
					for (final L2Register register :
						instruction.destinationRegisters())
					{
						workingSet.writeRegister(register, registerIdFunction);
					}
				}
			}
			for (final L2PcOperand edge : block.successorEdges())
			{
				// Handle the phi instructions of the target here.  Create a
				// workingCopy for each edge.
				final UsedRegisters workingCopy = new UsedRegisters(
					workingSet.liveObjectRegisters,
					workingSet.liveIntRegisters);
				final L2BasicBlock targetBlock = edge.targetBlock();
				final int predecessorIndex =
					targetBlock.predecessorEdges().indexOf(edge);
				if (predecessorIndex == -1)
				{
					System.out.println("Phi predecessor not found");
					assert false : "Phi predecessor not found";
				}
				for (final L2Instruction phiInTarget
					: targetBlock.instructions())
				{
					if (!phiInTarget.operation.isPhi())
					{
						// All the phis are at the start of the block.
						break;
					}
					final L2Register phiSource =
						phiInTarget.sourceRegisters().get(predecessorIndex);
					workingCopy.readRegister(phiSource, registerIdFunction);
					workingCopy.writeRegister(
						phiInTarget.destinationRegisters().get(0),
						registerIdFunction);
				}
				blocksToCheck.add(new Pair<>(edge.targetBlock(), workingCopy));
			}
		}
	}

	/**
	 * Perform a basic sanity check on the instruction graph.
	 *
	 * @param interpreter
	 *        The current {@link Interpreter}.
	 */
	private void sanityCheck (final Interpreter interpreter)
	{
		if (shouldSanityCheck)
		{
			final long before = System.nanoTime();
			checkBlocksAndInstructions();
			checkUniqueOperands();
			checkEdgesAndPhis();
			checkRegistersAreInitialized(L2Register::uniqueValue);
			final long after = System.nanoTime();
			sanityCheckStat.record(
				after - before, interpreter.interpreterIndex);
		}
	}

	enum OPTIMIZATION_PHASE
	{
		/**
		 * Start by eliminating debris created during the initial L1 → L2
		 * translation.
		 */
		REMOVE_DEAD_CODE_1(L2ControlFlowGraph::removeDeadCode),

		/**
		 * Transform into SSA edge-split form, to avoid inserting redundant
		 * phi-moves.
		 */
		BECOME_EDGE_SPLIT_SSA(L2ControlFlowGraph::transformToEdgeSplitSSA),

		/**
		 * Find multi-way instructions (at the ends of basic blocks) where
		 * along
		 * some outbound edges a value is live, but along other edges from the
		 * same block, the value is not live.  If it's safe, we move the
		 * defining instruction forward along the edges where it's live,
		 * thereby
		 * not having to execute it along paths where it's not actually live.
		 */
		COMPUTE_LIVENESS_AT_EDGES(
			L2ControlFlowGraph::computeLivenessAtEachEdge),

		/**
		 * For each register defined by an instruction with a side-effect, mark
		 * that register as live in each edge of the graph.
		 */
		CONSIDER_SIDE_EFFECT_REGISTERS_LIVE(
			L2ControlFlowGraph::considerSideEffectRegistersLive),

		/**
		 * Attempt to slide forward any definition instructions that we can.
		 * If
		 * they hit the end of a diverging block in which the defined registers
		 * are only needed by one successor, continue moving the instruction
		 * into that branch.
		 *
		 * TODO MvG - Eventually track will-use and might-use separately, and
		 * allow the instruction to be replicated into all might-use paths,
		 * introducing additional registers and phis as needed.
		 */
		POSTPONE_CONDITIONALLY_USED_VALUES(
			L2ControlFlowGraph::postponeConditionallyUsedValues),

		/**
		 * Insert phi moves, which makes it no longer in SSA form.
		 */
		INSERT_PHI_MOVES(
			L2ControlFlowGraph::insertPhiMoves),

		/**
		 * Remove constant moves made unnecessary by the introduction of new
		 * constant moves after phis (the ones that are constant-valued).
		 */
		REMOVE_DEAD_CODE_2(L2ControlFlowGraph::removeDeadCode),

		/**
		 * Compute the register-coloring interference graph while we're just
		 * out
		 * of SSA form – phis have been replaced by moves on incoming edges.
		 */
		COMPUTE_INTERFERENCE_GRAPH(
			L2ControlFlowGraph::computeInterferenceGraph),

		/**
		 * Color all registers, using the previously computed interference
		 * graph.  This creates a dense finalIndex numbering for the registers
		 * in such a way that no two registers that have to maintain distinct
		 * values at the same time will have the same number.
		 */
		COALESCE_REGISTERS_IN_NONINTERFERING_MOVES(
			L2ControlFlowGraph::coalesceNoninterferingMoves),

		/** Computed and assign final register colors. */
		ASSIGN_REGISTER_COLORS(L2ControlFlowGraph::computeColors),

		/**
		 * Create a replacement register for each used color (of each kind).
		 * Transform each reference to an old register into a reference to the
		 * replacement, updating structures as needed.
		 */
		REPLACE_REGISTERS_BY_COLOR(L2ControlFlowGraph::replaceRegistersByColor),

		/**
		 * Remove any remaining moves between two registers of the same color.
		 */
		REMOVE_SAME_COLOR_MOVES(L2ControlFlowGraph::removeSameColorMoves),

		/**
		 * Every L2PcOperand that leads to an L2_JUMP should now be redirected
		 * to the target of the jump (transitively, if the jump leads to
		 * another
		 * jump).  We specifically do this after inserting phi moves to ensure
		 * we don't jump past irremovable phi moves.
		 */
		ADJUST_EDGES_LEADING_TO_JUMPS(
			L2ControlFlowGraph::adjustEdgesLeadingToJumps),

		/**
		 * Having adjusted edges to avoid landing on L2_JUMPs, some blocks may
		 * have become unreachable.
		 */
		REMOVE_UNREACHABLE_BLOCKS(L2ControlFlowGraph::removeUnreachableBlocks),

		/**
		 * Choose an order for the blocks.  This isn't important while we're
		 * interpreting L2Chunks, but it will ultimately affect the quality of
		 * JVM translation.  Prefer to have the target block of an
		 * unconditional
		 * jump to follow the jump, since final code generation elides the jump.
		 */
		ORDER_BLOCKS(L2ControlFlowGraph::orderBlocks);

		// Additional optimization ideas:
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

		/** The optimization action to perform for this pass. */
		final Continuation1<L2ControlFlowGraph> action;

		/** The {@link Statistic} for tracking this pass's cost. */
		final Statistic stat;

		/**
		 * Create the enumeration value.
		 *
		 * @param action The action to perform for this pass.
		 */
		OPTIMIZATION_PHASE (final Continuation1<L2ControlFlowGraph> action)
		{
			this.action = action;
			this.stat = new Statistic(
				name(),
				StatisticReport.L2_OPTIMIZATION_TIME);
		}
	}

	/**
	 * Optimize the graph of instructions.
	 */
	public void optimize (final Interpreter interpreter)
	{
		for (final OPTIMIZATION_PHASE phase : OPTIMIZATION_PHASE.values())
		{
			final long before = System.nanoTime();
			phase.action.value(this);
			final long after = System.nanoTime();
			phase.stat.record(
				after - before, interpreter.interpreterIndex);
			sanityCheck(interpreter);
		}
	}

	/** Statistic for tracking the cost of sanity checks. */
	private static final Statistic sanityCheckStat = new Statistic(
		"(Sanity check)",
		StatisticReport.L2_OPTIMIZATION_TIME);

	/**
	 * Produce the final list of instructions.  Should only be called after
	 * all optimizations have been performed.
	 *
	 * @param instructions
	 *        The list of instructions to populate.
	 */
	public void generateOn (final List<L2Instruction> instructions)
	{
		for (final L2BasicBlock block : basicBlockOrder)
		{
			block.generateOn(instructions);
		}
	}
}
