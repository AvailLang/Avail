/*
 * MalformedMessageException.kt
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

import com.avail.compiler.splitter.MessageSplitter

import java.util.function.Supplier

/**
 * A `MalformedMessageException` is thrown when a method name is malformed
 * and therefore cannot be converted to parsing instructions.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @see SignatureException
 *
 * @property descriptionSupplier
 *   A [Supplier] that can produce a description of what the problem is with the
 *   message name.
 *
 * @constructor
 * Construct a new `MalformedMessageException` with the specified
 * [error&#32;code][AvailErrorCode] and the specified [Supplier] that describes the
 * problem.
 *
 * @param errorCode
 *   The [error code][AvailErrorCode].
 * @param descriptionSupplier
 *   A [Supplier] that produces a [String] describing what was malformed about
 *   the signature that failed to be parsed by a [MessageSplitter].
 */
class MalformedMessageException constructor(
		errorCode: AvailErrorCode,
		private val descriptionSupplier: () -> String)
	: AvailException(errorCode)
{
	/**
	 * Answer a description of how the signature is malformed.
	 *
	 * @return
	 *   A description of what is wrong with the signature being analyzed.
	 */
	fun describeProblem(): String = descriptionSupplier.invoke()
}
