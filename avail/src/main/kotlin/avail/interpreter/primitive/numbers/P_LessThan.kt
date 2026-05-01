/*
 * P_LessThan.kt
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

import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.lessThan
import avail.descriptor.numbers.AbstractNumberDescriptor.Companion.possibleOrdersWhenComparingInstancesOf
import avail.descriptor.numbers.AbstractNumberDescriptor.Order.EQUAL
import avail.descriptor.numbers.AbstractNumberDescriptor.Order.INCOMPARABLE
import avail.descriptor.numbers.AbstractNumberDescriptor.Order.LESS
import avail.descriptor.numbers.AbstractNumberDescriptor.Order.MORE
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.falseType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.trueType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NUMBER
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.NumericComparator
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive2
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.unboxedIntConditions

/**
 * **Primitive:** Compare two extended integers and answer a
 * [boolean][booleanType].
 */
@Suppress("unused")
object P_LessThan : Primitive2(CannotFail, CanFold, CanInline)
{
	override fun attempt2(
		interpreter: Interpreter,
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val a = arg1
		val b = arg2
		return objectFromBoolean(a.lessThan(b))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(NUMBER(), NUMBER()), booleanType)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?, argumentTypes: List<A_Type>): A_Type
	{
		val (type1, type2) = argumentTypes
		val possible = possibleOrdersWhenComparingInstancesOf(type1, type2)
		val canBeTrue = possible.contains(LESS)
		val canBeFalse =
			(possible.contains(EQUAL)
				|| possible.contains(MORE)
				|| possible.contains(INCOMPARABLE))
		assert(canBeTrue || canBeFalse)
		return if (canBeTrue)
		{
			if (canBeFalse) { booleanType } else { trueType }
		}
		else
		{
			falseType
		}
	}

	override fun interestingSplitConditions(
		readBoxedOperands: List<L2ReadBoxedOperand>,
		rawFunction: A_RawFunction
	): List<L2SplitCondition?> = buildList {
		val (arg1, arg2) = readBoxedOperands
		if (arg1.restriction().intersectsType(i32)
			&& arg2.restriction().intersectsType(i32))
		{
			addAll(unboxedIntConditions(listOf(arg1.register())))
			addAll(unboxedIntConditions(listOf(arg2.register())))
		}
	}

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val (firstReg, secondReg) = arguments
		val firstType = firstReg.type()
		val secondType = secondReg.type()

		val possible =
			possibleOrdersWhenComparingInstancesOf(firstType, secondType)
		val canBeTrue = possible.contains(LESS)
		val canBeFalse =
			(possible.contains(EQUAL)
				|| possible.contains(MORE)
				|| possible.contains(INCOMPARABLE))
		assert(canBeTrue || canBeFalse)
		if (!canBeTrue || !canBeFalse)
		{
			// The branch direction has been statically proven.
			callSiteHelper.useAnswer(
				boxedConstant(objectFromBoolean(canBeTrue)), false)
			return true
		}
		val truePath = createBasicBlock("true path")
		val falsePath = createBasicBlock("false path")
		compareAndBranchBoxed(
			NumericComparator.Less,
			firstReg,
			secondReg,
			edgeTo(truePath),
			edgeTo(falsePath))
		startBlock(truePath)
		callSiteHelper.useAnswer(boxedConstant(trueObject), false)
		startBlock(falsePath)
		callSiteHelper.useAnswer(boxedConstant(falseObject), false)
		return true
	}

	override val canDestroyArguments get() = false
}
