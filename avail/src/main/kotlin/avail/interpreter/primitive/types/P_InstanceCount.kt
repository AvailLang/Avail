/*
 * P_InstanceCount.kt
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
package avail.interpreter.primitive.types

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.L2MeetsRestrictionCondition.Companion.typeRestrictionCondition
import avail.optimizer.reoptimizer.L2Regenerator

/**
 * **Primitive:** How many instances does the specified
 * [type][InstanceMetaDescriptor.topMeta] have?
 */
@Suppress("unused")
object P_InstanceCount : Primitive(1, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val type = interpreter.argument(0)
		return interpreter.primitiveSuccess(type.instanceCount)
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>
	): A_Type
	{
		val type = argumentTypes[0]

		if (type.equals(bottomMeta)) return instanceType(zero)
		if (type.isInstanceMeta)
		{
			val innerType = type.instance
			if (innerType.isEnumeration && !innerType.isInstanceMeta)
			{
				// Say we statically have a metatype whose instance is an
				// enumeration type of N non-types.  For example, boolean's type
				// has the instance boolean, which is just an enumeration of the
				// true and false atoms.  If a call site for this primitive has
				// the argument typed as boolean's type, then it may be called
				// at runtime with boolean, {true}ᵀ, {false}ᵀ, or ⊥. These would
				// have an instance count of 2, 1, 1, and 0, respectively.
				return inclusive(zero, innerType.instanceCount)
			}
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
	}

	override fun interestingSplitConditions(
		readBoxedOperands: List<L2ReadBoxedOperand>,
		rawFunction: A_RawFunction
	): List<L2SplitCondition?>
	{
		// If we can separate knowledge of whether the argument is bottomMeta,
		// we can produce 0 along that path.
		val argument = readBoxedOperands[0]
		if (argument.restriction().intersectsType(bottomMeta))
		{
			return listOf(
				typeRestrictionCondition(
					setOf(argument.register()),
					boxedRestrictionForConstant(bottom)))
		}
		return super.interestingSplitConditions(readBoxedOperands, rawFunction)
	}

	override fun emitTransformedInfalliblePrimitive(
		operation: L2_RUN_INFALLIBLE_PRIMITIVE,
		rawFunction: A_RawFunction,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand,
		regenerator: L2Regenerator)
	{
		val instanceTypeRead = arguments.elements[0]
		val restriction = instanceTypeRead.restriction()
		restriction.constantOrNull?.let { constant ->
			regenerator.addInstruction(
				L2_MOVE.boxed,
				regenerator.boxedConstant(constant.instanceCount),
				result)
			return
		}
		val canBeBottom = restriction.intersectsType(bottomMeta)
		val instanceType = restriction.type
		if (instanceType.isInstanceMeta)
		{
			val innerType = instanceType.instance
			if (innerType.isEnumeration && !innerType.isInstanceMeta)
			{
				// Say we statically have a metatype whose instance is an
				// enumeration type of N non-types.  For example, boolean's type
				// has the instance boolean, which is just an enumeration of the
				// true and false atoms.  If a call site for this primitive has
				// the argument typed as boolean's type, then it may be called
				// at runtime with boolean, {true}ᵀ, {false}ᵀ, or ⊥. These would
				// have an instance count of 2, 1, 1, and 0, respectively.
				var range = inclusive(
					if (canBeBottom) zero else one,
					innerType.instanceCount)
				if (range.lowerBound.equals(range.upperBound))
				{
					// There's only one value it can be.
					regenerator.addInstruction(
						L2_MOVE.boxed,
						regenerator.boxedConstant(range.lowerBound),
						result)
					return
				}
				// At least we can narrow (possibly) the result type.
				super.emitTransformedInfalliblePrimitive(
					operation,
					rawFunction,
					arguments,
					L2WriteBoxedOperand(
						result.semanticValues(),
						result.restriction().intersectionWithType(range),
						result.register()),
					regenerator)
				return
			}
		}
		super.emitTransformedInfalliblePrimitive(
			operation, rawFunction, arguments, result, regenerator)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(topMeta),
			inclusive(zero, positiveInfinity))
}
