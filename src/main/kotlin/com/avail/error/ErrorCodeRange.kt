/*
 * ErrorCodeRange.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.error

/**
 * An `ErrorCodeRange` represents a range of related [ErrorCode]s.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
interface ErrorCodeRange
{
	/**
	 * The minimum allowed value for an [ErrorCode] in this [ErrorCodeRange].
	 */
	val minCode: Int get() = range.first

	/**
	 * The maximum allowed value for an [ErrorCode] in this [ErrorCodeRange].
	 */
	val maxCode: Int get() = range.last

	/**
	 * The name of this [ErrorCodeRange].
	 */
	val name: String

	/**
	 * The [IntRange] that represents the valid range of [ErrorCode]s.
	 */
	val range: IntRange

	/**
	 * Answer the [ErrorCode] for the given [ErrorCode.code].
	 *
	 * @param code
	 *   The [ErrorCode.code] to lookup.
	 * @return The associated `ErrorCode` or [InvalidErrorCode] if the provided
	 *   code is not valid.
	 */
	fun errorCode (code: Int): ErrorCode
}