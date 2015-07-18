/**
 * All rights reserved.
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.AvailObject.multiplier;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.DatatypeConverter;
import com.avail.annotations.InnerAccess;
import com.avail.annotations.Nullable;
import com.avail.builder.*;
import com.avail.compiler.ModuleHeader;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.serialization.Serializer;
import com.avail.utility.evaluation.Transformer2;

/**
 * An {@code IndexedRepositoryManager} manages a persistent {@linkplain
 * IndexedRepository indexed repository} of compiled {@linkplain
 * ModuleDescriptor modules}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class IndexedRepositoryManager
{
	/** The {@linkplain Logger logger}. */
	public static final Logger logger = Logger.getLogger(
		IndexedRepositoryManager.class.getName());

	/**
	 * Whether to log repository accesses to standard output.
	 */
	private static boolean debugRepository = false;

	/**
	 * Log the specified message if {@linkplain #debugRepository debugging} is
	 * enabled.
	 *
	 * @param level
	 *        The {@linkplain Level severity level}.
	 * @param format
	 *        The format string.
	 * @param args
	 *        The format arguments.
	 */
	public static void log (
		final Level level,
		final String format,
		final Object... args)
	{
		if (debugRepository)
		{
			if (logger.isLoggable(level))
			{
				logger.log(level, format, args);
			}
		}
	}

	/**
	 * Log the specified message if {@linkplain #debugRepository debugging} is
	 * enabled.
	 *
	 * @param level
	 *        The {@linkplain Level severity level}.
	 * @param exception
	 *        The {@linkplain Throwable exception} that motivated this log
	 *        entry.
	 * @param format
	 *        The format string.
	 * @param args
	 *        The format arguments.
	 */
	public static void log (
		final Level level,
		final Throwable exception,
		final String format,
		final Object... args)
	{
		if (debugRepository)
		{
			if (logger.isLoggable(level))
			{
				logger.log(level, String.format(format, args), exception);
			}
		}
	}

	/**
	 * The name of the {@link MessageDigest} used to detect file changes.
	 */
	private static final String DIGEST_ALGORITHM = "SHA-256";

	/**
	 * The size in bytes of the digest of a source file.
	 */
	private static final int DIGEST_SIZE = 256 / 8;

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
		h *= multiplier;
		h += (int)newLong;
		h *= multiplier;
		h ^= (int)(newLong >> 32);
		return h;
	}

	/**
	 * Used to determine if the file's version is compatible with the current
	 * version in the code.  Return true to indicate they're compatible, or
	 * false to cause on open attempt to fail.  The first argument is the file's
	 * version, and the second is the code's version.
	 */
	final static Transformer2<Integer, Integer, Boolean> versionCheck =
		new Transformer2<Integer, Integer, Boolean>()
		{
			@Override
			public @Nullable Boolean value (
				final @Nullable Integer fileVersion,
				final @Nullable Integer codeVersion)
			{
				assert fileVersion != null;
				assert codeVersion != null;
				return fileVersion.intValue() == codeVersion.intValue();
			}
		};

	/**
	 * A {@link Map} which discards the oldest entry whenever an attempt is made
	 * to store more than the {@link #maximumSize} elements in it.
	 *
	 * @param <K> The keys of the cache.
	 * @param <V> The values associated with keys of the cache.
	 */
	@SuppressWarnings("serial")
	public static class LimitedCache<K, V>
	extends LinkedHashMap<K, V>
	{
		/**
		 * The largest size that this cache can be after any public operation.
		 */
		final int maximumSize;

		/**
		 * Construct a new {@link IndexedRepositoryManager.LimitedCache} with
		 * the given maximum size.
		 *
		 * @param maximumSize The maximum cache size.
		 */
		public LimitedCache (final int maximumSize)
		{
			super(maximumSize, 0.75f, true);
			assert maximumSize > 0;
			this.maximumSize = maximumSize;
		}

		@Override
		protected boolean removeEldestEntry (
			@Nullable final Map.Entry<K, V> eldest)
		{
			return size() > maximumSize;
		}
	}

	/**
	 * All information associated with a particular module name in this module,
	 * across all known versions.
	 */
	public class ModuleArchive
	{
		/** The maximum number of versions to keep for each module. */
		private final static int maxRecordedVersionsPerModule = 10;

		/** The maximum number of digests to cache per module. */
		private final static int maxRecordedDigestsPerModule = 20;

		/** The latest N versions of this module. */
		private final LinkedHashMap <ModuleVersionKey, ModuleVersion> versions =
			new LinkedHashMap<>(maxRecordedVersionsPerModule, 0.75f, true);

		/** This module's name, relative to its root. */
		@InnerAccess final String rootRelativeName;

		/**
		 * A {@link LimitedCache} used to avoid computing digests of files when
		 * the file's timestamp has not changed.  Each key is a {@link Long}
		 * representing the file's  {@linkplain File#lastModified() last
		 * modification time}.  The value is a byte array holding the SHA-256
		 * digest of the file content.
		 */
		private final LimitedCache<Long, byte []> digestCache =
			new LimitedCache<>(maxRecordedDigestsPerModule);

		/**
		 * Determine the cryptographic hash of the file's current contents.
		 * Since we assume that the same filename and modification time implies
		 * the same digest, we cache the digest under that combination for
		 * performance.
		 *
		 * @param resolvedModuleName
		 *        The {@link ResolvedModuleName resolved name} of the module, in
		 *        case the backing source file must be read to produce a digest.
		 * @return The digest of the file, updating the {@link #digestCache} if
		 *         necessary.
		 */
		public byte [] digestForFile (
			final ResolvedModuleName resolvedModuleName)
		{
			assert resolvedModuleName.rootRelativeName()
				.equals(rootRelativeName);
			final File sourceFile = resolvedModuleName.sourceReference();
			assert sourceFile != null;
			final long lastModification = sourceFile.lastModified();
			byte [] digest = digestCache.get(lastModification);
			if (digest == null)
			{
				// Don't bother protecting against computing the digest for the
				// same file in multiple threads.  At worst it's extra work, and
				// it's not likely that maintenance on the build mechanism would
				// *ever* cause it to do that anyhow.
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
					digestCache.put(lastModification, digest);
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
		 * Output this {@link ModuleArchive} to the provided {@link
		 * DataOutputStream}.  It can later be reconstituted via the constructor
		 * taking a {@link DataInputStream}.
		 *
		 * @param binaryStream
		 *        A DataOutputStream on which to write this module archive.
		 * @throws IOException
		 *         If I/O fails.
		 */
		public void write (final DataOutputStream binaryStream)
			throws IOException
		{
			binaryStream.writeUTF(rootRelativeName);
			binaryStream.writeInt(digestCache.size());
			for (final Map.Entry<Long, byte []> entry : digestCache.entrySet())
			{
				binaryStream.writeLong(entry.getKey());
				binaryStream.write(entry.getValue());
			}
			binaryStream.writeInt(versions.size());
			for (final Map.Entry<ModuleVersionKey, ModuleVersion> entry
				: versions.entrySet())
			{
				entry.getKey().write(binaryStream);
				entry.getValue().write(binaryStream);
			}
		}

		/**
		 * Reconstruct a {@link ModuleArchive}, having previously been
		 * written via {@link #write(DataOutputStream)}.
		 *
		 * @param binaryStream Where to read the module archive from.
		 * @throws IOException If I/O fails.
		 */
		ModuleArchive (final DataInputStream binaryStream)
			throws IOException
		{
			rootRelativeName = binaryStream.readUTF();
			int digestCount = binaryStream.readInt();
			while (digestCount-- > 0)
			{
				final long lastModification = binaryStream.readLong();
				final byte [] digest = new byte [DIGEST_SIZE];
				binaryStream.readFully(digest);
				digestCache.put(lastModification, digest);
			}
			int versionCount = binaryStream.readInt();
			while (versionCount-- > 0)
			{
				final ModuleVersionKey versionKey =
					new ModuleVersionKey(binaryStream);
				final ModuleVersion version = new ModuleVersion(binaryStream);
				versions.put(versionKey, version);
			}
		}


		/**
		 * Construct a new {@link IndexedRepositoryManager.ModuleArchive}.
		 *
		 * @param rootRelativeName
		 *        The name of the module, relative to the root of this
		 *        repository.
		 */
		public ModuleArchive (final String rootRelativeName)
		{
			this.rootRelativeName = rootRelativeName;
		}

		/**
		 * If this {@link ModuleVersion} exists in the repository, then answer
		 * it; otherwise answer {@code null}.
		 *
		 * @param versionKey
		 *        The {@link ModuleVersionKey} identifying the version of a
		 *        module's source.
		 * @return The associated {@link ModuleVersion} if present, otherwise
		 *         {@code null}.
		 */
		public @Nullable ModuleVersion getVersion (
			final ModuleVersionKey versionKey)
		{
			lock.lock();
			try
			{
				return versions.get(versionKey);
			}
			finally
			{
				lock.unlock();
			}
		}


		/**
		 * Record a {@link ModuleVersion version} of a {@linkplain
		 * ModuleDescriptor module}.  This includes information about the
		 * source's digest and the list of local imports.
		 *
		 * <p>There must not already be a version with that key in the
		 * repository.</p>
		 *
		 * @param versionKey
		 *        The {@link ModuleVersionKey} identifying the version of a
		 *        module's source.
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
				assert !versions.containsKey(versionKey);
				versions.put(versionKey, version);
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
				final ModuleVersion version = versions.get(versionKey);
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
	}

	/**
	 * An immutable key which specifies a version of some module.  It includes
	 * whether the module's name refers to a package (a directory), and the
	 * digest of the file's contents.
	 */
	public static class ModuleVersionKey
	{
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
			int h = isPackage ? 0xDEAD_BEEF : 0xA_CABBA6E;
			for (int i = 0; i < sourceDigest.length; i++)
			{
				h = h * multiplier + sourceDigest[i];
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
			binaryStream.writeBoolean(isPackage);
			binaryStream.write(sourceDigest);
		}

		@Override
		public String toString ()
		{
			return String.format(
				"VersionKey(@%s...)",
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
		 * @param sourceDigest
		 *        The digest of the module, which (cryptographically) uniquely
		 *        identifies which source code is present within this version.
		 */
		public ModuleVersionKey (
			final ResolvedModuleName moduleName,
			final byte [] sourceDigest)
		{
			assert sourceDigest.length == DIGEST_SIZE;
			this.sourceDigest = sourceDigest;
			this.isPackage = moduleName.isPackage();
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
		 * The maximum number of compilations to keep available for a particular
		 * module version.
		 */
		private final static int maxHistoricalVersionCompilations = 10;

		/**
		 * The list of entry points declared by this version of the module.
		 * Note that because the entry point declarations are in the module
		 * header and in a fixed syntax, all valid compilations of the module
		 * would produce the same list of entry points.  Therefore the entry
		 * points belong here in the module version, not with a compilation.
		 */
		private final List<String> entryPoints;

		/**
		 * The N most recently recorded compilations of this version of the
		 * module.
		 */
		@InnerAccess
		final LimitedCache<ModuleCompilationKey, ModuleCompilation>
			compilations =
				new LimitedCache<>(maxHistoricalVersionCompilations);

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
		 * Answer the list of entry point names declared by this version of the
		 * module.
		 *
		 * @return The list of entry point names.
		 */
		public List<String> getEntryPoints ()
		{
			return entryPoints;
		}

		/**
		 * The persistent record number of the {@linkplain ModuleHeader module
		 * header} for this {@linkplain ModuleVersion version}.
		 */
		private long moduleHeaderRecordNumber = -1;

		/**
		 * Write the specified byte array (encoding a {@linkplain
		 * ModuleHeader module header}) into the indexed file. Record the record
		 * position for subsequent retrieval.
		 *
		 * @param bytes
		 *        A {@linkplain Serializer serialized} module header.
		 */
		public void putModuleHeader (final byte[] bytes)
		{
			// Write the serialized data to the end of the repository.
			final IndexedRepository repo = repository();
			lock.lock();
			try
			{
				moduleHeaderRecordNumber = repo.longSize();
				repo.add(moduleHeaderRecordNumber, bytes);
				markDirty();
			}
			finally
			{
				lock.unlock();
			}
		}

		/**
		 * Answer the {@linkplain Serializer serialized} {@linkplain
		 * ModuleHeader module header} associated with this {@linkplain
		 * ModuleVersion version}.
		 *
		 * @return A serialized module header.
		 */
		public byte[] getModuleHeader ()
		{
			assert moduleHeaderRecordNumber != -1;
			lock.lock();
			try
			{
				return repository().get(moduleHeaderRecordNumber);
			}
			finally
			{
				lock.unlock();
			}
		}

		/**
		 * The persistent record number of the Stacks {@linkplain
		 * CommentTokenDescriptor comments} associated with this {@linkplain
		 * ModuleVersion version} of the {@linkplain ModuleDescriptor module}.
		 */
		private long stacksRecordNumber = -1L;

		/**
		 * Write the specified byte array (encoding a {@linkplain
		 * TupleDescriptor tuple} of {@linkplain CommentTokenDescriptor comment
		 * tokens}) into the indexed file. Record the record position for
		 * subsequent retrieval.
		 *
		 * @param bytes
		 *        A {@linkplain Serializer serialized} tuple of comment tokens.
		 */
		public void putComments (final byte[] bytes)
		{
			// Write the comment tuple to the end of the repository.
			final IndexedRepository repo = repository();
			lock.lock();
			try
			{
				stacksRecordNumber = repo.longSize();
				repo.add(stacksRecordNumber, bytes);
				markDirty();
			}
			finally
			{
				lock.unlock();
			}
		}

		/**
		 * Answer the {@linkplain Serializer serialized} {@linkplain
		 * TupleDescriptor tuple} of {@linkplain CommentTokenDescriptor comment
		 * tokens} associated with this {@linkplain ModuleVersion version}.
		 *
		 * @return A serialized tuple of comment tokens, or {@code null} if the
		 *         {@linkplain ModuleDescriptor module} has not been compiled
		 *         yet.
		 */
		public @Nullable byte[] getComments ()
		{
			if (stacksRecordNumber == -1)
			{
				return null;
			}
			assert stacksRecordNumber != -1;
			lock.lock();
			try
			{
				return repository().get(stacksRecordNumber);
			}
			finally
			{
				lock.unlock();
			}
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
			binaryStream.writeInt(entryPoints.size());
			for (final String entryPoint : entryPoints)
			{
				binaryStream.writeUTF(entryPoint);
			}
			binaryStream.writeInt(compilations.size());
			for (final Map.Entry<ModuleCompilationKey, ModuleCompilation>
				entry : compilations.entrySet())
			{
				entry.getKey().write(binaryStream);
				entry.getValue().write(binaryStream);
			}
			binaryStream.writeLong(moduleHeaderRecordNumber);
			binaryStream.writeLong(stacksRecordNumber);
		}

		@Override
		public String toString ()
		{
			return String.format(
				"Version:%n"
				+"\t\timports=%s%s%n"
				+ "\t\tcompilations=%s%n"
				+ "\t\tmoduleHeaderRecordNumber=%d%n"
				+ "\t\tstacksRecordNumber=%d%n",
				localImportNames,
				entryPoints.isEmpty()
					? ""
					: "\n\t\tentry points=" + entryPoints.toString(),
				compilations.values(),
				moduleHeaderRecordNumber,
				stacksRecordNumber);
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
			int entryPointCount = binaryStream.readInt();
			entryPoints = new ArrayList<>(entryPointCount);
			while (entryPointCount-- > 0)
			{
				entryPoints.add(binaryStream.readUTF());
			}
			int compilationsCount = binaryStream.readInt();
			while (compilationsCount-- > 0)
			{
				compilations.put(
					new ModuleCompilationKey(binaryStream),
					new ModuleCompilation(binaryStream));
			}
			moduleHeaderRecordNumber = binaryStream.readLong();
			stacksRecordNumber = binaryStream.readLong();
		}

		/**
		 * Construct a new {@link ModuleVersion}.
		 *
		 * @param moduleSize
		 *        The size of the compiled module, in bytes.
		 * @param localImportNames
		 *        The list of module names being imported.
		 * @param entryPoints
		 *        The list of entry points defined in the module.
		 */
		public ModuleVersion (
			final long moduleSize,
			final List<String> localImportNames,
			final List<String> entryPoints)
		{
			this.moduleSize = moduleSize;
			this.localImportNames = new ArrayList<>(localImportNames);
			this.entryPoints = new ArrayList<>(entryPoints);
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
	 * A {@link Map} from the {@link ResolvedModuleName#rootRelativeName() root-
	 * relative name} of each module that has ever been compiled within this
	 * repository to the corresponding ModuleArchive.
	 */
	private final Map<String, ModuleArchive> moduleMap = new HashMap<>(100);

	/**
	 * Look up the {@link ModuleArchive} with the specified name, creating one
	 * and adding it to my {@link #moduleMap} if necessary.
	 *
	 * @param rootRelativeName
	 *        The name of the module, relative to the repository's root.
	 * @return A {@link ModuleArchive} holding versioned data about this module.
	 */
	public ModuleArchive getArchive (final String rootRelativeName)
	{
		lock.lock();
		try
		{
			ModuleArchive archive = moduleMap.get(rootRelativeName);
			if (archive == null)
			{
				archive = new ModuleArchive(rootRelativeName);
				moduleMap.put(rootRelativeName, archive);
			}
			return archive;
		}
		finally
		{
			lock.unlock();
		}
	}

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
			log(Level.INFO, "Clear: %s%n", rootName);
			moduleMap.clear();
			final IndexedRepository repo = repository();
			repo.close();
			repository = null;
			try
			{
				fileName.delete();
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
				log(Level.FINER, "Commit: %s%n", rootName);
				final ByteArrayOutputStream byteStream =
					new ByteArrayOutputStream(131072);
				try (final DataOutputStream binaryStream =
					new DataOutputStream(byteStream))
				{
					binaryStream.writeInt(moduleMap.size());
					for (final ModuleArchive moduleArchive : moduleMap.values())
					{
						moduleArchive.write(binaryStream);
					}
					log(Level.FINEST, "Commit size = %d%n", byteStream.size());
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
			log(Level.FINE, "Close: %s%n", rootName);
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
	private void openOrCreate ()
	throws IndexedFileException
	{
		assert !isOpen;
		try
		{
			IndexedRepository repo = null;
			try
			{
				repo = IndexedFile.openFile(
					IndexedRepository.class, fileName, true, versionCheck);
			}
			catch (final IndexedFileException e)
			{
				log(
					Level.INFO,
					e,
					"Deleting obsolete repository: %s",
					fileName);
				repo = null;
			}
			if (repo == null)
			{
				repo =  IndexedFile.newFile(
					IndexedRepository.class, fileName, null);
			}
			final byte [] metadata = repo.metaData();
			if (metadata != null)
			{
				final ByteArrayInputStream byteStream =
					new ByteArrayInputStream(metadata);
				try (final DataInputStream binaryStream =
					new DataInputStream(byteStream))
				{
					int moduleCount = binaryStream.readInt();
					while (moduleCount-- > 0)
					{
						final ModuleArchive archive =
							new ModuleArchive(binaryStream);
						moduleMap.put(archive.rootRelativeName, archive);
					}
					assert byteStream.available() == 0;
				}
			}
			repository = repo;
			isOpen = true;
		}
		catch (final IOException e)
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
			log(
				Level.FINE,
				"Reopen if necessary %s (was open = %s)%n",
				rootName,
				isOpen);
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
		@SuppressWarnings("resource")
		final Formatter out = new Formatter();
		out.format("Repository \"%s\" with modules:", rootName);
		for (final Map.Entry<String, ModuleArchive> entry
			: moduleMap.entrySet())
		{
			out.format("%n\t%s → %s", entry.getKey(), entry.getValue());
		}
		return out.toString();
	}
}
