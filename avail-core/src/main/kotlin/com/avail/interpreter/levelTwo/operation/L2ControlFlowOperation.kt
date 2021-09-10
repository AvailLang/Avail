/*
 * L2ControlFlowOperation.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.interpreter.levelTwo.operation

import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.optimizer.L2BasicBlock

/**
 * An [L2Operation] that alters control flow, and therefore does not fall
 * through to the next instruction.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Protect the constructor so the subclasses can maintain a fly-weight
 * pattern (or arguably a singleton).
 *
 * @param theNamedOperandTypes
 *   The vararg array of [L2NamedOperandType]s that defines the layout of
 *   operands for [L2Instruction]s this use this operation.
 */
abstract class L2ControlFlowOperation protected constructor(
		vararg theNamedOperandTypes: L2NamedOperandType)
	: L2Operation(*theNamedOperandTypes)
{
	/**
	 * The array of operand indices which have type [L2PcOperand].
	 */
	private val labelOperandIndices: IntArray

	override fun altersControlFlow() = true

	/**
	 * Extract the operands which are [L2PcOperand]s.  These are what lead to
	 * other [L2BasicBlock]s.  They also carry an edge-specific array of slots,
	 * and edge-specific [TypeRestriction]s for registers.
	 *
	 * @param instruction
	 *   The [L2Instruction] to examine.
	 * @return
	 *   The [List] of target [L2PcOperand]s that are operands of the given
	 *   instruction.  These may be reachable directly via a control flow
	 *   change, or reachable only from some other mechanism like continuation
	 *   reification and later resumption of a continuation.
	 */
	override fun targetEdges(instruction: L2Instruction): List<L2PcOperand> =
		// Requires explicit parameter typing
		labelOperandIndices.map { instruction.operand<L2PcOperand>(it) }

	init
	{
		val labelIndicesList = namedOperandTypes.indices.filter {
			namedOperandTypes[it].operandType == L2OperandType.PC
		}
		labelOperandIndices =
			IntArray(labelIndicesList.size) { labelIndicesList[it] }
	}
}
