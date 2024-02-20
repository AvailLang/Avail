/*
 * L2_STRIP_MANIFEST.kt
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
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED_VECTOR
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticValue
import avail.utility.mapToSet
import org.objectweb.asm.MethodVisitor

/**
 * This is a helper operation which produces no JVM code, but is useful to limit
 * which [L2Register]s and [L2SemanticValue]s are live when reaching
 * a back-edge.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object L2_STRIP_MANIFEST : L2Operation(
	READ_BOXED_VECTOR.named("live values"))
{
	// Prevent this instruction from being removed, because it constrains
	// the manifest along a back-edge, even after optimization.
	override val hasSideEffect get() = true

	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		val liveVector = instruction.operand<L2ReadBoxedVectorOperand>(0)

		// Clear the manifest, other than the mentioned semantic values and
		// registers.
		val elements = liveVector.elements
		val liveSemanticValues = elements.mapToSet { it.semanticValue() }
		val liveRegisters = elements.mapToSet { it.register() }
		manifest.retainSemanticValues(liveSemanticValues)
		manifest.retainRegisters(liveRegisters)
		liveVector.instructionWasAdded(manifest)
		// After stripping the manifest down to the block arguments needed for a
		// P_RestartWithArguments, we *must not* allow any additional postponed
		// instructions to run.  There was a rare case (2022.07.07) in which an
		// L2_MAKE_IMMUTABLE was still present in the postponed instructions map
		// [MvG 2024.01.15 - immutability is now handled differently], and was
		// getting its input from other instructions that got their value from
		// semantic values already stripped from the manifest.  So we clear
		// postponed instructions, since after this L2_STRIP_MANIFEST there is
		// no valid thing that can be done except moves from those registers,
		// another strip-manifest for safety, and an L2_JUMP_BACK.
		manifest.clearPostponedInstructions()
	}

	override fun emitTransformedInstruction(
		transformedOperands: Array<L2Operand>,
		regenerator: L2Regenerator)
	{
		val liveVector = transformedOperands[0] as L2ReadBoxedVectorOperand

		// Re-strip the manifest.
		val manifest = regenerator.currentManifest
		val elements = liveVector.elements
		val liveSemanticValues = elements.mapToSet { it.semanticValue() }
		val liveRegisters = elements.mapToSet { it.register() }
		manifest.retainSemanticValues(liveSemanticValues)
		manifest.retainRegisters(liveRegisters)
		// Any postponed instructions that produce the values consumed by this
		// instruction have already been generated, so discard the rest.
		manifest.clearPostponedInstructions()
		super.emitTransformedInstruction(transformedOperands, regenerator)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		// No effect.
	}
}
