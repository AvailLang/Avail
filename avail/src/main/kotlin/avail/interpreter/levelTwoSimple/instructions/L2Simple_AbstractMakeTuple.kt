/*
 * L2Simple_AbstractMakeTuple.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.interpreter.levelTwoSimple.instructions

import avail.descriptor.tuples.A_Tuple.Companion.tupleCodePointAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.generateStringFromCodePoints
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i64
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u4
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u8
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwoSimple.L2SimpleTranslator
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.Write

/**
 * This is an abstract class for tuple-building instructions.
 */
abstract class L2Simple_AbstractMakeTuple(
	nextOffset: Offset,
	val elements: ReadArray,
	val tuple: Write
): L2SimpleInstruction(nextOffset)
{
	val tupleSize = elements.values.size

	companion object
	{
		fun L2SimpleTranslator.createMakeTuple(
			nextOffset: Offset = Offset.NEXT,
			elements: ReadArray,
			elementRestrictions: List<TypeRestriction>,
			elementTypes: List<A_Type>,
			out: Write
		): Unit
		{
			if (elementRestrictions.all(TypeRestriction::isConstant))
			{
				// The arguments are all constant.  Create a constant tuple.
				var tupleValue = tupleFromList(elementRestrictions.map {
					it.constantOrNull!!
				})
				if (tupleValue.isString)
				{
					// Rebuild it with a string representation.
					tupleValue = generateStringFromCodePoints(
						tupleValue.tupleSize
					) { i -> tupleValue.tupleCodePointAt(i) }
				}
				tupleValue = tupleValue.makeShared()
				+L2Simple_MoveConstant(
					value = tupleValue,
					to = out)
				return
			}
			val elementType =
				elementTypes.fold(bottom) { a, b -> a.typeUnion(b) }
			val size = elements.size
			assert(size > 0)
			when
			{
				elementType.isSubtypeOf(i64) -> when
				{
					elementType.isSubtypeOf(u4) ->
						+L2Simple_MakeNybbleTupleN(nextOffset, elements, out)
					elementType.isSubtypeOf(u8) ->
						+L2Simple_MakeByteTupleN(nextOffset, elements, out)
					elementType.isSubtypeOf(i32) ->
						+L2Simple_MakeIntTupleN(nextOffset, elements, out)
					else ->
						+L2Simple_MakeLongTupleN(nextOffset, elements, out)
				}
				elementType.isSubtypeOf(Types.CHARACTER()) ->
					+L2Simple_MakeCharacterTupleN(nextOffset, elements, out)
				size == 1 -> +L2Simple_MakeTuple1(nextOffset, elements, out)
				size == 2 -> +L2Simple_MakeTuple2(nextOffset, elements, out)
				size == 3 -> +L2Simple_MakeTuple3(nextOffset, elements, out)
				size == 4 -> +L2Simple_MakeTuple4(nextOffset, elements, out)
				else -> +L2Simple_MakeTupleN(nextOffset, elements, out)
			}
		}
	}
}
