/**
 * P_435_FileSetPermissions.java
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
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 435</strong>: Set the access rights for the file specified
 * by the given {@linkplain Path path}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_435_FileSetPermissions
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_435_FileSetPermissions().init(2, CanInline, HasSideEffect);

	/**
	 * Convert the specified {@linkplain SetDescriptor set} of {@linkplain
	 * IntegerDescriptor ordinals} into the corresponding {@linkplain Set set}
	 * of {@linkplain PosixFilePermission POSIX file permissions}.
	 *
	 * @param ordinals
	 *        Some ordinals.
	 * @return The equivalent POSIX file permissions.
	 */
	private Set<PosixFilePermission> permissionsFor (final A_Set ordinals)
	{
		final PosixFilePermission[] allPermissions =
			AvailRuntime.posixPermissions();
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
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_String filename = args.get(0);
		final A_Set ordinals = args.get(1);
		final AvailRuntime runtime = AvailRuntime.current();
		final Path path = runtime.fileSystem().getPath(
			filename.asNativeString());
		final Set<PosixFilePermission> permissions = permissionsFor(ordinals);
		try
		{
			Files.setPosixFilePermissions(path, permissions);
		}
		catch (final SecurityException e)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED);
		}
		catch (final IOException e)
		{
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
		catch (final UnsupportedOperationException e)
		{
			return interpreter.primitiveFailure(E_PRIMITIVE_NOT_SUPPORTED);
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.oneOrMoreOf(CHARACTER.o()),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.create(
						IntegerDescriptor.fromInt(0),
						true,
						IntegerDescriptor.fromInt(9),
						true),
					IntegerRangeTypeDescriptor.create(
						IntegerDescriptor.fromInt(1),
						true,
						IntegerDescriptor.fromInt(9),
						true))),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				E_PERMISSION_DENIED.numericCode(),
				E_IO_ERROR.numericCode(),
				E_PRIMITIVE_NOT_SUPPORTED.numericCode()
			).asSet());
	}
}
