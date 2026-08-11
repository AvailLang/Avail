/*
 * L2_ENTER_L2_CHUNK.kt
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

import avail.descriptor.representation.A_RegisterDump
import avail.descriptor.representation.A_RegisterDump.Companion.extractDumpedLongAtMethod
import avail.descriptor.representation.A_RegisterDump.Companion.extractDumpedObjectAtMethod
import avail.descriptor.representation.AvailObject
import avail.interpreter.JavaLibrary.bitCastLongToDoubleMethod
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.interpreterRunChunkMethod
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_CONTINUATION
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

/**
 * This marks the entry point into optimized (level two) code.  At entry, the
 * arguments are expected to be in the [Interpreter.argsBuffer].  Set up fresh
 * registers for this chunk, but do not write to them yet.
 *
 * This instruction also occurs at places that a reified continuation can be
 * re-entered, such as returning into it, restarting it, or continuing it after
 * an interrupt has been handled.  In that case, the entry point is lookeed up
 * during JVM code generation in the [JVMTranslator.entryPointLiveInfo], and any
 * registers saved by the [L2_SAVE_ALL_AND_PC_TO_INT] into an [A_RegisterDump]
 * will be restored in the generated JVM code.
 */
@ReadsHiddenVariable(CURRENT_CONTINUATION::class)
@WritesHiddenVariable(CURRENT_CONTINUATION::class)
class L2_ENTER_L2_CHUNK
constructor(
	var entryPointOffsetInDefaultChunk: L2IntImmediateOperand,
	var chunkEntryPointName: L2CommentOperand
): L2Instruction()
{
	override val isEntryPoint get() = true

	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
	}

	override fun JVMTranslator.translateToJVM()
	{
		// Skip the validity check for transient entry points, which can't
		// become invalid during their lifetimes.
		if (entryPointOffsetInDefaultChunk.value !=
			DefaultEntryPoint.TRANSIENT.offset)
		{
			// :: if (checkValidity()) {
			loadInterpreter()
			intConstant(entryPointOffsetInDefaultChunk.value)
			generateCall(Interpreter.checkValidityMethod)
			val isValidLabel = Label()
			method.visitJumpInsn(Opcodes.IFNE, isValidLabel)
			// ::    return interpreter.runChunk()
			loadInterpreter()
			generateCall(interpreterRunChunkMethod)
			method.visitInsn(Opcodes.ARETURN)

			// Happy path, chunk is still valid.
			method.visitLabel(isValidLabel)
		}

		// Extract register values that were saved in a register dump by the
		// corresponding L2_SAVE_ALL_AND_PC_TO_INT instruction, which nicely set
		// up for us the lists of registers that were saved.  The interpreter
		// should have extracted the registerDump for us already.
		entryPointLiveInfo[offset]?.let { localNumberLists ->
			val boxedList = localNumberLists[BOXED_KIND]!!
			val intsList = localNumberLists[INTEGER_KIND]!!
			val floatsList = localNumberLists[FLOAT_KIND]!!
			val boxedCount = boxedList.size
			val intsCount = intsList.size
			val floatsCount = floatsList.size
			var countdown = boxedCount + intsCount + floatsCount
			if (countdown > 0)
			{
				// Extract the register dump from the current continuation.
				loadInterpreter()
				// :: [interpreter]
				generateCall(Interpreter.getReifiedContinuationMethod)
				// :: [continuation]
				generateCall(AvailObject.registerDumpMethod)
				// :: [registerDump]
				// Stack now has the registerDump.
				for (i in 0 until boxedCount)
				{
					if (--countdown > 0)
					{
						method.visitInsn(Opcodes.DUP)
						// :: [registerDump, registerDump]
					}
					intConstant(i + 1) //one-based
					generateCall(extractDumpedObjectAtMethod)
					method.visitVarInsn(
						BOXED_KIND.jvmStoreInstruction, boxedList[i])
					// :: [registerDump], or [] on last use
				}
				var i = 1  //one-based
				for (intRegisterIndex in intsList)
				{
					if (--countdown > 0)
					{
						method.visitInsn(Opcodes.DUP)
						// :: [registerDump, registerDump]
					}
					intConstant(i++) //one-based
					generateCall(extractDumpedLongAtMethod)
					method.visitInsn(Opcodes.L2I)
					method.visitVarInsn(
						INTEGER_KIND.jvmStoreInstruction, intRegisterIndex)
					// :: [registerDump], or [] on last use
				}
				for (floatRegisterIndex in floatsList)
				{
					if (--countdown > 0)
					{
						method.visitInsn(Opcodes.DUP)
						// :: [registerDump, registerDump]
					}
					intConstant(i++) //one-based
					generateCall(extractDumpedLongAtMethod)
					generateCall(bitCastLongToDoubleMethod)
					method.visitVarInsn(
						FLOAT_KIND.jvmStoreInstruction, floatRegisterIndex)
					// :: [registerDump], or [] on last use
				}
				assert(countdown == 0)
				// The last copy of registerDumps was popped.
				// :: []
			}
		}
		// :: interpreter.popContinuation();
		loadInterpreter()
		// :: [interpreter]
		generateCall(Interpreter.popContinuationMethod)
		// :: [poppedContinuation]
		method.visitInsn(Opcodes.POP)
		// :: []
	}
}
