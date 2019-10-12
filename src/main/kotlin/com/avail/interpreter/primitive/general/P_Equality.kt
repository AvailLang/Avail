/*
 * P_Equality.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.general

import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Type
import com.avail.descriptor.EnumerationTypeDescriptor
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper

import com.avail.descriptor.AtomDescriptor.falseObject
import com.avail.descriptor.AtomDescriptor.objectFromBoolean
import com.avail.descriptor.AtomDescriptor.trueObject
import com.avail.descriptor.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.EnumerationTypeDescriptor.falseType
import com.avail.descriptor.EnumerationTypeDescriptor.trueType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail

/**
 * **Primitive:** Compare for equality. Answer a [ ][EnumerationTypeDescriptor.booleanType].
 */
object P_Equality : Primitive(2, CannotFail, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(2)
		val a = interpreter.argument(0)
		val b = interpreter.argument(1)
		return interpreter.primitiveSuccess(objectFromBoolean(a.equals(b)))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		assert(argumentTypes.size == 2)
		val type1 = argumentTypes[0]
		val type2 = argumentTypes[1]

		if (type1.typeIntersection(type2).isBottom)
		{
			// The actual values cannot be equal at runtime.
			return falseType()
		}
		if (type1.isEnumeration
		    && type1.equals(type2)
		    && type1.instanceCount().equalsInt(1))
		{
			val value = type1.instances().iterator().next()
			// Because of metacovariance, a meta may actually have many
			// instances.  For instance, tuple's type contains not only tuple,
			// but every subtype of tuple (e.g., string, <>'s type, etc.).
			if (!value.isType)
			{
				// The actual values will have to be equal at runtime.
				return trueType()
			}
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				ANY.o(),
				ANY.o()),
			booleanType())
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

		if (firstReg.register() === secondReg.register())
		{
			// A value is being compared to itself, even though we might not
			// know anything specific about what it is.
			callSiteHelper.useAnswer(
				translator.generator.boxedConstant(trueObject()))
			return true
		}

		val type1 = firstReg.type()
		val type2 = secondReg.type()
		if (type1.typeIntersection(type2).isBottom)
		{
			// The actual values cannot be equal at runtime.
			callSiteHelper.useAnswer(
				translator.generator.boxedConstant(falseObject()))
			return true
		}
		// Because of metacovariance, a meta may actually have many instances.
		// For instance, tuple's type contains not only tuple, but every subtype
		// of tuple (e.g., string, <>'s type, etc.).
		if (type1.equals(type2)
		    && type1.instanceCount().equalsInt(1)
		    && !type1.isInstanceMeta)
		{
			callSiteHelper.useAnswer(
				translator.generator.boxedConstant(trueObject()))
			return true
		}

		// It's contingent.  Eventually we could turn this into an
		// L2_JUMP_IF_OBJECTS_EQUAL, producing true on one path and false on the
		// other, merging with a phi.  That would introduce an opportunity for
		// code splitting back to here, if subsequent uses of the phi register
		// noticed its origins and cared to do something substantially different
		// in the true and false cases (say dispatching to an if/then/else).
		return super.tryToGenerateSpecialPrimitiveInvocation(
			functionToCallReg,
			rawFunction,
			arguments,
			argumentTypes,
			translator,
			callSiteHelper)
	}

}