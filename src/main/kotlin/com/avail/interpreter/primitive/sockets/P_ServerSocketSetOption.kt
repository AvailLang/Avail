/*
 * P_ServerSocketSetOption.kt
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

package com.avail.interpreter.primitive.sockets

import com.avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import com.avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import com.avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.SERVER_SOCKET_KEY
import com.avail.descriptor.maps.A_Map.Companion.mapIterable
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import com.avail.descriptor.types.MapTypeDescriptor.Companion.mapTypeForSizesKeyTypeValueType
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import com.avail.exceptions.AvailErrorCode.E_INVALID_HANDLE
import com.avail.exceptions.AvailErrorCode.E_IO_ERROR
import com.avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.execution.Interpreter
import com.avail.utility.cast
import java.io.IOException
import java.net.SocketOption
import java.net.StandardSocketOptions.SO_RCVBUF
import java.net.StandardSocketOptions.SO_REUSEADDR
import java.nio.channels.AsynchronousServerSocketChannel

/**
 * **Primitive:** Set the socket options for the
 * [asynchronous&#32;server&#32;socket][AsynchronousServerSocketChannel]
 * referenced by the specified [handle][AtomDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_ServerSocketSetOption : Primitive(2, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val handle = interpreter.argument(0)
		val options = interpreter.argument(1)
		val pojo = handle.getAtomProperty(SERVER_SOCKET_KEY.atom)
		if (pojo.isNil)
		{
			return interpreter.primitiveFailure(
				if (handle.isAtomSpecial) E_SPECIAL_ATOM
				else E_INVALID_HANDLE)
		}
		val socket = pojo.javaObjectNotNull<AsynchronousServerSocketChannel>()
		return try
		{
			for ((key, value) in options.mapIterable)
			{
				val option = Options.socketOptions[key.extractInt]!!
				if (option.type() == java.lang.Boolean::class.java
						&& value.isBoolean)
						{
					val booleanOption: SocketOption<Boolean> = option.cast()
					socket.setOption(booleanOption, value.extractBoolean)
				}
				else if (option.type() == java.lang.Integer::class.java
						&& value.isInt)
				{
					val intOption: SocketOption<Int> = option.cast()
					socket.setOption(intOption, value.extractInt)
				}
				else return interpreter.primitiveFailure(
					E_INCORRECT_ARGUMENT_TYPE)
			}
			interpreter.primitiveSuccess(nil)
		}
		catch (e: IllegalArgumentException)
		{
			interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE)
		}
		catch (e: IOException)
		{
			interpreter.primitiveFailure(E_IO_ERROR)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				ATOM.o,
				mapTypeForSizesKeyTypeValueType(
					inclusive(0, (Options.socketOptions.size - 1).toLong()),
					inclusive(1, (Options.socketOptions.size - 1).toLong()),
					ANY.o)),
			TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_INCORRECT_ARGUMENT_TYPE,
				E_IO_ERROR))

	/**
	 * Protect the creation of this constant array, since if we just make it a
	 * field of the outer object, it gets initialized *after* it's needed by
	 * [privateBlockTypeRestriction].  That's because the supertype, Primitive,
	 * invokes that function during instance initialization, which happens
	 * before the subtype (the object) gets a chance to do its initialization.
	 */
	object Options
	{
		/** A one-based list of the standard socket options. */
		val socketOptions =
			arrayOf<SocketOption<*>?>(null, SO_RCVBUF, SO_REUSEADDR)
	}
}
