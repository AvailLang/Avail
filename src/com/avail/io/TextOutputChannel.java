/**
 * TextOutputChannel.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.io;

import java.io.PrintStream;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousChannel;
import java.nio.channels.CompletionHandler;
import javax.annotation.Nullable;

/**
 * {@code TextOutputChannel} provides a wrapper for a standard output
 * {@linkplain PrintStream stream}, e.g., {@linkplain System#out System.out} and
 * {@linkplain System#err System.err}, that is <em>as asynchronous as
 * possible</em>.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public interface TextOutputChannel
extends AsynchronousChannel
{
	/**
	 * Writes a sequence of characters to this {@linkplain TextOutputChannel
	 * channel} from the given {@linkplain CharBuffer buffer}.
	 *
	 * <p>This method initiates an asynchronous write operation to write a
	 * sequence of characters to this channel from the given buffer. The
	 * supplied {@link CompletionHandler} is invoked when the write operation
	 * completes (or fails). The result passed to the completion handler is the
	 * number of characters written.</p>
	 *
	 * <p>The write operation may write up to {@code r} characters to the
	 * channel, where {@code r} is the {@linkplain CharBuffer#remaining() number
	 * of characters remaining} in the buffer at the time that the write is
	 * attempted. If {@code r} is {@code 0}, then the write operation completes
	 * immediately with a result of {@code 0} without initiating an I/O
	 * operation.</p>
	 *
	 * <p>Suppose that a character sequence of length {@code n} is written,
	 * where {@code 0 < n ≤ r}. This character sequence will be transferred from
	 * the buffer starting at index {@code p}, where {@code p} is the buffer's
	 * {@linkplain CharBuffer#position() position} at the moment the write is
	 * performed; the index of the last character written will be
	 * {@code p + n - 1}. Upon completion the buffer's position will be equal to
	 * {@code p + n}; its {@linkplain CharBuffer#limit() limit} will not have
	 * changed.</p>
	 *
	 * <p>Buffers are not safe for use by multiple concurrent {@linkplain Thread
	 * threads}, so care should be taken to avoid accessing the buffer until the
	 * operation has completed.</p>
	 *
	 * <p>This method may be invoked at any time.</p>

	 * @param <A>
	 *        The type of attachment accepted by the {@code CompletionHandler}.
	 * @param buffer
	 *        The buffer containing the characters to be transmitted.
	 * @param attachment
	 *        An arbitrary value that should be made available to the {@code
	 *        CompletionHandler}, irrespective of success.
	 * @param handler
	 *        What to do when the I/O operation {@linkplain
	 *        CompletionHandler#completed(Object, Object) succeeds} or
	 *        {@linkplain CompletionHandler#failed(Throwable, Object) fails}.
	 */
	<A> void write (
		CharBuffer buffer,
		@Nullable A attachment,
		CompletionHandler<Integer, A> handler);

	/**
	 * Writes a sequence of characters to this {@linkplain TextOutputChannel
	 * channel} from the given {@linkplain String string}.
	 *
	 * <p>This method initiates an series of asynchronous write operations to
	 * write a sequence of characters to this channel from the given string. The
	 * supplied {@link CompletionHandler} is invoked when the entire string has
	 * been written (or when any intermediate write fails). The result passed to
	 * the completion handler is the {@linkplain String#length() number of
	 * characters} written.</p>
	 *
	 * @param <A>
	 *        The type of attachment accepted by the {@code CompletionHandler}.
	 * @param data
	 *        The string to be written.
	 * @param attachment
	 *        An arbitrary value that should be made available to the {@code
	 *        CompletionHandler}, irrespective of success.
	 * @param handler
	 *        What to do when the I/O operation {@linkplain
	 *        CompletionHandler#completed(Object, Object) succeeds} or
	 *        {@linkplain CompletionHandler#failed(Throwable, Object) fails}.
	 */
	<A> void write (
		String data,
		@Nullable A attachment,
		CompletionHandler<Integer, A> handler);
}
