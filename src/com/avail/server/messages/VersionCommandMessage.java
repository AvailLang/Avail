/*
 * VersionCommandMessage.java
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

import com.avail.server.io.AvailServerChannel;
import com.avail.utility.evaluation.Continuation0;

/**
 * A {@code VersionCommandMessage} represents a {@link Command#VERSION
 * VERSION} {@linkplain Command command}, and carries the requested protocol
 * version.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class VersionCommandMessage
extends CommandMessage
{
	/** The requested protocol version. */
	private final int version;

	/**
	 * Answer the requested protocol version.
	 *
	 * @return The requested protocol version.
	 */
	public int version ()
	{
		return version;
	}

	/**
	 * Construct a new {@link VersionCommandMessage}.
	 *
	 * @param version
	 *        The requested protocol version.
	 */
	public VersionCommandMessage (final int version)
	{
		this.version = version;
	}

	@Override
	public Command command ()
	{
		return Command.VERSION;
	}

	@Override
	public void processThen (
		final AvailServerChannel channel,
		final Continuation0 continuation)
	{
		channel.server().negotiateVersionThen(
			channel, this, continuation);
	}
}
