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
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticValue
import avail.utility.mapToSet
import org.objectweb.asm.MethodVisitor

/**
 * This is a special operation which limits which [L2Register]s and
 * [L2SemanticValue]s are live when reaching a back-edge.  Otherwise there could
 * be confusion about whether a write preceded a read, since multiple loop
 * iterations could overlap in the analysis.  This way, only the writes
 * performed by the strip-manifest will be visible along the back-edge, sealing
 * up cycles tidily.  Some day, loop-specific optimizations may be able to
 * cautiously look past these barriers.
 *
 * Say the code generator detects a loop, by encountering an invocation of
 * [P_RestartContinuation] or [P_RestartContinuationWithArguments] with the
 * current frame's label as the first argument.  It will replace the invocation,
 * if possible, with code to strip the manifest down to just the (new) input
 * arguments to the function, and do a backward jump to a special block near the
 * top of the graph.  To strip the manifest safely, we move the live values into
 * temp registers, strip the manifest to those registers, move the values into
 * new registers associated with the function's input arguments' semantic
 * values, strip them again, and do a backward jump.
 *
 * The [L2_STRIP_MANIFEST] instructions must not be removed, but during register
 * coloring the corresponding inputs and outputs should be treated as moves, to
 * allow them to have the same color, and thereby elide any JVM code for the
 * actual moves.
 *
 * @constructor
 *   Create a new [L2_STRIP_MANIFEST] instruction.
 * @property inputs
 *   An [L2ReadBoxedVectorOperand] of inputs that correspond to the [outputs].
 * @property outputs
 *   An [L2WriteBoxedVectorOperand] that correspond to the [inputs].
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_STRIP_MANIFEST(
	var inputs: L2ReadBoxedVectorOperand,
	var outputs: L2WriteBoxedVectorOperand
): L2Instruction()
{
	/**
	 * Prevent this instruction from being removed, because it constrains the
	 * manifest along a back-edge, even after optimization.
	 */
	override val hasSideEffect get() = true

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)

		// Clear the manifest, other than the semantic values and registers that
		// are written by this instruction.
		val liveSemanticValues =
			outputs.elements.mapToSet { it.onlySemanticValue() }
		val liveRegisters = outputs.elements.mapToSet { it.register() }
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
		manifest.retainSemanticValues(liveSemanticValues)
		manifest.retainRegisters(liveRegisters)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// Transfer from the sources to the corresponding destinations.  Most of
		// these pairs will have been assigned to the same register, and can be
		// elided.
		(inputs.elements zip outputs.elements).forEach { (read, write) ->
			if (read.register().finalIndex() != write.register().finalIndex())
			{
				// That pair didn't get eliminated during coloring, so emit an
				// actual move.
				translator.load(method, read.register())
				translator.store(method, write.register())
			}
		}
	}
}
