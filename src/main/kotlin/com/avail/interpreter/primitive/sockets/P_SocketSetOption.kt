/*
 * P_SocketSetOption.java
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
import com.avail.descriptor.AtomDescriptor
import com.avail.descriptor.AtomDescriptor.SpecialAtom.SOCKET_KEY
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive
import com.avail.descriptor.MapTypeDescriptor.mapTypeForSizesKeyTypeValueType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TypeDescriptor.Types.*
import com.avail.exceptions.AvailErrorCode.*
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.utility.Casts.cast
import java.io.IOException
import java.net.SocketOption
import java.net.StandardSocketOptions.*
import java.nio.channels.AsynchronousSocketChannel

/**
 * **Primitive:** Set the socket options for the
 * [asynchronous socket channel][AsynchronousSocketChannel] referenced
 * by the specified [handle][AtomDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_SocketSetOption : Primitive(2, CanInline, HasSideEffect)
{
	/**
	 * A one-based list of the standard socket options.
	 */
	private val socketOptions = arrayOf<SocketOption<*>?>(
		null, SO_RCVBUF, SO_REUSEADDR, SO_SNDBUF, SO_KEEPALIVE, TCP_NODELAY)

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val handle = interpreter.argument(0)
		val options = interpreter.argument(1)
		val pojo = handle.getAtomProperty(SOCKET_KEY.atom)
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				if (handle.isAtomSpecial) E_SPECIAL_ATOM else E_INVALID_HANDLE)
		}
		val socket = pojo.javaObjectNotNull<AsynchronousSocketChannel>()
		try
		{
			for (entry in options.mapIterable())
			{
				val key = entry.key()
				val value = entry.value()
				socketOptions[key.extractInt()]?.let { option ->
					val type = option.type()
					if (type === java.lang.Boolean::class.java
						&& value.isBoolean)
					{
						val booleanOption: SocketOption<Boolean> = cast(option)
						socket.setOption(booleanOption, value.extractBoolean())
					}
					else if (type === java.lang.Integer::class.java
						&& value.isInt)
					{
						val intOption: SocketOption<Int> = cast(option)
						socket.setOption(intOption, value.extractInt())
					}
					else
					{
						return interpreter.primitiveFailure(
							E_INCORRECT_ARGUMENT_TYPE)
					}
				}
			}
			return interpreter.primitiveSuccess(nil)
		}
		catch (e: IllegalArgumentException)
		{
			return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE)
		}
		catch (e: IOException)
		{
			return interpreter.primitiveFailure(E_IO_ERROR)
		}

	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				ATOM.o(),
				mapTypeForSizesKeyTypeValueType(
					inclusive(0, (socketOptions.size - 1).toLong()),
					inclusive(1, (socketOptions.size - 1).toLong()),
					ANY.o())),
			TOP.o())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_INCORRECT_ARGUMENT_TYPE,
				E_IO_ERROR))
	}

}