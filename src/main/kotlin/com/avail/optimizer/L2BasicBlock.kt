/*
 * L2BasicBlock.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import com.avail.interpreter.levelTwo.operation.L2_JUMP
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import com.avail.optimizer.reoptimizer.L2Regenerator
import com.avail.utility.cast
import java.lang.Integer.toHexString

/**
 * This is a traditional basic block, consisting of a sequence of
 * [L2Instruction]s.  It has no incoming jumps except to the start, and has no
 * outgoing jumps or branches except from the last instruction.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property name
 *   A descriptive name for the block.
 * @property isLoopHead
 *   Whether this block is the head of a loop. Default is `false`.
 * @property zone
 *   The block's optional [L2ControlFlowGraph.Zone], a mechanism to visually
 *   group blocks in the [L2ControlFlowGraphVisualizer].
 *
 * @constructor
 * Create a new basic block, marking it as a loop head if requested.
 *
 * @param name
 *   A descriptive name for the block.
 * @param isLoopHead
 *   Whether this block is the head of a loop. Default is `false`.
 * @param zone
 *   A mechanism to visually group blocks in the
 *   [L2ControlFlowGraphVisualizer], indicating the purpose of that group.
 */
class L2BasicBlock @JvmOverloads constructor(
	private val name: String,
	var isLoopHead: Boolean = false,
	var zone: L2ControlFlowGraph.Zone? = null)
{
	/** The sequence of instructions within this basic block.  */
	private val instructions = mutableListOf<L2Instruction>()

	/**
	 * The [L2PcOperand]s that point to basic blocks that follow this one, taken
	 * in order from the last instruction.  This is kept synchronized with the
	 * predecessor lists of the successor blocks.
	 */
	private val successorEdges = mutableListOf<L2PcOperand>()

	/**
	 * The [L2PcOperand]s that point to this basic block.  They capture their
	 * containing [L2Instruction], which knows its own basic block, so we can
	 * easily get to the originating basic block.
	 */
	private val predecessorEdges = mutableListOf<L2PcOperand>()

	/**
	 * The L2 offset at which the block starts.  Only populated after code
	 * generation has completed.
	 */
	private var offset = -1

	/**
	 * Whether this block must be tracked until final code generation. Set for
	 * blocks that must not be removed. Such a block may be referenced for
	 * tracking entry points, and must therefore exist through final code
	 * generation.
	 */
	var isIrremovable = false
		private set

	/** Whether we've started adding instructions to this basic block.  */
	private var hasStartedCodeGeneration = false

	/**
	 * Keeps track whether a control-flow altering instruction has been added
	 * yet.  There must be one, and it must be the last instruction in the
	 * block.
	 */
	private var hasControlFlowAtEnd = false

	/**
	 * Answer the descriptive name of this basic block.
	 *
	 * @return
	 *   The basic block's name.
	 */
	fun name(): String = name

	/**
	 * Prevent this block from being removed, so that its position can be
	 * identified in the final generated code.
	 */
	fun makeIrremovable()
	{
		isIrremovable = true
	}

	/**
	 * Answer the L2 offset at which the block starts.  Only populated after
	 * code generation has completed.
	 *
	 * @return
	 *   The offset of the start of the block.
	 */
	fun offset(): Int = offset

	/**
	 * Answer this block's [List] of [L2Instruction]. They consist of a sequence
	 * of non-branching instructions, ending in an instruction that branches to
	 * zero or more targets via its [L2PcOperand]s.
	 *
	 * @return
	 *   The block's [List] of [L2Instruction]s.
	 */
	fun instructions(): MutableList<L2Instruction> = instructions

	/**
	 * Answer this block's last [L2Instruction]. This instruction must be a
	 * branching instruction of some sort, having zero or more target edges, but
	 * not falling through to the next instruction (which doesn't exist).
	 *
	 * @return
	 *   The last [L2Instruction] of this block.
	 */
	fun finalInstruction(): L2Instruction = instructions[instructions.size - 1]

	/**
	 * Answer a read-only [List] of predecessor edges of this block.
	 *
	 * @return
	 *   The predecessors.
	 */
	fun predecessorEdges(): List<L2PcOperand> = predecessorEdges

	/**
	 * Add a predecessor, due to an earlier basic block adding an instruction
	 * that reaches this basic block.  Also allow backward edges to attach to
	 * a loop head after code generation has already taken place in the target.
	 *
	 * @param predecessorEdge
	 *   The [L2PcOperand] that leads here.
	 */
	fun addPredecessorEdge(predecessorEdge: L2PcOperand)
	{
		assert(predecessorEdge.sourceBlock().hasStartedCodeGeneration)
		predecessorEdges.add(predecessorEdge)
		if (hasStartedCodeGeneration)
		{
			val predecessorManifest = predecessorEdge.manifest()
			for (i in instructions.indices)
			{
				val instruction = instructions[i]
				val operation = instruction.operation()
				if (!operation.isPhi)
				{
					// All the phi instructions are at the start, so we've
					// exhausted them.
					break
				}
				val phiOperation: L2_PHI_PSEUDO_OPERATION<*, *, *, *> =
					operation.cast()

				// The body of the loop is required to still have available
				// every semantic value mentioned in the original phis.
				phiOperation.updateLoopHeadPhi(predecessorManifest, instruction)
			}
		}
	}

	/**
	 * Remove a predecessor, perhaps due to a branch to it becoming unreachable.
	 *
	 * @param predecessorEdge
	 *   The [L2PcOperand] that no longer leads here.
	 */
	fun removePredecessorEdge(predecessorEdge: L2PcOperand)
	{
		assert(predecessorEdge.sourceBlock().hasStartedCodeGeneration)
		if (hasStartedCodeGeneration)
		{
			val index = predecessorEdges.indexOf(predecessorEdge)
			predecessorEdges.removeAt(index)
			for (i in instructions.indices)
			{
				val instruction = instructions[i]
				if (!instruction.operation().isPhi)
				{
					// Phi functions are always at the start of a block.
					break
				}
				val phiOperation: L2_PHI_PSEUDO_OPERATION<*, *, *, *> =
					instruction.operation().cast()
				val replacement = phiOperation.withoutIndex(instruction, index)
				instruction.justRemoved()
				instructions[i] = replacement
				replacement.justInserted()
			}
		}
		predecessorEdges.remove(predecessorEdge)
	}

	/**
	 * Answer the non-modifiable [List] of successor edges that this block has.
	 * The underlying [MutableList] might be changed by other operations, so be
	 * aware that it's not truly immutable, just an immutable view.
	 *
	 * @return
	 *   The successors, as a non-modifiable list.
	 */
	fun successorEdges(): List<L2PcOperand> = successorEdges

	/**
	 * Add a successor edge.
	 *
	 * @param successorEdge
	 *   The [L2PcOperand] leaving this block.
	 */
	fun addSuccessorEdge(successorEdge: L2PcOperand)
	{
		successorEdges.add(successorEdge)
	}

	/**
	 * Remove a successor edge.
	 *
	 * @param successorEdge
	 *   The [L2PcOperand] that no longer leaves this block.
	 */
	fun removeSuccessorEdge(successorEdge: L2PcOperand)
	{
		val success = successorEdges.remove(successorEdge)
		assert(success)
	}

	/**
	 * Having completed code generation in each of its predecessors, prepare
	 * this block for its own code generation.
	 *
	 * Examine the [predecessorEdges] to determine if phi operations need to be
	 * created to merge differences in the slot-to-register arrays. These will
	 * eventually turn into move instructions along the incoming edges, which
	 * will usually end up with the same register color on both ends of the
	 * move, allowing it to be eliminated.
	 *
	 * If phi operations are added, their target registers will be written to
	 * the appropriate slotRegisters of the provided [L1Translator].
	 *
	 * @param generator
	 *   The [L2Generator] generating instructions.
	 * @param generatePhis
	 *   Whether to automatically generate phi instructions if there are
	 *   multiple incoming edges with different registers associated with the
	 *   same semantic values.
	 * @param regenerator
	 *   The optional [L2Regenerator] to use.
	 */
	fun startIn(
		generator: L2Generator,
		generatePhis: Boolean = true,
		regenerator: L2Regenerator? = null)
	{
		generator.currentManifest().clear()
		if (isIrremovable)
		{
			// Irremovable blocks are entry points, and don't require any
			// registers to be defined yet, so ignore any registers that appear
			// to be defined (they're really not).
			return
		}
		// Keep semantic values that are common to all incoming paths.  Create
		// phi functions if the registers disagree.
		generator.currentManifest().populateFromIntersection(
			predecessorEdges.map(L2PcOperand::manifest),
			generator,
			generatePhis,
			isLoopHead,
			regenerator)
	}

	/**
	 * Append an instruction to this basic block, notifying the operands that
	 * the instruction was just added.  Adding a phi instruction automatically
	 * places it at the start.
	 *
	 * @param instruction
	 *   The [L2Instruction] to append.
	 * @param manifest
	 *   The [L2ValueManifest] that is active where this instruction was just
	 *   added to its `L2BasicBlock`.
	 */
	fun addInstruction(instruction: L2Instruction, manifest: L2ValueManifest)
	{
		assert(isIrremovable || predecessorEdges().isNotEmpty())
		justAddInstruction(instruction)
		instruction.justAdded(manifest)
	}

	/**
	 * Append an instruction to this basic block, without telling the operands
	 * that the instruction was just added.  Adding a phi instruction
	 * automatically places it at the start.
	 *
	 * @param instruction
	 *   The [L2Instruction] to append.
	 */
	private fun justAddInstruction(instruction: L2Instruction)
	{
		assert(!hasControlFlowAtEnd)
		assert(instruction.basicBlock() == this)
		if (instruction.operation().isPhi)
		{
			// For simplicity, phi functions are routed to the *start* of the
			// block.
			instructions.add(0, instruction)
		}
		else
		{
			instructions.add(instruction)
		}
		hasStartedCodeGeneration = true
		hasControlFlowAtEnd = instruction.altersControlFlow
	}

	/**
	 * Answer the zero-based index of the first index beyond any
	 * [L2_ENTER_L2_CHUNK] or [L2_PHI_PSEUDO_OPERATION]s.  It might be just past
	 * the last valid index (i.e., equal to the size).
	 *
	 * @return
	 *   The index of the first instruction that isn't an entry point or phi.
	 */
	fun indexAfterEntryPointAndPhis(): Int
	{
		instructions.forEachIndexed { i, instruction ->
			if (!instruction.isEntryPoint && !instruction.operation().isPhi)
			{
				return i
			}
		}
		return instructions.size - 1
	}

	/**
	 * Insert an instruction in this basic block at the specified instruction
	 * index, notifying the operands that the instruction was just added.
	 *
	 * @param index
	 *   The index at which to insert the instruction.
	 * @param instruction
	 *   The [L2Instruction] to insert.
	 */
	fun insertInstruction(index: Int, instruction: L2Instruction)
	{
		assert(instruction.basicBlock() == this)
		instructions.add(index, instruction)
		hasStartedCodeGeneration = true
		instruction.justInserted()
	}

	/**
	 * One of my predecessors has been replaced.  Update my list of predecessors
	 * to account for this change.
	 *
	 * @param oldPredecessorEdge
	 *   The [L2PcOperand] that used to point here.
	 * @param newPredecessorEdge
	 *   The [L2PcOperand] that points here instead.
	 */
	fun replacePredecessorEdge(
		oldPredecessorEdge: L2PcOperand,
		newPredecessorEdge: L2PcOperand)
	{
		predecessorEdges[predecessorEdges.indexOf(oldPredecessorEdge)] =
			newPredecessorEdge
	}

	/**
	 * The final instruction of the block altered control flow, and it has just
	 * been removed.  Subsequent code generation may take place in this block.
	 */
	fun removedControlFlowInstruction()
	{
		hasControlFlowAtEnd = false
	}

	/**
	 * The final instruction of the block altered control flow, it was removed
	 * and later re-added to the instructions list directly.
	 */
	fun readdedControlFlowInstruction()
	{
		hasControlFlowAtEnd = true
	}

	/**
	 * Determine whether code added after the last instruction of this block
	 * would be reachable.  Take into account whether the block itself seems to
	 * be reachable.
	 *
	 * @return
	 *   Whether it would be possible to reach a new instruction added to this
	 *   block.
	 */
	fun currentlyReachable(): Boolean =
		((isIrremovable || predecessorEdges.isNotEmpty())
		 	&& !hasControlFlowAtEnd)

	/**
	 * Add this block's instructions to the given instruction list.  Also do a
	 * special peephole optimization by removing any preceding [L2_JUMP] if its
	 * target is this block, *unless* this block is a loop head.
	 *
	 * @param output
	 *   The [List] of [L2Instruction]s in which to append this basic block's
	 *   instructions.
	 */
	fun generateOn(output: MutableList<L2Instruction>)
	{
		// If the preceding instruction was a jump to here, remove it.  In
		// fact, a null-jump might be on the end of the list, hiding another
		// jump just behind it that leads here, making that one also be a
		// null-jump.
		var changed: Boolean
		do
		{
			changed = false
			if (output.isNotEmpty())
			{
				val previousInstruction = output[output.size - 1]
				if (previousInstruction.operation() === L2_JUMP)
				{
					if (L2_JUMP.jumpTarget(previousInstruction).targetBlock()
						== this)
					{
						output.removeAt(output.size - 1)
						changed = true
					}
				}
			}
		}
		while (changed)
		var counter = output.size
		offset = counter
		for (instruction in instructions)
		{
			if (instruction.shouldEmit)
			{
				instruction.setOffset(counter++)
				output.add(instruction)
			}
		}
	}

	override fun toString(): String
	{
		var suffix = ""
		if (instructions.size > 0)
		{
			val firstOffset = instructions[0].offset()
			val lastOffset = instructions.last().offset()
			if (firstOffset != -1 && lastOffset != -1)
			{
				suffix = " [$firstOffset..$lastOffset]"
			}
		}
		val hex = toHexString(hashCode())
		return "BasicBlock($name)$suffix [$hex]"
	}
}
