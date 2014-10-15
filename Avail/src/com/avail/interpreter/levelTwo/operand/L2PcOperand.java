/**
 * L2PcOperand.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operation.L2_LABEL;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.utility.evaluation.*;

/**
 * An {@code L2ConstantOperand} is an operand of type {@link L2OperandType#PC}.
 * It also holds the {@link L2_LABEL} that is the target instruction to which
 * this operand refers.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2PcOperand extends L2Operand
{
	/**
	 * The label instruction that this operand refers to.
	 */
	private final L2Instruction label;

	/**
	 * Construct a new {@link L2PcOperand} with the specified {@link
	 * L2Instruction}, which should be a {@link L2_LABEL label}.
	 *
	 * @param label The target label.
	 */
	public L2PcOperand (
		final L2Instruction label)
	{
		assert label.operation == L2_LABEL.instance;
		this.label = label;
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
		final Transformer2<L2Register, L2OperandType, L2Register> transformer)
	{
		return this;
	}

	/**
	 * Answer the target label that this operand refers to.  It must be an
	 * {@link L2Instruction} whose operation is {@link L2_LABEL}.
	 *
	 * @return The target label instruction.
	 */
	public L2Instruction targetLabel ()
	{
		return label;
	}

	@Override
	public String toString ()
	{
		// Extract the comment from the target label.
		final L2CommentOperand commentOperand =
			(L2CommentOperand)label.operands[0];
		return String.format("Pc(%s=%d)",
			commentOperand.comment,
			label.offset());
	}
}
