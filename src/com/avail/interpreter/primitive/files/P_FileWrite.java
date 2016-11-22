/**
 * P_FileWrite.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.files;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import static java.lang.Math.min;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.AvailRuntime.BufferKey;
import com.avail.AvailRuntime.FileHandle;
import org.jetbrains.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.utility.Generator;
import com.avail.utility.Mutable;
import com.avail.utility.MutableOrNull;
import com.avail.utility.evaluation.Continuation0;

/**
 * <strong>Primitive:</strong> Write the specified {@linkplain
 * TupleDescriptor tuple} to the {@linkplain AsynchronousFileChannel file
 * channel} associated with the {@linkplain AtomDescriptor handle}. Writing
 * begins at the specified one-based position of the file.
 *
 * <p>
 * Answer a new fiber which, if the write is eventually successful, will be
 * started to run the success {@linkplain FunctionDescriptor function}.  If the
 * write is unsuccessful, the fiber will be started to apply the failure {@code
 * function} to the error code.  The fiber runs at the specified priority.
 * </p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_FileWrite
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_FileWrite().init(
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
		final A_Number positionObject = args.get(0);
		final A_Tuple bytes = args.get(1);
		final A_Atom atom = args.get(2);
		final A_Function succeed = args.get(3);
		final A_Function fail = args.get(4);
		final A_Number priority = args.get(5);

		final A_BasicObject pojo =
			atom.getAtomProperty(AtomDescriptor.fileKey());
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				atom.isAtomSpecial() ? E_SPECIAL_ATOM : E_INVALID_HANDLE);
		}
		final FileHandle handle = (FileHandle) pojo.javaObjectNotNull();
		if (!handle.canWrite)
		{
			return interpreter.primitiveFailure(E_NOT_OPEN_FOR_WRITE);
		}
		final AsynchronousFileChannel fileChannel = handle.channel;
		if (!positionObject.isLong())
		{
			return interpreter.primitiveFailure(E_EXCEEDS_VM_LIMIT);
		}
		final int alignment = handle.alignment;
		final AvailRuntime runtime = AvailRuntime.current();
		final long oneBasedPositionLong = positionObject.extractLong();
		// Guaranteed positive by argument constraint.
		assert oneBasedPositionLong > 0L;
		// Write the tuple of bytes, possibly split up into manageable sections.
		// Also update the buffer cache to reflect the modified file content.
		final A_Fiber current = interpreter.fiber();
		final A_Fiber newFiber = FiberDescriptor.newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priority.extractInt(),
			new Generator<A_String>()
			{
				@Override
				public A_String value ()
				{
					return StringDescriptor.format(
						"Asynchronous file write, %s",
						handle.filename);
				}
			});
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

		// The iterator produces non-empty ByteBuffers, possibly the same one
		// multiple times, refilling it each time.
		final int totalBytes = bytes.tupleSize();
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
			bufferIterator = new Iterator<ByteBuffer>()
			{
				final ByteBuffer buffer = ByteBuffer.allocateDirect(
					min(totalBytes, MAX_WRITE_BUFFER_SIZE));

				int nextSubscript = 1;

				@Override
				public boolean hasNext ()
				{
					return nextSubscript <= totalBytes;
				}

				@Override
				public ByteBuffer next ()
				{
					assert hasNext();
					buffer.clear();
					int count = nextSubscript + buffer.limit() - 1;
					if (count >= totalBytes)
					{
						// All the rest.
						count = totalBytes;
					}
					else
					{
						// It's not all the rest, so round down to the nearest
						// alignment boundary for performance.
						final long zeroBasedSubscriptAfterBuffer =
							oneBasedPositionLong + nextSubscript - 2 + count;
						final long modulus =
							zeroBasedSubscriptAfterBuffer % alignment;
						assert modulus == (int) modulus;
						if (modulus < count)
						{
							// Shorten this buffer so it ends at an alignment
							// boundary of the file, but only if it remains
							// non-empty.  Not only will this improve throughput
							// by allowing the operating system to avoid reading
							// a buffer so it can partially overwrite it, but it
							// also makes our own buffer overwriting code more
							// efficient.
							count -= (int) modulus;
						}
					}
					bytes.transferIntoByteBuffer(
						nextSubscript,
						nextSubscript + count - 1,
						buffer);
					buffer.flip();
					assert buffer.limit() == count;
					nextSubscript += count;
					return buffer;
				}

				@Override
				public void remove ()
				{
					throw new UnsupportedOperationException();
				}
			};
		}
		final Mutable<Long> nextPosition =
			new Mutable<>(oneBasedPositionLong - 1);
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
								// Invalidate *all* pages for this file to
								// ensure subsequent I/O has a proper
								// opportunity to re-encounter problems like
								// read faults and whatnot.
								for (final BufferKey key : new ArrayList<>(
									handle.bufferKeys.keySet()))
								{
									runtime.discardBuffer(key);
								}
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
					// Just finished the entire write.  Transfer the data onto
					// any affected cached pages.
					assert nextPosition.value ==
						oneBasedPositionLong + totalBytes - 1;
					int subscriptInTuple = 1;
					long startOfBuffer = (oneBasedPositionLong - 1)
						/ alignment * alignment + 1;
					int offsetInBuffer =
						(int) (oneBasedPositionLong - startOfBuffer + 1);
					// Skip this if the file isn't also open for read access.
					if (!handle.canRead)
					{
						subscriptInTuple = totalBytes + 1;
					}
					while (subscriptInTuple <= totalBytes)
					{
						// Update one buffer.
						final int consumedThisTime = min(
							alignment - offsetInBuffer,
							totalBytes - subscriptInTuple) + 1;
						final BufferKey key = new BufferKey(
							handle, startOfBuffer);
						final MutableOrNull<A_Tuple> bufferHolder =
							runtime.getBuffer(key);
						A_Tuple tuple = bufferHolder.value;
						if (offsetInBuffer == 1
							&& consumedThisTime == alignment)
						{
							tuple = bytes.copyTupleFromToCanDestroy(
								subscriptInTuple,
								subscriptInTuple + consumedThisTime - 1,
								false);
						}
						else if (tuple != null)
						{
							// Update the cached tuple.
							assert tuple.tupleSize() == alignment;
							final List<A_Tuple> parts = new ArrayList<>(3);
							if (offsetInBuffer > 1)
							{
								parts.add(tuple.copyTupleFromToCanDestroy(
									1, offsetInBuffer - 1, false));
							}
							parts.add(bytes.copyTupleFromToCanDestroy(
								subscriptInTuple,
								subscriptInTuple + consumedThisTime - 1,
								false));
							final int endInBuffer =
								offsetInBuffer + consumedThisTime - 1;
							if (endInBuffer < alignment)
							{
								parts.add(tuple.copyTupleFromToCanDestroy(
									endInBuffer + 1, alignment, false));
							}
							assert parts.size() > 1;
							tuple = parts.remove(0);
							while (!parts.isEmpty())
							{
								tuple = tuple.concatenateWith(
									parts.remove(0), true);
							}
							assert tuple.tupleSize() == alignment;
						}
						// Otherwise we're attempting to update a subregion of
						// an uncached buffer.  Just drop it in that case and
						// let the OS cache pick up the slack.
						if (tuple != null)
						{
							tuple = tuple.makeShared();
							synchronized (bufferHolder)
							{
								bufferHolder.value = tuple;
							}
						}
						subscriptInTuple += consumedThisTime;
						startOfBuffer += alignment;
						offsetInBuffer = 1;
					}
					assert subscriptInTuple == totalBytes + 1;
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
				IntegerRangeTypeDescriptor.naturalNumbers(),
				TupleTypeDescriptor.oneOrMoreOf(
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
			FiberTypeDescriptor.forResultType(TOP.o()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_NOT_OPEN_FOR_WRITE,
				E_EXCEEDS_VM_LIMIT));
	}
}
