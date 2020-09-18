/*
 * TestMessageHolder.kt
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

import com.avail.server.error.ServerErrorCode
import com.avail.server.messages.Message
import com.avail.server.messages.binary.editor.BinaryCommand
import java.nio.ByteBuffer

/**
 * A `TestBinaryMessageHolder` contains a binary message and performs basic
 * message parsing.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class TestBinaryMessageHolder constructor(val message: Message)
{
	/** [ByteBuffer] wrapping the [Message.content]. */
	val buffer = ByteBuffer.wrap(message.content)

	/** The [BinaryCommand.id]. */
	val binaryCommandId = buffer.int

	/** The associated [BinaryCommand]. */
	val binaryCommand = BinaryCommand.command(binaryCommandId)

	/** The [Message] command id that identifies the transaction exchange. */
	val commandId = buffer.long

	override fun toString(): String
	{
		return if (binaryCommand == BinaryCommand.ERROR)
		{
			val position = buffer.position()
			val ec = buffer.int
			buffer.position(position)
			ServerErrorCode.code(ec).name
			"${binaryCommand.name}: ${ServerErrorCode.code(ec).name}"
		}
		else
		{
			binaryCommand.name
		}
	}
}