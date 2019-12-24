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

import com.apple.eio.FileManager
import com.avail.server.error.ServerErrorCode
import com.avail.server.io.AvailServerChannel
import com.avail.server.io.files.EditRange
import com.avail.server.io.files.RedoAction
import com.avail.server.io.files.UndoAction
import java.lang.UnsupportedOperationException
import java.nio.ByteBuffer

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

	CREATE_FILE(2),

	/** Request to open a file in the [FileManager]. */
	OPEN_FILE(3),

	/** Request to close a file in the [FileManager]. */
	CLOSE_FILE(4),

	/** Request to save a file in the [FileManager] to disk. */
	SAVE_FILE(5),

	/** Response to open a file in the [FileManager]. */
	FILE_OPENED(6),

	/** Indicates a file following data is indicated file. */
	FILE_STREAM(7),

	/** An [EditRange] request. */
	EDIT_FILE_RANGE(8),

	/** An [UndoAction] request. */
	UNDO_FILE_EDIT(9),

	/** A [RedoAction] request. */
	REDO_FILE_EDIT(10);

	/**
	 * Process this [binary message][BinaryMessage] on behalf of the specified
	 * [channel][AvailServerChannel].
	 *
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
				else -> INVALID
			}
	}
}