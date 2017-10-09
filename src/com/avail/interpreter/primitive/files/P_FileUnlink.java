/**
 * P_FileUnlink.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.utility.Mutable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;
import static java.nio.file.FileVisitResult.CONTINUE;

/**
 * <strong>Primitive:</strong> Unlink the specified {@linkplain Path path}
 * from the file system.
 */
public final class P_FileUnlink
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_FileUnlink().init(
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
		final AvailRuntime runtime = currentRuntime();
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
					new Mutable<>(false);
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
		return interpreter.primitiveSuccess(nil);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return
			functionType(tuple(booleanType(), stringType(), booleanType(),
				booleanType()), TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return
			enumerationWith(set(E_INVALID_PATH, E_PERMISSION_DENIED, E_NO_FILE,
				E_DIRECTORY_NOT_EMPTY, E_IO_ERROR, E_PARTIAL_SUCCESS));
	}
}
