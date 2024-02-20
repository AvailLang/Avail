/*
 * L2_JUMP_BACK.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED_VECTOR
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticValue
import org.objectweb.asm.MethodVisitor

/**
 * Unconditionally jump to the level two offset in my [L2PcOperand], while also
 * limiting that edge's [L2ValueManifest] to the [L2ReadVectorOperand]'s reads.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_JUMP_BACK : L2ControlFlowOperation(
	PC.named("target", SUCCESS),
	READ_BOXED_VECTOR.named("registers to keep"))
{
	// It jumps, which counts as a side effect.
	override val hasSideEffect get() = true

	override val isUnconditionalJump get() = true

	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		val target = instruction.operand<L2PcOperand>(0)
		val preservedReads =
			instruction.operand<L2ReadBoxedVectorOperand>(1)

		// Play the reads against the old manifest, which is then filtered.
		preservedReads.instructionWasAdded(manifest)
		val semanticValuesToKeep = mutableSetOf<L2SemanticValue<*>>()
		val registersToKeep = mutableSetOf<L2Register<*>>()
		preservedReads.elements.forEach {
			semanticValuesToKeep.add(it.semanticValue())
			registersToKeep.add(it.register())
		}
		manifest.retainSemanticValues(semanticValuesToKeep)
		manifest.retainRegisters(registersToKeep)
		target.instructionWasAdded(manifest)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val target = instruction.operand<L2PcOperand>(0)

		// :: goto offset;
		translator.jump(method, instruction, target)
	}

	/**
	 * Extract the target of the given jump-back instruction.
	 *
	 * @param instruction
	 *   The [L2Instruction] to examine.  Its [L2Operation] must be an
	 *   `L2_JUMP_BACK`.
	 * @return
	 *   The [L2PcOperand] to which the instruction jumps.
	 */
	@JvmStatic
	fun jumpTarget(instruction: L2Instruction): L2PcOperand
	{
		assert(instruction.operation === this)
		return instruction.operand(0)
	}
}
