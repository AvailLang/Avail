/*
 * P_SocketIPv6Connect.kt
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

package com.avail.interpreter.primitive.sockets

import com.avail.AvailRuntime.currentRuntime
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import com.avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.SOCKET_KEY
import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.fiber.FiberDescriptor.Companion.newFiber
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import com.avail.descriptor.tuples.StringDescriptor.Companion.formatString
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.bytes
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.singleInt
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.unsignedShorts
import com.avail.descriptor.types.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import com.avail.exceptions.AvailErrorCode.E_INVALID_HANDLE
import com.avail.exceptions.AvailErrorCode.E_IO_ERROR
import com.avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import com.avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.execution.Interpreter.Companion.runOutermostFunction
import com.avail.io.SimpleCompletionHandler
import com.avail.io.SimpleCompletionHandler.Dummy.Companion.dummy
import java.net.Inet6Address
import java.net.Inet6Address.getByAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.nio.channels.AsynchronousSocketChannel
import java.util.Collections.emptyList

/**
 * **Primitive:** Connect the [AsynchronousSocketChannel] referenced by the
 * specified [handle][A_Atom] to an [IPv6&#32;address][Inet6Address] and
 * port. Create a new [fiber][FiberDescriptor] to respond to the asynchronous
 * completion of the operation; the fiber will run at the specified
 * [priority][IntegerRangeTypeDescriptor.bytes]. If the operation succeeds, then
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
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				if (handle.isAtomSpecial())
					E_SPECIAL_ATOM
				else
					E_INVALID_HANDLE)
		}
		val socket = pojo.javaObjectNotNull<AsynchronousSocketChannel>()
		// Build the big-endian address byte array.
		val addressBytes = ByteArray(16) {
			addressTuple.tupleAt(it + 1).extractUnsignedByte().toByte()
		}
		val address = try
		{
			InetSocketAddress(
				getByAddress(addressBytes) as Inet6Address,
				port.extractUnsignedShort())
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
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priority.extractInt()
		) {
			formatString(
				"Socket IPv6 connect, %s:%d",
				addressTuple.toString(),
				port.extractInt())
		}
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		newFiber.setAvailLoader(current.availLoader())
		// Share and inherit any heritable variables.
		newFiber.setHeritableFiberGlobals(
			current.heritableFiberGlobals().makeShared())
		// Inherit the fiber's text interface.
		newFiber.setTextInterface(current.textInterface())
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
				dummy,
				SimpleCompletionHandler(
					{
						runOutermostFunction(
							runtime, newFiber, succeed, emptyList())
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
		catch (e: SecurityException)
		{
			interpreter.primitiveFailure(E_PERMISSION_DENIED)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tupleFromArray(
				ATOM.o(),
				tupleTypeForSizesTypesDefaultType(
					singleInt(16), emptyTuple(), bytes()),
				unsignedShorts(),
				functionType(
					emptyTuple(),
					TOP.o()),
				functionType(
					tuple(instanceType(E_IO_ERROR.numericCode())),
					TOP.o()),
				bytes()),
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
