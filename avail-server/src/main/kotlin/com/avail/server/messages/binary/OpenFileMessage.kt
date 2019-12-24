/*
 * OpenFileMessage.kt
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

package com.avail.server.messages.binary

import com.avail.builder.ModuleName
import com.avail.server.error.ServerErrorCode
import com.avail.server.io.AvailServerChannel
import com.avail.server.io.files.FileManager
import com.avail.server.messages.Message
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import com.avail.server.AvailServer.Companion.logger
import java.util.logging.Level

/**
 * `OpenFileMessage` is a [BinaryMessage] request to open a file from the
 * [FileManager].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct an [OpenFileMessage].
 *
 * @param commandId
 *   The identifier of the [message][BinaryMessage]. This identifier should
 *   appear in any responses to this message.
 * @param buffer
 *   The [ByteBuffer] that contains the contents of the [Message] received from
 *   the client. The [ByteBuffer.position] should be at the start of the
 *   `OpenFileMessage`-specific content (past the [BinaryCommand.id] and
 *   [BinaryMessage.commandId].
 */
internal class OpenFileMessage constructor(
	override var commandId: Long, buffer: ByteBuffer): BinaryMessage()
{
	override val command = BinaryCommand.OPEN_FILE

	/**
	 * The [ModuleName] that represents the file to open. Note, this may not
	 * be a module, but instead, a resource file stored in the module root.
	 */
	private val target: ModuleName

	init
	{
		val raw = ByteArray(buffer.remaining())
		buffer.get(raw)
		val relativePath = String(raw, Charsets.UTF_8)
		assert(relativePath.isNotEmpty())
		target = ModuleName(relativePath)
	}

	override val message: Message get()
	{
		// Strictly reconstruct the message only if necessary
		val targetBytes =
			target.qualifiedName.toByteArray(StandardCharsets.UTF_8)
		val buffer = buffer(4 + targetBytes.size)
		buffer.put(targetBytes)
		buffer.flip()
		val content = ByteArray(buffer.limit())
		buffer.get(content)
		return Message(
			content, AvailServerChannel.ProtocolState.BINARY)
	}

	override fun processThen(channel: AvailServerChannel, continuation: () -> Unit)
	{
		channel.server.runtime.moduleRoots()
			.moduleRootFor(target.rootName)?.let { mr ->
				mr.sourceDirectory?.let {
					val path =
						Paths.get(it.path, target.rootRelativeName).toString()

					FileManager.readFile(
						path,
	                    { uuid, mime, bytes ->
							FileStreamMessage(commandId, uuid, mime, bytes)
								.processThen(channel)
	                    }) { code, throwable ->
							throwable?.let { e ->
								logger.log(Level.SEVERE, e) {
									"Could not read file, $path" }
							}
							channel.enqueueMessageThen(
								ErrorBinaryMessage(commandId, code).message) {}
						}
					// Request is asynchronous, so continue
					continuation()
				} ?: {
					// No source directory
					channel.enqueueMessageThen(
						ErrorBinaryMessage(
								commandId,
								ServerErrorCode.NO_SOURCE_DIRECTORY,
								false,
								"No source directory found for " +
									target.rootName
							).message,
						continuation)
				}()
			} ?: {
				// No module root found
			channel.enqueueMessageThen(
				ErrorBinaryMessage(
						commandId,
						ServerErrorCode.BAD_MODULE_ROOT,
						false,
						"${target.rootName} not found"
					).message,
				continuation)
		}()
	}
}