/*
 * P_CreateTupleFromPojoArray.kt
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

package com.avail.interpreter.primitive.pojos

import com.avail.descriptor.A_Type
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.ObjectTupleDescriptor.generateObjectTupleFrom
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PojoTypeDescriptor
import com.avail.descriptor.PojoTypeDescriptor.mostGeneralPojoArrayType
import com.avail.descriptor.PojoTypeDescriptor.unmarshal
import com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType
import com.avail.descriptor.tuples.A_Tuple
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import java.lang.reflect.Array

/**
 * **Primitive:** Convert the specified
 * [pojo&#32;array][PojoTypeDescriptor.mostGeneralPojoArrayType] to a
 * [tuple][A_Tuple].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_CreateTupleFromPojoArray : Primitive(1, CanInline, CannotFail)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val array = interpreter.argument(0)
		// Hold a lock on the array while accessing it.
		val tuple = array.lock<A_Tuple> {
			val rawArray = array.rawPojo().javaObjectNotNull<Any>()
			generateObjectTupleFrom(Array.getLength(rawArray)) {
				unmarshal(
					Array.get(rawArray, it - 1),
					array.kind().contentType())
			}
		}
		return interpreter.primitiveSuccess(tuple)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralPojoArrayType()),
			mostGeneralTupleType())
}
