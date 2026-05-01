/*
 * P_Instances.kt
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
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.SetTypeDescriptor.Companion.mostGeneralSetType
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.exceptions.AvailErrorCode.E_NOT_AN_ENUMERATION
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive1
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.existsCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionConditions

/**
 * **Primitive:** Obtain the instances of the specified [type][topMeta].
 */
@Suppress("unused")
object P_Instances : Primitive1(CanFold, CanInline)
{
	override fun attempt1(
		interpreter: Interpreter,
		arg1: AvailObject
	): A_BasicObject?
	{
		val type = arg1
		return if (!type.isEnumeration)
		{
			interpreter.fail(E_NOT_AN_ENUMERATION)
		}
		else type.instances
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(topMeta),
			mostGeneralSetType())

	override fun interestingSplitConditions(
		readBoxedOperands: List<L2ReadBoxedOperand>,
		rawFunction: A_RawFunction
	): List<L2SplitCondition?> = buildList {
		// It would be nice to know the number of instances.  For now, split on
		// whether the given type is bottom – and therefore has ∅ as its
		// instances.
		val typeRegister = readBoxedOperands[0].register()
		addAll(
			typeRestrictionConditions(
				setOf(typeRegister),
				boxedRestrictionForConstant(bottom)))
		// Knowing the instance count can help constrain the set size.
		add(
			existsCondition(
				setOf(
					P_InstanceCount.semanticInvocation(
						readBoxedOperands[0].semanticValue()))))
	}

	override fun L2GeneratorInterface.emitTransformedInfalliblePrimitive(
		rawFunction: A_RawFunction,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand)
	{
		val argument = arguments.elements[0]

		val manifest = currentManifest
		val countSemanticValue =
			P_InstanceCount.semanticInvocation(argument.semanticValue())
		manifest.equivalentSemanticValue(countSemanticValue)?.let {
				equivalentCount ->
			val countRange = manifest.restrictionFor(equivalentCount).type
			if (countRange.isSubtypeOf(inclusive(zero, zero)))
			{
				// The input must be bottom, so the output should be ∅.
				moveBoxedRegister(
					boxedConstant(emptySet).semanticValue(),
					result.semanticValues())
				return
			}
			if (countRange.upperBound.isFinite)
			{
				// We've deduced the possible sizes of the set of instances.  We
				// also proved it's finite, so the primitive won't fail.
				+L2_RUN_INFALLIBLE_PRIMITIVE.createInstruction(
					L2ConstantOperand(rawFunction),
					this@P_Instances,
					arguments,
					boxedWrite(
						result.semanticValues(),
						result.restriction().intersectionWithType(
							setTypeForSizesContentType(
								countRange,
								argument.restriction().type))))
				return
			}
		}
		emitBasicInfalliblePrimitive(rawFunction, arguments, result)
	}

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>
	): Fallibility
	{
		val meta = argumentTypes[0]
		return if (meta.instance.isEnumeration) CallSiteCannotFail
			else CallSiteCanFail
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_NOT_AN_ENUMERATION))
}
