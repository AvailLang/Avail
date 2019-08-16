/*
 * P_SocketRead.java
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

package com.avail.interpreter.primitive.sockets;

import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Arrays;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.SOCKET_KEY;
import static com.avail.descriptor.AtomDescriptor.objectFromBoolean;
import static com.avail.descriptor.ByteBufferTupleDescriptor.tupleForByteBuffer;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FiberDescriptor.newFiber;
import static com.avail.descriptor.FiberTypeDescriptor.mostGeneralFiberType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.bytes;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;
import static java.lang.Integer.MAX_VALUE;
import static java.util.Collections.singletonList;

/**
 * <strong>Primitive:</strong> Initiate an asynchronous read from the
 * {@linkplain AsynchronousSocketChannel socket} referenced by the specified
 * {@linkplain AtomDescriptor handle}. Create a new {@linkplain FiberDescriptor
 * fiber} to respond to the asynchronous completion of the operation; the fiber
 * will run at the specified {@linkplain IntegerRangeTypeDescriptor#bytes()
 * priority}. If the operation succeeds, then eventually start the new fiber to
 * apply the {@linkplain FunctionDescriptor success function} to the {@linkplain
 * ByteBufferTupleDescriptor result tuple} and a {@linkplain
 * EnumerationTypeDescriptor#booleanType() boolean} that is {@linkplain
 * AtomDescriptor#trueObject() true} if the socket is exhausted. If the
 * operation fails, then eventually start the new fiber to apply the {@linkplain
 * FunctionDescriptor failure function} to the {@linkplain IntegerDescriptor
 * numeric} {@linkplain AvailErrorCode error code}. Answer the new fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_SocketRead
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_SocketRead().init(
			5, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(5);
		final A_Number size = interpreter.argument(0);
		final A_Atom handle = interpreter.argument(1);
		final A_Function succeed = interpreter.argument(2);
		final A_Function fail = interpreter.argument(3);
		final A_Number priority = interpreter.argument(4);
		final A_BasicObject pojo =
			handle.getAtomProperty(SOCKET_KEY.atom);
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				handle.isAtomSpecial() ? E_SPECIAL_ATOM : E_INVALID_HANDLE);
		}
		final AsynchronousSocketChannel socket = pojo.javaObjectNotNull();
		final ByteBuffer buffer = ByteBuffer.allocateDirect(size.extractInt());
		final A_Fiber current = interpreter.fiber();
		final A_Fiber newFiber = newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priority.extractInt(),
			() ->
				formatString("Socket read, %s", handle.atomName()));
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		newFiber.availLoader(current.availLoader());
		// Share and inherit any heritable variables.
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared());
		// Inherit the fiber's text interface.
		newFiber.textInterface(current.textInterface());
		// Share everything that will potentially be visible to the fiber.
		newFiber.makeShared();
		succeed.makeShared();
		fail.makeShared();
		// Now start the asynchronous read.
		final AvailRuntime runtime = currentRuntime();
		try
		{
			socket.read(
				buffer,
				null,
				new CompletionHandler<Integer, Void>()
				{
					@Override
					public void completed (
						final @Nullable Integer bytesRead,
						final @Nullable Void unused)
					{
						assert bytesRead != null;
						buffer.flip();
						Interpreter.runOutermostFunction(
							runtime,
							newFiber,
							succeed,
							Arrays.asList(
								tupleForByteBuffer(buffer),
								objectFromBoolean(bytesRead == -1)));
					}

					@Override
					public void failed (
						final @Nullable Throwable killer,
						final @Nullable Void attachment)
					{
						assert killer != null;
						Interpreter.runOutermostFunction(
							runtime,
							newFiber,
							fail,
							singletonList(
								E_IO_ERROR.numericCode()));
					}
				});
		}
		catch (final IllegalArgumentException e)
		{
			// This should only happen if the buffer is read only, which is
			// impossible by construction here.
			assert false;
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
		catch (final IllegalStateException e)
		{
			return interpreter.primitiveFailure(E_INVALID_HANDLE);
		}
		return interpreter.primitiveSuccess(newFiber);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				inclusive(0, MAX_VALUE),
				ATOM.o(),
				functionType(
					tuple(
						zeroOrMoreOf(bytes()),
						booleanType()),
					TOP.o()),
				functionType(
					tuple(instanceType(E_IO_ERROR.numericCode())),
					TOP.o()),
				bytes()),
			mostGeneralFiberType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_IO_ERROR));
	}
}
