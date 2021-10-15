/*
 * SimpleCommandMessage.kt
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

package avail.server.messages

import avail.server.AvailServer.Companion.commandsThen
import avail.server.io.AvailServerChannel
import avail.server.messages.TextCommand.*

/**
 * A `SimpleCommandMessage` contains no state beyond the style of
 * [command][TextCommand].
 *
 * @property command
 *   The [command][TextCommand].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `SimpleCommandMessage`.
 *
 * @param command
 *   The [command][TextCommand].
 */
class SimpleCommandMessage internal constructor(override val command: TextCommand)
	: CommandMessage()
{
	override fun processThen(channel: AvailServerChannel, continuation: ()->Unit)
	{
		val server = channel.server
		when (command)
		{
			COMMANDS ->
				commandsThen(channel, this, continuation)
			MODULE_ROOTS ->
				server.moduleRootsThen(channel, this, continuation)
			MODULE_ROOT_PATHS ->
				server.moduleRootPathsThen(channel, this, continuation)
			MODULE_ROOTS_PATH ->
				server.moduleRootsPathThen(channel, this, continuation)
			SOURCE_MODULES ->
				server.sourceModulesThen(channel, this, continuation)
			ENTRY_POINTS ->
				server.entryPointsThen(channel, this, continuation)
			CLEAR_REPOSITORIES ->
				server.clearRepositoriesThen(channel, this, continuation)
			UNLOAD_ALL_MODULES ->
				server.requestUpgradesForUnloadAllModulesThen(
					channel, this, continuation)
			ALL_FIBERS ->
				server.allFibersThen(channel, this, continuation)
			OPEN_EDITOR ->
				server.requestEditorThen(channel, this, continuation)
			SUBSCRIBE_NOTIFICATIONS ->
				server.requestSubscribeNotificationsThen(
					channel, this, continuation)
			UNSUBSCRIBE_NOTIFICATIONS ->
				server.requestUnsubscribeNotificationsThen(
					channel, this, continuation)
			VERSION,
			UPGRADE,
			LOAD_MODULE,
			UNLOAD_MODULE,
			RUN_ENTRY_POINT ->
				assert(false) { "This command should not be dispatched here!" }
		}
	}
}
