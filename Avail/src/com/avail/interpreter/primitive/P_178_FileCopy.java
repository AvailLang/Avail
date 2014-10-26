/**
 * P_178_FileCopy.java
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

import static java.nio.file.FileVisitResult.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import com.avail.AvailRuntime;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.utility.Mutable;

/**
 * <strong>Primitive 178</strong>: Recursively copy the source {@linkplain Path
 * path} to the destination path.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_178_FileCopy
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_178_FileCopy().init(
			5, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 5;
		final A_String source = args.get(0);
		final A_String destination = args.get(1);
		final A_Atom followSymlinks = args.get(2);
		final A_Atom replace = args.get(3);
		final A_Atom copyAttributes = args.get(4);
		final AvailRuntime runtime = AvailRuntime.current();
		final Path sourcePath;
		final Path destinationPath;
		try
		{
			sourcePath = runtime.fileSystem().getPath(
				source.asNativeString());
			destinationPath = runtime.fileSystem().getPath(
				destination.asNativeString());
		}
		catch (final InvalidPathException e)
		{
			return interpreter.primitiveFailure(E_INVALID_PATH);
		}
		final List<CopyOption> optionList = new ArrayList<>(2);
		if (replace.extractBoolean())
		{
			optionList.add(StandardCopyOption.REPLACE_EXISTING);
		}
		if (copyAttributes.extractBoolean())
		{
			optionList.add(StandardCopyOption.COPY_ATTRIBUTES);
		}
		final CopyOption[] options = optionList.toArray(
			new CopyOption[optionList.size()]);
		try
		{
			final Set<FileVisitOption> visitOptions =
				followSymlinks.extractBoolean()
				? EnumSet.of(FileVisitOption.FOLLOW_LINKS)
				: EnumSet.noneOf(FileVisitOption.class);
			final Mutable<Boolean> partialSuccess =
				new Mutable<Boolean>(false);
			Files.walkFileTree(
				sourcePath,
				visitOptions,
				Integer.MAX_VALUE,
				new FileVisitor<Path>()
				{
					@Override
					public FileVisitResult preVisitDirectory (
							final @Nullable Path dir,
							final @Nullable BasicFileAttributes unused)
						throws IOException
					{
						assert dir != null;
						final Path dstDir = destinationPath.resolve(
							sourcePath.relativize(dir));
						try
						{
							Files.copy(dir, dstDir, options);
						}
						catch (final FileAlreadyExistsException e)
						{
							if (!Files.isDirectory(dstDir))
							{
								throw e;
							}
						}
						return CONTINUE;
					}

					@Override
					public FileVisitResult visitFile (
							final @Nullable Path file,
							final @Nullable BasicFileAttributes unused)
						throws IOException
					{
						assert file != null;
						Files.copy(
							file,
							destinationPath.resolve(
								sourcePath.relativize(file)),
							options);
						return CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed (
							final @Nullable Path file,
							final @Nullable IOException unused)
						throws IOException
					{
						partialSuccess.value = true;
						return CONTINUE;
					}

					@Override
					public FileVisitResult postVisitDirectory (
							final @Nullable Path dir,
							final @Nullable IOException e)
						throws IOException
					{
						if (e != null)
						{
							partialSuccess.value = true;
						}
						return CONTINUE;
					}
				});
			if (partialSuccess.value)
			{
				return interpreter.primitiveFailure(E_PARTIAL_SUCCESS);
			}
		}
		catch (final SecurityException|AccessDeniedException e)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED);
		}
		catch (final IOException e)
		{
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.stringType(),
				TupleTypeDescriptor.stringType(),
				EnumerationTypeDescriptor.booleanObject(),
				EnumerationTypeDescriptor.booleanObject(),
				EnumerationTypeDescriptor.booleanObject()),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				E_INVALID_PATH.numericCode(),
				E_PERMISSION_DENIED.numericCode(),
				E_IO_ERROR.numericCode(),
				E_PARTIAL_SUCCESS.numericCode()
			).asSet());
	}
}
