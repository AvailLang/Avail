/*
 * P_FileRealPath.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

import avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.exceptions.AvailErrorCode.E_INVALID_PATH
import avail.exceptions.AvailErrorCode.E_IO_ERROR
import avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.execution.Interpreter
import avail.io.IOSystem
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * **Primitive:** Answer the [real&#32;path][Path.toRealPath] that corresponds
 * to the specified path.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_FileRealPath : Primitive(2, CanInline, HasSideEffect)
{
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

		val options = IOSystem.followSymlinks(followSymlinks.extractBoolean)
		val realPath: Path =
			try
			{
				path.toRealPath(*options)
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

		return interpreter.primitiveSuccess(stringFrom(realPath.toString()))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(stringType, booleanType), stringType)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_INVALID_PATH, E_PERMISSION_DENIED, E_IO_ERROR))
}
