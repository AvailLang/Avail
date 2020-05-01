/*
 * P_FileGetAlignment.kt
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

import com.avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.FILE_KEY
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.InstanceTypeDescriptor.instanceType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.exceptions.AvailErrorCode.E_INVALID_HANDLE
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.io.IOSystem.FileHandle

/**
 * **Primitive:** Determine the transfer alignment of the underlying file.  This
 * might not agree with the buffer size used in higher level abstractions, but
 * the VM manages buffers for the file along this  alignment.
 *
 * @author Mark van Gulik&lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_FileGetAlignment : Primitive(1, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val atom = interpreter.argument(0)
		val pojo = atom.getAtomProperty(FILE_KEY.atom)
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(E_INVALID_HANDLE)
		}
		val handle = pojo.javaObjectNotNull<FileHandle>()
		return interpreter.primitiveSuccess(fromInt(handle.alignment))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(ATOM.o()), naturalNumbers())

	override fun privateFailureVariableType(): A_Type =
		instanceType(E_INVALID_HANDLE.numericCode())
}
