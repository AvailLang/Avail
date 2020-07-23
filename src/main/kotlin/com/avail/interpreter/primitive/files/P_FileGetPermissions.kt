/*
 * P_FileGetPermissions.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.generateSetFrom
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import com.avail.exceptions.AvailErrorCode.E_INVALID_PATH
import com.avail.exceptions.AvailErrorCode.E_IO_ERROR
import com.avail.exceptions.AvailErrorCode.E_OPERATION_NOT_SUPPORTED
import com.avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.execution.Interpreter
import com.avail.io.IOSystem
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.*

/**
 * **Primitive:** Answer the [ordinals][IntegerDescriptor] (into
 * [IOSystem.posixPermissions]) of the
 * [POSIX&#32;file&#32;permissions][PosixFilePermission] that describe the
 * access rights granted by the file named by specified [path][Path].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_FileGetPermissions : Primitive(2, CanInline, HasSideEffect)
{
	/**
	 * A [map][Map] from [POSIX&#32;file][PosixFilePermission] to
	 * [ordinals][IntegerDescriptor].
	 */
	private val permissionMap =
		EnumMap<PosixFilePermission, A_Number>(PosixFilePermission::class.java)

	// This is safe to do statically, since IntegerDescriptor holds the first
	// 255 integers statically. This means that a specific AvailRuntime is not
	// necessary.
	init
	{
		val permissions = IOSystem.posixPermissions
		for (i in permissions.indices)
		{
			permissionMap[permissions[i]] = fromInt(i + 1)
		}
	}

	/**
	 * Convert the specified [set][Set] of [permissions][PosixFilePermission]
	 * into the equivalent [set][SetDescriptor] of
	 * [ordinals][IntegerDescriptor].
	 *
	 * @param permissions
	 *   Some POSIX file permissions.
	 * @return The equivalent ordinals.
	 */
	private fun ordinalsFromPosixPermissions(
		permissions: Set<PosixFilePermission>
	): A_Set = generateSetFrom(permissions) { permissionMap[it]!! }

	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val filename = interpreter.argument(0)
		val followSymlinks = interpreter.argument(1)
		val path: Path =
			try
			{
				IOSystem.fileSystem.getPath(filename.asNativeString())
			}
			catch (e: InvalidPathException)
			{
				return interpreter.primitiveFailure(E_INVALID_PATH)
			}

		val options = IOSystem.followSymlinks(
			followSymlinks.extractBoolean())
		val permissions: Set<PosixFilePermission> =
			try
			{
				Files.getPosixFilePermissions(path, *options)
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
			catch (e: UnsupportedOperationException)
			{
				return interpreter.primitiveFailure(E_OPERATION_NOT_SUPPORTED)
			}

		val ordinals = ordinalsFromPosixPermissions(permissions)
		return interpreter.primitiveSuccess(ordinals)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(stringType(), booleanType),
			setTypeForSizesContentType(
				wholeNumbers, inclusive(1, 9)))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INVALID_PATH,
				E_PERMISSION_DENIED,
				E_IO_ERROR,
				E_OPERATION_NOT_SUPPORTED))
}
