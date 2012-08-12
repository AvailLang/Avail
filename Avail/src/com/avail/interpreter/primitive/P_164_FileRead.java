/**
 * P_164_FileRead.java
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

import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.io.*;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 164:</strong> Read the requested number of bytes from
 * the {@linkplain RandomAccessFile file} associated with the specified
 * {@linkplain AtomDescriptor handle} and answer them as a {@linkplain
 * ByteTupleDescriptor tuple} of bytes. If fewer bytes are available, then
 * simply return a shorter tuple. If the request amount is infinite, then
 * answer a tuple containing all remaining bytes, or a very large buffer
 * size, whichever is less. Reading begins at the current file position.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class P_164_FileRead
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_164_FileRead().init(
		2, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 2;
		final AvailObject handle = args.get(0);
		final AvailObject size = args.get(1);

		if (!handle.isAtom())
		{
			return interpreter.primitiveFailure(E_INVALID_HANDLE);
		}

		final RandomAccessFile file =
			interpreter.runtime().getReadableFile(handle);
		if (file == null)
		{
			return interpreter.primitiveFailure(E_INVALID_HANDLE);
		}

		final byte[] buffer;
		final int bytesRead;
		try
		{
			if (size.isFinite())
			{
				buffer = new byte[size.extractInt()];
			}
			else
			{
				final int bufferSize = (int) Math.min(
					Integer.MAX_VALUE,
					file.length() - file.getFilePointer());
				buffer = new byte[bufferSize];
			}
			bytesRead = file.read(buffer);
		}
		catch (final IOException e)
		{
			return interpreter.primitiveFailure(E_IO_ERROR);
		}

		final AvailObject tuple;
		if (bytesRead > 0)
		{
			tuple = ByteTupleDescriptor.mutableObjectOfSize(bytesRead);
			for (int i = 1; i <= bytesRead; i++)
			{
				tuple.rawByteAtPut(i, (short) (buffer[i - 1] & 0xff));
			}
		}
		else
		{
			tuple = TupleDescriptor.empty();
		}

		return interpreter.primitiveSuccess(tuple);
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ATOM.o(),
				IntegerRangeTypeDescriptor.wholeNumbers()),
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				IntegerRangeTypeDescriptor.bytes()));
	}
}