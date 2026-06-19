/*
 * P_InvokePojoConstructor.kt
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
package avail.interpreter.primitive.pojos

import avail.AvailRuntime.HookType
import avail.descriptor.pojos.PojoDescriptor.Companion.newPojo
import avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Tuple
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.returnType
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.PojoTypeDescriptor.Companion.pojoTypeForClass
import avail.descriptor.types.PojoTypeDescriptor.Companion.unmarshal
import avail.exceptions.AvailErrorCode
import avail.exceptions.MarshalingException
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive.Flag.Private
import avail.interpreter.primitive.PrimitiveHelper.marshalValues
import avail.interpreter.primitive.PrimitiveN
import avail.utility.Mutable
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException

/**
 * **Primitive:** Invoke a Java [Constructor], passing marshaled forms of this
 * primitive's arguments.  Unmarshal the resulting object as needed.
 *
 * If an exception is thrown during evaluation, raise it as an Avail exception
 * via the [HookType.RAISE_JAVA_EXCEPTION_IN_AVAIL] hook.
 *
 * The current function was constructed via [P_CreatePojoConstructorFunction],
 * and has two outer values: the Java [Constructor] and the [tuple][A_Tuple] of
 * marshaled [types][A_Type].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_InvokePojoConstructor : PrimitiveN(-1, Private, HasSideEffect)
{
	override fun Interpreter.attemptN(
		args: Array<AvailObject>
	): A_BasicObject?
	{
		val constructorArgs = tupleFromArray(*args)

		val primitiveFunction = function!!
		val primitiveRawFunction = primitiveFunction.code()
		assert(primitiveRawFunction.codePrimitive() === P_InvokePojoConstructor)

		val constructorPojo = primitiveFunction.outerVarAt(1)
		val marshaledTypes = primitiveFunction.outerVarAt(2)
		// The exact return kind was captured in the function type.
		val expectedType = primitiveRawFunction.functionType().returnType

		availLoaderOrNull()?.statementCanBeSummarized(false)

		// Marshal the arguments.
		val constructor = constructorPojo.javaObjectNotNull<Constructor<*>>()
		val errorOut = Mutable<AvailErrorCode?>(null)
		val marshaledArgs = marshalValues(
			marshaledTypes, constructorArgs, errorOut)
		if (errorOut.value !== null)
		{
			val e = errorOut.value!!
			return fail(
				newPojo(identityPojo(e), pojoTypeForClass(e.javaClass)))
		}

		// Invoke the constructor.
		val result: Any
		try
		{
			result = marshaledArgs
				?.let { constructor.newInstance(*it) }
				?: constructor.newInstance(null)
		}
		catch (e: InvocationTargetException)
		{
			val cause = e.cause!!
			return fail(
				newPojo(identityPojo(cause), pojoTypeForClass(cause.javaClass)))
		}
		catch (e: Throwable)
		{
			// This is an unexpected failure in the invocation mechanism.  For
			// now, report it like an expected InvocationTargetException.
			return fail(
				newPojo(identityPojo(e), pojoTypeForClass(e.javaClass)))
		}

		return try
		{
			unmarshal(result, expectedType)
		}
		catch (e: MarshalingException)
		{
			fail(newPojo(identityPojo(e), pojoTypeForClass(e.javaClass)))
		}
	}

	/**
	 * An argument might capture an escaped variable and make it shared here.
	 */
	override fun mightMakeEscapedVariableShared(
		argumentTypes: List<A_Type>
	): Boolean = true

	/**
	 * This primitive is suitable for any block signature, although really the
	 * primitive could only be applied if the function returns any.
	 */
	override fun privateBlockTypeRestriction(): A_Type = bottom

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type
	{
		return rawFunction!!.functionType().returnType
	}

	override fun privateFailureVariableType(): A_Type =
		pojoTypeForClass(Throwable::class.java)

}
