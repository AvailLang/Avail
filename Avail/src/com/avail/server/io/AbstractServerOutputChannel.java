/**
 * AbstractServerOutputChannel.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import com.avail.annotations.Nullable;
import com.avail.io.TextOutputChannel;
import com.avail.server.messages.Message;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.json.JSONWriter;

/**
 * A {@code AbstractServerOutputChannel} adapts an {@link AvailServerChannel
 * channel} for ordinary output.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class AbstractServerOutputChannel
implements TextOutputChannel
{
	/** The underlying {@linkplain AvailServerChannel server channel}. */
	private final AvailServerChannel channel;

	/**
	 * Construct a new {@link AbstractServerOutputChannel}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel server channel} to adapt for
	 *        general output.
	 */
	public AbstractServerOutputChannel (final AvailServerChannel channel)
	{
		this.channel = channel;
	}

	@Override
	public final boolean isOpen ()
	{
		return channel.isOpen();
	}

	/**
	 * Answer the prefix for {@linkplain Message message} {@linkplain
	 * Message#content() content} written to this output channel.
	 *
	 * @return The prefix for this output channel.
	 */
	protected abstract String channelTag ();

	/**
	 * Answer a {@linkplain Message message} that suitably represents this
	 * {@linkplain AbstractServerOutputChannel channel} and the specified
	 * {@linkplain String content}.
	 *
	 * @param data
	 *        The content of the message.
	 * @return A message.
	 */
	private Message newMessage (final String data)
	{
		@SuppressWarnings("resource")
		final JSONWriter writer = new JSONWriter();
		writer.startObject();
		writer.write("tag");
		writer.write(channelTag());
		writer.write("content");
		writer.write(data);
		writer.endObject();
		return new Message(writer.toString());
	}

	@Override
	public final <A> void write (
		final CharBuffer buffer,
		final @Nullable A attachment,
		final CompletionHandler<Integer, A> handler)
	{
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
		final int limit = buffer.limit();
		final Message message = newMessage(buffer.toString());
		channel.enqueueMessageThen(
			message,
			new Continuation0()
			{
				@Override
				public void value ()
				{
					handler.completed(limit, attachment);
				}
			});
	}

	@Override
	public final <A> void write (
		final String data,
		final @Nullable A attachment,
		final CompletionHandler<Integer, A> handler)
	{
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
		final Message message = newMessage(data);
		channel.enqueueMessageThen(
			message,
			new Continuation0()
			{
				@Override
				public void value ()
				{
					handler.completed(data.length(), attachment);
				}
			});
	}

	@Override
	public void close ()
	{
		// The AvailServerChannel should be closed, not this.
		assert false : "This should not be closed directly!";
	}
}
