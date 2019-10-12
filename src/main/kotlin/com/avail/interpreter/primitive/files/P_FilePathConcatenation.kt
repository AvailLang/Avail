/*
 * P_FilePathConcatenation.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.StringDescriptor
import com.avail.descriptor.StringDescriptor.stringFrom
import com.avail.descriptor.TupleDescriptor
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.exceptions.AvailErrorCode.E_INVALID_PATH
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.io.IOSystem
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * **Primitive:** Concatenate the [ elements][StringDescriptor] of the specified [tuple][TupleDescriptor] to form a
 * platform-specific [path][Path].
 *
 * @author authorName &lt;emailAddress@availlang.org&gt;
 */
object P_FilePathConcatenation : Primitive(1, CanInline, CanFold)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(1)
		val tuple = interpreter.argument(0)
		val fileSystem = IOSystem.fileSystem
		var path: Path? = null
		try
		{
			for (component in tuple)
			{
				val componentString = component.asNativeString()
				if (path === null)
				{
					path = fileSystem.getPath(componentString)
				}
				else
				{
					path = path.resolve(componentString)
				}
			}
		}
		catch (e: InvalidPathException)
		{
			return interpreter.primitiveFailure(E_INVALID_PATH)
		}

		return if (path === null)
		{
			interpreter.primitiveSuccess(emptyTuple())
		}
		else interpreter.primitiveSuccess(stringFrom(path.toString()))
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(zeroOrMoreOf(stringType())),
			stringType())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(set(E_INVALID_PATH))
	}

}