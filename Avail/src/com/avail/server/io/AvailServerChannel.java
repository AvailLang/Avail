/**
 * AvailServerChannel.java
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

import com.avail.annotations.Nullable;
import com.avail.io.TextInterface;
import com.avail.server.AvailServer;
import com.avail.server.messages.Message;
import com.avail.utility.evaluation.Continuation0;

/**
 * An {@code AvailServerChannel} represents an abstract connection between an
 * {@link AvailServer} and a client (represented by a {@link TransportAdapter}).
 * It provides mechanisms for sending and receiving {@linkplain Message
 * messages}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class AvailServerChannel
implements AutoCloseable
{
	/**
	 * Answer the {@linkplain AvailServer server} that created this {@linkplain
	 * AbstractTransportChannel channel}.
	 *
	 * @return The {@code AvailServer} that created this channel.
	 */
	public abstract AvailServer server ();

	/**
	 * Is the {@linkplain AvailServerChannel channel} open?
	 *
	 * @return {@code true} if the channel is open, {@code false} otherwise.
	 */
	public abstract boolean isOpen ();

	/**
	 * Enqueue the given {@linkplain Message message}. When the message has
	 * been enqueued, then execute the {@linkplain Continuation0 continuation}.
	 *
	 * @param message
	 *        A message.
	 * @param enqueueSucceeded
	 *        What to do when the message has been successfully enqueued.
	 */
	public abstract void enqueueMessageThen (
		final Message message,
		final Continuation0 enqueueSucceeded);

	/**
	 * Receive an incoming {@linkplain Message message}.
	 *
	 * @param message
	 *        A message.
	 */
	public abstract void receiveMessage (final Message message);

	/**
	 * Is this {@linkplain AvailServerChannel channel} eligible for upgrade?
	 */
	private volatile boolean eligibleForUpgrade = true;

	/**
	 * Is this {@linkplain AvailServerChannel channel} eligible for upgrade?
	 *
	 * @return {@code true} if the channel is eligible for upgrade, {@code
	 *         false} otherwise.
	 */
	public boolean isEligibleForUpgrade ()
	{
		return eligibleForUpgrade;
	}

	/**
	 * Mark the {@linkplain AvailServerChannel channel} as ineligible for
	 * upgrade.
	 */
	public void beIneligibleForUpgrade ()
	{
		eligibleForUpgrade = false;
	}

	/**
	 * Is the {@linkplain AvailServerChannel receiver} a general I/O channel?
	 *
	 * @return {@code true} if the receiver is a general I/O channel, {@code
	 *         false} otherwise.
	 */
	public boolean isIOChannel ()
	{
		return textInterface != null;
	}

	/**
	 * The {@linkplain TextInterface text interface}, or {@code null} if the
	 * {@linkplain AvailServerChannel receiver} is not an upgraded I/O channel.
	 */
	private @Nullable TextInterface textInterface;

	/**
	 * Answer the {@linkplain TextInterface text interface}. As a precondition
	 * of invocation, {@link #isIOChannel()} must return {@code true}.
	 *
	 * @return The text interface.
	 */
	public TextInterface textInterface ()
	{
		final TextInterface io = textInterface;
		assert io != null;
		return io;
	}

	/**
	 * Upgrade the {@linkplain AvailServerChannel channel} for general I/O.
	 */
	public void upgradeToIOChannel ()
	{
		textInterface = new TextInterface(
			new ServerInputChannel(this),
			new ServerOutputChannel(this),
			new ServerErrorChannel(this));
	}
}
