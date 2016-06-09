/**
 * P_FileClose.java
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
import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.util.List;
import com.avail.AvailRuntime.FileHandle;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Close the {@linkplain AsynchronousFileChannel
 * file} associated with the specified {@linkplain AtomDescriptor handle}.
 * Forget the association between the handle and the file.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_FileClose
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_FileClose().init(
			1, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Atom atom = args.get(0);
		final A_BasicObject pojo =
			atom.getAtomProperty(AtomDescriptor.fileKey());
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(E_INVALID_HANDLE);
		}
		final FileHandle handle = (FileHandle) pojo.javaObjectNotNull();
		try
		{
			handle.channel.close();
		}
		catch (final IOException e)
		{
			// There isn't much to do about a failed close, especially since
			// we've already forgotten about the handle. There's no reason
			// to fail the primitive.
		}
		atom.setAtomProperty(
			AtomDescriptor.fileKey(), NilDescriptor.nil());
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ATOM.o()),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return InstanceTypeDescriptor.on(E_INVALID_HANDLE.numericCode());
	}
}
