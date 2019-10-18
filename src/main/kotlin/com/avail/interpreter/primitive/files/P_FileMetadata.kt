/*
 * P_FileMetadata.kt
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
import com.avail.descriptor.IntegerDescriptor.fromInt
import com.avail.descriptor.IntegerDescriptor.fromLong
import com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive
import com.avail.descriptor.IntegerRangeTypeDescriptor.singleInt
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray
import com.avail.descriptor.PojoDescriptor.newPojo
import com.avail.descriptor.PojoTypeDescriptor.mostGeneralPojoType
import com.avail.descriptor.PojoTypeDescriptor.pojoTypeForClass
import com.avail.descriptor.RawPojoDescriptor.equalityPojo
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType
import com.avail.exceptions.AvailErrorCode.*
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
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
 * **Primitive:** Answer the [ metadata][BasicFileAttributes] for the file indicated by the specified [path][Path].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_FileMetadata : Primitive(2, CanInline, HasSideEffect)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val filename = interpreter.argument(0)
		val followSymlinks = interpreter.argument(1)
		val path: Path
		try
		{
			path = IOSystem.fileSystem.getPath(filename.asNativeString())
		}
		catch (e: InvalidPathException)
		{
			return interpreter.primitiveFailure(E_INVALID_PATH)
		}

		val options = IOSystem.followSymlinks(
			followSymlinks.extractBoolean())
		val attributes: BasicFileAttributes
		try
		{
			attributes = Files.readAttributes(
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
		val rawClass: Class<*>
		// The file key may be null, in which case just use the path itself.
		// Try to use the absolute path if it's available, otherwise just use
		// the one supplied.
		if (fileId !== null)
		{
			raw = fileId
			rawClass = fileId.javaClass
		}
		else
		{
			// Curse you, Java, for your incomplete flow analysis.
			var temp: Any
			try
			{
				temp = path.toAbsolutePath()
			}
			catch (e: SecurityException)
			{
				temp = path
			}
			catch (e: IOError)
			{
				temp = path
			}

			raw = temp
			rawClass = Path::class.java
		}
		val tuple = tupleFromArray(
			newPojo(
				equalityPojo(raw),
				pojoTypeForClass(rawClass)),
			fromInt(
				if (attributes.isRegularFile)
					1
				else if (attributes.isDirectory)
					2
				else if (attributes.isSymbolicLink)
					3
				else
					4), fromLong(
			attributes.creationTime().toMillis()),
			fromLong(
				attributes.lastModifiedTime().toMillis()),
			fromLong(
				attributes.lastAccessTime().toMillis()),
			fromLong(
				attributes.size()))
		return interpreter.primitiveSuccess(tuple)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(tuple(stringType(), booleanType()),
		                    tupleTypeForSizesTypesDefaultType(
			                    singleInt(6),
			                    tuple(
				                    mostGeneralPojoType(),
				                    inclusive(1, 4)),
			                    inclusive(0, MAX_VALUE)))
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(E_INVALID_PATH, E_PERMISSION_DENIED, E_IO_ERROR))
	}

}