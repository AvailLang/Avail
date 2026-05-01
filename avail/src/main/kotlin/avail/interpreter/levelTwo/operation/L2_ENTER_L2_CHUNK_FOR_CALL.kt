/*
 * L2_ENTER_L2_CHUNK_FOR_CALL.kt
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

import avail.descriptor.representation.AvailObject
import avail.interpreter.JavaLibrary.listGetMethod
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_CONTINUATION
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint.AFTER_PRIMITIVE_FAILURE
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * This marks the entry point into optimized (level two) code.  At entry, the
 * arguments are expected to be in the [Interpreter.argsBuffer].  Set up fresh
 * registers for this chunk, but do not write to them yet.
 *
 * This instruction also occurs at places that a reified continuation can be
 * re-entered, such as returning into it, restarting it, or continuing it after
 * an interrupt has been handled.
 */
@ReadsHiddenVariable(CURRENT_CONTINUATION::class)
@WritesHiddenVariable(CURRENT_CONTINUATION::class)
class L2_ENTER_L2_CHUNK_FOR_CALL(
	@Suppress("unused")
	var chunkEntryPointName: L2CommentOperand,
	var writeArguments: L2WriteBoxedVectorOperand,
) : L2Instruction()
{
	override val isEntryPoint get() = true

	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		writeArguments.elements.forEachIndexed { i, write ->
			append("\n\t")
			append(write.registerString())
			append(" = arg #")
			append(i + 1)
		}
	}

	override fun JVMTranslator.translateToJVM()
	{
		// While it's true that the raw function's starting chunk will be
		// switched to the default chunk during invalidation, we can still reach
		// this point via a restart of an existing continuation that still
		// refers to the old chunk, so we still have to check validity and fall
		// back to the default chunk, using the TO_RESTART entry point.  Note
		// that there can't be a primitive for such continuations.

		// :: if (!checkValidity()) {
		loadInterpreter()
		intConstant(AFTER_PRIMITIVE_FAILURE.offset)
		generateCall(Interpreter.checkValidityMethod)
		val isValidLabel = Label()
		method.visitJumpInsn(Opcodes.IFNE, isValidLabel)
		// ::    return null;
		method.visitInsn(Opcodes.ACONST_NULL)
		method.visitInsn(Opcodes.ARETURN)
		// :: }
		method.visitLabel(isValidLabel)

		val argWrites = writeArguments.elements
		if (argWrites.isNotEmpty())
		{
			// Populate the argument registers from the argsBuffer.
			loadInterpreter()
			load(Interpreter.argsBufferField)
			// [argsBuffer]
			argWrites.forEachIndexed { i, write ->
				if (i < argWrites.size - 1)
				{
					// [argsBuffer, argsBuffer]
					method.visitInsn(Opcodes.DUP)
				}
				// [... argsBuffer]
				intConstant(i)
				// [... argsBuffer, i]
				generateCall(listGetMethod)
				// [... argsBuffer[i]]
				method.visitTypeInsn(
					Opcodes.CHECKCAST,
					Type.getInternalName(AvailObject::class.java))
				store(write.register())
				// [...]
			}
		}
		// []  – i.e., last occurrence of argsBuffer has been popped.
	}
}
