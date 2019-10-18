/*
 * P_ComputeDigest.kt
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

package com.avail.interpreter.primitive.general

import com.avail.descriptor.A_Tuple
import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.ByteArrayTupleDescriptor.tupleForByteArray
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.bytes
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.TupleDescriptor.tupleFromIntegerList
import com.avail.descriptor.TupleTypeDescriptor.oneOrMoreOf
import com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Arrays.asList

/**
 * **Primitive:** Use a strong cryptographic message digest
 * algorithm to produce a hash of the specified [tuple][A_Tuple] of
 * bytes.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_ComputeDigest : Primitive(2, CannotFail, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val algorithm = interpreter.argument(0)
		val bytes = interpreter.argument(1)
		val digest: MessageDigest
		try
		{
			digest = MessageDigest.getInstance("SHA-$algorithm")
		}
		catch (e: NoSuchAlgorithmException)
		{
			assert(false) { "these are standard digest algorithm available in all " + "Java implementations" }
			throw RuntimeException(e)
		}

		val size = bytes.tupleSize()
		val buffer = ByteBuffer.allocateDirect(size)
		bytes.transferIntoByteBuffer(1, size, buffer)
		buffer.flip()
		digest.update(buffer)
		val digestBytes = digest.digest()
		val digestTuple = tupleForByteArray(digestBytes)
		return interpreter.primitiveSuccess(digestTuple)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				enumerationWith(
					tupleFromIntegerList(asList(1, 256, 384, 512)).asSet()),
				zeroOrMoreOf(bytes())),
			oneOrMoreOf(bytes()))
	}

}