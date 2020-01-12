/*
 * BinaryCommand.kt
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
import com.avail.server.AvailServer
import com.avail.server.io.files.FileManager
import com.avail.server.error.ServerErrorCode
import com.avail.server.io.AvailServerChannel
import com.avail.server.io.files.EditRange
import com.avail.server.io.files.RedoAction
import com.avail.server.io.files.SaveAction
import com.avail.server.io.files.UndoAction
import com.avail.server.messages.Message
import java.lang.UnsupportedOperationException
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.*
import java.util.logging.Level

/**
 * `BinaryCommand` enumerates the set of possible commands available for use
 * over a [binary][AvailServerChannel.ProtocolState.BINARY]
 * [channel][AvailServerChannel]
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property id
 *   The integer that uniquely identifies this [BinaryCommand].
 *
 * @constructor
 * Construct a [BinaryCommand].
 *
 * @param id
 *   The integer that uniquely identifies this [BinaryCommand].
 */
enum class BinaryCommand constructor(val id: Int)
{
	/** The canonical representation of an invalid [BinaryCommand]. */
	INVALID(-1)
	{
		override fun receiveThen(
			id: Int,
			commandId: Long,
			buffer: ByteBuffer,
			channel: AvailServerChannel,
			continuation: () -> Unit)
		{
			buffer.rewind()
			val content = ByteArray(buffer.limit())
			buffer.get(content)
			InvalidBinaryMessage(commandId, id, content).processThen(channel)
		}
	},

	/** Confirmation of successful completion of a request/command. */
	OK(0)
	{
		override fun receiveThen(
			id: Int,
			commandId: Long,
			buffer: ByteBuffer,
			channel: AvailServerChannel,
			continuation: () -> Unit)
		{
			continuation()
		}
	},

	/** Indicates an [error][ServerErrorCode] has occurred. */
	ERROR(1)
	{
		override fun receiveThen(
			id: Int,
			commandId: Long,
			buffer: ByteBuffer,
			channel: AvailServerChannel,
			continuation: () -> Unit)
		{
			if (buffer.remaining() >= 4)
			{
				val errorCode = ServerErrorCode.code(buffer.int)
				val remaining = buffer.remaining()
				val description =
					if (remaining > 0)
					{
						val remainder = ByteArray(remaining)
						String(remainder, Charsets.UTF_8)
					}
					else { null }
				// TODO any special error handling?
				ErrorBinaryMessage(commandId, errorCode, false, description)
					.processThen(channel, continuation)
			}
			else
			{
				continuation()
			}
		}
	},

	/** [Create][FileManager.createFile] a new file. */
	CREATE_FILE(2)
	{
		override fun receiveThen(
			id: Int,
			commandId: Long,
			buffer: ByteBuffer,
			channel: AvailServerChannel,
			continuation: () -> Unit)
		{
			val raw = ByteArray(buffer.remaining())
			buffer.get(raw)
			val relativePath = String(raw, Charsets.UTF_8)
			assert(relativePath.isNotEmpty())
			val target = ModuleName(relativePath)
			channel.server.runtime.moduleRoots()
				.moduleRootFor(target.rootName)?.let { mr ->
					mr.sourceDirectory?.let {
						val path =
							Paths.get(it.path, target.rootRelativeName).toString()

						FileManager.createFile(
							path,
							{ uuid, mime, bytes ->
								FileOpenedMessage(commandId, uuid, bytes.size, mime)
									.processThen(channel)
									{
										FileStreamMessage(commandId, uuid, bytes)
											.processThen(channel)
									}
							}) { code, throwable ->
								throwable?.let { e ->
									AvailServer.logger.log(Level.SEVERE, e) {
										"Could not create file, $path" }
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
	},

	/** Request to open a file in the [FileManager]. */
	OPEN_FILE(3)
	{
		override fun receiveThen(
			id: Int,
			commandId: Long,
			buffer: ByteBuffer,
			channel: AvailServerChannel,
			continuation: () -> Unit)
		{
			val raw = ByteArray(buffer.remaining())
			buffer.get(raw)
			val relativePath = String(raw, Charsets.UTF_8)
			assert(relativePath.isNotEmpty())
			val target = ModuleName(relativePath)
			channel.server.runtime.moduleRoots()
				.moduleRootFor(target.rootName)?.let { mr ->
					mr.sourceDirectory?.let {
						val path =
							Paths.get(it.path, target.rootRelativeName).toString()

						FileManager.readFile(
							path,
							{ uuid, mime, bytes ->
								FileOpenedMessage(commandId, uuid, bytes.size, mime)
									.processThen(channel)
									{
										FileStreamMessage(commandId, uuid, bytes)
											.processThen(channel)
									}
							}) { code, throwable ->
								throwable?.let { e ->
									AvailServer.logger.log(Level.SEVERE, e) {
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
	},

	/** Request to close a file in the [FileManager]. */
	CLOSE_FILE(4)
	{
		override fun receiveThen(
			id: Int,
			commandId: Long,
			buffer: ByteBuffer,
			channel: AvailServerChannel,
			continuation: () -> Unit)
		{
			val mostSig = buffer.long
			val leastSig = buffer.long
			val uuid = UUID(mostSig, leastSig)
			FileManager.deregisterInterest(uuid)
			OkMessage(commandId).processThen(channel, continuation)
		}
	},

	/** Request to save a file in the [FileManager] to disk. */
	SAVE_FILE(5)
	{
		override fun receiveThen(
			id: Int,
			commandId: Long,
			buffer: ByteBuffer,
			channel: AvailServerChannel,
			continuation: () -> Unit)
		{
			val mostSig = buffer.long
			val leastSig = buffer.long
			val uuid = UUID(mostSig, leastSig)
			val fail: (ServerErrorCode, Throwable?) -> Unit =
				{ code, e ->
					ErrorBinaryMessage(commandId, code, false)
						.processThen(channel)
				}
			FileManager.executeAction(uuid, SaveAction(fail), fail)
			continuation()
		}
	},

	/** Response to open a file in the [FileManager]. */
	FILE_OPENED(6),

	/** Stream the contents of a file to the client. */
	FILE_STREAM(7),

	/** An [EditRange] request. */
	EDIT_FILE_RANGE(8)
	{
		/**
		 * Process the
		 * @param id
		 *   The [BinaryCommand.id].
		 * @param commandId
		 *   The identifier of the [message][BinaryMessage]. This identifier should
		 *   appear in any responses to this message.
		 * @param buffer
		 *   The [ByteBuffer] that contains the [Message].
		 * @param channel
		 *   The channel that is associated with this message.
		 * @param continuation
		 *   What to do when sufficient processing has occurred.
		 */
		override fun receiveThen(
			id: Int,
			commandId: Long,
			buffer: ByteBuffer,
			channel: AvailServerChannel,
			continuation: () -> Unit)
		{
			val mostSig = buffer.long
			val leastSig = buffer.long
			val uuid = UUID(mostSig, leastSig)
			val start = buffer.int
			val end = buffer.int
			val data = ByteArray(buffer.remaining())
			buffer.get(data)
			val edit = EditRange(data, start, end)
			FileManager.executeAction(uuid, edit) { code, e ->
				ErrorBinaryMessage(commandId, code, false)
					.processThen(channel)
			}
			continuation()
		}
	},

	/** An [UndoAction] request. */
	UNDO_FILE_EDIT(9)
	{
		override fun receiveThen(
			id: Int,
			commandId: Long,
			buffer: ByteBuffer,
			channel: AvailServerChannel,
			continuation: () -> Unit)
		{
			val mostSig = buffer.long
			val leastSig = buffer.long
			val uuid = UUID(mostSig, leastSig)
			FileManager.executeAction(uuid, UndoAction) { code, e ->
				ErrorBinaryMessage(commandId, code, false)
					.processThen(channel)
			}
			continuation()
		}
	},

	/** A [RedoAction] request. */
	REDO_FILE_EDIT(10)
	{
		override fun receiveThen(
			id: Int,
			commandId: Long,
			buffer: ByteBuffer,
			channel: AvailServerChannel,
			continuation: () -> Unit)
		{
			val mostSig = buffer.long
			val leastSig = buffer.long
			val uuid = UUID(mostSig, leastSig)
			FileManager.executeAction(uuid, RedoAction) { code, e ->
				ErrorBinaryMessage(commandId, code, false)
					.processThen(channel)
			}
			continuation()
		}
	},

	/** A request to delete a file. */
	DELETE_FILE(11)
	{
		override fun receiveThen(
			id: Int,
			commandId: Long,
			buffer: ByteBuffer,
			channel: AvailServerChannel,
			continuation: () -> Unit)
		{
			val raw = ByteArray(buffer.remaining())
			buffer.get(raw)
			val relativePath = String(raw, Charsets.UTF_8)
			assert(relativePath.isNotEmpty())
			val target = ModuleName(relativePath)
			channel.server.runtime.moduleRoots()
				.moduleRootFor(target.rootName)?.let { mr ->
					mr.sourceDirectory?.let {
						val path =
							Paths.get(it.path, target.rootRelativeName).toString()

						FileManager.delete(
							path,
							{
								OkMessage(commandId)
									.processThen(channel, continuation)
							}) { code, throwable ->
								throwable?.let { e ->
									AvailServer.logger.log(Level.SEVERE, e) {
										"Could not delete file, $path" }
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
	};

	/**
	 * Process this [binary message][BinaryMessage] on behalf of the specified
	 * [channel][AvailServerChannel].
	 *
	 * @param id
	 *   The [BinaryCommand.id].
	 * @param commandId
	 *   The identifier of the [message][BinaryMessage]. This identifier should
	 *   appear in any responses to this message.
	 * @param buffer
	 *   The [ByteBuffer] that contains the [Message].
	 * @param channel
	 *   The channel that is associated with this message.
	 * @param continuation
	 *   What to do when sufficient processing has occurred.
	 */
	open fun receiveThen (
		id: Int,
		commandId: Long,
		buffer: ByteBuffer,
		channel: AvailServerChannel,
		continuation: ()->Unit)
	{
		throw UnsupportedOperationException("$name does not support receiveThen")
	}

	companion object
	{
		/**
		 * Answer the [BinaryCommand] for the provided [BinaryCommand.id].
		 *
		 * @param id
		 *   The integer value used to identify the `BinaryCommand`.
		 * @return
		 *   The associated `BinaryCommand` or [BinaryCommand.INVALID] if the
		 *   id is not found.
		 */
		fun command (id: Int): BinaryCommand =
			when(id)
			{
				0 -> OK
				1 -> ERROR
				2 -> CREATE_FILE
				3 -> OPEN_FILE
				4 -> CLOSE_FILE
				5 -> SAVE_FILE
				6 -> FILE_OPENED
				7 -> FILE_STREAM
				8 -> EDIT_FILE_RANGE
				9 -> UNDO_FILE_EDIT
				10 -> REDO_FILE_EDIT
				11 -> DELETE_FILE
				else -> INVALID
			}
	}
}