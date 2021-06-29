/*
 * L2_CREATE_TUPLE.kt
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
package com.avail.interpreter.levelTwo.operation

import com.avail.descriptor.character.A_Character.Companion.codePoint
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.tuples.ByteStringDescriptor.Companion.createUninitializedByteStringMethod
import com.avail.descriptor.tuples.ByteTupleDescriptor.Companion.createUninitializedByteTupleMethod
import com.avail.descriptor.tuples.IntTupleDescriptor.Companion.createUninitializedIntTupleMethod
import com.avail.descriptor.tuples.LongTupleDescriptor.Companion.createUninitializedLongTupleMethod
import com.avail.descriptor.tuples.NybbleTupleDescriptor.Companion.createUninitializedNybbleTupleMethod
import com.avail.descriptor.tuples.ObjectTupleDescriptor
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple1Method
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple2Method
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple3Method
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple4Method
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple5Method
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArrayMethod
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.Companion.tupleAtPuttingMethod
import com.avail.descriptor.tuples.TwoByteStringDescriptor.Companion.createUninitializedTwoByteStringMethod
import com.avail.descriptor.types.A_Type.Companion.instances
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.bytes
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int64
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.nybbles
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED_VECTOR
import com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Create a [tuple][TupleDescriptor] from the [objects][AvailObject] in the
 * specified registers.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_CREATE_TUPLE : L2Operation(
	READ_BOXED_VECTOR.named("elements"),
	WRITE_BOXED.named("tuple"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val values = instruction.operand<L2ReadBoxedVectorOperand>(0)
		val tuple = instruction.operand<L2WriteBoxedOperand>(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(tuple.registerString())
		builder.append(" ← ")
		builder.append(values.elements())
	}

	/**
	 * Generated code uses:
	 *
	 *  * [TupleDescriptor.emptyTuple] (zero arguments)
	 *  * [ObjectTupleDescriptor.tuple] (one argument)
	 *  * [ObjectTupleDescriptor.tuple] (two arguments)
	 *  * [ObjectTupleDescriptor.tuple] (three arguments)
	 *  * [ObjectTupleDescriptor.tuple] (four arguments)
	 *  * [ObjectTupleDescriptor.tuple] (five arguments)
	 *  * [ObjectTupleDescriptor.tupleFromArray] (>5 arguments)
	 *
	 */
	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val values = instruction.operand<L2ReadBoxedVectorOperand>(0)
		val tuple = instruction.operand<L2WriteBoxedOperand>(1)

		val elements = values.elements()
		val size = elements.size

		// Special cases for small tuples
		assert (size > 0) {
			"Empty tuple should have been replaced via generateReplacement()"
		}

		// Special cases for characters and integers
		val unionType = elements.fold(bottom) { t, read ->
			t.typeUnion(read.type())
		}
		when
		{
			unionType.isSubtypeOf(Types.CHARACTER.o) ->
			{
				translator.intConstant(method, size)
				// :: size
				if (elements.any { read ->
						read.type().run {
							isEnumeration &&
								instances.any { c -> c.codePoint() > 255 }
						}
					})
				{
					// At least one element type is an enumeration that contains
					// a non-Latin-1 character.  Not a perfect indicator by a
					// long shot, but probably a pretty good predictor that this
					// should start out as a two-byte-string.
					createUninitializedTwoByteStringMethod.generateCall(method)
					// :: uninitialized-two-byte-string
				}
				else
				{
					// Otherwise, just guess that it'll stay a byte-string.
					createUninitializedByteStringMethod.generateCall(method)
					// :: uninitialized-byte-string
				}
			}
			unionType.isSubtypeOf(int64) ->
			{
				// It'll be a numeric tuple that we're able to optimize. Call
				// the appropriate operation to create an uninitialized tuple
				// with the best representation.
				translator.intConstant(method, size)
				// :: size
				when
				{
					unionType.isSubtypeOf(nybbles) ->
						createUninitializedNybbleTupleMethod.generateCall(
							method)
					unionType.isSubtypeOf(bytes) ->
						createUninitializedByteTupleMethod.generateCall(method)
					unionType.isSubtypeOf(int32) ->
						createUninitializedIntTupleMethod.generateCall(method)
					else ->
						createUninitializedLongTupleMethod.generateCall(method)
				}
				// :: uninitialized-numeric-tuple
			}
			else ->
			{
				// Build a general object tuple.  First, push the elements.
				if (size <= 5)
				{
					elements.forEach { translator.load(method, it.register()) }
					// :: element1... elementN
				}
				when (size)
				{
					1 -> tuple1Method.generateCall(method)
					2 -> tuple2Method.generateCall(method)
					3 -> tuple3Method.generateCall(method)
					4 -> tuple4Method.generateCall(method)
					5 -> tuple5Method.generateCall(method)
					else ->
					{
						// The elements are NOT already pushed.
						translator.objectArray(
							method, elements, A_BasicObject::class.java)
						// :: initialized_array
						tupleFromArrayMethod.generateCall(method)
					}
				}
				// :: A_Tuple
				method.visitTypeInsn(
					Opcodes.CHECKCAST,
					Type.getInternalName(AvailObject::class.java))
				// :: AvailObject
				translator.store(method, tuple.register())
				return
			}
		}
		// :: an-uninitialized-tuple
		elements.forEachIndexed { zeroIndex, read ->
			translator.intConstant(method, zeroIndex + 1)
			translator.load(method, read.register())
			tupleAtPuttingMethod.generateCall(method)
		}
		// :: AvailObject
		translator.store(method, tuple.register())
	}

	override fun extractTupleElement(
		tupleReg: L2ReadBoxedOperand,
		index: Int,
		generator: L2Generator
	): L2ReadBoxedOperand
	{
		val instruction = tupleReg.definition().instruction()
		val values = instruction.operand<L2ReadBoxedVectorOperand>(0)
		// val tuple = instruction.operand<L2WriteBoxedOperand>(1)

		return values.elements[index - 1]
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
	fun tupleSourceRegistersOf(
		instruction: L2Instruction): List<L2ReadBoxedOperand>
	{
		assert(instruction.operation() === this)
		val vector = instruction.operand<L2ReadBoxedVectorOperand>(0)
		return vector.elements()
	}
}
