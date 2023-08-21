/*
 * P_CreateDirectory.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

import avail.descriptor.fiber.A_Fiber
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.fiber.A_Fiber.Companion.heritableFiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.textInterface
import avail.descriptor.fiber.FiberDescriptor.Companion.newFiber
import avail.descriptor.functions.A_Function
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.formatString
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FiberTypeDescriptor.Companion.fiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u8
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.exceptions.AvailErrorCode.E_FILE_EXISTS
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
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.EnumSet

/**
 * **Primitive:** Create a directory with the indicated name and permissions.
 * Answer a new [fiber][A_Fiber] which, if creation is successful, will be
 * started to run the success [function][A_Function]. If the creation fails,
 * then the fiber will be started to apply the failure function to the error
 * code. The fiber runs at the specified priority.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_CreateDirectory : Primitive(5, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(5)
		val directoryName = interpreter.argument(0)
		val ordinals = interpreter.argument(1)
		val succeed = interpreter.argument(2)
		val fail = interpreter.argument(3)
		val priority = interpreter.argument(4)

		val runtime = interpreter.runtime
		val fileSystem = IOSystem.fileSystem
		val path: Path =
			try
			{
				fileSystem.getPath(directoryName.asNativeString())
			}
			catch (e: InvalidPathException)
			{
				return interpreter.primitiveFailure(E_INVALID_PATH)
			}

		val priorityInt = priority.extractInt
		val current = interpreter.fiber()
		val newFiber = newFiber(
			succeed.kind().returnType.typeUnion(fail.kind().returnType),
			interpreter.runtime,
			current.textInterface,
			priorityInt)
		{
			formatString("Asynchronous create directory, %s", path)
		}
		newFiber.availLoader = current.availLoader
		newFiber.heritableFiberGlobals =
			current.heritableFiberGlobals.makeShared()
		newFiber.makeShared()
		succeed.makeShared()
		fail.makeShared()

		val permissions = permissionsFor(ordinals)
		val attr =
			PosixFilePermissions.asFileAttribute(permissions)
		runtime.ioSystem.executeFileTask(
			Runnable {
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
					runtime.runOutermostFunction(
						newFiber, fail, listOf(E_FILE_EXISTS.numericCode()))
					return@Runnable
				}
				catch (e: SecurityException)
				{
					runtime.runOutermostFunction(
						newFiber,
						fail,
						listOf(E_PERMISSION_DENIED.numericCode()))
					return@Runnable
				}
				catch (e: AccessDeniedException)
				{
					runtime.runOutermostFunction(
						newFiber,
						fail,
						listOf(E_PERMISSION_DENIED.numericCode()))
					return@Runnable
				}
				catch (e: IOException)
				{
					runtime.runOutermostFunction(
						newFiber, fail, listOf(E_IO_ERROR.numericCode()))
					return@Runnable
				}
				runtime.runOutermostFunction(newFiber, succeed, emptyList())
			})
		return interpreter.primitiveSuccess(newFiber)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				stringType,
				setTypeForSizesContentType(inclusive(0, 9), inclusive(1, 9)),
				functionType(emptyTuple, TOP.o),
				functionType(
					tuple(
						enumerationWith(
							set(
								E_FILE_EXISTS,
								E_PERMISSION_DENIED,
								E_IO_ERROR))),
						TOP.o),
					u8),
			fiberType(TOP.o))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_INVALID_PATH))

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
