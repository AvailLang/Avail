/*
 * P_FileOpen.java
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
import com.avail.AvailRuntime.FileHandle;
import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.SetDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystem;
import java.nio.file.InvalidPathException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.FILE_KEY;
import static com.avail.descriptor.AtomDescriptor.createAtom;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.exceptions.AvailErrorCode.E_EXCEEDS_VM_LIMIT;
import static com.avail.exceptions.AvailErrorCode.E_ILLEGAL_OPTION;
import static com.avail.exceptions.AvailErrorCode.E_INVALID_PATH;
import static com.avail.exceptions.AvailErrorCode.E_IO_ERROR;
import static com.avail.exceptions.AvailErrorCode.E_OPERATION_NOT_SUPPORTED;
import static com.avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * <strong>Primitive:</strong> Open an {@linkplain AsynchronousFileChannel
 * file}. Answer a {@linkplain AtomDescriptor handle} that uniquely identifies
 * the file.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_FileOpen
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_FileOpen().init(
			4, CanInline, HasSideEffect);

	/**
	 * Stash the enum values for StandardOpenOption to avoid array copying.
	 */
	static final StandardOpenOption[] allStandardOpenOptions =
		StandardOpenOption.values();

	/**
	 * Construct the {@linkplain EnumSet set} of {@linkplain OpenOption open
	 * options} that correspond to the supplied {@linkplain SetDescriptor set}
	 * of integral option indicators.
	 *
	 * @param optionInts
	 *        Some integral option indicators.
	 * @return The implied open options.
	 */
	private static Set<? extends OpenOption> openOptionsFor (
		final A_Set optionInts)
	{
		final EnumSet<StandardOpenOption> options =
			EnumSet.noneOf(StandardOpenOption.class);
		for (final A_Number optionInt : optionInts)
		{
			options.add(
				allStandardOpenOptions[optionInt.extractInt()]);
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
	 * @return An array whose lone element is a set containing an attribute that
	 *         specifies the implied POSIX file permissions, or an empty array
	 *         if the {@linkplain FileSystem file system} does not support POSIX
	 *         file permissions.
	 */
	private static FileAttribute<?>[] permissionsFor (final A_Set optionInts)
	{
		if (AvailRuntime.fileSystem().supportedFileAttributeViews().contains(
			"posix"))
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
			return new FileAttribute<?>[]
				{PosixFilePermissions.asFileAttribute(permissions)};
		}
		return new FileAttribute<?>[0];
	}

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(4);
		final A_String filename = interpreter.argument(0);
		final A_Number alignment = interpreter.argument(1);
		final A_Set options = interpreter.argument(2);
		final A_Set permissions = interpreter.argument(3);

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
		final AvailRuntime runtime = currentRuntime();
		final Set<? extends OpenOption> fileOptions = openOptionsFor(options);
		final FileAttribute<?>[] fileAttributes = permissionsFor(permissions);
		if (!fileOptions.contains(READ) && !fileOptions.contains(WRITE))
		{
			return interpreter.primitiveFailure(E_ILLEGAL_OPTION);
		}
		final Path path;
		try
		{
			path = AvailRuntime.fileSystem().getPath(
				filename.asNativeString());
		}
		catch (final InvalidPathException e)
		{
			return interpreter.primitiveFailure(E_INVALID_PATH);
		}
		final A_Atom atom = createAtom(filename, nil);
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
		final AvailObject pojo = identityPojo(fileHandle);
		atom.setAtomProperty(FILE_KEY.atom, pojo);
		return interpreter.primitiveSuccess(atom);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				stringType(),
				wholeNumbers(),
				setTypeForSizesContentType(
					wholeNumbers(),
					inclusive(0, 9)),
				setTypeForSizesContentType(
					wholeNumbers(),
					inclusive(1, 9))),
			ATOM.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_EXCEEDS_VM_LIMIT,
				E_INVALID_PATH,
				E_ILLEGAL_OPTION,
				E_OPERATION_NOT_SUPPORTED,
				E_PERMISSION_DENIED,
				E_IO_ERROR));
	}
}
