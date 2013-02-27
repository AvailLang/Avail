/**
 * P_566_SocketWrite.java
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
 * <strong>Primitive 566</strong>: Initiate an asynchronous write from the
 * {@linkplain AsynchronousSocketChannel socket} referenced by the specified
 * {@linkplain AtomDescriptor handle}. Create a new {@linkplain FiberDescriptor
 * fiber} to respond to the asynchronous completion of the operation; the fiber
 * will run at the specified {@linkplain IntegerRangeTypeDescriptor#bytes()
 * priority}. If the operation succeeds, then eventually start the new fiber to
 * apply the {@linkplain FunctionDescriptor success function}. If the operation
 * fails, then eventually start the new fiber to apply the {@linkplain
 * FunctionDescriptor failure function} to the {@linkplain IntegerDescriptor
 * numeric} {@linkplain AvailErrorCode error code}. Answer the new fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_566_SocketWrite
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_566_SocketWrite().init(5, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 5;
		final AvailObject tuple = args.get(0);
		final AvailObject handle = args.get(1);
		final AvailObject succeed = args.get(2);
		final AvailObject fail = args.get(3);
		final AvailObject priority = args.get(4);
		final AvailObject pojo =
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
		// Obtain a buffer for writing.
		final ByteBuffer buffer;
		if (tuple.isByteBufferTuple())
		{
			buffer = tuple.byteBuffer();
			buffer.rewind();
		}
		else if (tuple.isByteArrayTuple())
		{
			buffer = ByteBuffer.wrap(tuple.byteArray());
		}
		else if (tuple.isByteTuple())
		{
			buffer = ByteBuffer.allocateDirect(tuple.tupleSize());
			for (int i = 1, end = tuple.tupleSize(); i <= end; i++)
			{
				buffer.put((byte) tuple.rawByteAt(i));
			}
			buffer.flip();
		}
		else
		{
			buffer = ByteBuffer.allocateDirect(tuple.tupleSize());
			for (int i = 1, end = tuple.tupleSize(); i <= end; i++)
			{
				buffer.put((byte) tuple.tupleAt(i).extractInt());
			}
			buffer.flip();
		}
		final A_BasicObject current = FiberDescriptor.current();
		final AvailObject newFiber =
			FiberDescriptor.newFiber(priority.extractInt());
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		newFiber.availLoader(current.availLoader());
		// Don't inherit the success continuation, but inherit the failure
		// continuation. Only loader fibers should have something real plugged
		// into this field, and none of them should fail because of a Java
		// exception.
		newFiber.failureContinuation(current.failureContinuation());
		// Share and inherit any heritable variables.
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared());
		// Share everything that will potentially be visible to the fiber.
		newFiber.makeShared();
		succeed.makeShared();
		fail.makeShared();
		// Now start the asynchronous write.
		final AvailRuntime runtime = AvailRuntime.current();
		try
		{
			socket.write(
				buffer,
				null,
				new CompletionHandler<Integer, Void>()
				{
					@Override
					public void completed (
						final @Nullable Integer bytesWritten,
						final @Nullable Void attachment)
					{
						// If termination has been requested, then take no
						// further action.
						if (!newFiber.getAndClearInterruptRequestFlag(
							TERMINATION_REQUESTED))
						{
							// If not all bytes have been written yet, then keep
							// writing.
							if (buffer.hasRemaining())
							{
								socket.write(buffer, null, this);
							}
							// Otherwise, report success.
							else
							{
								Interpreter.runOutermostFunction(
									runtime,
									newFiber,
									succeed,
									Collections.<AvailObject>emptyList());
							}
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
									(AvailObject) E_IO_ERROR.numericCode()));
						}
					}
				});
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
				TupleTypeDescriptor.zeroOrMoreOf(
					IntegerRangeTypeDescriptor.bytes()),
				ATOM.o(),
				FunctionTypeDescriptor.create(
					TupleDescriptor.empty(),
					TOP.o()),
				FunctionTypeDescriptor.create(
					TupleDescriptor.from(
						AbstractEnumerationTypeDescriptor.withInstance(
							E_IO_ERROR.numericCode())),
					TOP.o()),
				IntegerRangeTypeDescriptor.bytes()),
			FIBER.o());
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
