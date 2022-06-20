/*
 * TextOutputChannel.kt
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

package avail.io

import java.io.PrintStream
import java.nio.CharBuffer
import java.nio.channels.AsynchronousChannel
import java.nio.channels.CompletionHandler

/**
 * `TextOutputChannel` provides a wrapper for a standard output
 * [stream][PrintStream], e.g., [System.out] and [System.err], that is *as
 * asynchronous as possible*.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
interface TextOutputChannel : AsynchronousChannel
{
	/**
	 * Writes a sequence of characters to this `TextOutputChannel` from the
	 * given [buffer][CharBuffer].
	 *
	 * This method initiates an asynchronous write operation to write a
	 * sequence of characters to this channel from the given buffer. The
	 * supplied [CompletionHandler] is invoked when the write operation
	 * completes (or fails). The result passed to the completion handler is the
	 * number of characters written.
	 *
	 * The write operation may write up to `r` characters to the channel, where
	 * `r` is the [number][CharBuffer.remaining] in the buffer at the time that
	 * the write is attempted. If `r` is `0`, then the write operation completes
	 * immediately with a result of `0` without initiating an I/O operation.
	 *
	 * Suppose that a character sequence of length `n` is written, where `0 < n
	 * ≤ r`. This character sequence will be transferred from the buffer
	 * starting at index `p`, where `p` is the buffer's
	 * [position][CharBuffer.position] at the moment the write is performed; the
	 * index of the last character written will be `p + n - 1`. Upon completion
	 * the buffer's position will be equal to `p + n`; its
	 * [limit][CharBuffer.limit] will not have changed.
	 *
	 * Buffers are not safe for use by multiple concurrent [ threads][Thread],
	 * so care should be taken to avoid accessing the buffer until the operation
	 * has completed.
	 *
	 * This method may be invoked at any time.
	 *
	 * @param A
	 *   The type of attachment accepted by the `CompletionHandler`.
	 * @param buffer
	 *   The buffer containing the characters to be transmitted.
	 * @param attachment
	 *   An arbitrary value that should be made available to the
	 *   `CompletionHandler`, irrespective of success.
	 * @param handler
	 *   What to do when the I/O operation
	 *   [succeeds][CompletionHandler.completed] or
	 *   [fails][CompletionHandler.failed].
	 */
	fun <A> write(
		buffer: CharBuffer,
		attachment: A?,
		handler: CompletionHandler<Int, A>)

	/**
	 * Writes a sequence of characters to this `TextOutputChannel` from the
	 * given [string][String].
	 *
	 * This method initiates an series of asynchronous write operations to write
	 * a sequence of characters to this channel from the given string. The
	 * supplied [CompletionHandler] is invoked when the entire string has been
	 * written (or when any intermediate write fails). The result passed to the
	 * completion handler is the [number&#32;of][String.length] written.
	 *
	 * @param A
	 *   The type of attachment accepted by the `CompletionHandler`.
	 * @param data
	 *   The string to be written.
	 * @param attachment
	 *   An arbitrary value that should be made available to the
	 *   `CompletionHandler`, irrespective of success.
	 * @param handler
	 *   What to do when the I/O operation
	 *   [succeeds][CompletionHandler.completed] or
	 *   [fails][CompletionHandler.failed].
	 */
	fun <A> write(
		data: String,
		attachment: A?,
		handler: CompletionHandler<Int, A>)
}
