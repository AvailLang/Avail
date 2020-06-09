/*
 * P_CreateDirectory.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.files

import com.avail.descriptor.fiber.A_Fiber
import com.avail.descriptor.fiber.FiberDescriptor.Companion.newFiber
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.StringDescriptor.Companion.formatString
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FiberTypeDescriptor.Companion.fiberType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.bytes
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import com.avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import com.avail.descriptor.types.TupleTypeDescriptor.stringType
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_FILE_EXISTS
import com.avail.exceptions.AvailErrorCode.E_INVALID_PATH
import com.avail.exceptions.AvailErrorCode.E_IO_ERROR
import com.avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.execution.Interpreter
import com.avail.io.IOSystem
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.*

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

		val runtime = interpreter.runtime()
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

		val priorityInt = priority.extractInt()
		val current = interpreter.fiber()
		val newFiber =
			newFiber(
				succeed.kind().returnType().typeUnion(fail.kind().returnType()),
				priorityInt)
			{ formatString("Asynchronous create directory, %s", path) }
		newFiber.setAvailLoader(current.availLoader())
		newFiber.setHeritableFiberGlobals(
			current.heritableFiberGlobals().makeShared())
		newFiber.setTextInterface(current.textInterface())
		newFiber.makeShared()
		succeed.makeShared()
		fail.makeShared()

		val permissions = permissionsFor(ordinals)
		val attr =
			PosixFilePermissions.asFileAttribute(permissions)
		runtime.ioSystem().executeFileTask(
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
                   Interpreter.runOutermostFunction(
                       runtime,
                       newFiber,
                       fail,
                       listOf(E_FILE_EXISTS.numericCode()))
                   return@Runnable
               }
               catch (e: SecurityException)
               {
                   Interpreter.runOutermostFunction(
                       runtime,
                       newFiber,
                       fail,
                       listOf(E_PERMISSION_DENIED.numericCode()))
                   return@Runnable
               }
               catch (e: AccessDeniedException)
               {
                   Interpreter.runOutermostFunction(
	                   runtime,
	                   newFiber,
	                   fail,
	                   listOf(E_PERMISSION_DENIED.numericCode()))
                   return@Runnable
               }
               catch (e: IOException)
               {
                   Interpreter.runOutermostFunction(
                       runtime,
                       newFiber,
                       fail,
                       listOf(E_IO_ERROR.numericCode()))
                   return@Runnable
               }

               Interpreter.runOutermostFunction(
                   runtime,
                   newFiber,
                   succeed,
                   emptyList())
           })
		return interpreter.primitiveSuccess(newFiber)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			ObjectTupleDescriptor.tuple(stringType(),
										setTypeForSizesContentType(
																				  inclusive(0, 9),
																				  inclusive(1, 9)),
										functionType(emptyTuple(), TOP.o()),
										functionType(tuple(
																				  enumerationWith(
																					  set(E_FILE_EXISTS, E_PERMISSION_DENIED, E_IO_ERROR))),
																						   TOP.o()), bytes()),
			fiberType(TOP.o()))

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
			permissions.add(allPermissions[ordinal.extractInt() - 1])
		}
		return permissions
	}
}
