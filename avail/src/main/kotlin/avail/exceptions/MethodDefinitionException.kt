/*
 * MethodDefinitionException.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.exceptions

import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.AbstractDefinitionDescriptor
import avail.descriptor.methods.ForwardDefinitionDescriptor
import avail.descriptor.methods.MethodDefinitionDescriptor
import avail.descriptor.methods.MethodDescriptor
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.exceptions.AvailErrorCode.E_ABSTRACT_METHOD_DEFINITION
import avail.exceptions.AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION
import avail.exceptions.AvailErrorCode.E_FORWARD_METHOD_DEFINITION
import avail.exceptions.AvailErrorCode.E_NO_METHOD
import avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION

/**
 * A `MethodDefinitionException` is raised whenever an error condition is
 * discovered that pertains to failed resolution of a [method][MethodDescriptor]
 * or [method][MethodDefinitionDescriptor] or failed invocation of a method
 * definition.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `MethodDefinitionException`.
 *
 * @param code
 *    An [error&#32;code][AvailErrorCode].
 */
class MethodDefinitionException private constructor(code: AvailErrorCode)
	: AvailException(code)
{
	companion object
	{
		/**
		 * Answer a `MethodDefinitionException` that indicates the nonexistence
		 * of a [method][MethodDescriptor].
		 *
		 * @return
		 *   The requested exception.
		 */
		fun noMethod(): MethodDefinitionException =
			MethodDefinitionException(E_NO_METHOD)

		/**
		 * Answer a `MethodDefinitionException` that indicates the resolved
		 * [definition][MethodDefinitionDescriptor] is a
		 * [forward][ForwardDefinitionDescriptor].
		 *
		 * @return
		 *   The requested exception.
		 */
		fun forwardMethod(): MethodDefinitionException =
			MethodDefinitionException(E_FORWARD_METHOD_DEFINITION)

		/**
		 * Answer a `MethodDefinitionException` that indicates the resolved
		 * [definition][MethodDefinitionDescriptor] is
		 * [abstract][AbstractDefinitionDescriptor].
		 *
		 * @return
		 *   The requested exception.
		 */
		fun abstractMethod(): MethodDefinitionException =
			MethodDefinitionException(E_ABSTRACT_METHOD_DEFINITION)

		/**
		 * Answer the sole [A_Definition] from the given tuple of definitions,
		 * raising a [MethodDefinitionException] if the tuple doesn't have
		 * exactly one element.
		 *
		 * @param methodDefinitions
		 *   A tuple of [A_Definition]s.
		 * @return
		 *   The requested exception.
		 * @throws MethodDefinitionException
		 *   If the tuple did not contain exactly one definition.
		 */
		@Throws(MethodDefinitionException::class)
		fun extractUniqueMethod(methodDefinitions: A_Tuple): A_Definition
		{
			when (methodDefinitions.tupleSize)
			{
				1 -> return methodDefinitions.tupleAt(1)
				0 -> throw MethodDefinitionException(E_NO_METHOD_DEFINITION)
				else -> throw MethodDefinitionException(
					E_AMBIGUOUS_METHOD_DEFINITION)
			}
		}
	}
}
