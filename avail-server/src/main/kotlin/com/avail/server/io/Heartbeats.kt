/*
 * Heartbeat.kt
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

package com.avail.server.io

import java.util.Random
import java.util.UUID
import java.util.concurrent.ScheduledFuture

/**
 * A `Heartbeat` declares function signatures for managing bi-directional
 * heartbeats between the client and server. It is the mechanism for performing
 * desired actions when sending and receiving a heartbeat.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
interface Heartbeat
{
	/** Send a heartbeat to the connected client. */
	fun sendHeartbeat ()

	/** Receive a heartbeat from the connected client. */
	fun receiveHeartbeat ()

	/** Discontinue sending any [Heartbeat]. */
	fun cancel ()

	/** The [AvailServerChannel.id] this [Heartbeat] is for. */
	val channelId: UUID?
}

/**
 * An `AbstractHeartbeat` is a [Heartbeat] for sending and receiving heartbeats
 * across an [AbstractTransportChannel].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property heartbeatInterval
 *   The time in milliseconds between each [Heartbeat] request made by the
 *   server to the client after receiving a `Heartbeat` from the client.
 * @property heartbeatTimeout
 *   The amount of time, in milliseconds, after which the heartbeat will fail if
 *   a heartbeat is not received from the client by the server.
 * @property heartbeatFailureThreshold
 *   The number of consecutive times the [heartbeatTimeout] is allowed to be
 *   reached before [heartbeatFailureThresholdReached] is called.
 *
 * @constructor
 * Construct an [AbstractHeartbeat].
 *
 * @param heartbeatInterval
 *   The time in milliseconds between each [Heartbeat] request made by the
 *   server to the client after receiving a `Heartbeat` from the client.
 * @param heartbeatTimeout
 *   The amount of time, in milliseconds, after which the heartbeat will fail if
 *   a heartbeat is not received from the client by the server.
 * @param heartbeatFailureThreshold
 *   The number of consecutive times the [heartbeatTimeout] is allowed to be
 *   reached before [heartbeatFailureThresholdReached] is called.
 */
internal abstract class AbstractHeartbeat constructor(
	private val heartbeatFailureThreshold: Int,
	protected val heartbeatInterval: Long,
	private val heartbeatTimeout: Long) : Heartbeat
{
	/**
	 * The [timer][ScheduledFuture] for monitoring transport substrate
	 * connectivity via the use of a heartbeat.
	 */
	private var heartBeatTimer: ScheduledFuture<*>? = null

	/** The number of times the pong has failed to respond to the ping. */
	private var heartbeatFailureCount = 0

	/**
	 * `true` indicates this [Heartbeat] should be [cancelled][cancel]; `false`
	 * otherwise.
	 */
	protected var heartbeatCancelled = false

	override fun cancel()
	{
		heartbeatCancelled = true
		heartBeatTimer?.cancel(true)
	}

	/**
	 * Schedule the next action to be taken if the [heartBeatTimer] were to
	 * expire.
	 *
	 * @param action
	 *   The zero-argument function returning `Unit` to be run when the timer
	 *   expires.
	 */
	abstract fun scheduleNextHeartbeatAction (action: () -> Unit)
		: ScheduledFuture<*>

	/**
	 * Performs the prescribed actions when a [heartbeatFailureCount]
	 */
	abstract fun heartbeatFailureThresholdReached ()

	/**
	 * Start the [heartBeatTimer] to send a heartbeat again when the next
	 * [heartbeatInterval] occurs.
	 */
	protected fun startHeartbeatTimer ()
	{
		heartBeatTimer?.cancel(true)
		if (heartbeatCancelled) { return }
		heartBeatTimer = scheduleNextHeartbeatAction {
			heartBeatTimer?.cancel(true)
			heartBeatTimer = scheduleNextHeartbeatAction {
				if (++heartbeatFailureCount == 3)
				{
					heartBeatTimer?.cancel(true)
					heartbeatCancelled = true
					heartbeatFailureThresholdReached()
				}
				else
				{
					sendHeartbeat()
				}
			}
		}
	}

	override fun receiveHeartbeat ()
	{
		heartbeatFailureCount = 0
		startHeartbeatTimer()
	}
}

/**
 * `WebSocketChannelHeartbeat` is an [AbstractHeartbeat] used on a
 * [WebSocketChannel].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property channel
 *   The [WebSocketChannel] to add this [WebSocketChannelHeartbeat] to.
 *
 * @constructor
 * Construct a [WebSocketChannelHeartbeat].
 *
 * @param channel
 *   The [WebSocketChannel] to add this [WebSocketChannelHeartbeat] to.
 * @param heartbeatInterval
 *   The time in milliseconds between each [Heartbeat] request made by the
 *   server to the client after receiving a `Heartbeat` from the client.
 * @param heartbeatTimeout
 *   The amount of time, in milliseconds, after which the heartbeat will fail if
 *   a heartbeat is not received from the client by the server.
 * @param heartbeatFailureThreshold
 *   The number of consecutive times the [heartbeatTimeout] is allowed to be
 *   reached before [heartbeatFailureThresholdReached] is called.
 */
internal class WebSocketChannelHeartbeat constructor(
		val channel: WebSocketChannel,
		heartbeatFailureThreshold: Int,
		heartbeatInterval: Long,
		heartbeatTimeout: Long)
	: AbstractHeartbeat(
		heartbeatFailureThreshold, heartbeatInterval, heartbeatTimeout)
{
	override val channelId: UUID? get() = channel.id

	override fun scheduleNextHeartbeatAction(action: () -> Unit)
		: ScheduledFuture<*> =
			channel.adapter.millisTimer(heartbeatInterval, action)

	override fun sendHeartbeat ()
	{
		if (heartbeatCancelled) { return }
		val byteArray = ByteArray(10)
		Random().nextBytes(byteArray)
		WebSocketAdapter.sendPing(
			channel,
			byteArray,
			{
				startHeartbeatTimer()
			},
			{
				startHeartbeatTimer()
			})
	}

	override fun heartbeatFailureThresholdReached()
	{
		channel.adapter.sendClose(channel, HeartbeatFailureDisconnect)
	}
}

/**
 * A `NoHeartbeat` is a deactivated [Heartbeat]. This is used when no
 * `Heartbeat` is desired for the underlying transport mechanism.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object NoHeartbeat: Heartbeat
{
	// Do nothing
	override fun sendHeartbeat() = Unit

	// Do nothing
	override fun receiveHeartbeat() = Unit

	// Do nothing
	override fun cancel() = Unit

	override val channelId: UUID? = null
}
