/**
 * SimpleCommandMessage.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.server.messages;

import com.avail.server.AvailServer;
import com.avail.server.io.AvailServerChannel;
import com.avail.utility.evaluation.Continuation0;

/**
 * A {@code SimpleCommandMessage} contains no state beyond the style of
 * {@linkplain Command command}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class SimpleCommandMessage
extends CommandMessage
{
	/** The {@linkplain Command command}. */
	private final Command command;

	@Override
	public Command command ()
	{
		return command;
	}

	/**
	 * Construct a new {@link SimpleCommandMessage}.
	 *
	 * @param command
	 *        The {@linkplain Command command}.
	 */
	SimpleCommandMessage (final Command command)
	{
		this.command = command;
	}

	@Override
	public void processThen (
		final AvailServerChannel channel,
		final Continuation0 continuation)
	{
		final AvailServer server = channel.server();
		switch (command)
		{
			case COMMANDS:
				server.commandsThen(channel, this, continuation);
				break;
			case MODULE_ROOTS:
				server.moduleRootsThen(channel, this, continuation);
				break;
			case MODULE_ROOT_PATHS:
				server.moduleRootPathsThen(channel, this, continuation);
				break;
			case MODULE_ROOTS_PATH:
				server.moduleRootsPathThen(channel, this, continuation);
				break;
			case SOURCE_MODULES:
				server.sourceModulesThen(channel, this, continuation);
				break;
			case ENTRY_POINTS:
				server.entryPointsThen(channel, this, continuation);
				break;
			case CLEAR_REPOSITORIES:
				server.clearRepositoriesThen(channel, this, continuation);
				break;
			case UNLOAD_ALL_MODULES:
				server.requestUpgradesForUnloadAllModulesThen(
					channel, this, continuation);
				break;
			case ALL_FIBERS:
				server.allFibersThen(channel, this, continuation);
				break;
			case VERSION:
			case UPGRADE:
			case LOAD_MODULE:
			case UNLOAD_MODULE:
			case RUN_ENTRY_POINT:
				assert false : "This command should not be dispatched here!";
				break;
		}
	}
}
