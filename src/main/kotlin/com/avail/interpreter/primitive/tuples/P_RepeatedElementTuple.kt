/*
 * P_RepeatedElementTuple.java
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

import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Type
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive

import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceTypeDescriptor.instanceType
import com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.RepeatedElementTupleDescriptor.createRepeatedElementTuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType
import com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.exceptions.AvailErrorCode.E_EXCEEDS_VM_LIMIT
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Create a [ ].
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
object P_RepeatedElementTuple : Primitive(2, CanInline, CanFold)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(2)

		val size = interpreter.argument(0)
		val element = interpreter.argument(1)

		if (!size.isInt)
		{
			return interpreter.primitiveFailure(E_EXCEEDS_VM_LIMIT)
		}
		val sizeAsInt = size.extractInt()
		return interpreter.primitiveSuccess(
			createRepeatedElementTuple(
				sizeAsInt, element))
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				wholeNumbers(),
				ANY.o()),
			mostGeneralTupleType())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(set(E_EXCEEDS_VM_LIMIT))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		assert(argumentTypes.size == 2)

		val sizeType = argumentTypes[0]
		val elementType = argumentTypes[1]

		return if (sizeType.instanceCount().equalsInt(1) && elementType.instanceCount().equalsInt(1))
		{
			instanceType(
				createRepeatedElementTuple(
					sizeType.instance().extractInt(), elementType.instance()))
		}
		else tupleTypeForSizesTypesDefaultType(
			sizeType, emptyTuple(), elementType)
	}

}