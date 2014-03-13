/**
 * StacksOutputFile.java
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
 * The way a file is created.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksOutputFile extends AbstractStacksOutputFile
{
	/**
	 * The error log file for the malformed comments.
	 */
	private AsynchronousFileChannel outputFile;

	/**
	 * File position tracker for error log
	 */
	private long errorFilePosition;

	/**
	 * @return the errorFilePosition
	 */
	public AsynchronousFileChannel file ()
	{
		return outputFile;
	}

	/**
	 * Add a new error log entry to the error error log.
	 * @param buffer
	 * 		The error log buffer
	 * @param addToErrorCount
	 * 		The amount of errors added with this log update.
	 */
	@Override
	public synchronized void addLogEntry(final ByteBuffer buffer,
		final int addToErrorCount)
	{
		final long position = errorFilePosition;
		errorFilePosition += buffer.limit();
		outputFile.write(buffer, position);
	}

	/**
	 * Construct a new {@link StacksOutputFile}.
	 *
	 * @param outputPath
	 *        The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for documentation and
	 *        data files.
	 * @param fileName
	 * 		The name of the new file
	 * @param outputText
	 * 		The text to be written to the file.
	 */
	public StacksOutputFile (final Path outputPath, final String fileName,
		final String outputText)
	{
		super(outputPath);

		this.errorFilePosition = 0;
		try
		{
			final Path errorLogPath = outputPath.resolve(fileName);
			Files.createDirectories(outputPath);
			this.outputFile = AsynchronousFileChannel.open(
				errorLogPath,
				StandardOpenOption.CREATE,
				StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING);

			final ByteBuffer openHTML = ByteBuffer.wrap(
				(outputText.getBytes(StandardCharsets.UTF_8)));
			addLogEntry(openHTML,0);
		}
		catch (final IOException e)
		{
			e.printStackTrace();
		}
	}
}
