/*
 * L2_GET_TYPE.kt
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

import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor
import com.avail.descriptor.types.InstanceMetaDescriptor
import com.avail.descriptor.types.InstanceTypeDescriptor
import com.avail.descriptor.types.TypeDescriptor
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.function.Consumer

/**
 * Extract the [exact type][InstanceTypeDescriptor] of an object in a register,
 * writing the type to another register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_GET_TYPE : L2Operation(
	L2OperandType.READ_BOXED.`is`("value"),
	L2OperandType.WRITE_BOXED.`is`("value's type"))
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator)
	{
		val valueReg = instruction.operand<L2ReadBoxedOperand>(0)
		val typeReg = instruction.operand<L2WriteBoxedOperand>(1)
		registerSet.removeConstantAt(typeReg.register())
		if (registerSet.hasTypeAt(valueReg.register()))
		{
			val type = registerSet.typeAt(valueReg.register())
			// Apply the rule of metacovariance. It says that given types T1
			// and T2, T1 <= T2 implies T1 type <= T2 type. It is guaranteed
			// true for all types in Avail.
			val meta = InstanceMetaDescriptor.instanceMeta(type)
			registerSet.typeAtPut(typeReg.register(), meta, instruction)
		}
		else
		{
			registerSet.typeAtPut(
				typeReg.register(),
				InstanceMetaDescriptor.topMeta(),
				instruction)
		}
		if (registerSet.hasConstantAt(valueReg.register())
			&& !registerSet.constantAt(valueReg.register()).isType)
		{
			registerSet.constantAtPut(
				typeReg.register(),
				AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn(
					registerSet.constantAt(valueReg.register())),
				instruction)
		}
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: Consumer<Boolean>)
	{
		assert(this == instruction.operation())
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
		if (value.restriction().containedByType(TypeDescriptor.Types.NONTYPE.o()))
		{
			// The value will *never* be a type.
			InstanceTypeDescriptor.instanceTypeMethod.generateCall(method)
		}
		else if (value.restriction().containedByType(InstanceMetaDescriptor.topMeta()))
		{
			// The value will *always* be a type. Strengthen to AvailObject.
			InstanceMetaDescriptor.instanceMetaMethod.generateCall(method)
			method.visitTypeInsn(
				Opcodes.CHECKCAST, Type.getInternalName(AvailObject::class.java))
		}
		else
		{
			// The value could be either a type or a non-type.
			AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOnMethod
				.generateCall(method)
			// Strengthen to AvailObject
			method.visitTypeInsn(
				Opcodes.CHECKCAST, Type.getInternalName(AvailObject::class.java))
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
	@kotlin.jvm.JvmStatic
	fun sourceValueOf(
		instruction: L2Instruction): L2ReadBoxedOperand
	{
		assert(instruction.operation() === this)
		return instruction.operand(0)
	}
}