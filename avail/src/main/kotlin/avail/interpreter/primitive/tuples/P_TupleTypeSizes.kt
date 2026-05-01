/*
 * P_TupleTypeSizes.kt
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
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleMeta
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive1
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionConditions

/**
 * **Primitive:** Answer the allowed size [ranges][IntegerRangeTypeDescriptor]
 * for this [tuple&#32;type][A_Type]. These are the sizes that a
 * [tuple][A_Tuple] may be and still be considered instances of the tuple type,
 * assuming the element [types][A_Type] are consistent with those specified by
 * the tuple type.
 */
@Suppress("unused")
object P_TupleTypeSizes : Primitive1(CannotFail, CanFold, CanInline)
{
	override fun attempt1(
		interpreter: Interpreter,
		arg1: AvailObject
	): A_BasicObject?
	{
		val tupleType = arg1
		return tupleType.sizeRange
	}

	override fun L2GeneratorInterface.emitBasicInfalliblePrimitive(
		rawFunction: A_RawFunction,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand)
	{
		val tupleMetaRestriction = arguments.elements[0].restriction()
		if (tupleMetaRestriction.type.equals(bottomMeta))
		{
			// The tuple type is necessrily bottom, so the size range is also
			// bottom.
			moveBoxedRegister(
				boxedConstant(bottom).semanticValue(),
				result.semanticValues())
			return
		}
		val sizeRange = tupleMetaRestriction.type.instance.sizeRange
		var strongSizeRestriction =
			boxedRestrictionForType(instanceMeta(sizeRange))
		if (tupleMetaRestriction.canBeBottom)
		{
			// The tuple type can be bottom, which means the output size range
			// can also be bottom.
			strongSizeRestriction = strongSizeRestriction.withCanBeBottom(true)
		}
		if (tupleMetaRestriction.isConstant)
		{
			// For this invocation, only one size happens to be possible.
			moveBoxedRegister(
				boxedConstant(
					tupleMetaRestriction.constantOrNull!!.sizeRange
				).semanticValue(),
				result.semanticValues())
			return
		}
		+L2_RUN_INFALLIBLE_PRIMITIVE.createInstruction(
			L2ConstantOperand(rawFunction),
			this@P_TupleTypeSizes,
			arguments,
			boxedWrite(result.semanticValues(), strongSizeRestriction))
	}

	override fun interestingSplitConditions(
		readBoxedOperands: List<L2ReadBoxedOperand>,
		rawFunction: A_RawFunction
	): List<L2SplitCondition?> = buildList {
		val tupleTypeRegister = readBoxedOperands[0].register()
		addAll(
			typeRestrictionConditions(
				setOf(tupleTypeRegister),
				boxedRestrictionForConstant(bottom)))
		addAll(
			typeRestrictionConditions(
				setOf(tupleTypeRegister),
				readBoxedOperands[0].restriction().withCanBeBottom(false)))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				tupleMeta),
			instanceMeta(wholeNumbers))
}
