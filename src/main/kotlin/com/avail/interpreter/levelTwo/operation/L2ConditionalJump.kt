/*
 * L2ConditionalJump.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.utility.Nulls
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

	/**
	 * An `enum` indicating whether the decision whether to branch or not can be
	 * reduced to a static decision.
	 */
	enum class BranchReduction
	{
		/** The branch will always be taken.  */
		AlwaysTaken,

		/** The branch will never be taken.  */
		NeverTaken,

		/** It could not be determined if the branch will be taken or not.  */
		SometimesTaken
	}

	/**
	 * Determine if the branch can be eliminated.
	 *
	 * @param instruction
	 *   The [L2Instruction] being examined.
	 * @param registerSet
	 *   The [RegisterSet] at the current code position.
	 * @param generator
	 *   The [L2Generator] in which code (re)generation is taking place.
	 * @return
	 *   A [BranchReduction] indicating whether the branch direction can be
	 *   statically decided.
	 */
	open fun branchReduction(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator): BranchReduction
	{
		return BranchReduction.SometimesTaken
	}

	override fun hasSideEffect(): Boolean
	{
		// It jumps, which counts as a side effect.
		return true
	}

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
				Nulls.stripNull(conditionHolds.counter),
				Nulls.stripNull(conditionDoesNotHold.counter))
		}
	}
}