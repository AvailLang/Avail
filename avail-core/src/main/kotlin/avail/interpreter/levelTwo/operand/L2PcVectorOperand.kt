/*
 * L2PcVectorOperand.kt
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
package avail.interpreter.levelTwo.operand

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2ValueManifest
import avail.utility.cast

/**
 * An [L2PcVectorOperand] is an operand of type [L2OperandType.PC_VECTOR]. It
 * holds a [List] of [L2PcOperand]s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new [L2PcVectorOperand] with the specified [List] of
 * [L2PcOperand]s. The order of the elements should be understood by the
 * [L2Operation] of the [L2Instruction] in which this vector occurs.
 *
 * @param edges
 *   The list of [L2PcOperand]s.
 */
class L2PcVectorOperand constructor(
	val edges: List<L2PcOperand>)
: L2Operand()
{
	// Clone each edge.
	override fun clone(): L2PcVectorOperand =
		L2PcVectorOperand(edges.map { it.clone().cast() })

	override fun adjustCloneForInstruction(theInstruction: L2Instruction)
	{
		super.adjustCloneForInstruction(theInstruction)
		edges.forEach { it.adjustCloneForInstruction(theInstruction) }
	}

	override val operandType: L2OperandType
		get() = L2OperandType.PC_VECTOR

	override fun setInstruction(theInstruction: L2Instruction?)
	{
		super.setInstruction(theInstruction)
		// Also update the instruction fields of its L2PcOperands.
		edges.forEach { it.setInstruction(theInstruction) }
	}

	override fun dispatchOperand(dispatcher: L2OperandDispatcher)
	{
		dispatcher.doOperand(this)
	}

	override fun instructionWasAdded(manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		edges.forEach { it.instructionWasAdded(manifest) }
	}

	override fun instructionWasInserted(
		newInstruction: L2Instruction)
	{
		super.instructionWasInserted(newInstruction)
		edges.forEach { it.instructionWasInserted(newInstruction) }
	}

	override fun instructionWasRemoved()
	{
		edges.forEach { it.instructionWasRemoved() }
		super.instructionWasRemoved()
	}

	override fun replaceRegisters(
		registerRemap: Map<L2Register, L2Register>,
		theInstruction: L2Instruction)
	{
		edges.forEach { it.replaceRegisters(registerRemap, theInstruction) }
		super.replaceRegisters(registerRemap, theInstruction)
	}

	override fun appendTo(builder: StringBuilder): Unit = with(builder)
	{
		append("→ <")
		edges.forEachIndexed { zeroIndex, edge ->
			append("\n\t(#").append(zeroIndex + 1).append("): ")
			edge.appendTo(builder)
		}
		append("\n>")
	}

	override fun postOptimizationCleanup(): Unit =
		edges.forEach(L2PcOperand::postOptimizationCleanup)
}
