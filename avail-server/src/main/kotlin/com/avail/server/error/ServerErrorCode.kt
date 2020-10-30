/*
 * ServerErrorCode.kt
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

package com.avail.server.error

import com.avail.error.ErrorCode
import com.avail.error.ErrorCodeRange
import com.avail.error.InvalidErrorCode
import com.avail.server.AvailServer
import com.avail.server.messages.TextCommand
import com.avail.server.messages.Message
import com.avail.server.messages.binary.editor.BinaryCommand
import com.avail.server.session.Session

/**
 * `ServerErrorCodeRange` is an [ErrorCodeRange] that specifies errors that can
 * happen while running an [AvailServer].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object ServerErrorCodeRange : ErrorCodeRange
{
	override val range: IntRange = IntRange(100000, 199999)
	override val name: String = "Server Error Codes"

	override fun errorCode(code: Int): ErrorCode
	{
		assert(code in range)
		return ServerErrorCode.code(code - minCode)
	}
}

/**
 * `ServerErrorCode` is an enumeration of [ErrorCode] that represent all
 * possible failures that can occur while running an [AvailServer].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
enum class ServerErrorCode(code: Int) : ErrorCode
{
	/** An unspecified error has occurred. */
	UNSPECIFIED(100000),

	/**
	 * Indicates the request made by the client does not correspond with any
	 * command ([TextCommand] nor [BinaryCommand]).
	 */
	INVALID_REQUEST(100001),

	/** Indicates a received [Message] is of an improper format. */
	MALFORMED_MESSAGE(100002),

	/**
	 * Could not locate a [Session].
	 */
	NO_SESSION(100003);

	override val errorCodeRange: ErrorCodeRange
		get() = ServerErrorCodeRange

	override val code: Int

	init
	{
		val expectedCode = ordinal + errorCodeRange.minCode
		require(code == expectedCode) {
			"ServerErrorCode $name's provided code did not match the ordinal " +
				"+ errorCodeRange.minCode: ($ordinal + " +
				"${errorCodeRange.minCode}). To ensure uniqueness the code " +
				"must be its ordinal position in the enum added to the range " +
				"minimum."
		}
		this.code = code
	}

	companion object
	{
		/**
		 * Answer the [ErrorCode] for the provided [ErrorCode.code].
		 *
		 * @param code
		 *   The integer value used to identify the `ServerErrorCode`.
		 * @return
		 *   The associated `ServerErrorCode` or [InvalidErrorCode] if the id is
		 *   not found.
		 */
		fun code (code: Int): ErrorCode =
			when(code)
			{
				UNSPECIFIED.code -> UNSPECIFIED
				INVALID_REQUEST.code -> INVALID_REQUEST
				MALFORMED_MESSAGE.code -> MALFORMED_MESSAGE
				else -> InvalidErrorCode(code, ServerErrorCodeRange)
			}
	}
}