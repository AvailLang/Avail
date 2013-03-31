/**
 * IndexedRepositoryManager.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.persistence;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import com.avail.annotations.Nullable;
import com.avail.builder.*;
import com.avail.descriptor.ModuleDescriptor;

/**
 * An {@code IndexedRepositoryManager} manages a persistent {@linkplain
 * IndexedRepository indexed repository} of compiled {@linkplain
 * ModuleDescriptor modules}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class IndexedRepositoryManager
{
	/**
	 * The {@linkplain ReentrantLock lock} responsible for guarding against
	 * unsafe concurrent access.
	 */
	private final ReentrantLock lock = new ReentrantLock();

	/**
	 * The name of the {@linkplain ModuleRoot Avail root} represented by this
	 * {@linkplain IndexedRepository indexed repository}.
	 */
	private final String rootName;

	/**
	 * Answer the name of the {@linkplain ModuleRoot Avail root} represented by
	 * this {@linkplain IndexedRepository indexed repository}.
	 *
	 * @return The root name.
	 */
	public String rootName ()
	{
		return rootName;
	}

	/**
	 * The {@linkplain File filename} of the {@linkplain IndexedRepository
	 * indexed repository}.
	 */
	private final File fileName;

	/**
	 * Answer the {@linkplain File filename} of the {@linkplain
	 * IndexedRepository indexed repository}.
	 *
	 * @return The repository's location.
	 */
	public File fileName ()
	{
		return fileName;
	}

	/**
	 * The {@linkplain IndexedRepository repository} that stores this
	 * {@linkplain IndexedRepositoryManager manager}'s compiled {@linkplain
	 * ModuleDescriptor modules}.
	 */
	private @Nullable IndexedRepository repository;

	/**
	 * Answer the {@linkplain IndexedRepository repository} that stores this
	 * {@linkplain IndexedRepositoryManager manager}'s compiled {@linkplain
	 * ModuleDescriptor modules}.
	 *
	 * @return The repository.
	 */
	private IndexedRepository repository ()
	{
		final IndexedRepository repo = repository;
		assert repo != null;
		return repo;
	}

	/**
	 * Information kept in memory about a compiled {@linkplain ModuleDescriptor
	 * module}.
	 */
	private static class ModuleSummary
	{
		/**
		 * Is the {@linkplain ModuleDescriptor module} a package representative?
		 */
		public boolean isPackage;

		/**
		 * The {@link File#lastModified() modification time} of the module's
		 * source file corresponding to the compiled form (i.e., what the
		 * source modification time was when the module was compiled).
		 */
		public final long sourceLastModified;

		/**
		 * The size of the compiled {@linkplain ModuleDescriptor module}, in
		 * bytes.
		 */
		public final long moduleSize;

		/**
		 * The persistent record number of this version of the compiled
		 * {@linkplain ModuleDescriptor module}.
		 */
		public final long index;

		/**
		 * Construct a new {@link ModuleSummary}.
		 *
		 * @param isPackage
		 *        Is the {@linkplain ModuleDescriptor module} a package
		 *        representative?
		 * @param sourceLastModified
		 *        The source file's modification time stamp.
		 * @param moduleSize
		 *        The size of the compiled module, in bytes.
		 * @param index
		 *        The persistent record number.
		 */
		ModuleSummary (
			final boolean isPackage,
			final long sourceLastModified,
			final long moduleSize,
			final long index)
		{
			this.isPackage = isPackage;
			this.sourceLastModified = sourceLastModified;
			this.moduleSize = moduleSize;
			this.index = index;
		}
	}

	/**
	 * A map from {@linkplain ModuleName module name} {@linkplain
	 * ModuleName#rootRelativeName() root-relative name} to {@linkplain
	 * ModuleSummary compiled module summary}.
	 */
	private final Map<String, ModuleSummary> moduleMap =
		new HashMap<String, ModuleSummary>(100);

	/**
	 * Clear the underlying {@linkplain IndexedRepository repository} and
	 * discard any cached data. Set up the repository for subsequent usage.
	 *
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 * @throws IndexedFileException
	 *         If any other {@linkplain Exception exception} occurs.
	 */
	public void clear () throws IOException, IndexedFileException
	{
		lock.lock();
		try
		{
			moduleMap.clear();
			final IndexedRepository repo = repository();
			repo.close();
			repository = null;
			final RandomAccessFile file = new RandomAccessFile(fileName, "rw");
			try
			{
				file.setLength(0L);
			}
			finally
			{
				file.close();
			}
			try
			{
				repository = IndexedFile.newFile(
					IndexedRepository.class,
					fileName,
					null);
			}
			catch (final Exception e)
			{
				throw new IndexedFileException(e);
			}
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Answer whether there is a compiled {@linkplain ModuleDescriptor module}
	 * for the specified {@linkplain ModuleName source module}.
	 *
	 * @param name
	 *        The name of the source module.
	 * @return {@code true} if there is already a valid compiled module
	 *         available, or {@code false} otherwise.
	 */
	public boolean hasKey (final ModuleName name)
	{
		if (!name.rootName().equals(rootName))
		{
			return false;
		}
		lock.lock();
		try
		{
			final ModuleSummary summary =
				moduleMap.get(name.rootRelativeName());
			if (summary != null)
			{
				return true;
			}
			return false;
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Answer whether there is a compiled {@linkplain ModuleDescriptor module}
	 * for the specified {@linkplain ModuleName source module} and last
	 * modification time.
	 *
	 * @param name
	 *        The name of the source module.
	 * @param sourceLastModified
	 *        The last modification time of the source module.
	 * @return {@code true} if there is already a valid compiled module
	 *         available, or {@code false} otherwise.
	 */
	public boolean hasKey (
		final ModuleName name,
		final long sourceLastModified)
	{
		if (!name.rootName().equals(rootName))
		{
			return false;
		}
		lock.lock();
		try
		{
			final ModuleSummary summary =
				moduleMap.get(name.rootRelativeName());
			if (summary != null)
			{
				if (summary.sourceLastModified == sourceLastModified)
				{
					return true;
				}
			}
			return false;
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Is the specified {@linkplain ModuleDescriptor module} a package
	 * representative?
	 *
	 * @param name
	 *        A {@linkplain ModuleName module name}.
	 * @return {@code true} if the module is a package representative, {@code
	 *         false} otherwise.
	 */
	public boolean isPackage (final ModuleName name)
	{
		assert name.rootName().equals(rootName);
		lock.lock();
		try
		{
			final ModuleSummary summary =
				moduleMap.get(name.rootRelativeName());
			assert summary != null;
			return summary.isPackage;
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Answer the specified {@linkplain ModuleDescriptor module}'s size.
	 *
	 * @param name
	 *        A {@linkplain ModuleName module name}.
	 * @return The size of the compiled module, in bytes.
	 */
	public long moduleSize (final ModuleName name)
	{
		assert name.rootName().equals(rootName);
		lock.lock();
		try
		{
			final ModuleSummary summary =
				moduleMap.get(name.rootRelativeName());
			assert summary != null;
			return summary.moduleSize;
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Answer the compiled {@linkplain ModuleDescriptor module} for the
	 * specified {@linkplain ModuleName source module}.
	 *
	 * @param name
	 *        The name of the source module.
	 * @return The compiled module, or {@code null} if no compiled module is
	 *         available.
	 */
	public @Nullable byte[] get (final ModuleName name)
	{
		if (!name.rootName().equals(rootName))
		{
			return null;
		}
		lock.lock();
		try
		{
			final ModuleSummary summary =
				moduleMap.get(name.rootRelativeName());
			if (summary != null)
			{
				return repository().get(summary.index);
			}
			return null;
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Answer the compiled {@linkplain ModuleDescriptor module} for the
	 * specified {@linkplain ModuleName source module} and last modification
	 * time.
	 *
	 * @param name
	 *        The name of the source module.
	 * @param sourceLastModified
	 *        The last modification time of the source module.
	 * @return The compiled module, or {@code null} if no compiled module is
	 *         available.
	 */
	public @Nullable byte[] get (
		final ModuleName name,
		final long sourceLastModified)
	{
		if (!name.rootName().equals(rootName))
		{
			return null;
		}
		lock.lock();
		try
		{
			final ModuleSummary summary =
				moduleMap.get(name.rootRelativeName());
			if (summary != null)
			{
				if (summary.sourceLastModified == sourceLastModified)
				{
					return repository().get(summary.index);
				}
			}
			return null;
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Record a compiled {@linkplain ModuleDescriptor module}.
	 *
	 * @param name
	 *        The {@linkplain ModuleName name} of the module.
	 * @param sourceLastModified
	 *        The last modification time of the source module.
	 * @param compiledBytes
	 *        A byte array that encodes the compiled module.
	 * @throws IndexedFileException
	 */
	public void put (
			final ResolvedModuleName name,
			final long sourceLastModified,
			final byte[] compiledBytes)
		throws IndexedFileException
	{
		assert name.rootName().equals(rootName);
		lock.lock();
		try
		{
			final IndexedRepository repo = repository();
			final ModuleSummary summary = new ModuleSummary(
				name.isPackage(),
				sourceLastModified,
				compiledBytes.length,
				repo.longSize());
			moduleMap.put(name.rootRelativeName(), summary);
			repo.add(compiledBytes);
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Write all pending data and metadata to the {@linkplain IndexedRepository
	 * indexed repository}.
	 *
	 * @throws IndexedFileException
	 *         If anything goes wrong.
	 */
	public void commit () throws IndexedFileException
	{
		lock.lock();
		try
		{
			final ByteArrayOutputStream byteStream =
				new ByteArrayOutputStream(131072);
			DataOutputStream binaryStream = null;
			try
			{
				binaryStream = new DataOutputStream(byteStream);
				binaryStream.writeInt(moduleMap.size());
				for (final Map.Entry<String, ModuleSummary> e :
					moduleMap.entrySet())
				{
					final String name = e.getKey();
					assert name != null;
					binaryStream.writeUTF(name);
					final ModuleSummary summary = e.getValue();
					assert summary != null;
					binaryStream.writeBoolean(summary.isPackage);
					binaryStream.writeLong(summary.sourceLastModified);
					binaryStream.writeLong(summary.moduleSize);
					binaryStream.writeLong(summary.index);
				}
			}
			finally
			{
				if (binaryStream != null)
				{
					binaryStream.close();
				}
			}
			final IndexedRepository repo = repository();
			repo.metaData(byteStream.toByteArray());
			repo.commit();
		}
		catch (final IndexedFileException e)
		{
			throw e;
		}
		catch (final Exception e)
		{
			throw new IndexedFileException(e);
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Close the underlying {@linkplain IndexedRepository indexed repository}.
	 */
	public void close ()
	{
		lock.lock();
		try
		{
			repository().close();
			moduleMap.clear();
		}
		finally
		{
			isOpen = false;
			lock.unlock();
		}
	}

	/**
	 * Open the {@linkplain IndexedRepository repository} and initialize the
	 * {@linkplain IndexedRepositoryManager manager}'s internal data structures.
	 *
	 * @throws IndexedFileException
	 *         If anything goes wrong.
	 */
	private void openOrCreate () throws IndexedFileException
	{
		try
		{
			final boolean exists = fileName.exists();
			final IndexedRepository repo = exists
				? IndexedFile.openFile(IndexedRepository.class, fileName, true)
				: IndexedFile.newFile(IndexedRepository.class, fileName, null);
			if (exists)
			{
				final byte[] metadata = repo.metaData();
				if (metadata != null)
				{
					DataInputStream binaryStream = null;
					try
					{
						final ByteArrayInputStream byteStream =
							new ByteArrayInputStream(metadata);
						binaryStream = new DataInputStream(byteStream);
						int count = binaryStream.readInt();
						while (count-- > 0)
						{
							final String rootRelativeName =
								binaryStream.readUTF();
							final ModuleSummary summary =
								new ModuleSummary(
									binaryStream.readBoolean(),
									binaryStream.readLong(),
									binaryStream.readLong(),
									binaryStream.readLong());
							moduleMap.put(rootRelativeName, summary);
						}
					}
					finally
					{
						if (binaryStream != null)
						{
							binaryStream.close();
						}
					}
				}
			}
			repository = repo;
			isOpen = true;
		}
		catch (final Exception e)
		{
			throw new IndexedFileException(e);
		}
	}

	/** Is the {@linkplain IndexedRepository repository} open? */
	private boolean isOpen;

	/**
	 * Reopen the {@linkplain IndexedRepository repository file} and
	 * reinitialize the {@linkplain IndexedRepositoryManager manager}.
	 */
	public void reopenIfNecessary ()
	{
		lock.lock();
		try
		{
			if (!isOpen)
			{
				openOrCreate();
			}
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Construct a new {@link IndexedRepositoryManager}.
	 *
	 * @param rootName
	 *        The name of the Avail root represented by the {@linkplain
	 *        IndexedRepository indexed repository}.
	 * @param fileName
	 *        The {@linkplain File path} to the indexed repository.
	 * @throws IndexedFileException
	 *         If an {@linkplain Exception exception} occurs.
	 */
	public IndexedRepositoryManager (
		final String rootName,
		final File fileName)
	{
		this.rootName = rootName;
		this.fileName = fileName;
		openOrCreate();
	}

	/**
	 * Create a {@linkplain IndexedRepositoryManager repository manager} for
	 * a temporary {@linkplain IndexedFile indexed file}. The indexed file will
	 * be deleted on exit.
	 *
	 * @param rootName
	 *        The name of the Avail root represented by the {@linkplain
	 *        IndexedRepository indexed repository}.
	 * @param prefix
	 *        A prefix used in generation of the temporary file name.
	 * @param suffix
	 *        A suffix used in generation of the temporary file name.
	 * @return The indexed repository manager.
	 * @throws IndexedFileException
	 *         If an {@linkplain Exception exception} occurs.
	 */
	public static IndexedRepositoryManager createTemporary (
		final String rootName,
		final String prefix,
		final @Nullable String suffix)
	{
		try
		{
			final File file = File.createTempFile(prefix, suffix);
			file.deleteOnExit();
			IndexedRepository indexedFile = null;
			try
			{
				indexedFile = IndexedFile.newFile(
					IndexedRepository.class, file, null);
			}
			finally
			{
				if (indexedFile != null)
				{
					indexedFile.close();
				}
			}
			return new IndexedRepositoryManager(rootName, file);
		}
		catch (final Exception e)
		{
			throw new IndexedFileException(e);
		}
	}

	/**
	 * Is the specified {@linkplain File file} an {@linkplain IndexedRepository
	 * indexed repository}?
	 *
	 * @param path
	 *        A path.
	 * @return {@code true} if the path refers to an indexed repository, {@code
	 *         false} otherwise.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	public static boolean isIndexedRepositoryFile (final File path)
		throws IOException
	{
		if (path.isFile())
		{
			try (final RandomAccessFile file = new RandomAccessFile(path, "r"))
			{
				final byte[] repositoryHeader = IndexedRepository.header();
				final byte[] buffer = new byte[repositoryHeader.length];
				int pos = 0;
				while (true)
				{
					final int bytesRead =
						file.read(buffer, pos, buffer.length - pos);
					if (bytesRead == -1 || (pos += bytesRead) == buffer.length)
					{
						break;
					}
				}
				return
					pos == buffer.length
					&& Arrays.equals(repositoryHeader, buffer);
			}
		}
		return false;
	}
}
