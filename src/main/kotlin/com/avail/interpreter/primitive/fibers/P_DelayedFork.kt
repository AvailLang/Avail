/*
 * P_DelayedFork.kt
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

package com.avail.interpreter.primitive.fibers

import com.avail.AvailRuntime.currentRuntime
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.FiberDescriptor
import com.avail.descriptor.FiberDescriptor.newFiber
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.sets.SetDescriptor.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.StringDescriptor.formatString
import com.avail.descriptor.tuples.TupleDescriptor.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.FiberTypeDescriptor.mostGeneralFiberType
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.FunctionTypeDescriptor.functionTypeReturning
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.bytes
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.inclusive
import com.avail.descriptor.types.TupleTypeDescriptor.mostGeneralTupleType
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Interpreter.runOutermostFunction
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import java.util.*

/**
 * **Primitive:** Schedule a new [fiber][FiberDescriptor] to execute the
 * specified [function][FunctionDescriptor] with the supplied arguments. The
 * fiber will begin running after at least the specified number of milliseconds
 * have elapsed. Answer the new fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_DelayedFork : Primitive(
	4, CanInline, HasSideEffect, WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(4)
		val sleepMillis = interpreter.argument(0)
		val function = interpreter.argument(1)
		val argTuple = interpreter.argument(2)
		val priority = interpreter.argument(3)

		// Ensure that the function is callable with the specified arguments.
		val numArgs = argTuple.tupleSize()
		val code = function.code()
		if (code.numArgs() != numArgs)
		{
			return interpreter.primitiveFailure(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		val callArgs = ArrayList<AvailObject>(numArgs)
		val tupleType = function.kind().argsTupleType()
		for (i in 1 .. numArgs)
		{
			val anArg = argTuple.tupleAt(i)
			if (!anArg.isInstanceOf(tupleType.typeAtIndex(i)))
			{
				return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE)
			}
			callArgs.add(anArg)
		}
		// Now that we know that the call will really happen, share the function
		// and the arguments.
		function.makeShared()
		for (arg in callArgs)
		{
			arg.makeShared()
		}
		val current = interpreter.fiber()
		val newFiber = newFiber(
			function.kind().returnType(),
			priority.extractInt())
		{
			formatString(
				"Delayed fork, %s, %s:%d",
				code.methodName(),
				if (code.module().equalsNil())
					emptyTuple()
				else
					code.module().moduleName(),
				code.startingLineNumber())
		}
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		newFiber.availLoader(current.availLoader())
		// Share and inherit any heritable variables.
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared())
		// Inherit the fiber's text interface.
		newFiber.textInterface(current.textInterface())
		// Share the fiber, since it will be visible in the caller.
		newFiber.makeShared()
		// If the requested sleep time is 0 milliseconds, then fork immediately.
		if (sleepMillis.equalsInt(0))
		{
			runOutermostFunction(currentRuntime(), newFiber, function, callArgs)
		}
		else if (sleepMillis.isLong)
		{
			val runtime = interpreter.runtime()
			runtime.timer.schedule(
				object : TimerTask()
				{
					override fun run()
					{
						runOutermostFunction(
							runtime, newFiber, function, callArgs)
					}
				},
				sleepMillis.extractLong())
		}// Otherwise, if the delay time isn't colossal, then schedule the fiber
		// to start later.
		return interpreter.primitiveSuccess(newFiber)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				inclusive(zero(), positiveInfinity()),
				functionTypeReturning(TOP.o()),
				mostGeneralTupleType(),
				bytes()),
			mostGeneralFiberType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_INCORRECT_NUMBER_OF_ARGUMENTS, E_INCORRECT_ARGUMENT_TYPE))
}
