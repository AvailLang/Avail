/*
 * AvailServerChannel.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package com.avail.server.io

import com.avail.io.TextInterface
import com.avail.server.AvailServer
import com.avail.server.messages.TextCommand
import com.avail.server.messages.CommandMessage
import com.avail.server.messages.Message
import com.avail.server.messages.UpgradeCommandMessage
import com.avail.server.session.Session
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * An `AvailServerChannel` represents an abstract connection between an
 * [AvailServer] and a client (represented by a [TransportAdapter]). It provides
 * mechanisms for sending and receiving [messages][Message].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct an [AvailServerChannel].
 *
 * @param closeAction
 *   The custom action that is to be called when the this channel is closed in
 *   order to support implementation-specific requirements after the closing of
 *   this channel.
 */
abstract class AvailServerChannel constructor(
	closeAction: (DisconnectReason, AvailServerChannel) -> Unit = {_,_->})
{
	/** `true` if the channel is open, `false` otherwise. */
	abstract val isOpen: Boolean

	/**
	 * The [ChannelCloseHandler] that contains the application-specific close
	 * logic to be run after this [AvailServerChannel] is closed.
	 */
	val channelCloseHandler: ChannelCloseHandler by lazy {
		ChannelCloseHandler(this, closeAction)
	}

	/**
	 * The unique channel [identifier][UUID] used to distinguish this
	 * [AvailServerChannel] from other `AvailServerChannel`s.
	 *
	 * Only command channels will keep their UUID assigned upon construction.
	 * All subordinate IO-channels will have their identifiers set to the
	 * [TextCommand.UPGRADE] token received from a client-sent
	 * [UpgradeCommandMessage].
	 */
	var id: UUID = UUID.randomUUID()

	/**
	 * The [id] of the command [channel][AvailServerChannel] that is responsible
	 * for spawning this channel if this channel was created through the
	 * [upgrade][TextCommand.UPGRADE] process.
	 *
	 * If the value is `null` that means that either one of two things has
	 * happened:
	 *
	 *  1. This is a new channel that has not been upgraded to a specific type
	 *     (e.g IO, Binary, or COMMAND).
	 *  2. This is a COMMAND channel (ProtocolState = COMMAND)
	 *
	 * If this parent id is not null, then the parent id is the same as the
	 * [Session.id] to which this channel belongs to.
	 */
	var parentId: UUID? = null

	/**
	 * The [Session.id] of the [Session] this [AvailServerChannel] belongs to;
	 * `null` if this `AvailServerChannel` has not yet been assigned to a
	 * session.
	 */
	val sessionId: UUID? get() =
		when
		{
			state == ProtocolState.COMMAND -> id
			parentId != null -> parentId
			else -> null
		}

	/**
	 * The [Session] this [AvailServerChannel] belongs to or `null` if not yet
	 * assigned to a `Session`.
 	 */
	val session: Session? get() = sessionId?.let {
		server.sessions[it]
	}

	/**
	 * The time in milliseconds since the Unix Epoch when this
	 * [AvailServerChannel] was first created.
	 */
	val created = System.currentTimeMillis()

	/**
	 * `true` if this [AvailServerChannel] is an IO channel for a
	 * [parent][parentId] command channel.
	 */
	private val isIOChannel get() = state.generalTextIO

	/** The current [protocol state][ProtocolState].  */
	var state = ProtocolState.VERSION_NEGOTIATION
		set (newState)
		{
			assert(this.state.allowedSuccessorStates.contains(newState))
			field = newState
		}

	/**
	 * Schedule this [AvailServerChannel] to be closed cleanly if possible. This
	 * does not guarantee that the channel will close right away nor does it
	 * guarantee that it will be closed cleanly.
	 *
	 * @param reason
	 *   The [DisconnectReason] for closing the channel.
	 */
	abstract fun scheduleClose (reason: DisconnectReason)

	/**
	 * Close this [AvailServerChannel] immediately. The provided reason will
	 * be provided to the [channelCloseHandler] as the closing reason,
	 * overriding any previously stated close reason if any had been provided
	 * during a [scheduleClose] request.
	 *
	 * @param reason
	 *   The [DisconnectReason] for closing the channel.
	 */
	abstract fun closeImmediately (reason: DisconnectReason)

	/**
	 * The [text&#32;interface][TextInterface], or `null` if the
	 * [receiver][AvailServerChannel] is not an upgraded I/O channel.
	 */
	var textInterface: TextInterface? = null

	/**
	 * A `ServerInputNotificationChannel` adapts a [channel][AvailServerChannel]
	 * for use as a notifier of a pending interest in receiving standard input.
	 */
	inner class ServerInputNotificationChannel
		: AbstractServerOutputChannel(this)
	{
		override val channelTag = "in"
	}

	/**
	 * The [ServerInputNotificationChannel], if any. Present only for I/O
	 * channels.
	 */
	var inputNotificationChannel: ServerInputNotificationChannel? = null

	/**
	 * The [UUID]s of any upgrade requests issued by this
	 * [channel][AvailServerChannel].
	 */
	private val requestedUpgrades = mutableSetOf<UUID>()

	/** The next [command][CommandMessage] [identifier][AtomicLong] to issue. */
	private val commandId = AtomicLong(1)

	/**
	 * The [server][AvailServer] that created this
	 * [channel][AbstractTransportChannel].
	 */
	abstract val server: AvailServer

	/**
	 * Enqueue the given [message][Message]. When the message has been enqueued,
	 * then execute the action.
	 *
	 * @param message
	 *   A message.
	 * @param enqueueSucceeded
	 *   What to do when the message has been successfully enqueued.
	 */
	abstract fun enqueueMessageThen(
		message: Message,
		enqueueSucceeded: ()->Unit)

	/**
	 * Receive an incoming [message][Message].
	 *
	 * @param message
	 *   A message.
	 */
	abstract fun receiveMessage(message: Message)

	/**
	 * `ProtocolState` represents the communication state of a
	 * [server&#32;channel][AvailServerChannel].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	enum class ProtocolState
	{
		/** Protocol version must be negotiated.  */
		VERSION_NEGOTIATION
		{
			override val allowedSuccessorStates
				get() = setOf(ELIGIBLE_FOR_UPGRADE)
			override val versionNegotiated get() = false
		},

		/**
		 * The [channel][AvailServerChannel] is eligible for upgrade.
		 */
		ELIGIBLE_FOR_UPGRADE
		{
			override val allowedSuccessorStates: Set<ProtocolState>
				get() = EnumSet.of(COMMAND, IO, BINARY)
			override val eligibleForUpgrade get() = true
		},

		/**
		 * The [channel][AvailServerChannel] should henceforth be used to issue
		 * [commands][CommandMessage].
		 */
		COMMAND
		{
			override val allowedSuccessorStates: Set<ProtocolState>
				get() = emptySet()
		},

		/**
		 * The [channel][AvailServerChannel] should henceforth be used for
		 * general text I/O.
		 */
		IO
		{
			override val allowedSuccessorStates: Set<ProtocolState>
				get() = emptySet()
			override val generalTextIO get() = true
		},

		/**
		 * The [channel][AvailServerChannel] should henceforth be used for
		 * general binary data transmission.
		 */
		BINARY
		{
			override val allowedSuccessorStates: Set<ProtocolState>
				get() = emptySet()
			override val generalBinary get() = true
		};

		/** The allowed successor [states][ProtocolState] of the receiver. */
		internal abstract val allowedSuccessorStates: Set<ProtocolState>

		/**
		 * Does this [state][ProtocolState] indicate that the version has
		 * already been negotiated? `true` if the version has already been
		 * negotiated, `false` otherwise.
		 */
		open val versionNegotiated get() = true

		/**
		 * Does this [state][ProtocolState] indicate eligibility for upgrade?
		 * `true` if the state indicates eligibility for upgrade, `false`
		 * otherwise.
		 */
		open val eligibleForUpgrade get() = false

		/**
		 * Does this [state][ProtocolState] indicate a capability to do general
		 * text I/O? `true` if the state indicates the capability, `false`
		 * otherwise.
		 */
		open val generalTextIO get() = false

		/**
		 * Does this [state][ProtocolState] indicate a capability to transmit
		 * binary? `true` if the state indicates the capability, `false`
		 * otherwise.
		 */
		open val generalBinary get() = false
	}

	/**
	 * Upgrade the [channel][AvailServerChannel] for general text I/O.
	 */
	fun upgradeToIOChannel()
	{
		state = ProtocolState.IO
		textInterface = TextInterface(
			ServerInputChannel(this),
			ServerOutputChannel(this),
			ServerErrorChannel(this))
		inputNotificationChannel = ServerInputNotificationChannel()
	}

	/**
	 * Record an upgrade request instigated by this
	 * [channel][AvailServerChannel].
	 *
	 * @param uuid
	 *   The [UUID] that identifies the upgrade request.
	 */
	fun recordUpgradeRequest(uuid: UUID)
	{
		synchronized(requestedUpgrades) {
			requestedUpgrades.add(uuid)
		}
	}

	protected fun finalize()
	{
		try
		{
			server.discontinueUpgradeRequests(requestedUpgrades)
		}
		catch (e: Throwable)
		{
			// Do not prevent destruction of this channel.
		}

	}

	/**
	 * The next [command&#32;identifier][CommandMessage] from the
	 * [channel][AvailServerChannel]'s internal sequence.
	 */
	val nextCommandId get() = commandId.getAndIncrement()

	override fun toString(): String =
		if (isIOChannel)
		{
			"($id) $state: [$parentId] "
		}
		else
		{
			"($id) $state"
		}
}
