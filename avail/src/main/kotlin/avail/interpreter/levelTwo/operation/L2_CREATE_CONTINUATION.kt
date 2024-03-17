/*
 * L2_CREATE_CONTINUATION.kt
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

import avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationExceptFrameMethod
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.COMMENT
import avail.interpreter.levelTwo.L2OperandType.Companion.INT_IMMEDIATE
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED_VECTOR
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_INT
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_BOXED
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor

/**
 * Create a continuation from scratch, using the specified caller, function,
 * constant level one program counter, constant stack pointer, continuation
 * slot values, and level two program counter.  Write the new continuation
 * into the specified register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_CREATE_CONTINUATION : L2Operation(
	READ_BOXED.named("function"),
	READ_BOXED.named("caller"),
	INT_IMMEDIATE.named("level one pc"),
	INT_IMMEDIATE.named("stack pointer"),
	READ_BOXED_VECTOR.named("slot values"),
	WRITE_BOXED.named("destination"),
	READ_INT.named("label address"),
	READ_BOXED.named("register dump"),
	COMMENT.named("usage comment"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val function = instruction.operand<L2ReadBoxedOperand>(0)
		val caller = instruction.operand<L2ReadBoxedOperand>(1)
		val levelOnePC = instruction.operand<L2IntImmediateOperand>(2)
		val levelOneStackp = instruction.operand<L2IntImmediateOperand>(3)
		val slots = instruction.operand<L2ReadBoxedVectorOperand>(4)
		val destReg = instruction.operand<L2WriteBoxedOperand>(5)
		//		final L2ReadIntOperand labelIntReg = instruction.operand(6);
		//		final L2ReadBoxedOperand registerDumpReg = instruction.operand(7);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(destReg)
		builder.append(" ← $[")
		builder.append(function)
		builder.append("]\n\tpc=")
		builder.append(levelOnePC)
		builder.append("\n\tstack=[")
		var first = true
		for (slot in slots.elements)
		{
			if (!first)
			{
				builder.append(",")
			}
			first = false
			builder.append("\n\t\t")
			builder.append(slot)
		}
		builder.append("]\n\t[stackp=")
		builder.append(levelOneStackp)
		builder.append("]\n\tcaller=")
		builder.append(caller)
		renderOperandsStartingAt(instruction, 6, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		assert(this == instruction.operation)
		val function = instruction.operand<L2ReadBoxedOperand>(0)
		val caller = instruction.operand<L2ReadBoxedOperand>(1)
		val levelOnePC = instruction.operand<L2IntImmediateOperand>(2)
		val levelOneStackp = instruction.operand<L2IntImmediateOperand>(3)
		val slots = instruction.operand<L2ReadBoxedVectorOperand>(4)
		val destReg = instruction.operand<L2WriteBoxedOperand>(5)
		val labelIntReg = instruction.operand<L2ReadIntOperand>(6)
		val registerDumpReg = instruction.operand<L2ReadBoxedOperand>(7)

		// :: continuation = createContinuationExceptFrame(
		// ::    function,
		// ::    caller,
		// ::    registerDump
		// ::    levelOnePC,
		// ::    levelOneStackp,
		// ::    interpreter.chunk,
		// ::    onRampOffset);
		translator.load(method, function.register())
		translator.load(method, caller.register())
		translator.load(method, registerDumpReg.register())
		translator.literal(method, levelOnePC.value)
		translator.literal(method, levelOneStackp.value)
		translator.loadInterpreter(method)
		Interpreter.chunkField.generateRead(method)
		translator.load(method, labelIntReg.register())
		createContinuationExceptFrameMethod.generateCall(method)
		val slotCount = slots.elements.size
		var pushed = 0
		for (i in 0 until slotCount)
		{
			val regRead = slots.elements[i]
			val constant: A_BasicObject? = regRead.constantOrNull()
			// Skip if it's always nil, since the continuation was already
			// initialized with nils.
			if (constant === null || constant.notNil)
			{
				// :: continuation.frameAtPut(«i + 1», «slots[i]»)...
				// [continuation]
				translator.intConstant(method, i + 1)
				translator.load(method, slots.elements[i].register())
				if (++pushed == 6)
				{
					AvailObject.frameAtPut6Method.generateCall(method)
					// Method returns continuation to simplify stack handling.
					// [continuation]
					pushed = 0
				}
			}
		}
		when (pushed)
		{
			0 -> { }
			1 -> AvailObject.frameAtPutMethod.generateCall(method)
			2 -> AvailObject.frameAtPut2Method.generateCall(method)
			3 -> AvailObject.frameAtPut3Method.generateCall(method)
			4 -> AvailObject.frameAtPut4Method.generateCall(method)
			5 -> AvailObject.frameAtPut5Method.generateCall(method)
			else -> throw AssertionError(
				"Internal error - wrong bulk write size for frame")
		}
		// [continuation]
		translator.store(method, destReg.register())
	}
}
