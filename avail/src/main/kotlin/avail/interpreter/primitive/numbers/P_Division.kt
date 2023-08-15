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

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.divideCanDestroy
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.lessThan
import avail.descriptor.numbers.AbstractNumberDescriptor.Companion.binaryNumericOperationTypeBound
import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.negativeOne
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.A_Set.Companion.setWithoutElementCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NUMBER
import avail.exceptions.ArithmeticException
import avail.exceptions.AvailErrorCode.E_CANNOT_DIVIDE_BY_ZERO
import avail.exceptions.AvailErrorCode.E_CANNOT_DIVIDE_INFINITIES
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.Primitive.Fallibility.CallSiteMustFail
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_COMPARE_INT
import avail.optimizer.L1Translator
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue

/**
 * **Primitive:** Divide a number by another number.
 */
@Suppress("unused")
object P_Division : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val a = interpreter.argument(0)
		val b = interpreter.argument(1)
		if (b.equalsInt(0) && a.isInstanceOf(integers))
		{
			return interpreter.primitiveFailure(E_CANNOT_DIVIDE_BY_ZERO)
		}
		return try
		{
			interpreter.primitiveSuccess(a.divideCanDestroy(b, true))
		}
		catch (e: ArithmeticException)
		{
			interpreter.primitiveFailure(e)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(NUMBER.o, NUMBER.o), NUMBER.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_CANNOT_DIVIDE_BY_ZERO, E_CANNOT_DIVIDE_INFINITIES))

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
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
		}
		val max = quotients.maxWithOrNull { a, b ->
			if (a.lessThan(b)) -1 else 1
		}
		return inclusive(min!!, max!!)
			.typeIntersection(integers)
			.makeImmutable()
	}

	override fun fallibilityForArgumentTypes(argumentTypes: List<A_Type>)
		: Fallibility
	{
		val (aType, bType) = argumentTypes

		val bTypeIncludesZero = zero.isInstanceOf(bType)
		if (bTypeIncludesZero)
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

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: L1Translator.CallSiteHelper): Boolean
	{
		//TODO Fix this – it leads to infinite Avail stack recursion somehow.
		if (true)
		{
			translator.generateGeneralFunctionInvocation(
				functionToCallReg, arguments, false, callSiteHelper)
			return true
		}
		val (a, b) = arguments
		val (aType, bType) = argumentTypes

		val generator = translator.generator
		// If either of the argument types does not intersect with int32, then
		// fall back to boxed division.
		if (aType.typeIntersection(i32).isBottom
			|| bType.typeIntersection(i32).isBottom)
		{
			translator.generateGeneralFunctionInvocation(
				functionToCallReg, arguments, false, callSiteHelper)
			return true
		}

		// Extract int32s, falling back if the actual values aren't in range.
		val fallback = generator.createBasicBlock("fall back to boxed division")
		val intA = generator.readInt(
			L2SemanticUnboxedInt(a.semanticValue()), fallback)
		val intB = generator.readInt(
			L2SemanticUnboxedInt(b.semanticValue()), fallback)
		// We've checked that both arguments intersected int32, so now we're
		// on the happy path where we've extracted two ints.
		assert(generator.currentlyReachable())
		val returnTypeIfInts = returnTypeGuaranteedByVM(
			rawFunction,
			argumentTypes.map { it.typeIntersection(i32) })
		val semanticQuotient = L2SemanticValue.primitiveInvocation(
			this, listOf(a.semanticValue(), b.semanticValue()))
		val quotientWriter = generator.intWrite(
			setOf(L2SemanticUnboxedInt(semanticQuotient)),
			restrictionForType(returnTypeIfInts, UNBOXED_INT_FLAG))
		val notZeroDenominator = L2BasicBlock("fast path division")
		// This branch is optimized out if the denominator can't equal zero.
		L2_JUMP_IF_COMPARE_INT.notEqual.compareAndBranch(
			generator,
			intB,
			generator.unboxedIntConstant(0),
			edgeTo(notZeroDenominator),
			edgeTo(fallback))
		generator.startBlock(notZeroDenominator)
		// At this point the denominator isn't zero.
		val manifest = generator.currentManifest
		val aRestriction = manifest.restrictionFor(intA.semanticValue())
		val bRestriction = manifest.restrictionFor(intB.semanticValue())
		if (aRestriction.intersectsType(instanceType(fromInt(Int.MIN_VALUE)))
			&& bRestriction.intersectsType(instanceType(negativeOne)))
		{
			// The combination MIN_INT/-1 is possible here, so test just the
			// numerator and fall back to the slow path if it's MIN_INT, even if
			// the denominator isn't -1.
			val notMinInt = L2BasicBlock("not MIN_INT")
			L2_JUMP_IF_COMPARE_INT.notEqual.compareAndBranch(
				generator,
				intA,
				generator.unboxedIntConstant(Int.MIN_VALUE),
				edgeTo(notMinInt),
				edgeTo(fallback))
			generator.startBlock(notMinInt)
		}
		// At this point the result will not throw division-by-zero or overflow
		// an int32.
		translator.addInstruction(
			L2_BIT_LOGIC_OP.wrappedDivide, intA, intB, quotientWriter)
		// Even though we're just using the boxed value again, the unboxed
		// form is also still available for use by subsequent primitives,
		// which could allow the boxing instruction to evaporate.
		callSiteHelper.useAnswer(generator.readBoxed(semanticQuotient))

		if (fallback.predecessorEdges().isNotEmpty())
		{
			// The fallback block is reachable, so generate the slow case within
			// it.  Fallback may happen from conversion of non-int32 arguments,
			// or from int32 overflow calculating the product.
			generator.startBlock(fallback)
			translator.generateGeneralFunctionInvocation(
				functionToCallReg, arguments, false, callSiteHelper)
		}
		return true
	}
}
