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

import avail.descriptor.functions.A_RegisterDump.Companion.extractDumpedLongAtMethod
import avail.descriptor.functions.A_RegisterDump.Companion.extractDumpedObjectAtMethod
import avail.descriptor.representation.AvailObject
import avail.interpreter.JavaLibrary.bitCastLongToDoubleMethod
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2JVMChunk.ChunkEntryPoint
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.COMMENT
import avail.interpreter.levelTwo.L2OperandType.Companion.INT_IMMEDIATE
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_CONTINUATION
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

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
object L2_ENTER_L2_CHUNK : L2Operation(
	INT_IMMEDIATE.named("entry point offset in default chunk"),
	COMMENT.named("chunk entry point name"))
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
		if (offsetInDefaultChunk.value !=
			ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk)
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
		if (localNumberLists !== null)
		{
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
					extractDumpedObjectAtMethod.generateCall(method)
					method.visitVarInsn(
						BOXED_KIND.storeInstruction, boxedList[i])
				}
				var i = 1  //one-based
				for (intRegisterIndex in intsList)
				{
					if (--countdown > 0)
					{
						method.visitInsn(Opcodes.DUP)
						// Stack has two registerDumps if needed.
					}
					translator.intConstant(method, i++) //one-based
					extractDumpedLongAtMethod.generateCall(method)
					method.visitInsn(Opcodes.L2I)
					method.visitVarInsn(
						INTEGER_KIND.storeInstruction, intRegisterIndex)
				}
				for (floatRegisterIndex in floatsList)
				{
					if (--countdown > 0)
					{
						method.visitInsn(Opcodes.DUP)
						// Stack has two registerDumps if needed.
					}
					translator.intConstant(method, i++) //one-based
					extractDumpedLongAtMethod.generateCall(method)
					bitCastLongToDoubleMethod.generateCall(method)
					method.visitVarInsn(
						FLOAT_KIND.storeInstruction, floatRegisterIndex)
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
