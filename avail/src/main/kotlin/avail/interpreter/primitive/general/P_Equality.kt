/*
 * P_Equality.kt
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
package avail.interpreter.primitive.general

import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.falseType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.trueType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_OBJECTS_EQUAL
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive2
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2Generator.Companion.edgeTo

/**
 * **Primitive:** Compare for equality. Answer a
 * [boolean][booleanType].
 */
@Suppress("unused")
object P_Equality : Primitive2(CannotFail, CanFold, CanInline)
{
	override fun attempt2(
		interpreter: Interpreter,
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val a = arg1
		val b = arg2
		return objectFromBoolean(a.equals(b))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type
	{
		assert(argumentTypes.size == 2)
		val (type1, type2) = argumentTypes

		if (type1.typeIntersection(type2).isBottom)
		{
			// The actual values cannot be equal at runtime.
			return falseType
		}
		if (type1.isEnumeration
			&& type1.equals(type2)
			&& type1.instanceCount.equalsInt(1))
		{
			val value = type1.instances.single()
			// Because of metacovariance, a meta may actually have many
			// instances.  For instance, tuple's type contains not only tuple,
			// but every subtype of tuple (e.g., string, <>'s type, etc.).
			if (!value.isType)
			{
				// The actual values will have to be equal at runtime.
				return trueType
			}
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(ANY(), ANY()), booleanType)

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val (firstReg, secondReg) = arguments

		val manifest = currentManifest
		if (manifest.semanticValueToSynonym(firstReg.semanticValue())
			== manifest.semanticValueToSynonym(secondReg.semanticValue()))
		{
			// A value is being compared to itself, even though we might not
			// know anything specific about what it is.
			callSiteHelper.useAnswer(boxedConstant(trueObject), false)
			return true
		}

		val type1 = firstReg.type()
		val type2 = secondReg.type()
		if (type1.typeIntersection(type2).isBottom)
		{
			// The actual values cannot be equal at runtime.
			callSiteHelper.useAnswer(boxedConstant(falseObject), false)
			return true
		}
		// Because of metacovariance, a meta may actually have many instances.
		// For instance, tuple's type contains not only tuple, but every subtype
		// of tuple (e.g., string, <>'s type, etc.).
		if (type1.equals(type2)
			&& type1.instanceCount.equalsInt(1)
			&& !type1.isInstanceMeta)
		{
			callSiteHelper.useAnswer(boxedConstant(trueObject), false)
			return true
		}

		// At least avoid the overhead of a general primitive call.  Make sure
		// to generate L2 instructions that expose the selection of booleans
		// through control flow, so that code splitting can use it.
		val ifEqual = createBasicBlock("equal")
		val ifNotEqual = createBasicBlock("not equal")
		val c1 = firstReg.constantOrNull
		val c2 = secondReg.constantOrNull
		when
		{
			c1 !== null -> jumpIfEqualsConstant(
				secondReg,
				c1,
				ifEqual,
				ifNotEqual)
			c2 !== null -> jumpIfEqualsConstant(
				firstReg,
				c2,
				ifEqual,
				ifNotEqual)
			else -> +L2_JUMP_IF_OBJECTS_EQUAL(
				firstReg,
				secondReg,
				edgeTo(ifEqual),
				edgeTo(ifNotEqual))
		}
		if (ifEqual.currentlyReachable())
		{
			startBlock(ifEqual)
			callSiteHelper.useAnswer(boxedConstant(trueObject), false)
		}
		if (ifNotEqual.currentlyReachable())
		{
			startBlock(ifNotEqual)
			callSiteHelper.useAnswer(boxedConstant(falseObject), false)
		}
		return true
	}

	override val canDestroyArguments get() = false
}
