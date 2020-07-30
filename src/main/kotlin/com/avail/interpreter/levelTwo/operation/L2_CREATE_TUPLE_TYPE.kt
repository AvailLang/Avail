/*
 * L2_CREATE_TUPLE_TYPE.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.InstanceMetaDescriptor
import com.avail.descriptor.types.TupleTypeDescriptor
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor

/**
 * Create a fixed sized [tuple&#32;type][TupleTypeDescriptor] from the
 * [types][A_Type] in the specified registers.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object L2_CREATE_TUPLE_TYPE : L2Operation(
	L2OperandType.READ_BOXED_VECTOR.named("element types"),
	L2OperandType.WRITE_BOXED.named("tuple type"))
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator)
	{
		val types =
			instruction.operand<L2ReadBoxedVectorOperand>(0)
		val tupleType =
			instruction.operand<L2WriteBoxedOperand>(1)
		val elements = types.elements()
		if (registerSet.allRegistersAreConstant(elements))
		{
			// The types are all constants, so create the tuple type statically.
			val constants =
				elements.map { registerSet.constantAt(it.register()) }
			val newTupleType =
				TupleTypeDescriptor.tupleTypeForTypes(constants)
			newTupleType.makeImmutable()
			registerSet.constantAtPut(
				tupleType.register(), newTupleType, instruction)
		}
		else
		{
			val newTypes = elements.map {
				if (registerSet.hasTypeAt(it.register()))
				{
					val meta = registerSet.typeAt(it.register())
					if (meta.isInstanceMeta)
					{
						meta.instance()
					}
					else
					{
						ANY.o
					}
				}
				else
				{
					ANY.o
				}
			}
			val newTupleType = TupleTypeDescriptor.tupleTypeForTypes(newTypes)
			val newTupleMeta = InstanceMetaDescriptor.instanceMeta(newTupleType)
			newTupleMeta.makeImmutable()
			registerSet.removeConstantAt(tupleType.register())
			registerSet.typeAtPut(
				tupleType.register(), newTupleMeta, instruction)
		}
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val types =
			instruction.operand<L2ReadBoxedVectorOperand>(0)
		val tupleType =
			instruction.operand<L2WriteBoxedOperand>(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(tupleType.registerString())
		builder.append(" ← ")
		builder.append(types.elements())
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val types =
			instruction.operand<L2ReadBoxedVectorOperand>(0)
		val tupleType =
			instruction.operand<L2WriteBoxedOperand>(1)

		// :: tupleType = TupleTypeDescriptor.tupleTypeForTypes(types);
		translator.objectArray(method, types.elements(), A_Type::class.java)
		TupleTypeDescriptor.tupleTypesForTypesArrayMethod.generateCall(method)
		translator.store(method, tupleType.register())
	}
}
