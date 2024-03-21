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
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2JVMChunk
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.COMMENT
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_BOXED_VECTOR
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_CONTINUATION
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
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
object L2_ENTER_L2_CHUNK_FOR_CALL : L2Operation(
	COMMENT.named("chunk entry point name"),
	WRITE_BOXED_VECTOR.named("arguments"))
{
	override fun isEntryPoint(instruction: L2Instruction): Boolean = true

	override val hasSideEffect get() = true

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		//val comment = instruction.operand<L2CommentOperand>(0)
		val writeArguments = instruction.operand<L2WriteBoxedVectorOperand>(1)
		renderPreamble(instruction, builder)
		writeArguments.elements.forEachIndexed { i, write ->
			builder.append("\n\t")
			builder.append(write.registerString())
			builder.append(" = arg #")
			builder.append(i)
		}
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		//val comment = instruction.operand<L2CommentOperand>(0)
		val writeArguments = instruction.operand<L2WriteBoxedVectorOperand>(1)

		// While it's true that the raw function's starting chunk will be
		// switched to the default chunk during invalidation, we can still reach
		// this point via a restart of an existing continuation that still
		// refers to the old chunk, so we still have to check validity and fall
		// back to the default chunk, using the TO_RESTART entry point.  Note
		// that there can't be a primitive for such continuations.

		// :: if (!checkValidity()) {
		translator.loadInterpreter(method)
		translator.literal(
			method, L2JVMChunk.ChunkEntryPoint.TO_RESTART.offsetInDefaultChunk)
		Interpreter.checkValidityMethod.generateCall(method)
		val isValidLabel = Label()
		method.visitJumpInsn(Opcodes.IFNE, isValidLabel)
		// ::    return null;
		method.visitInsn(Opcodes.ACONST_NULL)
		method.visitInsn(Opcodes.ARETURN)
		// :: }
		method.visitLabel(isValidLabel)

		// If this chunk had an L2_VIRTUAL_CREATE_LABEL that survived, producing
		// an empty register dump, or it didn't producing no entry at all.
		// Either is acceptable, and should be ignored.
		val localNumberLists =
			translator.liveLocalNumbersByKindPerEntryPoint[instruction]
		if (localNumberLists !== null)
		{
			val boxedList = localNumberLists[BOXED_KIND]!!
			val intsList = localNumberLists[INTEGER_KIND]!!
			val floatsList = localNumberLists[FLOAT_KIND]!!
			assert(boxedList.isEmpty())
			assert(intsList.isEmpty())
			assert(floatsList.isEmpty())
		}

		val argWrites = writeArguments.elements
		if (argWrites.isNotEmpty())
		{
			// Populate the argument registers from the argsBuffer.
			translator.loadInterpreter(method)
			Interpreter.argsBufferField.generateRead(method)
			// [argsBuffer]
			argWrites.forEachIndexed { i, write ->
				if (i < argWrites.size - 1)
				{
					// [argsBuffer, argsBuffer]
					method.visitInsn(Opcodes.DUP)
				}
				// [... argsBuffer]
				translator.intConstant(method, i)
				// [... argsBuffer, i]
				listGetMethod.generateCall(method)
				// [... argsBuffer[i]]
				method.visitTypeInsn(
					Opcodes.CHECKCAST,
					Type.getInternalName(AvailObject::class.java))
				translator.store(method, write.register())
				// [...]
			}
		}
		// []  – i.e., last occurrence of argsBuffer has been popped.
	}
}
