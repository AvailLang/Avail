/*
 * ServerErrorCode.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.server.error

import com.avail.builder.ModuleRoot
import com.avail.builder.ModuleRoots
import com.avail.server.AvailServer
import com.avail.server.messages.TextCommand
import com.avail.server.messages.Message
import com.avail.server.messages.binary.editor.BinaryCommand
import com.avail.server.session.Session

/**
 * `ServerErrorCode` is an enumeration of all possible failures that can occur
 * while running an [AvailServer].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
enum class ServerErrorCode constructor(val code: Int)
{
	/** An unspecified error has occurred. */
	UNSPECIFIED(0),

	/**
	 * Indicates a file that was attempted to be created already exists.
	 */
	FILE_ALREADY_EXISTS (1),

	/** Could not locate a file at specified location. */
	FILE_NOT_FOUND(2),

	/**
	 * The cache id provided to refer to a file did not refer to any file.
	 */
	BAD_FILE_ID(3),

	/** A general IO exception. */
	IO_EXCEPTION(4),

	/**
	 * Indicates the request made by the client does not correspond with any
	 * command ([TextCommand] nor [BinaryCommand]).
	 */
	INVALID_REQUEST(5),

	/** Indicates a received [Message] is of an improper format. */
	MALFORMED_MESSAGE(6),

	/** Could not [find][ModuleRoots.moduleRootFor] [ModuleRoot]. */
	BAD_MODULE_ROOT(7),

	/**
	 * Located [ModuleRoot] has no
	 * [source directory][ModuleRoot.sourceDirectory].
	 */
	NO_SOURCE_DIRECTORY(8),

	/**
	 * Could not locate a [Session].
	 */
	NO_SESSION(9);

	companion object
	{
		/**
		 * Answer the [ServerErrorCode] for the provided [ServerErrorCode.code].
		 *
		 * @param code
		 *   The integer value used to identify the `ServerErrorCode`.
		 * @return
		 *   The associated `ServerErrorCode` or [ServerErrorCode.UNSPECIFIED]
		 *   if the id is not found.
		 */
		fun code (code: Int): ServerErrorCode =
			when(code)
			{
				0 -> UNSPECIFIED
				1 -> FILE_ALREADY_EXISTS
				2 -> FILE_NOT_FOUND
				3 -> BAD_FILE_ID
				4 -> IO_EXCEPTION
				5 -> INVALID_REQUEST
				6 -> MALFORMED_MESSAGE
				7 -> BAD_MODULE_ROOT
				8 -> NO_SOURCE_DIRECTORY
				else -> UNSPECIFIED
			}
	}
}