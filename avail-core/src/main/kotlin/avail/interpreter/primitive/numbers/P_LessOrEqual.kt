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
package avail.interpreter.primitive.numbers

import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.lessOrEqual
import avail.descriptor.numbers.AbstractNumberDescriptor.Companion.possibleOrdersWhenComparingInstancesOf
import avail.descriptor.numbers.AbstractNumberDescriptor.Order.EQUAL
import avail.descriptor.numbers.AbstractNumberDescriptor.Order.INCOMPARABLE
import avail.descriptor.numbers.AbstractNumberDescriptor.Order.LESS
import avail.descriptor.numbers.AbstractNumberDescriptor.Order.MORE
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.EnumerationTypeDescriptor
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.falseType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.trueType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NUMBER
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_GREATER_THAN_OR_EQUAL_TO_CONSTANT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_LESS_THAN_OR_EQUAL_TO_CONSTANT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_LESS_THAN_OR_EQUAL_TO_OBJECT
import avail.optimizer.L1Translator
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2Generator

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
		val (type1, type2) = argumentTypes
		val possible = possibleOrdersWhenComparingInstancesOf(type1, type2)
		val canBeTrue = possible.contains(LESS) || possible.contains(EQUAL)
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
		val (firstReg, secondReg) = arguments
		val firstType = firstReg.type()
		val secondType = secondReg.type()
		val generator = translator.generator
		val possible =
			possibleOrdersWhenComparingInstancesOf(firstType, secondType)
		val canBeTrue =
			possible.contains(LESS) || possible.contains(EQUAL)
		val canBeFalse =
			possible.contains(MORE) || possible.contains(INCOMPARABLE)
		assert(canBeTrue || canBeFalse)
		if (!canBeTrue || !canBeFalse)
		{
			// The branch direction has been statically proven.
			callSiteHelper.useAnswer(
				generator.boxedConstant(objectFromBoolean(canBeTrue)))
			return true
		}
		val firstConstant = firstReg.constantOrNull()
		val secondConstant = secondReg.constantOrNull()
		val truePath = generator.createBasicBlock("true path")
		val falsePath = generator.createBasicBlock("false path")
		when
		{
			secondConstant !== null ->
				generator.addInstruction(
					L2_JUMP_IF_LESS_THAN_OR_EQUAL_TO_CONSTANT,
					firstReg,
					L2ConstantOperand(secondConstant),
					L2Generator.edgeTo(truePath),
					L2Generator.edgeTo(falsePath))
			firstConstant !== null ->
				generator.addInstruction(
					L2_JUMP_IF_GREATER_THAN_OR_EQUAL_TO_CONSTANT,
					secondReg,
					L2ConstantOperand(firstConstant),
					L2Generator.edgeTo(truePath),
					L2Generator.edgeTo(falsePath))
			else ->
				generator.addInstruction(
					L2_JUMP_IF_LESS_THAN_OR_EQUAL_TO_OBJECT,
					firstReg,
					secondReg,
					L2Generator.edgeTo(truePath),
					L2Generator.edgeTo(falsePath)
				)
		}
		generator.startBlock(truePath)
		callSiteHelper.useAnswer(generator.boxedConstant(trueObject))
		generator.startBlock(falsePath)
		callSiteHelper.useAnswer(generator.boxedConstant(falseObject))
		return true
	}
}
