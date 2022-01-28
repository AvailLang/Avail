/*
 * L2_GET_TYPE.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
import avail.descriptor.types.AbstractEnumerationTypeDescriptor
import avail.descriptor.types.InstanceMetaDescriptor
import avail.descriptor.types.InstanceTypeDescriptor
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NONTYPE
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Extract the [exact&#32;type][InstanceTypeDescriptor] of an object in a
 * register, writing the type to another register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_GET_TYPE : L2Operation(
	L2OperandType.READ_BOXED.named("value"),
	L2OperandType.WRITE_BOXED.named("value's type"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val type = instruction.operand<L2WriteBoxedOperand>(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(type.registerString())
		builder.append(" ← ")
		builder.append(value.registerString())
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val type = instruction.operand<L2WriteBoxedOperand>(1)
		translator.load(method, value.register())
		// [value]
		when
		{
			value.restriction().containedByType(NONTYPE.o) ->
			{
				// The value will *never* be a type.
				InstanceTypeDescriptor.instanceTypeMethod.generateCall(method)
			}
			value.restriction().containedByType(
				InstanceMetaDescriptor.topMeta()) ->
			{
				// The value will *always* be a type. Strengthen to AvailObject.
				InstanceMetaDescriptor.instanceMetaMethod.generateCall(method)
				method.visitTypeInsn(
					Opcodes.CHECKCAST,
					Type.getInternalName(AvailObject::class.java))
			}
			else ->
			{
				// The value could be either a type or a non-type.
				AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOnMethod
					.generateCall(method)
				// Strengthen to AvailObject
				method.visitTypeInsn(
					Opcodes.CHECKCAST, Type.getInternalName(AvailObject::class.java))
			}
		}
		// [type]
		translator.store(method, type.register())
	}

	/**
	 * Extract the register providing the value whose type is to be produced.
	 *
	 * @param instruction
	 *   The instruction to examine.
	 * @return
	 *   The [L2ReadBoxedOperand] supplying the value.
	 */
	@JvmStatic
	fun sourceValueOf(
		instruction: L2Instruction): L2ReadBoxedOperand
	{
		assert(instruction.operation === this)
		return instruction.operand(0)
	}
}
