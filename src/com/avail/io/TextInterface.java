/**
 * TextInterface.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.io;

import com.avail.builder.AvailBuilder;
import com.avail.descriptor.A_Fiber;

import java.nio.charset.StandardCharsets;

/**
 * A {@code TextInterface} represents an interface between an external process,
 * device, or user and an Avail agent (e.g., an {@link AvailBuilder} or
 * {@linkplain A_Fiber fiber}). As such, it combines {@linkplain
 * TextInputChannel input}, {@linkplain TextOutputChannel output}, and
 * error channels, corresponding to the usual notions of standard input, output,
 * and error, respectively. These channels are each text-oriented, and
 * constrained to operate on {@linkplain StandardCharsets#UTF_8 UTF-8} encoded
 * character data.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class TextInterface
{
	/** The {@linkplain TextInputChannel standard input channel}. */
	private final TextInputChannel inputChannel;

	/**
	 * Answer the {@linkplain TextInputChannel standard input channel}.
	 *
	 * @return The standard input channel.
	 */
	public TextInputChannel inputChannel ()
	{
		return inputChannel;
	}

	/** The {@linkplain TextOutputChannel standard output channel}. */
	private final TextOutputChannel outputChannel;

	/**
	 * Answer the {@linkplain TextOutputChannel standard output channel}.
	 *
	 * @return The standard output channel.
	 */
	public TextOutputChannel outputChannel ()
	{
		return outputChannel;
	}

	/** The {@linkplain TextOutputChannel standard error channel}. */
	private final TextOutputChannel errorChannel;

	/**
	 * Answer the {@linkplain TextOutputChannel standard output channel}.
	 *
	 * @return The standard output channel.
	 */
	public TextOutputChannel errorChannel ()
	{
		return errorChannel;
	}

	/**
	 * Construct a new {@link TextInterface}.
	 *
	 * @param inputChannel
	 *        The {@linkplain TextInputChannel standard input channel}.
	 * @param outputChannel
	 *        The {@linkplain TextOutputChannel standard output channel}.
	 * @param errorChannel
	 *        The standard error channel.
	 */
	public TextInterface (
		final TextInputChannel inputChannel,
		final TextOutputChannel outputChannel,
		final TextOutputChannel errorChannel)
	{
		this.inputChannel = inputChannel;
		this.outputChannel = outputChannel;
		this.errorChannel = errorChannel;
	}

	/**
	 * Answer a {@link TextInterface} bound to the {@linkplain System}
	 * {@linkplain System#in input}, {@linkplain System#out output}, and
	 * {@linkplain System#err error} channels.
	 *
	 * @return A text interface suitable for managing the system streams.
	 */
	public static TextInterface system ()
	{
		return new TextInterface(
			new ConsoleInputChannel(System.in),
			new ConsoleOutputChannel(System.out),
			new ConsoleOutputChannel(System.err));
	}
}
