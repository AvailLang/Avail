/*
 * P_Division.kt
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
package avail.interpreter.primitive.numbers

import avail.descriptor.numbers.AbstractNumberDescriptor.Companion.binaryNumericOperationTypeBound
import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.negativeOne
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number
import avail.descriptor.representation.A_Number.Companion.divideCanDestroy
import avail.descriptor.representation.A_Number.Companion.equalsInt
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Number.Companion.lessThan
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.representation.A_Set.Companion.setWithoutElementCanDestroy
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instanceCount
import avail.descriptor.representation.A_Type.Companion.instances
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.typeIntersection
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i31
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NUMBER
import avail.exceptions.ArithmeticException
import avail.exceptions.AvailErrorCode.E_CANNOT_DIVIDE_BY_ZERO
import avail.exceptions.AvailErrorCode.E_CANNOT_DIVIDE_INFINITIES
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.interpreter.levelTwo.operation.NumericComparator.GreaterOrEqual
import avail.interpreter.levelTwo.operation.NumericComparator.LessOrEqual
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Div
import avail.interpreter.levelTwo.operation.numbers.L2_DIVIDE_INT_BY_INT
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteMustFail
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive2
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionConditions
import avail.optimizer.L2SplitCondition.Companion.unboxedIntConditions

/**
 * **Primitive:** Divide a number by another number.
 */
@Suppress("unused")
object P_Division : Primitive2(CanFold, CanInline)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val a = arg1
		val b = arg2
		if (b.equalsInt(0) && a.isInstanceOf(integers))
		{
			return fail(E_CANNOT_DIVIDE_BY_ZERO)
		}
		return try
		{
			a.divideCanDestroy(b, true)
		}
		catch (e: ArithmeticException)
		{
			fail(e.errorCode)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(NUMBER(), NUMBER()), NUMBER())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_CANNOT_DIVIDE_BY_ZERO, E_CANNOT_DIVIDE_INFINITIES))

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type
	{
		val (aType, bType) = argumentTypes
		if (!aType.isSubtypeOf(integers) || !bType.isSubtypeOf(integers))
		{
			return binaryNumericOperationTypeBound(
				argumentTypes[0], argumentTypes[1])
		}
		// The values are integers.
		if (aType.isEnumeration && bType.isEnumeration
			&& aType.instanceCount.extractInt * bType.instanceCount.extractInt
				< 100)
		{
			// Calculate the exact set of quotients.
			var values = emptySet
			val bInstances = bType.instances
				.setWithoutElementCanDestroy(zero, false)
			aType.instances.forEach { aValue ->
				bInstances.forEach { bValue ->
					if (aValue.isFinite || bValue.isFinite)
					{
						values = values.setWithElementCanDestroy(
							aValue.divideCanDestroy(bValue, false), true)
					}
				}
			}
			return enumerationWith(values).makeImmutable()
		}
		val aBoundaries = mutableSetOf<A_Number>()
		listOf(
			naturalNumbers,
			singleInt(0),
			inclusive(negativeInfinity, negativeOne())
		).forEach { range ->
			val clipped = aType.typeIntersection(range)
			if (!clipped.isBottom)
			{
				aBoundaries.add(clipped.lowerBound)
				aBoundaries.add(clipped.upperBound)
			}
		}
		// Ignore b=0 case, as it doesn't produce a result.
		val bBoundaries = mutableSetOf<A_Number>()
		listOf(
			naturalNumbers,
			inclusive(negativeInfinity, negativeOne())
		).forEach { range ->
			val clipped = bType.typeIntersection(range)
			if (!clipped.isBottom)
			{
				bBoundaries.add(clipped.lowerBound)
				bBoundaries.add(clipped.upperBound)
			}
		}
		val quotients = mutableSetOf<A_Number>()
		aBoundaries.forEach { aValue ->
			bBoundaries.forEach { bValue ->
				// ±∞/±∞ doesn't contribute to boundary conditions.
				if (aValue.isFinite || bValue.isFinite)
				{
					quotients.add(aValue.divideCanDestroy(bValue, false))
				}
			}
		}
		if (quotients.isEmpty()) return bottom
		val min = quotients.minWithOrNull { a, b ->
			if (a.lessThan(b)) -1 else 1
		}!!.makeImmutable()
		val max = quotients.maxWithOrNull { a, b ->
			if (a.lessThan(b)) -1 else 1
		}!!.makeImmutable()
		return inclusive(min, max)
			.typeIntersection(integers)
			.makeImmutable()
	}

	override fun fallibilityForArgumentTypes(argumentTypes: List<A_Type>)
		: Fallibility
	{
		val (aType, bType) = argumentTypes

		if (zero.isInstanceOf(bType))
		{
			return when
			{
				bType.typeIntersection(integers).run {
					lowerBound.equalsInt(0) && upperBound.equalsInt(0)
				} -> CallSiteMustFail
				else -> CallSiteCanFail
			}
		}
		val aTypeIncludesInfinity =
			negativeInfinity.isInstanceOf(aType)
				|| positiveInfinity.isInstanceOf(aType)
		val bTypeIncludesInfinity =
			negativeInfinity.isInstanceOf(bType)
				|| positiveInfinity.isInstanceOf(bType)
		return when
		{
			aTypeIncludesInfinity && bTypeIncludesInfinity -> CallSiteCanFail
			else -> CallSiteCannotFail
		}
	}

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean = attemptToGenerateTwoIntToIntPrimitive(
		callSiteHelper,
		functionToCallReg,
		rawFunction,
		arguments,
		argumentTypes,
		ifOutputIsInt = {
			val positiveDenominator = createBasicBlock("positive denominator")
			compareAndBranchInt(
				comparator = LessOrEqual,
				int1Reg = intB,
				int2Reg = unboxedIntConstant(0),
				ifTrue = edgeTo(intFailure),
				ifFalse = edgeTo(positiveDenominator))
			startBlock(positiveDenominator)
			if (!currentlyReachable())
				return@attemptToGenerateTwoIntToIntPrimitive
			val nonnegativeNumerator =
				createBasicBlock("non-negative numerator")
			compareAndBranchInt(
				comparator = GreaterOrEqual,
				int1Reg = intA,
				int2Reg = unboxedIntConstant(0),
				ifTrue = edgeTo(nonnegativeNumerator),
				ifFalse = edgeTo(intFailure))
			startBlock(nonnegativeNumerator)
			if (!currentlyReachable())
				return@attemptToGenerateTwoIntToIntPrimitive
			val strongerType = returnTypeGuaranteedByVM(
				null,
				listOf(
					currentManifest.restrictionFor(intA).type,
					currentManifest.restrictionFor(intB).type))
			+L2_BIT_LOGIC_OP(
				Div,
				intA,
				intB,
				intWrite(
					intWrite.semanticValues(),
					intRestrictionForType(strongerType)))
		},
		ifOutputIsPossiblyInt = {
			+L2_DIVIDE_INT_BY_INT(
				dividend = intA,
				divisor = intB,
				quotient = intWrite,
				outOfRangeOrZeroDiv = edgeTo(intFailure),
				success = edgeTo(intSuccess))
		})

	override fun interestingSplitConditions(
		readBoxedOperands: List<L2ReadBoxedOperand>,
		rawFunction: A_RawFunction
	): List<L2SplitCondition?> = buildList {
		val (aRead, bRead) = readBoxedOperands
		if (!aRead.restriction().intersectsType(i31)) return emptyList()
		if (!bRead.restriction().intersectsType(positiveI31)) return emptyList()
		// The division is possible in 32-bit math.
		if (!aRead.restriction().containedByType(i31))
		{
			addAll(
				typeRestrictionConditions(
					setOf(aRead.register()),
					boxedRestrictionForType(i31)))
		}
		if (!bRead.restriction().containedByType(positiveI31))
		{
			addAll(
				typeRestrictionConditions(
					setOf(bRead.register()),
					boxedRestrictionForType(positiveI31)))
		}
		// Since we've already excluded the case that the values are always
		// out of range, we can still wish for the values to be in int
		// registers already, since the range test will be quicker if the
		// values are already unboxed.
		addAll(unboxedIntConditions(listOf(aRead.register())))
		addAll(unboxedIntConditions(listOf(bRead.register())))
	}

	/** The type for strictly positive 32-bit integers. */
	val positiveI31 = inclusive(1, Int.MAX_VALUE)

	override val semanticInfixOperatorString: String? get() = "Div"
}
