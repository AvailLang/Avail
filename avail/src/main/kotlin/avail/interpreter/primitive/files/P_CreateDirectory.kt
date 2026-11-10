/*
 * P_CreateDirectory.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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

package avail.interpreter.primitive.files

import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Set
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.exceptions.AvailErrorCode.E_FILE_EXISTS
import avail.exceptions.AvailErrorCode.E_INVALID_PATH
import avail.exceptions.AvailErrorCode.E_IO_ERROR
import avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanSuspend
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive2
import avail.io.IOSystem
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.EnumSet

/**
 * **Primitive:** Create a directory with the indicated name and permissions.
 *
 * The calling fiber is suspended while the directory is created on the
 * [IOSystem]'s file executor, since changing the file system's metadata can
 * take a long time.  The fiber resumes when the directory has been created, or
 * fails with the reason it could not be.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_CreateDirectory : Primitive2(CanSuspend, HasSideEffect)
{
	override fun attempt2(
		interpreter: Interpreter,
		directoryName: AvailObject,
		ordinals: AvailObject
	): A_BasicObject? = interpreter.run {
		val fileSystem = IOSystem.fileSystem
		val path: Path =
			try
			{
				fileSystem.getPath(directoryName.asNativeString())
			}
			catch (e: InvalidPathException)
			{
				return fail(E_INVALID_PATH)
			}
		val permissions = permissionsFor(ordinals)
		val attr = PosixFilePermissions.asFileAttribute(permissions)
		return suspendThen {
			// Creating a directory changes the file system's metadata, which
			// can block for a long time, such as while the file system commits
			// a transaction that a concurrent drive flush has claimed, so it
			// runs on the I/O system's file executor instead of on an
			// interpreter thread.
			runtime.ioSystem.executeFileTask {
				try
				{
					try
					{
						Files.createDirectory(path, attr)
					}
					catch (e: UnsupportedOperationException)
					{
						// Retry without setting the permissions.
						Files.createDirectory(path)
					}
				}
				catch (e: FileAlreadyExistsException)
				{
					fail(E_FILE_EXISTS)
					return@executeFileTask
				}
				catch (e: SecurityException)
				{
					fail(E_PERMISSION_DENIED)
					return@executeFileTask
				}
				catch (e: AccessDeniedException)
				{
					fail(E_PERMISSION_DENIED)
					return@executeFileTask
				}
				catch (e: IOException)
				{
					fail(E_IO_ERROR)
					return@executeFileTask
				}
				succeed(nil)
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				stringType,
				setTypeForSizesContentType(inclusive(0, 9), inclusive(1, 9))),
			TOP())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INVALID_PATH,
				E_FILE_EXISTS,
				E_PERMISSION_DENIED,
				E_IO_ERROR))

	/**
	 * Convert the specified [set][A_Set] of
	 * [ordinals][IntegerDescriptor] into the corresponding [set][Set] of
	 * [POSIX&#32;file&#32;permissions][PosixFilePermission].
	 *
	 * @param ordinals
	 *   Some ordinals.
	 * @return The equivalent POSIX file permissions.
	 */
	private fun permissionsFor(
		ordinals: A_Set): Set<PosixFilePermission>
	{
		val allPermissions = IOSystem.posixPermissions
		val permissions = EnumSet.noneOf(PosixFilePermission::class.java)
		for (ordinal in ordinals)
		{
			permissions.add(allPermissions[ordinal.extractInt - 1])
		}
		return permissions
	}
}
