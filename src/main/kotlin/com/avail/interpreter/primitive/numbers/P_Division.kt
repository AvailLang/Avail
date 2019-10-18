/*
 * P_Division.kt
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
package com.avail.interpreter.primitive.numbers

import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.AbstractNumberDescriptor.binaryNumericOperationTypeBound
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InfinityDescriptor.negativeInfinity
import com.avail.descriptor.InfinityDescriptor.positiveInfinity
import com.avail.descriptor.IntegerDescriptor.zero
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TypeDescriptor.Types.NUMBER
import com.avail.exceptions.ArithmeticException
import com.avail.exceptions.AvailErrorCode.E_CANNOT_DIVIDE_BY_ZERO
import com.avail.exceptions.AvailErrorCode.E_CANNOT_DIVIDE_INFINITIES
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Divide an extended integer by another one.
 */
object P_Division : Primitive(2, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val a = interpreter.argument(0)
		val b = interpreter.argument(1)
		try
		{
			return interpreter.primitiveSuccess(a.divideCanDestroy(b, true))
		}
		catch (e: ArithmeticException)
		{
			return interpreter.primitiveFailure(e)
		}

	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				NUMBER.o(),
				NUMBER.o()),
			NUMBER.o())
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val aType = argumentTypes[0]
		val bType = argumentTypes[1]

		return binaryNumericOperationTypeBound(aType, bType)
	}

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>): Primitive.Fallibility
	{
		val aType = argumentTypes[0]
		val bType = argumentTypes[1]

		val aTypeIncludesInfinity = negativeInfinity().isInstanceOf(aType) || positiveInfinity().isInstanceOf(aType)
		val bTypeIncludesInfinity = negativeInfinity().isInstanceOf(bType) || positiveInfinity().isInstanceOf(bType)
		val bTypeIncludesZero = zero().isInstanceOf(bType)
		return if (bTypeIncludesZero || aTypeIncludesInfinity && bTypeIncludesInfinity)
		{
			CallSiteCanFail
		}
		else CallSiteCannotFail
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(
				E_CANNOT_DIVIDE_BY_ZERO,
				E_CANNOT_DIVIDE_INFINITIES))
	}

}