/**
 * AbstractTransportChannel.java
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

package com.avail.server.io;

import java.util.Deque;
import java.util.LinkedList;
import com.avail.server.AvailServer;
import com.avail.server.messages.Message;
import com.avail.utility.Pair;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1;

/**
 * An {@code AbstractTransportChannel} represents an abstract connection between
 * an {@link AvailServer} and a {@link TransportAdapter}. It encapsulates the
 * implementation-specific channel required by the {@code TransportAdapter}, and
 * provides mechanisms for sending and receiving messages.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <T> The type of the enclosed channel.
 */
abstract class AbstractTransportChannel<T>
extends AvailServerChannel
{
	/**
	 * Answer the {@link TransportAdapter} that created this {@linkplain
	 * AbstractTransportChannel channel}.
	 *
	 * @return The {@code TransportAdapter} that created this channel.
	 */
	protected abstract TransportAdapter<T> adapter ();

	/**
	 * Answer the underlying channel.
	 *
	 * @return The underlying channel.
	 */
	protected abstract T transport ();

	/**
	 * Answer the {@linkplain AvailServer server} that created this {@linkplain
	 * AbstractTransportChannel channel}.
	 *
	 * @return The {@code AvailServer} that created this channel.
	 */
	@Override
	public AvailServer server ()
	{
		return adapter().server();
	}

	/**
	 * A {@linkplain Deque queue} of {@linkplain Message messages} awaiting
	 * transmission by the {@linkplain TransportAdapter adapter}.
	 */
	protected final Deque<Message> sendQueue = new LinkedList<>();

	/**
	 * Should the {@linkplain AbstractTransportChannel channel} close after
	 * emptying the {@linkplain #sendQueue message queue}?
	 */
	protected boolean closeAfterEmptyingSendQueue = false;

	/**
	 * A {@linkplain Deque queue} of {@linkplain Pair pairs} of {@linkplain
	 * Message messages} and {@linkplain Continuation0 continuations}, as
	 * supplied to calls of {@link #enqueueMessageThen(Message, Continuation0)
	 * enqueueMessageThen}. The maximum depth of this queue is proportional to
	 * the size of the I/O thread pool.
	 */
	protected final Deque<Pair<Message, Continuation0>> senders =
		new LinkedList<>();

	/**
	 * Answer the maximum send queue depth for the message queue.
	 *
	 * @return The maximum send queue depth for the message queue.
	 */
	protected abstract int maximumSendQueueDepth ();

	/**
	 * Begin transmission of the enqueued messages, starting with the specified
	 * {@linkplain Message message}. The argument must be the first message
	 * in the message queue.
	 *
	 * @param message
	 *        The first message in the message queue (still enqueued).
	 */
	protected void beginTransmission (final Message message)
	{
		final Continuation1<Throwable> fail = null;
		final TransportAdapter<T> adapter = adapter();
		adapter.sendUserData(
			this,
			message,
			new Continuation0()
			{
				@Override
				public void value ()
				{
					final Message nextMessage;
					Pair<Message, Continuation0> pair;
					synchronized (sendQueue)
					{
						// The message remains on the queue during
						// transmission (in order to simplify the execution
						// model). Remove it *after* transmission completes.
						final Message sentMessage = sendQueue.removeFirst();
						if (sentMessage.closeAfterSending())
						{
							adapter.sendClose(AbstractTransportChannel.this);
							return;
						}
						// Remove the oldest sender, but release the monitor
						// before evaluating it.
						pair = senders.pollFirst();
						if (pair != null)
						{
							sendQueue.addLast(pair.first());
						}
						nextMessage = sendQueue.peekFirst();
						assert sendQueue.size() <= maximumSendQueueDepth();
					}
					// Begin transmission of the next message.
					if (nextMessage != null)
					{
						adapter.sendUserData(
							AbstractTransportChannel.this,
							nextMessage,
							this,
							fail);
					}
					// If a close is in progress, but awaiting the queue to
					// empty, then finish the close.
					else if (closeAfterEmptyingSendQueue)
					{
						adapter.sendClose(AbstractTransportChannel.this);
						return;
					}
					// Proceed the paused client.
					if (pair != null)
					{
						pair.second().value();
					}
				}
			},
			fail);
	}

	@Override
	public void enqueueMessageThen (
		final Message message,
		final Continuation0 enqueueSucceeded)
	{
		final boolean beginTransmitting;
		final boolean invokeContinuationNow;
		final int maxQueueDepth = maximumSendQueueDepth();
		synchronized (sendQueue)
		{
			final int size = sendQueue.size();
			// On the transition from empty to nonempty, begin consuming the
			// message queue.
			if (size == 0)
			{
				sendQueue.addLast(message);
				beginTransmitting = true;
				invokeContinuationNow = true;
			}
			// If the queue is nonempty and there is room available on the
			// message queue, then simply enqueue the message.
			else if (size < maxQueueDepth)
			{
				sendQueue.addLast(message);
				beginTransmitting = false;
				invokeContinuationNow = true;
			}
			// If there is no room available on the message queue, then pause
			// the client until room becomes available.
			else
			{
				assert size == maxQueueDepth;
				senders.addLast(new Pair<>(
					message, enqueueSucceeded));
				beginTransmitting = false;
				invokeContinuationNow = false;
			}
		}
		// Initiate the asynchronous transmission "loop".
		if (beginTransmitting)
		{
			beginTransmission(message);
		}
		// Run the supplied continuation to proceed the execution of the client.
		if (invokeContinuationNow)
		{
			enqueueSucceeded.value();
		}
	}

	/**
	 * A {@linkplain Deque queue} of {@linkplain Message messages} awaiting
	 * processing by the {@linkplain AvailServer server}.
	 */
	protected final Deque<Message> receiveQueue = new LinkedList<>();

	/**
	 * Answer the maximum receive queue depth for the message queue.
	 *
	 * @return The maximum receive queue depth for the message queue.
	 */
	protected abstract int maximumReceiveQueueDepth ();

	/**
	 * Begin receipt of the enqueued messages, starting with the specified
	 * {@linkplain Message message}. The argument must be the first message
	 * in the message queue.
	 *
	 * @param message
	 *        The first message in the message queue (still enqueued).
	 */
	protected void beginReceiving (final Message message)
	{
		final int maxQueueDepth = maximumReceiveQueueDepth();
		final AvailServer server = adapter().server();
		server.receiveMessageThen(
			message,
			this,
			new Continuation0()
			{
				@Override
				public void value ()
				{
					final Message nextMessage;
					final boolean resumeReading;
					synchronized (receiveQueue)
					{
						// The message remains on the queue during
						// reception (in order to simplify the execution
						// model). Remove it *after* reception completes.
						receiveQueue.removeFirst();
						nextMessage = receiveQueue.peekFirst();
						assert receiveQueue.size() < maximumReceiveQueueDepth();
						// If the queue transitioned from full to non-full,
						// then resume reading from the transport.
						resumeReading =
							receiveQueue.size() == maxQueueDepth - 1;
					}
					// Begin receipt of the next message.
					if (resumeReading)
					{
						adapter().readMessage(AbstractTransportChannel.this);
					}
					// Process the next message.
					if (nextMessage != null)
					{
						server.receiveMessageThen(
							nextMessage,
							AbstractTransportChannel.this,
							this);
					}
				}
			});
	}

	@Override
	public void receiveMessage (final Message message)
	{
		final boolean beginReceiving;
		final boolean resumeReading;
		final int maxQueueDepth = maximumReceiveQueueDepth();
		synchronized (receiveQueue)
		{
			final int size = receiveQueue.size();
			// On the transition from empty to nonempty, begin consuming the
			// message queue.
			if (size == 0)
			{
				receiveQueue.addLast(message);
				beginReceiving = true;
				resumeReading = true;
			}
			// If the queue is nonempty and there is room available on the
			// message queue, then simply enqueue the message.
			else if (size < maxQueueDepth)
			{
				receiveQueue.addLast(message);
				beginReceiving = false;
				resumeReading = true;
			}
			// If there is no room available on the message queue, then pause
			// the transport until room becomes available.
			else
			{
				assert size == maxQueueDepth;
				beginReceiving = false;
				resumeReading = false;
			}
		}
		// Resume reading messages from the transport.
		if (resumeReading)
		{
			adapter().readMessage(this);
		}
		// Initiate the asynchronous reception "loop".
		if (beginReceiving)
		{
			beginReceiving(message);
		}
	}
}
