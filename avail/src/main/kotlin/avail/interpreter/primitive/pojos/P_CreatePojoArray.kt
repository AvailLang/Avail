/*
 * P_CreatePojoArray.kt
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
package avail.interpreter.primitive.pojos

import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.pojos.PojoDescriptor.Companion.newPojo
import avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInteger
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.PojoTypeDescriptor
import avail.descriptor.types.PojoTypeDescriptor.Companion.mostGeneralPojoArrayType
import avail.descriptor.types.PojoTypeDescriptor.Companion.pojoArrayType
import avail.descriptor.types.TypeDescriptor
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive2
import java.lang.reflect.Array

/**
* **Primitive:** Create a [pojo&#32;array][PojoTypeDescriptor] that stores and
 * answers elements of the specified Avail [type][TypeDescriptor] and has the
 * specified [length][IntegerDescriptor].
 */
@Suppress("unused")
object P_CreatePojoArray : Primitive2(CannotFail, CanInline)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val elementType = arg1
		val length = arg2

		availLoaderOrNull()?.statementCanBeSummarized(false)

		val pojoType = pojoArrayType(elementType, singleInteger(length))
		val array = Array.newInstance(
			elementType.marshalToJava(null) as Class<*>, length.extractInt)
		return newPojo(identityPojo(array), pojoType)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				anyMeta,
				wholeNumbers),
			mostGeneralPojoArrayType())
}
