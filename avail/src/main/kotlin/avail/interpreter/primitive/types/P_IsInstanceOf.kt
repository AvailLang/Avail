/*
 * P_IsInstanceOf.kt
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

import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_OBJECT
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive2
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2Generator.Companion.edgeTo

/**
 * **Primitive:** Answer whether `value` is an instance of `type`.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_IsInstanceOf : Primitive2(CannotFail, CanFold, CanInline)
{
	override fun attempt2(
		interpreter: Interpreter,
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val value = arg1
		val type = arg2

		return objectFromBoolean(value.isInstanceOf(type))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(ANY(), topMeta),
			booleanType)

	/**
	 * Some identities apply.  The terms x and y are the values being compared
	 * (not necessarily known statically), and x' and y' are their static types
	 * (making them metatypes).
	 *
	 *  1. The test is always true if the exact type y1 is known (not a
	 * subtype) and x' ∈ y1'.
	 *  1. The test is always false if the exact type x1 is known (not a
	 * subtype) and x1' ∉ y'.
	 *  1. The test is always true if y = ⊤.
	 */
	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val (xReg, yTypeReg) = arguments

		if (xReg.restriction().metaRestriction().intersection(
				yTypeReg.restriction()).type.isVacuousType)
		{
			// The intersection is vacuous, so no further testing is required.
			callSiteHelper.useAnswer(boxedConstant(falseObject), false)
			return true
		}

		val ifInstance = createBasicBlock("if instance")
		val ifNotInstance = createBasicBlock("not instance")

		val constantYType = yTypeReg.constantOrNull
		if (constantYType !== null)
		{
			jumpIfKindOfConstant(
				xReg,
				constantYType.typeIntersection(xReg.type()),
				ifInstance,
				ifNotInstance)
		}
		else
		{
			+L2_JUMP_IF_KIND_OF_OBJECT(
				xReg,
				yTypeReg,
				edgeTo(ifInstance),
				edgeTo(ifNotInstance))
		}
		startBlock(ifInstance)
		if (currentlyReachable())
		{
			callSiteHelper.useAnswer(boxedConstant(trueObject), false)
		}
		startBlock(ifNotInstance)
		if (currentlyReachable())
		{
			callSiteHelper.useAnswer(boxedConstant(falseObject), false)
		}
		return true
	}

	override val canDestroyArguments get() = false
}
