/**
 * P_FileMetadata.java
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

import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Answer the {@linkplain BasicFileAttributes
 * metadata} for the file indicated by the specified {@linkplain Path path}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_FileMetadata
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_FileMetadata().init(
			2, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_String filename = args.get(0);
		final A_Atom followSymlinks = args.get(1);
		final AvailRuntime runtime = AvailRuntime.current();
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
		final LinkOption[] options = AvailRuntime.followSymlinks(
			followSymlinks.extractBoolean());
		final BasicFileAttributes attributes;
		try
		{
			attributes = Files.readAttributes(
				path, BasicFileAttributes.class, options);
		}
		catch (final SecurityException|AccessDeniedException e)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED);
		}
		catch (final IOException e)
		{
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
		// Build the attribute tuple.
		final Object fileId = attributes.fileKey();
		final Object raw;
		final Class<?> rawClass;
		// The file key may be null, in which case just use the path itself.
		// Try to use the absolute path if it's available, otherwise just use
		// the one supplied.
		if (fileId != null)
		{
			raw = fileId;
			rawClass = fileId.getClass();
		}
		else
		{
			// Curse you, Java, for your incomplete flow analysis.
			Object temp;
			try
			{
				temp = path.toAbsolutePath();
			}
			catch (final SecurityException|IOError e)
			{
				temp = path;
			}
			raw = temp;
			rawClass = Path.class;
		}
		final A_Tuple tuple = TupleDescriptor.tuple(
			PojoDescriptor.newPojo(
				RawPojoDescriptor.equalityWrap(raw),
				PojoTypeDescriptor.pojoTypeForClass(rawClass)),
			IntegerDescriptor.fromInt(
				attributes.isRegularFile() ? 1
				: attributes.isDirectory() ? 2
				: attributes.isSymbolicLink() ? 3
				: 4),
			IntegerDescriptor.fromLong(
				attributes.creationTime().toMillis()),
			IntegerDescriptor.fromLong(
				attributes.lastModifiedTime().toMillis()),
			IntegerDescriptor.fromLong(
				attributes.lastAccessTime().toMillis()),
			IntegerDescriptor.fromLong(
				attributes.size()));
		return interpreter.primitiveSuccess(tuple);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.functionType(
			TupleDescriptor.tuple(
				stringType(),
				booleanType()),
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.singleInt(6),
				TupleDescriptor.tuple(
					PojoTypeDescriptor.mostGeneralPojoType(),
					IntegerRangeTypeDescriptor.inclusive(1, 4)),
				IntegerRangeTypeDescriptor.inclusive(0, Long.MAX_VALUE)));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.enumerationWith(
			SetDescriptor.set(
				E_INVALID_PATH,
				E_PERMISSION_DENIED,
				E_IO_ERROR));
	}
}
