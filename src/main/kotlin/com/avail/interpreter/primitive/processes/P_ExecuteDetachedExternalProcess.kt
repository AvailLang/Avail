/*
 * P_ExecuteDetachedExternalProcess.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.processes

import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.types.MapTypeDescriptor.mapTypeForSizesKeyTypeValueType
import com.avail.descriptor.types.TupleTypeDescriptor.oneOrMoreOf
import com.avail.descriptor.types.TupleTypeDescriptor.stringType
import com.avail.descriptor.types.TupleTypeDescriptor.zeroOrOneOf
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_NO_EXTERNAL_PROCESS
import com.avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.execution.Interpreter
import java.io.File
import java.io.IOException

/**
 * **Primitive**: Execute a detached external [process][Process]. No capability
 * is provided to communicate with this process.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_ExecuteDetachedExternalProcess : Primitive(6, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(6)
		val processArgsTuple = interpreter.argument(0)
		val optDir = interpreter.argument(1)
		val optIn = interpreter.argument(2)
		val optOut = interpreter.argument(3)
		val optError = interpreter.argument(4)
		val optEnvironment = interpreter.argument(5)
		val processArgs = processArgsTuple.map(AvailObject::asNativeString)
		val builder = ProcessBuilder(processArgs)
		if (optDir.tupleSize() == 1)
		{
			builder.directory(File(optDir.tupleAt(1).asNativeString()))
		}
		if (optIn.tupleSize() == 1)
		{
			builder.redirectInput(File(optIn.tupleAt(1).asNativeString()))
		}
		if (optOut.tupleSize() == 1)
		{
			builder.redirectOutput(File(optOut.tupleAt(1).asNativeString()))
		}
		if (optError.tupleSize() == 1)
		{
			builder.redirectError(File(optError.tupleAt(1).asNativeString()))
		}
		if (optEnvironment.tupleSize() == 1)
		{
			val newEnvironmentMap =
				optEnvironment.tupleAt(1).mapIterable().associate {
					(k, v) -> k.asNativeString() to v.asNativeString()
				}
			val environmentMap = builder.environment()
			environmentMap.clear()
			environmentMap.putAll(newEnvironmentMap)
		}
		try
		{
			builder.start()
		}
		catch (e: SecurityException)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED)
		}
		catch (e: IOException)
		{
			return interpreter.primitiveFailure(E_NO_EXTERNAL_PROCESS)
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tupleFromArray(
				oneOrMoreOf(stringType()),
				zeroOrOneOf(stringType()),
				zeroOrOneOf(stringType()),
				zeroOrOneOf(stringType()),
				zeroOrOneOf(stringType()),
				zeroOrOneOf(
					mapTypeForSizesKeyTypeValueType(
						wholeNumbers(),
						stringType(),
						stringType()))),
			TOP.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_PERMISSION_DENIED,
				E_NO_EXTERNAL_PROCESS))
}
