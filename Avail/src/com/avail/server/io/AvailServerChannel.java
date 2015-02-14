/**
 * AvailServerChannel.java
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import com.avail.annotations.Nullable;
import com.avail.io.TextInterface;
import com.avail.server.AvailServer;
import com.avail.server.messages.CommandMessage;
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
	 * {@code ProtocolState} represents the communication state of a {@linkplain
	 * AvailServerChannel server channel}.
	 */
	public static enum ProtocolState
	{
		/** Protocol version must be negotiated. */
		VERSION_NEGOTIATION
		{
			@Override
			Set<ProtocolState> allowedSuccessorStates ()
			{
				return Collections.singleton(ELIGIBLE_FOR_UPGRADE);
			}

			@Override
			public boolean versionNegotiated ()
			{
				return false;
			}
		},

		/**
		 * The {@linkplain AvailServerChannel channel} is eligible for upgrade.
		 */
		ELIGIBLE_FOR_UPGRADE
		{
			@Override
			Set<ProtocolState> allowedSuccessorStates ()
			{
				return new HashSet<>(Arrays.asList(COMMAND, IO));
			}

			@Override
			public boolean eligibleForUpgrade ()
			{
				return true;
			}
		},

		/**
		 * The {@linkplain AvailServerChannel channel} should henceforth be
		 * used to issue {@linkplain CommandMessage commands}.
		 */
		COMMAND
		{
			@Override
			Set<ProtocolState> allowedSuccessorStates ()
			{
				return Collections.emptySet();
			}
		},

		/**
		 * The {@linkplain AvailServerChannel channel} should henceforth be
		 * used for general text I/O.
		 */
		IO
		{
			@Override
			Set<ProtocolState> allowedSuccessorStates ()
			{
				return Collections.emptySet();
			}

			@Override
			public boolean generalTextIO ()
			{
				return true;
			}
		};

		/**
		 * Answer the allowed successor {@linkplain ProtocolState states} of
		 * the receiver.
		 *
		 * @return The allowed successor states.
		 */
		abstract Set<ProtocolState> allowedSuccessorStates ();

		/**
		 * Does this {@linkplain ProtocolState state} indicate that the version
		 * has already been negotiated?
		 *
		 * @return {@code true} if the version has already been negotiated,
		 *         {@code false} otherwise.
		 */
		public boolean versionNegotiated ()
		{
			return true;
		}

		/**
		 * Does this {@linkplain ProtocolState state} indicate eligibility for
		 * upgrade?
		 *
		 * @return {@code true} if the state indicates eligibility for upgrade,
		 *         {@code false} otherwise.
		 */
		public boolean eligibleForUpgrade ()
		{
			return false;
		}

		/**
		 * Does this {@linkplain ProtocolState state} indicate a capability to
		 * do general text I/O?
		 *
		 * @return {@code true} if the state indicates the capability, {@code
		 *         false} otherwise.
		 */
		public boolean generalTextIO ()
		{
			return false;
		}
	}

	/** The current {@linkplain ProtocolState protocol state}. */
	private ProtocolState state = ProtocolState.VERSION_NEGOTIATION;

	/**
	 * Answer the current {@linkplain ProtocolState protocol state}.
	 *
	 * @return The current protocol state.
	 */
	public ProtocolState state ()
	{
		return state;
	}

	/**
	 * Set the {@linkplain ProtocolState protocol state}.
	 *
	 * @param state
	 *        The new protocol state. This must be an {@linkplain
	 *        ProtocolState#allowedSuccessorStates() allowed successor} of the
	 *        {@linkplain #state() current protocol state}.
	 */
	public void setState (final ProtocolState state)
	{
		assert this.state.allowedSuccessorStates().contains(state);
		this.state = state;
	}

	/**
	 * The {@linkplain TextInterface text interface}, or {@code null} if the
	 * {@linkplain AvailServerChannel receiver} is not an upgraded I/O channel.
	 */
	private @Nullable TextInterface textInterface;

	/**
	 * Answer the {@linkplain TextInterface text interface}. This is applicable
	 * for {@linkplain ProtocolState#generalTextIO() general text I/O}
	 * {@linkplain AvailServerChannel channels} only.
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
		setState(ProtocolState.IO);
		textInterface = new TextInterface(
			new ServerInputChannel(this),
			new ServerOutputChannel(this),
			new ServerErrorChannel(this));
	}

	/**
	 * The {@link UUID}s of any upgrade requests issued by this {@linkplain
	 * AvailServerChannel channel}.
	 */
	private final Set<UUID> requestedUpgrades = new HashSet<>();

	/**
	 * Record an upgrade request instigated by this {@linkplain
	 * AvailServerChannel channel}.
	 *
	 * @param uuid
	 *        The {@link UUID} that identifies the upgrade request.
	 */
	public void recordUpgradeRequest (final UUID uuid)
	{
		synchronized (requestedUpgrades)
		{
			requestedUpgrades.add(uuid);
		}
	}

	@Override
	protected void finalize ()
	{
		try
		{
			server().discontinueUpgradeRequests(requestedUpgrades);
		}
		catch (final Throwable e)
		{
			// Do not prevent destruction of this channel.
		}
	}

	/**
	 * The next {@linkplain CommandMessage command} {@linkplain AtomicLong
	 * identifier} to issue.
	 */
	private final AtomicLong commandId = new AtomicLong(1);

	/**
	 * Answer the next {@linkplain CommandMessage command identifier} from the
	 * {@linkplain AvailServerChannel channel}'s internal sequence.
	 *
	 * @return The next command identifier.
	 */
	public long nextCommandId ()
	{
		return commandId.getAndIncrement();
	}
}
