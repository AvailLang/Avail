/*
 * P_CharacterFromCodePoint.kt
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
package avail.interpreter.primitive.characters

import avail.descriptor.character.CharacterDescriptor
import avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.characterCodePoints
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.CHARACTER
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter

/**
* **Primitive:** Convert a [code&#32;point][IntegerDescriptor] into a
 * [character][CharacterDescriptor].
 */
@Suppress("unused")
object P_CharacterFromCodePoint : Primitive(1, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val codePoint = interpreter.argument(0)
		return interpreter.primitiveSuccess(fromCodePoint(codePoint.extractInt))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>
	): A_Type
	{
		val intType = argumentTypes[0]
		if (intType.equals(characterCodePoints)) return CHARACTER.o

		var chars = emptySet
		if (intType.isEnumeration)
		{
			for (intValue in intType.instances)
			{
				chars = chars.setWithElementCanDestroy(
					fromCodePoint(intValue.extractInt), true)
			}
		}
		else
		{
			// intType is a subrange of characterCodePoints, so it's always
			// bound-inclusive and fits in an Int.
			val low = intType.lowerBound.extractInt
			val high = intType.upperBound.extractInt
			for (intValue in low .. high)
			{
				chars = chars.setWithElementCanDestroy(
					fromCodePoint(intValue), true)
			}
		}
		return enumerationWith(chars)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				characterCodePoints),
			CHARACTER.o)
}
