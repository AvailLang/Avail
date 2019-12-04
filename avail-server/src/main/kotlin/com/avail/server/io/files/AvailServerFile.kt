/*
 * AvailServerFile.kt
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
package com.avail.server.io.files

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler

/**
 * `AvailSeverFile` is an interface for declaring behavior and state for a file
 * that has been opened by the Avail Server.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property path
 *   The on-disk absolute location of the file.
 * @property file
 *   The [AsynchronousFileChannel] used to access the file.
 * @property mimeType
 *   The MIME type of the file.
 * @property serverFileWrapper
 *   The [ServerFileWrapper] that wraps this [AvailServerFile].
 *
 * @constructor
 * Construct an [AvailServerFile].
 *
 * @param path
 *   The on-disk absolute location of the file.
 * @param file
 *   The [AsynchronousFileChannel] used to access the file.
 * @param mimeType
 *   The MIME type of the file.
 * @param serverFileWrapper
 *   The [ServerFileWrapper] that wraps this [AvailServerFile].
 */
internal abstract class AvailServerFile constructor(
	protected val path: String,
	protected val file: AsynchronousFileChannel,
	protected val mimeType: String,
	protected val serverFileWrapper: ServerFileWrapper)
{
	/**
     * The raw bytes of the file.
	 */
	abstract val rawContent: ByteArray

	/** Close the backing channel [file]. */
	fun close () = file.close()

	/**
	 * Save the [rawContent] to disk.
	 *
	 * @param handler
	 *   The [CompletionHandler] to use when save either completes or fails.
	 */
	fun save (handler: CompletionHandler<Int, Any?>) =
		file.write(ByteBuffer.wrap(rawContent), 0, null, handler)

	/**
	 * Accepts a function that accepts the [rawContent] of this
	 * [AvailServerFile].
	 */
	fun provideContent(consumer: (ByteArray) -> Unit) =
		consumer(rawContent)
}
