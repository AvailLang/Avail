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

import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2GeneratorInterface
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
class L2_JUMP_BACK(
	@On(SUCCESS) var target: L2PcOperand,
	var registersToKeep: L2ReadBoxedVectorOperand
): L2ControlFlowInstruction()
{
	// It jumps, which counts as a side effect.
	override val hasSideEffect get() = true

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		// Play the reads against the old manifest, which is then filtered.
		registersToKeep.instructionWasAdded(manifest)
		val semanticValuesToKeep = mutableSetOf<L2SemanticValue<*>>()
		val registersToKeep = mutableSetOf<L2Register<*>>()
		this.registersToKeep.elements.forEach { read: L2ReadBoxedOperand ->
			semanticValuesToKeep.add(read.semanticValue())
			read.restriction().constantOrNull?.let { constant ->
				// Also include any associated semantic constant, to ensure the
				// invariant of the manifest is maintained – i.e., that any
				// synonym of boxed values constrained to a constant must
				// include a semantic constant.
				semanticValuesToKeep.add(L2SemanticValue.constant(constant))
			}
			registersToKeep.add(read.register())
		}
		manifest.clearPostponedInstructions()
		manifest.retainSemanticValues(semanticValuesToKeep)
		manifest.retainRegisters(registersToKeep)
		target.instructionWasAdded(manifest)
		target.forcedClampedEntities =
			(semanticValuesToKeep + registersToKeep).toMutableSet()
	}

	override fun replaceConstantReads(
		generator: L2GeneratorInterface,
		registerToValueMap: MutableMap<L2Register<*>, L2SemanticValue<*>>)
	{
		// Don't replace my registersToKeep with constants, since that makes it
		// too confusing to process backward jumps and doesn't add any value.
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: goto offset;
		translator.jumpOrFallThrough(method, target)
	}
}
