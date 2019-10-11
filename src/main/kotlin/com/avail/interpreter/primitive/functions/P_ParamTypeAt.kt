/*
 * P_ParamTypeAt.java
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
package com.avail.interpreter.primitive.functions

import com.avail.descriptor.A_Number
import com.avail.descriptor.A_Type
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import com.avail.descriptor.BottomTypeDescriptor.bottom
import com.avail.descriptor.FunctionTypeDescriptor.functionMeta
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.IntegerRangeTypeDescriptor.int32
import com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail

/**
 * **Primitive:** Answer the type of the parameter at the
 * given index within the given functionType.
 */
object P_ParamTypeAt : Primitive(2, CannotFail, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(2)
		val functionType = interpreter.argument(0)
		val indexObject = interpreter.argument(1)
		val parametersType = functionType.argsTupleType()
		if (!indexObject.isInt)
		{
			// Note that it's already restricted statically to natural numbers.
			return if (parametersType.upperBound().lessThan(indexObject))
			{
				interpreter.primitiveSuccess(bottom())
			}
			else interpreter.primitiveSuccess(parametersType.defaultType())
		}
		val index = indexObject.extractInt()
		val argumentType = functionType.argsTupleType().typeAtIndex(index)
		return interpreter.primitiveSuccess(argumentType)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				functionMeta(),
				naturalNumbers()),
			anyMeta())
	}

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>): Primitive.Fallibility
	{
		//		final A_Type functionType = argumentTypes.get(0);
		val indexType = argumentTypes[1]
		return if (indexType.isSubtypeOf(int32()))
			CallSiteCannotFail
		else
			CallSiteCanFail
	}

}