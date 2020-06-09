/*
 * P_FileMetadata.kt
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
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import com.avail.descriptor.pojos.PojoDescriptor.Companion.newPojo
import com.avail.descriptor.pojos.RawPojoDescriptor.Companion.equalityPojo
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.mostGeneralPojoType
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.pojoTypeForClass
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import com.avail.exceptions.AvailErrorCode.E_INVALID_PATH
import com.avail.exceptions.AvailErrorCode.E_IO_ERROR
import com.avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.execution.Interpreter
import com.avail.io.IOSystem
import java.io.IOError
import java.io.IOException
import java.lang.Long.MAX_VALUE
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * **Primitive:** Answer the [metadata][BasicFileAttributes] for the file
 * indicated by the specified [path][Path].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_FileMetadata : Primitive(2, CanInline, HasSideEffect)
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

		val options =
			IOSystem.followSymlinks(followSymlinks.extractBoolean())
		val attributes: BasicFileAttributes =
			try
			{
				Files.readAttributes(
					path, BasicFileAttributes::class.java, *options)
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

		// Build the attribute tuple.
		val fileId = attributes.fileKey()
		val raw: Any
		// The file key may be null, in which case just use the path itself.
		// Try to use the absolute path if it's available, otherwise just use
		// the one supplied.
		val rawClass: Class<*> =
			if (fileId !== null)
			{
				raw = fileId
				fileId.javaClass
			}
			else
			{
				raw = try {
					path.toAbsolutePath()
				} catch (e: SecurityException) {
					path
				} catch (e: IOError) {
					path
				}
				Path::class.java
			}
		val tuple = tupleFromArray(
			newPojo(equalityPojo(raw), pojoTypeForClass(rawClass)),
			fromInt(
				when
				{
					attributes.isRegularFile -> 1
					attributes.isDirectory -> 2
					attributes.isSymbolicLink -> 3
					else -> 4
				}), fromLong(
			attributes.creationTime().toMillis()),
			fromLong(attributes.lastModifiedTime().toMillis()),
			fromLong(attributes.lastAccessTime().toMillis()),
			fromLong(attributes.size()))
		return interpreter.primitiveSuccess(tuple)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(stringType(),
				  booleanType()),
			tupleTypeForSizesTypesDefaultType(
				singleInt(6),
				tuple(mostGeneralPojoType(), inclusive(1, 4)),
				inclusive(0, MAX_VALUE)))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_INVALID_PATH, E_PERMISSION_DENIED, E_IO_ERROR))
}
