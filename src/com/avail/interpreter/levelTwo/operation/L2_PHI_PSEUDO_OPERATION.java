/**
 * L2_PHI_PSEUDO_OPERATION.java
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_VECTOR;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;

/**
 * The {@code L2_PHI_PSEUDO_OPERATION} occurs at the start of a {@link
 * L2BasicBlock}.  It's a convenient fiction that allows an {@link
 * L2ControlFlowGraph} to be in Static Single Assignment form (SSA), where each
 * {@link L2Register} has exactly one instruction that writes to it.
 *
 * <p>The vector of source registers are in the same order as the corresponding
 * predecessors of the containing {@link L2BasicBlock}.  The runtime effect
 * would be to select from that vector, based on the predecessor from which
 * control arrives, and move that register's value to the destination register.
 * However, that's a fiction, and the phi operation is instead removed during
 * the transition of the control flow graph out of SSA, being replaced by move
 * instructions along each incoming edge.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_PHI_PSEUDO_OPERATION extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_PHI_PSEUDO_OPERATION().init(
			READ_VECTOR.is("potential sources"),
			WRITE_POINTER.is("destination"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		throw new UnsupportedOperationException(
			"This instruction should be factored out before execution");
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final List<L2ReadPointerOperand> inputRegs =
			instruction.readVectorRegisterAt(0);
		final L2WritePointerOperand destinationReg =
			instruction.writeObjectRegisterAt(1);

		@SuppressWarnings("ConstantConditions")
		final TypeRestriction restriction = inputRegs.stream()
			.map(L2ReadPointerOperand::restriction)
			.reduce(TypeRestriction::union)
			.get();
		registerSet.removeConstantAt(destinationReg.register());
		registerSet.removeTypeAt(destinationReg.register());
		registerSet.typeAtPut(
			destinationReg.register(), restriction.type, instruction);
		if (restriction.constantOrNull != null)
		{
			registerSet.constantAtPut(
				destinationReg.register(),
				restriction.constantOrNull,
				instruction);
		}
	}
}
