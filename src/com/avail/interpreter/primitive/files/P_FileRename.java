/*
 * P_FileRename.java
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

import com.avail.AvailRuntime;
import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FiberDescriptor.newFiber;
import static com.avail.descriptor.FiberTypeDescriptor.fiberType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.bytes;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive:</strong> Rename the source {@linkplain Path path} to
 * the destination path. Try not to overwrite an existing destination. This
 * operation is only likely to work for two paths provided by the same
 * {@linkplain FileStore file store}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_FileRename
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_FileRename().init(
			6, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(6);
		final A_String source = interpreter.argument(0);
		final A_String destination = interpreter.argument(1);
		final A_Atom replaceExisting = interpreter.argument(2);
		final A_Function succeed = interpreter.argument(3);
		final A_Function fail = interpreter.argument(4);
		final A_Number priority = interpreter.argument(5);

		final AvailRuntime runtime = currentRuntime();
		final Path sourcePath;
		final Path destinationPath;
		try
		{
			sourcePath = AvailRuntime.fileSystem().getPath(
				source.asNativeString());
			destinationPath = AvailRuntime.fileSystem().getPath(
				destination.asNativeString());
		}
		catch (final InvalidPathException e)
		{
			return interpreter.primitiveFailure(E_INVALID_PATH);
		}

		final int priorityInt = priority.extractInt();
		final A_Fiber current = interpreter.fiber();
		final A_Fiber newFiber = newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priorityInt,
			() ->
				formatString("Asynchronous file rename, %s → %s", sourcePath,
					destinationPath));
		newFiber.availLoader(current.availLoader());
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared());
		newFiber.textInterface(current.textInterface());
		newFiber.makeShared();
		succeed.makeShared();
		fail.makeShared();

		final boolean replace = replaceExisting.extractBoolean();
		runtime.executeFileTask(() ->
		{
			final List<CopyOption> options = new ArrayList<>();
			if (replace)
			{
				options.add(StandardCopyOption.REPLACE_EXISTING);
			}
			try
			{
				Files.move(
					sourcePath,
					destinationPath,
					options.toArray(new CopyOption[options.size()]));
			}
			catch (final SecurityException|AccessDeniedException e)
			{
				Interpreter.runOutermostFunction(
					runtime,
					newFiber,
					fail,
					Collections.singletonList(
						E_PERMISSION_DENIED.numericCode()));
				return;
			}
			catch (final NoSuchFileException e)
			{
				Interpreter.runOutermostFunction(
					runtime,
					newFiber,
					fail,
					Collections.singletonList(E_NO_FILE.numericCode()));
				return;
			}
			catch (final FileAlreadyExistsException e)
			{
				Interpreter.runOutermostFunction(
					runtime,
					newFiber,
					fail,
					Collections.singletonList(
						E_FILE_EXISTS.numericCode()));
				return;
			}
			catch (final IOException e)
			{
				Interpreter.runOutermostFunction(
					runtime,
					newFiber,
					fail,
					Collections.singletonList(E_IO_ERROR.numericCode()));
				return;
			}
			Interpreter.runOutermostFunction(
				runtime,
				newFiber,
				succeed,
				Collections.emptyList());
		});
		return interpreter.primitiveSuccess(newFiber);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tupleFromArray(
				stringType(),
				stringType(),
				booleanType(),
				functionType(
					emptyTuple(),
					TOP.o()),
				functionType(
					tuple(
						enumerationWith(
							set(
								E_PERMISSION_DENIED,
								E_FILE_EXISTS,
								E_NO_FILE,
								E_IO_ERROR))),
					TOP.o()),
				bytes()),
			fiberType(TOP.o()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_INVALID_PATH));
	}
}
