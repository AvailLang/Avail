/**
 * P_165_FileWrite.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.utility.Mutable;
import com.avail.utility.MutableOrNull;
import com.avail.utility.evaluation.Continuation0;

/**
 * <strong>Primitive 165:</strong> Write the specified {@linkplain
 * TupleDescriptor tuple} to the {@linkplain AsynchronousFileChannel file
 * channel} associated with the {@linkplain AtomDescriptor handle}. Writing
 * begins at the specified one-based position of the file.
 *
 * <p>
 * Answer a new fiber which, if the write is eventually successful, will be
 * started to run the {@linkplain FunctionDescriptor success function}.  If the
 * write is unsuccessful, the fiber will be started to apply the {@code failure
 * function} to the error code.  The fiber runs at the specified priority.
 * </p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_165_FileWrite
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_165_FileWrite().init(
		6, CanInline, HasSideEffect);

	/**
	 * The maximum transfer size when writing to a file.  Attempts to write
	 * more bytes than this may be broken down internally into transfers that
	 * are this small, possibly recycling the same buffer.
	 */
	public final static int MAX_WRITE_BUFFER_SIZE = 4_194_304;

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 6;
		final A_Atom handle = args.get(0);
		final A_Tuple bytes = args.get(1);
		final A_Number positionObject = args.get(2);
		final A_Function succeed = args.get(3);
		final A_Function fail = args.get(4);
		final A_Number priority = args.get(5);
		final A_BasicObject pojo =
			handle.getAtomProperty(AtomDescriptor.fileKey());
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				handle.isAtomSpecial() ? E_SPECIAL_ATOM : E_INVALID_HANDLE);
		}
		final A_BasicObject mode =
			handle.getAtomProperty(AtomDescriptor.fileModeWriteKey());
		if (mode.equalsNil())
		{
			return interpreter.primitiveFailure(E_NOT_OPEN_FOR_WRITE);
		}
		final AsynchronousFileChannel fileChannel =
			(AsynchronousFileChannel) pojo.javaObject();
		if (!positionObject.isLong())
		{
			return interpreter.primitiveFailure(E_EXCEEDS_VM_LIMIT);
		}
		final long oneBasedPositionLong = positionObject.extractLong();
		// Should be guaranteed by argument constraint...
		assert oneBasedPositionLong > 0L;

		final A_Fiber current = interpreter.fiber();
		final A_Fiber newFiber = FiberDescriptor.newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priority.extractInt(),
			StringDescriptor.format(
				"Asynchronous file write (prim 165), %s",
				handle.atomName()));
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

		// The iterator produces non-empty ByteBuffers, possibly the same one
		// multiple times after refilling it.
		final Iterator<ByteBuffer> bufferIterator;
		if (bytes.isByteBufferTuple())
		{
			final ByteBuffer buffer = bytes.byteBuffer().slice();
			bufferIterator = Collections.singletonList(buffer).iterator();
		}
		else if (bytes.isByteArrayTuple())
		{
			final ByteBuffer buffer = ByteBuffer.wrap(bytes.byteArray());
			bufferIterator = Collections.singletonList(buffer).iterator();
		}
		else
		{
			final ByteBuffer buffer = ByteBuffer.allocateDirect(
				Math.min(bytes.tupleSize(), MAX_WRITE_BUFFER_SIZE));
			bufferIterator = new Iterator<ByteBuffer>()
			{
				int nextSubscript = 1;

				@Override
				public boolean hasNext ()
				{
					return nextSubscript <= bytes.tupleSize();
				}

				@Override
				public ByteBuffer next ()
				{
					assert hasNext();
					buffer.limit(0);
					assert buffer.position() == 0;
					bytes.transferIntoByteBuffer(
						nextSubscript,
						Math.min(
							nextSubscript + buffer.capacity() - 1,
							bytes.tupleSize()),
						buffer);
					buffer.flip();
					nextSubscript += buffer.limit();
					return buffer;
				}

				@Override
				public void remove ()
				{
					throw new UnsupportedOperationException();
				}
			};
		}
		final AvailRuntime runtime = AvailRuntime.current();
		final Mutable<Long> nextPosition =
			new Mutable<>(oneBasedPositionLong - 1);
		assert bufferIterator.hasNext();
		final Mutable<ByteBuffer> currentBuffer =
			new Mutable<>(bufferIterator.next());
		final MutableOrNull<Continuation0> continueWriting =
			new MutableOrNull<>();
		continueWriting.value = new Continuation0()
		{
			@Override
			public void value ()
			{
				if (!currentBuffer.value.hasRemaining())
				{
					if (bufferIterator.hasNext())
					{
						currentBuffer.value = bufferIterator.next();
						assert currentBuffer.value.hasRemaining();
					}
				}
				if (currentBuffer.value.hasRemaining())
				{
					fileChannel.write(
						currentBuffer.value,
						nextPosition.value,
						null,
						new CompletionHandler<Integer, Void>()
						{
							@Override
							public void completed (
								final @Nullable Integer bytesWritten,
								final @Nullable Void attachment)
							{
								assert bytesWritten != null;
								nextPosition.value += (long) bytesWritten;
								continueWriting.value().value();
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
									Collections.singletonList(
										E_IO_ERROR.numericCode()));
							}
						});
				}
				else
				{
					// Just finished the entire write.
					assert nextPosition.value ==
						oneBasedPositionLong + bytes.tupleSize() - 1;
					Interpreter.runOutermostFunction(
						runtime,
						newFiber,
						succeed,
						Collections.<A_BasicObject>emptyList());
				}
			}
		};
		continueWriting.value().value();
		return interpreter.primitiveSuccess(newFiber);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ATOM.o(),
				TupleTypeDescriptor.oneOrMoreOf(
					IntegerRangeTypeDescriptor.bytes()),
				IntegerRangeTypeDescriptor.naturalNumbers(),
				FunctionTypeDescriptor.create(
					TupleDescriptor.empty(),
					TOP.o()),
				FunctionTypeDescriptor.create(
					TupleDescriptor.from(
						AbstractEnumerationTypeDescriptor.withInstance(
							E_IO_ERROR.numericCode())),
					TOP.o()),
				IntegerRangeTypeDescriptor.bytes()),
			FiberTypeDescriptor.forResultType(TOP.o()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				E_INVALID_HANDLE.numericCode(),
				E_SPECIAL_ATOM.numericCode(),
				E_NOT_OPEN_FOR_WRITE.numericCode(),
				E_EXCEEDS_VM_LIMIT.numericCode()
			).asSet());
	}
}