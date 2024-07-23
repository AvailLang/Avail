/*
 * AvailUnsupportedOperationException.kt
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

import avail.descriptor.representation.AbstractDescriptor
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Descriptor

/**
 * An `AvailUnsupportedOperationException` is thrown whenever an
 * [Avail&#32;object][AvailObject]'s [descriptor][Descriptor] is asked to
 * perform an unsupported operation.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `AvailUnsupportedOperationException`.
 *
 * @param descriptorClass
 *   The [descriptor][AbstractDescriptor]'s [class][Class].
 * @param messageName
 *   The name of the unsupported operation.
 */
class AvailUnsupportedOperationException constructor(
	descriptorClass: Class<*>,
	messageName: String
) : RuntimeException(
	"${descriptorClass.simpleName} does not meaningfully implement " +
		messageName)


/**
 * Throw an [AvailUnsupportedOperationException] suitable to be thrown by
 * the sender.
 *
 * The exception indicates that the receiver does not meaningfully implement
 * the method that immediately invoked this.  This is a strong indication
 * that the wrong kind of object is being used somewhere.
 *
 * @throws AvailUnsupportedOperationException
 */
private fun unsupportedOperation (problemClass: Class<*>): Nothing
{
	val callerName =
		try
		{
			throw Exception("just want the caller's frame")
		}
		catch (e: Exception)
		{
			var name = e.stackTrace[1].methodName
			if (name == "getUnsupported")  // property name
			{
				name = e.stackTrace[2].methodName
			}
			name
		}
	throw AvailUnsupportedOperationException(problemClass, callerName)
}

/**
 * This read-only property can be used in place of [unsupportedOperation].
 * Using the getter produces almost the same diagnostic stack trace when
 * executed, but is a much shorter expression.
 */
val Any.unsupported: Nothing
	get() = unsupportedOperation(this::class.java)
