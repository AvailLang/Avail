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

import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instance
import avail.descriptor.representation.A_Type.Companion.instanceCount
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operation.L2_MOVE_BOXED
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive1
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionConditions

/**
 * **Primitive:** How many instances does the specified [type][topMeta] have?
 */
@Suppress("unused")
object P_InstanceCount : Primitive1(CannotFail, CanFold, CanInline)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val type = arg1
		return type.instanceCount
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
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
	): List<L2SplitCondition?> = buildList {
		// If we can separate knowledge of whether the argument is bottomMeta,
		// we can produce 0 along that path.
		val argument = readBoxedOperands[0]
		if (argument.restriction().intersectsType(bottomMeta))
		{
			addAll(
				typeRestrictionConditions(
					setOf(argument.register()),
					boxedRestrictionForConstant(bottom)))
		}
		else
		{
			addAll(
				super.interestingSplitConditions(
					readBoxedOperands, rawFunction))
		}
	}

	override fun L2GeneratorInterface.emitTransformedInfalliblePrimitive(
		rawFunction: A_RawFunction,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand)
	{
		val instanceTypeRead = arguments.elements[0]
		val restriction = instanceTypeRead.restriction()
		restriction.constantOrNull?.let { constant ->
			+L2_MOVE_BOXED(boxedConstant(constant.instanceCount), result)
			return
		}
		val instanceType = restriction.type
		if (instanceType.equals(bottomMeta))
		{
			+L2_MOVE_BOXED(boxedConstant(zero), result)
			return
		}
		val minCount = if (restriction.canBeBottom) zero else one
		val innerType = instanceType.instance
		val maxCount = when
		{
			innerType.isInstanceMeta -> one
			// Say we statically have a metatype whose instance is an
			// enumeration type of N non-types.  For example, boolean's type
			// has the instance boolean, which is just an enumeration of the
			// true and false atoms.  If a call site for this primitive has
			// the argument typed as boolean's type, then it may be called
			// at runtime with boolean, {true}ᵀ, {false}ᵀ, or ⊥. These would
			// have an instance count of 2, 1, 1, and 0, respectively.
			innerType.isSubtypeOf(Types.NONTYPE()) -> innerType.instanceCount
			else -> positiveInfinity
		}
		val rangeRestriction = result.restriction().intersectionWithType(
			inclusive(minCount, maxCount))
		if (rangeRestriction.type.lowerBound
			.equals(rangeRestriction.type.upperBound))
		{
			// There's only one value it can be.
			+L2_MOVE_BOXED(boxedConstant(rangeRestriction.type.lowerBound), result)
			return
		}
		emitBasicInfalliblePrimitive(
			rawFunction,
			arguments,
			L2WriteBoxedOperand(result.semanticValues(), rangeRestriction))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(topMeta),
			inclusive(zero, positiveInfinity))

	override val canDestroyArguments get() = false
}
