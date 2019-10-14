/*
 * P_DelayedForkOrphan.java
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

package com.avail.interpreter.primitive.fibers

import com.avail.AvailRuntime.currentRuntime
import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.AvailObject
import com.avail.descriptor.FiberDescriptor
import com.avail.descriptor.FiberDescriptor.newFiber
import com.avail.descriptor.FunctionDescriptor
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.FunctionTypeDescriptor.functionTypeReturning
import com.avail.descriptor.InfinityDescriptor.positiveInfinity
import com.avail.descriptor.IntegerDescriptor.fromLong
import com.avail.descriptor.IntegerDescriptor.zero
import com.avail.descriptor.IntegerRangeTypeDescriptor.bytes
import com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.StringDescriptor.formatString
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import java.util.*

/**
 * **Primitive:** Schedule a new [ fiber][FiberDescriptor] to execute the specified [function][FunctionDescriptor] with
 * the supplied arguments. The fiber will begin running after at least the
 * specified number of milliseconds have elapsed. Do not retain a reference to
 * the new fiber; it is created as an orphan fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_DelayedForkOrphan : Primitive(4, CanInline, HasSideEffect)
{

	override fun attempt(
		interpreter: Interpreter): Result
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
			return interpreter.primitiveFailure(
				E_INCORRECT_NUMBER_OF_ARGUMENTS)
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
		// If the sleep time is colossal, then the fiber would never actually
		// start, so exit early.
		if (sleepMillis.greaterThan(fromLong(java.lang.Long.MAX_VALUE)))
		{
			return interpreter.primitiveSuccess(nil)
		}
		// Now that we know that the call will really happen, share the function
		// and the arguments.
		function.makeShared()
		for (arg in callArgs)
		{
			arg.makeShared()
		}
		val current = interpreter.fiber()
		val orphan = newFiber(
			function.kind().returnType(),
			priority.extractInt()
		) {
			formatString(
				"Delayed fork orphan, %s, %s:%d",
				code.methodName(),
				if (code.module().equalsNil())
					emptyTuple()
				else
					code.module().moduleName(),
				code.startingLineNumber())
		}
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		orphan.availLoader(current.availLoader())
		// Share and inherit any heritable variables.
		orphan.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared())
		// Inherit the fiber's text interface.
		orphan.textInterface(current.textInterface())
		// If the requested sleep time is 0 milliseconds, then fork immediately.
		val runtime = currentRuntime()
		if (sleepMillis.equalsInt(0))
		{
			Interpreter.runOutermostFunction(
				runtime, orphan, function, callArgs)
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
				sleepMillis.extractLong())
		}// Otherwise, schedule the fiber to start later.
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				inclusive(zero(), positiveInfinity()),
				functionTypeReturning(TOP.o()),
				mostGeneralTupleType(),
				bytes()),
			TOP.o())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_INCORRECT_ARGUMENT_TYPE))
	}

}