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
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2Register;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
	 * The {@link Set} of {@link L2Register}s that are written in all pasts, and
	 * are consumed along all future paths after the start of this block.  This
	 * is only populated during optimization, while the control flow graph is
	 * still in SSA form.
	 *
	 * <p>This is a superset of {@link #sometimesLiveInRegisters}.</p>
	 */
	public final Set<L2Register> alwaysLiveInRegisters = new HashSet<>();

	/**
	 * The {@link Set} of {@link L2Register}s that are written in all pasts, and
	 * are consumed along at least one future after the start of this block.
	 * This is only populated during optimization, while the control flow graph
	 * is still in SSA form.
	 *
	 * <p>This is a subset of {@link #alwaysLiveInRegisters}.</p>
	 */
	public final Set<L2Register> sometimesLiveInRegisters = new HashSet<>();

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
	void makeIrremovable ()
	{
		isIrremovable = true;
	}

	/**
	 * Answer whether this block must be tracked until final code generation.
	 * */
	boolean isIrremovable ()
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
	L2Instruction finalInstruction ()
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
	 *        The {@link L1Translator} generating instructions.
	 */
	void startIn (final L1Translator translator)
	{
		translator.currentManifest.clear();
		if (isIrremovable())
		{
			// Irremovable blocks are entry points, and don't require any
			// registers to be defined yet, so ignore any registers that appear
			// to be defined (they're really not).
			return;
		}
		// Keep semantic values that are common to all incoming paths.  Create
		// phi functions if the registers disagree.
		final List<L2ValueManifest> manifests = new ArrayList<>();
		for (final L2PcOperand predecessorEdge : predecessorEdges)
		{
			manifests.add(predecessorEdge.manifest());
		}
		translator.currentManifest.populateFromIntersection(
			manifests, translator);
	}

	/**
	 * Append an instruction to this basic block, notifying the operands that
	 * the instruction was just added.  Adding a phi instruction automatically
	 * places it at the start.
	 *
	 * @param instruction The {@link L2Instruction} to append.
	 */
	public void addInstruction (final L2Instruction instruction)
	{
		assert isIrremovable() || hasPredecessors();
		justAddInstruction(instruction);
		instruction.justAdded();
	}

	/**
	 * Append an instruction to this basic block, without telling the operands
	 * that the instruction was just added.  Adding a phi instruction
	 * automatically places it at the start.
	 *
	 * @param instruction The {@link L2Instruction} to append.
	 */
	public void justAddInstruction (final L2Instruction instruction)
	{
		assert !hasControlFlowAtEnd;
		assert instruction.basicBlock == this;
		if (instruction.operation.isPhi())
		{
			// For simplicity, phi functions are routed to the *start* of the
			// block.
			instructions.add(0, instruction);
		}
		else
		{
			instructions.add(instruction);
		}
		hasStartedCodeGeneration = true;
		hasControlFlowAtEnd = instruction.altersControlFlow();
	}

	/**
	 * Insert an instruction in this basic block at the specified instruction
	 * index, notifying the operands that the instruction was just added.
	 *
	 * @param index The index at which to insert the instruction.
	 * @param instruction The {@link L2Instruction} to insert.
	 */
	public void insertInstruction (
		final int index,
		final L2Instruction instruction)
	{
		assert isIrremovable() || hasPredecessors();
		assert instruction.basicBlock == this;
		instructions.add(index, instruction);
		hasStartedCodeGeneration = true;
		instruction.justAdded();
	}

	/**
	 * The final instruction of the block altered control flow, and it has just
	 * been removed.  Subsequent code generation may take place in this block.
	 */
	public void removedControlFlowInstruction ()
	{
		hasControlFlowAtEnd = false;
	}

	/**
	 * Determine whether code added after the last instruction of this block
	 * would be reachable.  Take into account whether the block itself seems to
	 * be reachable.
	 *
	 * @return Whether it would be possible to reach a new instruction added to
	 *         this block.
	 */
	boolean currentlyReachable ()
	{
		return (isIrremovable || !predecessorEdges.isEmpty())
			&& !hasControlFlowAtEnd;
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
	void generateOn (final List<L2Instruction> output)
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
