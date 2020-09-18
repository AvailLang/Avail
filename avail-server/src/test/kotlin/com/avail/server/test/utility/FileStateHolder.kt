/*
 * FileStateHolder.kt
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

package com.avail.server.test.utility

import com.avail.server.error.ServerErrorCode
import com.avail.server.io.files.FileManager
import com.avail.utility.Mutable
import java.util.UUID

/**
 * `FileStateHolder` is holds on to state from [FileManager] callbacks.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class FileStateHolder
{
	/** Mutable holder for any errors that have been thrown. */
	private val errorWrapper : Mutable<Throwable?> = Mutable(null)

	/** The currently wrapped [Throwable]. */
	val error get() =  errorWrapper.value

	/** Mutable holder for any [ServerErrorCode]s that have been reported. */
	private val errorCodeWrapper: Mutable<ServerErrorCode?> = Mutable(null)

	/** The currently wrapped [ServerErrorCode]. */
	val errorCode get() = errorCodeWrapper.value

	/** Mutable holder for File id from [FileManager]. */
	private val fileIdWrapper : Mutable<UUID?> = Mutable(null)

	/** The currently wrapped [FileManager] file id. */
	val fileId get() = fileIdWrapper.value

	/** Mutable holder for file mime. */
	private val fileMimeWrapper : Mutable<String?> = Mutable(null)

	/** The currently wrapped file mime. */
	val fileMime get() = fileMimeWrapper.value

	/** Mutable holder for String file contents. */
	private val fileContentsWrapper: Mutable<String?> = Mutable(null)

	/** The currently wrapped file contents.. */
	val fileContents get() = fileContentsWrapper.value

	/** Reset all [Mutable.value]s to `null`. */
	fun reset ()
	{
		errorWrapper.value = null
		errorCodeWrapper.value = null
		fileIdWrapper.value = null
		fileMimeWrapper.value = null
		fileContentsWrapper.value = null
	}

	/**
	 * Reset [fileId], [fileMime], [fileContents] [Mutable.value]s `null'
	 */
	fun resetFile ()
	{
		fileIdWrapper.value = null
		fileMimeWrapper.value = null
		fileContentsWrapper.value = null
	}

	/** Reset [errorWrapper] and [errorCodeWrapper] [Mutable.value]s `null' */
	fun resetError ()
	{
		errorWrapper.value = null
		errorCodeWrapper.value = null
	}

	/**
	 * Update [fileId], [fileMime], [fileContents] [Mutable.value]s.
	 *
	 * @param id
	 *   The contents of [fileId].
	 * @param mime
	 *   The contents of [fileMime].
	 * @param raw
	 *   The raw bytes to transform into a String for [fileContents].
	 */
	fun updateFile (id: UUID, mime: String, raw: ByteArray)
	{
		fileIdWrapper.value = id
		fileMimeWrapper.value = mime
		fileContentsWrapper.value = String(raw, Charsets.UTF_16BE)
	}

	/**
	 * Update [errorWrapper] and [errorCodeWrapper] [Mutable.value]s.
	 *
	 * @param code
	 *   The contents of [errorCodeWrapper].
	 * @param e
	 *   The contents of [errorWrapper].
	 */
	fun updateError (code: ServerErrorCode, e: Throwable?)
	{
		errorWrapper.value = e
		errorCodeWrapper.value = code
	}

	/**
	 * Conditionally throw the wrapped [errorWrapper] if there is one; do nothing
	 * otherwise.
	 */
	fun conditionallyThrowError ()
	{
		val e = error
		if (e != null)
		{
			throw e
		}
	}
}