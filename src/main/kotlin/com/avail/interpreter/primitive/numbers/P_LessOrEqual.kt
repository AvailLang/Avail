/*
 * P_LessOrEqual.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.numbers

import com.avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.numbers.A_Number.Companion.lessOrEqual
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Companion.possibleOrdersWhenComparingInstancesOf
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Order.EQUAL
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Order.INCOMPARABLE
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Order.LESS
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Order.MORE
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.EnumerationTypeDescriptor
import com.avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import com.avail.descriptor.types.EnumerationTypeDescriptor.Companion.falseType
import com.avail.descriptor.types.EnumerationTypeDescriptor.Companion.trueType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.TypeDescriptor.Types.NUMBER
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper

/**
 * **Primitive:** Compare two extended integers and answer a
 * [boolean][EnumerationTypeDescriptor.booleanType].
 */
@Suppress("unused")
object P_LessOrEqual : Primitive(2, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val a = interpreter.argument(0)
		val b = interpreter.argument(1)
		return interpreter.primitiveSuccess(
			objectFromBoolean(a.lessOrEqual(b)))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(NUMBER.o, NUMBER.o), booleanType)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
	{
		val possible =
			possibleOrdersWhenComparingInstancesOf(
				argumentTypes[0], argumentTypes[1])
		val canBeTrue =
			possible.contains(LESS) || possible.contains(EQUAL)
		val canBeFalse =
			possible.contains(MORE) || possible.contains(INCOMPARABLE)
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

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val firstReg = arguments[0]
		val secondReg = arguments[1]
		val firstType = firstReg.type()
		val secondType = secondReg.type()
		val possible =
			possibleOrdersWhenComparingInstancesOf(firstType, secondType)
		val canBeTrue =
			possible.contains(LESS) || possible.contains(EQUAL)
		val canBeFalse =
			possible.contains(MORE) || possible.contains(INCOMPARABLE)
		assert(canBeTrue || canBeFalse)
		if (!canBeTrue || !canBeFalse)
		{
			callSiteHelper.useAnswer(
				translator.generator
					.boxedConstant(objectFromBoolean(canBeTrue)))
			return true
		}
		return super.tryToGenerateSpecialPrimitiveInvocation(
			functionToCallReg,
			rawFunction,
			arguments,
			argumentTypes,
			translator,
			callSiteHelper)
	}
}
