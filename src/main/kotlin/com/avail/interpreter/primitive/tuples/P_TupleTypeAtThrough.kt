/*
 * P_TupleTypeAtThrough.kt
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
package com.avail.interpreter.primitive.tuples

import com.avail.descriptor.A_Type
import com.avail.descriptor.BottomTypeDescriptor
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InfinityDescriptor.positiveInfinity
import com.avail.descriptor.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.IntegerDescriptor.zero
import com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive
import com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.TupleTypeDescriptor
import com.avail.descriptor.TupleTypeDescriptor.tupleMeta
import com.avail.descriptor.TypeDescriptor
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * **Primitive:** Answer the [type][TypeDescriptor] that is the union of the
 * types within the given range of indices of the given [tuple
 * type][TupleTypeDescriptor]. Answer [bottom][BottomTypeDescriptor] if all the
 * indices are out of range.
 */
object P_TupleTypeAtThrough : Primitive(3, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val tupleType = interpreter.argument(0)
		val startIndex = interpreter.argument(1)
		val endIndex = interpreter.argument(2)
		val startInt = if (startIndex.isInt) startIndex.extractInt()
		else Integer.MAX_VALUE
		val endInt = if (endIndex.isInt) endIndex.extractInt()
		else Integer.MAX_VALUE
		return interpreter.primitiveSuccess(
			tupleType.unionOfTypesAtThrough(startInt, endInt))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				tupleMeta(),
				naturalNumbers(),
				inclusive(
					zero(),
					positiveInfinity())),
			anyMeta())
}