/*
 * FileErrorCodeRange.kt
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

package avail.files

import avail.error.ErrorCode
import avail.error.ErrorCodeRange
import avail.error.InvalidErrorCode

/**
 * A `FileErrorCodeRange` is an [ErrorCodeRange] that holds defined error codes
 * that involve failures while handling files.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object FileErrorCodeRange : ErrorCodeRange
{
	override val name: String = "File Error Code Range"

	override val range: IntRange = IntRange(200000, 299999)

	override fun errorCode(code: Int): ErrorCode
	{
		assert(code in range)
		return FileErrorCode.code(code - minCode)
	}
}

/**
 * `FileErrorCode` is an enumeration of [ErrorCode] that list errors that can
 * happen while dealing with files.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
enum class FileErrorCode(code: Int): ErrorCode
{
	/** An unspecified error has occurred. */
	UNSPECIFIED(200000),

	/**
	 * Indicates a file that was attempted to be created already exists.
	 */
	FILE_ALREADY_EXISTS(200001),

	/** Could not locate a file at specified location. */
	FILE_NOT_FOUND(200002),

	/**
	 * The cache id provided to refer to a file did not refer to any file.
	 */
	BAD_FILE_ID(200003),

	/**
	 * The permissions associated with the file does not allow the attempted
	 * action.
	 */
	PERMISSIONS(200004),

	/**
	 * An attempt to access a [FileManager]-cached file that has been closed
	 * and removed from the cache has occurred.
	 */
	FILE_CLOSED(200005),

	/**
	 * Encoding the file for storage has failed.
	 */
	ENCODER_FAILURE(200006),

	/**
	 * Decoding a file has failed.
	 */
	DECODER_FAILURE(200007);

	override val errorCodeRange: ErrorCodeRange
		get() = FileErrorCodeRange

	override val code: Int

	init
	{
		val expectedCode = ordinal + errorCodeRange.minCode
		require(code == expectedCode) {
			"FileErrorCode $name's provided code did not match the ordinal " +
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
		 *   The integer value used to identify the [FileErrorCode].
		 * @return
		 *   The associated `FileErrorCode` or [InvalidErrorCode] if the id is
		 *   not found.
		 */
		fun code (code: Int): ErrorCode =
			when(code)
			{
				UNSPECIFIED.code -> UNSPECIFIED
				FILE_ALREADY_EXISTS.code -> FILE_ALREADY_EXISTS
				FILE_NOT_FOUND.code -> FILE_NOT_FOUND
				BAD_FILE_ID.code -> BAD_FILE_ID
				PERMISSIONS.code -> PERMISSIONS
				FILE_CLOSED.code -> FILE_CLOSED
				else -> InvalidErrorCode(code, FileErrorCodeRange)
			}
	}
}
