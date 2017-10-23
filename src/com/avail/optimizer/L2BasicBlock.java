/**
 * L2BasicBlock.java
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
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static com.avail.optimizer.L1Translator.readVector;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * This is a traditional basic block, consisting of a sequence of {@link
 * L2Instruction}s.  It has no incoming jumps except to the start, and has no
 * outgoing jumps or branches except from the last instruction.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2BasicBlock
{
	/** A descriptive name for this basic block. */
	private final String name;

	/** The sequence of instructions within this basic block. */
	private final List<L2Instruction> instructions = new ArrayList<>();

	/**
	 * The {@link L2PcOperand}s that point to basic blocks that follow this one,
	 * taken in order from the last instruction.  This is kept synchronized with
	 * the predecessor lists of the successor blocks.
	 */
	private final List<L2PcOperand> successorEdges = new ArrayList<>(2);

	/**
	 * The {@link L2PcOperand}s that point to this basic block.  They capture
	 * their containing {@link L2Instruction}, which knows its own basic block,
	 * so we can easily get to the originating basic block.
	 */
	private final List<L2PcOperand> predecessorEdges = new ArrayList<>();

	/**
	 * The L2 offset at which the block starts.  Only populated after code
	 * generation has completed.
	 */
	private int offset = -1;

	/**
	 * Set for blocks that must not be removed.  These blocks may be referenced
	 * for tracking entry points, and must exist through final code generation.
	 */
	private boolean isIrremovable = false;

	/** Whether we've started adding instructions to this basic block. */
	private boolean hasStartedCodeGeneration = false;

	/**
	 * Keeps track whether a control-flow altering instruction has been added
	 * yet.  There must be one, and it must be the last instruction in the
	 * block.
	 */
	private boolean hasControlFlowAtEnd = false;

	/**
	 * Answer the descriptive name of this basic block.
	 *
	 * @return The basic block's name.
	 */
	public String name ()
	{
		return name;
	}

	/**
	 * Prevent this block from being removed, so that its position can be
	 * identified in the final generated code.
	 */
	public void makeIrremovable ()
	{
		isIrremovable = true;
	}

	/**
	 * Answer whether this block must be tracked until final code generation.
	 * */
	public boolean isIrremovable ()
	{
		return isIrremovable;
	}

	/**
	 * Answer the L2 offset at which the block starts.  Only populated after
	 * code generation has completed.
	 */
	public int offset ()
	{
		return offset;
	}

	/**
	 * Answer this block's {@link List} of {@link L2Instruction}.  They consist
	 * of a sequence of non-branching instructions, ending in an instruction
	 * that branches to zero or more targets via its {@link L2PcOperand}s.
	 *
	 * @return The block's {@link List} of {@link L2Instruction}s.
	 */
	public List<L2Instruction> instructions ()
	{
		return instructions;
	}

	/**
	 * Answer this block's last {@link L2Instruction}.  This instruction must be
	 * a branching instruction of some sort, having zero or more target edges,
	 * but not falling through to the next instruction (which doesn't exist).
	 *
	 * @return The last {@link L2Instruction} of this block.
	 */
	public L2Instruction finalInstruction ()
	{
		return instructions.get(instructions.size() - 1);
	}

	/**
	 * Add a predecessor, due to an earlier basic block adding an instruction
	 * that reaches this basic block.
	 *
	 * @param predecessorEdge The {@link L2PcOperand} that leads here.
	 */
	public void addPredecessorEdge (
		final L2PcOperand predecessorEdge)
	{
		assert predecessorEdge.sourceBlock().hasStartedCodeGeneration;
		if (hasStartedCodeGeneration)
		{
			assert predecessorEdge.slotRegisters().length == 0;
			predecessorEdges.forEach(
				e ->
				{
					assert e.slotRegisters().length == 0;
				});
		}
		predecessorEdges.add(predecessorEdge);
	}

	/**
	 * Remove a predecessor, perhaps due to a branch to it becoming unreachable.
	 *
	 * @param predecessorEdge The {@link L2PcOperand} that no longer leads here.
	 */
	public void removePredecessorEdge (
		final L2PcOperand predecessorEdge)
	{
		assert predecessorEdge.sourceBlock().hasStartedCodeGeneration;
		// TODO MvG - We should probably rebuild the phis here.
		if (hasStartedCodeGeneration)
		{
			final int index = predecessorEdges.indexOf(predecessorEdge);
			predecessorEdges.remove(index);
			for (int i = 0; i < instructions.size(); i++)
			{
				final L2Instruction instruction = instructions.get(i);
				if (instruction.operation.isPhi())
				{
					final L2Instruction replacement =
						L2_PHI_PSEUDO_OPERATION.withoutIndex(
							instruction, index);
					instructions.set(i, replacement);
				}
				else
				{
					// Phi functions are always at the start of a block.
					break;
				}
			}
		}
		predecessorEdges.remove(predecessorEdge);
	}

	/**
	 * Determine if this basic block has any predecessor basic blocks.
	 *
	 * @return The value {@code true} if this basic block has one or more
	 *         predecessor basic blocks, otherwise {@code false}.
	 */
	public boolean hasPredecessors ()
	{
		return !predecessorEdges.isEmpty();
	}

	/**
	 * Answer all {@link L2PcOperand}s that lead to this block.
	 *
	 * @return The {@link List} of {@link L2PcOperand}s leading here.
	 */
	public List<L2PcOperand> predecessorEdges ()
	{
		return predecessorEdges;
	}
	/**
	 * Answer the {@link L2PcOperand}s taken in order from the last {@link
	 * L2Instruction} of this basic block.  These operands lead to the successor
	 * blocks of this one.
	 *
	 * @return a {@link List} of {@link L2PcOperand}s.
	 */
	public List<L2PcOperand> successorEdges ()
	{
		return successorEdges;
	}

	/**
	 * Having completed code generation in each of its predecessors, prepare
	 * this block for its own code generation.
	 *
	 * <p>Examine the {@link #predecessorEdges} to determine if phi operations
	 * need to be created to merge differences in the slot-to-register arrays.
	 * These will eventually turn into move instructions along the incoming
	 * edges, which will usually end up with the same register color on both
	 * ends of the move, allowing it to be eliminated.</p>
	 *
	 * <p>If phi operations are added, their target registers will be written to
	 * the appropriate slotRegisters of the provided {@link L1Translator}.
	 * </p>
	 *
	 * @param translator
	 *        The {@link L1Translator} that's generating instructions.
	 */
	public void startIn (final L1Translator translator)
	{
		final int numSlots = translator.numSlots;
		final List<List<L2ReadPointerOperand>> sourcesBySlot =
			IntStream.range(0, numSlots)
				.mapToObj(i -> new ArrayList<L2ReadPointerOperand>())
				.collect(toList());
		// Determine the sets of registers feeding each slot index.
		for (final L2PcOperand edge : predecessorEdges)
		{
			final L2ReadPointerOperand[] edgeSlots = edge.slotRegisters();
			assert edgeSlots.length == numSlots;
			for (int i = 0; i < numSlots; i++)
			{
				sourcesBySlot.get(i).add(edgeSlots[i]);
			}
		}
		if (isIrremovable())
		{
			// Irremovable blocks are entry points, and don't require any
			// registers to be defined yet, so ignore any registers that appear
			// to be defined (they're really not).
			return;
		}
		// Create phi operations.
		for (int slotIndex = 1; slotIndex <= numSlots; slotIndex++)
		{
			final List<L2ReadPointerOperand> registerReads =
				sourcesBySlot.get(slotIndex - 1);
			final Set<L2ObjectRegister> distinct = registerReads.stream()
				.map(L2ReadPointerOperand::register)
				.collect(toSet());
			assert !distinct.isEmpty();
			if (distinct.size() == 1)
			{
				// All predecessors set up this register the same way, so this
				// register doesn't need a phi function.  However, this might
				// not be the *current* register, due to block creation order.
				// Bring the translator into agreement.
				translator.forceSlotRegister(slotIndex, registerReads.get(0));
			}
			else
			{
				// Create a phi instruction to merge these sources together into
				// a new register at that slot index.
				@SuppressWarnings("ConstantConditions")
				final TypeRestriction restriction = distinct.stream()
					.map(L2ObjectRegister::restriction)
					.reduce(TypeRestriction::union)
					.get();
				translator.addInstruction(
					L2_PHI_PSEUDO_OPERATION.instance,
					readVector(registerReads),
					translator.writeSlot(
						slotIndex,
						restriction.type,
						restriction.constantOrNull));
			}
		}
	}

	/**
	 * Append an instruction to this basic block.  The array of {@link
	 * L2ObjectRegister}s represents the state of the virtual continuation slot
	 * registers after the instruction.
	 *
	 * <p>Technically, the array of registers doesn't have to be correlated to
	 * the virtual continuation, so we could include temp registers that require
	 * a phi mapping.</p>
	 *
	 * @param instruction The {@link L2Instruction} to append.
	 */
	public void addInstruction (final L2Instruction instruction)
	{
		assert !hasControlFlowAtEnd;
		assert instruction.basicBlock == this;
		instructions.add(instruction);
		hasStartedCodeGeneration = true;
		hasControlFlowAtEnd = instruction.altersControlFlow();
		instruction.justAdded();
	}

	/**
	 * Create a new basic block.
	 */
	public L2BasicBlock (final String name)
	{
		this.name = name;
	}

	/**
	 * Add this block's instructions to the given instruction list.  Also do
	 * a special peephole optimization by removing any preceding {@link L2_JUMP}
	 * if its target is this block.
	 *
	 * @param output
	 *        The {@link List} of {@link L2Instruction}s in which to append this
	 *        basic block's instructions.
	 */
	public void generateOn (final List<L2Instruction> output)
	{
		// If the preceding instruction was a jump to here, remove it.  In fact,
		// a null-jump might be on the end of the list, hiding another jump
		// just behind it that leads here, making that one also be a null-jump.
		boolean changed;
		do
		{
			changed = false;
			if (!output.isEmpty())
			{
				final L2Instruction previousInstruction =
					output.get(output.size() - 1);
				if (previousInstruction.operation instanceof L2_JUMP)
				{
					if (L2_JUMP.jumpTarget(previousInstruction).targetBlock()
						== this)
					{
						output.remove(output.size() - 1);
						changed = true;
					}
				}
			}
		} while (changed);

		int counter = output.size();
		offset = counter;
		for (final L2Instruction instruction : instructions)
		{
			if (instruction.shouldEmit())
			{
				instruction.setOffset(counter++);
				output.add(instruction);
			}
		}
	}

	@Override
	public String toString ()
	{
		return "BasicBlock(" + name + ")";
	}
}
