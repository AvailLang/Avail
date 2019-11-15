/*
 * ChannelCloseHandler.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

import com.avail.server.AvailServer.Companion.logger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/**
 * A `ChannelCloseHandler` manages a custom action to be run upon the closing of
 * an [AvailServerChannel].
 *
 * A [DisconnectReason] will be [logged][DisconnectReason.log] when the
 * application logging level is set to [Level.FINER] or below.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a [ChannelCloseHandler].
 *
 * @param channel
 *   The [channel][AvailServerChannel] this `ChannelCloseHandler` will run the
 *   close action for upon the channel closing.
 * @param onChannelCloseAction
 *   The custom action that is to be called when the provided channel is closed
 *   in order to support implementation-specific requirements during the closing
 *   of a channel.
 */
class ChannelCloseHandler constructor(
	channel: AvailServerChannel,
	onChannelCloseAction: (DisconnectReason, AvailServerChannel) -> Unit)
{
	/**
	 * `true` indicates the close [onCloseAction] has not been run and
	 * is eligible to run; `false` otherwise.
	 */
	private val onCloseActionNotRun = AtomicBoolean(true)

	/**
	 * A [DisconnectReason] place holder for scheduled close of an
	 * [AvailServerChannel] that is scheduled to be closed.
	 */
	var reason: DisconnectReason? = null
		set(value)
		{
			logger.log(
				Level.FINEST,
				"Channel close reason changed from $reason to $value")
			field = value
		}

	/**
	 * The action to run when the [AvailServerChannel] is to be closed. This
	 * action is only permitted to be run once.
	 */
	private val onCloseAction: (DisconnectReason) -> Unit =
	{
		if (onCloseActionNotRun.getAndSet(false))
		{
			onChannelCloseAction(it, channel)
			it.log(logger, Level.FINER, channel.toString())
		}
		else
		{
			it.log(
				logger,
				Level.FINEST,
				"Duplication channel close request: [$channel]")
		}
	}

	/**
	 * Run the [onCloseAction] with the provided [DisconnectReason],
	 * disregarding any previously set `DisconnectReason` scheduled through
	 * [reason].
	 */
	fun close (reason: DisconnectReason) = onCloseAction(reason)

	/**
	 * Run the [onCloseAction] with the [reason] if available,
	 * otherwise run it with [UnspecifiedDisconnectReason].
	 */
	fun close () =
		reason?.let {
			onCloseAction(it)
		} ?: onCloseAction(UnspecifiedDisconnectReason)
}