/*
 * P_CreatePojoStaticMethodFunction.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.FunctionDescriptor.Companion.createWithOuters2
import avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import avail.descriptor.pojos.RawPojoDescriptor.Companion.equalityPojo
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeVariables
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.PojoTypeDescriptor.Companion.marshalTypes
import avail.descriptor.types.PojoTypeDescriptor.Companion.resolvePojoType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.RAW_POJO
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE
import avail.exceptions.AvailErrorCode.E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS
import avail.exceptions.MarshalingException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.pojos.PrimitiveHelper.lookupMethod
import avail.interpreter.primitive.pojos.PrimitiveHelper.rawPojoInvokerFunctionFromFunctionType
import avail.utility.Mutable
import avail.utility.cast
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * **Primitive:** Given a [type][A_Type] that can be successfully marshaled to a
 * Java type, a [string][A_String] that names a `static` [method][Method] of
 * that type, and a [tuple][A_Tuple] of parameter [types][A_Type], create a
 * [function][A_Function] that when applied will invoke the `static` method. The
 * `static` method is invoked with arguments conforming to the marshaling of the
 * parameter types. If the return value has a preferred Avail surrogate type,
 * then marshal the value to the surrogate type prior to answering it.
 *
 *
 * Should the Java method raise an exception, re-raise it within Avail as a
 * pojo exception.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_CreatePojoStaticMethodFunction : Primitive(3, CanInline, CanFold)
{
	/**
	 * Cache of [A_RawFunction]s, keyed by the function [A_Type].
	 */
	private val rawFunctionCache = WeakHashMap<A_Type, A_RawFunction>()

	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val pojoType = interpreter.argument(0)
		val methodName = interpreter.argument(1)
		val paramTypes = interpreter.argument(2)

		interpreter.availLoaderOrNull()?.statementCanBeSummarized(false)

		// Marshal the argument types.
		val method: Method?
		val marshaledTypesTuple: A_Tuple
		try
		{
			val marshaledTypes = marshalTypes(paramTypes)
			val errorOut = Mutable<AvailErrorCode?>(null)
			method = lookupMethod(
				pojoType, methodName, marshaledTypes, errorOut)
			if (method === null)
			{
				return interpreter.primitiveFailure(errorOut.value!!)
			}
			marshaledTypesTuple = generateObjectTupleFrom(marshaledTypes.size) {
				equalityPojo(marshaledTypes[it - 1])
			}
		}
		catch (e: MarshalingException)
		{
			return interpreter.primitiveFailure(e)
		}

		val returnType = resolvePojoType(
			method.genericReturnType,
			if (pojoType.isPojoType) pojoType.typeVariables else emptyMap)
		val functionType = functionType(paramTypes, returnType)
		val rawFunction = synchronized(rawFunctionCache) {
			rawFunctionCache.computeIfAbsent(functionType) {
				rawPojoInvokerFunctionFromFunctionType(
					P_InvokeStaticPojoMethod,
					it,
					// Outer#1 = Static method to invoke.
					RAW_POJO.o,
					// Outer#2 = Marshaled type parameters.
					zeroOrMoreOf(RAW_POJO.o))
			}
		}
		val function = createWithOuters2(
			rawFunction,
			// Outer#1 = Static method to invoke.
			equalityPojo(method),
			// Outer#2 = Marshaled type parameters.
			marshaledTypesTuple.cast())
		return interpreter.primitiveSuccess(function)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				anyMeta(),
				stringType,
				zeroOrMoreOf(anyMeta())),
			functionTypeReturning(TOP.o))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_JAVA_METHOD_NOT_AVAILABLE,
				E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS))
}
