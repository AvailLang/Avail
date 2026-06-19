/*
 * P_MapReplacingNAryKey.kt
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

package avail.interpreter.primitive.maps

import avail.descriptor.maps.MapDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.MapTypeDescriptor.Companion.mostGeneralMapType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import avail.exceptions.AvailErrorCode.E_KEY_NOT_FOUND
import avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import avail.exceptions.AvailException
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive3

/**
 * **Primitive:** Replace the value at the location indicated by the path
 * [tuple][TupleDescriptor] of the target [map][MapDescriptor] with a new value.
 *
 * @author Rich &lt;rich@availlang.org&gt;
 */
@Suppress("unused")
object P_MapReplacingNAryKey : Primitive3(CanInline, CanFold)
{
	override fun Interpreter.attempt3(
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?
	{
		val map = arg1
		val pathTuple = arg2
		val newValue = arg3
		return try
		{
			map.recursivelyUpdate(pathTuple.iterator()) { newValue }
		}
		catch (e: AvailException)
		{
			fail(e.errorCode)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(mostGeneralMapType(), oneOrMoreOf(ANY()), ANY()),
			mostGeneralMapType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_SUBSCRIPT_OUT_OF_BOUNDS,
				E_INCORRECT_ARGUMENT_TYPE,
				E_KEY_NOT_FOUND))
}
