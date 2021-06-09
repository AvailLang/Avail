/*
 * AvailRuntimeException.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import com.avail.descriptor.representation.AvailObject

/**
 * `AvailRuntimeException` is the root of the hierarchy of
 * [runtime&#32;exception][RuntimeException] that are specific to the
 * implementation of [AvailObject] and its numerous primitive operations.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
open class AvailRuntimeException : RuntimeException
{
	/** The [error code][AvailErrorCode].  */
	val errorCode: AvailErrorCode

	/**
	 * Answer the numeric error code as an [Avail][AvailObject].
	 *
	 * @return
	 *   The [numeric&#32;error&#32;code][AvailObject].
	 */
	val numericCode get () = errorCode.numericCode()

	/**
	 * Construct a new `AvailRuntimeException` with the specified
	 * [error&#32;code][AvailErrorCode].
	 *
	 * @param errorCode
	 *   The [error&#32;code][AvailErrorCode].
	 */
	constructor(errorCode: AvailErrorCode)
	{
		this.errorCode = errorCode
	}

	/**
	 * Construct a new `AvailRuntimeException` with the specified
	 * [error&#32;code][AvailErrorCode] and [cause][Throwable].
	 *
	 * @param errorCode
	 *   The [error&#32;code][AvailErrorCode].
	 * @param cause
	 *   The proximal [cause][Throwable] of the [exception][AvailException].
	 */
	constructor(errorCode: AvailErrorCode, cause: Throwable) : super(cause)
	{
		this.errorCode = errorCode
	}

	override val message: String
		get ()
		{
			var message: String? = super.message
			if (message === null)
			{
				message = errorCode.toString()
			}
			return message
		}
}
