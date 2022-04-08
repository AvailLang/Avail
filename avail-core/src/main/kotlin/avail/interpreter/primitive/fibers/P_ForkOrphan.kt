/*
 * P_ForkOrphan.kt
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

package avail.interpreter.primitive.fibers

import avail.AvailRuntime.Companion.currentRuntime
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.fiber.A_Fiber.Companion.heritableFiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.textInterface
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.Companion.newFiber
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.module.A_Module.Companion.shortModuleNameNative
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.formatString
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.bytes
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.runOutermostFunction

/**
 * **Primitive:** Fork a new [fiber][FiberDescriptor] to execute the specified
 * [function][FunctionDescriptor] with the supplied arguments. Do not retain a
 * reference to the new fiber; it is created as an orphan fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_ForkOrphan : Primitive(
	3, CanInline, HasSideEffect, WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val (function, argTuple, priority) = interpreter.argsBuffer

		// Ensure that the function is callable with the specified arguments.
		val numArgs = argTuple.tupleSize
		val code = function.code()
		if (code.numArgs() != numArgs)
		{
			return interpreter.primitiveFailure(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		val tupleType = function.kind().argsTupleType
		val callArgs = (1 .. numArgs).map {
			val anArg = argTuple.tupleAt(it)
			if (!anArg.isInstanceOf(tupleType.typeAtIndex(it)))
			{
				return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE)
			}
			anArg.makeShared()
		}
		function.makeShared()
		val current = interpreter.fiber()
		val orphan = newFiber(
			interpreter.runtime,
			function.kind().returnType,
			priority.extractInt)
		{
			formatString(
				"Fork orphan, %s, %s:%d",
				code.methodName,
				if (code.module.isNil) "no module"
				else code.module.shortModuleNameNative,
				code.codeStartingLineNumber)
		}
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		orphan.availLoader = current.availLoader
		// Share and inherit any heritable variables.
		orphan.heritableFiberGlobals =
			current.heritableFiberGlobals.makeShared()
		// Inherit the fiber's text interface.
		orphan.textInterface = current.textInterface
		// Schedule the fiber to run the specified function.
		runOutermostFunction(currentRuntime(), orphan, function, callArgs)
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				functionTypeReturning(TOP.o),
				mostGeneralTupleType,
				bytes),
			TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_INCORRECT_NUMBER_OF_ARGUMENTS, E_INCORRECT_ARGUMENT_TYPE))
}
