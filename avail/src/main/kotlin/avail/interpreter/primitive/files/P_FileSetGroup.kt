/*
 * P_FileSetGroup.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.interpreter.primitive.files

import avail.descriptor.representation.A_Atom.Companion.extractBoolean
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.exceptions.AvailErrorCode.E_INVALID_PATH
import avail.exceptions.AvailErrorCode.E_IO_ERROR
import avail.exceptions.AvailErrorCode.E_OPERATION_NOT_SUPPORTED
import avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanSuspend
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive3
import avail.io.IOSystem
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributeView

/**
 * **Primitive:**
 * [Set&#32;the&#32;group&#32;owner][PosixFileAttributeView.setGroup] of the
 * file denoted by the specified [path][Path] to the [group][GroupPrincipal]
 * denoted by the specified name.
 *
 * The lookup and the change run on the [IOSystem]'s file executor while the
 * calling fiber is suspended, since looking up a group and changing the file
 * system's metadata can each take a long time.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_FileSetGroup : Primitive3(CanSuspend, HasSideEffect)
{
	override fun attempt3(
		interpreter: Interpreter,
		filename: AvailObject,
		groupName: AvailObject,
		followSymlinks: AvailObject
	): A_BasicObject? = interpreter.run {
		val fileSystem = IOSystem.fileSystem
		val path: Path =
			try
			{
				IOSystem.fileSystem.getPath(filename.asNativeString())
			}
			catch (e: InvalidPathException)
			{
				return fail(E_INVALID_PATH)
			}

		val options = IOSystem.followSymlinks(followSymlinks.extractBoolean)
		val view = Files.getFileAttributeView(
			path, PosixFileAttributeView::class.java, *options)
				?: return fail(E_OPERATION_NOT_SUPPORTED)
		val groupNameString = groupName.asNativeString()
		return suspendThen {
			// Looking up the group can block on directory services, and
			// changing the group changes the file system's metadata, which can
			// block for a long time, such as while the file system commits a
			// transaction that a concurrent drive flush has claimed, so both
			// run on the I/O system's file executor instead of on an
			// interpreter thread.
			runtime.ioSystem.executeFileTask {
				try
				{
					val lookupService = fileSystem.userPrincipalLookupService
					val group =
						lookupService.lookupPrincipalByGroupName(
							groupNameString)
					view.setGroup(group)
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
				catch (e: UnsupportedOperationException)
				{
					fail(E_OPERATION_NOT_SUPPORTED)
					return@executeFileTask
				}
				succeed(nil)
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(stringType, stringType, booleanType), TOP())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INVALID_PATH,
				E_OPERATION_NOT_SUPPORTED,
				E_PERMISSION_DENIED,
				E_IO_ERROR))
}
