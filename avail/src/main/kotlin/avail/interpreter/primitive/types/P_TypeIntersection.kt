/*
 * P_TypeIntersection.kt
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
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TypeDescriptor
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
 * **Primitive:** Answer the type intersection of the
 * specified [types][TypeDescriptor].
 */
@Suppress("unused")
object P_TypeIntersection : Primitive(2, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val type1 = interpreter.argument(0)
		val type2 = interpreter.argument(1)
		return interpreter.primitiveSuccess(
			type1.typeIntersection(type2).makeImmutable())
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				topMeta(),
				topMeta()),
			topMeta())

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val (meta1, meta2) = argumentTypes
		return meta1.typeIntersection(meta2)  // by metavariance
	}

	override fun interestingSplitConditions(
		readBoxedOperands: List<L2ReadBoxedOperand>,
		rawFunction: A_RawFunction
	): List<L2SplitCondition?>
	{
		val identities = mutableListOf<L2SplitCondition?>()
		readBoxedOperands.forEach { read ->
			identities.add(
				typeRestrictionCondition(
					listOf(read.register()),
					boxedRestrictionForConstant(bottom)))
			identities.add(
				typeRestrictionCondition(
					listOf(read.register()),
					boxedRestrictionForConstant(TOP.o)))
		}
		// TODO: It would be nice to wish for one or the other argument to be
		//  a constant, without specifying a particular constant.  That isn't
		//  supported yet [2024.02.24].
		return identities
	}

	override fun emitTransformedInfalliblePrimitive(
		operation: L2_RUN_INFALLIBLE_PRIMITIVE,
		rawFunction: A_RawFunction,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand,
		regenerator: L2Regenerator)
	{
		// Take advantage of identities exposed by the split conditions.
		val (arg1, arg2) = arguments.elements
		val (restriction1, restriction2) =
			arguments.elements.map(L2ReadBoxedOperand::restriction)
		var moveSource: L2ReadBoxedOperand? = null
		restriction1.constantOrNull?.let { const1 ->
			moveSource = when
			{
				restriction2.containsEntireType(const1) -> arg1
				restriction2.containedByType(const1) -> arg2
				else -> null
			}
		}
		restriction2.constantOrNull?.let { const2 ->
			moveSource = when
			{
				restriction1.containsEntireType(const2) -> arg2
				restriction1.containedByType(const2) -> arg1
				else -> null
			}
		}
		moveSource?.let { source ->
			regenerator.moveRegister(
				L2_MOVE.boxed,
				source.semanticValue(),
				result.semanticValues())
		}
		super.emitTransformedInfalliblePrimitive(
			operation,
			rawFunction,
			arguments,
			result,
			regenerator)
	}
}
