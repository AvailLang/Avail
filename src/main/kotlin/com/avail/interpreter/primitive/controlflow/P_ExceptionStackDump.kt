/*
 * P_ExceptionStackDump.kt
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

package com.avail.interpreter.primitive.controlflow

import com.avail.descriptor.functions.A_Continuation
import com.avail.descriptor.functions.ContinuationDescriptor.Companion.dumpStackThen
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.objects.ObjectTypeDescriptor
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.exceptionType
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.stackDumpAtom
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import com.avail.exceptions.AvailErrorCode.E_KEY_NOT_FOUND
import com.avail.exceptions.MapException
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.Unknown
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Get the [ObjectTypeDescriptor.stackDumpAtom] associated with
 * the specified [exception][ObjectTypeDescriptor.exceptionType].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_ExceptionStackDump : Primitive(1, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val exception = interpreter.argument(0)

		val runtime = interpreter.runtime
		// The primitive is flagged CanSuspend to force the stack to be reified.
		val continuation : A_Continuation =
			try { exception.fieldAt(stackDumpAtom()) }
			catch (e: MapException)
			{
				assert(e.numericCode.extractInt()
					       == E_KEY_NOT_FOUND.nativeCode())
				return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE)
			}

		val textInterface = interpreter.fiber().textInterface()
		return interpreter.suspendThen {
			dumpStackThen(runtime, textInterface, continuation)
			{	stack ->
				succeed(tupleFromList(stack.indices.reversed().map {
					stringFrom(stack[it])
				}))
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(exceptionType()), zeroOrMoreOf(stringType()))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_INCORRECT_ARGUMENT_TYPE))
}
