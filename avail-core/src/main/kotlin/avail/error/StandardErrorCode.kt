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

package avail.error

/**
 * `StandardErrorCodeRange` is an [ErrorCodeRange] that specifies standard
 * errors that can occur during the regular function of Avail.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object StandardErrorCodeRange : ErrorCodeRange
{
	override val range: IntRange = IntRange(1000, 1999)
	override val name: String = "Standard Error Codes"

	override fun errorCode(code: Int): ErrorCode
	{
		assert(code in range)
		return StandardErrorCode.code(code - minCode)
	}
}

/**
 * `StandardErrorCode` is an enumeration of [ErrorCode] that represent standard
 * failures that can occur while running Avail.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
enum class StandardErrorCode(code: Int) : ErrorCode
{
	/** An unspecified error has occurred. */
	UNSPECIFIED(1000),

	/**
	 * An error has occurred when performing IO functions.
	 */
	IO_EXCEPTION(1001);

	override val errorCodeRange: ErrorCodeRange
		get() = StandardErrorCodeRange

	override val code: Int

	init
	{
		val expectedCode = ordinal + errorCodeRange.minCode
		require(code == expectedCode) {
			"StandardErrorCode $name's provided code did not match the ordinal " +
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
		 *   The integer value used to identify the [StandardErrorCode].
		 * @return
		 *   The associated `StandardErrorCode` or [InvalidErrorCode] if the id
		 *   is not found.
		 */
		fun code (code: Int): ErrorCode =
			when(code)
			{
				UNSPECIFIED.code -> UNSPECIFIED
				IO_EXCEPTION.code -> IO_EXCEPTION
				else -> InvalidErrorCode(code, StandardErrorCodeRange)
			}
	}
}
