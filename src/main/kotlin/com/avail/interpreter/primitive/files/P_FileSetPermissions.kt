/*
 * P_FileSetPermissions.java
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

package com.avail.interpreter.primitive.files

import com.avail.descriptor.A_Set
import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.*
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.io.IOSystem
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.*

/**
 * **Primitive:** Set the access rights for the file specified
 * by the given [path][Path].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_FileSetPermissions : Primitive(3, CanInline, HasSideEffect)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val filename = interpreter.argument(0)
		val ordinals = interpreter.argument(1)
		val followSymlinks = interpreter.argument(2)
		val path: Path
		try
		{
			path = IOSystem.fileSystem.getPath(filename.asNativeString())
		}
		catch (e: InvalidPathException)
		{
			return interpreter.primitiveFailure(E_INVALID_PATH)
		}

		val permissions = permissionsFor(ordinals)
		val options = IOSystem.followSymlinks(
			followSymlinks.extractBoolean())
		val view = Files.getFileAttributeView(
			path, PosixFileAttributeView::class.java, *options)
		           ?: return interpreter.primitiveFailure(E_OPERATION_NOT_SUPPORTED)
		try
		{
			view.setPermissions(permissions)
		}
		catch (e: SecurityException)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED)
		}
		catch (e: AccessDeniedException)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED)
		}
		catch (e: IOException)
		{
			return interpreter.primitiveFailure(E_IO_ERROR)
		}

		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(tuple(stringType(), setTypeForSizesContentType(
			inclusive(0, 9),
			inclusive(1, 9)), booleanType()), TOP.o())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(E_INVALID_PATH, E_PERMISSION_DENIED, E_IO_ERROR,
			    E_OPERATION_NOT_SUPPORTED))
	}

	/**
	 * Convert the specified [set][SetDescriptor] of [ ] into the corresponding [set][Set]
	 * of [POSIX file permissions][PosixFilePermission].
	 *
	 * @param ordinals
	 * Some ordinals.
	 * @return The equivalent POSIX file permissions.
	 */
	private fun permissionsFor(
		ordinals: A_Set): Set<PosixFilePermission>
	{
		val allPermissions = IOSystem.posixPermissions
		val permissions = EnumSet.noneOf(PosixFilePermission::class.java)
		for (ordinal in ordinals)
		{
			permissions.add(allPermissions[ordinal.extractInt() - 1])
		}
		return permissions
	}
}