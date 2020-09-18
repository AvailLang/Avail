/*
 * TextInputChannel.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.io

import java.io.IOException
import java.nio.CharBuffer
import java.nio.channels.AsynchronousChannel
import java.nio.channels.CompletionHandler

/**
 * `TextInputChannel` provides a wrapper for a synchronous input reader that is
 * *as asynchronous as possible*.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
interface TextInputChannel : AsynchronousChannel
{
	/**
	 * Reads a sequence of characters from this [reader][TextInputChannel] into
	 * the given [buffer][CharBuffer].
	 *
	 * This method initiates an asynchronous read operation to read a sequence
	 * of channels from this channel into the given buffer. The supplied
	 * [CompletionHandler] is invoked when the read operation completes (or
	 * fails). The result passed to the completion handler is the number of
	 * characters read or `-1` if no characters could be read because the
	 * channel has reached end-of-stream.
	 *
	 * The read operation may read up to `r` bytes from the channel, where r is
	 * the [number&#32;of&#32;characters][CharBuffer.remaining] in the buffer at
	 * the time that the read is attempted. If `r` is `0`, then the read
	 * operation completes immediately with a result of `0` without initiating
	 * an I/O operation.
	 *
	 * Suppose that a character sequence of length `n` is read, where `0 < n ≤
	 * r`. This character sequence will be transferred into the buffer so that
	 * the first byte in the sequence is at index `p` and the last byte is at
	 * index `p + n - 1`, where `p` is the buffer's
	 * [position][CharBuffer.position] at the moment that the read is performed.
	 * Upon completion, the buffer's position will be equal to `p + n`; its
	 * [limit][CharBuffer.limit] will not have changed.
	 *
	 * Buffers are not safe for use by multiple concurrent [ threads][Thread],
	 * so care should be taken to avoid accessing the buffer until the
	 * asynchronous read operation has completed.
	 *
	 * This method may be invoked at any time.
	 *
	 * @param A
	 *   The type of attachment accepted by the `CompletionHandler`.
	 * @param buffer
	 *   The buffer into which characters should be read.
	 * @param attachment
	 *   An arbitrary value that should be made available to the
	 *   `CompletionHandler`, irrespective of success.
	 * @param handler
	 *   What to do when the I/O operation
	 *   [succeeds][CompletionHandler.completed] or
	 *   [fails][CompletionHandler.failed].
	 */
	fun <A> read(
		buffer: CharBuffer,
		attachment: A?,
		handler: CompletionHandler<Int, A>)

	/**
	 * Mark the current position in the [channel][TextInputChannel]. Subsequent
	 * calls to [reset] will attempt to reset this position.
	 *
	 * @param readAhead
	 *   The number of characters to preserve without invalidating the mark. If
	 *   more than this limit are read, then a subsequent call to `reset()` will
	 *   fail.
	 * @throws IOException
	 *   If the mark could not be set for any reason.
	 */
	@Throws(IOException::class)
	fun mark(readAhead: Int)

	/**
	 * Attempt to reposition the [channel][TextInputChannel] to the most
	 * recently saved [mark][mark].
	 *
	 * @throws IOException
	 *   If the channel has not been marked, or if the mark has been
	 *   invalidated.
	 */
	@Throws(IOException::class)
	fun reset()
}
