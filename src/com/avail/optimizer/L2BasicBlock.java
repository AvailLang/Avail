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
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	 * The basic blocks that follow this one, in an order dependent on the last
	 * instruction.  This is kept synchronized with the predecessor lists of the
	 * successor blocks.
	 */
	private final List<L2BasicBlock> successors = new ArrayList<>();

	/**
	 * The basic blocks that precede this one.  This is kept synchronized with
	 * the successor lists of the predecessor blocks.
	 */
	private final List<L2BasicBlock> predecessors = new ArrayList<>();

	/**
	 * The L2 offset at which the block starts.  Only populated after code
	 * generation has completed.
	 */
	private int offset = -1;

	/** Whether we've started adding instructions to this basic block. */
	private boolean hasStartedCodeGeneration = false;

	/**
	 * Keeps track whether a control-flow altering instruction has been added
	 * yet.  There must be one, and it must be the last instruction in the
	 * block.
	 */
	private boolean hasControlFlowAtEnd = false;

	/**
	 * The arrays of virtual continuation slot registers.  The inner arrays are
	 * indexed by slot index, and the outer array is indexed by relative
	 * positions of {@link L2PcOperand} operands.
	 *
	 * <p>Prior to the final instruction being added to this basic block, the
	 * outer array has a single element, the state of the registers after the
	 * last added instruction.</p>
	 */
	@Nullable L2ObjectRegister[][] slotsByTarget;

	/**
	 * Keep a mapping of target registers with their sources.
	 */
	private Map<L2ObjectRegister, Map<L2BasicBlock, L2ObjectRegister>>
		phiSources = new HashMap<>();

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
	 * Answer the L2 offset at which the block starts.  Only populated after
	 * code generation has completed.
	 */
	public int offset ()
	{
		return offset;
	}

	/**
	 * Add a predecessor, due to an earlier basic block adding an instruction
	 * that refers to this basic block.
	 *
	 * @param predecessor The preceding block.
	 * @param slotRegisters The virtual continuation slot registers
	 */
	private void addPredecessor (
		final L2BasicBlock predecessor,
		final L2ObjectRegister[] slotRegisters)
	{
		assert predecessor.hasStartedCodeGeneration;
		assert !hasStartedCodeGeneration;
		predecessors.add(predecessor);
	}

	/**
	 * Determine if this basic block has any predecessor basic blocks.
	 *
	 * @return The value {@code true} if this basic block has one or more
	 *         predecessor basic blocks, otherwise {@code false}.
	 */
	public boolean hasPredecessors ()
	{
		return !predecessors.isEmpty();
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
		instruction.justAdded(this);
		hasStartedCodeGeneration = true;
		hasControlFlowAtEnd = instruction.altersControlFlow();
		if (hasControlFlowAtEnd)
		{
			// Let the instruction/operation indicate which output registers are
			// written along which outbound branch, since some operations have
			// different output states depending on which branch is taken.
			assert slotsByTarget.length == 1;
			final L2ObjectRegister[] priorSlots = slotsByTarget[0];

			final List<L2BasicBlock> targetBlocks = instruction.targetBlocks();
			for (int i = 0; i < targetBlocks.size(); i++)
			{
				final L2BasicBlock successor = successors.get(i);
				final L2ObjectRegister[] adjustedSlots =
					instruction.operation.effectiveSlotsForBranch(
						i, priorSlots, slotRegisters);
				successors.add(successor);
				successor.addPredecessor(this, adjustedSlots);

			}


			// Let the instruction calculate the phi-function information.
			// Connect the successor blocks now.
			final List<L2ObjectRegister[]> slotLists = new ArrayList<>();
			for (final L2BasicBlock successor : instruction.targetBlocks())
			{
				***
			}
		}
		else
		{
			assert slotsByTarget == null
				|| (slotsByTarget.length == 1
				    && slotsByTarget[0].length == slotRegisters.length);
			slotsByTarget = new L2ObjectRegister[][] { slotRegisters.clone() };
		}
	}

	/**
	 * Create a new basic block.
	 */
	public L2BasicBlock (final String name)
	{
		this.name = name;
	}
}
