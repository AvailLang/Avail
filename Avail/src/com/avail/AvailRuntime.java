/**
 * AvailRuntime.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static java.nio.file.attribute.PosixFilePermission.*;
import java.io.*;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.avail.annotations.*;
import com.avail.builder.*;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.FiberDescriptor.TraceFlag;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.exceptions.*;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.io.TextInterface;
import com.avail.utility.LRUCache;
import com.avail.utility.MutableOrNull;
import com.avail.utility.evaluation.*;

/**
 * An {@code AvailRuntime} comprises the {@linkplain ModuleDescriptor
 * modules}, {@linkplain MethodDescriptor methods}, and {@linkplain
 * #specialObject(int) special objects} that define an Avail system. It also
 * manages global resources, such as file connections.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class AvailRuntime
{
	/** The build version, set by the build process. */
	private static final String buildVersion;

	/*
	 * Initialize the build version from a resource bundled with the
	 * distribution JAR.
	 */
	static
	{
		String version = "dev";
		try (
			final InputStream resourceStream =
				ClassLoader.getSystemResourceAsStream(
					"resources/build.time.txt"))
		{
			if (resourceStream != null)
			{
				final Scanner scanner = new Scanner(resourceStream);
				version = scanner.nextLine();
				scanner.close();
			}
		}
		catch (final IOException e)
		{
			version = "UNKNOWN";
		}
		buildVersion = version;
	}

	/**
	 * This is a volatile static field used for the sole purpose of establishing
	 * very weak happens-before relations between threads without using mutual
	 * exclusions or any blocking other than memory barriers.
	 */
	private static volatile int synchronizationBarrierField;

	/**
	 * Ensure writes from this thread that happen before the call to
	 * writeBarrier() complete before any writes that occur after.  If another
	 * thread performs a readBarrier() and then sees a value that was written
	 * after the writeBarrier(), then any subsequent read will be guaranteed to
	 * see values at least as recent as the writes that occurred before the
	 * writeBarrier().
	 *
	 * <p>This is a very weak condition, and should only be used to ensure
	 * a weak ordering of writes and reads suitable for implementing the
	 * double-check pattern.  Or anti-pattern, since it's so difficult to get
	 * right in general.</p>
	 */
	public static void writeBarrier ()
	{
		synchronizationBarrierField = 0;
	}

	/**
	 * Say some other thread performs some writes, then executes a writeBarrier,
	 * then performs more writes.  Ensure that after the readBarrier completes,
	 * if an observation of a write performed after the writeBarrier is
	 * detected, all values written before the writeBarrier will be visible.
	 */
	public static void readBarrier ()
	{
		@SuppressWarnings("unused")
		final int ignored = synchronizationBarrierField;
	}

	/**
	 * Answer the build version, as set by the build process.
	 *
	 * @return The build version, or {@code "dev"} if Avail is not running from
	 *         a distribution JAR.
	 */
	public static final String buildVersion ()
	{
		return buildVersion;
	}

	/**
	 * The active versions of the Avail virtual machine. These are the versions
	 * for which the virtual machine guarantees compatibility.
	 */
	private static final String[] activeVersions = {"1.0.0 DEV 2014-04-28"};

	/**
	 * Answer the active versions of the Avail virtual machine. These are the
	 * versions for which the virtual machine guarantees compatibility.
	 *
	 * @return The active versions.
	 */
	public static final A_Set activeVersions ()
	{
		A_Set versions = SetDescriptor.empty();
		for (final String version : activeVersions)
		{
			versions = versions.setWithElementCanDestroy(
				StringDescriptor.from(version),
				true);
		}
		return versions;
	}

	/**
	 * Answer the {@linkplain AvailRuntime Avail runtime} associated with the
	 * current {@linkplain Thread thread}.
	 *
	 * @return The Avail runtime of the current thread.
	 */
	public static final AvailRuntime current ()
	{
		return ((AvailThread) Thread.currentThread()).runtime;
	}

	/**
	 * A general purpose {@linkplain Random pseudo-random number generator}.
	 */
	private static final Random rng = new Random();

	/**
	 * Answer a new value suitable for use as the {@linkplain AvailObject#hash()
	 * hash code} for an immutable {@linkplain AvailObject value}.
	 *
	 * <p>Note that the implementation uses opportunistic locking internally, so
	 * explicit synchronization here is not required.  However, synchronization
	 * is included anyhow since that behavior is not part of Random's
	 * specification.</p>
	 *
	 * @return A 32-bit pseudo-random number.
	 */
	@ThreadSafe
	public static synchronized int nextHash ()
	{
		return rng.nextInt();
	}

	/**
	 * The source of {@linkplain FiberDescriptor fiber} identifiers.
	 */
	private static final AtomicInteger fiberIdGenerator = new AtomicInteger(1);

	/**
	 * Answer the next unused {@linkplain FiberDescriptor fiber} identifier.
	 * Fiber identifiers will not repeat for 2^32 invocations.
	 *
	 * @return The next fiber identifier.
	 */
	@ThreadSafe
	public static int nextFiberId ()
	{
		return fiberIdGenerator.getAndIncrement();
	}

	/**
	 * The {@linkplain ThreadFactory thread factory} for creating {@link
	 * AvailThread}s on behalf of this {@linkplain AvailRuntime Avail runtime}.
	 */
	private final ThreadFactory executorThreadFactory = new ThreadFactory()
	{
		@Override
		public AvailThread newThread (final @Nullable Runnable runnable)
		{
			assert runnable != null;
			return new AvailThread(
				runnable,
				new Interpreter(AvailRuntime.this));
		}
	};

	/**
	 * The {@linkplain ThreadFactory thread factory} for creating {@link
	 * Thread}s for processing file I/O on behalf of this {@linkplain
	 * AvailRuntime Avail runtime}.
	 */
	private final ThreadFactory fileThreadFactory = new ThreadFactory()
	{
		AtomicInteger counter = new AtomicInteger();

		@Override
		public Thread newThread (final @Nullable Runnable runnable)
		{
			assert runnable != null;
			return new Thread(
				runnable, "AvailFile-" + counter.incrementAndGet());
		}
	};

	/**
	 * The {@linkplain ThreadFactory thread factory} for creating {@link
	 * Thread}s for processing socket I/O on behalf of this {@linkplain
	 * AvailRuntime Avail runtime}.
	 */
	private final ThreadFactory socketThreadFactory = new ThreadFactory()
	{
		AtomicInteger counter = new AtomicInteger();

		@Override
		public Thread newThread (final @Nullable Runnable runnable)
		{
			assert runnable != null;
			return new Thread(
				runnable, "AvailSocket-" + counter.incrementAndGet());
		}
	};

	/** The number of available processors. */
	private static final int availableProcessors =
		Runtime.getRuntime().availableProcessors();

	/**
	 * The maximum number of {@link Interpreter}s that can be constructed for
	 * this runtime.
	 */
	public static final int maxInterpreters = availableProcessors * 3;

	/**
	 * A counter from which unique interpreter indices in [0..maxInterpreters)
	 * are allotted thread-safely.
	 */
	public static int nextInterpreterIndex = 0;

	/**
	 * Allocate the next interpreter index in [0..maxInterpreters)
	 * thread-safely.
	 *
	 * @return A new unique interpreter index.
	 */
	public synchronized int allocateInterpreterIndex ()
	{
		final int index = nextInterpreterIndex;
		nextInterpreterIndex++;
		assert 0 <= index && index < maxInterpreters;
		return index;
	}

	/**
	 * The {@linkplain ThreadPoolExecutor thread pool executor} for
	 * this {@linkplain AvailRuntime Avail runtime}.
	 */
	private final ThreadPoolExecutor executor =
		new ThreadPoolExecutor(
			availableProcessors,
			maxInterpreters,
			10L,
			TimeUnit.SECONDS,
			new PriorityBlockingQueue<Runnable>(100),
			executorThreadFactory,
			new ThreadPoolExecutor.AbortPolicy());

	/**
	 * Schedule the specified {@linkplain AvailTask task} for eventual
	 * execution. The implementation is free to run the task immediately or
	 * delay its execution arbitrarily. The task is guaranteed to execute on an
	 * {@linkplain AvailThread Avail thread}.
	 *
	 * @param task A task.
	 */
	public void execute (final AvailTask task)
	{
		executor.execute(task);
	}

	/**
	 * The {@linkplain ThreadPoolExecutor thread pool executor} for asynchronous
	 * file operations performed on behalf of this {@linkplain AvailRuntime
	 * Avail runtime}.
	 */
	private final ThreadPoolExecutor fileExecutor =
		new ThreadPoolExecutor(
			availableProcessors,
			availableProcessors * 4,
			10L,
			TimeUnit.SECONDS,
			new LinkedBlockingQueue<Runnable>(10),
			fileThreadFactory,
			new ThreadPoolExecutor.CallerRunsPolicy());

	/**
	 * Schedule the specified {@linkplain AvailTask task} for eventual execution
	 * by the {@linkplain ThreadPoolExecutor thread pool executor} for
	 * asynchronous file operations. The implementation is free to run the task
	 * immediately or delay its execution arbitrarily. The task will not execute
	 * on an {@linkplain AvailThread Avail thread}.
	 *
	 * @param task A task.
	 */
	public void executeFileTask (final AvailTask task)
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
			availableProcessors * 4,
			10L,
			TimeUnit.SECONDS,
			new LinkedBlockingQueue<Runnable>(),
			socketThreadFactory,
			new ThreadPoolExecutor.CallerRunsPolicy());

	/**
	 * The {@linkplain AsynchronousChannelGroup asynchronous channel group}
	 * that manages {@linkplain AsynchronousSocketChannel asynchronous socket
	 * channels} on behalf of this {@linkplain AvailRuntime Avail runtime}.
	 */
	public final AsynchronousChannelGroup socketGroup;

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
	public void executeSocketTask (final Runnable task)
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
	 * @param attrs
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
	public AsynchronousFileChannel openFile (
			final Path path,
			final Set<? extends OpenOption> options,
			final FileAttribute<?>... attrs)
		throws
			IllegalArgumentException,
			UnsupportedOperationException,
			SecurityException,
			IOException
	{
		return AsynchronousFileChannel.open(
			path, options, fileExecutor, attrs);
	}

	/** The default {@linkplain FileSystem file system}. */
	private final static FileSystem fileSystem = FileSystems.getDefault();

	/**
	 * Answer the default {@linkplain FileSystem file system}.
	 *
	 * @return The default file system.
	 */
	public FileSystem fileSystem ()
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
	private static final LinkOption[] dontFollowSymlinks =
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
		return shouldFollow ? followSymlinks : dontFollowSymlinks;
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
		 * Construct a new {@link BufferKey}.
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
			h += (int)(startPosition >> 32);
			h *= multiplier;
			h ^= 0x918F7711;
			h += (int)startPosition;
			h *= multiplier;
			h ^= 0x32AE891D;
			return h;
		}
	}

	/**
	 * A {@code FileHandle} is an abstraction which wraps an {@link
	 * AsynchronousFileChannel} with some additional information like filename
	 * and buffer alignment.  It gets stuffed in a {@linkplain PojoDescriptor
	 * pojo} in a {@linkplain AtomDescriptor#fileKey() property} of the
	 * {@linkplain AtomDescriptor atom} that serves as Avail's most basic view
	 * of a file handle.  Sockets use a substantially similar technique.
	 *
	 * <p>
	 * In addition, the {@code FileHandle} weakly tracks which buffers need to
	 * be evicted from Avail's {@link AvailRuntime#cachedBuffers file buffer
	 * cache}.
	 * </p>
	 *
	 * @author Mark van Gulik&lt;mark@availlang.org&gt;
	 */
	public static final class FileHandle
	{
		/** The name of the file. */
		public final A_String filename;

		/**
		 * The buffer alignment for the file.  Reading is only ever attempted
		 * on this file at buffer boundaries.  There is a {@linkplain
		 * AvailRuntime#getBuffer(BufferKey) global file buffer cache}, which is
		 * an {@link LRUCache} of buffers across all open files.  Each buffer in
		 * the cache has a length exactly equal to that file handle's alignment.
		 * A file will often have a partial buffer at the end due to its size
		 * not being an integral multiple of the alignment.  Such a partial
		 * buffer is always excluded from the global file buffer cache.
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
		 * there may be entries in the {@linkplain
		 * AvailRuntime#getBuffer(BufferKey) global file buffer cache}.  Since
		 * the buffer keys are specific to each {@link FileHandle}, they are
		 * removed from the cache explicitly when the file is closed.  This weak
		 * set allows the cache removals to happen efficiently.
		 */
		public final WeakHashMap<BufferKey, Void> bufferKeys =
			new WeakHashMap<>();

		/**
		 * Construct a new {@link AvailRuntime.FileHandle}.
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
	 * data, which is especially powerful since the buffers are {@linkplain
	 * Mutability#SHARED shared} (immutable and thread-safe).
	 *
	 * <p>A miss for this cache doesn't actually read the necessary data from
	 * the operating system.  Instead, it simply creates a {@link MutableOrNull}
	 * initially.  The client is responsible for reading the actual data that
	 * should be stored into the {@code MutableOrNull}.</p>
	 */
	@SuppressWarnings("javadoc")
	private final LRUCache<
			BufferKey,
			MutableOrNull<A_Tuple>>
		cachedBuffers = new LRUCache<>(
			10000,
			10,
			new Transformer1<
				BufferKey,
				MutableOrNull<A_Tuple>>()
			{
				@Override
				public MutableOrNull<A_Tuple> value (
					final @Nullable BufferKey key)
				{
					assert key != null;
					return new MutableOrNull<>();
				}
			},
			new Continuation2<
				BufferKey,
				MutableOrNull<A_Tuple>>()
			{
				@Override
				public void value (
					final @Nullable BufferKey key,
					final @Nullable MutableOrNull<A_Tuple> value)
				{
					assert value != null;
					// Just clear the mutable's value slot, freeing the actual
					// buffer.
					value.value = null;
				}
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
		final MutableOrNull<A_Tuple> buffer = cachedBuffers.get(key);
		assert buffer != null;
		return buffer;
	}

	/**
	 * Discard the {@linkplain MutableOrNull container} responsible for the
	 * {@linkplain A_Tuple buffer} indicated by the supplied {@linkplain
	 * BufferKey key}.
	 *
	 * @param key
	 *        A key.
	 * @return A container for the associated buffer, or {@code null} if the
	 *         buffer is no longer valid.
	 */
	public @Nullable MutableOrNull<A_Tuple> discardBuffer (final BufferKey key)
	{
		return cachedBuffers.remove(key);
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
	 * The {@linkplain Timer timer} that managed scheduled {@linkplain
	 * TimerTask tasks} for this {@linkplain AvailRuntime runtime}. The timer
	 * thread is not an {@linkplain AvailThread Avail thread}, and therefore
	 * cannot directly execute {@linkplain FiberDescriptor fibers}. It may,
	 * however, schedule fiber-related tasks.
	 */
	public final Timer timer = new Timer(
		String.format("timer for %s", this),
		true);

	/**
	 * The number of clock ticks since this {@linkplain AvailRuntime runtime}
	 * was created.
	 */
	public final AtomicLong clock = new AtomicLong(0);

	{
		// Schedule a fixed-rate timer task to increment the runtime clock.
		timer.schedule(
			new TimerTask()
			{
				@Override
				public void run ()
				{
					clock.incrementAndGet();
				}
			},
			1,
			1);
	}

	/**
	 * Whether to show all {@link MacroDefinitionDescriptor macro} expansions as
	 * they happen.
	 */
	public boolean debugMacroExpansions = false;

	/**
	 * The {@linkplain ModuleNameResolver module name resolver} that this
	 * {@linkplain AvailRuntime runtime} should use to resolve unqualified
	 * {@linkplain ModuleDescriptor module} names.
	 */
	private final ModuleNameResolver moduleNameResolver;

	/**
	 * Answer the {@linkplain ModuleNameResolver module name resolver} that this
	 * {@linkplain AvailRuntime runtime} should use to resolve unqualified
	 * {@linkplain ModuleDescriptor module} names.
	 *
	 * @return A {@linkplain ModuleNameResolver module name resolver}.
	 */
	public ModuleNameResolver moduleNameResolver ()
	{
		final ModuleNameResolver resolver = moduleNameResolver;
		assert resolver != null;
		return resolver;
	}

	/**
	 * Answer the Avail {@linkplain ModuleRoots module roots}.
	 *
	 * @return The Avail {@linkplain ModuleRoots module roots}.
	 */
	@ThreadSafe
	public ModuleRoots moduleRoots ()
	{
		return moduleNameResolver().moduleRoots();
	}

	/**
	 * The {@linkplain ClassLoader class loader} that should be used to locate
	 * and load Java {@linkplain Class classes}.
	 */
	private final ClassLoader classLoader;

	/**
	 * Answer the {@linkplain ClassLoader class loader} that should be used to
	 * locate and load Java {@linkplain Class classes}.
	 *
	 * @return A class loader.
	 */
	public ClassLoader classLoader ()
	{
		return classLoader;
	}

	/**
	 * The {@linkplain AvailRuntime runtime}'s default {@linkplain
	 * TextInterface text interface}.
	 */
	private TextInterface textInterface = TextInterface.system();

	/**
	 * A {@linkplain RawPojoDescriptor raw pojo} wrapping the {@linkplain
	 * #textInterface default} {@linkplain TextInterface text interface}.
	 */
	private AvailObject textInterfacePojo =
		RawPojoDescriptor.identityWrap(textInterface);

	/**
	 * Answer the {@linkplain AvailRuntime runtime}'s default {@linkplain
	 * TextInterface text interface}.
	 *
	 * @return The default text interface.
	 */
	@ThreadSafe
	public TextInterface textInterface ()
	{
		runtimeLock.readLock().lock();
		try
		{
			return textInterface;
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Answer the {@linkplain RawPojoDescriptor raw pojo} that wraps the
	 * {@linkplain #textInterface default} {@linkplain TextInterface text
	 * interface}.
	 *
	 * @return The raw pojo holding the default text interface.
	 */
	@ThreadSafe
	public AvailObject textInterfacePojo ()
	{
		runtimeLock.readLock().lock();
		try
		{
			return textInterfacePojo;
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Set the {@linkplain AvailRuntime runtime}'s default {@linkplain
	 * TextInterface text interface}.
	 *
	 * @param textInterface
	 *        The new default text interface.
	 */
	@ThreadSafe
	public void setTextInterface (final TextInterface textInterface)
	{
		runtimeLock.writeLock().lock();
		try
		{
			this.textInterface = textInterface;
			this.textInterfacePojo =
				RawPojoDescriptor.identityWrap(textInterface);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * The {@linkplain FunctionDescriptor function} that performs
	 * stringification. It accepts a single argument, the value to stringify.
	 */
	private volatile @Nullable A_Function stringificationFunction;

	/**
	 * Answer the {@linkplain FunctionDescriptor atom} that performs
	 * stringification.
	 *
	 * @return The requested function, or {@code null} if no such function has
	 *         been made known to the implementation.
	 */
	@ThreadSafe
	public @Nullable A_Function stringificationFunction ()
	{
		return stringificationFunction;
	}

	/**
	 * Set the {@linkplain FunctionDescriptor function} that performs
	 * stringification.
	 *
	 * @param function
	 *        The stringification function.
	 */
	@ThreadSafe
	public void setStringificationFunction (final A_Function function)
	{
		stringificationFunction = function;
	}

	/**
	 * The {@linkplain FunctionDescriptor function} to invoke whenever an
	 * unassigned variable is read.
	 */
	private volatile A_Function unassignedVariableReadFunction;

	/**
	 * Answer the {@linkplain FunctionDescriptor function} to invoke whenever an
	 * unassigned variable is read.
	 *
	 * @return The requested function.
	 */
	@ThreadSafe
	public A_Function unassignedVariableReadFunction ()
	{
		return unassignedVariableReadFunction;
	}

	/**
	 * Set the {@linkplain FunctionDescriptor function} to invoke whenever an
	 * unassigned variable is read.
	 *
	 * @param function
	 *        The function to invoke whenever an unassigned variable is read.
	 */
	@ThreadSafe
	public void setUnassignedVariableReadFunction (final A_Function function)
	{
		unassignedVariableReadFunction = function;
	}

	/**
	 * The {@linkplain FunctionDescriptor function} to invoke whenever an
	 * unassigned variable is read.
	 */
	private volatile A_Function resultDisagreedWithExpectedTypeFunction;

	/**
	 * Answer the {@linkplain FunctionDescriptor function} to invoke whenever
	 * the value produced by a {@linkplain MethodDescriptor method} send
	 * disagrees with the {@linkplain TypeDescriptor type} expected.
	 *
	 * @return The requested function.
	 */
	@ThreadSafe
	public A_Function resultDisagreedWithExpectedTypeFunction ()
	{
		return resultDisagreedWithExpectedTypeFunction;
	}

	/**
	 * Set the {@linkplain FunctionDescriptor function} to invoke whenever
	 * the value produced by a {@linkplain MethodDescriptor method} send
	 * disagrees with the {@linkplain TypeDescriptor type} expected.
	 *
	 * @param function
	 *        The function to invoke whenever the value produced by a method
	 *        send disagrees with the type expected.
	 */
	@ThreadSafe
	public void setResultDisagreedWithExpectedTypeFunction (
		final A_Function function)
	{
		resultDisagreedWithExpectedTypeFunction = function;
	}

	/**
	 * The {@linkplain FunctionDescriptor function} to invoke whenever a
	 * {@linkplain VariableDescriptor variable} with {@linkplain
	 * VariableAccessReactor write reactors} is written when {@linkplain
	 * TraceFlag#TRACE_VARIABLE_WRITES write tracing} is not enabled.
	 */
	private volatile A_Function implicitObserveFunction;

	/**
	 * Answer the {@linkplain FunctionDescriptor function} to invoke whenever
	 * a {@linkplain VariableDescriptor variable} with {@linkplain
	 * VariableAccessReactor write reactors} is written when {@linkplain
	 * TraceFlag#TRACE_VARIABLE_WRITES write tracing} is not enabled.
	 *
	 * @return The requested function.
	 */
	@ThreadSafe
	public A_Function implicitObserveFunction ()
	{
		return implicitObserveFunction;
	}

	/**
	 * Set the {@linkplain FunctionDescriptor function} to invoke whenever a
	 * {@linkplain VariableDescriptor variable} with {@linkplain
	 * VariableAccessReactor write reactors} is written when {@linkplain
	 * TraceFlag#TRACE_VARIABLE_WRITES write tracing} is not enabled.
	 *
	 * @param function
	 *        The function to invoke whenever a variable with write reactors is
	 *        written when write tracing is not enabled.
	 */
	@ThreadSafe
	public void setImplicitObserveFunction (final A_Function function)
	{
		implicitObserveFunction = function;
	}

	/**
	 * The {@linkplain FunctionDescriptor function} to invoke whenever a
	 * {@linkplain MethodDescriptor method} send fails for a definitional
	 * reason.
	 */
	private volatile A_Function invalidMessageSendFunction;

	/**
	 * Answer the {@linkplain FunctionDescriptor function} to invoke whenever a
	 * {@linkplain MethodDescriptor method} send fails for a definitional
	 * reason.
	 *
	 * @return The function to invoke whenever a message send fails dynamically
	 *         because of an ambiguous, invalid, or incomplete lookup.
	 */
	@ThreadSafe
	public A_Function invalidMessageSendFunction ()
	{
		return invalidMessageSendFunction;
	}

	/**
	 * Set the {@linkplain FunctionDescriptor function} to invoke whenever a
	 * {@linkplain MethodDescriptor method} send fails for a definitional
	 * reason.
	 *
	 * @param function
	 *        The function to invoke whenever a message send fails dynamically
	 *        because of an ambiguous, invalid, or incomplete lookup.
	 */
	@ThreadSafe
	public void setInvalidMessageSendFunction (final A_Function function)
	{
		invalidMessageSendFunction = function;
	}

	{
		final A_Function function = FunctionDescriptor.newCrashFunction(
			TupleDescriptor.empty());
		unassignedVariableReadFunction = function;
		resultDisagreedWithExpectedTypeFunction =
			FunctionDescriptor.newCrashFunction(
				TupleDescriptor.from(
					FunctionTypeDescriptor.mostGeneralType(),
					InstanceMetaDescriptor.topMeta(),
					VariableTypeDescriptor.wrapInnerType(ANY.o())));
		implicitObserveFunction = FunctionDescriptor.newCrashFunction(
			TupleDescriptor.from(
				FunctionTypeDescriptor.mostGeneralType(),
				TupleTypeDescriptor.mostGeneralType()));
		invalidMessageSendFunction = FunctionDescriptor.newCrashFunction(
			TupleDescriptor.from(
				AbstractEnumerationTypeDescriptor.withInstances(
					TupleDescriptor.from(
							E_NO_METHOD.numericCode(),
							E_NO_METHOD_DEFINITION.numericCode(),
							E_AMBIGUOUS_METHOD_DEFINITION.numericCode(),
							E_FORWARD_METHOD_DEFINITION.numericCode(),
							E_ABSTRACT_METHOD_DEFINITION.numericCode())
						.asSet()),
				METHOD.o(),
				TupleTypeDescriptor.mostGeneralType()));
	}

	/**
	 * Construct a new {@link AvailRuntime}.
	 *
	 * @param moduleNameResolver
	 *        The {@linkplain ModuleNameResolver module name resolver} that this
	 *        {@linkplain AvailRuntime runtime} should use to resolve
	 *        unqualified {@linkplain ModuleDescriptor module} names.
	 * @param classLoader
	 *        The {@linkplain ClassLoader class loader} that should be used to
	 *        locate and dynamically load Java {@linkplain Class classes}.
	 */
	public AvailRuntime (
		final ModuleNameResolver moduleNameResolver,
		final ClassLoader classLoader)
	{
		this.moduleNameResolver = moduleNameResolver;
		this.classLoader = classLoader;
	}

	/**
	 * Construct a new {@link AvailRuntime}. Use the {@linkplain ClassLoader
	 * class loader} that loaded this {@linkplain Class class}.
	 *
	 * @param moduleNameResolver
	 *        The {@linkplain ModuleNameResolver module name resolver} that this
	 *        {@linkplain AvailRuntime runtime} should use to resolve
	 *        unqualified {@linkplain ModuleDescriptor module} names.
	 */
	public AvailRuntime (final ModuleNameResolver moduleNameResolver)
	{
		this(moduleNameResolver, AvailRuntime.class.getClassLoader());
	}

	/**
	 * The {@linkplain ReentrantReadWriteLock lock} that protects the
	 * {@linkplain AvailRuntime runtime} data structures against dangerous
	 * concurrent access.
	 */
	private final ReentrantReadWriteLock runtimeLock =
		new ReentrantReadWriteLock();

	/**
	 * The {@linkplain AvailObject special objects} of the {@linkplain
	 * AvailRuntime runtime}.
	 */
	private static final AvailObject[] specialObjects =
		new AvailObject[160];

	/**
	 * An unmodifiable {@link List} of the {@linkplain AvailRuntime runtime}'s
	 * special objects.
	 */
	private static final List<AvailObject> specialObjectsList =
		Collections.unmodifiableList(Arrays.asList(specialObjects));

	/**
	 * Answer the {@linkplain AvailObject special objects} of the {@linkplain
	 * AvailRuntime runtime} as an {@linkplain
	 * Collections#unmodifiableList(List) immutable} {@linkplain List list}.
	 * Some elements may be {@code null}.
	 *
	 * @return The special objects.
	 */
	@ThreadSafe
	public static List<AvailObject> specialObjects ()
	{
		return specialObjectsList;
	}

	/**
	 * Answer the {@linkplain AvailObject special object} with the specified
	 * ordinal.
	 *
	 * @param ordinal The {@linkplain AvailObject special object} with the
	 *                specified ordinal.
	 * @return An {@link AvailObject}.
	 * @throws ArrayIndexOutOfBoundsException
	 *         If the ordinal is out of bounds.
	 */
	@ThreadSafe
	public static AvailObject specialObject (final int ordinal)
		throws ArrayIndexOutOfBoundsException
	{
		return specialObjects[ordinal];
	}

	/**
	 * The {@linkplain AtomDescriptor special atoms} known to the {@linkplain
	 * AvailRuntime runtime}.  Populated by the anonymous static section below.
	 */
	private static final List<A_Atom> specialAtomsList = new ArrayList<>();

	/**
	 * Answer the {@linkplain AtomDescriptor special atoms} known to the
	 * {@linkplain AvailRuntime runtime} as an {@linkplain
	 * Collections#unmodifiableList(List) immutable} {@linkplain List list}.
	 *
	 * @return The special atoms list.
	 */
	@ThreadSafe
	public static List<A_Atom> specialAtoms()
	{
		return specialAtomsList;
	}

	static
	{
		// Set up the special objects.
		final A_BasicObject[] specials = specialObjects;
		specials[1] = ANY.o();
		specials[2] = EnumerationTypeDescriptor.booleanObject();
		specials[3] = CHARACTER.o();
		specials[4] = FunctionTypeDescriptor.mostGeneralType();
		specials[5] = FunctionTypeDescriptor.meta();
		specials[6] = CompiledCodeTypeDescriptor.mostGeneralType();
		specials[7] = VariableTypeDescriptor.mostGeneralType();
		specials[8] = VariableTypeDescriptor.meta();
		specials[9] = ContinuationTypeDescriptor.mostGeneralType();
		specials[10] = ContinuationTypeDescriptor.meta();
		specials[11] = ATOM.o();
		specials[12] = DOUBLE.o();
		specials[13] = IntegerRangeTypeDescriptor.extendedIntegers();
		specials[14] = InstanceMetaDescriptor.on(
			TupleTypeDescriptor.zeroOrMoreOf(InstanceMetaDescriptor.anyMeta()));
		specials[15] = FLOAT.o();
		specials[16] = NUMBER.o();
		specials[17] = IntegerRangeTypeDescriptor.integers();
		specials[18] = IntegerRangeTypeDescriptor.meta();
		specials[19] = MapTypeDescriptor.meta();
		specials[20] = MODULE.o();
		specials[21] = TupleDescriptor.fromIntegerList(
			AvailErrorCode.allNumericCodes());
		specials[22] = ObjectTypeDescriptor.mostGeneralType();
		specials[23] = ObjectTypeDescriptor.meta();
		specials[24] = ObjectTypeDescriptor.exceptionType();
		specials[25] = FiberTypeDescriptor.mostGeneralType();
		specials[26] = SetTypeDescriptor.mostGeneralType();
		specials[27] = SetTypeDescriptor.meta();
		specials[28] = TupleTypeDescriptor.stringType();
		specials[29] = BottomTypeDescriptor.bottom();
		specials[30] = InstanceMetaDescriptor.on(BottomTypeDescriptor.bottom());
		specials[31] = NONTYPE.o();
		specials[32] = TupleTypeDescriptor.mostGeneralType();
		specials[33] = TupleTypeDescriptor.meta();
		specials[34] = InstanceMetaDescriptor.topMeta();
		specials[35] = TOP.o();
		specials[36] = IntegerRangeTypeDescriptor.wholeNumbers();
		specials[37] = IntegerRangeTypeDescriptor.naturalNumbers();
		specials[38] = IntegerRangeTypeDescriptor.characterCodePoints();
		specials[39] = MapTypeDescriptor.mostGeneralType();
		specials[40] = MESSAGE_BUNDLE.o();
		specials[41] = MESSAGE_BUNDLE_TREE.o();
		specials[42] = METHOD.o();
		specials[43] = DEFINITION.o();
		specials[44] = ABSTRACT_DEFINITION.o();
		specials[45] = FORWARD_DEFINITION.o();
		specials[46] = METHOD_DEFINITION.o();
		specials[47] = MACRO_DEFINITION.o();
		specials[48] = TupleTypeDescriptor.zeroOrMoreOf(
			FunctionTypeDescriptor.mostGeneralType());
		specials[50] = PARSE_NODE.mostGeneralType();
		specials[51] = SEQUENCE_NODE.mostGeneralType();
		specials[52] = EXPRESSION_NODE.mostGeneralType();
		specials[53] = ASSIGNMENT_NODE.mostGeneralType();
		specials[54] = BLOCK_NODE.mostGeneralType();
		specials[55] = LITERAL_NODE.mostGeneralType();
		specials[56] = REFERENCE_NODE.mostGeneralType();
		specials[57] = SEND_NODE.mostGeneralType();
		specials[58] = InstanceMetaDescriptor.on(
			LiteralTokenTypeDescriptor.mostGeneralType());
		specials[59] = LIST_NODE.mostGeneralType();
		specials[60] = VARIABLE_USE_NODE.mostGeneralType();
		specials[61] = DECLARATION_NODE.mostGeneralType();
		specials[62] = ARGUMENT_NODE.mostGeneralType();
		specials[63] = LABEL_NODE.mostGeneralType();
		specials[64] = LOCAL_VARIABLE_NODE.mostGeneralType();
		specials[65] = LOCAL_CONSTANT_NODE.mostGeneralType();
		specials[66] = MODULE_VARIABLE_NODE.mostGeneralType();
		specials[67] = MODULE_CONSTANT_NODE.mostGeneralType();
		specials[68] = PRIMITIVE_FAILURE_REASON_NODE.mostGeneralType();
		specials[69] = InstanceMetaDescriptor.anyMeta();
		specials[70] = AtomDescriptor.trueObject();
		specials[71] = AtomDescriptor.falseObject();
		specials[72] =
			TupleTypeDescriptor.zeroOrMoreOf(
				TupleTypeDescriptor.stringType());
		specials[73] =
			TupleTypeDescriptor.zeroOrMoreOf(
				InstanceMetaDescriptor.topMeta());
		specials[74] =
			TupleTypeDescriptor.zeroOrMoreOf(
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleTypeDescriptor.stringType()));
		specials[75] =
			SetTypeDescriptor.setTypeForSizesContentType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleTypeDescriptor.stringType());
		specials[76] =
			FunctionTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.naturalNumbers()),
				BottomTypeDescriptor.bottom());
		specials[77] = SetDescriptor.empty();
		specials[78] = InfinityDescriptor.negativeInfinity();
		specials[79] = InfinityDescriptor.positiveInfinity();
		specials[80] = PojoTypeDescriptor.mostGeneralType();
		specials[81] = BottomPojoTypeDescriptor.pojoBottom();
		specials[82] = PojoDescriptor.nullObject();
		specials[83] = PojoTypeDescriptor.selfType();
		specials[84] = InstanceMetaDescriptor.on(
			PojoTypeDescriptor.mostGeneralType());
		specials[85] = InstanceMetaDescriptor.on(
			PojoTypeDescriptor.mostGeneralArrayType());
		specials[86] = FunctionTypeDescriptor.forReturnType(
			PojoTypeDescriptor.mostGeneralType());
		specials[87] = PojoTypeDescriptor.mostGeneralArrayType();
		specials[88] = PojoTypeDescriptor.selfAtom();
		specials[89] = PojoTypeDescriptor.forClass(Throwable.class);
		specials[90] = FunctionTypeDescriptor.create(
			TupleDescriptor.empty(),
			TOP.o());
		specials[91] = FunctionTypeDescriptor.create(
			TupleDescriptor.empty(),
			EnumerationTypeDescriptor.booleanObject());
		specials[92] = VariableTypeDescriptor.wrapInnerType(
			ContinuationTypeDescriptor.mostGeneralType());
		specials[93] = MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			IntegerRangeTypeDescriptor.wholeNumbers(),
			ATOM.o(),
			ANY.o());
		specials[94] = MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			IntegerRangeTypeDescriptor.wholeNumbers(),
			ATOM.o(),
			InstanceMetaDescriptor.anyMeta());
		specials[95] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.singleInt(2),
					TupleDescriptor.empty(),
					ANY.o()));
		specials[96] = MapDescriptor.empty();
		specials[97] = MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			IntegerRangeTypeDescriptor.naturalNumbers(),
			ANY.o(),
			ANY.o());
		specials[98] = InstanceMetaDescriptor.on(
			IntegerRangeTypeDescriptor.wholeNumbers());
		specials[99] = SetTypeDescriptor.setTypeForSizesContentType(
			IntegerRangeTypeDescriptor.naturalNumbers(),
			ANY.o());
		specials[100] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				TupleTypeDescriptor.mostGeneralType());
		specials[101] = IntegerRangeTypeDescriptor.nybbles();
		specials[102] =
			TupleTypeDescriptor.zeroOrMoreOf(
				IntegerRangeTypeDescriptor.nybbles());
		specials[103] = IntegerRangeTypeDescriptor.unsignedShorts();
		specials[104] = TupleDescriptor.empty();
		specials[105] = FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				BottomTypeDescriptor.bottom()),
			TOP.o());
		specials[106] = InstanceTypeDescriptor.on(IntegerDescriptor.zero());
		specials[107] = FunctionTypeDescriptor.forReturnType(
			InstanceMetaDescriptor.topMeta());
		specials[108] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				FunctionTypeDescriptor.forReturnType(
					InstanceMetaDescriptor.topMeta()));
		specials[109] = FunctionTypeDescriptor.forReturnType(
			PARSE_NODE.mostGeneralType());
		specials[110] = InstanceTypeDescriptor.on(IntegerDescriptor.two());
		specials[111] = DoubleDescriptor.fromDouble(Math.E);
		specials[112] = InstanceTypeDescriptor.on(
			DoubleDescriptor.fromDouble(Math.E));
		specials[113] = InstanceMetaDescriptor.on(PARSE_NODE.mostGeneralType());
		specials[114] = SetTypeDescriptor.setTypeForSizesContentType(
			IntegerRangeTypeDescriptor.wholeNumbers(),
			ATOM.o());
		specials[115] = TOKEN.o();
		specials[116] = LiteralTokenTypeDescriptor.mostGeneralType();
		specials[117] =
			TupleTypeDescriptor.zeroOrMoreOf(InstanceMetaDescriptor.anyMeta());
		specials[118] =
			IntegerRangeTypeDescriptor.inclusive(
				IntegerDescriptor.zero(),
				InfinityDescriptor.positiveInfinity());
		specials[119] =
			TupleTypeDescriptor.zeroOrMoreOf(
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.singleInt(2),
					TupleDescriptor.from(ATOM.o()),
					InstanceMetaDescriptor.anyMeta()));
		specials[120] =
			TupleTypeDescriptor.zeroOrMoreOf(
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.singleInt(2),
					TupleDescriptor.from(ATOM.o()),
					ANY.o()));
		specials[121] =
			TupleTypeDescriptor.zeroOrMoreOf(PARSE_NODE.mostGeneralType());
		specials[122] =
			TupleTypeDescriptor.zeroOrMoreOf(ARGUMENT_NODE.mostGeneralType());
		specials[123] =
			TupleTypeDescriptor.zeroOrMoreOf(
				DECLARATION_NODE.mostGeneralType());
		specials[124] =
			VariableTypeDescriptor.fromReadAndWriteTypes(
				TOP.o(),
				EXPRESSION_NODE.create(BottomTypeDescriptor.bottom()));
		specials[125] =
			TupleTypeDescriptor.zeroOrMoreOf(EXPRESSION_NODE.create(ANY.o()));
		specials[126] = EXPRESSION_NODE.create(ANY.o());
		specials[127] =
			FunctionTypeDescriptor.create(
				TupleDescriptor.from(
					PojoTypeDescriptor.forClass(Throwable.class)),
				BottomTypeDescriptor.bottom());
		specials[128] =
			TupleTypeDescriptor.zeroOrMoreOf(
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ATOM.o()));
		specials[129] = IntegerRangeTypeDescriptor.bytes();
		specials[130] = TupleTypeDescriptor.zeroOrMoreOf(
			TupleTypeDescriptor.zeroOrMoreOf(
				InstanceMetaDescriptor.anyMeta()));
		specials[131] = VariableTypeDescriptor.fromReadAndWriteTypes(
			IntegerRangeTypeDescriptor.extendedIntegers(),
			BottomTypeDescriptor.bottom());
		specials[132] = FiberTypeDescriptor.meta();
		specials[133] = TupleTypeDescriptor.oneOrMoreOf(CHARACTER.o());
		specials[134] = SetTypeDescriptor.setTypeForSizesContentType(
			IntegerRangeTypeDescriptor.wholeNumbers(),
			ObjectTypeDescriptor.exceptionType());
		specials[135] = SetTypeDescriptor.setTypeForSizesContentType(
			IntegerRangeTypeDescriptor.naturalNumbers(),
			TupleTypeDescriptor.stringType());
		specials[136] = SetTypeDescriptor.setTypeForSizesContentType(
			IntegerRangeTypeDescriptor.naturalNumbers(),
			ATOM.o());
		specials[137] = TupleTypeDescriptor.oneOrMoreOf(ANY.o());
		specials[138] = TupleTypeDescriptor.zeroOrMoreOf(
			IntegerRangeTypeDescriptor.integers());
		specials[139] = TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			IntegerRangeTypeDescriptor.create(
				IntegerDescriptor.fromInt(2), true,
				InfinityDescriptor.positiveInfinity(),false),
			TupleDescriptor.empty(),
			ANY.o());
		// Some of these entries may need to be shuffled into earlier slots to
		// maintain reasonable topical consistency.
		specials[140] = FIRST_OF_SEQUENCE_NODE.mostGeneralType();
		specials[141] = PERMUTED_LIST_NODE.mostGeneralType();
		specials[142] = SUPER_CAST_NODE.mostGeneralType();
		specials[143] = AtomDescriptor.clientDataGlobalKey();
		specials[144] = AtomDescriptor.compilerScopeMapKey();
		specials[145] = AtomDescriptor.allTokensKey();
		specials[146] = IntegerRangeTypeDescriptor.int32();
		specials[147] = IntegerRangeTypeDescriptor.int64();
		specials[148] = STATEMENT_NODE.mostGeneralType();

		// DO NOT CHANGE THE ORDER OF THESE ENTRIES!  Serializer compatibility
		// depends on the order of this list.
		assert specialAtomsList.isEmpty();
		specialAtomsList.addAll(Arrays.asList(
			AtomDescriptor.trueObject(),
			AtomDescriptor.falseObject(),
			PojoTypeDescriptor.selfAtom(),
			ObjectTypeDescriptor.exceptionAtom(),
			MethodDescriptor.vmCrashAtom(),
			MethodDescriptor.vmFunctionApplyAtom(),
			MethodDescriptor.vmMethodDefinerAtom(),
			MethodDescriptor.vmMacroDefinerAtom(),
			MethodDescriptor.vmPublishAtomsAtom(),
			ObjectTypeDescriptor.stackDumpAtom(),
			AtomDescriptor.fileKey(),
			CompiledCodeDescriptor.methodNameKeyAtom(),
			CompiledCodeDescriptor.lineNumberKeyAtom(),
			AtomDescriptor.messageBundleKey(),
			MethodDescriptor.vmDeclareStringifierAtom(),
			AtomDescriptor.clientDataGlobalKey(),
			AtomDescriptor.compilerScopeMapKey(),
			AtomDescriptor.allTokensKey()));

		for (final A_Atom atom : specialAtomsList)
		{
			assert atom.isAtomSpecial();
		}

		// Make sure all special objects are shared, and also make sure all
		// special objects that are atoms are also special.
		for (int i = 0; i < specialObjects.length; i++)
		{
			final AvailObject object = specialObjects[i];
			if (object != null)
			{
				specialObjects[i] = object.makeShared();
				if (object.isAtom())
				{
					assert object.isAtomSpecial();
				}
			}
		}
	}

	/**
	 * The loaded Avail {@linkplain ModuleDescriptor modules}: a
	 * {@linkplain MapDescriptor map} from {@linkplain TupleDescriptor module
	 * names} to {@linkplain ModuleDescriptor modules}.
	 */
	private A_Map modules = MapDescriptor.empty();

	/**
	 * Add the specified {@linkplain ModuleDescriptor module} to the
	 * {@linkplain AvailRuntime runtime}.
	 *
	 * @param module A {@linkplain ModuleDescriptor module}.
	 */
	@ThreadSafe
	public void addModule (final A_Module module)
	{
		runtimeLock.writeLock().lock();
		try
		{
			assert !includesModuleNamed(module.moduleName());
			modules = modules.mapAtPuttingCanDestroy(
				module.moduleName(), module, true);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Remove the specified {@linkplain ModuleDescriptor module} from this
	 * runtime.  The module's code should already have been removed via {@link
	 * A_Module#removeFrom(AvailLoader, Continuation0)}.
	 *
	 * @param module The module to remove.
	 */
	public void unlinkModule (final A_Module module)
	{
		runtimeLock.writeLock().lock();
		try
		{
			assert includesModuleNamed(module.moduleName());
			modules = modules.mapWithoutKeyCanDestroy(
				module.moduleName(), true);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Does the {@linkplain AvailRuntime runtime} define a {@linkplain
	 * ModuleDescriptor module} with the specified {@linkplain
	 * TupleDescriptor name}?
	 *
	 * @param moduleName A {@linkplain TupleDescriptor name}.
	 * @return {@code true} if the {@linkplain AvailRuntime runtime} defines a
	 *          {@linkplain ModuleDescriptor module} with the specified
	 *          {@linkplain TupleDescriptor name}, {@code false} otherwise.
	 */
	@ThreadSafe
	public boolean includesModuleNamed (final A_String moduleName)
	{
		assert moduleName.isString();

		runtimeLock.readLock().lock();
		try
		{
			return modules.hasKey(moduleName);
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Answer my current map of modules.  Mark it {@linkplain
	 * A_BasicObject#makeShared() shared} first for safety.
	 *
	 * @return A {@link MapDescriptor map} from resolved module {@linkplain
	 *         ResolvedModuleName names} ({@linkplain StringDescriptor strings})
	 *         to {@link ModuleDescriptor modules}.
	 */
	public A_Map loadedModules ()
	{
		runtimeLock.readLock().lock();
		try
		{
			return modules.makeShared();
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Answer the {@linkplain ModuleDescriptor module} with the specified
	 * {@linkplain TupleDescriptor name}.
	 *
	 * @param moduleName A {@linkplain TupleDescriptor name}.
	 * @return A {@linkplain ModuleDescriptor module}.
	 */
	@ThreadSafe
	public AvailObject moduleAt (final A_String moduleName)
	{
		assert moduleName.isString();

		runtimeLock.readLock().lock();
		try
		{
			assert modules.hasKey(moduleName);
			return modules.mapAt(moduleName);
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Unbind the specified {@linkplain DefinitionDescriptor definition} from
	 * the runtime system.  If no definitions or grammatical restrictions remain
	 * in its {@linkplain MethodDescriptor method}, then remove all of its
	 * bundles.
	 *
	 * @param definition A definition.
	 */
	@ThreadSafe
	public void removeDefinition (
		final A_Definition definition)
	{
		runtimeLock.writeLock().lock();
		try
		{
			final A_Method method = definition.definitionMethod();
			method.removeDefinition(definition);
			if (method.isMethodEmpty())
			{
				for (final A_Bundle bundle : method.bundles())
				{
					// Remove the desiccated message bundle from its atom.
					final A_Atom atom = bundle.message();
					atom.setAtomProperty(
						AtomDescriptor.messageBundleKey(),
						NilDescriptor.nil());
				}
			}
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Add a semantic restriction to the method associated with the
	 * given method name.
	 *
	 * @param restriction
	 *            A {@linkplain SemanticRestrictionDescriptor semantic
	 *            restriction} that validates the static types of arguments at
	 *            call sites.
	 */
	public void addSemanticRestriction (
		final A_SemanticRestriction restriction)
	{
		runtimeLock.writeLock().lock();
		try
		{
			final A_Method method = restriction.definitionMethod();
			method.addSemanticRestriction(restriction);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Remove a semantic restriction from the method associated with the
	 * given method name.
	 *
	 * @param restriction
	 *            A {@linkplain SemanticRestrictionDescriptor semantic
	 *            restriction} that validates the static types of arguments at
	 *            call sites.
	 */
	public void removeTypeRestriction (
		final A_SemanticRestriction restriction)
	{
		runtimeLock.writeLock().lock();
		try
		{
			final A_Method method = restriction.definitionMethod();
			method.removeSemanticRestriction(restriction);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Add a seal to the method associated with the given method name.
	 *
	 * @param methodName
	 *        The method name, an {@linkplain AtomDescriptor atom}.
	 * @param sealSignature
	 *        The tuple of types at which to seal the method.
	 * @throws MalformedMessageException
	 *         If anything is wrong with the method name.
	 */
	public void addSeal (
			final A_Atom methodName,
			final A_Tuple sealSignature)
		throws MalformedMessageException
	{
		assert methodName.isAtom();
		assert sealSignature.isTuple();
		runtimeLock.writeLock().lock();
		try
		{
			final A_Bundle bundle = methodName.bundleOrCreate();
			final A_Method method = bundle.bundleMethod();
			assert method.numArgs() == sealSignature.tupleSize();
			method.addSealedArgumentsType(sealSignature);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Remove a seal from the method associated with the given method name.
	 *
	 * @param methodName
	 *        The method name, an {@linkplain AtomDescriptor atom}.
	 * @param sealSignature
	 *        The signature at which to unseal the method. There may be other
	 *        seals remaining, even at this very signature.
	 * @throws MalformedMessageException
	 *         If anything is wrong with the method name.
	 */
	public void removeSeal (
			final A_Atom methodName,
			final A_Tuple sealSignature)
		throws MalformedMessageException
	{
		runtimeLock.writeLock().lock();
		try
		{
			final A_Bundle bundle = methodName.bundleOrCreate();
			final A_Method method = bundle.bundleMethod();
			method.removeSealedArgumentsType(sealSignature);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Add a macro prefix function at the given prefix function index (also
	 * called the section checkpoint index) in the given method.
	 *
	 * @param method The method to which to add the prefix function.
	 * @param index The index for which to add a prefix function.
	 * @param prefixFunction The function to add.
	 */
	public void addPrefixFunction (
		final A_Method method,
		final int index,
		final A_Function prefixFunction)
	{
		runtimeLock.writeLock().lock();
		try
		{
			A_Tuple functionTuples = method.prefixFunctions();
			assert 1 <= index && index <= functionTuples.tupleSize();
			A_Tuple functionTuple = functionTuples.tupleAt(index);
			functionTuple = functionTuple.appendCanDestroy(
				prefixFunction, true);
			functionTuples = functionTuples.tupleAtPuttingCanDestroy(
				index, functionTuple, true);
			method.prefixFunctions(functionTuples.makeShared());
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Remove a macro prefix function at the given prefix function index (also
	 * called the section checkpoint index) in the given method.
	 *
	 * @param method The method from which to remove the prefix function.
	 * @param index The index in which the prefix function should be found.
	 * @param prefixFunction The function to remove (one occurrence of).
	 */
	public void removePrefixFunction (
		final A_Method method,
		final int index,
		final A_Function prefixFunction)
	{
		runtimeLock.writeLock().lock();
		try
		{
			A_Tuple functionTuples = method.prefixFunctions();
			assert 1 <= index && index <= functionTuples.tupleSize();
			A_Tuple functionTuple = functionTuples.tupleAt(index);
			final int functionTupleSize = functionTuple.tupleSize();
			int indexToRemove = Integer.MIN_VALUE;
			for (int i = 1; i <= functionTupleSize; i++)
			{
				if (functionTuple.tupleAt(i).equals(prefixFunction))
				{
					indexToRemove = i;
					break;
				}
			}
			assert indexToRemove > 0;
			final A_Tuple part1 = functionTuple.copyTupleFromToCanDestroy(
				1, indexToRemove - 1, false);
			final A_Tuple part2 = functionTuple.copyTupleFromToCanDestroy(
				indexToRemove + 1, functionTupleSize, false);
			functionTuple = part1.concatenateWith(part2, true);
			functionTuples = functionTuples.tupleAtPuttingCanDestroy(
				index, functionTuple, true);
			method.prefixFunctions(functionTuples.makeShared());
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * The {@linkplain ReentrantLock lock} that guards access to the Level One
	 * {@linkplain #levelOneSafeTasks -safe} and {@linkplain
	 * #levelOneUnsafeTasks -unsafe} queues and counters.
	 *
	 * <p>For example, a {@link L2Chunk} may not be {@linkplain
	 * L2Chunk#invalidate() invalidated} while any {@linkplain FiberDescriptor
	 * fiber} is {@linkplain ExecutionState#RUNNING running} a Level Two chunk.
	 * These two activities are mutually exclusive.</p>
	 */
	@InnerAccess final ReentrantLock levelOneSafeLock = new ReentrantLock();

	/**
	 * The {@linkplain Queue queue} of Level One-safe {@linkplain Runnable
	 * tasks}. A Level One-safe task requires that no {@linkplain
	 * #levelOneUnsafeTasks Level One-unsafe tasks} are running.
	 *
	 * <p>For example, a {@linkplain L2Chunk Level Two chunk} may not be
	 * {@linkplain L2Chunk#invalidate() invalidated} while any {@linkplain
	 * FiberDescriptor fiber} is {@linkplain ExecutionState#RUNNING running} a
	 * Level Two chunk. These two activities are mutually exclusive.</p>
	 */
	@InnerAccess Queue<AvailTask> levelOneSafeTasks =
		new ArrayDeque<AvailTask>();

	/**
	 * The {@linkplain Queue queue} of Level One-unsafe {@linkplain
	 * Runnable tasks}. A Level One-unsafe task requires that no
	 * {@linkplain #levelOneSafeTasks Level One-safe tasks} are running.
	 */
	@InnerAccess Queue<AvailTask> levelOneUnsafeTasks =
		new ArrayDeque<AvailTask>();

	/**
	 * The number of {@linkplain #levelOneSafeTasks Level One-safe tasks} that
	 * have been {@linkplain #executor scheduled for execution} but have not
	 * yet reached completion.
	 */
	@InnerAccess int incompleteLevelOneSafeTasks = 0;

	/**
	 * The number of {@linkplain #levelOneUnsafeTasks Level One-unsafe tasks}
	 * that have been {@linkplain #executor scheduled for execution} but have
	 * not yet reached completion.
	 */
	@InnerAccess int incompleteLevelOneUnsafeTasks = 0;

	/**
	 * Has {@linkplain #whenLevelOneUnsafeDo(AvailTask) Level One safety}
	 * been requested?
	 */
	@InnerAccess volatile boolean levelOneSafetyRequested = false;

	/**
	 * Has {@linkplain #whenLevelOneUnsafeDo(AvailTask) Level One safety}
	 * been requested?
	 *
	 * @return {@code true} if Level One safety has been requested, {@code
	 *         false} otherwise.
	 */
	public boolean levelOneSafetyRequested ()
	{
		return levelOneSafetyRequested;
	}

	/**
	 * Request that the specified {@linkplain Continuation0 continuation} be
	 * executed as a Level One-unsafe task at such a time as there are no Level
	 * One-safe tasks running.
	 *
	 * @param unsafeTask
	 *        What to do when Level One safety is not required.
	 */
	public void whenLevelOneUnsafeDo (final AvailTask unsafeTask)
	{
		final AvailTask wrapped = new AvailTask(unsafeTask.priority)
		{
			@Override
			public void value ()
			{
				try
				{
					unsafeTask.run();
				}
				finally
				{
					levelOneSafeLock.lock();
					try
					{
						incompleteLevelOneUnsafeTasks--;
						if (incompleteLevelOneUnsafeTasks == 0)
						{
							assert incompleteLevelOneSafeTasks == 0;
							incompleteLevelOneSafeTasks =
								levelOneSafeTasks.size();
							for (final AvailTask task : levelOneSafeTasks)
							{
								execute(task);
							}
							levelOneSafeTasks.clear();
						}
					}
					finally
					{
						levelOneSafeLock.unlock();
					}
				}
			}
		};
		levelOneSafeLock.lock();
		try
		{
			// Hasten the execution of pending Level One-safe tasks by
			// postponing this task if there are any Level One-safe tasks
			// waiting to run.
			if (incompleteLevelOneSafeTasks == 0
				&& levelOneSafeTasks.isEmpty())
			{
				assert !levelOneSafetyRequested;
				incompleteLevelOneUnsafeTasks++;
				executor.execute(wrapped);
			}
			else
			{
				levelOneUnsafeTasks.add(wrapped);
			}
		}
		finally
		{
			levelOneSafeLock.unlock();
		}
	}

	/**
	 * Request that the specified {@linkplain Continuation0 continuation} be
	 * executed as a Level One-safe task at such a time as there are no Level
	 * One-unsafe tasks running.
	 *
	 * @param safeTask
	 *        What to do when Level One safety is ensured.
	 */
	public void whenLevelOneSafeDo (final AvailTask safeTask)
	{
		final AvailTask wrapped = new AvailTask(safeTask.priority)
		{
			@Override
			public void value ()
			{
				try
				{
					safeTask.run();
				}
				finally
				{
					levelOneSafeLock.lock();
					try
					{
						incompleteLevelOneSafeTasks--;
						if (incompleteLevelOneSafeTasks == 0)
						{
							assert incompleteLevelOneUnsafeTasks == 0;
							levelOneSafetyRequested = false;
							incompleteLevelOneUnsafeTasks =
								levelOneUnsafeTasks.size();
							for (final AvailTask task : levelOneUnsafeTasks)
							{
								execute(task);
							}
							levelOneUnsafeTasks.clear();
						}
					}
					finally
					{
						levelOneSafeLock.unlock();
					}
				}
			}
		};
		levelOneSafeLock.lock();
		try
		{
			levelOneSafetyRequested = true;
			if (incompleteLevelOneUnsafeTasks == 0)
			{
				incompleteLevelOneSafeTasks++;
				executor.execute(wrapped);
			}
			else
			{
				levelOneSafeTasks.add(wrapped);
			}
		}
		finally
		{
			levelOneSafeLock.unlock();
		}
	}

	/**
	 * Destroy all data structures used by this {@code AvailRuntime}.  Also
	 * disassociate it from the current {@link Thread}'s local storage.
	 */
	public void destroy ()
	{
		timer.cancel();
		executor.shutdownNow();
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
			executor.awaitTermination(10, TimeUnit.SECONDS);
		}
		catch (final InterruptedException e)
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
		modules = NilDescriptor.nil();
	}
}
