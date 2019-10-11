/*
 * P_ConcatenateTuples.java
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

import com.avail.descriptor.A_Number
import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Tuple
import com.avail.descriptor.A_Type
import com.avail.descriptor.TupleDescriptor
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import com.avail.descriptor.ConcatenatedTupleTypeDescriptor.concatenatingAnd
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceTypeDescriptor.instanceType
import com.avail.descriptor.IntegerDescriptor.fromInt
import com.avail.descriptor.IntegerDescriptor.one
import com.avail.descriptor.IntegerRangeTypeDescriptor.integerRangeType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType
import com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType
import com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail

/**
 * **Primitive:** Concatenate a [ tuple][TupleDescriptor] of tuples together into a single tuple.
 */
object P_ConcatenateTuples : Primitive(1, CannotFail, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(1)
		val tuples = interpreter.argument(0)
		return interpreter.primitiveSuccess(
			tuples.concatenateTuplesCanDestroy(true))
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(zeroOrMoreOf(mostGeneralTupleType())),
			mostGeneralTupleType())
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val tuplesType = argumentTypes[0]

		val tuplesSizes = tuplesType.sizeRange()
		val lowerBound = tuplesSizes.lowerBound()
		val upperBound = tuplesSizes.upperBound()
		if (lowerBound.equals(upperBound))
		{
			// A fixed number of subtuples.  Must be finite, of course.
			if (lowerBound.greaterThan(fromInt(20)))
			{
				// Too expensive to compute here.
				return super.returnTypeGuaranteedByVM(
					rawFunction, argumentTypes)
			}
			// A (reasonably small) collection of tuple types.
			assert(lowerBound.isInt)
			val bound = lowerBound.extractInt()
			if (bound == 0)
			{
				return instanceType(emptyTuple())
			}
			var concatenatedType = tuplesType.typeAtIndex(1)
			for (i in 2 .. bound)
			{
				concatenatedType = concatenatingAnd(
					concatenatedType, tuplesType.typeAtIndex(i))
			}
			return concatenatedType
		}
		// A variable number of subtuples.  See if it's homogeneous.
		if (tuplesType.typeTuple().tupleSize() == 0)
		{
			// The outer tuple type is homogeneous.
			val innerTupleType = tuplesType.defaultType()
			if (innerTupleType.typeTuple().tupleSize() == 0)
			{
				// The inner tuple type is also homogeneous.
				val innerSizes = innerTupleType.sizeRange()
				val minSize = tuplesSizes.lowerBound().timesCanDestroy(
					innerSizes.lowerBound(), false)
				val maxSize = tuplesSizes.upperBound().timesCanDestroy(
					innerSizes.upperBound(), false)
				val newSizeRange = integerRangeType(
					minSize,
					true,
					maxSize.plusCanDestroy(one(), true),
					false)
				return tupleTypeForSizesTypesDefaultType(
					newSizeRange,
					emptyTuple(),
					innerTupleType.defaultType())
			}
		}
		// Too tricky to bother narrowing.
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
	}

}