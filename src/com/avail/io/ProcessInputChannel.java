/**
 * ProcessInputChannel.java
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

package com.avail.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import com.avail.AvailRuntime;
import com.avail.AvailTask;
import com.avail.annotations.InnerAccess;
import com.avail.annotations.Nullable;
import com.avail.descriptor.A_Fiber;
import com.avail.utility.evaluation.Continuation0;

/**
 * A {@code ProcessInputChannel} provides a faux {@linkplain
 * TextInputChannel asynchronous interface} to a synchronous {@linkplain Process
 * process} {@linkplain InputStream input stream}. The reader must supply
 * {@linkplain StandardCharsets#UTF_8 UTF-8} encoded characters.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class ProcessInputChannel
implements TextInputChannel
{
	/** The wrapped {@linkplain Reader reader}. */
	@InnerAccess final Reader in;

	/**
	 * Construct a new {@link ProcessInputChannel} that wraps the specified
	 * {@linkplain InputStream stream}.
	 *
	 * @param stream
	 *        An input stream. This should generally be {@link System#in}.
	 */
	public ProcessInputChannel (final InputStream stream)
	{
		final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
		decoder.onMalformedInput(CodingErrorAction.REPLACE);
		decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
		in = new BufferedReader(new InputStreamReader(stream, decoder));
	}

	@Override
	public boolean isOpen ()
	{
		// The standard input stream is always open; we do not permit it to be
		// closed, at any rate.
		return true;
	}

	@Override
	public <A> void read (
		final CharBuffer buffer,
		final @Nullable A attachment,
		final CompletionHandler<Integer, A> handler)
	{
		final AvailRuntime runtime = AvailRuntime.current();
		final A_Fiber fiber = (A_Fiber) attachment;
		runtime.executeFileTask(AvailTask.forUnboundFiber(
			fiber,
			new Continuation0()
			{
				@Override
				public void value ()
				{
					final int charsRead;
					try
					{
						charsRead = in.read(buffer);
						if (charsRead == -1)
						{
							throw new IOException("end of stream");
						}
					}
					catch (final IOException e)
					{
						handler.failed(e, attachment);
						return;
					}
					handler.completed(charsRead, attachment);
				}
			}));
	}

	@Override
	public void mark (final int readAhead) throws IOException
	{
		in.mark(readAhead);
	}

	@Override
	public void reset () throws IOException
	{
		in.reset();
	}

	@Override
	public void close ()
	{
		// Do nothing. Definitely don't close the underlying input stream.
	}
}
