/*
 * P_SocketIPv6Connect.kt
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

package avail.interpreter.primitive.sockets

import avail.AvailRuntime.Companion.currentRuntime
import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.SOCKET_KEY
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.fiber.A_Fiber.Companion.heritableFiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.textInterface
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.Companion.newFiber
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.extractUnsignedByte
import avail.descriptor.numbers.A_Number.Companion.extractUnsignedShort
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import avail.descriptor.tuples.StringDescriptor.Companion.formatString
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u8
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u16
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import avail.exceptions.AvailErrorCode.E_INVALID_HANDLE
import avail.exceptions.AvailErrorCode.E_IO_ERROR
import avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.execution.Interpreter
import avail.io.SimpleCompletionHandler
import java.net.Inet6Address
import java.net.Inet6Address.getByAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.nio.channels.AsynchronousSocketChannel

/**
 * **Primitive:** Connect the [AsynchronousSocketChannel] referenced by the
 * specified [handle][A_Atom] to an [IPv6&#32;address][Inet6Address] and
 * port. Create a new [fiber][FiberDescriptor] to respond to the asynchronous
 * completion of the operation; the fiber will run at the specified
 * [priority][IntegerRangeTypeDescriptor.u8]. If the operation succeeds, then
 * eventually start the new fiber to apply the
 * [success&#32;function][FunctionDescriptor]. If the operation fails, then
 * eventually start the new fiber to apply the
 * [failure&#32;function][FunctionDescriptor] to the numeric
 * [error&#32;code][AvailErrorCode]. Answer the new fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_SocketIPv6Connect : Primitive(6, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(6)
		val handle = interpreter.argument(0)
		val addressTuple = interpreter.argument(1)
		val port = interpreter.argument(2)
		val succeed = interpreter.argument(3)
		val fail = interpreter.argument(4)
		val priority = interpreter.argument(5)
		val pojo = handle.getAtomProperty(SOCKET_KEY.atom)
		if (pojo.isNil)
		{
			return interpreter.primitiveFailure(
				if (handle.isAtomSpecial) E_SPECIAL_ATOM
				else E_INVALID_HANDLE)
		}
		val socket = pojo.javaObjectNotNull<AsynchronousSocketChannel>()
		// Build the big-endian address byte array.
		val addressBytes = ByteArray(16) {
			addressTuple.tupleAt(it + 1).extractUnsignedByte.toByte()
		}
		val address = try
		{
			InetSocketAddress(
				getByAddress(addressBytes) as Inet6Address,
				port.extractUnsignedShort)
		}
		catch (e: UnknownHostException)
		{
			// This shouldn't actually happen, since we carefully enforce the
			// range of addresses.
			assert(false)
			return interpreter.primitiveFailure(E_IO_ERROR)
		}

		val current = interpreter.fiber()
		val newFiber = newFiber(
			succeed.kind().returnType.typeUnion(fail.kind().returnType),
			interpreter.runtime,
			current.textInterface,
			priority.extractInt)
		{
			formatString(
				"Socket IPv6 connect, %s:%d",
				addressTuple.toString(),
				port.extractInt)
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
		// Now start the asynchronous connect.
		val runtime = currentRuntime()
		return try
		{
			socket.connect(
				address,
				Unit,
				SimpleCompletionHandler(
					{
						runtime.runOutermostFunction(
							newFiber, succeed, emptyList(), false)
					},
					{
						runtime.runOutermostFunction(
							newFiber,
							fail,
							listOf(E_IO_ERROR.numericCode()),
							false)
					}))
			interpreter.primitiveSuccess(newFiber)
		}
		catch (e: SecurityException)
		{
			interpreter.primitiveFailure(E_PERMISSION_DENIED)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tupleFromArray(
				ATOM.o,
				tupleTypeForSizesTypesDefaultType(
					singleInt(16), emptyTuple, u8),
				u16,
				functionType(
					emptyTuple,
					TOP.o),
				functionType(
					tuple(instanceType(E_IO_ERROR.numericCode())),
					TOP.o),
				u8),
			mostGeneralFiberType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_INCORRECT_ARGUMENT_TYPE,
				E_IO_ERROR,
				E_PERMISSION_DENIED))
}
