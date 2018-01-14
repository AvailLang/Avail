/**
 * L2PcOperand.java
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.values.L2SemanticValue;

import javax.annotation.Nullable;

import static com.avail.utility.Nulls.stripNull;
import static java.lang.String.format;
import static java.util.Arrays.asList;

/**
 * An {@code L2ConstantOperand} is an operand of type {@link L2OperandType#PC}.
 * It refers to the target {@link L2BasicBlock}, and may contain {@link
 * PhiRestriction}s that narrow register type information along this branch.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2PcOperand extends L2Operand
{
	/** The {@link L2BasicBlock} that this operand leads to. */
	private L2BasicBlock targetBlock;

	/** The instruction that this operand is part of. */
	private @Nullable L2Instruction instruction;

	/**
	 * The manifest linking semantic values and registers at this control flow
	 * edge.
	 */
	private final L2ValueManifest manifest;

	/**
	 * The collection of {@link PhiRestriction}s along this particular control
	 * flow transition.  An instruction that has multiple control flow
	 * transitions from it may produce different type restrictions along each
	 * branch, e.g., type narrowing from a type-testing branch.
	 *
	 * TODO MvG - This is not yet used.
	 */
	public final PhiRestriction[] phiRestrictions;

	/**
	 * Construct a new {@code L2PcOperand} with the specified {@link
	 * L2BasicBlock}, array of naive slot registers, and additional phi
	 * restrictions.  The array is copied before being captured.
	 *
	 * @param targetBlock
	 *        The {@link L2BasicBlock} The target basic block.
	 * @param manifest
	 *        The {@link L2ValueManifest} that records which {@link L2Register}s
	 *        hold which {@link L2SemanticValue}s.
	 * @param phiRestrictions
	 *        Additional register type and value restrictions to apply along
	 *        this control flow edge.
	 */
	public L2PcOperand (
		final L2BasicBlock targetBlock,
		final L2ValueManifest manifest,
		final PhiRestriction... phiRestrictions)
	{
		this.targetBlock = targetBlock;
		this.manifest = new L2ValueManifest(manifest);
		this.phiRestrictions = phiRestrictions.clone();
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.PC;
	}

	/**
	 * Answer the {@link L2ValueManifest} for this edge, which describes which
	 * {@link L2Register}s hold which {@link L2SemanticValue}s.
	 *
	 * @return This edge's {@link L2ValueManifest}.
	 */
	public L2ValueManifest manifest ()
	{
		return manifest;
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	@Override
	public void instructionWasAdded (
		final L2Instruction theInstruction)
	{
		assert this.instruction == null;
		this.instruction = theInstruction;
		instruction.basicBlock.successorEdges().add(this);
		targetBlock.addPredecessorEdge(this);
		super.instructionWasAdded(theInstruction);
	}

	@Override
	public void instructionWasRemoved (
		final L2Instruction theInstruction)
	{
		final L2BasicBlock sourceBlock = stripNull(instruction).basicBlock;
		sourceBlock.successorEdges().remove(this);
		targetBlock.removePredecessorEdge(this);
		this.instruction = null;
		sourceBlock.removedControlFlowInstruction();
		super.instructionWasRemoved(theInstruction);
	}

	/**
	 * Answer the target {@link L2BasicBlock} that this operand refers to.
	 *
	 * @return The target basic block.
	 */
	public L2BasicBlock targetBlock ()
	{
		return targetBlock;
	}

	/**
	 * Answer the source {@link L2BasicBlock} that this operand is an edge from.
	 *
	 * @return The source basic block.
	 */
	public L2BasicBlock sourceBlock ()
	{
		return stripNull(instruction).basicBlock;
	}

	@Override
	public String toString ()
	{
		// Show the basic block's name.
		return format("Pc(%d: %s)",
			targetBlock.offset(),
			targetBlock.name());
	}

	/**
	 * Create a new {@link L2BasicBlock} that will be the new target of this
	 * edge, and write an {@link L2_JUMP} into the new block to jump to the
	 * old target of this edge.  Be careful to maintain predecessor order at the
	 * target block.
	 *
	 * @param controlFlowGraph
	 *        The {@link L2ControlFlowGraph} being updated.
	 * @return The new {@link L2BasicBlock} that splits the given edge.  This
	 *         block has not yet been added to the controlFlowGraph, and the
	 *         client should do this to keep the graph consistent.
	 */
	public L2BasicBlock splitEdgeWith (
		final L2ControlFlowGraph controlFlowGraph)
	{
		assert instruction != null;

		// Capture where this edge originated.
		final L2Instruction originalSource = instruction;

		// Create a new intermediary block.
		final L2BasicBlock newBlock = new L2BasicBlock(
			"edge-split ["
				+ originalSource.basicBlock.name()
				+ " / "
				+ targetBlock.name()
				+ "]");
		controlFlowGraph.startBlock(newBlock);

		// Create a jump instruction, reusing this edge.  That way we don't have
		// to do anything to the target block where the phi stuff is.  Currently
		// the L2Instruction constructor copies the operands for safety, and
		// also establishes bi-directional links through L2PcOperands.  We
		// bypass that after construction.
		final L2BasicBlock garbageBlock = new L2BasicBlock("garbage block");
		final L2Instruction jump = new L2Instruction(
			newBlock,
			L2_JUMP.instance,
			new L2PcOperand(garbageBlock, manifest, phiRestrictions));
		jump.operands[0] = this;
		instruction = jump;
		newBlock.justAddInstruction(jump);
		assert newBlock.successorEdges().isEmpty();
		newBlock.successorEdges().add(this);

		// Create a new edge from the original source to the new block.
		final L2PcOperand newEdge = new L2PcOperand(
			newBlock, manifest, phiRestrictions);
		newEdge.instruction = originalSource;
		newBlock.predecessorEdges().add(newEdge);

		// Wire in the new edge.
		asList(originalSource.operands).replaceAll(
			x -> x == this ? newEdge : x);
		originalSource.basicBlock.successorEdges().replaceAll(
			x -> x == this ? newEdge : x);

		return newBlock;
	}

	/**
	 * In a non-SSA control flow graph that has had its phi functions removed
	 * and converted to moves, switch the target of this edge.
	 *
	 * @param newTarget The new target {@link L2BasicBlock} of this edge.
	 */
	public void switchTargetBlockNonSSA (final L2BasicBlock newTarget)
	{
		final L2BasicBlock oldTarget = targetBlock;
		targetBlock = newTarget;
		oldTarget.predecessorEdges().remove(this);
		newTarget.predecessorEdges().add(this);
	}
}
