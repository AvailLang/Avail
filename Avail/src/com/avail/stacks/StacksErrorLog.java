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
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import com.avail.AvailRuntime;
import com.avail.annotations.InnerAccess;
import com.avail.utility.Mutable;

/**
 * A Stacks log file that contains errors from processing comments in Avail
 * modules.
 *
 * @author Richard A Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksErrorLog extends AbstractStacksOutputFile
{
	/**
	 * The error log file for the malformed comments.
	 */
	@InnerAccess AsynchronousFileChannel errorLog;

	/**
	 * The amount of errors listed in the file
	 */
	private int errorCount;

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
	 * @param outputPath
	 *        The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for documentation and
	 *        data files.
	 * @param synchronizer
	 * 		The {@linkplain StacksSynchronizer} used to control the creation
	 * 		of Stacks documentation
	 */
	public StacksErrorLog (final Path outputPath,
		final StacksSynchronizer synchronizer)
	{
		super(outputPath, synchronizer);
		this.errorFilePosition = 0;
		this.errorCount = 0;



		final Path errorLogPath = outputPath.resolve("errorlog.html");
		try
		{
			Files.createDirectories(outputPath);
		}
		catch (final IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try
		{
			this.errorLog = AvailRuntime.current().openFile(
				errorLogPath, EnumSet.of(StandardOpenOption.CREATE,
					StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING));
		}
		catch (IllegalArgumentException | UnsupportedOperationException
			| SecurityException | IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		final ByteBuffer openHTML = ByteBuffer.wrap(
			("<!DOCTYPE html>\n<head><meta charset=\"UTF-8\"><style>h3 "
			+ "{text-decoration:underline;}\n "
			+ "strong, em {color:blue;}</style>\n"
			+ "</head>\n<body>\n")
			.getBytes(StandardCharsets.UTF_8));
		addLogEntry(openHTML,0);
	}

	/**
	 * Add a new error log entry to the error error log.
	 * @param buffer
	 * 		The error log buffer
	 * @param addToErrorCount
	 * 		The amount of errors added with this log update.
	 */
	public synchronized void addLogEntry(final ByteBuffer buffer,
		final int addToErrorCount)
	{
		errorCount += addToErrorCount;
		final Mutable<Long> position = new Mutable<Long>(errorFilePosition);
		errorFilePosition += buffer.limit();

		errorLog.write(
			buffer,
			position.value,
			null,
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (final Integer bytesWritten, final Void unused)
				{
					if (buffer.hasRemaining())
					{
						position.value += bytesWritten;
						errorLog.write(buffer, position.value, null, this);
					}
					else
					{
						synchronizer.decrementWorkCounter();
					}
				}

				@Override
				public void failed (final Throwable exc, final Void unused)
				{
					// Log something?
					synchronizer.decrementWorkCounter();
				}
			});
	}

	/**
	 * @return the errorCount
	 */
	public int errorCount ()
	{
		return errorCount;
	}
}
