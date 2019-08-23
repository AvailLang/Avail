/*
 * IOSystem.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

import com.avail.AvailRuntime;
import com.avail.AvailThread;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.AtomDescriptor.SpecialAtom;
import com.avail.descriptor.PojoDescriptor;
import com.avail.utility.LRUCache;
import com.avail.utility.MutableOrNull;
import com.avail.utility.SimpleThreadFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;

import static com.avail.AvailRuntimeConfiguration.availableProcessors;
import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.utility.Nulls.stripNull;
import static java.nio.file.attribute.PosixFilePermission.*;
import static java.util.Collections.synchronizedMap;

/**
 * This aggregates socket and file I/O information and behavior specific to an
 * {@link AvailRuntime}.
 */
public class IOSystem
{
	/**
	 * The {@link AvailRuntime} that this is part of.
	 */
	public final AvailRuntime runtime;

	/**
	 * Create a new {@code IOSystem} for the given {@link AvailRuntime}.
	 * @param runtime The {@link AvailRuntime}.
	 */
	public IOSystem (final AvailRuntime runtime)
	{
		this.runtime = runtime;
	}

	/**
	 * The {@linkplain ThreadPoolExecutor thread pool executor} for asynchronous
	 * file operations performed on behalf of this {@linkplain AvailRuntime
	 * Avail runtime}.
	 */
	private final ThreadPoolExecutor fileExecutor =
		new ThreadPoolExecutor(
			availableProcessors,
			availableProcessors << 2,
			10L,
			TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(),
			new SimpleThreadFactory("AvailFile"),
			new CallerRunsPolicy());

	/**
	 * Schedule the specified {@linkplain Runnable task} for eventual execution
	 * by the {@linkplain ThreadPoolExecutor thread pool executor} for
	 * asynchronous file operations. The implementation is free to run the task
	 * immediately or delay its execution arbitrarily. The task will not execute
	 * on an {@linkplain AvailThread Avail thread}.
	 *
	 * @param task A task.
	 */
	public void executeFileTask (final Runnable task)
	{
		fileExecutor.execute(task);
	}

	/**
	 * The {@linkplain ThreadPoolExecutor thread pool executor} for asynchronous
	 * socket operations performed on behalf of this {@linkplain AvailRuntime
	 * Avail runtime}.
	 */
	private final ThreadPoolExecutor socketExecutor =
		new ThreadPoolExecutor(
			availableProcessors,
			availableProcessors << 2,
			10L,
			TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(),
			new SimpleThreadFactory("AvailSocket"),
			new CallerRunsPolicy());

	/**
	 * The {@linkplain AsynchronousChannelGroup asynchronous channel group}
	 * that manages {@linkplain AsynchronousSocketChannel asynchronous socket
	 * channels} on behalf of this {@linkplain AvailRuntime Avail runtime}.
	 */
	private final AsynchronousChannelGroup socketGroup;

	{
		try
		{
			socketGroup = AsynchronousChannelGroup.withThreadPool(
				socketExecutor);
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Schedule the specified {@linkplain Runnable task} for eventual
	 * execution by the {@linkplain ThreadPoolExecutor thread pool executor} for
	 * asynchronous socket operations. The implementation is free to run the
	 * task immediately or delay its execution arbitrarily. The task will not
	 * execute on an {@linkplain AvailThread Avail thread}.
	 *
	 * @param task A task.
	 */
	@SuppressWarnings("unused")
	void executeSocketTask (final Runnable task)
	{
		socketExecutor.execute(task);
	}

	/**
	 * Open an {@linkplain AsynchronousFileChannel asynchronous file channel}
	 * for the specified {@linkplain Path path}.
	 *
	 * @param path
	 *        A path.
	 * @param options
	 *        The {@linkplain OpenOption open options}.
	 * @param attributes
	 *        The {@linkplain FileAttribute file attributes} (for newly created
	 *        files only).
	 * @return An asynchronous file channel.
	 * @throws IllegalArgumentException
	 *         If the combination of options is invalid.
	 * @throws UnsupportedOperationException
	 *         If an option is invalid for the specified path.
	 * @throws SecurityException
	 *         If the {@linkplain SecurityManager security manager} denies
	 *         permission to complete the operation.
	 * @throws IOException
	 *         If the open fails for any reason.
	 */
	@SuppressWarnings("ThrowsRuntimeException")
	public AsynchronousFileChannel openFile (
		final Path path,
		final Set<? extends OpenOption> options,
		final FileAttribute<?>... attributes)
	throws
		IllegalArgumentException,
		UnsupportedOperationException,
		SecurityException,
		IOException
	{
		return AsynchronousFileChannel.open(
			path, options, fileExecutor, attributes);
	}

	/** The default {@linkplain FileSystem file system}. */
	private static final FileSystem fileSystem = FileSystems.getDefault();

	/**
	 * Answer the default {@linkplain FileSystem file system}.
	 *
	 * @return The default file system.
	 */
	public static FileSystem fileSystem ()
	{
		return fileSystem;
	}

	/**
	 * The {@linkplain LinkOption link options} for following symbolic links.
	 */
	private static final LinkOption[] followSymlinks = {};

	/**
	 * The {@linkplain LinkOption link options} for forbidding traversal of
	 * symbolic links.
	 */
	private static final LinkOption[] doNotFollowSymbolicLinks =
		{LinkOption.NOFOLLOW_LINKS};

	/**
	 * Answer the appropriate {@linkplain LinkOption link options} for
	 * following, or not following, symbolic links.
	 *
	 * @param shouldFollow
	 *        {@code true} for an array that permits symbolic link traversal,
	 *        {@code false} for an array that forbids symbolic link traversal.
	 * @return An array of link options.
	 */
	public static LinkOption[] followSymlinks (final boolean shouldFollow)
	{
		return shouldFollow ? followSymlinks : doNotFollowSymbolicLinks;
	}

	/**
	 * The {@linkplain PosixFilePermission POSIX file permissions}. <em>The
	 * order of these elements should not be changed!</em>
	 */
	private static final PosixFilePermission[] posixPermissions =
		{
			OWNER_READ,
			OWNER_WRITE,
			OWNER_EXECUTE,
			GROUP_READ,
			GROUP_WRITE,
			GROUP_EXECUTE,
			OTHERS_READ,
			OTHERS_WRITE,
			OTHERS_EXECUTE
		};

	/**
	 * The {@linkplain PosixFilePermission POSIX file permissions}.
	 *
	 * @return The POSIX file permissions.
	 */
	public static PosixFilePermission[] posixPermissions ()
	{
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		return posixPermissions;
	}

	/**
	 * A {@code BufferKey} identifies a file buffer in the {@linkplain
	 * #cachedBuffers buffer cache}.
	 */
	public static final class BufferKey
	{
		/**
		 * The {@linkplain FileHandle file handle} that represents the
		 * provenance of the associated buffer.
		 */
		final FileHandle fileHandle;

		/**
		 * The start position of the buffer within the underlying file. This
		 * value is measured in bytes, and need not be aligned.
		 */
		final long startPosition;

		/**
		 * Construct a new buffer key, used to identify a buffer in the global
		 * cache.
		 *
		 * @param fileHandle
		 *        The {@link FileHandle} that represents the provenance of the
		 *        associated buffer.
		 * @param startPosition
		 *        The start position of the buffer within the underlying file.
		 *        This value is measured in bytes, and need not be aligned.
		 */
		public BufferKey (
			final FileHandle fileHandle,
			final long startPosition)
		{
			this.fileHandle = fileHandle;
			this.startPosition = startPosition;
		}

		@Override
		public boolean equals (final @Nullable Object obj)
		{
			if (obj instanceof BufferKey)
			{
				final BufferKey other = (BufferKey) obj;
				return fileHandle == other.fileHandle
					&& startPosition == other.startPosition;
			}
			return false;
		}

		@Override
		public int hashCode ()
		{
			int h = fileHandle.hashCode();
			h *= multiplier;
			h ^= 0xD198EA23;
			h += (int) (startPosition >> 32);
			h *= multiplier;
			h ^= 0x918F7711;
			h += (int) startPosition;
			h *= multiplier;
			h ^= 0x32AE891D;
			return h;
		}
	}

	/**
	 * A {@code FileHandle} is an abstraction which wraps an {@link
	 * AsynchronousFileChannel} with some additional information like filename
	 * and buffer alignment.  It gets stuffed in a {@linkplain PojoDescriptor
	 * pojo} in a {@linkplain SpecialAtom#FILE_KEY property} of the
	 * {@linkplain AtomDescriptor atom} that serves as Avail's most basic view
	 * of a file handle.  Sockets use a substantially similar technique.
	 *
	 * <p>In addition, the {@code FileHandle} weakly tracks which buffers need
	 * to be evicted from Avail's {@link #cachedBuffers file buffer cache}.</p>
	 *
	 * @author Mark van Gulik&lt;mark@availlang.org&gt;
	 */
	public static final class FileHandle
	{
		/** The name of the file. */
		public final A_String filename;

		/**
		 * The buffer alignment for the file.  Reading is only ever attempted on
		 * this file at buffer boundaries.  There is a {@linkplain
		 * #getBuffer(BufferKey) global file buffer cache}, which is an {@link
		 * LRUCache} of buffers across all open files.  Each buffer in the cache
		 * has a length exactly equal to that file handle's alignment. A file
		 * will often have a partial buffer at the end due to its size not being
		 * an integral multiple of the alignment.  Such a partial buffer is
		 * always excluded from the global file buffer cache.
		 */
		public final int alignment;

		/** Whether this file can be read. */
		public final boolean canRead;

		/** Whether this file can be written. */
		public final boolean canWrite;

		/**
		 * The underlying {@link AsynchronousFileChannel} through which input
		 * and/or output takes place.
		 */
		public final AsynchronousFileChannel channel;

		/**
		 * A weak set of {@link BufferKey}s pertaining to this file, for which
		 * there may be entries in the {@linkplain #getBuffer(BufferKey) global
		 * file buffer cache}.  Since the buffer keys are specific to each
		 * {@link FileHandle}, they are removed from the cache explicitly when
		 * the file is closed.  This weak set allows the cache removals to
		 * happen efficiently.
		 */
		public final Map<BufferKey, Void> bufferKeys =
			synchronizedMap(new WeakHashMap<>());

		/**
		 * Construct a new file handle.
		 *
		 * @param filename The {@link A_String name} of the file.
		 * @param alignment The alignment by which to access the file.
		 * @param canRead Whether the file can be read.
		 * @param canWrite Whether the file can be written.
		 * @param channel The {@link AsynchronousFileChannel} with which to do
		 *                reading and writing.
		 */
		public FileHandle (
			final A_String filename,
			final int alignment,
			final boolean canRead,
			final boolean canWrite,
			final AsynchronousFileChannel channel)
		{
			this.filename = filename;
			this.alignment = alignment;
			this.canRead = canRead;
			this.canWrite = canWrite;
			this.channel = channel;
		}
	}

	/**
	 * Maintain an {@link LRUCache} of file buffers.  This allows us to avoid
	 * a trip to the operating system to re-fetch recently accessed buffers of
	 * data, which is especially powerful since the buffers are shared
	 * (immutable and thread-safe).
	 *
	 * <p>A miss for this cache doesn't actually read the necessary data from
	 * the operating system.  Instead, it simply creates a {@link MutableOrNull}
	 * initially.  The client is responsible for reading the actual data that
	 * should be stored into the {@code MutableOrNull}.</p>
	 */
	private final LRUCache<
		BufferKey,
		MutableOrNull<A_Tuple>>
		cachedBuffers = new LRUCache<>(
			10000,
			10,
			key -> new MutableOrNull<>(),
			(key, value) ->
			{
				assert value != null;
				// Just clear the mutable's value slot, freeing the actual
				// buffer.
				value.value = null;
			});

	/**
	 * Answer the {@linkplain MutableOrNull container} responsible for the
	 * {@linkplain A_Tuple buffer} indicated by the supplied {@linkplain
	 * BufferKey key}.
	 *
	 * @param key
	 *        A key.
	 * @return A container for a buffer, possibly empty.
	 */
	public MutableOrNull<A_Tuple> getBuffer (final BufferKey key)
	{
		return stripNull(cachedBuffers.get(key));
	}

	/**
	 * Discard the {@linkplain MutableOrNull container} responsible for the
	 * {@linkplain A_Tuple buffer} indicated by the supplied {@linkplain
	 * BufferKey key}.
	 *
	 * @param key
	 *        A key.
	 */
	public void discardBuffer (final BufferKey key)
	{
		cachedBuffers.remove(key);
	}

	/**
	 * Open an {@linkplain AsynchronousServerSocketChannel asynchronous server
	 * socket channel}.
	 *
	 * @return An asynchronous server socket channel.
	 * @throws IOException
	 *         If the open fails for some reason.
	 */
	public AsynchronousServerSocketChannel openServerSocket ()
	throws IOException
	{
		return AsynchronousServerSocketChannel.open(socketGroup);
	}

	/**
	 * Open an {@linkplain AsynchronousSocketChannel asynchronous socket
	 * channel}.
	 *
	 * @return An asynchronous socket channel.
	 * @throws IOException
	 *         If the open fails for some reason.
	 */
	public AsynchronousSocketChannel openSocket () throws IOException
	{
		return AsynchronousSocketChannel.open(socketGroup);
	}

	/**
	 * Destroy all data structures used by this {@code AvailRuntime}.  Also
	 * disassociate it from the current {@link Thread}'s local storage.
	 */
	public void destroy ()
	{
		fileExecutor.shutdownNow();
		try
		{
			socketGroup.shutdownNow();
		}
		catch (final IOException e)
		{
			// Ignore.
		}

		try
		{
			fileExecutor.awaitTermination(10, TimeUnit.SECONDS);
		}
		catch (final InterruptedException e)
		{
			// Ignore.
		}

		try
		{
			socketGroup.awaitTermination(10, TimeUnit.SECONDS);
		}
		catch (final InterruptedException e)
		{
			// Ignore.
		}
	}


}
