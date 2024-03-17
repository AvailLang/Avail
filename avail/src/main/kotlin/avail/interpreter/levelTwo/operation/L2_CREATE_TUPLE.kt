/*
 * L2_CREATE_TUPLE.kt
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

import avail.descriptor.character.A_Character.Companion.codePoint
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ByteStringDescriptor.Companion.createUninitializedByteStringMethod
import avail.descriptor.tuples.ByteTupleDescriptor.Companion.createUninitializedByteTupleMethod
import avail.descriptor.tuples.IntTupleDescriptor.Companion.createUninitializedIntTupleMethod
import avail.descriptor.tuples.LongTupleDescriptor.Companion.createUninitializedLongTupleMethod
import avail.descriptor.tuples.NybbleTupleDescriptor.Companion.createUninitializedNybbleTupleMethod
import avail.descriptor.tuples.ObjectTupleDescriptor
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple1Method
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple2Method
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple3Method
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple4Method
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple5Method
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArrayMethod
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.tupleAtPuttingMethod
import avail.descriptor.tuples.TwoByteStringDescriptor.Companion.createUninitializedTwoByteStringMethod
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i64
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u4
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u8
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED_VECTOR
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_BOXED
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.optimizer.L2Generator
import avail.optimizer.jvm.JVMTranslator
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
		assert(this == instruction.operation)
		val values = instruction.operand<L2ReadBoxedVectorOperand>(0)
		val tuple = instruction.operand<L2WriteBoxedOperand>(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(tuple.registerString())
		builder.append(" ← ")
		builder.append(values.elements)
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

		val elements = values.elements
		val size = elements.size

		// Special cases for small tuples
		assert(size > 0) {
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
								instances.any { c -> c.codePoint > 255 }
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
			unionType.isSubtypeOf(i64) ->
			{
				// It'll be a numeric tuple that we're able to optimize. Call
				// the appropriate operation to create an uninitialized tuple
				// with the best representation.
				translator.intConstant(method, size)
				// :: size
				when
				{
					unionType.isSubtypeOf(u4) ->
						createUninitializedNybbleTupleMethod.generateCall(
							method)
					unionType.isSubtypeOf(u8) ->
						createUninitializedByteTupleMethod.generateCall(method)
					unionType.isSubtypeOf(i32) ->
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
		tupleReg: L2ReadOperand<BOXED_KIND>,
		index: Int,
		generator: L2Generator
	): L2ReadBoxedOperand
	{
		val instruction = tupleReg.definition().instruction
		val values = instruction.operand<L2ReadBoxedVectorOperand>(0)
		// val tuple = instruction.operand<L2WriteBoxedOperand>(1)

		return values.elements[index - 1]
	}
}
