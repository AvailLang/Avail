/*
 * BinaryMessageBuilder.kt
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

import com.avail.server.io.AvailServerChannel
import com.avail.server.messages.Message
import com.avail.server.messages.binary.editor.BinaryCommand
import com.avail.server.messages.binary.editor.BinaryMessage
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * `BinaryMessageBuilder` is a helper for creating client binary messages.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class BinaryMessageBuilder
{
	/**
	 * A counter for the transaction command id
	 */
	val transactionId = AtomicLong(1)

	/** Encode the message. */
	private fun encodeMessage (command: BinaryCommand, content: ByteArray): Message
	{
		val size = BinaryMessage.PREFIX_SIZE + content.size
		val raw = ByteArray(size)
		ByteBuffer.allocate(size).apply {
			this.putInt(command.id)
				.putLong(transactionId.getAndIncrement())
				.put(content)
				.flip()
		}.get(raw)

		return  Message(raw, AvailServerChannel.ProtocolState.BINARY)
	}

	fun openFile (relativePath: String): Message =
		encodeMessage(
			BinaryCommand.OPEN_FILE,
			relativePath.toByteArray(Charsets.UTF_8))
}