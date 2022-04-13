/*
 * P_SocketRead.kt
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

package avail.interpreter.primitive.sockets

import avail.AvailRuntime.Companion.currentRuntime
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.SOCKET_KEY
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.fiber.A_Fiber.Companion.heritableFiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.textInterface
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.Companion.newFiber
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ByteBufferTupleDescriptor
import avail.descriptor.tuples.ByteBufferTupleDescriptor.Companion.tupleForByteBuffer
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.formatString
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.EnumerationTypeDescriptor
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.bytes
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.E_INVALID_HANDLE
import avail.exceptions.AvailErrorCode.E_IO_ERROR
import avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.runOutermostFunction
import avail.io.SimpleCompletionHandler
import java.lang.Integer.MAX_VALUE
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

/**
 * **Primitive:** Initiate an asynchronous read from the
 * [socket][AsynchronousSocketChannel] referenced by the specified
 * [handle][AtomDescriptor]. Create a new [fiber][FiberDescriptor] to respond to
 * the asynchronous completion of the operation; the fiber will run at the
 * specified [priority][IntegerRangeTypeDescriptor.bytes]. If the operation
 * succeeds, then eventually start the new fiber to apply the
 * [success&#32;function][FunctionDescriptor] to the
 * [result&#32;tuple][ByteBufferTupleDescriptor] and a
 * [boolean][EnumerationTypeDescriptor.booleanType] that is
 * [true][AtomDescriptor.trueObject] if the socket is exhausted. If the
 * operation fails, then eventually start the new fiber to apply the
 * [failure&#32;function][FunctionDescriptor] to the numeric
 * [error&#32;code][AvailErrorCode]. Answer the new fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_SocketRead : Primitive(5, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(5)
		val size = interpreter.argument(0)
		val handle = interpreter.argument(1)
		val succeed = interpreter.argument(2)
		val fail = interpreter.argument(3)
		val priority = interpreter.argument(4)
		val pojo = handle.getAtomProperty(SOCKET_KEY.atom)
		if (pojo.isNil)
		{
			return interpreter.primitiveFailure(
				if (handle.isAtomSpecial) E_SPECIAL_ATOM else E_INVALID_HANDLE)
		}
		val socket = pojo.javaObjectNotNull<AsynchronousSocketChannel>()
		val buffer = ByteBuffer.allocateDirect(size.extractInt)
		val current = interpreter.fiber()
		val newFiber = newFiber(
			succeed.kind().returnType.typeUnion(fail.kind().returnType),
			interpreter.runtime,
			current.textInterface,
			priority.extractInt)
		{
			formatString("Socket read, %s", handle.atomName)
		}
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		newFiber.availLoader = current.availLoader
		// Share and inherit any heritable variables.
		newFiber.heritableFiberGlobals =
			current.heritableFiberGlobals.makeShared()
		// Share everything that will potentially be visible to the fiber.
		newFiber.makeShared()
		succeed.makeShared()
		fail.makeShared()
		// Now start the asynchronous read.
		val runtime = currentRuntime()
		return try
		{
			socket.read(
				buffer,
				Unit,
				SimpleCompletionHandler(
					{
						buffer.flip()
						runOutermostFunction(
							runtime,
							newFiber,
							succeed,
							listOf(
								tupleForByteBuffer(buffer),
								objectFromBoolean(value == -1)))
					},
					{
						runOutermostFunction(
							runtime,
							newFiber,
							fail,
							listOf(E_IO_ERROR.numericCode()))
					}))
			interpreter.primitiveSuccess(newFiber)
		}
		catch (e: Throwable)
		{
			interpreter.primitiveFailure(E_IO_ERROR)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				inclusive(0, MAX_VALUE.toLong()),
				ATOM.o,
				functionType(
					tuple(
						zeroOrMoreOf(bytes),
						booleanType),
					TOP.o),
				functionType(
					tuple(instanceType(E_IO_ERROR.numericCode())),
					TOP.o),
				bytes),
			mostGeneralFiberType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_IO_ERROR))
}
