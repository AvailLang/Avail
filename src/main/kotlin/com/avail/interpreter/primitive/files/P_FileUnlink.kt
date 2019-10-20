/*
 * P_FileUnlink.kt
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

import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.*
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.io.IOSystem
import com.avail.utility.Mutable
import java.io.IOException
import java.nio.file.*
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.attribute.BasicFileAttributes
import java.util.*

/**
 * **Primitive:** Unlink the specified [path][Path] from the file system.
 */
@Suppress("unused")
object P_FileUnlink : Primitive(4, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(4)
		val recursive = interpreter.argument(0)
		val filename = interpreter.argument(1)
		val requireExistence = interpreter.argument(2)
		val followSymlinks = interpreter.argument(3)
		val path: Path =
			try
			{
				IOSystem.fileSystem.getPath(filename.asNativeString())
			}
			catch (e: InvalidPathException)
			{
				return interpreter.primitiveFailure(E_INVALID_PATH)
			}

		// Unless the unlink should be recursive, then try unlinking the target
		// directly.
		if (!recursive.extractBoolean())
		{
			try
			{
				if (requireExistence.extractBoolean())
				{
					Files.delete(path)
				}
				else
				{
					Files.deleteIfExists(path)
				}
			}
			catch (e: SecurityException)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED)
			}
			catch (e: AccessDeniedException)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED)
			}
			catch (e: NoSuchFileException)
			{
				return interpreter.primitiveFailure(E_NO_FILE)
			}
			catch (e: DirectoryNotEmptyException)
			{
				return interpreter.primitiveFailure(E_DIRECTORY_NOT_EMPTY)
			}
			catch (e: IOException)
			{
				return interpreter.primitiveFailure(E_IO_ERROR)
			}

		}
		else
		{
			val visitOptions =
				if (followSymlinks.extractBoolean())
				{
					EnumSet.of(FileVisitOption.FOLLOW_LINKS)
				}
				else
				{
					EnumSet.noneOf(FileVisitOption::class.java)
				}
			try
			{
				val partialSuccess = Mutable(false)
				Files.walkFileTree(
					path,
					visitOptions,
					Integer.MAX_VALUE,
					object : FileVisitor<Path>
					{
						override fun preVisitDirectory(
							dir: Path?,
							unused: BasicFileAttributes?): FileVisitResult
						{
							return CONTINUE
						}

						@Throws(IOException::class)
						override fun visitFile(
							file: Path?,
							unused: BasicFileAttributes?): FileVisitResult
						{
							assert(file !== null)
							Files.deleteIfExists(file!!)
							return CONTINUE
						}

						override fun visitFileFailed(
							file: Path?,
							unused: IOException?): FileVisitResult
						{
							partialSuccess.value = true
							return CONTINUE
						}

						@Throws(IOException::class)
						override fun postVisitDirectory(
							dir: Path?,
							e: IOException?): FileVisitResult
						{
							assert(dir !== null)
							if (e !== null)
							{
								partialSuccess.value = true
							}
							else
							{
								Files.deleteIfExists(dir!!)
							}
							return CONTINUE
						}
					})
				if (partialSuccess.value)
				{
					return interpreter.primitiveFailure(E_PARTIAL_SUCCESS)
				}
			}
			catch (e: SecurityException)
			{
				return interpreter.primitiveFailure(E_PERMISSION_DENIED)
			}
			catch (e: IOException)
			{
				return interpreter.primitiveFailure(E_IO_ERROR)
			}

		}// Otherwise, perform a recursive unlink.
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(booleanType(), stringType(), booleanType(), booleanType()),
			TOP.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INVALID_PATH,
				E_PERMISSION_DENIED,
				E_NO_FILE,
				E_DIRECTORY_NOT_EMPTY,
				E_IO_ERROR,
				E_PARTIAL_SUCCESS))
}