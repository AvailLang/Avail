/**
 * P_175_FileUnlink.java
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

import static java.nio.file.FileVisitResult.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import com.avail.AvailRuntime;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.utility.Mutable;

/**
 * <strong>Primitive 175:</strong> Unlink the specified {@linkplain Path path}
 * from the file system.
 */
public final class P_175_FileUnlink
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_175_FileUnlink().init(
			4, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 4;
		final A_Atom recursive = args.get(0);
		final A_String filename = args.get(1);
		final A_Atom requireExistence = args.get(2);
		final A_Atom followSymlinks = args.get(3);
		final AvailRuntime runtime = AvailRuntime.current();
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
		// Unless the unlink should be recursive, then try unlinking the target
		// directly.
		if (!recursive.extractBoolean())
		{
			try
			{
				if (requireExistence.extractBoolean())
				{
					Files.delete(path);
				}
				else
				{
					Files.deleteIfExists(path);
				}
			}
			catch (final SecurityException|AccessDeniedException e)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED);
			}
			catch (final NoSuchFileException e)
			{
				return interpreter.primitiveFailure(E_NO_FILE);
			}
			catch (final DirectoryNotEmptyException e)
			{
				return interpreter.primitiveFailure(E_DIRECTORY_NOT_EMPTY);
			}
			catch (final IOException e)
			{
				return interpreter.primitiveFailure(E_IO_ERROR);
			}
		}
		// Otherwise, perform a recursive unlink.
		else
		{
			final Set<FileVisitOption> visitOptions =
				followSymlinks.extractBoolean()
				? EnumSet.of(FileVisitOption.FOLLOW_LINKS)
				: EnumSet.noneOf(FileVisitOption.class);
			try
			{
				final Mutable<Boolean> partialSuccess =
					new Mutable<Boolean>(false);
				Files.walkFileTree(
					path,
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
							return CONTINUE;
						}

						@Override
						public FileVisitResult visitFile (
								final @Nullable Path file,
								final @Nullable BasicFileAttributes unused)
							throws IOException
						{
							assert file != null;
							Files.deleteIfExists(file);
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
							assert dir != null;
							if (e != null)
							{
								partialSuccess.value = true;
							}
							else
							{
								Files.deleteIfExists(dir);
							}
							return CONTINUE;
						}
					});
				if (partialSuccess.value)
				{
					return interpreter.primitiveFailure(E_PARTIAL_SUCCESS);
				}
			}
			catch (final SecurityException e)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED);
			}
			catch (final IOException e)
			{
				return interpreter.primitiveFailure(E_IO_ERROR);
			}
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				EnumerationTypeDescriptor.booleanObject(),
				TupleTypeDescriptor.stringType(),
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
				E_NO_FILE.numericCode(),
				E_DIRECTORY_NOT_EMPTY.numericCode(),
				E_IO_ERROR.numericCode(),
				E_PARTIAL_SUCCESS.numericCode()
			).asSet());
	}
}
