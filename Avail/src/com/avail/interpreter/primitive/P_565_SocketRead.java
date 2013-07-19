/**
 * P_565_SocketRead.java
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

import static com.avail.descriptor.FiberDescriptor.InterruptRequestFlag.TERMINATION_REQUESTED;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 565</strong>: Initiate an asynchronous read from the
 * {@linkplain AsynchronousSocketChannel socket} referenced by the specified
 * {@linkplain AtomDescriptor handle}. Create a new {@linkplain FiberDescriptor
 * fiber} to respond to the asynchronous completion of the operation; the fiber
 * will run at the specified {@linkplain IntegerRangeTypeDescriptor#bytes()
 * priority}. If the operation succeeds, then eventually start the new fiber to
 * apply the {@linkplain FunctionDescriptor success function} to the {@linkplain
 * ByteBufferTupleDescriptor result tuple} and a {@linkplain
 * EnumerationTypeDescriptor#booleanObject() boolean} that is {@linkplain
 * AtomDescriptor#trueObject() true} if the socket is exhausted. If the
 * operation fails, then eventually start the new fiber to apply the {@linkplain
 * FunctionDescriptor failure function} to the {@linkplain IntegerDescriptor
 * numeric} {@linkplain AvailErrorCode error code}. Answer the new fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_565_SocketRead
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_565_SocketRead().init(5, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 5;
		final A_Number size = args.get(0);
		final A_Atom handle = args.get(1);
		final A_Function succeed = args.get(2);
		final A_Function fail = args.get(3);
		final A_Number priority = args.get(4);
		final A_BasicObject pojo =
			handle.getAtomProperty(AtomDescriptor.socketKey());
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				AvailRuntime.isSpecialAtom(handle)
				? E_SPECIAL_ATOM
				: E_INVALID_HANDLE);
		}
		final AsynchronousSocketChannel socket =
			(AsynchronousSocketChannel) pojo.javaObject();
		final ByteBuffer buffer = ByteBuffer.allocateDirect(size.extractInt());
		final A_Fiber current = FiberDescriptor.current();
		final A_Fiber newFiber = FiberDescriptor.newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priority.extractInt(),
			StringDescriptor.from(
				String.format(
					"Socket read (prim 565), %s",
					handle.atomName())));
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
		// Now start the asynchronous read.
		final AvailRuntime runtime = AvailRuntime.current();
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
						// If termination has not been requested, then start the
						// fiber.
						if (!newFiber.getAndClearInterruptRequestFlag(
							TERMINATION_REQUESTED))
						{
							Interpreter.runOutermostFunction(
								runtime,
								newFiber,
								succeed,
								Arrays.asList(
									ByteBufferTupleDescriptor.forByteBuffer(
										buffer),
									AtomDescriptor.objectFromBoolean(
										bytesRead == -1)));
						}
					}

					@Override
					public void failed (
						final @Nullable Throwable killer,
						final @Nullable Void attachment)
					{
						assert killer != null;
						// If termination has not been requested, then start the
						// fiber.
						if (!newFiber.getAndClearInterruptRequestFlag(
							TERMINATION_REQUESTED))
						{
							Interpreter.runOutermostFunction(
								runtime,
								newFiber,
								fail,
								Collections.singletonList(
									E_IO_ERROR.numericCode()));
						}
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
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				IntegerRangeTypeDescriptor.create(
					IntegerDescriptor.zero(),
					true,
					IntegerDescriptor.fromInt(Integer.MAX_VALUE),
					true),
				ATOM.o(),
				FunctionTypeDescriptor.create(
					TupleDescriptor.from(
						TupleTypeDescriptor.zeroOrMoreOf(
							IntegerRangeTypeDescriptor.bytes()),
						EnumerationTypeDescriptor.booleanObject()),
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
				E_IO_ERROR.numericCode()
			).asSet());
	}
}
