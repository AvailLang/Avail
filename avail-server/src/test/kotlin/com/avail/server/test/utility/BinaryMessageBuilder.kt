/*
 * BinaryMessageBuilder.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation
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

package com.avail.server.test.utility

import com.avail.builder.ModuleRoot
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
	private fun encodeMessage (
		command: BinaryCommand, content: ByteArray): Message
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

	/** Encode the message. */
	private fun encodeFileIdMessage (
		command: BinaryCommand, fileId: Int, content: ByteArray): Message
	{
		val size = BinaryMessage.PREFIX_SIZE + content.size + 4
		val raw = ByteArray(size)
		ByteBuffer.allocate(size).apply {
			this.putInt(command.id)
				.putLong(transactionId.getAndIncrement())
				.putInt(fileId)
				.put(content)
				.flip()
		}.get(raw)

		return  Message(raw, AvailServerChannel.ProtocolState.BINARY)
	}

	/** Encode the message. */
	private fun encodeFileIdMessage (
		command: BinaryCommand, fileId: Int): Message
	{
		val size = BinaryMessage.PREFIX_SIZE + 4
		val raw = ByteArray(size)
		ByteBuffer.allocate(size).apply {
			this.putInt(command.id)
				.putLong(transactionId.getAndIncrement())
				.putInt(fileId)
				.flip()
		}.get(raw)

		return  Message(raw, AvailServerChannel.ProtocolState.BINARY)
	}

	/**
	 * Create a [BinaryCommand.OPEN_FILE] message.
	 *
	 * @param relativePath
	 *   The [ModuleRoot] relative path of the file to open.
	 */
	fun openFile (relativePath: String): Message =
		encodeMessage(
			BinaryCommand.OPEN_FILE,
			relativePath.toByteArray(Charsets.UTF_8))

	/**
	 * Create a [BinaryCommand.CREATE_FILE] message.
	 *
	 * @param relativePath
	 *   The [ModuleRoot] relative path of the file to open.
	 */
	fun createFile (relativePath: String): Message =
		encodeMessage(
			BinaryCommand.CREATE_FILE,
			relativePath.toByteArray(Charsets.UTF_8))

	/**
	 * Create a [BinaryCommand.REPLACE_CONTENTS] message.
	 *
	 * @param fileId
	 *   The session-specific file cache id.
	 * @param replace
	 *   The full text to replace the file with.
	 */
	fun replaceFile (fileId: Int, replace: String): Message =
		encodeFileIdMessage(
			BinaryCommand.REPLACE_CONTENTS,
			fileId,
			replace.toByteArray(Charsets.UTF_16BE))

	/**
	 * Create a [BinaryCommand.REPLACE_CONTENTS] message.
	 *
	 * @param fileId
	 *   The session-specific file cache id.
	 * @param start
	 *   The location in the file to insert/overwrite the data.
	 * @param end
	 *   The location in the file to stop overwriting, exclusive. All data from
	 *   this point should be preserved.
	 * @param edit
	 *   The text to insert.
	 */
	fun editFile (
		fileId: Int,
		start: Int,
		end: Int,
		edit: String): Message
	{
		val content = edit.toByteArray(Charsets.UTF_16BE)
		val size = BinaryMessage.PREFIX_SIZE + content.size + 12
		val raw = ByteArray(size)
		ByteBuffer.allocate(size).apply {
			this.putInt(BinaryCommand.EDIT_FILE_RANGE.id)
				.putLong(transactionId.getAndIncrement())
				.putInt(fileId)
				.putInt(start)
				.putInt(end)
				.put(content)
				.flip()
		}.get(raw)

		return  Message(raw, AvailServerChannel.ProtocolState.BINARY)
	}

	/**
	 * Create a [BinaryCommand.SAVE_FILE] message.
	 *
	 * @param fileId
	 *   The session-specific file cache id.
	 */
	fun saveFile (fileId: Int): Message =
		encodeFileIdMessage(BinaryCommand.SAVE_FILE, fileId)

	/**
	 * Create a [BinaryCommand.CLOSE_FILE] message.
	 *
	 * @param fileId
	 *   The session-specific file cache id.
	 */
	fun closeFile (fileId: Int): Message =
		encodeFileIdMessage(BinaryCommand.CLOSE_FILE, fileId)

	/**
	 * Create a [BinaryCommand.UNDO_FILE_EDIT] message.
	 *
	 * @param fileId
	 *   The session-specific file cache id.
	 */
	fun undoFile (fileId: Int): Message =
		encodeFileIdMessage(BinaryCommand.UNDO_FILE_EDIT, fileId)

	/**
	 * Create a [BinaryCommand.REDO_FILE_EDIT] message.
	 *
	 * @param fileId
	 *   The session-specific file cache id.
	 */
	fun redoFile (fileId: Int): Message =
		encodeFileIdMessage(BinaryCommand.REDO_FILE_EDIT, fileId)

	/**
	 * Create a [BinaryCommand.DELETE_FILE] message.
	 *
	 * @param relativePath
	 *   The [ModuleRoot] relative path of the file to open.
	 */
	fun deleteFile (relativePath: String): Message =
		encodeMessage(
			BinaryCommand.DELETE_FILE,
			relativePath.toByteArray(Charsets.UTF_8))

}