/*
 * P_DelayedForkOrphan.kt
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
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.greaterThan
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.formatString
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.bytes
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.execution.Interpreter
import java.util.TimerTask

/**
 * **Primitive:** Schedule a new [fiber][FiberDescriptor] to execute the
 * specified [function][FunctionDescriptor] with the supplied arguments. The
 * fiber will begin running after at least the specified number of milliseconds
 * have elapsed. Do not retain a reference to the new fiber; it is created as an
 * orphan fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_DelayedForkOrphan : Primitive(
	4, CanInline, HasSideEffect, WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(4)
		val (sleepMillis, function, argTuple, priority) = interpreter.argsBuffer

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
			anArg
		}
		// If the sleep time is colossal, then the fiber would never actually
		// start, so exit early.
		if (sleepMillis.greaterThan(fromLong(java.lang.Long.MAX_VALUE)))
		{
			return interpreter.primitiveSuccess(nil)
		}
		// Now that we know that the call will really happen, share the function
		// and the arguments.
		function.makeShared()
		callArgs.forEach { it.makeShared() }
		val current = interpreter.fiber()
		val orphan = newFiber(
			function.kind().returnType,
			interpreter.runtime,
			current.textInterface,
			priority.extractInt)
		{
			formatString(
				"Delayed fork orphan, %s, %s:%d",
				code.methodName,
				if (code.module.isNil) emptyTuple
				else code.module.shortModuleNameNative,
				code.codeStartingLineNumber)
		}
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		orphan.availLoader = current.availLoader
		// Share and inherit any heritable variables.
		orphan.heritableFiberGlobals =
			current.heritableFiberGlobals.makeShared()
		// If the requested sleep time is 0 milliseconds, then fork immediately.
		val runtime = currentRuntime()
		if (sleepMillis.equalsInt(0))
		{
			Interpreter.runOutermostFunction(runtime, orphan, function, callArgs)
		}
		else
		{
			runtime.timer.schedule(
				object : TimerTask()
				{
					override fun run()
					{
						// Don't check for the termination requested interrupt
						// here, since no fiber could have signaled it.
						Interpreter.runOutermostFunction(
							runtime, orphan, function, callArgs)
					}
				},
				sleepMillis.extractLong)
		} // Otherwise, schedule the fiber to start later.
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				inclusive(zero, positiveInfinity),
				functionTypeReturning(TOP.o),
				mostGeneralTupleType,
				bytes),
			TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_INCORRECT_NUMBER_OF_ARGUMENTS, E_INCORRECT_ARGUMENT_TYPE))
}
