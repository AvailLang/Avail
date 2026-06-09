/*
 * P_AtomicAddToMap.kt
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

package avail.interpreter.primitive.variables

import avail.AvailRuntimeSupport.captureNanos
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.keyType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.A_Type.Companion.valueType
import avail.descriptor.types.A_Type.Companion.writeType
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.MapTypeDescriptor.Companion.mostGeneralMapType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableReadWriteType
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.A_Variable.Companion.atomicAddToMap
import avail.descriptor.variables.A_Variable.Companion.atomicAddToMapNoCheck
import avail.exceptions.AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE
import avail.exceptions.AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.effects.LoadingEffectToRunPrimitive
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwoSimple.L2SimpleTranslator
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive3

/**
 * **Primitive:** Atomically read and update the map in the specified
 * [variable][A_Variable] by adding the given key and value.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_AtomicAddToMap : Primitive3(CanInline, HasSideEffect) {
	override fun Interpreter.attempt3(
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?
	{
		val variable = arg1
		val key = arg2
		val value = arg3
		try
		{
			variable.atomicAddToMap(key, value)
		}
		catch (e: VariableGetException)
		{
			return fail(e.errorCode)
		}
		catch (e: VariableSetException)
		{
			return fail(e.errorCode)
		}

		availLoaderOrNull()?.recordEffect(
			LoadingEffectToRunPrimitive(
				SpecialMethodAtom.ADD_TO_MAP_VARIABLE, variable, key, value))
		return nil
	}

	/**
	 * Override to produce special code for this primitive, if it can be shown
	 * statically that the value being written is of the correct type.
	 */
	override fun L2SimpleTranslator.simplePrimitiveNilpotentInvocation(
		functionIfKnown: A_Function?,
		rawFunction: A_RawFunction,
		argRestrictions: List<TypeRestriction>,
		expectedType: A_Type
	): ((Interpreter)->A_BasicObject?)?
	{
		val variableType = argRestrictions[0].type
		val keyType = argRestrictions[1].type
		val valueType = argRestrictions[2].type

		assert(variableType.isSubtypeOf(mostGeneralVariableType))
		val contentType = variableType.writeType
		if (!contentType.isMapType) return null
		if (!keyType.isSubtypeOf(contentType.keyType)) return null
		if (!valueType.isSubtypeOf(contentType.valueType)) return null
		if (contentType.sizeRange.upperBound.isFinite) return null
		// The value being written doesn't need to be type checked at runtime.
		return ::nilpotentAttempt
	}

	override fun nilpotentAttempt(interpreter: Interpreter): A_BasicObject?
	{
		val before = captureNanos()
		val (variable, newKey, newValue) = interpreter.argsBuffer
		val success = try {
			variable.atomicAddToMapNoCheck(newKey, newValue)
			nil
		}
		catch (e: VariableGetException)
		{
			// Let the subsequent full primitive retry handle this.
			null
		}
		catch (e: VariableSetException)
		{
			// Let the subsequent full primitive retry handle this.
			null
		}
		val stat = when (success)
		{
			null -> l2SimpleNilpotentAbortStatistic
			else -> l2SimpleNilpotentSuccessStatistic
		}
		stat.record(captureNanos() - before, interpreter.interpreterIndex)
		return success
	}

	/**
	 * A variable may be part of the value, so it could become shared here.
	 */
	override fun mightMakeEscapedVariableShared(
		argumentTypes: List<A_Type>
	): Boolean = true

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				variableReadWriteType(
					mostGeneralMapType(),
					bottom),
				ANY(),
				ANY()),
			TOP())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_CANNOT_READ_UNASSIGNED_VARIABLE,
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE))
}
