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

import com.avail.interpreter.execution.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_BACK;
import com.avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE;
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.operation.L2_VIRTUAL_CREATE_LABEL;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L2ControlFlowGraph.StateFlag;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.MutableInt;
import com.avail.utility.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import javax.annotation.Nullable;
import java.util.*;
import java.util.Map.Entry;

import static com.avail.AvailRuntimeSupport.captureNanos;
import static com.avail.utility.Casts.cast;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.Strings.increaseIndentation;
import static java.util.Collections.disjoint;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toCollection;

/**
 * An {@code L2Optimizer} optimizes its {@link L2ControlFlowGraph}. This is a control graph.  The vertices are {@link L2BasicBlock}s, which are connected via their successor and predecessor lists.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2Optimizer
{
	/**
	 * An {@link L2Generator} used for splicing short sequences of code as part
	 * of optimization.
	 */
	public final L2Generator generator;

	/** The {@link L2ControlFlowGraph} to optimize. */
	private final L2ControlFlowGraph controlFlowGraph;

	/** The mutable list of blocks taken from the {@link #controlFlowGraph}. */
	public final List<L2BasicBlock> blocks;

	/**
	 * Whether any {@link L2_VIRTUAL_CREATE_LABEL} instructions, or other
	 * placeholders, were replaced since the last time we cleared the flag.
	 */
	public boolean replacedAnyPlaceholders = false;

	/** Whether to sanity-check the graph between optimization steps. */
	public static boolean shouldSanityCheck = true; //TODO false;

	/** The register coloring algorithm. */
	private @Nullable L2RegisterColorer colorer = null;

	/** Statistic for tracking the cost of sanity checks. */
	private static final Statistic sanityCheckStat = new Statistic(
		"(Sanity check)",
		StatisticReport.L2_OPTIMIZATION_TIME);

	/**
	 * Create an optimizer for the given {@link L2ControlFlowGraph} and its mutable {@link List} of {@link L2BasicBlock}s.
	 *
	 * @param generator
	 *        An {@link L2Generator} used for splicing in short sequences of new code as part of optimization.
	 */
	L2Optimizer (final L2Generator generator)
	{
		this.generator = generator;
		this.controlFlowGraph = generator.controlFlowGraph;
		this.blocks = controlFlowGraph.basicBlockOrder;
	}

	/**
	 * Set each of the specified {@link StateFlag}s in the
	 * {@link #controlFlowGraph}.
	 *
	 * @param flags
	 *        The collection of {@link StateFlag}s to add.
	 */
	public void set (final Collection<Class<? extends StateFlag>> flags)
	{
		controlFlowGraph.set(flags);
	}

	/**
	 * Clear each of the specified {@link StateFlag}s from the
	 * {@link #controlFlowGraph}.
	 *
	 * @param flags
	 *        The collection of {@link StateFlag}s to remove.
	 */
	public void clear (final Collection<Class<? extends StateFlag>> flags)
	{
		controlFlowGraph.clear(flags);
	}

	/**
	 * Assert that each of the specified {@link StateFlag}s has been set in the
	 * {@link #controlFlowGraph}.
	 *
	 * @param flags
	 *        The collection of {@link StateFlag}s to check.
	 */
	public void check (final Collection<Class<? extends StateFlag>> flags)
	{
		controlFlowGraph.check(flags);
	}

	/**
	 * Assert that each of the specified {@link StateFlag}s has been cleared
	 * in the {@link #controlFlowGraph}.
	 *
	 * @param flags
	 *        The collection of {@link StateFlag}s to check for absence.
	 */
	public void checkNot (final Collection<Class<? extends StateFlag>> flags)
	{
		controlFlowGraph.checkNot(flags);
	}

	/**
	 * Find the {@link L2BasicBlock} that are actually reachable recursively from the blocks marked as {@link L2BasicBlock#isIrremovable()}.
	 *
	 * @return
	 * {@code true} if any blocks were removed, otherwise {@code false}.
	 */
	boolean removeUnreachableBlocks ()
	{
		final Deque<L2BasicBlock> blocksToVisit = blocks.stream()
			.filter(L2BasicBlock::isIrremovable)
			.collect(toCollection(ArrayDeque::new));
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
		if (changed)
		{
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
		}
		return changed;
	}

	/**
	 * Remove any unnecessary instructions.  Answer true if any were removed.
	 *
	 * @param dataCouplingMode
	 *        The {@link DataCouplingMode} that chooses how to trace liveness.
	 * @return
	 * Whether any dead instructions were removed or changed.
	 */
	boolean removeDeadInstructions (
		final DataCouplingMode dataCouplingMode)
	{
		final DeadCodeAnalyzer analyzer =
			new DeadCodeAnalyzer(dataCouplingMode, controlFlowGraph);
		analyzer.analyzeReads();
		final Set<L2Instruction> liveInstructions = analyzer.liveInstructions();
		boolean anyRemoved = false;
		for (final L2BasicBlock block : blocks)
		{
			final ListIterator<L2Instruction> iterator =
				block.instructions().listIterator();
			while (iterator.hasNext())
			{
				final L2Instruction instruction = iterator.next();
				if (!liveInstructions.contains(instruction))
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
						if (replacement.operation() == L2_JUMP.INSTANCE)
						{
							replacement.justInserted();
						}
					}
				}
			}
		}
		if (anyRemoved)
		{
			updateAllSemanticValuesOfReads();
			updateAllManifests();
		}
		return anyRemoved;
	}

	/**
	 * Visit all manifests, retaining only the {@link L2Register}s that are written by live code along <em>all</em> of that manifest's histories. Loop edges may have to be visited multiple times to ensure convergence to a maximal fixed point.
	 */
	private void updateAllManifests ()
	{
		// Visit all L2WriteOperands, recording which L2Registers are written.
		final Set<L2Register> registersToKeep = new HashSet<>(100);
		blocks.forEach(
			block -> block.instructions().forEach(
				instruction -> instruction.writeOperands().forEach(
					writeOperand ->
						registersToKeep.add(writeOperand.register()))));
		// Visit each edge's manifest.  In the manifest's definitions map's
		// values (which are lists of L2Registers), retain only those registers
		// for which writes were found above.
		blocks.forEach(
			block -> block.successorEdgesIterator().forEachRemaining(
				edge -> edge.manifest().retainRegisters(registersToKeep)));
	}

	/**
	 * Code has been removed, replaced, or moved.  As a consequence, some {@link L2ReadOperand}s may now be referring to {@link L2SemanticValue}s that are inaccessible at the point of read, via an {@link L2Register} that is still accessible.  Replace the {@link L2SemanticValue} of that read to one that's present in every {@link L2WriteOperand} that writes to the register.
	 *
	 * <p>It should be the case that there will always be at least one common
	 * semantic value among all writes to a register.</p>
	 */
	private void updateAllSemanticValuesOfReads ()
	{
		final Map<L2Register, L2SemanticValue> favoredSemanticValuesMap =
			new HashMap<>();
		for (final L2BasicBlock block : blocks)
		{
			for (final L2Instruction instruction : block.instructions())
			{
				if (instruction.operation().isPhi())
				{
					// Don't mess with the semantic values coming into a phi, as
					// they're always expected to mutually agree.
					continue;
				}
				for (final L2ReadOperand<?> read : instruction.readOperands())
				{
					@Nullable L2SemanticValue favoredSemanticValue =
						favoredSemanticValuesMap.get(read.register());
					if (favoredSemanticValue == null)
					{
						@Nullable Set<L2SemanticValue> intersection =
							null;
						for (final L2WriteOperand<?> write :
							read.register().definitions())
						{
							if (intersection == null)
							{
								intersection =
									new HashSet<>(write.semanticValues());
							}
							else
							{
								intersection.retainAll(write.semanticValues());
							}
						}
						assert intersection != null;
						assert !intersection.isEmpty();
						favoredSemanticValue = intersection.iterator().next();
						favoredSemanticValuesMap.put(
							read.register(), favoredSemanticValue);
					}
					read.updateSemanticValue(favoredSemanticValue);
				}
			}
		}
	}

	/**
	 * Remove all unreachable blocks and all instructions that don't either have
	 * a side-effect or produce a value ultimately used by an instruction that
	 * has a side-effect.
	 *
	 * @param dataCouplingMode
	 *        How to trace data dependencies.
	 */
	void removeDeadCode (
		final DataCouplingMode dataCouplingMode)
	{
		// Removing instructions won't cause blocks to be inaccessible, so just
		// clean up unreachable blocks once at the start.
		removeUnreachableBlocks();
		if (!removeDeadInstructions(dataCouplingMode))
		{
			// No instructions were removed, so don't bother cleaning up the
			// manifests.
			return;
		}
		// Clean up all manifests so that they only mention registers that
		// are guaranteed to have values at that point.
		final Map<L2PcOperand, Set<L2Register>> visibleRegisters =
			new HashMap<>();
		final Map<L2PcOperand, Set<L2SemanticValue>> visibleSemanticValues =
			new HashMap<>();
		blocks.forEach(
			b -> b.predecessorEdgesDo(e ->
			{
				visibleRegisters.put(e, new HashSet<>());
				visibleSemanticValues.put(e, new HashSet<>());
				return null;
			}));
		// These two collections should maintain the same membership.
		final Deque<L2BasicBlock> toVisitQueue = new ArrayDeque<>(blocks);
		final Set<L2BasicBlock> toVisitSet = new HashSet<>(blocks);
		while (!toVisitQueue.isEmpty())
		{
			final L2BasicBlock block = toVisitQueue.removeFirst();
			toVisitSet.remove(block);
			final Set<L2Register> regs;
			final Set<L2SemanticValue> values;
			final Iterator<L2PcOperand> predecessors =
				block.predecessorEdgesIterator();
			if (predecessors.hasNext())
			{
				final L2PcOperand first = predecessors.next();
				regs = new HashSet<>(visibleRegisters.get(first));
				values = new HashSet<>(visibleSemanticValues.get(first));
				predecessors.forEachRemaining(edge ->
				{
					regs.retainAll(visibleRegisters.get(edge));
					values.retainAll(visibleSemanticValues.get(edge));
				});
			}
			else
			{
				regs = new HashSet<>();
				values = new HashSet<>();
			}
			for (final L2Instruction instruction : block.instructions())
			{
				if (!instruction.altersControlFlow())
				{
					instruction.writeOperands().forEach(write ->
					{
						regs.add(write.register());
						values.addAll(write.semanticValues());
					});
				}
				else
				{
					instruction.edgesAndPurposesDo((edge, purpose) ->
					{
						final Set<L2Register> regsForEdge =
							new HashSet<>(regs);
						final Set<L2SemanticValue> valuesForEdge =
							new HashSet<>(values);
						instruction.writesForPurposeDo(purpose, write ->
							{
								regsForEdge.add(write.register());
								valuesForEdge.addAll(
									write.semanticValues());
								return null;
							});
						boolean changed = visibleRegisters.get(edge)
							.addAll(regsForEdge);
						changed |= visibleSemanticValues.get(edge)
							.addAll(valuesForEdge);
						if (changed && toVisitSet.add(edge.targetBlock()))
						{
							toVisitQueue.add(edge.targetBlock());
						}
						return null;
					});
				}
			}
		}
		// Now that we have the complete registers available at each edge,
		// narrow each edge's manifest accordingly.
		visibleRegisters.forEach((edge, regs) ->
			edge.manifest().retainRegisters(regs));
		visibleSemanticValues.forEach((edge, values) ->
			edge.manifest().retainSemanticValues(values));
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
				sourceBlock.successorEdgesDo(edge ->
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
					return null;
				});
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
			block.predecessorEdgesDo(predecessor ->
			{
				predecessor.alwaysLiveInRegisters.clear();
				predecessor.sometimesLiveInRegisters.clear();
				return null;
			});
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
			final int finalLastPhiIndex = lastPhiIndex;
			final MutableInt edgeIndex = new MutableInt(0);
			block.predecessorEdgesDo(edge ->
			{
				final Set<L2Register> edgeAlwaysLiveIn =
					new HashSet<>(alwaysLive);
				final Set<L2Register> edgeSometimesLiveIn =
					new HashSet<>(sometimesLive);
				// Add just the registers used along this edge.
				for (int i = finalLastPhiIndex; i >= 0; i--)
				{
					final L2Instruction phiInstruction = instructions.get(i);
					final L2_PHI_PSEUDO_OPERATION<?, ?, ?> phiOperation =
						cast(phiInstruction.operation());
					edgeSometimesLiveIn.removeAll(
						phiInstruction.destinationRegisters());
					edgeAlwaysLiveIn.removeAll(
						phiInstruction.destinationRegisters());
					final List<? extends L2ReadOperand<?>> sources =
						phiOperation.sourceRegisterReads(phiInstruction);
					final L2Register source =
						sources.get(edgeIndex.value).register();
					edgeSometimesLiveIn.add(source);
					edgeAlwaysLiveIn.add(source);
				}
				final L2PcOperand predecessorEdge =
					block.predecessorEdgeAt(edgeIndex.value);
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
				edgeIndex.value++;
				return null;
			});
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
	 * <p>This requires edge-split form.  It does not preserve SSA.</p>
	 */
	void postponeConditionallyUsedValues ()
	{
		boolean changed;
		do
		{
			changed = false;
			for (final L2BasicBlock block : blocks)
			{
				final Set<L2Register> registersConsumedLaterInBlock =
					new HashSet<>();
				final Set<L2SemanticValue> semanticValuesConsumedLaterInBlock =
					new HashSet<>();
				int dependentReadsLaterInBlock = 0;
				int allWritesLaterInBlock = 0;
				final List<L2Instruction> instructions =
					new ArrayList<>(block.instructions());
				// This set is populated right away by the last instruction of
				// the block, which is the only place control flow can be
				// altered.
				final Set<L2PcOperand> reachableTargetEdges = new HashSet<>();
				for (int i = instructions.size() - 1; i >= 0; i--)
				{
					final L2Instruction instruction = instructions.get(i);
					final L2Operation operation = instruction.operation();
					if (operation.goesMultipleWays()) {
						// Don't move anything past an instruction that goes
						// multiple ways from the same circumstance.  Otherwise,
						// any value produced in the first edge would be
						// redundant with (and maybe even disagree with, in the
						// case of identity) the values that would be produced
						// later in the second edge.
						break;
					}
					final @Nullable Set<L2PcOperand> edgesToMoveThrough =
						successorEdgesToMoveThrough(
							instruction,
							registersConsumedLaterInBlock,
							semanticValuesConsumedLaterInBlock,
							dependentReadsLaterInBlock,
							allWritesLaterInBlock,
							reachableTargetEdges);
					if (edgesToMoveThrough != null
						&& edgesToMoveThrough.stream()
							.noneMatch(L2PcOperand::isBackward))
					{
						assert !edgesToMoveThrough.isEmpty();
						changed = true;
						@Nullable L2ReadBoxedOperand mutableRead = null;
						@Nullable Pair<Set<L2SemanticValue>, TypeRestriction>
							pair = null;
						if (operation == L2_MAKE_IMMUTABLE.INSTANCE)
						{
							mutableRead = L2_MAKE_IMMUTABLE.sourceOfImmutable(
								instruction);
							pair = mutableRead.findSourceInformation();
						}
						block.instructions().remove(instruction);
						instruction.justRemoved();
						final List<L2WriteOperand<?>> writesList =
							instruction.writeOperands();
						final Set<L2Register> writtenSet = writesList.stream()
							.map(L2WriteOperand::register)
							.collect(toCollection(HashSet::new));
						for (final L2PcOperand edge : edgesToMoveThrough)
						{
							if (!writtenSet.isEmpty())
							{
								edge.manifest().forgetRegisters(writtenSet);
							}
							if (operation == L2_MAKE_IMMUTABLE.INSTANCE)
							{
								edge.manifest().recordSourceInformation(
									mutableRead.register(),
									pair.first(),
									pair.second());
							}
							final L2BasicBlock destinationBlock =
								edge.targetBlock();
							final L2Instruction newInstruction =
								new L2Instruction(
									destinationBlock,
									operation,
									instruction.operands());
							destinationBlock.insertInstruction(
								destinationBlock.indexAfterEntryPointAndPhis(),
								newInstruction);
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
						// Erase its writes in every path at and after the edges
						// that we *didn't* move it to.  We don't have to worry
						// about hitting a block that does care about the value,
						// because we already know it was never live-in anywhere
						// after this edge.
						reachableTargetEdges.forEach(edge ->
						{
							if (!edgesToMoveThrough.contains(edge))
							{
								edge.forgetRegistersInManifestsRecursively(
									writtenSet);
							}
						});

						// We moved the instruction *just* out of the block, so
						// when we move prior instructions, they'll still end up
						// before this one.  Therefore, don't track its reads.
						// Similarly, its writes have also moved past where any
						// new instructions might get moved.
					}
					else
					{
						// The instruction stayed where it was.  Record the
						// registers or semantic values that it consumed, to pin
						// any prior instructions that provide those values,
						// since they also can't be moved out of the block.
						registersConsumedLaterInBlock.addAll(
							instruction.sourceRegisters());
						instruction.readOperands().stream()
							.map(L2ReadOperand::semanticValue)
							.collect(
								toCollection(
									() -> semanticValuesConsumedLaterInBlock));
						// It might read values that prior instructions write,
						// so we should prevent those instructions from moving.
						dependentReadsLaterInBlock |=
							operation.readsHiddenVariablesMask;
						// It might overwrite hidden values needed by prior
						// instructions, so prevent those readers from moving.
						allWritesLaterInBlock |=
							operation.writesHiddenVariablesMask;
						// The last instruction of the block can alter the
						// control flow, so capture its edges.
						reachableTargetEdges.addAll(instruction.targetEdges());
					}
				}
			}
			if (changed)
			{
				updateAllManifests();
				computeLivenessAtEachEdge();
			}
		}
		while (changed);
	}

	/**
	 * If this instruction can be moved/duplicated into one or more successor
	 * blocks, answer a {@link List} of those blocks.  Otherwise answer {@code null}.
	 *
	 * @param instruction
	 *        The instruction to analyze.
	 * @param registersConsumedLaterInBlock
	 *        The set of {@link L2Register}s which are consumed by instructions after the given one within the same {@link L2BasicBlock}.  If the given instruction produces an output consumed by a later instruction, the given instruction cannot be moved forward out of its basic block.
	 * @param semanticValuesConsumedLaterInBlock
	 *        The set of {@link L2SemanticValue}s which are consumed by instructions after the given one within the same block.  If the given instruction produces an output consumed by a later instruction, the given instruction cannot be moved forward out of its basic block.
	 * @param readMaskLaterInBlock
	 *        A mask of {@link HiddenVariable} dependencies that instructions later in the block will read.  Writes to these hidden variables must not be moved past those reads, or the reads won't be able to access the values.
	 * @param writeMaskLaterInBlock
	 *        A mask of {@link HiddenVariable} dependencies that instructions later in the block will write.  Reads of these hidden variables must not be moved past those writes, or the reads won't be able to access the values.
	 * @param candidateTargetEdges
	 *        The edges that this instruction might be moved through.  These are all outbound edges in instructions that occur later in the current basic block.
	 * @return
	 * The successor {@link L2PcOperand}s through which the instruction can be moved, or {@code null} if the instruction should not move.
	 */
	private static @Nullable Set<L2PcOperand> successorEdgesToMoveThrough (
		final L2Instruction instruction,
		final Set<L2Register> registersConsumedLaterInBlock,
		final Set<L2SemanticValue> semanticValuesConsumedLaterInBlock,
		final int readMaskLaterInBlock,
		final int writeMaskLaterInBlock,
		final Set<L2PcOperand> candidateTargetEdges)
	{
		if (instruction.altersControlFlow()
			|| instruction.operation().isPhi()
			|| instruction.isEntryPoint())
		{
			return null;
		}
		if (instruction.hasSideEffect())
		{
			return null;
		}
		final int writeMask = instruction.operation().writesHiddenVariablesMask;
		if ((writeMask & readMaskLaterInBlock) != 0
			|| (writeMask & writeMaskLaterInBlock) != 0)
		{
			// Either the instruction writes something that a later instruction
			// reads, or there are multiple writes that should maintain their
			// order in case a later block has a dependent read.  Don't move it.
			return null;
		}
		final int readMask = instruction.operation().readsHiddenVariablesMask;
		if ((readMask & writeMaskLaterInBlock) != 0)
		{
			// The instruction reads something that a later instruction would
			// clobber.  Don't move it.
			return null;
		}

		final List<L2Register> written = instruction.destinationRegisters();
		final List<L2SemanticValue> writtenSemanticValues = new ArrayList<>();
		instruction.writeOperands().forEach(
			write -> writtenSemanticValues.addAll(write.semanticValues()));
		assert !written.isEmpty()
			: "Every instruction should either have side effects or write to "
			+ "at least one register";
		if (!disjoint(written, registersConsumedLaterInBlock))
		{
			// A later instruction in the current basic block consumes one of
			// the registers produced by the given instruction, so we can't move
			// the given instruction into later blocks.
			return null;
		}
		if (!disjoint(
			writtenSemanticValues, semanticValuesConsumedLaterInBlock))
		{
			// A later instruction in the current basic block consumes one of
			// the semantic values produced by the given instruction, so we
			// can't move the given instruction into later blocks.
			return null;
		}
		if (candidateTargetEdges.size() == 1)
		{
			// There's only one successor edge.  Since the CFG is in edge-split
			// form, the successor might have multiple predecessors.  Don't move
			// across the edge in that case, since it may cause the instruction
			// to run in situations that it doesn't need to.
			//
			// TODO When code splitting is eventually implemented, it should
			// clean up this case by duplicating the successor block just for
			// this edge.
			final L2PcOperand successorEdge =
				candidateTargetEdges.iterator().next();
			final L2BasicBlock successor = successorEdge.targetBlock();
			if (successor.predecessorEdgesCount() > 1)
			{
				return null;
			}
		}
		final Set<L2PcOperand> destinations = new HashSet<>();
		boolean shouldMoveInstruction = false;
		for (final L2PcOperand edge : candidateTargetEdges)
		{
			final L2BasicBlock targetBlock = edge.targetBlock();
			assert targetBlock.predecessorEdgesCount() == 1
				: "CFG is not in edge-split form";
			if (!edge.alwaysLiveInRegisters.containsAll(written))
			{
				// There's an edge that it shouldn't flow to.
				shouldMoveInstruction = true;
			}
			if (!disjoint(edge.sometimesLiveInRegisters, written))
			{
				// There's an edge that's only sometimes live-in.
				shouldMoveInstruction = true;
				destinations.add(edge);
			}
		}
		if (!shouldMoveInstruction)
		{
			// It was always-live-in for every successor.
			return null;
		}
		// Due to previous code motion, the destinations list might be empty.
		// Skip the move, and let dead code elimination get it instead.
		if (destinations.isEmpty())
		{
			return null;
		}
		return destinations;
	}

	/**
	 * Find any remaining occurrences of {@link L2_VIRTUAL_CREATE_LABEL}, or any other {@link L2Instruction} using an {@link L2Operation} that says it {@link L2Operation#isPlaceholder()}.
	 *
	 * <p>Replace the instruction with code it produces via
	 * {@link L2Generator#replaceInstructionByGenerating(L2Instruction)}.</p>
	 *
	 * <p>If any placeholder instructions were found and replaced, set
	 * {@link #replacedAnyPlaceholders}.</p>
	 */
	void replacePlaceholderInstructions ()
	{
		replacedAnyPlaceholders = false;
		// Since placeholder expansions can introduce new basic blocks, it's
		// best to start the search at the beginning again after a substitution.
		outer:
		while (true)
		{
			for (final L2BasicBlock block : blocks)
			{
				for (final L2Instruction instruction : block.instructions())
				{
					if (instruction.operation().isPlaceholder())
					{
						generator.replaceInstructionByGenerating(instruction);
						computeLivenessAtEachEdge();
						replacedAnyPlaceholders = true;
						continue outer;
					}
				}
			}
			// There were no replacements this round, so we're done.
			break;
		}
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
				final L2_PHI_PSEUDO_OPERATION<?, ?, ?> phiOperation =
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
						.isUnconditionalJump();
					final L2ReadOperand<?> sourceRead = phiSources.get(i);
					final L2Instruction move =
						new L2Instruction(
							predecessor,
							phiOperation.getMoveOperation(),
							sourceRead,
							targetWriter.clone());
					predecessor.insertInstruction(
						instructions.size() - 1, move);
					if (edge.isBackward())
					{
						edge.manifest().replaceDefinitions(
							phiOperation.getMoveOperation().destinationOf(move));
					}
					else
					{
						edge.manifest().recordDefinition(
							phiOperation.getMoveOperation().destinationOf(move));
					}
					if (edge.forcedClampedEntities != null)
					{
						// Replace the semantic value(s) and register in the
						// clamped set of entities, if present.
						final Set<L2Entity> clamped =
							edge.forcedClampedEntities;
						if (clamped.remove(sourceRead.semanticValue()))
						{
							clamped.addAll(targetWriter.semanticValues());
						}
						if (clamped.remove(sourceRead.register()))
						{
							clamped.add(targetWriter.register());
						}
					}
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
	 * For each {@link L2_MOVE} instruction, if the register groups associated with the source and destination registers don't have an interference edge between them then merge the groups together.  The resulting merged group should have interferences with each group that the either the source register's group or the destination register's group had interferences with.
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
	 * {@link L2Register#getUniqueValue()} that's the same as its {@link  L2Register#finalIndex() finalIndex}.
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
		final Function1<? super L2Register, Unit> action = reg ->
		{
			remap.put(
				reg,
				byKindAndIndex
					.computeIfAbsent(reg.registerKind(), k -> new HashMap<>())
					.computeIfAbsent(
						reg.finalIndex(), i -> reg.copyAfterColoring()));
			oldRegisters.add(reg);
			return null;
		};
		blocks.forEach(
			block -> block.instructions().forEach(
				instruction ->
				{
					instruction.sourceRegisters().forEach(action::invoke);
					instruction.destinationRegisters().forEach(action::invoke);
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
	 * Eliminate any {@link L2_MOVE}s between registers of the same color. The graph must have been colored already, and is not expected to be in SSA form, and is certainly not after this, since removed moves are the SSA definition points for their target registers.
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
				final L2PcOperand jumpEdge;
				if (soleInstruction.operation() == L2_JUMP.INSTANCE)
				{
					jumpEdge = L2_JUMP.jumpTarget(soleInstruction);
				}
				else if (soleInstruction.operation() == L2_JUMP_BACK.INSTANCE)
				{
					jumpEdge = L2_JUMP_BACK.jumpTarget(soleInstruction);
				}
				else
				{
					continue;
				}
				// Redirect all predecessors through the jump.
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
					block.instructions().clear();
					soleInstruction.justRemoved();
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
		 * Which registers are live here, organized by {@link RegisterKind}'s ordinal.
		 */
		final BitSet[] liveRegistersByKind;

		/**
		 * Reduce the collection of registers live here by intersecting it with
		 * the argument.  Answer whether it changed.
		 *
		 * @param another
		 * The other {@code UsedRegisters}.
		 * @return
		 * Whether the intersection made a change.
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

		/**
		 * Record a register being read.
		 *
		 * @param register
		 *        The register being read.
		 * @param registerIdFunction
		 *        How to extract an id from the register.
		 */
		void readRegister (
			final L2Register register,
			final Function1<L2Register, Integer> registerIdFunction)
		{
			assert liveRegistersByKind[register.registerKind().ordinal()]
				.get(registerIdFunction.invoke(register));
		}

		/**
		 * Process a register being written.
		 *
		 * @param register
		 *        The register being written.
		 * @param registerIdFunction
		 *        How to extract an id from the register.
		 */
		void writeRegister (
			final L2Register register,
			final Function1<L2Register, Integer> registerIdFunction)
		{
			liveRegistersByKind[register.registerKind().ordinal()]
				.set(registerIdFunction.invoke(register));
		}

		/**
		 * Clear usage information about all registers.
		 */
		void clearAll ()
		{
			//noinspection ForLoopReplaceableByForEach
			for (int i = 0; i < liveRegistersByKind.length; i++)
			{
				liveRegistersByKind[i].clear();
			}
		}

		/** Create an instance with no tracking information. */
		UsedRegisters ()
		{
			liveRegistersByKind = new BitSet[RegisterKind.all.length];
			for (int i = 0; i < liveRegistersByKind.length; i++)
			{
				liveRegistersByKind[i] = new BitSet();
			}
		}

		/**
		 * Duplicate an existing instance.
		 *
		 * @param original
		 * The existing instance to duplicate.
		 */
		UsedRegisters (final UsedRegisters original)
		{
			liveRegistersByKind = new BitSet[RegisterKind.all.length];
			for (int i = 0; i < liveRegistersByKind.length; i++)
			{
				liveRegistersByKind[i] =
					cast(original.liveRegistersByKind[i].clone());
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
	 * {@link L2Instruction#basicBlock()} field.  Also check that every
	 * instruction's applicable operands are listed as uses or definitions of
	 * the register that they access, and that there are no other uses or
	 * definitions.
	 */
	private void checkBlocksAndInstructions ()
	{
		final Map<L2Register, Set<L2ReadOperand<?>>> uses = new HashMap<>();
		final Map<L2Register, Set<L2WriteOperand<?>>> definitions =
			new HashMap<>();
		blocks.forEach(
			block -> block.instructions().forEach(
				instruction ->
				{
					instruction.assertHasBeenEmitted();
					instruction.readOperands().forEach(read ->
						uses
							.computeIfAbsent(
								read.register(), x -> new HashSet<>())
							.add(read));
					instruction.writeOperands().forEach(write ->
						definitions
							.computeIfAbsent(
								write.register(), x -> new HashSet<>())
							.add(write));
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
						return null;
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
			final List<L2PcOperand> allEdgesFromBlock = new ArrayList<>();
			for (final L2Instruction instruction : block.instructions())
			{
				assert !instruction.operation().isPhi()
					|| instruction.sourceRegisters().size()
					== block.predecessorEdgesCount();
				allEdgesFromBlock.addAll(instruction.targetEdges());
			}
			assert block.successorEdgesCopy().equals(allEdgesFromBlock);
			// Check edges going forward.
			assert new HashSet<>(allEdgesFromBlock).equals(
				new HashSet<> (block.successorEdgesCopy()));
			block.successorEdgesDo(edge ->
			{
				assert edge.sourceBlock() == block;
				final L2BasicBlock targetBlock = edge.targetBlock();
				assert !edge.isBackward() || targetBlock.isLoopHead;
				assert blocks.contains(targetBlock);
				assert targetBlock.predecessorEdgesCopy().contains(edge);
				return null;
			});
			// Also check incoming edges.
			block.predecessorEdgesDo(inEdge ->
			{
				assert inEdge.targetBlock() == block;
				final L2BasicBlock predecessorBlock = inEdge.sourceBlock();
				assert blocks.contains(predecessorBlock);
				assert predecessorBlock.successorEdgesCopy().contains(inEdge);
				return null;
			});
		}
	}

	/**
	 * Perform a basic sanity check on the instruction graph, ensuring that each
	 * use of a register is preceded in all histories by a write to it.  Use the
	 * provided function to indicate what "the same" register means, so that
	 * this can be used for uncolored SSA and colored non-SSA graphs.
	 *
	 * @param registerIdFunction
	 *        A function that transforms a register into the index that should be used to identify it.  This allows pre-colored and post-colored register uses to be treated differently.
	 */
	private void checkRegistersAreInitialized (
		final Function1<L2Register, Integer> registerIdFunction)
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
					System.err.println("Phi predecessor not found");
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
	 * Ensure each instruction that's an {@linkplain L2Instruction#isEntryPoint() entry point} occurs at the start of a block.
	 */
	private void checkEntryPoints ()
	{
		blocks.forEach(
			b -> b.instructions().forEach(
				i ->
				{
					assert !i.isEntryPoint() || b.instructions().get(0) == i;
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
			checkRegistersAreInitialized(L2Register::getUniqueValue);
			checkEntryPoints();
			final long after = captureNanos();
			sanityCheckStat.record(
				after - before, interpreter.interpreterIndex);
		}
	}

	/**
	 * Optimize the graph of instructions.
	 *
	 * @param interpreter
	 * The current {@link Interpreter}.
	 */
	public void optimize (final Interpreter interpreter)
	{
		try
		{
			sanityCheck(interpreter);
			for (final OptimizationPhase phase : OptimizationPhase.values())
			{
				final long before = captureNanos();
				phase.run(this);
				final long after = captureNanos();
				phase.stat.record(after - before, interpreter.interpreterIndex);
				sanityCheck(interpreter);
			}
		}
		catch (final Throwable e)
		{
			// Here's a good place for a breakpoint, to allow L2 translation to
			// restart, since the outer catch is already too late.
			System.err.println("Unrecoverable problem during optimization.");
			throw e;
		}
	}
}
