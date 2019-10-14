/*
 * P_CreatePojoArray.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.pojos

import com.avail.descriptor.A_Type
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.IntegerDescriptor
import com.avail.descriptor.IntegerRangeTypeDescriptor.singleInteger
import com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PojoDescriptor.newPojo
import com.avail.descriptor.PojoTypeDescriptor.mostGeneralPojoArrayType
import com.avail.descriptor.PojoTypeDescriptor.pojoArrayType
import com.avail.descriptor.RawPojoDescriptor.identityPojo
import com.avail.descriptor.TypeDescriptor
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import java.lang.reflect.Array

/**
 * **Primitive:** Create a [ ] that stores and answers elements of the
 * specified [Avail type][TypeDescriptor] and has the specified
 * [length][IntegerDescriptor].
 */
object P_CreatePojoArray : Primitive(2, CannotFail, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val elementType = interpreter.argument(0)
		val length = interpreter.argument(1)

		val loader = interpreter.availLoaderOrNull()
		loader?.statementCanBeSummarized(false)

		val pojoType = pojoArrayType(
			elementType, singleInteger(length))
		val array = Array.newInstance(
			elementType.marshalToJava(null) as Class<*>?, length.extractInt())
		val pojo = newPojo(identityPojo(array), pojoType)
		return interpreter.primitiveSuccess(pojo)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				anyMeta(),
				wholeNumbers()),
			mostGeneralPojoArrayType())
	}

}