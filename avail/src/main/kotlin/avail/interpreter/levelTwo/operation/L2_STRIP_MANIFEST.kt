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
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticValue
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

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		inputs.elements.zip(outputs.elements)
			.forEachIndexed { i, (input, output) ->
				append(
					"\n\t#${i+1}: ${output.registerString()}" +
						" ← ${input.registerString()}")
			}
	}

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		// Clear the manifest, other than the semantic values and registers that
		// are written by this instruction.
		inputs.instructionWasAdded(manifest)
		manifest.clear()
		manifest.clearPostponedInstructions()
		outputs.instructionWasAdded(manifest)
	}

	override val producesAnyJvmCode: Boolean
		get() = inputs.registers()
			.zip(outputs.registers())
			.any { (read, write) ->
				read.finalIndex == -1 || read.finalIndex != write.finalIndex
			}

	override fun sourceOfMoveToRegister(
		destinationRegister: L2Register<*>
	): L2Register<*>?
	{
		// The inputs are considered moved to the corresponding outputs.
		val index = destinationRegisters.indexOf(destinationRegister)
		assert(index != -1)
		return sourceRegisters[index]
	}

	override fun processForMakeImmutable(
		firstUses: MutableMap<L2BoxedRegister, Pair<Int, L2ReadBoxedOperand>>,
		insertions: MutableList<Pair<Int, L2ReadBoxedOperand>>,
		mutables: MutableSet<L2BoxedRegister>,
		uniqueGenerator: ()->Int)
	{
		// Deal with L2_STRIP_MANIFEST instructions that have their data moves
		// *entirely* elided due to register coloring.  If even one move is
		// still needed, generate makeImmutables for *all* of the registers.
		if (sourceRegisters == destinationRegisters) return
		super.processForMakeImmutable(
			firstUses, insertions, mutables, uniqueGenerator)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// Transfer from the sources to the corresponding destinations.  Most of
		// these pairs will have been assigned to the same register, and can be
		// elided.
		translator.transferPairwise(
			method, inputs.registers(), outputs.registers())
	}
}
