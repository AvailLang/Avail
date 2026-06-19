/*
 * P_SetSize.kt
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
package avail.interpreter.primitive.sets

import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Set.Companion.setSize
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.sizeRange
import avail.descriptor.representation.A_Type.Companion.typeIntersection
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i31
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.SetTypeDescriptor.Companion.mostGeneralSetType
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive1

/**
 * **Primitive:** Answer the size of the [set][SetDescriptor].
 */
@Suppress("unused")
object P_SetSize : Primitive1(CannotFail, CanFold, CanInline)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val set = arg1
		return fromInt(set.setSize)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(mostGeneralSetType()),
			wholeNumbers)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type = argumentTypes[0].sizeRange.typeIntersection(i31)

	override val canDestroyArguments get() = false
}
