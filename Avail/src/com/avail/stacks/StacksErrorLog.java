/**
 * StacksErrorLog.java
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

package com.avail.stacks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * A Stacks log file that contains errors from processing comments in Avail
 * modules.
 *
 * @author Richard A Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksErrorLog
{
	/**
	 * The error log file for the malformed comments.
	 */
	private AsynchronousFileChannel errorLog;

	/**
	 * File position tracker for error log
	 */
	private long errorFilePosition;

	/**
	 * @return the errorFilePosition
	 */
	public AsynchronousFileChannel file ()
	{
		return errorLog;
	}

	/**
	 * Construct a new {@link StacksErrorLog}.
	 * @param logPath
	 *        The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for documentation and
	 *        data files.
	 *
	 */
	public StacksErrorLog (final Path logPath)
	{
		this.errorFilePosition = 0;
		try
		{
			final Path errorLogPath = logPath.resolve("errorlog.html");
			Files.createDirectories(logPath);
			this.errorLog = AsynchronousFileChannel.open(
				errorLogPath,
				StandardOpenOption.CREATE,
				StandardOpenOption.WRITE);
			final ByteBuffer openHTML = ByteBuffer.wrap(
				"<!DOCTYPE html><html><body><ol>"
					.getBytes(StandardCharsets.UTF_8));
			addLogEntry(openHTML);
		}
		catch (final IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * @param buffer
	 */
	public synchronized void addLogEntry(final ByteBuffer buffer)
	{
		final long position = errorFilePosition;
		errorFilePosition += buffer.limit();
		errorLog.write(buffer, position);
	}

}
