/*
 * L2_CREATE_TUPLE.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.interpreter.levelTwo.operation.tuples

import avail.descriptor.character.A_Character.Companion.codePoint
import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ByteStringDescriptor
import avail.descriptor.tuples.ByteTupleDescriptor
import avail.descriptor.tuples.IntTupleDescriptor
import avail.descriptor.tuples.LongTupleDescriptor
import avail.descriptor.tuples.NybbleTupleDescriptor
import avail.descriptor.tuples.ObjectTupleDescriptor
import avail.descriptor.tuples.StringDescriptor
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TwentyOneBitStringDescriptor
import avail.descriptor.tuples.TwoByteStringDescriptor
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.BottomTypeDescriptor
import avail.descriptor.types.IntegerRangeTypeDescriptor
import avail.descriptor.types.PrimitiveTypeDescriptor
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.L2Generator
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticBoxedValue
import avail.utility.Strings.increaseIndentation
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
class L2_CREATE_TUPLE(
	var elements: L2ReadBoxedVectorOperand,
	var tuple: L2WriteBoxedOperand
): L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(tuple.registerString())
		append(" ← <")
		elements.elements.withIndex().joinTo(this, ",") { (i, read) ->
			"\n\t#${i+1}: ${increaseIndentation(read.registerString(), 1)}"
		}
		append("\n>")
	}

	override fun extractTupleElement(
		tupleRead: L2ReadBoxedOperand,
		index: Int,
		destinationSemanticValues: Set<L2SemanticBoxedValue>,
		generator: L2Generator)
	{
		val instruction = tupleRead.definition().instruction
		val values = instruction.operand<L2ReadBoxedVectorOperand>(0)
		generator.moveBoxedRegister(
			values.elements[index - 1].semanticValue(),
			destinationSemanticValues)
	}

	/**
	 * Generated code uses:
	 *
	 *  * [TupleDescriptor.emptyTuple] (zero arguments)
	 *  * [ObjectTupleDescriptor.tuple] (1..5 arguments)
	 *  * [ObjectTupleDescriptor.tupleFromArray] (>5 arguments)
	 *
	 */
	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		val size = elements.elements.size

		// Special cases for small tuples
		assert(size > 0) {
			"Empty tuple should have been replaced via generateReplacement()"
		}

		// Special cases for characters and integers
		val unionType = elements.elements.fold(BottomTypeDescriptor.bottom) { t, read ->
			t.typeUnion(read.type())
		}
		when
		{
			unionType.isSubtypeOf(PrimitiveTypeDescriptor.Types.CHARACTER()) ->
			{
				val maxCodepoint = elements.elements
					.map(L2ReadBoxedOperand::type)
					.filter(A_Type::isEnumeration)
					.maxOfOrNull { it.instances.maxOf { c -> c.codePoint} }
				val constantEntries = elements.elements.map { read ->
					read.type().run {
						if (instanceCount.equals(IntegerDescriptor.one)) instance
						else null
					}
				}
				if (constantEntries.any { it !== null })
				{
					// Some of the elements are constant.  Pre-build a constant
					// string with those values filled in, and dummy characters
					// for the rest, then generate code to push that string and
					// perform necessary updates on it (the first update will
					// clone it as mutable).
					val template = StringDescriptor
						.generateStringFromCodePoints(size) {
							constantEntries[it]?.codePoint ?: 0
						}.makeShared()
					translator.loadLiteralObject(method, template)
					// :: template-string
					elements.elements.forEachIndexed { zeroIndex, read ->
						if (constantEntries[zeroIndex] === null)
						{
							// Replace the dummy character, cloning the template
							// and even changing its representation if needed.
							translator.intConstant(method, zeroIndex + 1)
							translator.load(method, read)
							TupleDescriptor.tupleAtPuttingMethod.generateCall(method)
						}
					}
					// :: string
					translator.store(method, tuple.register())
					return
				}
				// There weren't any literal character elements.
				translator.intConstant(method, size)
				// :: size
				if (maxCodepoint === null || maxCodepoint <= 0xFF)
				{
					// There are no enumeration character types present, or
					// they're all single-bytes. Guess that we're creating a
					// byte string, although it may have to be upgraded.
					ByteStringDescriptor.createUninitializedByteStringMethod.generateCall(method)
					// :: uninitiaalized-byte-string
				}
				else if (maxCodepoint <= 0xFFFF)
				{
					// A two-byte character may be present.
					TwoByteStringDescriptor.createUninitializedTwoByteStringMethod.generateCall(method)
					// :: uninitialized-two-byte-string
				}
				else
				{
					// One of the enumerations for an element indicated a
					// possible value beyond the 16-bit range.
					TwentyOneBitStringDescriptor.createUninitializedTwentyOneBitStringMethod.generateCall(
						method)
					// :: uninitialized-21-bit-string
				}
			}
			unionType.isSubtypeOf(IntegerRangeTypeDescriptor.i64) ->
			{
				// It'll be a numeric tuple that we're able to optimize. Call
				// the appropriate operation to create an uninitialized tuple
				// with the best representation.
				translator.intConstant(method, size)
				// :: size
				when
				{
					unionType.isSubtypeOf(IntegerRangeTypeDescriptor.u4) ->
						NybbleTupleDescriptor.createUninitializedNybbleTupleMethod.generateCall(
							method)
					unionType.isSubtypeOf(IntegerRangeTypeDescriptor.u8) ->
						ByteTupleDescriptor.createUninitializedByteTupleMethod.generateCall(method)
					unionType.isSubtypeOf(IntegerRangeTypeDescriptor.i32) ->
						IntTupleDescriptor.createUninitializedIntTupleMethod.generateCall(method)
					else ->
						LongTupleDescriptor.createUninitializedLongTupleMethod.generateCall(method)
				}
				// :: uninitialized-numeric-tuple
			}
			else ->
			{
				// Build a general object tuple.  First, push the elements.
				if (size <= 5)
				{
					elements.elements.forEach {
						translator.load(method, it)
					}
					// :: element1... elementN
				}
				when (size)
				{
					1 -> ObjectTupleDescriptor.tuple1Method.generateCall(method)
					2 -> ObjectTupleDescriptor.tuple2Method.generateCall(method)
					3 -> ObjectTupleDescriptor.tuple3Method.generateCall(method)
					4 -> ObjectTupleDescriptor.tuple4Method.generateCall(method)
					5 -> ObjectTupleDescriptor.tuple5Method.generateCall(method)
					else ->
					{
						// The elements are NOT already pushed.
						translator.objectArray(
							method,
							elements.elements,
							A_BasicObject::class.java)
						// :: initialized_array
						ObjectTupleDescriptor.tupleFromArrayMethod.generateCall(method)
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
		elements.elements.forEachIndexed { zeroIndex, read ->
			translator.intConstant(method, zeroIndex + 1)
			translator.load(method, read)
			TupleDescriptor.tupleAtPuttingMethod.generateCall(method)
		}
		// :: AvailObject
		translator.store(method, tuple.register())
	}
}
