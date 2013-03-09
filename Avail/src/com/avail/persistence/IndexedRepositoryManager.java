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
import java.util.zip.*;
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
	 * The {@linkplain File filename} of the {@linkplain IndexedRepository
	 * indexed repository}.
	 */
	private final File fileName;

	/** A {@linkplain ModuleNameResolver module name resolver}. */
	private final ModuleNameResolver resolver;

	/**
	 * The {@linkplain IndexedRepository repository} that stores this
	 * {@linkplain IndexedRepositoryManager manager}'s compiled {@linkplain
	 * ModuleDescriptor modules}.
	 */
	private IndexedRepository repository;

	/**
	 * Information kept in memory about a compiled {@linkplain ModuleDescriptor
	 * module}, including the source file's {@linkplain #sourceLastModified
	 * modification time} and persistent record number.
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
		 * The persistent record number of this version of the compiled
		 * {@linkplain ModuleDescriptor module}.
		 */
		public final long index;

		/**
		 * Construct a new {@link CompiledModulePointer}.
		 *
		 * @param sourceLastModified
		 *        The source file's modification time stamp.
		 * @param index
		 *        The persistent record number.
		 */
		CompiledModulePointer (
			final long sourceLastModified,
			final long index)
		{
			this.sourceLastModified = sourceLastModified;
			this.index = index;
		}
	}

	/**
	 * A map from {@linkplain ResolvedModuleName resolved module name} to
	 * {@linkplain CompiledModulePointer compiled module pointer}.
	 */
	private final Map<ResolvedModuleName, CompiledModulePointer> moduleMap =
		new HashMap<ResolvedModuleName, CompiledModulePointer>(100);

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
			repository.close();
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
				repository = (IndexedRepository) IndexedFile.newFile(
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
	 * @param sourceLastModified
	 *        The last modification time of the source module.
	 * @return {@code true} if there is already a valid compiled module
	 *         available, or {@code false} otherwise.
	 */
	public boolean hasKey (
		final ModuleName name,
		final long sourceLastModified)
	{
		lock.lock();
		try
		{
			final ResolvedModuleName resolvedName =
				name instanceof ResolvedModuleName
				? (ResolvedModuleName) name
				: resolver.resolve(name);
			final CompiledModulePointer pointer = moduleMap.get(resolvedName);
			if (pointer != null)
			{
				if (pointer.sourceLastModified == sourceLastModified)
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
	 * Answer the compiled {@linkplain ModuleDescriptor module} for the
	 * specified {@linkplain ModuleName source module}.
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
		lock.lock();
		try
		{
			final ResolvedModuleName resolvedName =
				name instanceof ResolvedModuleName
				? (ResolvedModuleName) name
				: resolver.resolve(name);
			final CompiledModulePointer pointer = moduleMap.get(resolvedName);
			if (pointer != null)
			{
				if (pointer.sourceLastModified == sourceLastModified)
				{
					return repository.get(pointer.index);
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
			final ModuleName name,
			final long sourceLastModified,
			final byte[] compiledBytes)
		throws IndexedFileException
	{
		lock.lock();
		try
		{
			final CompiledModulePointer pointer = new CompiledModulePointer(
				sourceLastModified,
				repository.longSize());
			final ResolvedModuleName resolvedName =
				name instanceof ResolvedModuleName
				? (ResolvedModuleName) name
				: resolver.resolve(name);
			moduleMap.put(resolvedName, pointer);
			repository.add(compiledBytes);
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
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 * @throws IndexedFileException
	 *         If any other {@linkplain Exception exception} occurs.
	 */
	public void commit () throws IOException, IndexedFileException
	{
		lock.lock();
		try
		{
			final ByteArrayOutputStream byteStream =
				new ByteArrayOutputStream(131072);
			DataOutputStream binaryStream = null;
			try
			{
				final DeflaterOutputStream deflateStream =
					new DeflaterOutputStream(
						byteStream, new Deflater(Deflater.BEST_COMPRESSION));
				binaryStream = new DataOutputStream(deflateStream);
				binaryStream.writeInt(moduleMap.size());
				for (final Map.Entry<ResolvedModuleName, CompiledModulePointer>
					e : moduleMap.entrySet())
				{
					final ResolvedModuleName name = e.getKey();
					assert name != null;
					binaryStream.writeUTF(name.qualifiedName());
					final CompiledModulePointer pointer = e.getValue();
					assert pointer != null;
					binaryStream.writeLong(pointer.sourceLastModified);
					binaryStream.writeLong(pointer.index);
				}
			}
			finally
			{
				if (binaryStream != null)
				{
					binaryStream.close();
				}
			}
			repository.metaData(byteStream.toByteArray());
			repository.commit();
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Construct a new {@link IndexedRepositoryManager}.
	 *
	 * @param fileName
	 *        The {@linkplain File filename} of the {@linkplain
	 *        IndexedRepository indexed repository}.
	 * @param resolver
	 *        The {@linkplain ModuleNameResolver module name resolver} to use
	 *        for {@linkplain ModuleName module name} {@linkplain
	 *        ResolvedModuleName resolution}.
	 * @throws IndexedFileException
	 *         If an {@linkplain Exception exception} occurs.
	 */
	public IndexedRepositoryManager (
		final File fileName,
		final ModuleNameResolver resolver)
	{
		this.fileName = fileName;
		this.resolver = resolver;
		try
		{
			final Class<? extends IndexedFile> subclass =
				IndexedRepository.class;
			final boolean exists = fileName.exists();
			repository = (IndexedRepository) (exists
				? IndexedFile.openFile(subclass, fileName, true)
				: IndexedFile.newFile(subclass, fileName, null));
			if (exists)
			{
				final byte[] metadata = repository.metaData();
				if (metadata != null)
				{
					DataInputStream binaryStream = null;
					try
					{
						final ByteArrayInputStream byteStream =
							new ByteArrayInputStream(metadata);
						final InflaterInputStream inflateStream =
							new InflaterInputStream(byteStream, new Inflater());
						binaryStream = new DataInputStream(inflateStream);
						int count = binaryStream.readInt();
						while (count-- > 0)
						{
							final ResolvedModuleName name = resolver.resolve(
								new ModuleName(binaryStream.readUTF()));
							final CompiledModulePointer pointer =
								new CompiledModulePointer(
									binaryStream.readLong(),
									binaryStream.readLong());
							moduleMap.put(name, pointer);
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
		}
		catch (final Exception e)
		{
			throw new IndexedFileException(e);
		}
	}

	/**
	 * Create a {@linkplain IndexedRepositoryManager repository manager} for
	 * a temporary {@linkplain IndexedFile indexed file}. The indexed file will
	 * be deleted on exit.
	 *
	 * @param resolver
	 *        The {@linkplain ModuleNameResolver module name resolver} to use
	 *        for {@linkplain ModuleName module name} {@linkplain
	 *        ResolvedModuleName resolution}.
	 * @return The indexed repository manager.
	 * @throws IndexedFileException
	 *         If an {@linkplain Exception exception} occurs.
	 */
	public static IndexedRepositoryManager createTemporary (
		final ModuleNameResolver resolver)
	{
		try
		{
			final File file = File.createTempFile("repository", null);
			file.deleteOnExit();
			IndexedFile indexedFile = null;
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
			return new IndexedRepositoryManager(file, resolver);
		}
		catch (final Exception e)
		{
			throw new IndexedFileException(e);
		}
	}
}
