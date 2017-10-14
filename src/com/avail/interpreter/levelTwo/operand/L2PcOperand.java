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
import com.avail.interpreter.levelTwo.register.RegisterTransformer;
import com.avail.optimizer.L2BasicBlock;

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
	private final L2BasicBlock targetBlock;

	/** The instruction that this operand is part of. */
	private L2Instruction instruction;

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
	public PhiRestriction[] getPhiRestrictions ()
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

	/**
	 * This is an operand of the given instruction, which was just added to its
	 * basic block.
	 *
	 * @param theInstruction
	 *        The {@link L2Instruction} that was just added.
	 */
	@Override
	public void instructionWasAdded (
		final L2Instruction theInstruction)
	{
		// Capture the containing instruction.
		this.instruction = theInstruction;
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
		return instruction.basicBlock;
	}

	@Override
	public String toString ()
	{
		// Show the basic block's name.
		return format("Pc(%s)",
			targetBlock.name());
	}
}
