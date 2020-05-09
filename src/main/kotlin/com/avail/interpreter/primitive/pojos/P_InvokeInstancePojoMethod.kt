/*
 * P_InvokeInstancePojoMethod.kt
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
package com.avail.interpreter.primitive.pojos

import com.avail.AvailRuntime.HookType
import com.avail.descriptor.pojos.PojoDescriptor.newPojo
import com.avail.descriptor.pojos.PojoDescriptor.nullPojo
import com.avail.descriptor.pojos.RawPojoDescriptor.identityPojo
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromList
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomTypeDescriptor.bottom
import com.avail.descriptor.types.PojoTypeDescriptor.pojoTypeForClass
import com.avail.descriptor.types.PojoTypeDescriptor.unmarshal
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.MarshalingException
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Private
import com.avail.interpreter.primitive.pojos.PrimitiveHelper.marshalValues
import com.avail.utility.MutableOrNull
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * **Primitive:** Given arguments that start with the receiver of a Java
 * [Method], followed by the method's own arguments, invoke the method.  Note
 * that this is a late-bound invocation, so it dynamically locates the actual
 * Java code to invoke.
 *
 * Perform necessary marshalling of the receiver and arguments, and
 * unmarshalling of the result.  If an exception is thrown during evaluation,
 * raise it as an Avail exception via the
 * [HookType.RAISE_JAVA_EXCEPTION_IN_AVAIL] hook.
 *
 * The current function was constructed via
 * [P_CreatePojoInstanceMethodFunction], and has two outer values: the Java
 * [Method] and the [tuple][A_Tuple] of marshaled types.
 */
object P_InvokeInstancePojoMethod : Primitive(-1, Private)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		val methodArgs = tupleFromList(interpreter.argsBuffer)

		val primitiveFunction = interpreter.function!!
		val primitiveRawFunction = primitiveFunction.code()
		assert(primitiveRawFunction.primitive() === this)

		val methodPojo = primitiveFunction.outerVarAt(1)
		val marshaledTypes = primitiveFunction.outerVarAt(2)
		// The exact return kind was captured in the function type.
		val expectedType = primitiveRawFunction.functionType().returnType()

		interpreter.availLoaderOrNull()?.statementCanBeSummarized(false)

		// Marshal the arguments.
		val method = methodPojo.javaObjectNotNull<Method>()
		val errorOut = MutableOrNull<AvailErrorCode>()
		val receiver = methodArgs.tupleAt(1).marshalToJava(
			marshaledTypes.tupleAt(1).javaObject())
		val marshaledArgs = marshalValues(
			marshaledTypes.copyTupleFromToCanDestroy(
				2, marshaledTypes.tupleSize(), false),
			methodArgs.copyTupleFromToCanDestroy(
				2, methodArgs.tupleSize(), false),
			errorOut)
		if (errorOut.value !== null)
		{
			val e = errorOut.value()
			return interpreter.primitiveFailure(
				newPojo(identityPojo(e), pojoTypeForClass(e.javaClass)))
		}

		// Invoke the instance method.
		val result: Any? = try
		{
			method.invoke(receiver, *marshaledArgs!!)
		}
		catch (e: InvocationTargetException)
		{
			val cause = e.cause!!
			return interpreter.primitiveFailure(
				newPojo(
					identityPojo(cause), pojoTypeForClass(cause.javaClass)))
		}
		catch (e: Throwable)
		{
			// This is an unexpected failure in the invocation mechanism.  For
			// now, report it like an expected InvocationTargetException.
			return interpreter.primitiveFailure(
				newPojo(identityPojo(e), pojoTypeForClass(e.javaClass)))
		}

		result ?: return interpreter.primitiveSuccess(nullPojo())

		return try {
			interpreter.primitiveSuccess(unmarshal(result, expectedType))
		} catch (e: MarshalingException) {
			interpreter.primitiveFailure(
				newPojo(identityPojo(e), pojoTypeForClass(e.javaClass)))
		}
	}

	/**
	 * This primitive is suitable for any block signature, although really the
	 * primitive could only be applied if the function returns any.
	 */
	override fun privateBlockTypeRestriction(): A_Type = bottom()

	override fun privateFailureVariableType(): A_Type =
		pojoTypeForClass(Throwable::class.java)
}
