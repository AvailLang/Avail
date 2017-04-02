/**
 * P_FileTruncate.java
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

package com.avail.interpreter.primitive.files;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.io.*;
import java.nio.channels.AsynchronousFileChannel;
import java.util.Collections;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.AvailTask;
import com.avail.AvailRuntime.FileHandle;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.utility.Generator;

/**
 * <strong>Primitive:</strong> If the specified size is less than the size
 * of the indicated {@linkplain FileHandle#canWrite writable} {@linkplain
 * AsynchronousFileChannel file channel} associated with the {@linkplain
 * AtomDescriptor handle}, then reduce its size as indicated, discarding any
 * data beyond the new file size.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_FileTruncate
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_FileTruncate().init(
			5, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 5;
		final A_Atom atom = args.get(0);
		final A_Number sizeObject = args.get(1);
		final A_Function succeed = args.get(2);
		final A_Function fail = args.get(3);
		final A_Number priority = args.get(4);

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
		// Truncating to something beyond the file size has no effect, so use
		// Long.MAX_VALUE if the newSize is bigger than that.
		final long size = sizeObject.isLong()
			? sizeObject.extractLong()
			: Long.MAX_VALUE;
		final AvailRuntime runtime = AvailRuntime.current();
		// Guaranteed non-negative by argument constraint.
		assert size >= 0L;

		final int priorityInt = priority.extractInt();
		final A_Fiber current = interpreter.fiber();
		final A_Fiber newFiber = FiberDescriptor.newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priorityInt,
			new Generator<A_String>()
			{
				@Override
				public A_String value ()
				{
					return StringDescriptor.format(
						"Asynchronous truncate, %s",
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

		runtime.executeFileTask(new AvailTask(priorityInt)
		{
			@Override
			public void value ()
			{
				try
				{
					fileChannel.truncate(size);
				}
				catch (final IOException e)
				{
					Interpreter.runOutermostFunction(
						runtime,
						newFiber,
						fail,
						Collections.singletonList(
							E_IO_ERROR.numericCode()));
					return;
				}
				Interpreter.runOutermostFunction(
					runtime,
					newFiber,
					succeed,
					Collections.<A_BasicObject>emptyList());
			}
		});

		return interpreter.primitiveSuccess(newFiber);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ATOM.o(),
				IntegerRangeTypeDescriptor.wholeNumbers(),
				FunctionTypeDescriptor.create(
					TupleDescriptor.from(),
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
				E_NOT_OPEN_FOR_WRITE,
				E_SPECIAL_ATOM));
	}
}
