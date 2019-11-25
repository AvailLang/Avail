/*
 * L2Optimizer.java
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

package com.avail.optimizer;

import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteOperand;
import com.avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.MutableInt;
import com.avail.utility.Pair;
import com.avail.utility.evaluation.Continuation1;

import javax.annotation.Nullable;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import static com.avail.AvailRuntimeSupport.captureNanos;
import static com.avail.utility.Casts.cast;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.Strings.increaseIndentation;
import static java.util.Collections.disjoint;
import static java.util.Collections.emptySet;

/**
 * An {@code L2Optimizer} optimizes its {@link L2ControlFlowGraph}.
 * This is a control graph.  The vertices are {@link L2BasicBlock}s, which are
 * connected via their successor and predecessor lists.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2Optimizer
{
	/** The {@link L2ControlFlowGraph} to optimize. */
	private final L2ControlFlowGraph controlFlowGraph;

	/** The mutable list of blocks taken from the {@link #controlFlowGraph}. */
	public final List<L2BasicBlock> blocks;

	/** Whether to sanity-check the graph between optimization steps. */
	public static boolean shouldSanityCheck = false;

	/** The register coloring algorithm. */
	private @Nullable L2RegisterColorer colorer = null;

	/** Statistic for tracking the cost of sanity checks. */
	private static final Statistic sanityCheckStat = new Statistic(
		"(Sanity check)",
		StatisticReport.L2_OPTIMIZATION_TIME);

	/**
	 * Create an optimizer for the given {@link L2ControlFlowGraph} and its
	 * mutable {@link List} of {@link L2BasicBlock}s.
	 *
	 * @param controlFlowGraph
	 *        The {@link L2ControlFlowGraph} to optimize.
	 * @param blocks
	 *        The mutable {@link List} of {@link L2BasicBlock}s from the control
	 *        flow graph.
	 */
	L2Optimizer (
		final L2ControlFlowGraph controlFlowGraph,
		final List<L2BasicBlock> blocks)
	{
		this.controlFlowGraph = controlFlowGraph;
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		this.blocks = blocks;
	}

	/**
	 * Find the {@link L2BasicBlock} that are actually reachable recursively
	 * from the blocks marked as {@link L2BasicBlock#isIrremovable()}.
	 *
	 * @return {@code true} if any blocks were removed, otherwise {@code false}.
	 */
	boolean removeUnreachableBlocks ()
	{
		final Deque<L2BasicBlock> blocksToVisit = blocks.stream()
			.filter(L2BasicBlock::isIrremovable)
			.collect(Collectors.toCollection(ArrayDeque::new));
		final Set<L2BasicBlock> reachableBlocks = new HashSet<>();
		while (!blocksToVisit.isEmpty())
		{
			final L2BasicBlock block = blocksToVisit.removeLast();
			if (!reachableBlocks.contains(block))
			{
				reachableBlocks.add(block);
				final Iterator<L2PcOperand> iterator =
					block.successorEdgesIterator();
				while (iterator.hasNext())
				{
					blocksToVisit.add(iterator.next().targetBlock());
				}
			}
		}
		final Set<L2BasicBlock> unreachableBlocks = new HashSet<>(blocks);
		unreachableBlocks.removeAll(reachableBlocks);
		for (final L2BasicBlock block : unreachableBlocks)
		{
			block.instructions().forEach(L2Instruction::justRemoved);
			block.instructions().clear();
		}
		final boolean changed = blocks.retainAll(reachableBlocks);
		// See if any blocks no longer need to be a loop head.
		for (final L2BasicBlock block : blocks)
		{
			if (block.isLoopHead
				&& block.predecessorEdgesCopy().stream()
					.noneMatch(L2PcOperand::isBackward))
			{
				// It's a loop head that has no back-edges pointing to it.
				block.isLoopHead = false;
			}
		}
		return changed;
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
		for (final L2BasicBlock block : blocks)
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
		for (final L2BasicBlock block : blocks)
		{
			final ListIterator<L2Instruction> iterator =
				block.instructions().listIterator();
			while (iterator.hasNext())
			{
				final L2Instruction instruction = iterator.next();
				if (!neededInstructions.contains(instruction))
				{
					anyRemoved = true;
					final @Nullable L2Instruction replacement =
						instruction.optionalReplacementForDeadInstruction();

					if (replacement == null)
					{
						iterator.remove();
						instruction.justRemoved();
					}
					else
					{
						iterator.set(replacement);
						instruction.justRemoved();
						if (replacement.operation() == L2_JUMP.instance)
						{
							final L2PcOperand target =
								L2_JUMP.jumpTarget(replacement);
							replacement.justInserted(target.manifest());
						}
					}
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
	void removeDeadCode ()
	{
		//noinspection StatementWithEmptyBody
		while (removeUnreachableBlocks() || removeDeadInstructions()) { }
	}

	/**
	 * For every edge leading from a multiple-out block to a multiple-in block,
	 * split it by inserting a new block along it.  Note that we do this
	 * regardless of whether the target block has any phi functions.
	 */
	void transformToEdgeSplitSSA ()
	{
		// Copy the list of blocks, to safely visit existing blocks while new
		// ones are added inside the loop.
		for (final L2BasicBlock sourceBlock : new ArrayList<>(blocks))
		{
			if (sourceBlock.successorEdgesCount() > 1)
			{
				for (final L2PcOperand edge : sourceBlock.successorEdgesCopy())
				{
					final L2BasicBlock targetBlock = edge.targetBlock();
					if (targetBlock.predecessorEdgesCount() > 1)
					{
						final L2BasicBlock newBlock =
							edge.splitEdgeWith(controlFlowGraph);
						// Add it somewhere that looks sensible for debugging,
						// although we'll order the blocks later.
						blocks.add(blocks.indexOf(targetBlock), newBlock);
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
	void computeLivenessAtEachEdge ()
	{
		for (final L2BasicBlock block : blocks)
		{
			final Iterator<L2PcOperand> iterator =
				block.predecessorEdgesIterator();
			while (iterator.hasNext())
			{
				final L2PcOperand predecessor = iterator.next();
				predecessor.alwaysLiveInRegisters.clear();
				predecessor.sometimesLiveInRegisters.clear();
			}
		}

		// The deque and the set maintain the same membership.
		final Deque<L2BasicBlock> workQueue = new ArrayDeque<>(blocks);
		final Set<L2BasicBlock> workSet = new HashSet<>(blocks);
		while (!workQueue.isEmpty())
		{
			final L2BasicBlock block = workQueue.removeLast();
			workSet.remove(block);
			// Take the union of the outbound edges' sometimes-live registers.
			// Also find the intersection of those edges' always-live registers.
			final Set<L2Register> alwaysLive = new HashSet<>();
			if (block.successorEdgesCount() > 0)
			{
				// Before processing instructions in reverse order, the
				// always-live-in set will be the intersection of the successor
				// edges' always-live-in sets.  Pick any edge's always-live-in
				// set as the starting case, to be intersected with each edge's
				// set in the loop below.
				alwaysLive.addAll(
					block.successorEdgeAt(0).alwaysLiveInRegisters);
			}
			final Set<L2Register> sometimesLive = new HashSet<>();
			final Iterator<L2PcOperand> successorsIterator =
				block.successorEdgesIterator();
			while (successorsIterator.hasNext())
			{
				final L2PcOperand edge = successorsIterator.next();
				sometimesLive.addAll(edge.sometimesLiveInRegisters);
				alwaysLive.retainAll(edge.alwaysLiveInRegisters);
			}
			// Now work backward through each instruction, removing registers
			// that it writes, and adding registers that it reads.
			final List<L2Instruction> instructions = block.instructions();
			int lastPhiIndex = -1;
			for (int i = instructions.size() - 1; i >= 0; i--)
			{
				final L2Instruction instruction = instructions.get(i);
				if (instruction.operation().isPhi())
				{
					// We've reached the phis at the start of the block.
					lastPhiIndex = i;
					break;
				}
				sometimesLive.removeAll(instruction.destinationRegisters());
				sometimesLive.addAll(instruction.sourceRegisters());
				alwaysLive.removeAll(instruction.destinationRegisters());
				alwaysLive.addAll(instruction.sourceRegisters());
			}

			// Add in the predecessor-specific live-in information for each edge
			// based on the corresponding positions inside phi instructions.
			final Iterator<L2PcOperand> iterator =
				block.predecessorEdgesIterator();
			int edgeIndex = 0;
			while (iterator.hasNext())
			{
				final L2PcOperand edge = iterator.next();
				final Set<L2Register> edgeAlwaysLiveIn =
					new HashSet<>(alwaysLive);
				final Set<L2Register> edgeSometimesLiveIn =
					new HashSet<>(sometimesLive);
				// Add just the registers used along this edge.
				for (int i = lastPhiIndex; i >= 0; i--)
				{
					final L2Instruction phiInstruction = instructions.get(i);
					final L2_PHI_PSEUDO_OPERATION<?, ?> phiOperation =
						cast(phiInstruction.operation());
					assert phiInstruction.operation().isPhi();
					edgeSometimesLiveIn.removeAll(
						phiInstruction.destinationRegisters());
					edgeAlwaysLiveIn.removeAll(
						phiInstruction.destinationRegisters());
					final List<? extends L2ReadOperand<?>> sources =
						phiOperation.sourceRegisterReads(phiInstruction);
					final L2Register source = sources.get(edgeIndex).register();
					edgeSometimesLiveIn.add(source);
					edgeAlwaysLiveIn.add(source);
				}
				final L2PcOperand predecessorEdge =
					block.predecessorEdgeAt(edgeIndex);
				boolean changed =
					predecessorEdge.sometimesLiveInRegisters.addAll(
						edgeSometimesLiveIn);
				changed |= predecessorEdge.alwaysLiveInRegisters.addAll(
					edgeAlwaysLiveIn);
				if (changed)
				{
					// We added to the known live registers of the edge.
					// Continue propagating to the predecessor.
					final L2BasicBlock predecessor = edge.sourceBlock();
					if (!workSet.contains(predecessor))
					{
						workQueue.addFirst(predecessor);
						workSet.add(predecessor);
					}
				}
				edgeIndex++;
			}
		}
	}

	/**
	 * Try to move any side-effect-less defining instructions to later points in
	 * the control flow graph.  If such an instruction defines a register that's
	 * used in the same basic block, don't bother moving it.  Also don't attempt
	 * to move it if it's always-live-in at each successor block, since the
	 * point of moving it forward is to avoid inessential computations.
	 *
	 * <p>So in the remaining case that the register is used in only some of the
	 * future paths, we attempt to move copies of the instruction into each
	 * successor that may require it.  Note that this can be <em>all</em> of the
	 * successors, if some of them are only maybe-live-in.</p>
	 *
	 * <p>This process is repeated until no more instructions are eligible to
	 * move forward.</p>
	 *
	 * <p>This requires edge-split SSA form as input, but the duplicated
	 * defining instructions break SSA.</p>
	 */
	void postponeConditionallyUsedValues ()
	{
		boolean changed;
		do
		{
			changed = false;
			for (final L2BasicBlock block : blocks)
			{
				// Copy the instructions list, since instructions may be removed
				// from it as we iterate.
				final Set<L2Register> registersConsumedLaterInBlock =
					new HashSet<>();
				final List<L2Instruction> instructions =
					new ArrayList<>(block.instructions());
				for (int i = instructions.size() - 1; i >= 0; i--)
				{
					final L2Instruction instruction = instructions.get(i);
					final @Nullable List<L2PcOperand> edgesToMoveThrough =
						successorEdgesToMoveThrough(
							instruction, registersConsumedLaterInBlock);
					if (edgesToMoveThrough != null)
					{
						assert !edgesToMoveThrough.isEmpty();
						changed = true;
						for (final L2PcOperand edge : edgesToMoveThrough)
						{
							final L2BasicBlock destinationBlock =
								edge.targetBlock();
							final L2Instruction newInstruction =
								new L2Instruction(
									destinationBlock,
									instruction.operation(),
									instruction.operands());
							destinationBlock.insertInstruction(
								0, newInstruction, edge.manifest());
							// None of the registers defined by the instruction
							// should be live-in any more at the edge.
							edge.sometimesLiveInRegisters.removeAll(
								newInstruction.destinationRegisters());
							edge.alwaysLiveInRegisters.removeAll(
								newInstruction.destinationRegisters());
							edge.sometimesLiveInRegisters.addAll(
								newInstruction.sourceRegisters());
							edge.alwaysLiveInRegisters.addAll(
								newInstruction.sourceRegisters());
						}
						block.instructions().remove(instruction);
						instruction.justRemoved();
					}
					else
					{
						// The instruction stayed where it was.  Record the
						// registers that it consumed, to pin any prior
						// instructions that provide those values, since they
						// can't be moved out of the block.
						registersConsumedLaterInBlock.addAll(
							instruction.sourceRegisters());
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
	 * @param instruction
	 *        The instruction to analyze.
	 * @param registersConsumedLaterInSameBlock
	 *        The set of registers which are consumed by instructions after the
	 *        given one within the same block.  If the given instruction
	 *        produces an output consumed by a later instruction, the given
	 *        instruction cannot be moved forward out of its basic block.
	 * @return The successor {@link L2PcOperand}s through which the instruction
	 *         can be moved, or {@code null} if the instruction should not move.
	 */
	private static @Nullable List<L2PcOperand> successorEdgesToMoveThrough (
		final L2Instruction instruction,
		final Set<L2Register> registersConsumedLaterInSameBlock)
	{
		if (instruction.hasSideEffect()
			|| instruction.altersControlFlow()
			|| instruction.operation().isPhi()
			|| instruction.operation().isEntryPoint(instruction))
		{
			return null;
		}
		final List<L2Register> written = instruction.destinationRegisters();
		assert !written.isEmpty()
			: "Every instruction should either have side effects or write to "
			+ "at least one register";
		if (!disjoint(written, registersConsumedLaterInSameBlock))
		{
			// A later instruction in the current basic block consumes one of
			// the values produced by the given instruction, so we can't move
			// the given instruction into later blocks.
			return null;
		}
		final L2BasicBlock block = instruction.basicBlock;
		if (block.successorEdgesCount() == 1)
		{
			// There's only one successor edge.  Since the CFG is in edge-split
			// form, the successor might have multiple predecessors.  Don't move
			// across the edge in that case, since it may cause the instruction
			// to run in situations that it doesn't need to.  When code
			// splitting is eventually implemented, it should clean up this case
			// by duplicating the successor block just for this edge.
			final L2BasicBlock successor =
				block.successorEdgeAt(0).targetBlock();
			if (successor.predecessorEdgesCount() > 1)
			{
				return null;
			}
		}
		final List<L2PcOperand> destinations = new ArrayList<>();
		boolean shouldMoveInstruction = false;
		final Iterator<L2PcOperand> iterator = block.successorEdgesIterator();
		while (iterator.hasNext())
		{
			final L2PcOperand edge = iterator.next();
			final L2BasicBlock targetBlock = edge.targetBlock();
			assert targetBlock.predecessorEdgesCount() == 1
				: "CFG is not in edge-split form";
			if (!edge.alwaysLiveInRegisters.containsAll(written))
			{
				shouldMoveInstruction = true;
			}
			if (!disjoint(edge.sometimesLiveInRegisters, written))
			{
				destinations.add(edge);
			}
		}
		if (!shouldMoveInstruction)
		{
			// It was always-live-in for every successor.
			return null;
		}
		assert !destinations.isEmpty();
		return destinations;
	}

	/**
	 * For every phi operation, insert a move at the end of the block that leads
	 * to it.  Because of our version of edge splitting, that block always
	 * contains just a jump.  The CFG will no longer be in SSA form, because the
	 * phi variables will have multiple defining instructions (the moves).
	 *
	 * <p>Also eliminate the phi functions.</p>
	 */
	void insertPhiMoves ()
	{
		for (final L2BasicBlock block : blocks)
		{
			final Iterator<L2Instruction> instructionIterator =
				block.instructions().iterator();
			while (instructionIterator.hasNext())
			{
				final L2Instruction instruction = instructionIterator.next();
				if (!(instruction.operation().isPhi()))
				{
					// Phi functions are always at the start, so we must be past
					// them, if any.
					break;
				}
				final L2_PHI_PSEUDO_OPERATION<?, ?> phiOperation =
					cast(instruction.operation());
				final L2WriteOperand<?> targetWriter =
					phiOperation.destinationRegisterWrite(instruction);
				final List<? extends L2ReadOperand<?>> phiSources =
					phiOperation.sourceRegisterReads(instruction);
				final int fanIn = block.predecessorEdgesCount();
				assert fanIn == phiSources.size();

				// Insert a non-SSA move in each predecessor block.
				for (int i = 0; i < fanIn; i++)
				{
					final L2PcOperand edge = block.predecessorEdgeAt(i);
					final L2BasicBlock predecessor = edge.sourceBlock();
					final List<L2Instruction> instructions =
						predecessor.instructions();
					assert predecessor.finalInstruction().operation()
						== L2_JUMP.instance;
					final L2ReadOperand<?> sourceRead = phiSources.get(i);
					final L2Instruction move =
						new L2Instruction(
							predecessor,
							sourceRead.phiMoveOperation(),
							sourceRead,
							targetWriter.clone());
					predecessor.insertInstruction(
						instructions.size() - 1, move, edge.manifest());
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
	void computeInterferenceGraph ()
	{
		colorer = new L2RegisterColorer(controlFlowGraph);
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
	void coalesceNoninterferingMoves ()
	{
		stripNull(colorer).coalesceNoninterferingMoves();
	}

	/**
	 * Assign final coloring to each register based on the interference graph
	 * and coalescing map.
	 */
	void computeColors ()
	{
		stripNull(colorer).computeColors();
		colorer = null;
	}

	/**
	 * Create a new register for every &lt;kind, finalIndex&gt; (i.e., color) of
	 * an existing register, then transform every instruction of this control
	 * flow graph to use the new registers.  The new registers have a
	 * {@link L2Register#uniqueValue} that's the same as its {@link
	 * L2Register#finalIndex() finalIndex}.
	 */
	void replaceRegistersByColor ()
	{
		// Create new registers for each <kind, finalIndex> in the existing
		// registers.
		final EnumMap<RegisterKind, Map<Integer, L2Register>> byKindAndIndex
			= new EnumMap<>(RegisterKind.class);
		final Map<L2Register, L2Register> remap = new HashMap<>();
		// Also collect all the old registers.
		final HashSet<L2Register> oldRegisters = new HashSet<>();
		blocks.forEach(
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
		blocks.forEach(
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
	void removeSameColorMoves ()
	{
		for (final L2BasicBlock block : blocks)
		{
			final Iterator<L2Instruction> iterator =
				block.instructions().iterator();
			while (iterator.hasNext())
			{
				final L2Instruction instruction = iterator.next();
				if (instruction.operation().isMove()
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
	 *
	 * <p>Don't adjust jumps that land on a jump inside a loop head block.</p>
	 */
	void adjustEdgesLeadingToJumps ()
	{
		boolean changed;
		do
		{
			changed = false;
			final Iterator<L2BasicBlock> blockIterator = blocks.iterator();
			while (blockIterator.hasNext())
			{
				final L2BasicBlock block = blockIterator.next();
				if (block.isLoopHead || block.instructions().size() != 1)
				{
					continue;
				}
				final L2Instruction soleInstruction = block.finalInstruction();
				if (soleInstruction.operation() != L2_JUMP.instance)
				{
					continue;
				}
				// Redirect all predecessors through the jump.
				final L2PcOperand jumpEdge =
					L2_JUMP.jumpTarget(block.finalInstruction());
				final L2BasicBlock jumpTarget = jumpEdge.targetBlock();
				final boolean isBackward = jumpEdge.isBackward();
				for (final L2PcOperand inEdge : block.predecessorEdgesCopy())
				{
					changed = true;
					inEdge.switchTargetBlockNonSSA(jumpTarget, isBackward);
				}
				// Eliminate the block, unless it has to be there for
				// external reasons (i.e., it's an L2 entry point).
				assert block.predecessorEdgesCount() == 0;
				if (!block.isIrremovable())
				{
					jumpTarget.removePredecessorEdge(jumpEdge);
					blockIterator.remove();
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
	 * predecessors have been placed (or if there are only cycles unplaced, pick
	 * one arbitrarily).</p>
	 */
	void orderBlocks ()
	{
		final Map<L2BasicBlock, MutableInt> countdowns = new HashMap<>();
		for (final L2BasicBlock block : blocks)
		{
			countdowns.put(
				block, new MutableInt(block.predecessorEdgesCount()));
		}
		final List<L2BasicBlock> order =
			new ArrayList<>(blocks.size());
		assert blocks.get(0).predecessorEdgesCount() == 0;
		final Deque<L2BasicBlock> zeroed = new ArrayDeque<>();
		for (int i = blocks.size() - 1; i >= 0; i--)
		{
			if (blocks.get(i).predecessorEdgesCount() == 0)
			{
				zeroed.add(blocks.get(i));
			}
		}
		assert zeroed.getLast() == blocks.get(0);
		while (!countdowns.isEmpty())
		{
			if (!zeroed.isEmpty())
			{
				final L2BasicBlock block = zeroed.removeLast();
				order.add(block);
				final Iterator<L2PcOperand> iterator =
					block.successorEdgesIterator();
				while (iterator.hasNext())
				{
					final L2PcOperand edge = iterator.next();
					final @Nullable MutableInt countdown =
						countdowns.get(edge.targetBlock());
					// Note that the entry may have been removed to break a
					// cycle.  See below.
					if (countdown != null && --countdown.value == 0)
					{
						countdowns.remove(edge.targetBlock());
						zeroed.add(edge.targetBlock());
					}
				}
			}
			else
			{
				// Only cycles and blocks reachable from cycles are left.  Pick
				// a node at random, preferring one that has had at least one
				// predecessor placed.
				@Nullable L2BasicBlock victim = null;
				for (final Entry<L2BasicBlock, MutableInt> entry
					: countdowns.entrySet())
				{
					if (entry.getValue().value
						< entry.getKey().predecessorEdgesCount())
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

		assert order.size() == blocks.size();
		assert order.get(0) == blocks.get(0);
		blocks.clear();
		blocks.addAll(order);
	}

	/**
	 * A helper class used for sanity checking the liveness of registers.
	 */
	private static class UsedRegisters
	{
		/**
		 * Which registers are live here, organized by {@link RegisterKind}'s
		 * ordinal.
		 */
		final BitSet[] liveRegistersByKind;

		/**
		 * Reduce the collection of registers live here by intersecting it with
		 * the argument.  Answer whether it changed.
		 *
		 * @param another The other {@code UsedRegisters}.
		 * @return Whether the intersection made a change.
		 */
		boolean restrictTo (final UsedRegisters another)
		{
			boolean changed = false;
			for (int i = 0; i < liveRegistersByKind.length; i++)
			{
				final BitSet registers = liveRegistersByKind[i];
				final int count = registers.cardinality();
				registers.and(another.liveRegistersByKind[i]);
				changed |= registers.cardinality() != count;
			}
			return changed;
		}

		void readRegister (
			final L2Register register,
			final ToIntFunction<L2Register> registerIdFunction)
		{
			assert liveRegistersByKind[register.registerKind().ordinal()]
				.get(registerIdFunction.applyAsInt(register));
		}

		void writeRegister (
			final L2Register register,
			final ToIntFunction<L2Register> registerIdFunction)
		{
			liveRegistersByKind[register.registerKind().ordinal()]
				.set(registerIdFunction.applyAsInt(register));
		}

		void clearAll ()
		{
			//noinspection ForLoopReplaceableByForEach
			for (int i = 0; i < liveRegistersByKind.length; i++)
			{
				liveRegistersByKind[i].clear();
			}
		}

		UsedRegisters ()
		{
			liveRegistersByKind = new BitSet[RegisterKind.all.length];
			for (int i = 0; i < liveRegistersByKind.length; i++)
			{
				liveRegistersByKind[i] = new BitSet();
			}
		}

		UsedRegisters (final UsedRegisters original)
		{
			liveRegistersByKind = new BitSet[RegisterKind.all.length];
			for (int i = 0; i < liveRegistersByKind.length; i++)
			{
				liveRegistersByKind[i] =
					(BitSet) original.liveRegistersByKind[i].clone();
			}
		}
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		for (final L2BasicBlock block : blocks)
		{
			builder.append(block.name());
			builder.append(":\n");
			for (final L2Instruction instruction : block.instructions())
			{
				builder.append('\t');
				builder.append(increaseIndentation(instruction.toString(), 1));
				builder.append('\n');
			}
			builder.append('\n');
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
		blocks.forEach(
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
		blocks.forEach(
			block -> block.instructions().forEach(
				instruction ->
				{
					instruction.operandsDo(operand ->
					{
						final boolean added = allOperands.add(operand);
						assert added;
						if (operand instanceof L2ReadVectorOperand)
						{
							final L2ReadVectorOperand<?, ?> vector =
								cast(operand);
							vector.elements().forEach(
								read ->
								{
									final boolean ok = allOperands.add(read);
									assert ok;
								});
						}
					});
				}
			));
	}

	/**
	 * Check that all edges are correctly connected, and that phi functions have
	 * the right number of inputs.
	 */
	private void checkEdgesAndPhis ()
	{
		for (final L2BasicBlock block : blocks)
		{
			for (final L2Instruction instruction : block.instructions())
			{
				assert !instruction.operation().isPhi()
					|| instruction.sourceRegisters().size()
					== block.predecessorEdgesCount();
			}
			// Check edges going forward.
			final L2Instruction lastInstruction = block.finalInstruction();
			assert lastInstruction.targetEdges().equals(
				block.successorEdgesCopy());
			for (final L2PcOperand edge : block.successorEdgesCopy())
			{
				assert edge.sourceBlock() == block;
				final L2BasicBlock targetBlock = edge.targetBlock();
				assert !edge.isBackward() || targetBlock.isLoopHead;
				assert blocks.contains(targetBlock);
				assert targetBlock.predecessorEdgesCopy().contains(edge);
			}
			// Also check edges going backward.
			for (final L2PcOperand backEdge : block.predecessorEdgesCopy())
			{
				assert backEdge.targetBlock() == block;
				final L2BasicBlock predecessorBlock = backEdge.sourceBlock();
				assert blocks.contains(predecessorBlock);
				assert predecessorBlock.successorEdgesCopy().contains(backEdge);
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
		final ToIntFunction<L2Register> registerIdFunction)
	{
		final Deque<Pair<L2BasicBlock, UsedRegisters>> blocksToCheck =
			new ArrayDeque<>();
		blocksToCheck.add(new Pair<>(blocks.get(0), new UsedRegisters()));
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
				checked = new UsedRegisters(newUsed);
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
			final UsedRegisters workingSet = new UsedRegisters(checked);
			for (final L2Instruction instruction : block.instructions())
			{
				if (instruction.operation() == L2_ENTER_L2_CHUNK.instance)
				{
					// Wipe all registers.
					workingSet.clearAll();
				}
				if (!instruction.operation().isPhi())
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
			final Iterator<L2PcOperand> iterator =
				block.successorEdgesIterator();
			while (iterator.hasNext())
			{
				// Handle the phi instructions of the target here.  Create a
				// workingCopy for each edge.
				final L2PcOperand edge = iterator.next();
				final UsedRegisters workingCopy = new UsedRegisters(workingSet);
				final L2BasicBlock targetBlock = edge.targetBlock();
				final int predecessorIndex =
					targetBlock.predecessorEdgesCopy().indexOf(edge);
				if (predecessorIndex == -1)
				{
					System.out.println("Phi predecessor not found");
					assert false : "Phi predecessor not found";
				}
				for (final L2Instruction phiInTarget
					: targetBlock.instructions())
				{
					if (!phiInTarget.operation().isPhi())
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
				blocksToCheck.add(new Pair<>(targetBlock, workingCopy));
			}
		}
	}

	/**
	 * Ensure each instruction that's an {@linkplain L2Operation#isEntryPoint(
	 * L2Instruction) entry point} occurs at the start of a block.
	 */
	private void checkEntryPoints ()
	{
		blocks.forEach(
			b -> b.instructions().forEach(
				i ->
				{
					assert !i.operation().isEntryPoint(i)
						|| b.instructions().get(0) == i;
				}
		));
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
			final long before = captureNanos();
			checkBlocksAndInstructions();
			checkUniqueOperands();
			checkEdgesAndPhis();
			checkRegistersAreInitialized(L2Register::uniqueValue);
			checkEntryPoints();
			final long after = captureNanos();
			sanityCheckStat.record(
				after - before, interpreter.interpreterIndex);
		}
	}

	/**
	 * The collection of phases of optimization, in sequence.
	 */
	enum OptimizationPhase
	{
		/**
		 * Start by eliminating debris created during the initial L1 → L2
		 * translation.
		 */
		REMOVE_DEAD_CODE_1(L2Optimizer::removeDeadCode),

		/**
		 * Transform into SSA edge-split form, to avoid inserting redundant
		 * phi-moves.
		 */
		BECOME_EDGE_SPLIT_SSA(L2Optimizer::transformToEdgeSplitSSA),

		/**
		 * Determine which registers are sometimes-live-in and/or always-live-in
		 * at each edge, in preparation for postponing instructions that don't
		 * have their outputs consumed in the same block, and aren't
		 * always-live-in in every successor.
		 */
		COMPUTE_LIVENESS_AT_EDGES(L2Optimizer::computeLivenessAtEachEdge),

		/**
		 * Try to move any side-effect-less instructions to later points in the
		 * control flow graph.  If such an instruction defines a register that's
		 * used in the same basic block, don't bother moving it.  Also don't
		 * attempt to move it if it's always-live-in at each successor block,
		 * since the point of moving it forward is to avoid inessential
		 * computations.
		 *
		 * <p>Note that this breaks SSA by duplicating defining instructions.
		 * </p>
		 */
		POSTPONE_CONDITIONALLY_USED_VALUES(
			L2Optimizer::postponeConditionallyUsedValues),

		/**
		 * Insert phi moves along preceding edges.  This requires the CFG to be
		 * in edge-split form, although strict SSA isn't required.
		 */
		INSERT_PHI_MOVES(L2Optimizer::insertPhiMoves),

		/**
		 * Remove constant moves made unnecessary by the introduction of new
		 * constant moves after phis (the ones that are constant-valued).
		 */
		REMOVE_DEAD_CODE_2(L2Optimizer::removeDeadCode),

		/**
		 * Compute the register-coloring interference graph while we're just
		 * out of SSA form – phis have been replaced by moves on incoming edges.
		 */
		COMPUTE_INTERFERENCE_GRAPH(L2Optimizer::computeInterferenceGraph),

		/**
		 * Color all registers, using the previously computed interference
		 * graph.  This creates a dense finalIndex numbering for the registers
		 * in such a way that no two registers that have to maintain distinct
		 * values at the same time will have the same number.
		 */
		COALESCE_REGISTERS_IN_NONINTERFERING_MOVES(
			L2Optimizer::coalesceNoninterferingMoves),

		/** Computed and assign final register colors. */
		ASSIGN_REGISTER_COLORS(L2Optimizer::computeColors),

		/**
		 * Create a replacement register for each used color (of each kind).
		 * Transform each reference to an old register into a reference to the
		 * replacement, updating structures as needed.
		 */
		REPLACE_REGISTERS_BY_COLOR(L2Optimizer::replaceRegistersByColor),

		/**
		 * Remove any remaining moves between two registers of the same color.
		 */
		REMOVE_SAME_COLOR_MOVES(L2Optimizer::removeSameColorMoves),

		/**
		 * Every L2PcOperand that leads to an L2_JUMP should now be redirected
		 * to the target of the jump (transitively, if the jump leads to another
		 * jump).  We specifically do this after inserting phi moves to ensure
		 * we don't jump past irremovable phi moves.
		 */
		ADJUST_EDGES_LEADING_TO_JUMPS(L2Optimizer::adjustEdgesLeadingToJumps),

		/**
		 * Having adjusted edges to avoid landing on L2_JUMPs, some blocks may
		 * have become unreachable.
		 */
		REMOVE_UNREACHABLE_BLOCKS(L2Optimizer::removeUnreachableBlocks),

		/**
		 * Choose an order for the blocks.  This isn't important while we're
		 * interpreting L2Chunks, but it will ultimately affect the quality of
		 * JVM translation.  Prefer to have the target block of an unconditional
		 * jump to follow the jump, since final code generation elides the jump.
		 */
		ORDER_BLOCKS(L2Optimizer::orderBlocks);

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
		final Continuation1<L2Optimizer> action;

		/** The {@link Statistic} for tracking this pass's cost. */
		final Statistic stat;

		/**
		 * Create the enumeration value.
		 *
		 * @param action The action to perform for this pass.
		 */
		OptimizationPhase (final Continuation1<L2Optimizer> action)
		{
			this.action = action;
			this.stat = new Statistic(
				name(),
				StatisticReport.L2_OPTIMIZATION_TIME);
		}
	}

	/**
	 * Optimize the graph of instructions.
	 *
	 * @param interpreter The current {@link Interpreter}.
	 */
	public void optimize (final Interpreter interpreter)
	{
		for (final OptimizationPhase phase : OptimizationPhase.values())
		{
			final long before = captureNanos();
			phase.action.value(this);
			final long after = captureNanos();
			phase.stat.record(after - before, interpreter.interpreterIndex);
			sanityCheck(interpreter);
		}
	}
}
