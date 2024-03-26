/*
 * P_TypeUnion.kt
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
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.TypeDescriptor
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operation.L2_TYPE_UNION
import avail.optimizer.L1Translator
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.L2MeetsRestrictionCondition.Companion.typeRestrictionCondition

/**
 * **Primitive:** Answer the type union of the specified
 * [types][TypeDescriptor].
 */
@Suppress("unused")
object P_TypeUnion : Primitive(2, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val type1 = interpreter.argument(0)
		val type2 = interpreter.argument(1)
		return interpreter.primitiveSuccess(
			type1.typeUnion(type2).makeImmutable())
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(topMeta, topMeta),
			topMeta)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val (meta1, meta2) = argumentTypes
		return meta1.typeUnion(meta2)  // by metavariance
	}

	override fun interestingSplitConditions(
		readBoxedOperands: List<L2ReadBoxedOperand>,
		rawFunction: A_RawFunction
	): List<L2SplitCondition?>
	{
		val arg1 = readBoxedOperands[0]
		val arg2 = readBoxedOperands[0]
		// Since we can optimize based on whether or not ⊥ is possible for one
		// or the other argument, and also whether only ⊥ is present, try to
		// avoid merges that destroy that information.
		return listOf(
			typeRestrictionCondition(
				setOf(arg1.register(), arg2.register()),
				boxedRestrictionForConstant(bottom)),
			typeRestrictionCondition(
				setOf(arg1.register(), arg2.register()),
				boxedRestrictionForType(topMeta).minusValue(bottom)))
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: L1Translator.CallSiteHelper
	): Boolean
	{
		val (arg1, arg2) = arguments
		val (argType1, argType2) = argumentTypes
		val const1 = arg1.restriction().constantOrNull
		val const2 = arg2.restriction().constantOrNull
		var resultCanBeBottom = true
		when
		{
			// ⊥ ∪ x = x
			argType1.equals(bottomMeta) ->
			{
				callSiteHelper.useAnswer(arg2)
				return true
			}
			// x ∪ ⊥ = x
			argType2.equals(bottomMeta) ->
			{
				callSiteHelper.useAnswer(arg1)
				return true
			}
			// fold constant types
			const1 !== null && const2 !== null ->
				callSiteHelper.useAnswer(
					callSiteHelper.generator.boxedConstant(
						const1.typeUnion(const2)))
			// t ∪ x = t, if t is a constant type subsuming x
			const1 !== null && argType2.instance.isSubtypeOf(const1) ->
				callSiteHelper.useAnswer(arg1)
			// x ∪ t = t, if t is a constant type subsuming x
			const2 !== null && argType1.instance.isSubtypeOf(const2) ->
				callSiteHelper.useAnswer(arg2)
			// if x≠⊥ ∧ y≠⊥, then (x∪y)≠⊥.
			!arg1.restriction().containsEntireType(bottomMeta) &&
				!arg2.restriction().containsEntireType(bottomMeta) ->
			{
				resultCanBeBottom = false
			}
		}
		// Compute the union of the metatypes (the argTypes), which will be the
		// bound for the resulting type (arg1 ∪ arg2), due to metacovariance.
		// As a potential performance nicety, exclude bottom if neither argument
		// could be bottom.
		var restriction = boxedRestrictionForType(argType1.typeUnion(argType2))
		if (!resultCanBeBottom)
		{
			restriction = restriction.minusValue(bottom)
		}
		val translator = callSiteHelper.translator
		val writer = translator.generator.boxedWriteTemp(restriction)
		translator.addInstruction(
			L2_TYPE_UNION(arg1, arg2, writer))
		callSiteHelper.useAnswer(translator.readBoxed(writer))
		return true
	}
}
