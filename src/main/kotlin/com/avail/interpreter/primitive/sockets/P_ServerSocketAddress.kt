/*
 * P_ServerSocketAddress.kt
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

package com.avail.interpreter.primitive.sockets

import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.ByteArrayTupleDescriptor.tupleForByteArray
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerDescriptor.fromInt
import com.avail.descriptor.IntegerRangeTypeDescriptor.*
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType
import com.avail.descriptor.TupleTypeDescriptor.tupleTypeForTypes
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.SERVER_SOCKET_KEY
import com.avail.exceptions.AvailErrorCode.*
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.ClosedChannelException

/**
 * **Primitive:** Answer the [socket&#32;address][InetSocketAddress] of the
 * [AsynchronousServerSocketChannel] referenced by the specified
 * [handle][AtomDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_ServerSocketAddress : Primitive(1, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val handle = interpreter.argument(0)
		val pojo = handle.getAtomProperty(SERVER_SOCKET_KEY.atom)
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				if (handle.isAtomSpecial)
					E_SPECIAL_ATOM
				else
					E_INVALID_HANDLE)
		}
		val socket = pojo.javaObjectNotNull<AsynchronousServerSocketChannel>()
		val peer: InetSocketAddress = try
		{
			socket.localAddress as InetSocketAddress
		}
		catch (e: ClosedChannelException)
		{
			return interpreter.primitiveFailure(E_INVALID_HANDLE)
		}
		catch (e: IOException)
		{
			return interpreter.primitiveFailure(E_IO_ERROR)
		}

		return interpreter.primitiveSuccess(
			tuple(tupleForByteArray(peer.address.address), fromInt(peer.port)))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(ATOM.o()),
			tupleTypeForTypes(
				tupleTypeForSizesTypesDefaultType(
					inclusive(4, 16),
					emptyTuple(),
					bytes()),
				unsignedShorts()))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_IO_ERROR))
}