/*
 * P_FileGetPermissions.java
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

import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.io.IOSystem;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive:</strong> Answer the {@linkplain IntegerDescriptor
 * ordinals} (into {@link IOSystem.Companion#posixPermissions}) of the
 * {@linkplain PosixFilePermission POSIX file permissions} that describe the
 * access rights granted by the file named by specified {@linkplain Path path}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_FileGetPermissions
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
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
		final PosixFilePermission[] permissions = IOSystem.Companion.posixPermissions();
		for (int i = 0; i < permissions.length; i++)
		{
			permissionMap.put(permissions[i], fromInt(i + 1));
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
	private static A_Set ordinalsFromPosixPermissions (
		final Set<PosixFilePermission> permissions)
	{
		A_Set permissionOrdinals = emptySet();
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
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_String filename = interpreter.argument(0);
		final A_Atom followSymlinks = interpreter.argument(1);
		final Path path;
		try
		{
			path = IOSystem.Companion.getFileSystem().getPath(filename.asNativeString());
		}
		catch (final InvalidPathException e)
		{
			return interpreter.primitiveFailure(E_INVALID_PATH);
		}
		final LinkOption[] options = IOSystem.Companion.followSymlinks(
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
		return functionType(
			tuple(
				stringType(),
				booleanType()),
			setTypeForSizesContentType(
				wholeNumbers(),
				inclusive(1, 9)));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_INVALID_PATH,
				E_PERMISSION_DENIED,
				E_IO_ERROR,
				E_OPERATION_NOT_SUPPORTED));
	}
}
