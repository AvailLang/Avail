/**
 * P_551_ServerSocketSetOption.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive;

import static java.net.StandardSocketOptions.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 551</strong>: Set the socket options for the
 * {@linkplain AsynchronousServerSocketChannel asynchronous server socket
 * channel} referenced by the specified {@linkplain AtomDescriptor handle}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_551_ServerSocketSetOption
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_551_ServerSocketSetOption().init(2, CanInline, HasSideEffect);

	/**
	 * A one-based list of the standard socket options.
	 */
	@SuppressWarnings("rawtypes")
	private static final SocketOption[] socketOptions =
		{null, SO_RCVBUF, SO_REUSEADDR};

	@SuppressWarnings("unchecked")
	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final AvailObject handle = args.get(0);
		final AvailObject options = args.get(1);
		final AvailObject pojo =
			handle.getAtomProperty(AtomDescriptor.serverSocketKey());
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				handle.isAtomSpecial()
				? E_SPECIAL_ATOM
				: E_INVALID_HANDLE);
		}
		final AsynchronousServerSocketChannel socket =
			(AsynchronousServerSocketChannel) pojo.javaObject();
		try
		{
			for (final MapDescriptor.Entry entry : options.mapIterable())
			{
				@SuppressWarnings("rawtypes")
				final SocketOption option =
					socketOptions[entry.key().extractInt()];
				final Object value;
				if (option.type().equals(Boolean.class)
					&& entry.value().isBoolean())
				{
					value = entry.value().extractBoolean();
				}
				else if (option.type().equals(Integer.class)
					&& entry.value().isInt())
				{
					value = entry.value().extractInt();
				}
				else
				{
					return interpreter.primitiveFailure(
						E_INCORRECT_ARGUMENT_TYPE);
				}
				socket.setOption(option, value);
			}
			return interpreter.primitiveSuccess(NilDescriptor.nil());
		}
		catch (final IllegalArgumentException e)
		{
			return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE);
		}
		catch (final IOException e)
		{
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ATOM.o(),
				MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
					IntegerRangeTypeDescriptor.inclusive(
						IntegerDescriptor.zero(),
						IntegerDescriptor.fromInt(socketOptions.length - 1)),
					IntegerRangeTypeDescriptor.inclusive(
						IntegerDescriptor.one(),
						IntegerDescriptor.fromInt(socketOptions.length - 1)),
					ANY.o())),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				E_INVALID_HANDLE.numericCode(),
				E_SPECIAL_ATOM.numericCode(),
				E_INCORRECT_ARGUMENT_TYPE.numericCode(),
				E_IO_ERROR.numericCode()
			).asSet());
	}
}
