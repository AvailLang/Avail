/*
 * P_CreatePojoConstructorFunction.kt
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

import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.FunctionDescriptor.Companion.createWithOuters2
import avail.descriptor.pojos.RawPojoDescriptor.Companion.equalityPojo
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.PojoTypeDescriptor.Companion.marshalDefiningType
import avail.descriptor.types.PojoTypeDescriptor.Companion.marshalTypes
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.RAW_POJO
import avail.exceptions.AvailErrorCode.E_JAVA_MARSHALING_FAILED
import avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE
import avail.exceptions.AvailErrorCode.E_POJO_TYPE_IS_ABSTRACT
import avail.exceptions.MarshalingException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.pojos.PrimitiveHelper.rawPojoInvokerFunctionFromFunctionType
import avail.utility.cast
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/**
 * **Primitive:** Given a [type][A_Type] that can be successfully marshaled to a
 * Java type, and a [tuple][A_Tuple] of parameter [types][A_Type], create a
 * [function][A_Function] that when applied will produce a new instance of the
 * defining Java type. The instance is created by invoking a reflected Java
 * [Constructor] with arguments conforming to the marshaling of the parameter
 * types. If the new instance has a preferred Avail surrogate type, then marshal
 * the value to the surrogate type prior to answering it.
 *
 * Should the Java method raise an exception, re-raise it within Avail as a
 * pojo exception.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_CreatePojoConstructorFunction : Primitive(2, CanInline, CanFold)
{
	/**
	 * Cache of [A_RawFunction]s, keyed by the function [A_Type].
	 */
	private val rawFunctionCache = WeakHashMap<A_Type, A_RawFunction>()

	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val pojoType = interpreter.argument(0)
		val paramTypes = interpreter.argument(1)

		interpreter.availLoaderOrNull()?.statementCanBeSummarized(false)

		// Marshal the argument types and look up the appropriate
		// constructor.
		val marshaledTypesTuple: A_Tuple
		val constructor: Constructor<*>
		try
		{
			val javaClass = marshalDefiningType(pojoType)
			if (javaClass.modifiers and Modifier.ABSTRACT != 0)
			{
				return interpreter.primitiveFailure(E_POJO_TYPE_IS_ABSTRACT)
			}
			val marshaledTypes = marshalTypes(paramTypes)
			constructor = javaClass.getConstructor(*marshaledTypes)
			marshaledTypesTuple =
				tupleFromList(marshaledTypes.map(::equalityPojo))
		}
		catch (e: MarshalingException)
		{
			return interpreter.primitiveFailure(e)
		}
		catch (e: Exception)
		{
			return interpreter.primitiveFailure(E_JAVA_METHOD_NOT_AVAILABLE)
		}

		val functionType = functionType(paramTypes, pojoType)
		val rawFunction = synchronized(rawFunctionCache) {
			rawFunctionCache.computeIfAbsent(functionType) {
				rawPojoInvokerFunctionFromFunctionType(
					P_InvokePojoConstructor,
					it,
					// Outer#1 = Constructor to invoke.
					RAW_POJO.o,
					// Outer#2 = Marshaled type parameters.
					zeroOrMoreOf(RAW_POJO.o))
			}
		}
		val function = createWithOuters2(
			rawFunction,
			// Outer#1 = Constructor to invoke.
			equalityPojo(constructor),
			// Outer#2 = Marshaled type parameters.
			marshaledTypesTuple.cast())
		return interpreter.primitiveSuccess(function)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				anyMeta(),
				zeroOrMoreOf(anyMeta())),
			functionTypeReturning(ANY.o))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_POJO_TYPE_IS_ABSTRACT,
				E_JAVA_MARSHALING_FAILED,
				E_JAVA_METHOD_NOT_AVAILABLE))
}
