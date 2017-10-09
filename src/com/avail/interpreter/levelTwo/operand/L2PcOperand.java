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
	 * The label instruction that this operand refers to.
	 */
	private final L2BasicBlock targetBlock;

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
	 * L2BasicBlock}.
	 *
	 * @param targetBlock
	 *        The {@link L2BasicBlock} The target basic block.
	 */
	public L2PcOperand (
		final L2BasicBlock targetBlock,
		final PhiRestriction... phiRestrictions)
	{
		this.targetBlock = targetBlock;
		this.phiRestrictions = phiRestrictions;
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.PC;
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
	 * Answer the target {@link L2BasicBlock} that this operand refers to.
	 *
	 * @return The target basic block.
	 */
	public L2BasicBlock targetBlock ()
	{
		return targetBlock;
	}

	@Override
	public String toString ()
	{
		// Show the basic block's name.
		return format("Pc(%s)",
			targetBlock.name());
	}
}
