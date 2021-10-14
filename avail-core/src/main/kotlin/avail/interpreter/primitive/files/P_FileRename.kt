/*
 * P_FileRename.kt
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
package avail.interpreter.primitive.files

import avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import avail.descriptor.fiber.FiberDescriptor.Companion.newFiber
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FiberTypeDescriptor.Companion.fiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.bytes
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailErrorCode.E_FILE_EXISTS
import avail.exceptions.AvailErrorCode.E_INVALID_PATH
import avail.exceptions.AvailErrorCode.E_IO_ERROR
import avail.exceptions.AvailErrorCode.E_NO_FILE
import avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.execution.Interpreter
import avail.io.IOSystem
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.CopyOption
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * **Primitive:** Rename the source [path][Path] to the destination path. Try
 * not to overwrite an existing destination. This operation is only likely to
 * work for two paths provided by the same [file&#32;store][FileStore].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_FileRename : Primitive(6, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(6)
		val source = interpreter.argument(0)
		val destination = interpreter.argument(1)
		val replaceExisting = interpreter.argument(2)
		val succeed = interpreter.argument(3)
		val fail = interpreter.argument(4)
		val priority = interpreter.argument(5)

		val runtime = interpreter.runtime
		val (sourcePath, destinationPath) =
			try
			{
				Pair(
					IOSystem.fileSystem.getPath(source.asNativeString()),
					IOSystem.fileSystem.getPath(destination.asNativeString()))
			}
			catch (e: InvalidPathException)
			{
				return interpreter.primitiveFailure(E_INVALID_PATH)
			}

		val priorityInt = priority.extractInt
		val current = interpreter.fiber()
		val newFiber = newFiber(
			succeed.kind().returnType.typeUnion(fail.kind().returnType),
			priorityInt)
		{
			stringFrom(
				"Asynchronous file rename, $sourcePath → $destinationPath")
		}
		newFiber.setAvailLoader(current.availLoader())
		newFiber.setHeritableFiberGlobals(
			current.heritableFiberGlobals().makeShared())
		newFiber.setTextInterface(current.textInterface())
		newFiber.makeShared()
		succeed.makeShared()
		fail.makeShared()

		val replace = replaceExisting.extractBoolean
		runtime.ioSystem.executeFileTask {
			val options = mutableListOf<CopyOption>()
			if (replace)
			{
				options.add(StandardCopyOption.REPLACE_EXISTING)
			}
			try
			{
				Files.move(
					sourcePath,
					destinationPath,
					*options.toTypedArray())
			}
			catch (e: Exception)
			{
				val errorCode = when (e)
				{
					is SecurityException -> E_PERMISSION_DENIED
					is AccessDeniedException -> E_PERMISSION_DENIED
					is NoSuchFileException -> E_NO_FILE
					is FileAlreadyExistsException -> E_FILE_EXISTS
					is IOException -> E_IO_ERROR
					else -> throw e
				}
				Interpreter.runOutermostFunction(
					runtime, newFiber, fail, listOf(errorCode.numericCode()))
				return@executeFileTask
			}

			Interpreter.runOutermostFunction(
				runtime, newFiber, succeed, emptyList())
		}
		return interpreter.primitiveSuccess(newFiber)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tupleFromArray(
				stringType,
				stringType,
				booleanType,
				functionType(emptyTuple, TOP.o),
				functionType(
					tuple(enumerationWith(
						set(
							E_PERMISSION_DENIED,
							E_FILE_EXISTS,
							E_NO_FILE,
							E_IO_ERROR))),
					TOP.o),
				bytes),
			fiberType(TOP.o))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_INVALID_PATH))
}
