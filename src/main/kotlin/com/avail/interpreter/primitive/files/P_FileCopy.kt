/*
 * P_FileCopy.kt
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
import com.avail.exceptions.AvailErrorCode.E_PARTIAL_SUCCESS
import com.avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.execution.Interpreter
import com.avail.io.IOSystem
import com.avail.utility.Mutable
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.CopyOption
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.EnumSet

/**
 * **Primitive:** Recursively copy the source [path][Path] to the destination
 * path.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_FileCopy : Primitive(5, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(5)
		val source = interpreter.argument(0)
		val destination = interpreter.argument(1)
		val followSymlinks = interpreter.argument(2)
		val replace = interpreter.argument(3)
		val copyAttributes = interpreter.argument(4)
		val sourcePath: Path
		val destinationPath: Path =
			try
			{
				sourcePath = IOSystem.fileSystem.getPath(
					source.asNativeString())
				IOSystem.fileSystem.getPath(destination.asNativeString())
			}
			catch (e: InvalidPathException)
			{
				return interpreter.primitiveFailure(E_INVALID_PATH)
			}

		val optionList = mutableListOf<CopyOption>()
		if (replace.extractBoolean())
		{
			optionList.add(StandardCopyOption.REPLACE_EXISTING)
		}
		if (copyAttributes.extractBoolean())
		{
			optionList.add(StandardCopyOption.COPY_ATTRIBUTES)
		}
		val options = optionList.toTypedArray()
		try
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
			val partialSuccess = Mutable(false)
			Files.walkFileTree(
				sourcePath,
				visitOptions,
				Integer.MAX_VALUE,
				object : FileVisitor<Path>
				{
					@Throws(IOException::class)
					override fun preVisitDirectory(
						dir: Path?,
						unused: BasicFileAttributes?): FileVisitResult
					{
						assert(dir !== null)
						val dstDir = destinationPath.resolve(
							sourcePath.relativize(dir!!))
						try
						{
							Files.copy(dir, dstDir, *options)
						}
						catch (e: FileAlreadyExistsException)
						{
							if (!Files.isDirectory(dstDir))
							{
								throw e
							}
						}

						return CONTINUE
					}

					@Throws(IOException::class)
					override fun visitFile(
						file: Path?,
						unused: BasicFileAttributes?): FileVisitResult
					{
						assert(file !== null)
						Files.copy(
							file!!,
							destinationPath.resolve(
								sourcePath.relativize(file)),
							*options)
						return CONTINUE
					}

					override fun visitFileFailed(
						file: Path?,
						unused: IOException?): FileVisitResult
					{
						partialSuccess.value = true
						return CONTINUE
					}

					override fun postVisitDirectory(
						dir: Path?,
						e: IOException?): FileVisitResult
					{
						if (e !== null)
						{
							partialSuccess.value = true
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

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				stringType(),
				stringType(),
				booleanType,
				booleanType,
				booleanType),
			TOP.o
		)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(
			E_INVALID_PATH, E_PERMISSION_DENIED, E_IO_ERROR, E_PARTIAL_SUCCESS))
}
