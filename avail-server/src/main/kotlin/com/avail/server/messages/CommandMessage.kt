/*
 * CommandMessage.kt
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

package com.avail.server.messages

import com.avail.server.io.AvailServerChannel

/**
 * A `CommandMessage` represents a fully-parsed [command][TextCommand]. Each command
 * message knows the kind of command that it represents.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class CommandMessage
{
	/**
	 * The identifier of the [message][CommandMessage]. This identifier should
	 * appear in any responses to this message.
	 */
	var commandId: Long = 0

	/** The encoded [command][TextCommand]. */
	abstract val command: TextCommand

	/**
	 * Process this [command&#32;message][CommandMessage] on behalf of the
	 * specified [channel][AvailServerChannel].
	 *
	 * @param channel
	 *   The channel that received this command message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred (and the channel
	 *   wishes to begin receiving messages again).
	 */
	abstract fun processThen(
		channel: AvailServerChannel,
		continuation: ()->Unit)
}
