/*
 * L2_JUMP_IF_UNBOX_INT.kt
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

import avail.descriptor.numbers.A_Number
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.PC
import avail.interpreter.levelTwo.L2OperandType.READ_BOXED
import avail.interpreter.levelTwo.L2OperandType.WRITE_INT
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Jump to `"if unboxed"` if an [Int] was unboxed from an [AvailObject],
 * otherwise jump to `"if not unboxed"`.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_JUMP_IF_UNBOX_INT : L2ConditionalJump(
	READ_BOXED.named("source"),
	WRITE_INT.named("destination", SUCCESS),
	PC.named("if not unboxed", FAILURE),
	PC.named("if unboxed", SUCCESS))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val source = instruction.operand<L2ReadBoxedOperand>(0)
		val destination = instruction.operand<L2WriteIntOperand>(1)
		//		final L2PcOperand ifNotUnboxed = instruction.operand(2);
//		final L2PcOperand ifUnboxed = instruction.operand(3);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(destination.registerString())
		builder.append(" ←? ")
		builder.append(source.registerString())
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		assert(this == instruction.operation)
		val source = instruction.operand<L2ReadBoxedOperand>(0)
		val destination = instruction.operand<L2WriteIntOperand>(1)
		val ifNotUnboxed = instruction.operand<L2PcOperand>(2)
		val ifUnboxed = instruction.operand<L2PcOperand>(3)
		source.instructionWasAdded(manifest)
		ifNotUnboxed.instructionWasAdded(
			L2ValueManifest(manifest).apply {
				subtractType(source.semanticValue(), int32)
			})
		// Ensure the value is available along the success edge.
		destination.instructionWasAdded(manifest)
		ifUnboxed.instructionWasAdded(
			L2ValueManifest(manifest).apply {
				intersectType(destination.pickSemanticValue(), int32)
			})
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val source = instruction.operand<L2ReadBoxedOperand>(0)
		val destination = instruction.operand<L2WriteIntOperand>(1)
		val ifNotUnboxed = instruction.operand<L2PcOperand>(2)
		val ifUnboxed = instruction.operand<L2PcOperand>(3)

		// :: if (!source.isInt()) goto ifNotUnboxed;
		translator.load(method, source.register())
		A_Number.isIntMethod.generateCall(method)
		method.visitJumpInsn(
			Opcodes.IFEQ, translator.labelFor(ifNotUnboxed.offset()))
		// :: else {
		// ::    destination = source.extractInt();
		// ::    goto ifUnboxed;
		// :: }
		translator.load(method, source.register())
		A_Number.extractIntStaticMethod.generateCall(method)
		translator.store(method, destination.register())
		translator.jump(method, instruction, ifUnboxed)
	}
}
