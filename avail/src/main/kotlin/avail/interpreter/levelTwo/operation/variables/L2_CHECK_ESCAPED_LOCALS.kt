/*
 * L2_CHECK_ESCAPED_LOCALS.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.interpreter.levelTwo.operation.variables

import avail.descriptor.representation.A_Variable.Companion.checkForSharedOrReactorsMethod
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_FUNCTION
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operation.L2ControlFlowInstruction
import avail.interpreter.levelTwo.operation.L2_MOVE_BOXED
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import avail.optimizer.L1Translator
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2Optimizer
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Opcodes

/**
 * Check each of the locals, which must contain a variable.  If any has become
 * shared or has had a reactor added to it, fall back to L1 [DefaultL1Chunk].
 * This instruction is emitted by the [L1Translator] immediately after an invoke
 * that might cause one of those effects to a variable, whether passed directly
 * or not.
 *
 * The [L2Optimizer.postponeConditionallyUsedValues] pass can cause the
 * [L2_CREATE_VARIABLE] instructions to migrate past it, which removes that
 * local from the instruction, or if it was the last one, removes the
 * instruction entirely.
 *
 * See "/avail/doc/Optimization/Level Two/Variable elision.md" for the overall
 * variable elision scheme.
 *
 * @param localsToCheck
 *   The local variables to be examined.
 * @param localsOutput
 *   The same local variables, but tied to different semantic values.
 * @param ifSafe
 *   Where to go if the variables are all ok.
 * @param ifFallBack
 *   Where to go if a variable has become shared or has a reactor, and we need
 *   to fall back to L1 execution.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@ReadsHiddenVariable(CURRENT_FUNCTION::class)
@WritesHiddenVariable(CURRENT_FUNCTION::class)
class L2_CHECK_ESCAPED_LOCALS(
	var reason: L2CommentOperand,
	var localsToCheck: L2ReadBoxedVectorOperand,
	@On(SUCCESS) var localsOutput: L2WriteBoxedVectorOperand,
	@On(SUCCESS) var ifSafe: L2PcOperand,
	@On(FAILURE) var ifFallBack: L2PcOperand
) : L2ControlFlowInstruction()
{
	// This instruction must not be removed.
	override val hasSideEffect get() = true

	/** Examining the variable doesn't add a reference to it. */
	override val readsThatMightDestroy get() = emptyList<L2ReadBoxedOperand>()

	override fun aboutToAdd(generator: L2GeneratorInterface): Boolean
	{
		val keptLocals = mutableListOf<L2ReadBoxedOperand>()
		val keptWrites = mutableListOf<L2WriteBoxedOperand>()
		localsToCheck.elements.zip(localsOutput.elements).forEach { pair ->
			val (read, write) = pair
			val postponed = generator.currentManifest
				.postponedInstructionFor(read.semanticValue())
			when (postponed)
			{
				is L2_CREATE_VARIABLE ->
				{
					// Keep the instruction postponed, and add a postponed move
					// from the old register with the variable to the new one.
					generator.run {
						+L2_MOVE_BOXED(read, write)
					}
				}
				else ->
				{
					// Either it's not postponed, or it's some other instruction
					// besides a variable creation that produced the variable.
					// The second case probably shouldn't happen, but play it
					// safe and force it to generate.
					generator.forceTranslationForRead(read.semanticValue())
					keptLocals.add(read)
					keptWrites.add(write)
				}
			}
		}
		when (keptLocals.size)
		{
			// Skip the check entirely.
			0 -> generator.jumpTo(ifSafe.targetBlock())
			// The instruction was unchanged.
			localsToCheck.elements.size -> return true
			// Produce a reduced instruction.
			else -> generator.addInstruction(
				L2_CHECK_ESCAPED_LOCALS(
					reason,
					L2ReadBoxedVectorOperand(keptLocals),
					L2WriteBoxedVectorOperand(keptWrites),
					ifSafe,
					ifFallBack))
		}
		// An alternative instruction was created.
		return false
	}

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(" Check locals: ")
		localsToCheck.elements.joinTo(this) { it.registerString() }
		append("\n\tinto: ")
		localsOutput.elements.joinTo(this) { it.registerString() }
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

	override fun JVMTranslator.translateToJVM()
	{
		localsToCheck.elements.forEach { local ->
			load(local)
			// :: local-variable
			generateCall(checkForSharedOrReactorsMethod)
			// :: local-shared-or-has-reactor
			method.visitJumpInsn(
				Opcodes.IFNE, labelFor(ifFallBack.offset()))
		}
		// Transfer from the sources to the corresponding destinations.  Most of
		// these pairs will have been assigned to the same register, and can be
		// elided.
		assert(localsToCheck.elements.size == localsOutput.elements.size)
		transferPairwise(localsToCheck.registers(), localsOutput.registers())
		jump(ifSafe)
	}
}
