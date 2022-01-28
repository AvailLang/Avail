/*
 * L2ConditionalJump.kt
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
package avail.interpreter.levelTwo.operation

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor

/**
 * Jump to `"if satisfied"` if some condition is met, otherwise jump to
 * `"if unsatisfied"`.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Protect the constructor so the subclasses can maintain a fly-weight
 * pattern (or arguably a singleton).
 *
 * By convention, there are always 2 [targetEdges], the first of which is the
 * "taken" branch, and the second of which is the "not taken" branch.
 *
 * @param theNamedOperandTypes
 *   The vararg array of [L2NamedOperandType]s that describe the operands of
 *   such an instruction.
 */
abstract class L2ConditionalJump protected constructor(
		vararg theNamedOperandTypes: L2NamedOperandType)
	: L2ControlFlowOperation(*theNamedOperandTypes)
{
	override fun instructionWasAdded(
		instruction: L2Instruction, manifest: L2ValueManifest)
	{
		super.instructionWasAdded(instruction, manifest)
		targetEdges(instruction).forEach {
			it.installCounter()
		}
	}

	// It jumps, which counts as a side effect.
	override val hasSideEffect: Boolean get() = true

	companion object
	{
		/**
		 * Emit a conditional branch, including an increment for the counters
		 * along each path.
		 *
		 * @param translator
		 *   The [JVMTranslator] controlling code generation.
		 * @param method
		 *   The [MethodVisitor] on which to write the instructions.
		 * @param instruction
		 *   The [L2Instruction] causing this code generation.
		 * @param opcode
		 *   The Java bytecode to emit for the branch instruction.
		 * @param conditionHolds
		 *   Where to jump if the condition holds.
		 * @param conditionDoesNotHold
		 *   Where to jump if the condition does not hold.
		 */
		@JvmStatic
		protected fun emitBranch(
			translator: JVMTranslator,
			method: MethodVisitor,
			instruction: L2Instruction,
			opcode: Int,
			conditionHolds: L2PcOperand,
			conditionDoesNotHold: L2PcOperand)
		{
			translator.branch(
				method,
				instruction,
				opcode,
				conditionHolds,
				conditionDoesNotHold,
				conditionHolds.counter!!,
				conditionDoesNotHold.counter!!)
		}
	}
}
