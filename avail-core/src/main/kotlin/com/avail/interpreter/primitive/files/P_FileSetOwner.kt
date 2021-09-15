/*
 * P_FileSetOwner.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
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
import java.nio.file.attribute.FileOwnerAttributeView
import java.nio.file.attribute.UserPrincipal

/**
 * **Primitive:** [Set&#32;the&#32;owner][FileOwnerAttributeView.setOwner] of
 * the file denoted by the specified [path][Path] to the [user][UserPrincipal]
 * denoted by the specified name.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_FileSetOwner : Primitive(3, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val filename = interpreter.argument(0)
		val userName = interpreter.argument(1)
		val followSymlinks = interpreter.argument(2)
		val fileSystem = IOSystem.fileSystem
		val path: Path =
			try
			{
				IOSystem.fileSystem.getPath(filename.asNativeString())
			}
			catch (e: InvalidPathException)
			{
				return interpreter.primitiveFailure(E_INVALID_PATH)
			}

		val options = IOSystem.followSymlinks(followSymlinks.extractBoolean)
		val view = Files.getFileAttributeView(
			path, FileOwnerAttributeView::class.java, *options)
				?: return interpreter.primitiveFailure(E_OPERATION_NOT_SUPPORTED)
		try
		{
			val lookupService =
				fileSystem.userPrincipalLookupService
			val user =
				lookupService.lookupPrincipalByName(userName.asNativeString())
			view.owner = user
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

		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(stringType, stringType, booleanType), TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INVALID_PATH,
				E_OPERATION_NOT_SUPPORTED,
				E_PERMISSION_DENIED,
				E_IO_ERROR))
}