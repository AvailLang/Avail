/*
 * TestAvailServerChannel.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.server.test.utility

import com.avail.server.AvailServer
import com.avail.server.io.AvailServerChannel
import com.avail.server.io.DisconnectReason
import com.avail.server.messages.Message
import com.avail.server.messages.binary.editor.BinaryCommand
import com.avail.server.session.Session
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * `TestAvailServerChannel` is a mock [AvailServerChannel] used for integration
 * tests.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a [TestAvailServerChannel].
 *
 * @param server
 *   The running [AvailServer].
 */
class TestAvailServerChannel constructor(
		override val server: AvailServer,
		closeAction: (DisconnectReason, AvailServerChannel) -> Unit = {_,_->})
	: AvailServerChannel(closeAction)
{
	/** Semaphore used to sync async actions. */
	val semaphore = Semaphore(0)

	/**
	 * Queue that contains messages that have been
	 * [enqueued][enqueueMessageThen]
	 */
	val sendQueue= mutableListOf<TestBinaryMessageHolder>()

	/**
	 * The number of messages expected to be [enqueued][enqueueMessageThen]
	 * before the [semaphore] should be released.
	 */
	val expectedMessageCount = AtomicInteger(0)

	init
	{
		parentId = id
		Session(this).let {
			server.sessions[it.id] = it
		}
		state = ProtocolState.ELIGIBLE_FOR_UPGRADE
		state = ProtocolState.BINARY
	}

	private val open = AtomicBoolean(false)

	override val isOpen: Boolean
		get() = open.get()

	override fun scheduleClose(reason: DisconnectReason)
	{
		// We won't treat this any differently than a closeImmediately
		// as doing anything more is outside the use case for this mock
		closeImmediately(reason)
	}

	override fun closeImmediately(reason: DisconnectReason)
	{
		channelCloseHandler.reason = reason
		open.set(false)
	}

	private val exitConditions =
		setOf(BinaryCommand.INVALID, BinaryCommand.ERROR)

	override fun enqueueMessageThen(
		message: Message,
		enqueueSucceeded: ()->Unit)
	{
		val testMessage = TestBinaryMessageHolder(message)
		sendQueue.add(testMessage)
		if (expectedMessageCount.decrementAndGet() == 0
			|| exitConditions.contains(testMessage.binaryCommand))
		{
			semaphore.release()
			return
		}
		else
		{
			enqueueSucceeded()
		}
	}

	override fun receiveMessage(message: Message)
	{
		val buffer = ByteBuffer.wrap(message.content)
		val id = buffer.int
		val commandId = buffer.long
		BinaryCommand.command(id).receiveThen(
			id, commandId, buffer, this) { }
	}
}