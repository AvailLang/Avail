/**
 * ServerInputChannel.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.server.io;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import com.avail.io.TextInputChannel;
import com.avail.server.messages.Message;
import com.avail.utility.evaluation.Continuation0;

/**
 * A {@code ServerInputChannel} adapts an {@link AvailServerChannel} for use as
 * a standard input channel.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class ServerInputChannel
implements TextInputChannel
{
	/** The underlying {@linkplain AvailServerChannel server channel}. */
	private final AvailServerChannel channel;

	/**
	 * Construct a new {@link ServerInputChannel}.
	 *
	 * @param channel
	 *        The {@linkplain ServerInputChannel server channel} to adapt as a
	 *        standard input channel.
	 */
	public ServerInputChannel (final AvailServerChannel channel)
	{
		this.channel = channel;
	}

	@Override
	public boolean isOpen ()
	{
		return channel.isOpen();
	}

	/**
	 * The {@linkplain Deque queue} of {@linkplain Message messages} awaiting
	 * delivery. It is invariant that there are either pending messages or
	 * pending {@linkplain #waiters}. Whenever there are no messages, the
	 * {@linkplain #position} must be {@code 0}.
	 */
	private final Deque<Message> messages = new ArrayDeque<>();

	/**
	 * The position of the next unread character within the {@linkplain
	 * Deque#peekFirst() head} {@linkplain Message message} of the {@linkplain
	 * #messages queue}.
	 */
	private int position = 0;

	/**
	 * A {@code Waiter} represents a client that is awaiting I/O completion.
	 */
	private static final class Waiter
	{
		/**
		 * The {@linkplain CharBuffer buffer} provided for receipt of inbound
		 * data.
		 */
		final CharBuffer buffer;

		/** The attachment, if any. */
		final @Nullable Object attachment;

		/**
		 * The {@linkplain CompletionHandler completion handler} provided for
		 * notification of data availability.
		 */
		final CompletionHandler<Integer, Object> handler;

		/**
		 * Construct a new {@link Waiter}.
		 *
		 * @param buffer
		 *        The {@linkplain CharBuffer buffer} provided for receipt of
		 *        inbound data.
		 * @param attachment
		 *        The attachment, of {@code null} if none.
		 * @param handler
		 *        The {@linkplain CompletionHandler completion handler} provided
		 *        for notification of data availability.
		 */
		@SuppressWarnings("unchecked")
		Waiter (
			final CharBuffer buffer,
			final @Nullable Object attachment,
			final CompletionHandler<Integer, ?> handler)
		{
			this.buffer = buffer;
			this.attachment = attachment;
			this.handler = (CompletionHandler<Integer, Object>) handler;
		}

		/** The number of bytes read. */
		int bytesRead = 0;

		/**
		 * Invoke the {@linkplain CompletionHandler handler}'s {@linkplain
		 * CompletionHandler#completed(Object, Object) success} entry point.
		 */
		void completed ()
		{
			handler.completed(bytesRead, attachment);
		}
	}

	/**
	 * The {@linkplain Deque queue} of {@linkplain Waiter waiters}. It is
	 * invariant that there are either pending waiters or pending {@linkplain
	 * #messages}.
	 */
	private final Deque<Waiter> waiters = new ArrayDeque<>();

	/**
	 * The {@linkplain CharBuffer buffer} of data read since the last call to
	 * {@link #mark(int) mark}.
	 */
	private @Nullable CharBuffer markBuffer;

	@Override
	public synchronized void mark (final int readAhead) throws IOException
	{
		markBuffer = CharBuffer.allocate(readAhead);
	}

	@Override
	public void reset () throws IOException
	{
		final List<Waiter> ready = new ArrayList<>();
		synchronized (this)
		{
			final CharBuffer buffer = markBuffer;
			if (buffer == null)
			{
				throw new IOException();
			}
			// Discard the mark.
			markBuffer = null;
			if (waiters.isEmpty())
			{
				final Message message = new Message(buffer.toString());
				messages.addFirst(message);
				position = 0;
				return;
			}
			assert messages.isEmpty();
			assert position == 0;
			final String content = buffer.toString();
			final int contentLength = content.length();
			while (position != contentLength && !waiters.isEmpty())
			{
				final Waiter waiter = waiters.removeFirst();
				final int size = Math.min(
					waiter.buffer.remaining(), contentLength - position);
				waiter.buffer.append(content, position, position + size);
				waiter.bytesRead = size;
				ready.add(waiter);
				position += size;
			}
			// If the message still contains data, then enqueue it. Preserve the
			// position.
			if (position != contentLength)
			{
				assert waiters.isEmpty();
				final Message message = new Message(content);
				messages.addFirst(message);
			}
			// Otherwise, reset the position (since the whole message was
			// consumed).
			else
			{
				position = 0;
			}
		}
		for (final Waiter waiter : ready)
		{
			waiter.completed();
		}
	}

	@Override
	public <A> void read (
		final CharBuffer buffer,
		final @Nullable A attachment,
		final CompletionHandler<Integer, A> handler)
	{
		// If the underlying channel is closed, then invoke the handler's
		// failure entry point.
		if (!isOpen())
		{
			try
			{
				throw new ClosedChannelException();
			}
			catch (final ClosedChannelException e)
			{
				handler.failed(e, attachment);
				return;
			}
		}
		int totalSize = 0;
		synchronized (this)
		{
			if (messages.isEmpty())
			{
				final Waiter waiter = new Waiter(buffer, attachment, handler);
				waiters.addLast(waiter);
				return;
			}
			assert waiters.isEmpty();
			// Otherwise, attempt to fill the buffer.
			while (buffer.hasRemaining() && !messages.isEmpty())
			{
				final Message message = messages.peekFirst();
				final String content = message.content();
				final int contentLength = content.length();
				final int size = Math.min(
					buffer.remaining(), contentLength - position);
				buffer.append(content, position, position + size);
				// If the channel has been marked, then duplicate message data
				// into the mark buffer.
				final CharBuffer mark = markBuffer;
				if (mark != null)
				{
					try
					{
						mark.append(content, position, position + size);
					}
					catch (final BufferOverflowException e)
					{
						// Invalidate the mark.
						markBuffer = null;
					}
				}
				position += size;
				if (position == contentLength)
				{
					messages.removeFirst();
					position = 0;
				}
				totalSize += size;
			}
			// If data has been marked, then truncate the current message if
			// necessary in order to keep positioning simple (by eliminating
			// redundant data).
			final CharBuffer mark = markBuffer;
			if (mark != null && position != 0 && !messages.isEmpty())
			{
				final Message message = messages.removeFirst();
				final Message newMessage = new Message(
					message.content().substring(position));
				messages.addFirst(newMessage);
				position = 0;
			}
		}
		handler.completed(totalSize, attachment);
	}

	/**
	 * Receive a {@linkplain Message message} for delivery to a reader.
	 *
	 * @param message
	 *        A message.
	 * @param receiveNext
	 *        How to receive the next message from the underlying {@linkplain
	 *        AvailServerChannel channel}.
	 */
	public void receiveMessageThen (
		final Message message,
		final Continuation0 receiveNext)
	{
		final List<Waiter> ready;
		synchronized (this)
		{
			if (waiters.isEmpty())
			{
				messages.addLast(message);
				return;
			}
			// Otherwise, attempt to feed the message into any waiters.
			assert messages.isEmpty();
			assert position == 0;
			ready = new ArrayList<>();
			final String content = message.content();
			final int contentLength = content.length();
			while (position != contentLength && !waiters.isEmpty())
			{
				final Waiter waiter = waiters.removeFirst();
				final int size = Math.min(
					waiter.buffer.remaining(), contentLength - position);
				waiter.buffer.append(content, position, position + size);
				waiter.bytesRead = size;
				ready.add(waiter);
				// If the channel has been marked, then duplicate message data
				// into the mark buffer.
				final CharBuffer mark = markBuffer;
				if (mark != null)
				{
					try
					{
						mark.append(content, position, position + size);
					}
					catch (final BufferOverflowException e)
					{
						// Invalidate the mark.
						markBuffer = null;
					}
				}
				position += size;
			}
			if (position != contentLength)
			{
				final CharBuffer mark = markBuffer;
				// If data has been marked, then truncate the current message if
				// necessary in order to keep positioning simple (by eliminating
				// redundant data). Reset the position.
				if (mark != null)
				{
					final Message newMessage = new Message(
						message.content().substring(position));
					messages.addLast(newMessage);
					position = 0;
				}
				// Otherwise, maintain the position and just add the message.
				else
				{
					messages.addLast(message);
				}
			}
			// Otherwise, reset the position.
			else
			{
				position = 0;
			}
		}
		for (final Waiter waiter : ready)
		{
			waiter.completed();
		}
		receiveNext.value();
	}

	@Override
	public void close () throws IOException
	{
		// The AvailServerChannel should be closed, not this.
		assert false : "This should not be closed directly!";
	}
}
