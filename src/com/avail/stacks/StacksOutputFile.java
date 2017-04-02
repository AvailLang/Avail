/**
 * StacksOutputFile.java
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
import org.jetbrains.annotations.Nullable;
import com.avail.utility.IO;
import com.avail.utility.Mutable;

/**
 * The way a file is created.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksOutputFile
{
	/**
	 * The {@linkplain Path path} to the output {@linkplain
	 * BasicFileAttributes#isDirectory() directory} for documentation and
	 * data files.
	 */
	final Path outputPath;

	/**
	 * The {@linkplain StacksSynchronizer} used to control the creation
	 * of Stacks documentation.
	 */
	final StacksSynchronizer synchronizer;

	/**
	 * The error log file for the malformed comments.
	 */
	@InnerAccess AsynchronousFileChannel outputFile;

	/**
	 * The exported name of the Method/Class/Global this file represents.
	 */
	final String name;

	/**
	 * @return the errorFilePosition
	 */
	public AsynchronousFileChannel file ()
	{
		return outputFile;
	}

	/**
	 * Write text to a file.
	 *
	 * @param outputText
	 *        The text to be written to file.
	 */
	public synchronized void write(final String outputText)
	{
		final ByteBuffer buffer = ByteBuffer.wrap(
			(outputText.getBytes(StandardCharsets.UTF_8)));
		final Mutable<Long> pos = new Mutable<Long>(0L);
		outputFile.write(
			buffer,
			pos.value,
			null,
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (
					final @Nullable Integer bytesWritten,
					final @Nullable Void unused)
				{
					if (buffer.hasRemaining())
					{
						pos.value += bytesWritten;
						outputFile.write(buffer, pos.value, null, this);
					}
					else
					{
						IO.close(outputFile);
						synchronizer.decrementWorkCounter();
					}
				}

				@Override
				public void failed (
					final @Nullable Throwable exc,
					final @Nullable Void unused)
				{
					// Log something?
					IO.close(outputFile);
					synchronizer.decrementWorkCounter();
				}
			});
	}

	/**
	 * Construct a new {@link StacksOutputFile}.
	 *
	 * @param outputPath
	 *        The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for documentation and
	 *        data files.
	 * @param fileName
	 *        The name of the new file
	 * @param synchronizer
	 *        The {@linkplain StacksSynchronizer} used to control the creation
	 *        of Stacks documentation
	 * @param runtime
	 *        An {@linkplain AvailRuntime runtime}.
	 * @param name
	 *        The name of the method the file represents as it is represented
	 *        from the point of view of the main module being documented.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	public StacksOutputFile (
			final Path outputPath,
			final StacksSynchronizer synchronizer,
			final String fileName,
			final AvailRuntime runtime,
			final String name)
		throws IOException
	{
		this.outputPath = outputPath;
		this.synchronizer = synchronizer;
		this.name = name;

		final Path filePath = outputPath.resolve(fileName);
		Files.createDirectories(outputPath);
		try
		{
			this.outputFile = runtime.openFile(
				filePath, EnumSet.of(StandardOpenOption.CREATE,
					StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING));
		}
		catch (
			IllegalArgumentException
			| UnsupportedOperationException
			| SecurityException
			| IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
