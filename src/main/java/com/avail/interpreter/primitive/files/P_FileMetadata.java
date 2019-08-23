/*
 * P_FileMetadata.java
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

import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.io.IOSystem;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerDescriptor.fromLong;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.singleInt;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray;
import static com.avail.descriptor.PojoDescriptor.newPojo;
import static com.avail.descriptor.PojoTypeDescriptor.mostGeneralPojoType;
import static com.avail.descriptor.PojoTypeDescriptor.pojoTypeForClass;
import static com.avail.descriptor.RawPojoDescriptor.equalityPojo;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;
import static java.lang.Long.MAX_VALUE;

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
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_FileMetadata().init(
			2, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_String filename = interpreter.argument(0);
		final A_Atom followSymlinks = interpreter.argument(1);
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
		final LinkOption[] options = IOSystem.followSymlinks(
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
		final A_Tuple tuple = tupleFromArray(
			newPojo(
				equalityPojo(raw),
				pojoTypeForClass(rawClass)),
			fromInt(
				attributes.isRegularFile() ? 1
					: attributes.isDirectory() ? 2
						: attributes.isSymbolicLink() ? 3
							: 4), fromLong(
				attributes.creationTime().toMillis()),
			fromLong(
				attributes.lastModifiedTime().toMillis()),
			fromLong(
				attributes.lastAccessTime().toMillis()),
			fromLong(
				attributes.size()));
		return interpreter.primitiveSuccess(tuple);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return
			functionType(tuple(stringType(), booleanType()),
				tupleTypeForSizesTypesDefaultType(
					singleInt(6),
					tuple(
						mostGeneralPojoType(),
						inclusive(1, 4)),
					inclusive(0, MAX_VALUE)));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(E_INVALID_PATH, E_PERMISSION_DENIED, E_IO_ERROR));
	}
}
