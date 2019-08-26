/*
 * AvailException.kt
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

import com.avail.descriptor.A_Number
import com.avail.descriptor.AvailObject
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * `AvailException` is the root of the hierarchy of [exceptions][Exception] that
 * are specific to the implementation of [AvailObject] and its numerous
 * primitive operations.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
open class AvailException : Exception
{
	/** The [error value][AvailObject].  */
	val errorCode: AvailErrorCode

	/**
	 * Answer the [error code][AvailErrorCode].
	 *
	 * @return
	 *   The error code.
	 */
	fun errorCode(): AvailErrorCode = errorCode

	/**
	 * Answer the numeric error code as an [Avail][AvailObject].
	 *
	 * @return
	 *   The [numeric error code][AvailObject].
	 */
	@ReferencedInGeneratedCode
	fun numericCode(): A_Number = errorCode.numericCode()

	/**
	 * Construct a new `AvailException` with the specified
	 * [error code][AvailErrorCode].
	 *
	 * @param errorCode
	 *   The [error code][AvailErrorCode].
	 */
	constructor(errorCode: AvailErrorCode)
	{
		this.errorCode = errorCode
	}

	/**
	 * Construct a new `AvailException` with the specified
	 * [error code][AvailErrorCode] and [cause][Throwable].
	 *
	 * @param errorCode
	 *   The [error code][AvailErrorCode].
	 * @param cause
	 *   The proximal [cause][Throwable] of the [        ].
	 */
	constructor(errorCode: AvailErrorCode, cause: Throwable) : super(cause)
	{
		this.errorCode = errorCode
	}
}
