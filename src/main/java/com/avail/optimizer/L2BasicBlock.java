/*
 * L2BasicBlock.java
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

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadOperand;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.optimizer.values.L2SemanticValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.avail.utility.Casts.cast;

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

	/** Whether this block is the head of a loop. */
	public boolean isLoopHead;

	/** Whether this block is part of a reification handler. */
	public boolean isInReificationHandler;

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
	 *
	 * @return The offset of the start of the block.
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
	@SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
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
	 * Answer the number of predecessor edges that this block has.
	 *
	 * @return The number of predecessors.
	 */
	public int predecessorEdgesCount ()
	{
		return predecessorEdges.size();
	}

	/**
	 * Answer an {@link Iterator} over the predecessor edges.  Don't change them
	 * during iteration.
	 *
	 * @return The iterator over predecessors.
	 */
	public Iterator<L2PcOperand> predecessorEdgesIterator ()
	{
		return predecessorEdges.iterator();
	}

	/**
	 * Answer a copy of the list of predecessor edges.
	 *
	 * @return The predecessor edges, copied to a new list.
	 */
	public List<L2PcOperand> predecessorEdgesCopy ()
	{
		return new ArrayList<>(predecessorEdges);
	}

	/**
	 * Answer the predecessor {@linkplain L2PcOperand edge} with the given index
	 * in my list of predecessors.
	 *
	 * @param index The index of the incoming edge.
	 * @return The indicated predecessor.
	 */
	public L2PcOperand predecessorEdgeAt (final int index)
	{
		return predecessorEdges.get(index);
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
		if (isLoopHead && hasStartedCodeGeneration)
		{
			final L2ValueManifest predecessorManifest =
				predecessorEdge.manifest();
			for (int i = 0; i < instructions.size(); i++)
			{
				final L2Instruction instruction = instructions.get(i);
				final L2Operation operation = instruction.operation();
				if (!operation.isPhi())
				{
					// All the phi instructions are at the start.
					break;
				}
				// The body of the loop is required to still have available
				// every semantic value mentioned in the original phis.
				final L2_PHI_PSEUDO_OPERATION<?, ?, ?> phiOperation =
					cast(instruction.operation());
				final L2SemanticValue semanticValue =
					phiOperation.sourceRegisterReads(instruction).get(0)
						.semanticValue();
				final L2ReadOperand<?> readOperand =
					predecessorManifest.readBoxed(semanticValue);
				phiOperation.withNewSource(
					instruction, cast(readOperand), predecessorManifest);
			}
		}
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
				if (!instruction.operation().isPhi())
				{
					// Phi functions are always at the start of a block.
					break;
				}
				final L2_PHI_PSEUDO_OPERATION<?, ?, ?> phiOperation =
					cast(instruction.operation());
				final L2Instruction replacement =
					phiOperation.withoutIndex(instruction, index);
				instructions.set(i, replacement);
			}
		}
		predecessorEdges.remove(predecessorEdge);
	}

	/**
	 * Answer the number of successor edges that this block has.
	 *
	 * @return The number of successors.
	 */
	public int successorEdgesCount ()
	{
		return successorEdges.size();
	}

	/**
	 * Answer an {@link Iterator} over the successor edges.  Don't change them
	 * during iteration.
	 *
	 * @return The iterator over successors.
	 */
	public Iterator<L2PcOperand> successorEdgesIterator ()
	{
		return successorEdges.iterator();
	}

	/**
	 * Answer a copy of the list of successor edges.
	 *
	 * @return The successor edges, copied to a new list.
	 */
	public List<L2PcOperand> successorEdgesCopy ()
	{
		return new ArrayList<>(successorEdges);
	}

	/**
	 * Answer the successor {@linkplain L2PcOperand edge} with the given index
	 * in my list of successors.
	 *
	 * @param index The index of the outgoing edge.
	 * @return The indicated successor.
	 */
	public L2PcOperand successorEdgeAt (final int index)
	{
		return successorEdges.get(index);
	}

	/**
	 * Add a successor edge.
	 *
	 * @param successorEdge
	 *        The {@link L2PcOperand} leaving this block.
	 */
	public void addSuccessorEdge (
		final L2PcOperand successorEdge)
	{
		successorEdges.add(successorEdge);
	}

	/**
	 * Remove a successor edge.
	 *
	 * @param successorEdge
	 *        The {@link L2PcOperand} that no longer leaves this block.
	 */
	public void removeSuccessorEdge (
		final L2PcOperand successorEdge)
	{
		final boolean success = successorEdges.remove(successorEdge);
		assert success;
	}

	/**
	 * Replace a successor edge with a replacement edge.
	 *
	 * @param oldSuccessorEdge
	 *        The {@link L2PcOperand} to remove.
	 * @param newSuccessorEdge
	 *        The {@link L2PcOperand} to add in its place.
	 */
	public void replaceSuccessorEdge (
		final L2PcOperand oldSuccessorEdge,
		final L2PcOperand newSuccessorEdge)
	{
		assert newSuccessorEdge.sourceBlock() == this;
		final int index = successorEdges.indexOf(oldSuccessorEdge);
		successorEdges.set(index, newSuccessorEdge);
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
	 * @param generator
	 *        The {@link L2Generator} generating instructions.
	 */
	void startIn (final L2Generator generator)
	{
		generator.currentManifest().clear();
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
		generator.currentManifest().populateFromIntersection(
			manifests, generator, isLoopHead);
	}

	/**
	 * Append an instruction to this basic block, notifying the operands that
	 * the instruction was just added.  Adding a phi instruction automatically
	 * places it at the start.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} to append.
	 * @param manifest
	 *        The {@link L2ValueManifest} that is active where this instruction
	 *        wos just added to its {@code L2BasicBlock}.
	 */
	public void addInstruction (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		assert isIrremovable() || predecessorEdgesCount() > 0;
		justAddInstruction(instruction);
		instruction.justAdded(manifest);
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
		if (instruction.operation().isPhi())
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
	 * @param index
	 *        The index at which to insert the instruction.
	 * @param instruction
	 *        The {@link L2Instruction} to insert.
	 * @param manifest
	 *        The {@link L2ValueManifest} that is active where this instruction
	 *        wos just added to its {@code L2BasicBlock}.
	 */
	public void insertInstruction (
		final int index,
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		assert isIrremovable() || predecessorEdgesCount() > 0;
		assert instruction.basicBlock == this;
		instructions.add(index, instruction);
		hasStartedCodeGeneration = true;
		instruction.justInserted(manifest);
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
	 * Create a new basic block, marking it as a loop head if requested.
	 *
	 * @param name
	 *        A descriptive name for the block.
	 * @param isLoopHead
	 *        Whether this block should be marked as the head of a loop.
	 * @param isInReificationHandler
	 *        Whether this block is part of a reification handler.
	 */
	public L2BasicBlock (
		final String name,
		final boolean isLoopHead,
		final boolean isInReificationHandler)
	{
		this.name = name;
		this.isLoopHead = isLoopHead;
		this.isInReificationHandler = isInReificationHandler;
	}

	/**
	 * Create a new basic block.
	 *
	 * @param name A descriptive name for the block.
	 */
	public L2BasicBlock (final String name)
	{
		this(name, false, false);
	}

	/**
	 * Add this block's instructions to the given instruction list.  Also do
	 * a special peephole optimization by removing any preceding {@link L2_JUMP}
	 * if its target is this block, <em>unless</em> this block is a loop head.
	 *
	 * @param output
	 *        The {@link List} of {@link L2Instruction}s in which to append this
	 *        basic block's instructions.
	 */
	void generateOn (final List<L2Instruction> output)
	{
		// If the preceding instruction was a jump to here, remove it.  In
		// fact, a null-jump might be on the end of the list, hiding another
		// jump just behind it that leads here, making that one also be a
		// null-jump.
		boolean changed;
		do
		{
			changed = false;
			if (!output.isEmpty())
			{
				final L2Instruction previousInstruction =
					output.get(output.size() - 1);
				if (previousInstruction.operation() == L2_JUMP.instance)
				{
					if (L2_JUMP.jumpTarget(previousInstruction).targetBlock()
						== this)
					{
						output.remove(output.size() - 1);
						changed = true;
					}
				}
			}
		}
		while (changed);

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
		return "BasicBlock(" + name + ')';
	}
}
