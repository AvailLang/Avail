/*
 * P_DeclareStringificationAtom.java
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

package com.avail.interpreter.primitive.hooks

import com.avail.AvailRuntime.HookType.STRINGIFICATION
import com.avail.descriptor.A_Type
import com.avail.descriptor.FunctionDescriptor.createFunction
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TypeDescriptor.Types.*
import com.avail.exceptions.AvailRuntimeException
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import com.avail.interpreter.levelOne.L1InstructionWriter
import com.avail.interpreter.levelOne.L1Operation

/**
 * **Primitive:** Inform the VM of the [ ] of the preferred stringification [ ].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_DeclareStringificationAtom : Primitive(1, CannotFail, HasSideEffect, Private)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val atom = interpreter.argument(0)
		// Generate a function that will invoke the stringifier method for
		// the specified value.
		val writer = L1InstructionWriter(nil, 0, nil)
		writer.argumentTypes(ANY.o())
		writer.returnType = stringType()
		writer.write(0, L1Operation.L1_doPushLocal, 1)
		try
		{
			writer.write(
				0,
				L1Operation.L1_doCall,
				writer.addLiteral(atom.bundleOrCreate()),
				writer.addLiteral(stringType()))
		}
		catch (e: MalformedMessageException)
		{
			assert(false) { "This should never happen!" }
			throw AvailRuntimeException(e.errorCode)
		}

		val function = createFunction(writer.compiledCode(), emptyTuple())
		function.makeShared()
		// Set the stringification function.
		STRINGIFICATION.set(interpreter.runtime(), function)
		val loader = interpreter.availLoaderOrNull()
		loader?.statementCanBeSummarized(false)
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(tuple(ATOM.o()), TOP.o())
	}

}