/*
 * P_FileOpen.kt
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

import com.avail.AvailRuntime.currentRuntime
import com.avail.descriptor.A_Set
import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive
import com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.RawPojoDescriptor.identityPojo
import com.avail.descriptor.SetDescriptor
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.FILE_KEY
import com.avail.descriptor.atoms.AtomDescriptor.createAtom
import com.avail.exceptions.AvailErrorCode.*
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.io.IOSystem
import com.avail.io.IOSystem.FileHandle
import java.io.IOException
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.*
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.*

/**
 * **Primitive:** Open an [file][AsynchronousFileChannel]. Answer a
 * [handle][AtomDescriptor] that uniquely identifies the file.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_FileOpen : Primitive(4, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(4)
		val filename = interpreter.argument(0)
		val alignment = interpreter.argument(1)
		val options = interpreter.argument(2)
		val permissions = interpreter.argument(3)

		if (!alignment.isInt)
		{
			return interpreter.primitiveFailure(E_EXCEEDS_VM_LIMIT)
		}
		var alignmentInt = alignment.extractInt()
		if (alignmentInt == 0)
		{
			// Plug in the default alignment for the device on which the
			// filename is located.  Ahem, erm, Java actually doesn't make this
			// available in any way.  Fudge it for now as 4096.
			alignmentInt = 4096
		}
		assert(alignmentInt > 0)
		val ioSystem = currentRuntime().ioSystem()
		val fileOptions = openOptionsFor(options)
		val fileAttributes = permissionsFor(permissions)
		if (!fileOptions.contains(READ) && !fileOptions.contains(WRITE))
		{
			return interpreter.primitiveFailure(E_ILLEGAL_OPTION)
		}
		val path: Path =
			try
			{
				IOSystem.fileSystem.getPath(filename.asNativeString())
			}
			catch (e: InvalidPathException)
			{
				return interpreter.primitiveFailure(E_INVALID_PATH)
			}

		val atom = createAtom(filename, nil)
		val channel: AsynchronousFileChannel =
			try
			{
				ioSystem.openFile(path, fileOptions, *fileAttributes)
			}
			catch (e: IllegalArgumentException)
			{
				return interpreter.primitiveFailure(E_ILLEGAL_OPTION)
			}
			catch (e: UnsupportedOperationException)
			{
				return interpreter.primitiveFailure(E_OPERATION_NOT_SUPPORTED)
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

		val fileHandle = FileHandle(
			filename,
			alignmentInt,
			fileOptions.contains(READ),
			fileOptions.contains(WRITE),
			channel)
		val pojo = identityPojo(fileHandle)
		atom.setAtomProperty(FILE_KEY.atom, pojo)
		return interpreter.primitiveSuccess(atom)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				stringType(),
				wholeNumbers(),
				setTypeForSizesContentType(
					wholeNumbers(), inclusive(0, 9)),
				setTypeForSizesContentType(
					wholeNumbers(), inclusive(1, 9))),
			ATOM.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_EXCEEDS_VM_LIMIT,
				E_INVALID_PATH,
				E_ILLEGAL_OPTION,
				E_OPERATION_NOT_SUPPORTED,
				E_PERMISSION_DENIED,
				E_IO_ERROR))

	/**
	 * Stash the enum values for StandardOpenOption to avoid array copying.
	 */
	internal val allStandardOpenOptions = StandardOpenOption.values()

	/**
	 * Construct the [set][EnumSet] of [open][OpenOption] that correspond to the
	 * supplied [set][SetDescriptor] of integral option indicators.
	 *
	 * @param optionInts
	 *   Some integral option indicators.
	 * @return The implied open options.
	 */
	private fun openOptionsFor(optionInts: A_Set): Set<OpenOption>
	{
		val options =
			EnumSet.noneOf(StandardOpenOption::class.java)
		for (optionInt in optionInts)
		{
			options.add(allStandardOpenOptions[optionInt.extractInt()])
		}
		return options
	}

	/**
	 * Construct the [set][EnumSet] of [file attributes][FileAttribute] that
	 * specify the [POSIX][PosixFilePermission] that correspond to the supplied
	 * [set][SetDescriptor] of integral option indicators.
	 *
	 * @param optionInts
	 *   Some integral option indicators.
	 * @return An array whose lone element is a set containing an attribute that
	 *   specifies the implied POSIX file permissions, or an empty array if the
	 *   [file system][FileSystem] does not support POSIX file permissions.
	 */
	private fun permissionsFor(optionInts: A_Set): Array<FileAttribute<*>> =
		if (IOSystem.fileSystem.supportedFileAttributeViews().contains("posix"))
		{
			val allPermissions = IOSystem.posixPermissions
			val permissions = EnumSet.noneOf(
				PosixFilePermission::class.java)
			for (optionInt in optionInts)
			{
				permissions.add(allPermissions[optionInt.extractInt() - 1])
			}
			arrayOf(PosixFilePermissions.asFileAttribute(permissions))
		}
		else { arrayOf() }
}