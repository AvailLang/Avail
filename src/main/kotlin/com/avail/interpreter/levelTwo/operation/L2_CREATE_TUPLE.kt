/*
 * L2_CREATE_TUPLE.kt
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

import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArrayMethod
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.InstanceTypeDescriptor
import com.avail.descriptor.types.TupleTypeDescriptor
import com.avail.descriptor.types.TypeDescriptor
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.*

/**
 * Create a [tuple][TupleDescriptor] from the [objects][AvailObject] in the
 * specified registers.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_CREATE_TUPLE : L2Operation(
	L2OperandType.READ_BOXED_VECTOR.named("elements"),
	L2OperandType.WRITE_BOXED.named("tuple"))
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator)
	{
		val values =
			instruction.operand<L2ReadBoxedVectorOperand>(0)
		val tuple =
			instruction.operand<L2WriteBoxedOperand>(1)
		val size = values.elements().size
		val sizeRange = fromInt(size).kind()
		val types: MutableList<A_Type> = ArrayList(size)
		for (element in values.elements())
		{
			if (registerSet.hasTypeAt(element.register()))
			{
				types.add(registerSet.typeAt(element.register()))
			}
			else
			{
				types.add(TypeDescriptor.Types.ANY.o())
			}
		}
		val tupleType =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				sizeRange, tupleFromList(types), bottom())
		tupleType.makeImmutable()
		registerSet.removeConstantAt(tuple.register())
		registerSet.typeAtPut(
			tuple.register(),
			tupleType,
			instruction)
		if (registerSet.allRegistersAreConstant(values.elements()))
		{
			val constants: MutableList<AvailObject> = ArrayList(size)
			for (element in values.elements())
			{
				constants.add(registerSet.constantAt(element.register()))
			}
			val newTuple =
				tupleFromList(constants)
			newTuple.makeImmutable()
			assert(newTuple.isInstanceOf(tupleType))
			registerSet.typeAtPut(
				tuple.register(),
				InstanceTypeDescriptor.instanceType(newTuple),
				instruction)
			registerSet.constantAtPut(
				tuple.register(), newTuple, instruction)
		}
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val values =
			instruction.operand<L2ReadBoxedVectorOperand>(0)
		val tuple =
			instruction.operand<L2WriteBoxedOperand>(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(tuple.registerString())
		builder.append(" ← ")
		builder.append(values.elements())
	}

	/**
	 * Generated code uses:
	 *
	 *  * [ObjectTupleDescriptor.tupleFromArray]
	 *  * [TupleDescriptor.emptyTuple]
	 *  * [ObjectTupleDescriptor.tuple]
	 *  * [ObjectTupleDescriptor.tuple]
	 *  * [ObjectTupleDescriptor.tuple]
	 *  * [ObjectTupleDescriptor.tuple]
	 *  * [ObjectTupleDescriptor.tuple]
	 *
	 */
	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val values =
			instruction.operand<L2ReadBoxedVectorOperand>(0)
		val tuple =
			instruction.operand<L2WriteBoxedOperand>(1)
		val elements = values.elements()
		val size = elements.size
		if (size <= 5)
		{
			// Special cases for small tuples
			if (size == 0)
			{
				// :: destination = theEmptyTupleLiteral;
				translator.literal(method, TupleDescriptor.emptyTuple())
				translator.store(method, tuple.register())
				return
			}
			// :: destination = TupleDescriptor.tuple(element1...elementN);
			for (i in 0 until size)
			{
				translator.load(method, elements[i].register())
			}
			val callSignature =
				Array(size)
				{
					A_BasicObject::class.java
				}
			val tupleMethod = CheckedMethod.staticMethod(
				ObjectTupleDescriptor::class.java,
				"tuple",
				A_Tuple::class.java,
				*callSignature)
			tupleMethod.generateCall(method)
			method.visitTypeInsn(
				Opcodes.CHECKCAST,
				Type.getInternalName(AvailObject::class.java))
			translator.store(method, tuple.register())
			return
		}
		// :: destination = TupleDescriptor.tupleFromArray(elements);
		translator.objectArray(method, elements, A_BasicObject::class.java)
		tupleFromArrayMethod.generateCall(method)
		method.visitTypeInsn(
			Opcodes.CHECKCAST, Type.getInternalName(AvailObject::class.java))
		translator.store(method, tuple.register())
	}

	/**
	 * Given an [L2Instruction] using this operation, extract the list of
	 * registers that supply the elements of the tuple.
	 *
	 * @param instruction
	 *   The tuple creation instruction to examine.
	 * @return
	 *   The instruction's [List] of [L2ReadBoxedOperand]s that supply the tuple
	 *   elements.
	 */
	@kotlin.jvm.JvmStatic
	fun tupleSourceRegistersOf(
		instruction: L2Instruction): List<L2ReadBoxedOperand>
	{
		assert(instruction.operation() === this)
		val vector =
			instruction.operand<L2ReadBoxedVectorOperand>(0)
		return vector.elements()
	}
}
