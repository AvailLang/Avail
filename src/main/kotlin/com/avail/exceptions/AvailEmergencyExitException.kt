/*
 * AvailEmergencyExitException.kt
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

package com.avail.exceptions

import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.tuples.StringDescriptor.stringFrom
import java.lang.String.format

/**
 * An `AvailEmergencyExitException` is thrown when a primitive fails during
 * system bootstrapping.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class AvailEmergencyExitException : Exception
{
	/**
	 * The [error&#32;message][StringDescriptor] describing the emergency exit
	 * situation.
	 */
	val failureString: A_String

	/**
	 * Construct a new [AvailEmergencyExitException].
	 *
	 * @param failureString
	 *   The [error&#32;message][StringDescriptor] describing the emergency exit
	 *   situation.
	 */
	constructor(failureString: A_String)
	{
		assert(failureString.isString)
		this.failureString = failureString
	}

	/**
	 * Construct a new [AvailEmergencyExitException].
	 *
	 * @param failureString
	 *   The [error&#32;message][StringDescriptor] describing the emergency exit
	 *   situation.
	 */
	constructor(failureString: String)
	{
		this.failureString = stringFrom(failureString)
	}

	override val message: String
		get()
		{
			return format(
				"A bootstrap operation failed: %s%n",
				failureString.asNativeString())
		}
}
