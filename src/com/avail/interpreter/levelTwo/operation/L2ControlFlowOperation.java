/*
 * L2ControlFlowOperation.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2NamedOperandType;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link L2Operation} that alters control flow, and therefore does not fall
 * through to the next instruction.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class L2ControlFlowOperation extends L2Operation
{
	/**
	 * The array of operand indices which have type {@link L2PcOperand}.
	 */
	private final int[] labelOperandIndices;

	/**
	 * Protect the constructor so the subclasses can maintain a fly-weight
	 * pattern (or arguably a singleton).
	 */
	protected L2ControlFlowOperation (
		final L2NamedOperandType... theNamedOperandTypes)
	{
		super(theNamedOperandTypes);
		final List<Integer> labelIndicesList = new ArrayList<>(2);
		for (int index = 0; index < namedOperandTypes.length; index++)
		{
			final L2NamedOperandType namedOperandType =
				namedOperandTypes[index];
			if (namedOperandType.operandType() == L2OperandType.PC)
			{
				labelIndicesList.add(index);
			}
		}
		labelOperandIndices =
			labelIndicesList.stream().mapToInt(Integer::intValue).toArray();
	}

	@Override
	public final boolean altersControlFlow ()
	{
		return true;
	}

	/**
	 * Propagate type, value, alias, and source instruction information due to
	 * the execution of this instruction.  The instruction must not have
	 * multiple possible successor instructions.
	 *
	 * <p>Present here only to avoid subclasses from implementing this form.</p>
	 *
	 * @param instruction
	 *        The L2Instruction containing this L2Operation.
	 * @param registerSet
	 *        A RegisterSet to advance to a state corresponding with after
	 *        having run the given instruction.
	 * @param generator
	 *        The {@link L2Generator} for which to advance the type analysis.
	 * @see #propagateTypes(L2Instruction, List, L2Generator)
	 */
	@Override
	protected final void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		throw new UnsupportedOperationException(
			"Single-target propagateTypes is not applicable to an "
				+ "L2ControlFlowOperation");
	}

	/**
	 * Extract the operands which are {@link L2PcOperand}s.  These are what lead
	 * to other {@link L2BasicBlock}s.  They also carry an edge-specific array
	 * of slots, and edge-specific {@link TypeRestriction}s for registers.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} to examine.
	 * @return The {@link List} of target {@link L2PcOperand}s that are operands
	 *         of the given instruction.  These may be reachable directly via a
	 *         control flow change, or reachable only from some other mechanism
	 *         like continuation reification and later resumption of a
	 *         continuation.
	 */
	@Override
	public final List<L2PcOperand> targetEdges (final L2Instruction instruction)
	{
		final List<L2PcOperand> edges =
			new ArrayList<>(labelOperandIndices.length);
		for (final int labelOperandIndex : labelOperandIndices)
		{
			edges.add(instruction.pcAt(labelOperandIndex));
		}
		return edges;
	}
}
