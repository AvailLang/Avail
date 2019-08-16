/*
 * P_CreateDirectory.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.SetDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.io.IOSystem;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FiberDescriptor.newFiber;
import static com.avail.descriptor.FiberTypeDescriptor.fiberType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.bytes;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;
import static java.util.Collections.singletonList;

/**
 * <strong>Primitive:</strong> Create a directory with the indicated name
 * and permissions. Answer a new {@linkplain A_Fiber fiber} which, if creation
 * is successful, will be started to run the success {@linkplain A_Function
 * function}. If the creation fails, then the fiber will be started to apply the
 * failure function to the error code. The fiber runs at the specified priority.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_CreateDirectory
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance = new P_CreateDirectory().init(
		5, CanInline, HasSideEffect);

	/**
	 * Convert the specified {@linkplain SetDescriptor set} of {@linkplain
	 * IntegerDescriptor ordinals} into the corresponding {@linkplain Set set}
	 * of {@linkplain PosixFilePermission POSIX file permissions}.
	 *
	 * @param ordinals
	 *        Some ordinals.
	 * @return The equivalent POSIX file permissions.
	 */
	private static Set<PosixFilePermission> permissionsFor (
		final A_Set ordinals)
	{
		final PosixFilePermission[] allPermissions =
			IOSystem.posixPermissions();
		final Set<PosixFilePermission> permissions =
			EnumSet.noneOf(PosixFilePermission.class);
		for (final A_Number ordinal : ordinals)
		{
			permissions.add(allPermissions[ordinal.extractInt() - 1]);
		}
		return permissions;
	}

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(5);
		final A_String directoryName = interpreter.argument(0);
		final A_Set ordinals = interpreter.argument(1);
		final A_Function succeed = interpreter.argument(2);
		final A_Function fail = interpreter.argument(3);
		final A_Number priority = interpreter.argument(4);

		final AvailRuntime runtime = interpreter.runtime();
		final FileSystem fileSystem = IOSystem.fileSystem();
		final Path path;
		try
		{
			path = fileSystem.getPath(directoryName.asNativeString());
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
			() -> formatString("Asynchronous create directory, %s", path));
		newFiber.availLoader(current.availLoader());
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared());
		newFiber.textInterface(current.textInterface());
		newFiber.makeShared();
		succeed.makeShared();
		fail.makeShared();

		final Set<PosixFilePermission> permissions = permissionsFor(ordinals);
		final FileAttribute<Set<PosixFilePermission>> attr =
			PosixFilePermissions.asFileAttribute(permissions);
		runtime.ioSystem().executeFileTask(() ->
		{
			try
			{
				try
				{
					Files.createDirectory(path, attr);
				}
				catch (final UnsupportedOperationException e)
				{
					// Retry without setting the permissions.
					Files.createDirectory(path);
				}
			}
			catch (final FileAlreadyExistsException e)
			{
				Interpreter.runOutermostFunction(
					runtime,
					newFiber,
					fail,
					singletonList(
						E_FILE_EXISTS.numericCode()));
				return;
			}
			catch (final SecurityException|AccessDeniedException e)
			{
				Interpreter.runOutermostFunction(
					runtime,
					newFiber,
					fail,
					singletonList(
						E_PERMISSION_DENIED.numericCode()));
				return;
			}
			catch (final IOException e)
			{
				Interpreter.runOutermostFunction(
					runtime,
					newFiber,
					fail,
					singletonList(E_IO_ERROR.numericCode()));
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
		return functionType(tuple(stringType(),
			setTypeForSizesContentType(
				inclusive(0, 9),
				inclusive(1, 9)),
			functionType(
				emptyTuple(),
				TOP.o()), functionType(
				tuple(
					enumerationWith(
						set(E_FILE_EXISTS, E_PERMISSION_DENIED,
							E_IO_ERROR))),
				TOP.o()), bytes()), fiberType(TOP.o()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return
			enumerationWith(set(E_INVALID_PATH));
	}
}
