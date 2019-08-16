/*
 * LoadModuleCommandMessage.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.builder.ModuleName;
import com.avail.descriptor.A_Module;
import com.avail.server.io.AvailServerChannel;
import com.avail.utility.evaluation.Continuation0;

/**
 * A {@code LoadModuleCommandMessage} represents a {@link Command#LOAD_MODULE
 * LOAD_MODULE} {@linkplain Command command}, and carries the {@linkplain
 * ModuleName name} of the target {@linkplain A_Module module}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class LoadModuleCommandMessage
extends CommandMessage
{
	/**
	 * The {@linkplain ModuleName name} of the target {@linkplain A_Module
	 * module}.
	 */
	private final ModuleName target;

	/**
	 * Answer the {@linkplain ModuleName name} of the target {@linkplain
	 * A_Module module}.
	 *
	 * @return The target module.
	 */
	public ModuleName target ()
	{
		return target;
	}

	/**
	 * Construct a new {@link LoadModuleCommandMessage}.
	 *
	 * @param target
	 *        The {@linkplain ModuleName name} of the target {@linkplain
	 *        A_Module module}.
	 */
	public LoadModuleCommandMessage (final ModuleName target)
	{
		this.target = target;
	}

	@Override
	public Command command ()
	{
		return Command.LOAD_MODULE;
	}

	@Override
	public void processThen (
		final AvailServerChannel channel,
		final Continuation0 continuation)
	{
		channel.server().requestUpgradesForLoadModuleThen(
			channel, this, continuation);
	}
}
