/*
 * FileErrorCodeRange.kt
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

package com.avail.files

import com.avail.builder.ModuleRoot
import com.avail.error.ErrorCode
import com.avail.error.ErrorCodeRange
import com.avail.error.InvalidErrorCode
import com.avail.resolver.ModuleRootResolver

/**
 * A {@code FileErrorCodeRange} is TODO: Document this!
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

enum class FileErrorCode: ErrorCode
{
	/** An unspecified error has occurred. */
	UNSPECIFIED,

	/**
	 * Indicates a file that was attempted to be created already exists.
	 */
	FILE_ALREADY_EXISTS,

	/** Could not locate a file at specified location. */
	FILE_NOT_FOUND,

	/**
	 * Located [ModuleRoot] has no [source location][ModuleRoot.resolver].
	 */
	NO_SOURCE_LOCATION,

	/**
	 * The cache id provided to refer to a file did not refer to any file.
	 */
	BAD_FILE_ID,

	/** A general IO exception. */
	IO_EXCEPTION,

	/**
	 * The permissions associated with the file does not allow the attempted
	 * action.
	 */
	PERMISSIONS,

	/**
	 * An attempt to access a [FileManager]-cached file that has been closed
	 * and removed from the cache has occurred.
	 */
	FILE_CLOSED,

	/**
	 * The [ModuleRootResolver] could not be successfully [ModuleRoot]
	 */
	MODULE_ROOT_RESOLUTION_FAILED;

	override val code: Int = ordinal + errorCodeRange.minCode
	override val errorCodeRange: ErrorCodeRange
		get() = FileErrorCodeRange

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
				0 -> UNSPECIFIED
				1 -> FILE_ALREADY_EXISTS
				2 -> FILE_NOT_FOUND
				3 -> NO_SOURCE_LOCATION
				4 -> BAD_FILE_ID
				5 -> IO_EXCEPTION
				else -> InvalidErrorCode(code, FileErrorCodeRange)
			}
	}
}