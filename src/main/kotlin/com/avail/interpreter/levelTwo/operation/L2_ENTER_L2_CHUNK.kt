/*
 * L2_ENTER_L2_CHUNK.kt
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

import com.avail.descriptor.functions.ContinuationRegisterDumpDescriptor
import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.JavaLibrary.bitCastLongToDoubleMethod
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.*
import com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_CONTINUATION
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * This marks the entry point into optimized (level two) code.  At entry, the
 * arguments are expected to be in the [Interpreter.argsBuffer].  Set up
 * fresh registers for this chunk, but do not write to them yet.
 *
 * This instruction also occurs at places that a reified continuation can be
 * re-entered, such as returning into it, restarting it, or continuing it after
 * an interrupt has been handled.
 */
@ReadsHiddenVariable(theValue = arrayOf(CURRENT_CONTINUATION::class))
@WritesHiddenVariable(CURRENT_CONTINUATION::class)
object L2_ENTER_L2_CHUNK : L2Operation(
	L2OperandType.INT_IMMEDIATE.named("entry point offset in default chunk"),
	L2OperandType.COMMENT.named("chunk entry point name"))
{
	override fun isEntryPoint(instruction: L2Instruction): Boolean = true

	override fun hasSideEffect(): Boolean = true

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		//		final L2IntImmediateOperand offsetInDefaultChunk =
//			instruction.operand(0);
//		final L2CommentOperand comment = instruction.operand(1);
		renderPreamble(instruction, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val offsetInDefaultChunk =
			instruction.operand<L2IntImmediateOperand>(0)
		//		final L2CommentOperand comment = instruction.operand(1);

		// Skip the validity check for transient entry points, which can't
		// become invalid during their lifetimes.
		if (offsetInDefaultChunk.value != ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk)
		{
			// :: if (!checkValidity()) {
			translator.loadInterpreter(method)
			translator.literal(method, offsetInDefaultChunk.value)
			Interpreter.checkValidityMethod.generateCall(method)
			val isValidLabel = Label()
			method.visitJumpInsn(Opcodes.IFNE, isValidLabel)
			// ::    return null;
			method.visitInsn(Opcodes.ACONST_NULL)
			method.visitInsn(Opcodes.ARETURN)
			// :: }
			method.visitLabel(isValidLabel)
		}

		// Extract register values that were saved in a register dump by the
		// corresponding L2_SAVE_ALL_AND_PC_TO_INT instruction, which nicely set
		// up for us the lists of registers that were saved.  The interpreter
		// should have extracted the registerDump for us already.
		val localNumberLists =
			translator.liveLocalNumbersByKindPerEntryPoint[instruction]
		if (localNumberLists != null)
		{
			val boxedList = localNumberLists[RegisterKind.BOXED]!!
			val intsList = localNumberLists[RegisterKind.INTEGER]!!
			val floatsList = localNumberLists[RegisterKind.FLOAT]!!
			val boxedCount = boxedList.size
			val intsCount = intsList.size
			val floatsCount = floatsList.size
			var countdown = boxedCount + intsCount + floatsCount
			if (countdown > 0)
			{
				// Extract the register dump from the current continuation.
				translator.loadInterpreter(method)
				Interpreter.getReifiedContinuationMethod.generateCall(method)
				AvailObject.registerDumpMethod.generateCall(method)
				// Stack now has the registerDump.
				for (i in 0 until boxedCount)
				{
					if (--countdown > 0)
					{
						method.visitInsn(Opcodes.DUP)
						// Stack has two registerDumps if needed.
					}
					translator.intConstant(method, i + 1) //one-based
					ContinuationRegisterDumpDescriptor.extractObjectAtMethod
						.generateCall(method)
					method.visitVarInsn(
						RegisterKind.BOXED.storeInstruction,
						boxedList[i])
				}
				var i = 0
				while (i < intsCount)
				{
					if (--countdown > 0)
					{
						method.visitInsn(Opcodes.DUP)
						// Stack has two registerDumps if needed.
					}
					translator.intConstant(method, i + 1) //one-based
					ContinuationRegisterDumpDescriptor.extractLongAtMethod
						.generateCall(method)
					method.visitInsn(Opcodes.L2I)
					method.visitVarInsn(
						RegisterKind.INTEGER.storeInstruction,
						intsList[i])
					i++
				}
				var j = 0
				while (j < floatsCount)
				{
					if (--countdown > 0)
					{
						method.visitInsn(Opcodes.DUP)
						// Stack has two registerDumps if needed.
					}
					translator.intConstant(method, i + 1) //one-based
					ContinuationRegisterDumpDescriptor.extractLongAtMethod.generateCall(method)
					bitCastLongToDoubleMethod.generateCall(
						method)
					method.visitVarInsn(
						RegisterKind.FLOAT.storeInstruction,
						floatsList[j])
					j++
					i++
				}
				assert(countdown == 0)
				// The last copy of registerDumps was popped.
			}

			// :: interpreter.popContinuation();
			translator.loadInterpreter(method)
			Interpreter.popContinuationMethod.generateCall(method)
		}
	}
}