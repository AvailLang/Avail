/**
 * P_562_SocketIPv6Connect.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 562</strong>: Connect the {@linkplain
 * AsynchronousSocketChannel asynchronous socket} reference by the specified
 * {@linkplain AtomDescriptor handle} to an {@linkplain Inet6Address IPv6
 * address} and port. Create a new {@linkplain FiberDescriptor fiber} to respond
 * to the asynchronous completion of the operation; the fiber will run at the
 * specified {@linkplain IntegerRangeTypeDescriptor#bytes() priority}. If the
 * operation succeeds, then eventually start the new fiber to apply the
 * {@linkplain FunctionDescriptor success function}. If the operation fails,
 * then eventually start the new fiber to apply the {@linkplain
 * FunctionDescriptor failure function} to the {@linkplain IntegerDescriptor
 * numeric} {@linkplain AvailErrorCode error code}. Answer the new fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_562_SocketIPv6Connect
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_562_SocketIPv6Connect().init(6, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 6;
		final A_Atom handle = args.get(0);
		final A_Tuple addressTuple = args.get(1);
		final A_Number port = args.get(2);
		final A_Function succeed = args.get(3);
		final A_Function fail = args.get(4);
		final A_Number priority = args.get(5);
		final A_BasicObject pojo =
			handle.getAtomProperty(AtomDescriptor.socketKey());
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				handle.isAtomSpecial()
				? E_SPECIAL_ATOM
				: E_INVALID_HANDLE);
		}
		final AsynchronousSocketChannel socket =
			(AsynchronousSocketChannel) pojo.javaObject();
		// Build the big-endian address byte array.
		final byte[] addressBytes = new byte[16];
		for (int i = 0; i < addressBytes.length; i++)
		{
			final A_Number addressByte = addressTuple.tupleAt(i + 1);
			addressBytes[i] = (byte) addressByte.extractUnsignedByte();
		}
		final SocketAddress address;
		try
		{
			final Inet6Address inetAddress = (Inet6Address)
				InetAddress.getByAddress(addressBytes);
			address = new InetSocketAddress(
				inetAddress, port.extractUnsignedShort());
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
		final A_Fiber current = interpreter.fiber();
		final A_Fiber newFiber = FiberDescriptor.newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priority.extractInt(),
			StringDescriptor.from(
				String.format(
					"Socket IPv6 connect (prim 562), %s:%d",
					addressTuple.toString(),
					port.extractInt())));
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		newFiber.availLoader(current.availLoader());
		// Share and inherit any heritable variables.
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared());
		// Share everything that will potentially be visible to the fiber.
		newFiber.makeShared();
		succeed.makeShared();
		fail.makeShared();
		// Now start the asynchronous connect.
		final AvailRuntime runtime = AvailRuntime.current();
		try
		{
			socket.connect(
				address,
				null,
				new CompletionHandler<Void, Void>()
				{
					@Override
					public void completed (
						final @Nullable Void unused1,
						final @Nullable Void unused2)
					{
						Interpreter.runOutermostFunction(
							runtime,
							newFiber,
							succeed,
							Collections.<AvailObject>emptyList());
					}

					@Override
					public void failed (
						final @Nullable Throwable killer,
						final @Nullable Void unused)
					{
						assert killer != null;
						Interpreter.runOutermostFunction(
							runtime,
							newFiber,
							fail,
							Collections.singletonList(
								E_IO_ERROR.numericCode()));
					}
				});
		}
		catch (final IllegalArgumentException e)
		{
			return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE);
		}
		catch (final IllegalStateException e)
		{
			return interpreter.primitiveFailure(E_INVALID_HANDLE);
		}
		catch (final SecurityException e)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED);
		}
		return interpreter.primitiveSuccess(newFiber);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ATOM.o(),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.create(
						IntegerDescriptor.fromInt(16),
						true,
						IntegerDescriptor.fromInt(16),
						true),
					TupleDescriptor.empty(),
					IntegerRangeTypeDescriptor.bytes()),
				IntegerRangeTypeDescriptor.unsignedShorts(),
				FunctionTypeDescriptor.create(
					TupleDescriptor.empty(),
					TOP.o()),
				FunctionTypeDescriptor.create(
					TupleDescriptor.from(
						AbstractEnumerationTypeDescriptor.withInstance(
							E_IO_ERROR.numericCode())),
					TOP.o()),
				IntegerRangeTypeDescriptor.bytes()),
			FiberTypeDescriptor.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				E_INVALID_HANDLE.numericCode(),
				E_SPECIAL_ATOM.numericCode(),
				E_INCORRECT_ARGUMENT_TYPE.numericCode(),
				E_IO_ERROR.numericCode(),
				E_PERMISSION_DENIED.numericCode()
			).asSet());
	}
}
