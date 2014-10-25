/**
 * RunEntryPointCommandMessage.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
 * A {@code RunEntryPointCommandMessage} represents a {@link
 * Command#RUN_ENTRY_POINT RUN_ENTRY_POINT} {@linkplain Command command}, and
 * carries the Avail command (i.e, entry point expression) that should be
 * executed.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class RunEntryPointCommandMessage
extends CommandMessage
{
	/** The command expression. */
	private final String expression;

	/**
	 * Answer the command expression.
	 *
	 * @return The command expression.
	 */
	public String expression ()
	{
		return expression;
	}

	/**
	 * Construct a new {@link RunEntryPointCommandMessage}.
	 *
	 * @param expression
	 *        The command expression.
	 */
	public RunEntryPointCommandMessage (final String expression)
	{
		this.expression = expression;
	}

	@Override
	public Command command ()
	{
		return Command.RUN_ENTRY_POINT;
	}

	@Override
	public void processThen (
		final AvailServerChannel channel,
		final Continuation0 continuation)
	{
		channel.server().requestUpgradesForRunThen(
			channel, this, continuation);
	}
}
