/**
 * P_164_FileRead.java
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
package com.avail.interpreter.primitive;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import static java.lang.Math.min;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.AvailRuntime.BufferKey;
import com.avail.AvailRuntime.FileHandle;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.utility.MutableOrNull;

/**
 * <strong>Primitive 164:</strong> Read the requested number of bytes from the
 * {@linkplain AsynchronousFileChannel file channel} associated with the
 * specified {@linkplain AtomDescriptor handle}, starting at the requested
 * one-based position. Produce them as a {@linkplain ByteArrayTupleDescriptor
 * tuple} of bytes. If fewer bytes are available, then simply produce a shorter
 * tuple; an empty tuple unambiguously indicates that the end of the file has
 * been reached. If the request amount is infinite or very large, fewer bytes
 * may be returned, at the discretion of the Avail VM.
 *
 * <p>
 * Answer a new fiber which, if the read is eventually successful, will be
 * started to apply the {@linkplain FunctionDescriptor success function} to the
 * resulting tuple of bytes.  If the read is unsuccessful, the fiber will be
 * started to apply the {@code failure function} to the error code.  The fiber
 * runs at the specified priority.
 * </p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_164_FileRead
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_164_FileRead().init(
			6, CanInline, HasSideEffect);

	/**
	 * The maximum transfer size when reading from a file.  Attempts to read
	 * more than this will simply be limited to this value.
	 */
	public final static int MAX_READ_SIZE = 4_194_304;

	/**
	 * The maximum transfer size for which a buffer is always allocated with the
	 * specified size, without first checking the file size.  Read requests with
	 * requested sizes greater than this will use the start position and the
	 * actual file size to determine how big a buffer to actually use to avoid
	 * over-allocating buffer space.
	 */
	public final static int THRESHOLD_READ_SIZE = 32768;

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 6;
		final A_Number positionObject = args.get(0);
		final A_Number sizeObject = args.get(1);
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
		if (!handle.canRead)
		{
			return interpreter.primitiveFailure(E_NOT_OPEN_FOR_READ);
		}
		final AsynchronousFileChannel fileChannel = handle.channel;
		if (!positionObject.isLong())
		{
			return interpreter.primitiveFailure(E_EXCEEDS_VM_LIMIT);
		}
		final AvailRuntime runtime = AvailRuntime.current();
		final long oneBasedPositionLong = positionObject.extractLong();
		// Guaranteed positive by argument constraint.
		assert oneBasedPositionLong > 0L;
		int size = min(
			sizeObject.isInt() ? sizeObject.extractInt() : MAX_READ_SIZE,
			MAX_READ_SIZE);
		if (size > THRESHOLD_READ_SIZE)
		{
			// Limit the buffer size based on the file's actual size.
			long actualFileSize;
			try
			{
				actualFileSize = fileChannel.size();
			}
			catch (final IOException e)
			{
				// The file's inaccessible somehow.  Don't report the exception,
				// since the Avail code didn't ask about the file size.  Limit
				// the buffer size to the threshold size to avoid
				// over-allocating due to this blindness.
				actualFileSize = Long.MAX_VALUE;
				size = THRESHOLD_READ_SIZE;
			}
			if (oneBasedPositionLong > actualFileSize)
			{
				// Don't bother dealing with empty buffers.  Besides, the file
				// might get more data before we actually read it.
				size = 1;
			}
			else
			{
				final long available =
					actualFileSize - oneBasedPositionLong + 1;
				size = min(size, (int) min(available, MAX_READ_SIZE));
			}
		}
		assert 0 < size && size <= MAX_READ_SIZE;
		final int alignment = handle.alignment;
		final long augmentedStart = (oneBasedPositionLong - 1)
			/ alignment * alignment + 1;
		final long augmentedEnd = (oneBasedPositionLong + size + alignment - 2)
			/ alignment * alignment;
		final long bufferCount = (augmentedEnd + 1 - augmentedStart)
			/ alignment;
		assert bufferCount == (int)bufferCount;
		final List<A_Tuple> buffers = new ArrayList<>((int)bufferCount);
		// Collect the initial run of either cache hits or cache misses.  Limit
		// the number of bytes actually returned to that first run, either
		// concatenating buffers for a run of hits or fetching into a big buffer
		// for a run of misses.
		long firstPresentBufferStart = Long.MIN_VALUE;
		long firstMissingBufferStart = Long.MIN_VALUE;
		for (
			long bufferStart = augmentedStart;
			bufferStart <= augmentedEnd;
			bufferStart += alignment)
		{
			final BufferKey key = new BufferKey(handle, bufferStart);
			final MutableOrNull<A_Tuple> bufferHolder = runtime.getBuffer(key);
			final @Nullable A_Tuple buffer = bufferHolder.value;
			if (buffer == null)
			{
				if (firstMissingBufferStart == Long.MIN_VALUE)
				{
					// This is the first null buffer encountered.
					firstMissingBufferStart = bufferStart;
					if (firstPresentBufferStart != Long.MIN_VALUE)
					{
						// We must have started with hits, and now we know how
						// many buffers in a row to return.
						break;
					}
				}
			}
			else
			{
				if (firstPresentBufferStart == Long.MIN_VALUE)
				{
					// This is the first hit encountered.
					firstPresentBufferStart = bufferStart;
					if (firstMissingBufferStart != Long.MIN_VALUE)
					{
						// We must have started with misses, and now we know how
						// many buffers in a row to fetch.
						break;
					}
				}
			}
			buffers.add(buffer);
		}
		final A_Fiber current = interpreter.fiber();
		final A_Fiber newFiber = FiberDescriptor.newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priority.extractInt(),
			StringDescriptor.format(
				"Asynchronous file read (prim 164), %s",
				handle.filename));
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
		if (firstPresentBufferStart == augmentedStart)
		{
			// We began with buffer hits, so don't fetch anything.
			// Concatenate the buffers we have.
			final A_Tuple buffersTuple = TupleDescriptor.fromList(buffers);
			final A_Tuple concatenated =
				buffersTuple.concatenateTuplesCanDestroy(false);
			Interpreter.runOutermostFunction(
				runtime,
				newFiber,
				succeed,
				Arrays.asList(concatenated));
			return interpreter.primitiveSuccess(newFiber);
		}
		// We began with buffer misses, and we can figure out how many...
		assert firstMissingBufferStart == augmentedStart;
		for (@Nullable final A_Tuple b : buffers)
		{
			assert b == null;
		}
		size = buffers.size() * alignment;
		// Now start the asynchronous read.
		final ByteBuffer buffer = ByteBuffer.allocateDirect(size);
		fileChannel.read(
			buffer,
			oneBasedPositionLong - 1,
			null,
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (
					final @Nullable Integer bytesRead,
					final @Nullable Void unused)
				{
					assert bytesRead != null;
					final A_Tuple bytesTuple;
					buffer.flip();
					if (bytesRead == -1)
					{
						// We started reading after the last byte of the file.
						// Avail expects an empty buffer in this case.
						assert buffer.remaining() == 0;
						bytesTuple = TupleDescriptor.empty();
					}
					else
					{
						assert buffer.remaining() == bytesRead;
						bytesTuple = ByteBufferTupleDescriptor.forByteBuffer(
								buffer)
							.makeShared();
						assert bytesTuple.tupleSize() == bytesRead;
						// Seed the file cache, except for the final partial
						// buffer.
						final long lastPosition =
							oneBasedPositionLong + bytesRead - 1;
						final long lastFullBufferStart =
							lastPosition
								/ alignment * alignment - alignment + 1;
						int offsetInBuffer = 1;
						for (
							long bufferStart = oneBasedPositionLong;
							bufferStart <= lastFullBufferStart;
							bufferStart += alignment)
						{
							final A_Tuple subtuple =
								bytesTuple.copyTupleFromToCanDestroy(
									offsetInBuffer,
									offsetInBuffer + alignment - 1,
									false
								).makeShared();
							assert subtuple.tupleSize() == alignment;
							final BufferKey key =
								new BufferKey(handle, bufferStart);
							final MutableOrNull<A_Tuple> bufferHolder =
								runtime.getBuffer(key);
							// The getBuffer() used a lock, so all writes have
							// now happened-before.
							bufferHolder.value = subtuple;
							// Do one more lookup of the key to ensure that
							// everything happens-after the above write.
							runtime.getBuffer(key);
							offsetInBuffer += alignment;
						}
					}
					Interpreter.runOutermostFunction(
						runtime,
						newFiber,
						succeed,
						Arrays.asList(bytesTuple));
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
		return interpreter.primitiveSuccess(newFiber);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				IntegerRangeTypeDescriptor.naturalNumbers(),
				IntegerRangeTypeDescriptor.inclusive(
					IntegerDescriptor.one(),
					InfinityDescriptor.positiveInfinity()),
				ATOM.o(),
				FunctionTypeDescriptor.create(
					TupleDescriptor.from(
						TupleTypeDescriptor.zeroOrMoreOf(
							IntegerRangeTypeDescriptor.bytes())),
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
				E_NOT_OPEN_FOR_READ.numericCode(),
				E_EXCEEDS_VM_LIMIT.numericCode()
			).asSet());
	}
}