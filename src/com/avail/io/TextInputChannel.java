/**
 * TextInputChannel.java
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

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousChannel;
import java.nio.channels.CompletionHandler;

/**
 * {@code TextInputChannel} provides a wrapper for a synchronous input reader
 * that is <em>as asynchronous as possible</em>.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public interface TextInputChannel
extends AsynchronousChannel
{
	/**
	 * Reads a sequence of characters from this {@linkplain TextInputChannel
	 * reader} into the given {@linkplain CharBuffer buffer}.
	 *
	 * <p>This method initiates an asynchronous read operation to read a
	 * sequence of channels from this channel into the given buffer. The
	 * supplied {@link CompletionHandler} is invoked when the read operation
	 * completes (or fails). The result passed to the completion handler is the
	 * number of characters read or {@code -1} if no characters could be read
	 * because the channel has reached end-of-stream.</p>
	 *
	 * <p>The read operation may read up to {@code r} bytes from the channel,
	 * where r is the {@linkplain CharBuffer#remaining() number of characters
	 * remaining} in the buffer at the time that the read is attempted.
	 * If {@code r} is {@code 0}, then the read operation completes immediately
	 * with a result of {@code 0} without initiating an I/O operation.</p>
	 *
	 * <p>Suppose that a character sequence of length {@code n} is read, where
	 * {@code 0 < n ≤ r}. This character sequence will be transferred into the
	 * buffer so that the first byte in the sequence is at index {@code p} and
	 * the last byte is at index {@code p + n - 1}, where {@code p} is the
	 * buffer's {@linkplain CharBuffer#position() position} at the moment that
	 * the read is performed. Upon completion, the buffer's position will be
	 * equal to {@code p + n}; its {@linkplain CharBuffer#limit() limit} will
	 * not have changed.</p>
	 *
	 * <p>Buffers are not safe for use by multiple concurrent {@linkplain Thread
	 * threads}, so care should be taken to avoid accessing the buffer until the
	 * asynchronous read operation has completed.</p>
	 *
	 * <p>This method may be invoked at any time.</p>
	 *
	 * @param <A>
	 *        The type of attachment accepted by the {@code CompletionHandler}.
	 * @param buffer
	 *        The buffer into which characters should be read.
	 * @param attachment
	 *        An arbitrary value that should be made available to the {@code
	 *        CompletionHandler}, irrespective of success.
	 * @param handler
	 *        What to do when the I/O operation {@linkplain
	 *        CompletionHandler#completed(Object, Object) succeeds} or
	 *        {@linkplain CompletionHandler#failed(Throwable, Object) fails}.
	 */
	<A> void read (
		CharBuffer buffer,
		@Nullable A attachment,
		CompletionHandler<Integer, A> handler);

	/**
	 * Mark the current position in the {@linkplain TextInputChannel channel}.
	 * Subsequent calls to {@link #reset()} will attempt to reset this position.
	 *
	 * @param readAhead
	 *        The number of characters to preserve without invalidating the
	 *        mark. If more than this limit are read, then a subsequent call to
	 *        {@code reset()} will fail.
	 * @throws IOException
	 *         If the mark could not be set for any reason.
	 */
	void mark (final int readAhead) throws IOException;

	/**
	 * Attempt to reposition the {@linkplain TextInputChannel channel} to the
	 * most recently saved {@linkplain #mark(int) mark}.
	 *
	 * @throws IOException
	 *         If the channel has not been marked, or if the mark has been
	 *         invalidated.
	 */
	void reset () throws IOException;
}
