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
import com.avail.interpreter.levelTwo.register.RegisterTransformer;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;

import javax.annotation.Nullable;

import static com.avail.utility.Nulls.stripNull;
import static java.lang.String.format;

/**
 * An {@code L2ConstantOperand} is an operand of type {@link L2OperandType#PC}.
 * It refers to the target {@link L2BasicBlock}, and may contain {@link
 * PhiRestriction}s that narrow register type information along this branch.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2PcOperand extends L2Operand
{
	/**
	 * The {@link L2BasicBlock} that this operand refers to.
	 */
	private L2BasicBlock targetBlock;

	/** The instruction that this operand is part of. */
	private @Nullable L2Instruction instruction;

	/**
	 * An array of {@link L2ReadPointerOperand}s, representing the slots of the
	 * virtual continuation when following this control flow edge.
	 */
	private final L2ReadPointerOperand[] slotRegisters;

	/**
	 * The collection of {@link PhiRestriction}s along this particular control
	 * flow transition.  An instruction that has multiple control flow
	 * transitions from it may produce different type restrictions along each
	 * branch, e.g., type narrowing from a type-testing branch.
	 */
	private final PhiRestriction[] phiRestrictions;

	/**
	 * Answer the array of {@link PhiRestriction}s along this branch.
	 *
	 * @return The array of {@link PhiRestriction}s.
	 */
	public PhiRestriction[] phiRestrictions ()
	{
		return phiRestrictions;
	}

	/**
	 * Construct a new {@code L2PcOperand} with the specified {@link
	 * L2BasicBlock}, array of naive slot registers, and additional phi
	 * restrictions.  The array is copied before being captured.
	 *
	 * @param targetBlock
	 *        The {@link L2BasicBlock} The target basic block.
	 * @param slotRegisters
	 *        The array of {@link L2ReadPointerOperand}s that hold the virtual
	 *        continuation's state when following this control flow edge.
	 * @param phiRestrictions
	 *        Additional register type and value restrictions to apply along
	 *        this control flow edge.
	 */
	public L2PcOperand (
		final L2BasicBlock targetBlock,
		final L2ReadPointerOperand[] slotRegisters,
		final PhiRestriction... phiRestrictions)
	{
		this.targetBlock = targetBlock;
		this.slotRegisters = slotRegisters.clone();
		this.phiRestrictions = phiRestrictions;
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.PC;
	}

	/**
	 * Answer the captured array of {@link L2ReadPointerOperand}s which
	 * correspond to the virtual continuation's state when traversing this
	 * control flow graph edge.
	 *
	 * <p>Do not modify this array.</p>
	 *
	 * @return An array of {@link L2ReadPointerOperand}s.
	 */
	public L2ReadPointerOperand[] slotRegisters ()
	{
		return slotRegisters;
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	@Override
	public L2PcOperand transformRegisters (
		final RegisterTransformer<L2OperandType> transformer)
	{
		return this;
	}

	@Override
	public void instructionWasAdded (
		final L2Instruction theInstruction)
	{
		assert this.instruction == null;
		this.instruction = theInstruction;
		instruction.basicBlock.successorEdges().add(this);
		targetBlock.predecessorEdges().add(this);
		super.instructionWasAdded(theInstruction);
	}

	@Override
	public void instructionWasRemoved (
		final L2Instruction theInstruction)
	{
		stripNull(instruction).basicBlock.successorEdges().remove(this);
		targetBlock.predecessorEdges().remove(this);
		this.instruction = null;
		super.instructionWasAdded(theInstruction);
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
	 */
	public void splitEdgeWith (final L2ControlFlowGraph controlFlowGraph)
	{
		assert instruction != null;
		final L2BasicBlock newBlock = controlFlowGraph.createBasicBlock(
			"edge-split");
		// Add a jump that loops to itself.  We'll update the jump's target and
		// the original edge's target to make things right, while being careful
		// not to disturb the predecessor order that's correlated with any phi
		// functions.
		newBlock.addInstruction(
			new L2Instruction(
				newBlock,
				L2_JUMP.instance,
				new L2PcOperand(
					newBlock,
					slotRegisters(),
					phiRestrictions())));
		// Copying happens when adding an instruction, but we're interested in
		// the exact edge that was added.
		final L2Instruction jump = newBlock.instructions().get(0);
		final L2PcOperand newEdge = L2_JUMP.jumpTarget(jump);
		// Adjust the newEdge's target.
		newEdge.targetBlock = targetBlock;
		// Replace the edge in the targetBlock's predecessors list.
		final int index = targetBlock.predecessorEdges().indexOf(this);
		targetBlock.predecessorEdges().set(index, newEdge);

		// Now redirect the original edge.
		targetBlock = newBlock;
		newBlock.predecessorEdges().clear();
		newBlock.predecessorEdges().add(this);
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
