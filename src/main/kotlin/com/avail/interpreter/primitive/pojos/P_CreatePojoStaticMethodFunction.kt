/*
 * P_CreatePojoStaticMethodFunction.kt
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
package com.avail.interpreter.primitive.pojos

import com.avail.descriptor.*
import com.avail.descriptor.FunctionDescriptor.createWithOuters2
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.FunctionTypeDescriptor.functionTypeReturning
import com.avail.descriptor.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.MapDescriptor.emptyMap
import com.avail.descriptor.ObjectTupleDescriptor.generateObjectTupleFrom
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PojoTypeDescriptor.marshalTypes
import com.avail.descriptor.PojoTypeDescriptor.resolvePojoType
import com.avail.descriptor.RawPojoDescriptor.equalityPojo
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.descriptor.TypeDescriptor.Types.RAW_POJO
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE
import com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS
import com.avail.exceptions.MarshalingException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.primitive.pojos.PrimitiveHelper.lookupMethod
import com.avail.interpreter.primitive.pojos.PrimitiveHelper.rawPojoInvokerFunctionFromFunctionType
import com.avail.utility.Casts.cast
import com.avail.utility.MutableOrNull
import java.lang.reflect.Method
import java.util.*
import java.util.Collections.synchronizedMap

/**
 * **Primitive:** Given a [type][A_Type] that can be
 * successfully marshaled to a Java type, a [string][A_String] that
 * names a `static` [method][Method] of that type, and a
 * [tuple][A_Tuple] of parameter [types][A_Type], create a
 * [function][A_Function] that when applied will invoke the `static` method. The `static` method is invoked with arguments
 * conforming to the marshaling of the parameter types. If the return value has
 * a preferred Avail surrogate type, then marshal the value to the surrogate
 * type prior to answering it.
 *
 *
 * Should the Java method raise an exception, re-raise it within Avail as a
 * pojo exception.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_CreatePojoStaticMethodFunction : Primitive(3, CanInline, CanFold)
{
	/**
	 * Cache of [A_RawFunction]s, keyed by the function [A_Type].
	 */
	private val rawFunctionCache = synchronizedMap(WeakHashMap<A_Type, A_RawFunction>())

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val pojoType = interpreter.argument(0)
		val methodName = interpreter.argument(1)
		val paramTypes = interpreter.argument(2)

		val loader = interpreter.availLoaderOrNull()
		loader?.statementCanBeSummarized(false)

		// Marshal the argument types.
		val method: Method?
		val marshaledTypesTuple: A_Tuple
		try
		{
			val marshaledTypes = marshalTypes(paramTypes)
			val errorOut = MutableOrNull<AvailErrorCode>()
			method = lookupMethod(
				pojoType, methodName, marshaledTypes, errorOut)
			if (method === null)
			{
				return interpreter.primitiveFailure(errorOut.value())
			}
			marshaledTypesTuple = generateObjectTupleFrom(
				marshaledTypes.size
			) { i -> equalityPojo(marshaledTypes[i - 1]) }
		}
		catch (e: MarshalingException)
		{
			return interpreter.primitiveFailure(e)
		}

		val returnType = resolvePojoType(
			method.genericReturnType,
			if (pojoType.isPojoType) pojoType.typeVariables() else emptyMap())
		val functionType = functionType(paramTypes, returnType)

		val rawFunction = (rawFunctionCache as java.util.Map<A_Type, A_RawFunction>).computeIfAbsent(
			functionType
		) { fType ->
			rawPojoInvokerFunctionFromFunctionType(
				P_InvokeStaticPojoMethod,
				fType,
				// Outer#1 = Static method to invoke.
				RAW_POJO.o(),
				// Outer#2 = Marshaled type parameters.
				zeroOrMoreOf(RAW_POJO.o()))
		}
		val function = createWithOuters2(
			rawFunction,
			// Outer#1 = Static method to invoke.
			equalityPojo(method),
			// Outer#2 = Marshaled type parameters.
			cast<A_Tuple, AvailObject>(marshaledTypesTuple))
		return interpreter.primitiveSuccess(function)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				anyMeta(),
				stringType(),
				zeroOrMoreOf(anyMeta())),
			functionTypeReturning(TOP.o()))
	}

	override fun privateFailureVariableType(): A_Type
	{
		return AbstractEnumerationTypeDescriptor.enumerationWith(set(
			E_JAVA_METHOD_NOT_AVAILABLE,
			E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS))
	}

}