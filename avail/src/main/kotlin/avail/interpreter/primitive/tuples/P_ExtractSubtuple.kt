/*
 * P_ExtractSubtuple.kt
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
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.greaterThan
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.A_Number.Companion.noFailMinusCanDestroy
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeTuple
import avail.descriptor.types.A_Type.Companion.unionOfTypesAtThrough
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.interpreter.levelTwo.operation.NumericComparator
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.tuples.L2_TUPLE_SIZE
import avail.interpreter.levelTwo.operation.tuples.L2_TUPLE_SUBRANGE_NO_FAIL
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteMustFail
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive3
import avail.interpreter.primitive.numbers.P_Subtraction
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2GeneratorInterface.Companion.readTwoInts
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedInt
import avail.optimizer.values.L2SemanticValue.Companion.constant
import avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation
import kotlin.math.max
import kotlin.math.min

/**
 * **Primitive:** Extract a [subtuple][TupleDescriptor] with the given range of
 * elements.
 */
@Suppress("unused")
object P_ExtractSubtuple : Primitive3(CanFold, CanInline)
{
	override fun attempt3(
		interpreter: Interpreter,
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?
	{
		val tuple = arg1
		val start = arg2
		val end = arg3
		if (!start.isInt || !end.isInt)
		{
			return interpreter.fail(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		val startInt = start.extractInt
		val endInt = end.extractInt
		return when
		{
			startInt < 1 ->
				interpreter.fail(E_SUBSCRIPT_OUT_OF_BOUNDS)
			startInt > endInt + 1 ->
				interpreter.fail(E_SUBSCRIPT_OUT_OF_BOUNDS)
			endInt > tuple.tupleSize ->
				interpreter.fail(E_SUBSCRIPT_OUT_OF_BOUNDS)
			else -> tuple.copyTupleFromToCanDestroy(startInt, endInt, true)
		}
	}

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>
	): Fallibility
	{
		val (tupleType, startIndexType, endIndexType) = argumentTypes
		return checkFallibility(
			tupleType.sizeRange, startIndexType, endIndexType)
	}

	/**
	 * Check whether the given tuple size range can have a slice extracted with
	 * start and end indices with the given range.  Answer a [Fallibility] that
	 * indicates whether the extraction would always, sometimes, or never fail.
	 *
	 * @param sizeRange
	 *   The integer range type describing the possible tuple sizes.
	 * @param startIndexType
	 *   An integer range type bounding the possible slice starting index.
	 * @param endIndexType
	 *   An integer range type bounding the possible slice ending index.
	 * @return
	 *   The fallibility guarantee of the tuple extraction attempt.
	 */
	fun checkFallibility(
		sizeRange: A_Type,
		startIndexType: A_Type,
		endIndexType: A_Type
	): Fallibility
	{
		val sizeLower = sizeRange.lowerBound.run {
			if (!isInt) return CallSiteMustFail  // Call can't actually happen.
			extractInt
		}
		val sizeUpper = sizeRange.upperBound.run {
			if (isInt) extractInt
			else Int.MAX_VALUE  // The *actual* tuple is constrained.
		}

		val startLower = startIndexType.lowerBound.run {
			if (!isInt) return CallSiteMustFail
			extractInt
		}
		val endLower = endIndexType.lowerBound.run {
			if (!isInt) return CallSiteMustFail
			extractInt
		}
		val startUpper = startIndexType.upperBound.run {
			if (!isInt) return CallSiteCanFail
			extractInt
		}
		val endUpper = endIndexType.upperBound.run {
			if (!isInt) return CallSiteCanFail
			extractInt
		}
		return when
		{
			// Definitely a degenerate slice.
			endUpper < startLower - 1 -> CallSiteMustFail
			// Definitely out-of-range.
			startLower - 1 > sizeUpper -> CallSiteMustFail
			// At this point, the slice is at least possible.  Check for
			// possible degeneracy.
			endLower < startUpper - 1 -> CallSiteCanFail
			// And check for a possible out-of-range.
			endUpper > sizeLower -> CallSiteCanFail
			else -> CallSiteCannotFail
		}
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>): A_Type
	{
		val (tupleType, startIndexType, endIndexType) = argumentTypes
		return computeSliceType(tupleType, startIndexType, endIndexType)
	}

	/**
	 * Compute the types that can be produced by extracting a subrange of a
	 * tuple having the given [tupleType], starting at an index that falls in
	 * the integer range type [startIndexType], and ending at an index that
	 * falls in the integer range type [endIndexType].
	 *
	 * @param tupleType
	 *   The type of the tuple being sliced.
	 * @param startIndexType
	 *   An integer range type bounding the possible slice starting index.
	 * @param endIndexType
	 *   An integer range type bounding the possible slice ending index.
	 * @return
	 *   The guaranteed type of the resulting tuple, in the event that it's
	 *   successful.
	 */
	fun computeSliceType(
		tupleType: A_Type,
		startIndexType: A_Type,
		endIndexType: A_Type
	): A_Type
	{
		// Precompute a fallback return type for easy exits.
		val fallback = mostGeneralTupleType

		val sizeRange = tupleType.sizeRange
		val sizeUpper = sizeRange.upperBound.run {
			if (isInt) extractInt
			else Int.MAX_VALUE  // If it's very large or even infinity
		}
		val startLower = startIndexType.lowerBound.run {
			if (!isInt) return fallback
			extractInt
		}
		val startUpper = startIndexType.upperBound.run {
			if (!isInt) Int.MAX_VALUE
			else extractInt
		}
		val endLower = endIndexType.lowerBound.run {
			if (!isInt) return fallback
			extractInt
		}
		val endUpper = endIndexType.upperBound.run {
			if (!isInt) Int.MAX_VALUE
			else extractInt
		}

		if (startLower > endUpper)
		{
			// It's always either empty or degenerate.
			return instanceType(emptyTuple)
		}

		// No need to compute element types beyond this position, since
		// they'll all have the same type.
		val variationLimit =
			min(tupleType.typeTuple.tupleSize + 1, sizeUpper)

		// The difference between extrema for starting positions.
		val smearDelta = startUpper - startLower

		val leadingTypes =
			(startLower .. max(startLower, min(endUpper, variationLimit))).map {
				tupleType.unionOfTypesAtThrough(
					it,
					// Don't exceed an int.
					min(
						it.toLong() + smearDelta,
						Int.MAX_VALUE.toLong()
					).toInt())
			}
		val minSize = max(endLower - startUpper + 1, 0)
		val maxSize = min(endUpper, sizeUpper) - startLower + 1
		val maxSizeObject = when
		{
			endUpper == Int.MAX_VALUE && sizeUpper == Int.MAX_VALUE ->
				positiveInfinity
			else -> fromInt(maxSize)
		}
		return tupleTypeForSizesTypesDefaultType(
			integerRangeType(
				fromInt(minSize),
				true,
				maxSizeObject,
				maxSizeObject.isFinite),
			tupleFromList(leadingTypes),
			leadingTypes.last()).makeImmutable()
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralTupleType,
				naturalNumbers,
				wholeNumbers),
			mostGeneralTupleType)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_SUBSCRIPT_OUT_OF_BOUNDS))

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val (tuple, low, high) = arguments
		// Generate code that does range checks, under the assumption that some
		// of them might become moot and be elided if integer inequalities are
		// checked later.
		val outOfRange =
			createBasicBlock("out of range for ExtractSubtuple")
		val (lowInt, highInt) = readTwoInts(
			low.semanticValue().unboxedInt,
			high.semanticValue().unboxedInt,
			outOfRange)
		{
			return false
		}
		val lowType = lowInt.restriction().type
		assert(lowType.isSubtypeOf(naturalNumbers))
		val highType = highInt.restriction().type
		assert(highType.isSubtypeOf(wholeNumbers))

		val sizeRange = tuple.type().sizeRange
		val sizeBoxed = primitiveInvocation(
			P_TupleSize,
			listOf(tuple.semanticValue()))
		val sizeInt = sizeBoxed.unboxedInt
		val equivalentSize = currentManifest.equivalentSemanticValue(sizeInt)
		if (equivalentSize !== null)
		{
			// It already exists, so reuse it.
			if (equivalentSize != sizeInt)
			{
				moveIntRegister(equivalentSize, setOf(sizeInt))
			}
		}
		else
		{
			// It's not yet available, so compute it.
			val writer = intWrite(
				setOf(sizeInt),
				intRestrictionForType(sizeRange))
			+L2_TUPLE_SIZE(tuple, writer)
		}

		// Now write range checks for the low and high index.
		// :: if (highInt > sizeInt) goto outOfBounds.
		if (highInt.type().upperBound.greaterThan(sizeRange.lowerBound))
		{
			// Dynamic check is needed.
			val inRange = createBasicBlock("high <= size")
			compareAndBranchInt(
				NumericComparator.LessOrEqual,
				highInt,
				readIntNoFail(sizeInt),
				edgeTo(inRange),
				edgeTo(outOfRange))
			startBlock(inRange)
		}
		// :: if (lowInt - 1 > highInt) goto outOfBounds.
		if (lowInt.type().upperBound.noFailMinusCanDestroy(one, false)
			.greaterThan(highInt.type().lowerBound))
		{
			// Dynamic check is needed.
			val lowMinusOne = primitiveInvocation(
				P_Subtraction,
				listOf(low.semanticValue(), constant(one)))
			+L2_BIT_LOGIC_OP(
				L2_BIT_LOGIC_OP.BitOperation.Sub,
				lowInt,
				unboxedIntConstant(1),
				intWrite(
					setOf(lowMinusOne.unboxedInt),
					intRestrictionForType(
						integerRangeType(
							lowInt.type().lowerBound
								.noFailMinusCanDestroy(one, true),
							true,
							lowInt.type().upperBound,
							false))))
			val inRange = createBasicBlock("low - 1 <= high")
			compareAndBranchInt(
				NumericComparator.LessOrEqual,
				readIntNoFail(lowMinusOne.unboxedInt),
				highInt,
				edgeTo(inRange),
				edgeTo(outOfRange))
			startBlock(inRange)
		}
		// At this point, the bounds have been verified correct.
		val temp = newTemp("subtuple")
		+L2_TUPLE_SUBRANGE_NO_FAIL(
			tuple,
			lowInt,
			highInt,
			boxedWrite(
				temp,
				boxedRestrictionForType(
					computeSliceType(
						tuple.type(),
						lowInt.type(),
						highInt.type()))))
		callSiteHelper.useAnswer(readBoxed(temp), false)
		startBlock(outOfRange)
		if (currentlyReachable())
		{
			// Deal with indices being out of range by doing the general case.
			generateGeneralFunctionInvocation(
				functionToCallReg,
				false,
				callSiteHelper,
				arguments,
				willAlwaysFailPrimitive = true)
		}
		return true
	}

	override fun L2GeneratorInterface.emitTransformedInfalliblePrimitive(
		rawFunction: A_RawFunction,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand)
	{
		val (inputTuple, low, high) = arguments.elements
		+L2_TUPLE_SUBRANGE_NO_FAIL(
			inputTuple,
			readIntNoFail(low.semanticValue().unboxedInt),
			readIntNoFail(high.semanticValue().unboxedInt),
			result)
	}
}
