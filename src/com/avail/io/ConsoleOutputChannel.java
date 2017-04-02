/**
 * ConsoleOutputChannel.java
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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.CharBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.Nullable;

/**
 * A {@code ConsoleInputChannel} provides a faux {@linkplain
 * TextOutputChannel asynchronous interface} to a synchronous {@linkplain
 * PrintStream output stream}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class ConsoleOutputChannel
implements TextOutputChannel
{
	/** The wrapped {@linkplain Writer writer}. */
	private final Writer out;

	/**
	 * Construct a new {@link ConsoleOutputChannel} that wraps the specified
	 * {@linkplain PrintStream output stream}.
	 *
	 * @param stream
	 *        An output stream. This should generally be either {@link
	 *        System#out} or {@link System#err}.
	 */
	public ConsoleOutputChannel (final PrintStream stream)
	{
		final CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
		encoder.onMalformedInput(CodingErrorAction.REPLACE);
		encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
		out = new BufferedWriter(new OutputStreamWriter(stream, encoder));
	}

	@Override
	public boolean isOpen ()
	{
		// The standard output stream is always open; we do not permit it to be
		// closed, at any rate.
		return false;
	}

	@Override
	public <A> void write (
		final CharBuffer buffer,
		final @Nullable A attachment,
		final CompletionHandler<Integer, A> handler)
	{
		try
		{
			out.write(buffer.toString());
			out.flush();
		}
		catch (final IOException e)
		{
			handler.failed(e, attachment);
			return;
		}
		handler.completed(buffer.limit(), attachment);
	}

	@Override
	public <A> void write (
		final String data,
		final @Nullable A attachment,
		final CompletionHandler<Integer, A> handler)
	{
		try
		{
			out.write(data);
			out.flush();
		}
		catch (final IOException e)
		{
			handler.failed(e, attachment);
			return;
		}
		handler.completed(data.length(), attachment);
	}

	@Override
	public void close ()
	{
		// Do nothing. Definitely don't close the underlying output stream.
	}
}
