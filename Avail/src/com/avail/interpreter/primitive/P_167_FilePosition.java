/**
 * P_167_FilePosition.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 167:</strong> Answer the current position of the file
 * pointer within the readable {@linkplain RandomAccessFile file} associated
 * with the specified {@linkplain AtomDescriptor handle}. Supports 64-bit
 * file sizes and positions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_167_FilePosition
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_167_FilePosition().init(
		1, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Atom handle = args.get(0);
		final A_BasicObject pojo =
			handle.getAtomProperty(AtomDescriptor.fileKey());
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(E_INVALID_HANDLE);
		}
		final RandomAccessFile file = (RandomAccessFile) pojo.javaObject();
		final long filePosition;
		try
		{
			filePosition = file.getFilePointer();
		}
		catch (final IOException e)
		{
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
		final A_Number oneBased = IntegerDescriptor.fromLong(filePosition)
			.plusCanDestroy(IntegerDescriptor.one(), true);
		return interpreter.primitiveSuccess(oneBased);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ATOM.o()),
			IntegerRangeTypeDescriptor.naturalNumbers());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				E_INVALID_HANDLE.numericCode(),
				E_IO_ERROR.numericCode()
			).asSet());
	}
}