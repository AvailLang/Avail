/*
 * FileStreamMessage.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.server.messages.binary.editor

import com.avail.server.io.AvailServerChannel
import com.avail.files.FileManager
import com.avail.server.messages.Message
import com.avail.server.session.Session
import java.nio.charset.StandardCharsets

/**
 * `FileOpenMessage` is a [BinaryMessage] response to an open file request.
 * It provides information about the file requested to be open.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct an [FileOpenedMessage].
 *
 * @param commandId
 *   The identifier of the [message][BinaryMessage]. This identifier should
 *   appear in any responses to this message.
 * @param fileId
 *   The [Session] file cache id that is linked to the [UUID ]that uniquely
 *   identifies the target file in the [FileManager].
 * @param fileSize
 *   The size of the file in byte count.
 * @param mime
 *   The String that identifies the MIME type of the represented file.
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
internal class FileOpenedMessage constructor(
	override var commandId: Long,
	fileId: Int,
	fileSize: Long,
	mime: String): BinaryMessage()
{
	override val command = BinaryCommand.FILE_OPENED
	override val message: Message

	init
	{
		val mimeBytes = mime.toByteArray(StandardCharsets.UTF_8)
		// Base size of payload is 24 byes broken down as:
		//   BinaryCommand.id = 4
		//   commandId = 8
		//   file id = 4
		//   mimeSize = 4
		// file size = 4
		val bufferSize = 24 + mimeBytes.size
		val buffer = buffer(bufferSize)
		buffer.putInt(fileId)
		buffer.putLong(fileSize)
		buffer.putInt(mimeBytes.size)
		buffer.put(mimeBytes)
		buffer.flip()
		val content = ByteArray(bufferSize)
		buffer.get(content)
		this.message =  Message( content, AvailServerChannel.ProtocolState.BINARY)
	}

	override fun processThen(channel: AvailServerChannel, continuation: () -> Unit)
	{
		channel.enqueueMessageThen(message, continuation)
	}
}