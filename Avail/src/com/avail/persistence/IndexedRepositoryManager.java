/**
 * All rights reserved.
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
 * IndexedRepositoryManager.java
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import javax.xml.bind.DatatypeConverter;
import com.avail.annotations.InnerAccess;
import com.avail.annotations.Nullable;
import com.avail.builder.*;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.serialization.Serializer;
import com.avail.utility.Pair;

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
	 * The name of the {@link MessageDigest} used to detect file changes.
	 */
	private static final String DIGEST_ALGORITHM = "SHA-256";

	/**
	 * The size in bytes of the digest of a source file.
	 */
	private static final int DIGEST_SIZE = 256 / 8;

	/**
	 * Whether to log repository accesses to System.out.
	 */
	private static boolean debugRepository = false;

	/**
	 * The {@linkplain ReentrantLock lock} responsible for guarding against
	 * unsafe concurrent access.
	 */
	@InnerAccess final ReentrantLock lock = new ReentrantLock();

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
	@InnerAccess IndexedRepository repository ()
	{
		final IndexedRepository repo = repository;
		assert repo != null;
		return repo;
	}

	/**
	 * Keep track of whether changes have happened since the last commit, and
	 * when the first such change happened.
	 */
	long dirtySince = 0L;

	/**
	 * Produce a new int hash value from an existing int and a long.
	 *
	 * @param currentHash
	 *        The current hash value.
	 * @param newLong
	 *        The long to be mixed in.
	 * @return
	 *        A hash value combining the two inputs.
	 */
	@InnerAccess static int mix (final int currentHash, final long newLong)
	{
		int h = currentHash;
		h *= A_BasicObject.multiplier;
		h += (int)newLong;
		h *= A_BasicObject.multiplier;
		h ^= (int)(newLong >> 32);
		return h;
	}

	/**
	 * An immutable key which specifies a version of a module.  It includes the
	 * root-relative name, whether the name refers to a package (a directory),
	 * and the digest of the file's contents.
	 */
	public static class ModuleVersionKey
	{
		/**
		 * The name of this module or package, relative to this repository's
		 * root.
		 */
		public final String rootRelativeName;

		/**
		 * Is the {@linkplain ModuleDescriptor module} a package
		 * representative?
		 */
		public final boolean isPackage;

		/**
		 * The SHA256 digest of the UTF-8 representation of the module's
		 * source code.
		 */
		public final byte [] sourceDigest;

		/**
		 * A hash of all the fields except the index.
		 */
		private final int hash;

		@Override
		public final int hashCode ()
		{
			return hash;
		}

		/**
		 * Calculate my hash.
		 *
		 * @return The hash of my immutable content.
		 */
		private int computeHash ()
		{
			int h = rootRelativeName.hashCode();
			h += isPackage ? 0xDEAD_BEEF : 0xA_CABBA6E;
			for (int i = 0; i < sourceDigest.length; i++)
			{
				h = h * A_BasicObject.multiplier + sourceDigest[i];
			}
			return h;
		}

		@Override
		public boolean equals (@Nullable final Object obj)
		{
			if (obj == null)
			{
				return false;
			}
			if (!(obj instanceof ModuleVersionKey))
			{
				return false;
			}
			final ModuleVersionKey key = (ModuleVersionKey)obj;
			return hash == key.hash
				&& isPackage == key.isPackage
				&& rootRelativeName.equals(key.rootRelativeName)
				&& Arrays.equals(sourceDigest, key.sourceDigest);
		}

		/**
		 * Output this module version key to the provided {@link
		 * DataOutputStream}.  An equal key can later be rebuilt via the
		 * constructor taking a {@link DataInputStream}.
		 *
		 * @param binaryStream A DataOutputStream on which to write this key.
		 * @throws IOException If I/O fails.
		 */
		public void write (final DataOutputStream binaryStream)
			throws IOException
		{
			binaryStream.writeUTF(rootRelativeName);
			binaryStream.writeBoolean(isPackage);
			binaryStream.write(sourceDigest);
		}

		@Override
		public String toString ()
		{
			return String.format(
				"VersionKey(%s @%s...)",
				rootRelativeName,
				DatatypeConverter.printHexBinary(
					Arrays.copyOf(sourceDigest, 3)));
		}

		/**
		 * Reconstruct a {@link ModuleVersionKey}, having previously been
		 * written via {@link #write(DataOutputStream)}.
		 *
		 * @param binaryStream Where to read the version key from.
		 * @throws IOException If I/O fails.
		 */
		ModuleVersionKey (final DataInputStream binaryStream)
			throws IOException
		{
			rootRelativeName = binaryStream.readUTF();
			isPackage = binaryStream.readBoolean();
			sourceDigest = new byte [DIGEST_SIZE];
			binaryStream.readFully(sourceDigest);
			hash = computeHash();
		}

		/**
		 * Construct a new {@link ModuleVersionKey}.
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module.
		 */
		public ModuleVersionKey (
			final ResolvedModuleName moduleName)
		{
			final String rootRelative = moduleName.rootRelativeName();
			final File file = new File(rootRelative);
			final File parent = file.getParentFile();
			assert parent == null || !parent.getName().equals(file.getName())
				: "Representative module encountered – should be package name";
			this.rootRelativeName = rootRelative;
			this.isPackage = moduleName.isPackage();
			this.sourceDigest =
				moduleName.repository().digestForFile(moduleName);
			assert sourceDigest.length == DIGEST_SIZE;
			this.hash = computeHash();
		}
	}

	/**
	 * An immutable key which specifies a version of a module and its context at
	 * the time of compilation.  It does not explicitly contain the {@link
	 * ModuleVersionKey}, but it includes the compilation times of the module's
	 * predecessors.
	 */
	public static class ModuleCompilationKey
	{
		/**
		 * The times at which this module's predecessors were compiled, in
		 * the order specified by the Uses/Extends declarations.
		 */
		public final long [] predecessorCompilationTimes;

		/**
		 * A hash of all the fields except the index.
		 */
		private final int hash;

		@Override
		public final int hashCode ()
		{
			return hash;
		}

		/**
		 * Calculate my hash.
		 *
		 * @return The hash of my immutable content.
		 */
		private int computeHash ()
		{
			int h = 0x9E5_90125;
			for (int i = 0; i < predecessorCompilationTimes.length; i++)
			{
				h = mix(h, predecessorCompilationTimes[i]);
			}
			return h;
		}

		@Override
		public boolean equals (@Nullable final Object obj)
		{
			if (obj == null)
			{
				return false;
			}
			if (!(obj instanceof ModuleCompilationKey))
			{
				return false;
			}
			final ModuleCompilationKey key = (ModuleCompilationKey)obj;
			return hash == key.hash
				&& Arrays.equals(
					predecessorCompilationTimes,
					key.predecessorCompilationTimes);
		}

		/**
		 * Output this module compilation key to the provided {@link
		 * DataOutputStream}.  An equal key can later be rebuilt via the
		 * constructor taking a {@link DataInputStream}.
		 *
		 * @param binaryStream A DataOutputStream on which to write this key.
		 * @throws IOException If I/O fails.
		 */
		public void write (final DataOutputStream binaryStream)
			throws IOException
		{
			binaryStream.writeInt(predecessorCompilationTimes.length);
			for (final long predecessorCompilationTime
				: predecessorCompilationTimes)
			{
				binaryStream.writeLong(predecessorCompilationTime);
			}
		}

		/**
		 * Reconstruct a {@link ModuleCompilationKey}, having previously been
		 * written via {@link #write(DataOutputStream)}.
		 *
		 * @param binaryStream Where to read the compilation key from.
		 * @throws IOException If I/O fails.
		 */
		ModuleCompilationKey (final DataInputStream binaryStream)
			throws IOException
		{
			final int predecessorsCount = binaryStream.readInt();
			predecessorCompilationTimes = new long [predecessorsCount];
			for (int i = 0; i < predecessorsCount; i++)
			{
				predecessorCompilationTimes[i] = binaryStream.readLong();
			}
			hash = computeHash();
		}

		/**
		 * Construct a new {@link ModuleCompilationKey}.
		 *
		 * @param predecessorCompilationTimes
		 *        The compilation times of this module's predecessors, in
		 *        the order of import declaration.
		 */
		public ModuleCompilationKey (
			final long [] predecessorCompilationTimes)
		{
			this.predecessorCompilationTimes = predecessorCompilationTimes;
			hash = computeHash();
		}
	}

	/**
	 * Information kept in memory about a specific version of a {@linkplain
	 * ModuleDescriptor module} file.
	 */
	public class ModuleVersion
	{
		/**
		 * The size of the {@linkplain ModuleDescriptor module}'s source code,
		 * in bytes.
		 */
		private final long moduleSize;

		/**
		 * The names of the modules being imported by this version of this
		 * module.  The names are local names, in the order they occur in the
		 * module source.
		 */
		private final List<String> localImportNames;

		/**
		 * All recorded compilations of this version of the module.
		 */
		@InnerAccess final Map<ModuleCompilationKey, ModuleCompilation>
			compilations;

		/**
		 * Look up the {@link ModuleCompilation} associated with the provided
		 * {@link ModuleCompilationKey}, answering {@code null} if unavailable.
		 *
		 * @param compilationKey
		 *        The context information about a compilation.
		 * @return The corresponding compilation or {@code null}.
		 */
		public @Nullable ModuleCompilation getCompilation (
			final ModuleCompilationKey compilationKey)
		{
			lock.lock();
			try
			{
				return compilations.get(compilationKey);
			}
			finally
			{
				lock.unlock();
			}
		}

		/**
		 * Answer the list of local module names imported by this version of the
		 * module.
		 *
		 * @return The list of local module names.
		 */
		public List<String> getImports ()
		{
			return localImportNames;
		}

		/**
		 * Output this module version to the provided {@link
		 * DataOutputStream}.  It can later be reconstructed via the constructor
		 * taking a {@link DataInputStream}.
		 *
		 * @param binaryStream
		 *        A DataOutputStream on which to write this module version.
		 * @throws IOException If I/O fails.
		 */
		@InnerAccess void write (final DataOutputStream binaryStream)
			throws IOException
		{
			binaryStream.writeLong(moduleSize);
			binaryStream.writeInt(localImportNames.size());
			for (final String importName : localImportNames)
			{
				binaryStream.writeUTF(importName);
			}
			binaryStream.writeInt(compilations.size());
			for (final Map.Entry<ModuleCompilationKey, ModuleCompilation>
				entry : compilations.entrySet())
			{
				entry.getKey().write(binaryStream);
				entry.getValue().write(binaryStream);
			}
		}

		@Override
		public String toString ()
		{
			return String.format(
				"Version:%n\t\timports=%s%n\t\tcompilations=%s",
				localImportNames,
				compilations.values());
		}

		/**
		 * Reconstruct a {@link ModuleVersion}, having previously been
		 * written via {@link #write(DataOutputStream)}.
		 *
		 * @param binaryStream Where to read the key from.
		 * @throws IOException If I/O fails.
		 */
		ModuleVersion (final DataInputStream binaryStream)
			throws IOException
		{
			moduleSize = binaryStream.readLong();
			int localImportCount = binaryStream.readInt();
			localImportNames = new ArrayList<>(localImportCount);
			while (localImportCount-- > 0)
			{
				localImportNames.add(binaryStream.readUTF());
			}
			int compilationsCount = binaryStream.readInt();
			compilations = new HashMap<>(compilationsCount);
			while (compilationsCount-- > 0)
			{
				compilations.put(
					new ModuleCompilationKey(binaryStream),
					new ModuleCompilation(binaryStream));
			}
		}

		/**
		 * Construct a new {@link ModuleVersion}.
		 *
		 * @param moduleSize
		 *        The size of the compiled module, in bytes.
		 * @param localImportNames
		 *        The list of module names being imported.
		 */
		public ModuleVersion (
			final long moduleSize,
			final List<String> localImportNames)
		{
			this.moduleSize = moduleSize;
			this.localImportNames = new ArrayList<>(localImportNames);
			this.compilations = new HashMap<>();
		}
	}

	/**
	 * Information kept in memory about a compilation of a {@linkplain
	 * ModuleDescriptor module}.
	 */
	public class ModuleCompilation
	{
		/**
		 * The time at which this module was compiled.
		 */
		public final long compilationTime;

		/**
		 * The persistent record number of this version of the compiled
		 * {@linkplain ModuleDescriptor module}.
		 */
		public final long recordNumber;

		/**
		 * @return
		 */
		public @Nullable byte [] getBytes ()
		{
			lock.lock();
			try
			{
				return repository().get(recordNumber);
			}
			finally
			{
				lock.unlock();
			}
		}

		/**
		 * Output this module compilation to the provided {@link
		 * DataOutputStream}.  It can later be reconstructed via the constructor
		 * taking a {@link DataInputStream}.  Note that the {@link
		 * ModuleCompilation} is output with the module compilation.
		 *
		 * @param binaryStream
		 *        A DataOutputStream on which to write this module compilation.
		 * @throws IOException If I/O fails.
		 */
		@InnerAccess void write (final DataOutputStream binaryStream)
			throws IOException
		{
			binaryStream.writeLong(compilationTime);
			binaryStream.writeLong(recordNumber);
		}

		@Override
		public String toString ()
		{
			return String.format(
				"Compilation(%tFT%<tTZ, rec=%d)",
				compilationTime,
				recordNumber);
		}

		/**
		 * Reconstruct a {@link ModuleCompilation}, having previously been
		 * written via {@link #write(DataOutputStream)}.
		 *
		 * @param binaryStream Where to read the key from.
		 * @throws IOException If I/O fails.
		 */
		ModuleCompilation (final DataInputStream binaryStream)
			throws IOException
		{
			compilationTime = binaryStream.readLong();
			recordNumber = binaryStream.readLong();
		}

		/**
		 * Construct a new {@link ModuleCompilation}, adding the serialized
		 * compiled module bytes to the repository without committing.
		 *
		 * @param compilationTime
		 *        The compilation time of this module.
		 * @param bytes
		 *        The {@link Serializer serialized} form of the compiled module.
		 */
		public ModuleCompilation (
			final long compilationTime,
			final byte [] bytes)
		{
			this.compilationTime = compilationTime;
			final IndexedRepository repo = repository();
			lock.lock();
			try
			{
				this.recordNumber = repo.longSize();
				repo.add(recordNumber, bytes);
			}
			finally
			{
				lock.unlock();
			}
		}
	}

	/**
	 * A {@link Map} used to avoid computing digests of files when the file's
	 * timestamp has not changed.  Each key is a {@link Pair} consisting of the
	 * module name relative to this repository's root and that file's
	 * {@linkplain File#lastModified() last modification} time.  The value is a
	 * byte array holding the SHA-256 digest of the file content.
	 */
	private final Map<Pair<String, Long>, byte []> digestCache =
		new HashMap<>(100);

	/**
	 * A map from each {@link ModuleVersionKey} to the corresponding
	 * {@linkplain ModuleVersion}.
	 */
	private final Map<ModuleVersionKey, ModuleVersion> moduleMap =
		new HashMap<>(100);

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
			if (debugRepository)
			{
				System.out.format("Clear %s%n", rootName);
				System.out.flush();
			}
			digestCache.clear();
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
				isOpen = true;
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
	 * Given a resolved module name, determine the cryptographic hash of the
	 * file contents.  Since we assume that the same filename and modification
	 * time implies the same digest, we cache the digest under that combination
	 * for performance.
	 *
	 * @param resolvedName
	 *        The resolved name of the module.
	 * @return The digest of the file, updating the {@link #digestCache} if
	 *         necessary.
	 */
	public byte [] digestForFile (
		final ResolvedModuleName resolvedName)
	{
		final File sourceFile = resolvedName.sourceReference();
		assert sourceFile != null;
		final long lastModification = sourceFile.lastModified();
		final Pair<String, Long> pair =
			new Pair<>(resolvedName.rootRelativeName(), lastModification);
		byte [] digest = digestCache.get(pair);
		if (digest == null)
		{
			// Don't bother protecting against computing the digest for the same
			// file in multiple threads.  At worst it's extra work, and it's not
			// likely that maintenance on the build mechanism would *ever* cause
			// it to do that anyhow.
			final byte [] buffer = new byte [4096];
			int bufferSize;
			final MessageDigest hasher;
			try (RandomAccessFile reader =
				new RandomAccessFile(sourceFile, "r"))
			{
				hasher = MessageDigest.getInstance(DIGEST_ALGORITHM);
				while ((bufferSize = reader.read(buffer)) != -1)
				{
					hasher.update(buffer, 0, bufferSize);
				}
			}
			catch (final NoSuchAlgorithmException | IOException e)
			{
				throw new RuntimeException(e);
			}
			digest = hasher.digest();
			assert digest.length == DIGEST_SIZE;
			lock.lock();
			try
			{
				digestCache.put(pair, digest);
				markDirty();
			}
			finally
			{
				lock.unlock();
			}
		}
		return digest;
	}

	/**
	 * If this repository is not already dirty, mark it as dirty as of now.
	 */
	public void markDirty ()
	{
		if (dirtySince == 0L)
		{
			dirtySince = System.currentTimeMillis();
		}
	}

	/**
	 * If this {@link ModuleVersion} exists in the repository, then answer it;
	 * otherwise answer {@code null}.
	 *
	 * @param versionKey
	 *        The {@link ModuleVersionKey} identifying the version of a module's
	 *        source.
	 * @return The associated {@link ModuleVersion} if present, otherwise
	 *         {@code null}.
	 */
	public @Nullable ModuleVersion getVersion (
		final ModuleVersionKey versionKey)
	{
		lock.lock();
		try
		{
			return moduleMap.get(versionKey);
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Record a {@link ModuleVersion version} of a {@linkplain ModuleDescriptor
	 * module}.  This includes information about the source's digest and the
	 * list of local imports.
	 *
	 * <p>There must not already be a version with that key in the repository.
	 *
	 * @param versionKey
	 *        The {@link ModuleVersionKey} identifying the version of a module's
	 *        source.
	 * @param version
	 *        The {@link ModuleVersion} to add.
	 */
	public void putVersion (
		final ModuleVersionKey versionKey,
		final ModuleVersion version)
	{
		lock.lock();
		try
		{
			assert !moduleMap.containsKey(versionKey);
			moduleMap.put(versionKey, version);
			markDirty();
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Record a new {@linkplain ModuleCompilation compilation} of a
	 * {@linkplain ModuleVersion module version}.  The version must already
	 * exist in the repository.  The {@linkplain ModuleCompilationKey
	 * compilation key} must not yet have a {@linkplain ModuleCompilation
	 * compilation} associated with it.
	 *
	 * @param versionKey
	 *        The {@link ModuleVersionKey} identifying the version of a module's
	 *        source.
	 * @param compilationKey
	 *        The {@link ModuleCompilationKey} under which to record the
	 *        compilation.
	 * @param compilation
	 *        The {@link ModuleCompilation} to add.
	 */
	public void putCompilation (
		final ModuleVersionKey versionKey,
		final ModuleCompilationKey compilationKey,
		final ModuleCompilation compilation)
	{
		lock.lock();
		try
		{
			final ModuleVersion version = moduleMap.get(versionKey);
			assert version != null;
			assert version.getCompilation(compilationKey) == null;
			version.compilations.put(compilationKey, compilation);
			markDirty();
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
			if (dirtySince != 0L)
			{
				if (debugRepository)
				{
					System.out.format("Commit %s%n", rootName);
					System.out.flush();
				}
				final ByteArrayOutputStream byteStream =
					new ByteArrayOutputStream(131072);
				try (final DataOutputStream binaryStream =
					new DataOutputStream(byteStream))
				{
					binaryStream.writeInt(digestCache.size());
					for (final Map.Entry<Pair<String, Long>, byte []>
						e : digestCache.entrySet())
					{
						final Pair<String, Long> pair = e.getKey();
						final byte [] value = e.getValue();
						binaryStream.writeUTF(pair.first());
						binaryStream.writeLong(pair.second());
						assert value.length == DIGEST_SIZE;
						binaryStream.write(value);
					}
					binaryStream.writeInt(moduleMap.size());
					for (final Map.Entry<ModuleVersionKey, ModuleVersion>
						e : moduleMap.entrySet())
					{
						e.getKey().write(binaryStream);
						e.getValue().write(binaryStream);
					}
				}
				reopenIfNecessary();
				final IndexedRepository repo = repository();
				repo.metaData(byteStream.toByteArray());
				repo.commit();
				dirtySince = 0L;
			}
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
	 * Commit the pending changes if they're more than the specified number of
	 * milliseconds old.
	 *
	 * @param maximumChangeAgeMs
	 *        The maximum age in milliseconds that we should leave changes
	 *        uncommitted.
	 */
	public void commitIfStaleChanges (final long maximumChangeAgeMs)
	{
		lock.lock();
		try
		{
			if (dirtySince != 0L
				&& System.currentTimeMillis() - dirtySince > maximumChangeAgeMs)
			{
				commit();
			}
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
			if (debugRepository)
			{
				System.out.format("Close %s%n", rootName);
				System.out.flush();
			}
			isOpen = false;
			final IndexedRepository repo = repository;
			if (repo != null)
			{
				repo.close();
			}
			moduleMap.clear();
		}
		finally
		{
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
		assert !isOpen;
		try
		{
			final boolean exists = fileName.exists();
			IndexedFile.logger.log(
				Level.INFO,
				"exists: {0} = {1}",
				new Object [] { fileName, exists });
			final IndexedRepository repo = exists
				? IndexedFile.openFile(IndexedRepository.class, fileName, true)
				: IndexedFile.newFile(IndexedRepository.class, fileName, null);
			if (exists)
			{
				final byte [] metadata = repo.metaData();
				if (metadata != null)
				{
					final ByteArrayInputStream byteStream =
						new ByteArrayInputStream(metadata);
					try (final DataInputStream binaryStream =
						new DataInputStream(byteStream))
					{
						int digestCacheSize = binaryStream.readInt();
						while (digestCacheSize-- > 0)
						{
							final String rootRelativeName =
								binaryStream.readUTF();
							final long lastModification =
								binaryStream.readLong();
							final byte [] digest = new byte [DIGEST_SIZE];
							binaryStream.readFully(digest);
							digestCache.put(
								new Pair<String, Long>(
									rootRelativeName,
									lastModification),
								digest);
						}
						int moduleMapSize = binaryStream.readInt();
						while (moduleMapSize-- > 0)
						{
							final ModuleVersionKey versionKey =
								new ModuleVersionKey(binaryStream);
							final ModuleVersion version =
								new ModuleVersion(binaryStream);
							moduleMap.put(versionKey, version);
						}
						assert byteStream.available() == 0;
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
	private boolean isOpen = false;

	/**
	 * Reopen the {@linkplain IndexedRepository repository file} and
	 * reinitialize the {@linkplain IndexedRepositoryManager manager}.
	 */
	public void reopenIfNecessary ()
	{
		lock.lock();
		try
		{
			if (debugRepository)
			{
				System.out.format(
					"Reopen if necessary %s (was open = %s)%n",
					rootName,
					isOpen);
				System.out.flush();
			}
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
				final byte [] repositoryHeader = IndexedRepository.header();
				final byte [] buffer = new byte[repositoryHeader.length];
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


	@Override
	public String toString ()
	{
		final Formatter out = new Formatter();
		out.format("Repository \"%s\" with modules:", rootName);
		for (final Map.Entry<ModuleVersionKey, ModuleVersion> entry
			: moduleMap.entrySet())
		{
			out.format("%n\t%s → %s", entry.getKey(), entry.getValue());
		}
		return out.toString();
	}
}
