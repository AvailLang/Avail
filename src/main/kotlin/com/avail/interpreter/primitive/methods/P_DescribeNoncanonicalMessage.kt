/*
 * P_DescribeNoncanonicalMessage.kt
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
package com.avail.interpreter.primitive.methods

import com.avail.compiler.splitter.MessageSplitter
import com.avail.descriptor.A_Type
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.StringDescriptor.stringFrom
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.bundles.MessageBundleDescriptor
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * **Primitive:** Answer a string describing why the given string is unsuitable
 * as a [message][MessageBundleDescriptor] name. If the given string is actually
 * suitable, answer the empty string.
 */
@Suppress("unused")
object P_DescribeNoncanonicalMessage
	: Primitive(1, CanInline, CanFold, CannotFail)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val messageName = interpreter.argument(0)
		try
		{
			val splitter = MessageSplitter(messageName)
		}
		catch (e: MalformedMessageException)
		{
			return interpreter.primitiveSuccess(
				stringFrom(e.describeProblem()))
		}

		return interpreter.primitiveSuccess(emptyTuple())
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(stringType()),stringType())
}