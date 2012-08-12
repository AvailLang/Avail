/**
 * Repository.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.builder;

import java.io.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;

/**
 * A {@code Repository} keeps pre-compiled versions of modules.  It tracks
 * changes to files to determine which modules need to be recompiled in order to
 * load a target module and its ancestors into an {@link AvailRuntime}.
 */
public class Repository
{
	/**
	 * Information kept in memory about a compiled module, including the source
	 * file's {@linkplain #sourceLastModified modification time} and where in
	 * the repository file to find the compiled module's bytes.
	 */
	private static class CompiledModulePointer
	{
		/**
		 * The {@link File#lastModified() modification time} of the module's
		 * source file corresponding to the compiled form (i.e., what the
		 * source modification time was when the module was compiled).
		 */
		public final long sourceLastModified;

		/**
		 * The start of the compiled data within the repository's {@link
		 * Repository#moduleBodiesFile}.
		 */
		public final long bytesStart;

		/**
		 * The number of bytes occupied by the compiled data within the
		 * repository's {@link Repository#moduleBodiesFile}.
		 */
		public final int bytesSize;

		/**
		 * Construct a new {@link CompiledModulePointer}.
		 *
		 * @param sourceLastModified The source file's modification time stamp.
		 * @param bytesStart The compiled module's position in the cache file.
		 * @param bytesSize The number of bytes occupied by the compiled module.
		 */
		CompiledModulePointer (
			final long sourceLastModified,
			final long bytesStart,
			final int bytesSize)
		{
			this.sourceLastModified = sourceLastModified;
			this.bytesStart = bytesStart;
			this.bytesSize = bytesSize;
		}
	}

	/**
	 * A {@link RandomAccessFile} used to record all compiled modules.
	 */
	final RandomAccessFile moduleBodiesFile;

	/**
	 * The last position of the file occupied by meaningful data.
	 */
	long lastWritePosition = 0L;

	/**
	 * A map from each compiled module's name to the most recent {@linkplain
	 * CompiledModulePointer compiled module information} for that module.
	 */
	final Map<String, CompiledModulePointer> map =
		new HashMap<String, CompiledModulePointer>();

	/**
	 * Construct a new {@link Repository}.
	 *
	 * @param bodiesFile The file containing the compiled modules.
	 */
	public Repository (
		final File bodiesFile)
	{
		try
		{
			this.moduleBodiesFile = new RandomAccessFile(bodiesFile, "rw");
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
		clear();
	}

	/**
	 * Create a temporary {@link Repository}, with a backing file which is to be
	 * deleted on exit.
	 *
	 * @return The {@code Repository}.
	 */
	public static Repository createTemporary ()
	{
		try
		{
			final File repositoryFile = File.createTempFile("repository", null);
			repositoryFile.deleteOnExit();
			return new Repository(repositoryFile);
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Answer whether there is a valid compiled representation of the named
	 * module, given that its current {@link File#lastModified() last
	 * modification time} is as specified.
	 *
	 * @param sourceFileName The module name.
	 * @param sourceLastModified The last time the source file was modified.
	 * @return Whether there is a valid compiled form of the module.
	 */
	public synchronized boolean hasKey (
		final String sourceFileName,
		final long sourceLastModified)
	{
		final CompiledModulePointer body = map.get(sourceFileName);
		if (body != null)
		{
			if (body.sourceLastModified == sourceLastModified)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * If there is a valid compiled representation of the named module then
	 * answer it.  The source file's {@link File#lastModified() last
	 * modification time} is provided.  Answer null if there is no valid
	 * compiled representation.
	 *
	 * @param sourceFileName The module name.
	 * @param sourceLastModified The last time the source file was modified.
	 * @return Either {@code null} or a byte array representing the compiled
	 *         module.
	 */
	public synchronized @Nullable byte[] get (
		final String sourceFileName,
		final long sourceLastModified)
	{
		final CompiledModulePointer body = map.get(sourceFileName);
		if (body != null)
		{
			if (body.sourceLastModified == sourceLastModified)
			{
				try
				{
					final byte[] bytes = new byte[body.bytesSize];
					moduleBodiesFile.seek(body.bytesStart);
					final long bytesRead = moduleBodiesFile.read(bytes);
					if (bytesRead != bytes.length)
					{
						throw new RuntimeException(
							"Wrong number of bytes were read");
					}
					return bytes;
				}
				catch (final IOException e)
				{
					throw new RuntimeException(e);
				}
			}
		}
		return null;
	}

	/**
	 * Record a compiled representation of a module for subsequent loading.
	 *
	 * @param sourceFileName
	 *            The module's name.
	 * @param sourceLastModified
	 *            The last modification time of the module source.
	 * @param bytes
	 *            The compiled representation of the module as a byte array.
	 */
	public synchronized void put (
		final String sourceFileName,
		final long sourceLastModified,
		final byte[] bytes)
	{
		try
		{
			final CompiledModulePointer body = new CompiledModulePointer(
				sourceLastModified,
				lastWritePosition,
				bytes.length);
			moduleBodiesFile.seek(lastWritePosition);
			moduleBodiesFile.write(bytes);
			lastWritePosition += bytes.length;
			map.put(sourceFileName, body);
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Erase all information about previously compiled modules.
	 */
	public synchronized void clear ()
	{
		map.clear();
		lastWritePosition = 0L;
		try
		{
			final long cleanupThreshold = 100000000L;
			if (moduleBodiesFile.length() > cleanupThreshold)
			{
				moduleBodiesFile.setLength(cleanupThreshold);
			}
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
	}
}
