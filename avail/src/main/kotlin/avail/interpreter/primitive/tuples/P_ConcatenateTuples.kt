/*
 * P_ConcatenateTuples.kt
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
package avail.interpreter.primitive.tuples

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.greaterThan
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.A_Number.Companion.plusCanDestroy
import avail.descriptor.numbers.A_Number.Companion.timesCanDestroy
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.concatenateTuplesCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.defaultType
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeTuple
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.ConcatenatedTupleTypeDescriptor.Companion.concatenatingAnd
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restriction
import avail.interpreter.levelTwo.operation.tuples.L2_APPEND_TO_TUPLE
import avail.interpreter.levelTwo.operation.tuples.L2_CONCATENATE_TUPLES
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive1
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.utility.notNullAnd

/**
 * **Primitive:** Concatenate a [tuple][TupleDescriptor] of tuples together into
 * a single tuple.
 */
@Suppress("unused")
object P_ConcatenateTuples : Primitive1(CannotFail, CanFold, CanInline)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val tuples = arg1
		return tuples.concatenateTuplesCanDestroy(true)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(zeroOrMoreOf(mostGeneralTupleType)),
			mostGeneralTupleType)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>): A_Type
	{
		val tuplesType = argumentTypes[0]

		val tuplesSizes = tuplesType.sizeRange
		val lowerBound = tuplesSizes.lowerBound
		val upperBound = tuplesSizes.upperBound
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
			val bound = lowerBound.extractInt
			if (bound == 0)
			{
				return instanceType(emptyTuple)
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
		if (tuplesType.typeTuple.tupleSize == 0)
		{
			// The outer tuple type is homogeneous.
			val innerTupleType = tuplesType.defaultType
			if (innerTupleType.typeTuple.tupleSize == 0)
			{
				// The inner tuple type is also homogeneous.
				val innerSizes = innerTupleType.sizeRange
				val minSize = tuplesSizes.lowerBound.timesCanDestroy(
					innerSizes.lowerBound, false)
				val maxSize = tuplesSizes.upperBound.timesCanDestroy(
					innerSizes.upperBound, false)
				val newSizeRange = integerRangeType(
					minSize, true, maxSize.plusCanDestroy(one, true), false)
				return tupleTypeForSizesTypesDefaultType(
					newSizeRange, emptyTuple, innerTupleType.defaultType)
			}
		}
		// Too tricky to bother narrowing.
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
	}

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		assert(arguments.size == 1)
		val tupleOfTuplesReg = arguments[0]

		val tupleOfTuplesType = tupleOfTuplesReg.type()
		val range = tupleOfTuplesType.sizeRange
		val size = range.lowerBound
		if (!size.isInt || !range.upperBound.equals(size)) return false
		val sizeInt = size.extractInt
		var elementRegs = explodeTupleIfPossible(
			tupleOfTuplesReg,
			tupleOfTuplesType.tupleOfTypesFromTo(1, sizeInt).toList())
		elementRegs ?: return false
		// Strip out any always-empty tuples.
		elementRegs = elementRegs.filter {
			!it.constantOrNull.notNullAnd { tupleSize == 0 }
		}
		// Statically concatenate adjacent constants, since tuple concatenation
		// associates.
		var currentTuple: A_Tuple? = null
		val adjustedSources = mutableListOf<L2ReadBoxedOperand>()
		for (source in elementRegs)
		{
			val constant = source.constantOrNull
			currentTuple = when
			{
				constant === null ->
				{
					if (currentTuple !== null)
					{
						adjustedSources.add(boxedConstant(currentTuple))
					}
					adjustedSources.add(source)
					null
				}
				currentTuple === null -> constant
				else -> currentTuple.concatenateWith(constant, false)
			}
		}
		if (currentTuple !== null)
		{
			// Deal with the final one.
			adjustedSources.add(boxedConstant(currentTuple))
		}
		if (adjustedSources.size == 2
			&& adjustedSources[1].type().sizeRange.run {
				upperBound.equalsInt(1) && lowerBound.equalsInt(1)
			})
		{
			// We can replacce it with a simple append.  This is a pattern that
			// shows up in Avail code, where an element needs to be concatenated
			// to a tuple, which looks like "oldTuple ++ <newElement>".
			val (inputTuple, newElementTuple) = adjustedSources
			val resultWrite = boxedWriteTemp(
				"left tuple",
				boxedRestrictionForType(
					concatenatingAnd(
						inputTuple.type(),
						newElementTuple.type())))
			val elementTemp = newTemp("value to append")
			extractTupleElement(newElementTuple, 1, setOf(elementTemp))
			+L2_APPEND_TO_TUPLE(
				inputTuple,
				readBoxed(elementTemp),
				resultWrite)
			callSiteHelper.useAnswer(
				readBoxed(resultWrite),
				// Append to a tuple can't endanger escaped locals.
				false)
			return true
		}
		when (adjustedSources.size)
		{
			0 -> callSiteHelper.useAnswer(boxedConstant(emptyTuple), false)
			1 -> callSiteHelper.useAnswer(adjustedSources[0], false)
			else ->
			{
				val guaranteedType = returnTypeGuaranteedByVM(
					rawFunction, argumentTypes)
				val write = boxedWriteTemp(
					"concatenated",
					restriction(guaranteedType, null))
				+L2_CONCATENATE_TUPLES(
					L2ReadBoxedVectorOperand(adjustedSources),
					write)
				callSiteHelper.useAnswer(readBoxed(write), false)
			}
		}
		return true
	}
}
