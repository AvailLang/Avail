/*
 * P_FileCanRead.java
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
package com.avail.interpreter.primitive.files;

import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.io.IOSystem;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AtomDescriptor.objectFromBoolean;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.exceptions.AvailErrorCode.E_INVALID_PATH;
import static com.avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive:</strong> Is the specified {@linkplain Path path}
 * readable?
 */
public final class P_FileCanRead
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_FileCanRead().init(
			1, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_String filename = interpreter.argument(0);
		final Path path;
		try
		{
			path = IOSystem.fileSystem().getPath(
				filename.asNativeString());
		}
		catch (final InvalidPathException e)
		{
			return interpreter.primitiveFailure(E_INVALID_PATH);
		}
		final boolean readable;
		try
		{
			readable = Files.isReadable(path);
		}
		catch (final SecurityException e)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED);
		}
		return interpreter.primitiveSuccess(objectFromBoolean(readable));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(stringType()),
			booleanType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_INVALID_PATH,
				E_PERMISSION_DENIED));
	}
}
