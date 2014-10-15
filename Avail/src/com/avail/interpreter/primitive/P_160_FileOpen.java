/**
 * P_160_FileOpen.java
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

import static java.nio.file.StandardOpenOption.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.io.*;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.InvalidPathException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import com.avail.AvailRuntime;
import com.avail.AvailRuntime.FileHandle;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 160:</strong> Open an {@linkplain AsynchronousFileChannel
 * file}. Answer a {@linkplain AtomDescriptor handle} that uniquely identifies
 * the file.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_160_FileOpen
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_160_FileOpen().init(
		4, CanInline, HasSideEffect);

	/**
	 * Construct the {@linkplain EnumSet set} of {@linkplain OpenOption open
	 * options} that correspond to the supplied {@linkplain SetDescriptor set}
	 * of integral option indicators.
	 *
	 * @param optionInts
	 *        Some integral option indicators.
	 * @return The implied open options.
	 */
	private Set<? extends OpenOption> openOptionsFor (final A_Set optionInts)
	{
		final EnumSet<StandardOpenOption> options =
			EnumSet.noneOf(StandardOpenOption.class);
		for (final A_Number optionInt : optionInts)
		{
			options.add(
				StandardOpenOption.values()[optionInt.extractInt()]);
		}
		return options;
	}

	/**
	 * Construct the {@linkplain EnumSet set} of {@linkplain FileAttribute
	 * file attributes} that specify the {@linkplain PosixFilePermission POSIX
	 * file permissions} that correspond to the supplied {@linkplain
	 * SetDescriptor set} of integral option indicators.
	 *
	 * @param optionInts
	 *        Some integral option indicators.
	 * @return A set containing an attribute that specifies the implied POSIX
	 *         file permissions.
	 */
	private FileAttribute<?> permissionsFor (final A_Set optionInts)
	{
		final PosixFilePermission[] allPermissions =
			AvailRuntime.posixPermissions();
		final Set<PosixFilePermission> permissions = EnumSet.noneOf(
			PosixFilePermission.class);
		for (final A_Number optionInt : optionInts)
		{
			final PosixFilePermission permission =
				allPermissions[optionInt.extractInt() - 1];
			permissions.add(permission);
		}
		return PosixFilePermissions.asFileAttribute(permissions);
	}

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 4;
		final A_String filename = args.get(0);
		final A_Number alignment = args.get(1);
		final A_Set options = args.get(2);
		final A_Set permissions = args.get(3);

		if (!alignment.isInt())
		{
			return interpreter.primitiveFailure(E_EXCEEDS_VM_LIMIT);
		}
		int alignmentInt = alignment.extractInt();
		if (alignmentInt == 0)
		{
			// Plug in the default alignment for the device on which the
			// filename is located.  Ahem, erm, Java actually doesn't make this
			// available in any way.  Fudge it for now as 4096.
			alignmentInt = 4096;
		}
		assert alignmentInt > 0;
		final AvailRuntime runtime = AvailRuntime.current();
		final Set<? extends OpenOption> fileOptions = openOptionsFor(options);
		final FileAttribute<?> fileAttributes = permissionsFor(permissions);
		if (!fileOptions.contains(READ) && !fileOptions.contains(WRITE))
		{
			return interpreter.primitiveFailure(E_ILLEGAL_OPTION);
		}
		final Path path;
		try
		{
			path = runtime.fileSystem().getPath(
				filename.asNativeString());
		}
		catch (final InvalidPathException e)
		{
			return interpreter.primitiveFailure(E_INVALID_PATH);
		}
		final A_Atom atom =
			AtomDescriptor.create(filename, NilDescriptor.nil());
		final AsynchronousFileChannel channel;
		try
		{
			channel = runtime.openFile(path, fileOptions, fileAttributes);
		}
		catch (final IllegalArgumentException e)
		{
			return interpreter.primitiveFailure(E_ILLEGAL_OPTION);
		}
		catch (final UnsupportedOperationException e)
		{
			return interpreter.primitiveFailure(E_OPERATION_NOT_SUPPORTED);
		}
		catch (final SecurityException|AccessDeniedException e)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED);
		}
		catch (final IOException e)
		{
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
		final FileHandle fileHandle = new FileHandle(
			filename,
			alignmentInt,
			fileOptions.contains(READ),
			fileOptions.contains(WRITE),
			channel);
		final AvailObject pojo = RawPojoDescriptor.identityWrap(fileHandle);
		atom.setAtomProperty(AtomDescriptor.fileKey(), pojo);
		return interpreter.primitiveSuccess(atom);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.stringType(),
				IntegerRangeTypeDescriptor.wholeNumbers(),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.inclusive(
						IntegerDescriptor.fromInt(0),
						IntegerDescriptor.fromInt(10)),
					IntegerRangeTypeDescriptor.inclusive(
						IntegerDescriptor.fromInt(0),
						IntegerDescriptor.fromInt(9))),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.inclusive(
						IntegerDescriptor.fromInt(0),
						IntegerDescriptor.fromInt(9)),
					IntegerRangeTypeDescriptor.inclusive(
						IntegerDescriptor.fromInt(1),
						IntegerDescriptor.fromInt(9)))),
			ATOM.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				E_EXCEEDS_VM_LIMIT.numericCode(),
				E_INVALID_PATH.numericCode(),
				E_ILLEGAL_OPTION.numericCode(),
				E_OPERATION_NOT_SUPPORTED.numericCode(),
				E_PERMISSION_DENIED.numericCode(),
				E_IO_ERROR.numericCode()
			).asSet());
	}
}