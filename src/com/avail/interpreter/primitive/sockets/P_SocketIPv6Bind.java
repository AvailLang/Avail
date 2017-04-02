/**
 * P_SocketIPv6Bind.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.sockets;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.io.IOException;
import java.net.*;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Bind the {@linkplain
 * AsynchronousSocketChannel asynchronous socket channel} referenced by the
 * specified {@linkplain AtomDescriptor handle} to an {@linkplain Inet6Address
 * IPv6 address} and port. The bytes of the address are specified in network
 * byte order, i.e., big-endian.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_SocketIPv6Bind
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_SocketIPv6Bind().init(
			3, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final AvailObject handle = args.get(0);
		final AvailObject addressTuple = args.get(1);
		final AvailObject port = args.get(2);
		final AvailObject pojo =
			handle.getAtomProperty(AtomDescriptor.socketKey());
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				handle.isAtomSpecial()
				? E_SPECIAL_ATOM
				: E_INVALID_HANDLE);
		}
		final AsynchronousSocketChannel socket =
			(AsynchronousSocketChannel) pojo.javaObjectNotNull();
		// Build the big-endian address byte array.
		final byte[] addressBytes = new byte[16];
		for (int i = 0; i < addressBytes.length; i++)
		{
			final AvailObject addressByte = addressTuple.tupleAt(i + 1);
			addressBytes[i] = (byte) addressByte.extractUnsignedByte();
		}
		try
		{
			final Inet4Address inetAddress = (Inet4Address)
				InetAddress.getByAddress(addressBytes);
			final SocketAddress address =
				new InetSocketAddress(inetAddress, port.extractUnsignedShort());
			socket.bind(address);
			return interpreter.primitiveSuccess(NilDescriptor.nil());
		}
		catch (final IllegalStateException e)
		{
			return interpreter.primitiveFailure(E_INVALID_HANDLE);
		}
		catch (final UnknownHostException e)
		{
			// This shouldn't actually happen, since we carefully enforce the
			// range of addresses.
			assert false;
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
		catch (final IOException e)
		{
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
		catch (final SecurityException e)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED);
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ATOM.o(),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.singleInt(16),
					TupleDescriptor.empty(),
					IntegerRangeTypeDescriptor.bytes()),
				IntegerRangeTypeDescriptor.unsignedShorts()),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_IO_ERROR,
				E_PERMISSION_DENIED));
	}
}
