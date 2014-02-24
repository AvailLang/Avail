/**
 * P_436_FileMetadata.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 436</strong>: Answer the {@linkplain BasicFileAttributes
 * metadata} for the file indicated by the specified {@linkplain Path path}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_436_FileMetadata
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_436_FileMetadata().init(2, CanInline, HasSideEffect);

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
		final Path path = runtime.fileSystem().getPath(
			filename.asNativeString());
		final LinkOption[] options = AvailRuntime.followSymlinks(
			followSymlinks.extractBoolean());
		final BasicFileAttributes attributes;
		try
		{
			attributes = Files.readAttributes(
				path, BasicFileAttributes.class, options);
		}
		catch (final IOException e)
		{
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
		catch (final SecurityException e)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED);
		}
		// Build the attribute tuple.
		final Object fileId = attributes.fileKey();
		final A_Tuple tuple = TupleDescriptor.from(
			PojoDescriptor.newPojo(
				RawPojoDescriptor.equalityWrap(fileId),
				PojoTypeDescriptor.forClass(fileId.getClass())),
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
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.oneOrMoreOf(CHARACTER.o()),
				EnumerationTypeDescriptor.booleanObject()),
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.singleInt(6),
				TupleDescriptor.from(
					PojoTypeDescriptor.mostGeneralType(),
					IntegerRangeTypeDescriptor.create(
						IntegerDescriptor.fromInt(1),
						true,
						IntegerDescriptor.fromInt(4),
						true)),
				IntegerRangeTypeDescriptor.create(
					IntegerDescriptor.fromInt(0),
					true,
					IntegerDescriptor.fromLong(Long.MAX_VALUE),
					true)));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				E_PERMISSION_DENIED.numericCode(),
				E_IO_ERROR.numericCode()
			).asSet());
	}
}
