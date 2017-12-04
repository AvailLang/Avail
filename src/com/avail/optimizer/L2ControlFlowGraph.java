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

import com.avail.descriptor.A_BasicObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteVectorOperand;
import com.avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.utility.Mutable;
import com.avail.utility.Pair;

import javax.annotation.Nullable;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

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
	private void removeDeadCode ()
	{
		//noinspection StatementWithEmptyBody
		while (removeUnreachableBlocks() || removeDeadInstructions()) { }
	}

	/**
	 * For every edge leading from a multiple-out block to a multiple-in block,
	 * split it by inserting a new block along it.  Note that we do this
	 * regardless of whether the target block has any phi functions.
	 */
	private void transformToEdgeSplitSSA ()
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
	 * Determine which registers are live-in for each block.
	 */
	private void computeLivenessAtEachEdge ()
	{
		for (final L2BasicBlock block : basicBlockOrder)
		{
			block.liveInRegisters.clear();
		}

		// The deque and the set maintain the same membership.
		final Deque<L2BasicBlock> workQueue = new ArrayDeque<>(basicBlockOrder);
		final Set<L2BasicBlock> workSet = new HashSet<>(basicBlockOrder);
		while (!workQueue.isEmpty())
		{
			final L2BasicBlock block = workQueue.removeLast();
			workSet.remove(block);
			// Take the union of the outbound edges' known live registers.
			final Set<L2Register> knownLive = new HashSet<>();
			for (final L2PcOperand edge : block.successorEdges())
			{
				knownLive.addAll(edge.targetBlock().liveInRegisters);
			}
			// Now work backward through each instruction, removing registers
			// that it writes, and adding registers that it reads.
			final List<L2Instruction> instructions = block.instructions();
			for (int i = instructions.size() - 1; i >= 0; i--)
			{
				final L2Instruction instruction = instructions.get(i);
				knownLive.removeAll(instruction.destinationRegisters());
				knownLive.addAll(instruction.sourceRegisters());
			}
			final boolean changed = block.liveInRegisters.addAll(knownLive);
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
	private void considerSideEffectRegistersLive ()
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
			block.liveInRegisters.addAll(registers);
		}
	}

	/**
	 * If a block has successor blocks with differing live-in sets, for each
	 * such register that's live at one successor but not another, try to move
	 * the defining instruction forward along the path where it's needed.  The
	 * goal is to avoid executing the instruction along paths where it's
	 * <em>not</em> needed.
	 *
	 * <p>If there are multiple successor blocks that need the register (say, a
	 * three-way branch where two branches use it), don't bother moving the
	 * instruction.  We'd have to either (1) break SSA by duplicating the
	 * instruction, or (2) find the dominator, and move the instruction
	 * there.</p>
	 *
	 * <p>Also, consider a block that defines X, then defines Y using X (i.e.,
	 * an instruction reads X and writes Y).  If Y is used by all successors,
	 * there's no advantage in trying to move the definition of X forward, since
	 * it really would have been needed to be calculated along all paths anyhow,
	 * since Y was needed.</p>
	 *
	 * <p>We deal with that by failing to move X past its use to compute Y.  In
	 * the other scenario where Y was only used in some successor blocks and
	 * could be moved, we'll get another chance on a subsequent loop to see if X
	 * can also move.</p>
	 *
	 * <p>Requires and preserves edge-split SSA form.</p>
	 */
	private void postponeConditionallyUsedValues ()
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
					final @Nullable L2BasicBlock blockToMoveTo =
						blockToMoveTo(instruction);
					if (blockToMoveTo != null)
					{
						changed = true;
						final L2Instruction newInstruction =
							new L2Instruction(
								blockToMoveTo,
								instruction.operation,
								instruction.operands);
						blockToMoveTo.insertInstruction(0, newInstruction);

						block.instructions().remove(instruction);
						instruction.justRemoved();

						// None of the registers defined by the instruction
						// should be live-in any more at the new block.
						blockToMoveTo.liveInRegisters.removeAll(
							newInstruction.destinationRegisters());
					}
				}
			}
		}
		while (changed);
	}

	/**
	 * Answer whether it's safe to move this instruction to the start of exactly
	 * one of its successor block.  Assumes {@link #computeLivenessAtEachEdge()}
	 * has already run.
	 *
	 * @param instruction The instruction to analyze.
	 * @return The successor {@link L2BasicBlock} to which the instruction can
	 *         be moved, or {@code null} if there is no such successor block.
	 */
	private static @Nullable L2BasicBlock blockToMoveTo (
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
			// SSA form, the successor might have multiple predecessors.  Don't
			// move across the edge in that case, since it may cause the
			// instruction to run in situations that it doesn't need to.
			final L2BasicBlock successor = successorEdges.get(0).targetBlock();
			if (successor.predecessorEdges().size() > 1)
			{
				return null;
			}
		}
		// Examine the successors, determining which ones require *any* of the
		// destination registers of the instruction.  If there are multiple,
		// give up.
		@Nullable L2BasicBlock candidate = null;
		for (final L2PcOperand edge : successorEdges)
		{
			final L2BasicBlock targetBlock = edge.targetBlock();
			assert targetBlock.predecessorEdges().size() <= 1
				: "CFG is not in edge-split SSA form";
			if (!disjoint(written, targetBlock.liveInRegisters))
			{
				//noinspection VariableNotUsedInsideIf
				if (candidate != null)
				{
					// There are multiple candidate successor blocks.  Don't
					// move the instruction.
					return null;
				}
				candidate = targetBlock;
			}
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
		return candidate;
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
	private void insertPhiMoves ()
	{
		for (final L2BasicBlock block : basicBlockOrder)
		{
			final List<L2Instruction> instructionsToPrepend = new ArrayList<>();
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
				@Nullable A_BasicObject constant = null;
				final List<L2ReadPointerOperand> sourceReaders =
					L2_PHI_PSEUDO_OPERATION.sourceRegisterReads(instruction);

				// Check for the special case that all the phi sources supply
				// the same constant value.
				for (int i = 0; i < fanIn; i++)
				{
					final @Nullable A_BasicObject newConstant =
						sourceReaders.get(i).constantOrNull();
					if (newConstant == null
						|| (constant != null && !newConstant.equals(constant)))
					{
						constant = null;
						break;
					}
					constant = newConstant;
				}
				if (constant != null)
				{
					// All inputs provide the same constant value.  Convert the
					// phi into a constant move, rather than introduce non-SSA
					// moves for it in the predecessor blocks.  Note that this
					// may cause the incoming registers to be dead.  Also note
					// that the order of these constant moves doesn't matter.
					instructionsToPrepend.add(
						new L2Instruction(
							block,
							L2_MOVE_CONSTANT.instance,
							new L2ConstantOperand(constant),
							targetWriter));
				}
				else
				{
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
				}
				// Eliminate the phi function itself.
				instructionIterator.remove();
				instruction.justRemoved();
			}
			block.instructions().addAll(0, instructionsToPrepend);
			instructionsToPrepend.forEach(L2Instruction::justAdded);
		}
	}

	/**
	 * Create a new register for every &lt;kind, finalIndex&gt; (i.e., color) of
	 * an existing register, then transform every instruction of this control
	 * flow graph to use the new registers.  The new registers have a
	 * {@link L2Register#uniqueValue} that's the same as its {@link
	 * L2Register#finalIndex}.
	 */
	public void replaceRegistersByColor ()
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
	private void removeSameColorMoves ()
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
	 * @param registerIdFunction
	 *        A function that transforms a register into the index that should
	 *        be used to identify it.  This allows pre-colored and post-colored
	 *        register uses to be treated differently.
	 */
	private void sanityCheck (
		final Function<L2Register, Integer> registerIdFunction)
	{
		if (shouldSanityCheck)
		{
			checkBlocksAndInstructions();
			checkUniqueOperands();
			checkEdgesAndPhis();
			checkRegistersAreInitialized(registerIdFunction);
		}
	}

	/**
	 * Optimize the graph of instructions.
	 */
	public void optimize ()
	{
		sanityCheck(L2Register::uniqueValue);

		// Remove all unreachable blocks, and any reachable instructions that
		// neither have an effect nor produce a value that is transitively
		// consumed by a later instruction that has an effect.
		removeDeadCode();
		sanityCheck(L2Register::uniqueValue);

		// Transform into SSA edge-split form, to avoid inserting redundant
		// phi-moves.
		transformToEdgeSplitSSA();
		sanityCheck(L2Register::uniqueValue);

		// Find multi-way instructions (at the ends of basic blocks) where along
		// some outbound edges a value is live, but along other edges from the
		// same block, the value is not live.  If it's safe, we move the
		// defining instruction forward along the edges where it's live, thereby
		// not having to execute it along paths where it's not actually live.
		computeLivenessAtEachEdge();
		considerSideEffectRegistersLive();
		postponeConditionallyUsedValues();
		sanityCheck(L2Register::uniqueValue);

		// Insert phi moves, which makes it no longer in SSA form.
		insertPhiMoves();
		sanityCheck(L2Register::uniqueValue);

		// Remove constant moves made unnecessary by the introduction of new
		// constant moves after phis (the ones that are constant-valued).
		removeDeadCode();
		sanityCheck(L2Register::uniqueValue);

		// Compute the register-coloring interference graph while we're just out
		// of SSA form – phis have been replaced by moves on incoming edges.
		final L2RegisterColorer colorer = new L2RegisterColorer(this);
		colorer.computeInterferenceGraph();

		// Color all registers, using the previously computed interference
		// graph.  This creates a dense finalIndex numbering for the registers
		// in such a way that no two registers that have to maintain distinct
		// values at the same time will have the same number.
		colorer.coalesceNoninterferingMoves();
		colorer.computeColors();
		sanityCheck(L2Register::finalIndex);

		// Create a replacement register for each used color (of each kind).
		// Transform each reference to an old register into a reference to the
		// replacement, updating structures as needed.
		replaceRegistersByColor();
		sanityCheck(L2Register::finalIndex);
		sanityCheck(L2Register::uniqueValue);

		// Remove moves between registers with the same color.  This may expose
		// edges-to-jumps that would otherwise be concealed by useless moves.
		removeSameColorMoves();
		sanityCheck(L2Register::finalIndex);

		// Every L2PcOperand that leads to an L2_JUMP should now be redirected
		// to the target of the jump (transitively, if the jump leads to another
		// jump).  We specifically do this after inserting phi moves to ensure
		// we don't jump past irremovable phi moves.
		adjustEdgesToJumps();
		sanityCheck(L2Register::finalIndex);

		// Having adjusted edges to avoid landing on L2_JUMPs, some blocks may
		// have become unreachable.
		removeUnreachableBlocks();
		sanityCheck(L2Register::finalIndex);

		// Choose an order for the blocks.  This isn't important while we're
		// interpreting L2Chunks, but it will ultimately affect the quality of
		// JVM translation.  Prefer to have the target block of an unconditional
		// jump to follow the jump, since final code generation elides the jump.
		orderBlocks();
		sanityCheck(L2Register::finalIndex);

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
