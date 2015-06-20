/**
 * P_FileGetPermissions.java
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

import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Answer the {@linkplain IntegerDescriptor
 * ordinals} (into {@link AvailRuntime#posixPermissions()}) of the {@linkplain
 * PosixFilePermission POSIX file permissions} that describe the access rights
 * granted by the file named by specified {@linkplain Path path}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_FileGetPermissions
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_FileGetPermissions().init(
			2, CanInline, HasSideEffect);

	/**
	 * A {@linkplain Map map} from {@linkplain PosixFilePermission POSIX file
	 * permissions} to {@linkplain IntegerDescriptor ordinals}.
	 */
	private static final Map<PosixFilePermission, A_Number> permissionMap =
		new EnumMap<>(PosixFilePermission.class);

	// This is safe to do statically, since IntegerDescriptor holds the first
	// 255 integers statically. This means that a specific AvailRuntime is not
	// necessary.
	static
	{
		final PosixFilePermission[] permissions =
			AvailRuntime.posixPermissions();
		for (int i = 0; i < permissions.length; i++)
		{
			permissionMap.put(permissions[i], IntegerDescriptor.fromInt(i + 1));
		}
	}

	/**
	 * Convert the specified {@linkplain Set set} of {@linkplain
	 * PosixFilePermission POSIX file permissions} into the equivalent
	 * {@linkplain SetDescriptor set} of {@linkplain IntegerDescriptor
	 * ordinals}.
	 *
	 * @param permissions
	 *        Some POSIX file permissions.
	 * @return The equivalent ordinals.
	 */
	private A_Set ordinalsFromPosixPermissions (
		final Set<PosixFilePermission> permissions)
	{
		A_Set permissionOrdinals = SetDescriptor.empty();
		for (final PosixFilePermission permission : permissions)
		{
			final A_Number ordinal = permissionMap.get(permission);
			permissionOrdinals = permissionOrdinals.setWithElementCanDestroy(
				ordinal, true);
		}
		return permissionOrdinals;
	}

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
			path = runtime.fileSystem().getPath(
				filename.asNativeString());
		}
		catch (final InvalidPathException e)
		{
			return interpreter.primitiveFailure(E_INVALID_PATH);
		}
		final LinkOption[] options = AvailRuntime.followSymlinks(
			followSymlinks.extractBoolean());
		final Set<PosixFilePermission> permissions;
		try
		{
			permissions = Files.getPosixFilePermissions(path, options);
		}
		catch (final SecurityException|AccessDeniedException e)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED);
		}
		catch (final IOException e)
		{
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
		catch (final UnsupportedOperationException e)
		{
			return interpreter.primitiveFailure(E_OPERATION_NOT_SUPPORTED);
		}
		final A_Set ordinals = ordinalsFromPosixPermissions(permissions);
		return interpreter.primitiveSuccess(ordinals);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.stringType(),
				EnumerationTypeDescriptor.booleanObject()),
			SetTypeDescriptor.setTypeForSizesContentType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				IntegerRangeTypeDescriptor.inclusive(
					IntegerDescriptor.fromInt(1),
					IntegerDescriptor.fromInt(9))));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				E_INVALID_PATH.numericCode(),
				E_PERMISSION_DENIED.numericCode(),
				E_IO_ERROR.numericCode(),
				E_OPERATION_NOT_SUPPORTED.numericCode()
			).asSet());
	}
}
