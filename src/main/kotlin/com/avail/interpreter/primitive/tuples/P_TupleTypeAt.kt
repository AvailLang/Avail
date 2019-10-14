/*
 * P_TupleTypeAt.java
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

import com.avail.descriptor.*
import com.avail.descriptor.BottomTypeDescriptor.bottom
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.InstanceMetaDescriptor.instanceMeta
import com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.TupleTypeDescriptor.tupleMeta
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * **Primitive:** Answer the [ type][TypeDescriptor] for the given element of [instances][TupleDescriptor] of
 * the given [tuple type][TupleTypeDescriptor]. Answer
 * [bottom][BottomTypeDescriptor] if out of range.
 */
object P_TupleTypeAt : Primitive(2, CannotFail, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val tupleType = interpreter.argument(0)
		val index = interpreter.argument(1)
		return if (!index.isInt)
		{
			interpreter.primitiveSuccess(bottom())
		}
		else interpreter.primitiveSuccess(
			tupleType.typeAtIndex(index.extractInt()))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val tupleMeta = argumentTypes[0]
		val indexType = argumentTypes[1]

		val tupleType = tupleMeta.instance()
		val minIndex = indexType.lowerBound()
		val maxIndex = indexType.upperBound()
		if (minIndex.isInt)
		{
			val elementTypeBound = tupleType.unionOfTypesAtThrough(
				minIndex.extractInt(),
				if (maxIndex.isInt)
					maxIndex.extractInt()
				else
					Integer.MAX_VALUE)
			return instanceMeta(elementTypeBound)
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				tupleMeta(),
				naturalNumbers()),
			anyMeta())
	}

}