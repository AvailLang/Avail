/*
 * AvailRuntime.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.annotations.ThreadSafe;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.ModuleRoots;
import com.avail.builder.ResolvedModuleName;
import com.avail.descriptor.*;
import com.avail.descriptor.AtomDescriptor.SpecialAtom;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.FiberDescriptor.TraceFlag;
import com.avail.descriptor.MapDescriptor.Entry;
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.primitive.phrases.P_CreateToken;
import com.avail.io.TextInterface;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.performance.Statistic;
import com.avail.utility.LRUCache;
import com.avail.utility.MutableOrNull;
import com.avail.utility.evaluation.Continuation0;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.avail.descriptor.AtomDescriptor.falseObject;
import static com.avail.descriptor.AtomDescriptor.trueObject;
import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.BottomPojoTypeDescriptor.pojoBottom;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.CompiledCodeTypeDescriptor.mostGeneralCompiledCodeType;
import static com.avail.descriptor.ContinuationTypeDescriptor.continuationMeta;
import static com.avail.descriptor.ContinuationTypeDescriptor.mostGeneralContinuationType;
import static com.avail.descriptor.DoubleDescriptor.fromDouble;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FiberTypeDescriptor.fiberMeta;
import static com.avail.descriptor.FiberTypeDescriptor.mostGeneralFiberType;
import static com.avail.descriptor.FunctionDescriptor.newCrashFunction;
import static com.avail.descriptor.FunctionTypeDescriptor.*;
import static com.avail.descriptor.InfinityDescriptor.negativeInfinity;
import static com.avail.descriptor.InfinityDescriptor.positiveInfinity;
import static com.avail.descriptor.InstanceMetaDescriptor.*;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerDescriptor.*;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.*;
import static com.avail.descriptor.LexerDescriptor.lexerBodyFunctionType;
import static com.avail.descriptor.LexerDescriptor.lexerFilterFunctionType;
import static com.avail.descriptor.LiteralTokenTypeDescriptor.mostGeneralLiteralTokenType;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.MapTypeDescriptor.*;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTypeDescriptor.*;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.*;
import static com.avail.descriptor.PojoDescriptor.nullPojo;
import static com.avail.descriptor.PojoTypeDescriptor.*;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.SetTypeDescriptor.*;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.*;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.VariableTypeDescriptor.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.StackPrinter.trace;
import static java.lang.Math.min;
import static java.nio.file.attribute.PosixFilePermission.*;
import static java.util.Arrays.asList;

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
				try (final Scanner scanner = new Scanner(resourceStream))
				{
					version = scanner.nextLine();
					scanner.close();
				}
			}
		}
		catch (final IOException e)
		{
			version = "UNKNOWN";
		}
		buildVersion = version;
	}

	/**
	 * Answer the build version, as set by the build process.
	 *
	 * @return The build version, or {@code "dev"} if Avail is not running from
	 *         a distribution JAR.
	 */
	@SuppressWarnings("unused")
	public static String buildVersion ()
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
	public static A_Set activeVersions ()
	{
		A_Set versions = emptySet();
		for (final String version : activeVersions)
		{
			versions = versions.setWithElementCanDestroy(
				stringFrom(version), true);
		}
		return versions;
	}

	/**
	 * Answer the Avail runtime associated with the current {@linkplain Thread
	 * thread}.
	 *
	 * @return The Avail runtime of the current thread.
	 */
	public static AvailRuntime currentRuntime ()
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
	@SuppressWarnings("ThisEscapedInObjectConstruction")
	private final ThreadFactory executorThreadFactory =
		runnable -> new AvailThread(
			runnable,
			new Interpreter(AvailRuntime.this));

	/**
	 * The {@linkplain ThreadFactory thread factory} for creating {@link
	 * Thread}s for processing file I/O on behalf of this {@linkplain
	 * AvailRuntime Avail runtime}.
	 */
	private final ThreadFactory fileThreadFactory = new ThreadFactory()
	{
		final AtomicInteger counter = new AtomicInteger();

		@Override
		public Thread newThread (final Runnable runnable)
		{
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
		final AtomicInteger counter = new AtomicInteger();

		@Override
		public Thread newThread (final Runnable runnable)
		{
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
	public static final int maxInterpreters = availableProcessors;

	/**
	 * A counter from which unique interpreter indices in [0..maxInterpreters)
	 * are allotted.
	 */
	private final AtomicInteger nextInterpreterIndex = new AtomicInteger(0);

	/**
	 * Allocate the next interpreter index in [0..maxInterpreters)
	 * thread-safely.
	 *
	 * @return A new unique interpreter index.
	 */
	public synchronized int allocateInterpreterIndex ()
	{
		final int index = nextInterpreterIndex.getAndIncrement();
		assert index < maxInterpreters;
		return index;
	}

	/**
	 * The {@linkplain ThreadPoolExecutor thread pool executor} for
	 * this {@linkplain AvailRuntime Avail runtime}.
	 */
	private final ThreadPoolExecutor executor =
		new ThreadPoolExecutor(
			min(availableProcessors, maxInterpreters),
			maxInterpreters,
			10L,
			TimeUnit.SECONDS,
			new PriorityBlockingQueue<>(100),
			executorThreadFactory,
			new AbortPolicy());

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
	 * Schedule the specified {@linkplain AvailTask task} for eventual
	 * execution. The implementation is free to run the task immediately or
	 * delay its execution arbitrarily. The task is guaranteed to execute on an
	 * {@linkplain AvailThread Avail thread}.
	 *
	 * @param priority
	 *        The desired priority, a long tied to milliseconds since the
	 *        current epoch.
	 * @param body
	 *        The {@link Continuation0} to execute for this task.
	 */
	public void execute (
		final int priority,
		final Continuation0 body)
	{
		executor.execute(new AvailTask(priority, body));
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
			new LinkedBlockingQueue<>(10),
			fileThreadFactory,
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
			socketThreadFactory,
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
	 * data, which is especially powerful since the buffers are {@linkplain
	 * Mutability#SHARED shared} (immutable and thread-safe).
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
		key ->
		{
			assert key != null;
			return new MutableOrNull<>();
		},
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
	 * The {@linkplain Timer timer} that managed scheduled {@linkplain
	 * TimerTask tasks} for this {@linkplain AvailRuntime runtime}. The timer
	 * thread is not an {@linkplain AvailThread Avail thread}, and therefore
	 * cannot directly execute {@linkplain FiberDescriptor fibers}. It may,
	 * however, schedule fiber-related tasks.
	 */
	public final Timer timer = new Timer(
		"timer for Avail runtime",
		true);

	/**
	 * Utility class for wrapping a volatile counter that can be polled.
	 */
	public class Clock
	{
		/**
		 * The current value of the monotonic counter.
		 */
		private final AtomicLong counter = new AtomicLong(0);

		/**
		 * Advance the clock.
		 */
		public void increment ()
		{
			counter.incrementAndGet();
		}

		/**
		 * Poll the monotonic counter of the clock.
		 *
		 * @return The current clock value.
		 */
		public long get ()
		{
			return counter.get();
		}
	}

	/**
	 * The number of clock ticks since this {@linkplain AvailRuntime runtime}
	 * was created.
	 */
	public final Clock clock = new Clock();

	{
		// Schedule a fixed-rate timer task to increment the runtime clock.
		timer.schedule(
			new TimerTask()
			{
				@Override
				public void run ()
				{
					clock.increment();
				}
			},
			10,
			10);
	}

	/**
	 * Whether to show all {@link MacroDefinitionDescriptor macro} expansions as
	 * they happen.
	 */
	public static boolean debugMacroExpansions = false;

	/**
	 * Whether to show detailed compiler trace information.
	 */
	public static boolean debugCompilerSteps = false;

	/**
	 * Perform an integrity check on the parser data structures.  Report the
	 * findings to System.out.
	 */
	public void integrityCheck ()
	{
		System.out.println("Integrity check:");
		runtimeLock.writeLock().lock();
		try
		{
			final Set<A_Atom> atoms = new HashSet<>();
			final Set<A_Definition> definitions = new HashSet<>();
			for (final Entry moduleEntry : modules.mapIterable())
			{
				final A_Module module = moduleEntry.value();
				atoms.addAll(
					toList(
						module.newNames().valuesAsTuple()));
				atoms.addAll(
					toList(
						module.visibleNames().asTuple()));
				A_Tuple atomSets = module.importedNames().valuesAsTuple();
				atomSets = atomSets.concatenateWith(
					module.privateNames().valuesAsTuple(), true);
				for (final A_Set atomSet : atomSets)
				{
					atoms.addAll(
						toList(atomSet.asTuple()));
				}
				for (final A_Definition definition : module.methodDefinitions())
				{
					if (definitions.contains(definition))
					{
						System.out.println(
							"Duplicate definition: " + definition);
					}
					definitions.add(definition);
				}
			}
			final Set<A_Method> methods = new HashSet<>();
			for (final A_Atom atom : atoms)
			{
				final A_Bundle bundle = atom.bundleOrNil();
				if (!bundle.equalsNil())
				{
					methods.add(bundle.bundleMethod());
				}
			}
			for (final A_Method method : methods)
			{
				final Set<A_Definition> bundleDefinitions = new HashSet<>(
					TupleDescriptor.<A_Definition>toList(
						method.definitionsTuple()));
				bundleDefinitions.addAll(
					toList(
						method.macroDefinitionsTuple()));
				for (final A_Bundle bundle : method.bundles())
				{
					final A_Map bundlePlans = bundle.definitionParsingPlans();
					if (bundlePlans.mapSize() != bundleDefinitions.size())
					{
						System.out.println(
							"Mismatched definitions / plans:"
								+ "\n\tbundle = " + bundle
								+ "\n\tdefinitions# = "
								+ bundleDefinitions.size()
								+ "\n\tplans# = " + bundlePlans.mapSize());
					}
				}
			}
			// TODO(MvG) - Do more checks.
			System.out.println("done.");
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * The {@linkplain ModuleNameResolver module name resolver} that this
	 * {@linkplain AvailRuntime runtime} should use to resolve unqualified
	 * {@linkplain ModuleDescriptor module} names.
	 */
	private final ModuleNameResolver moduleNameResolver;

	/**
	 * Answer the {@linkplain ModuleNameResolver module name resolver} that this
	 * runtime should use to resolve unqualified {@linkplain ModuleDescriptor
	 * module} names.
	 *
	 * @return A {@linkplain ModuleNameResolver module name resolver}.
	 */
	public ModuleNameResolver moduleNameResolver ()
	{
		return stripNull(moduleNameResolver);
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
	private AvailObject textInterfacePojo = identityPojo(textInterface);

	/**
	 * Answer the runtime's default {@linkplain TextInterface text interface}.
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
	 * Set the runtime's default {@linkplain TextInterface text interface}.
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
			this.textInterfacePojo = identityPojo(textInterface);
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
	private volatile A_Function unassignedVariableReadFunction =
		newCrashFunction(
			"attempted to read from unassigned variable",
			emptyTuple());

	/**
	 * Answer the {@linkplain FunctionDescriptor function} to invoke whenever an
	 * unassigned variable is read.
	 *
	 * @return The requested function.
	 */
	@ThreadSafe
	@ReferencedInGeneratedCode
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
	 * The type for the failure handler for reading from an unassigned variable.
	 */
	public static final A_Type unassignedVariableReadFunctionType =
		functionType(emptyTuple(), bottom());

	/**
	 * The {@linkplain FunctionDescriptor function} to invoke whenever a
	 * returned value disagrees with the expected type.
	 */
	private volatile A_Function resultDisagreedWithExpectedTypeFunction =
		newCrashFunction(
			"return result disagreed with expected type",
			tuple(
				mostGeneralFunctionType(),
				topMeta(),
				variableTypeFor(ANY.o())));

	/**
	 * Answer the {@linkplain FunctionDescriptor function} to invoke whenever
	 * the value produced by a {@linkplain MethodDescriptor method} send
	 * disagrees with the {@linkplain TypeDescriptor type} expected.
	 *
	 * <p>The function takes the function that's attempting to return, the
	 * expected return type, and a new variable holding the actual result being
	 * returned (or unassigned if it was {@code nil}).</p>
	 *
	 * @return The requested function.
	 */
	@ThreadSafe
	@ReferencedInGeneratedCode
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
	private volatile A_Function implicitObserveFunction =
		newCrashFunction(
			"variable with a write reactor was written with write-tracing off",
			tuple(mostGeneralFunctionType(), mostGeneralTupleType()));

	/**
	 * Answer the {@linkplain FunctionDescriptor function} to invoke whenever
	 * a {@linkplain VariableDescriptor variable} with {@linkplain
	 * VariableAccessReactor write reactors} is written when {@linkplain
	 * TraceFlag#TRACE_VARIABLE_WRITES write tracing} is not enabled.
	 *
	 * @return The requested function.
	 */
	@ThreadSafe
	@ReferencedInGeneratedCode
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

	/** The type of the {@link #implicitObserveFunction}. */
	public static final A_Type implicitObserveFunctionType =
		functionType(
			tuple(
				mostGeneralFunctionType(),
				mostGeneralTupleType()),
			TOP.o());

	/**
	 * The required type of the invalid message send function.
	 */
	public static final A_Type invalidMessageSendFunctionType =
		functionType(
			tuple(
				enumerationWith(
					set(
						E_NO_METHOD,
						E_NO_METHOD_DEFINITION,
						E_AMBIGUOUS_METHOD_DEFINITION,
						E_FORWARD_METHOD_DEFINITION,
						E_ABSTRACT_METHOD_DEFINITION)),
				METHOD.o(),
				mostGeneralTupleType()),
			bottom());

	/**
	 * The {@link A_Function} to invoke whenever a {@linkplain A_Method} send
	 * fails for a definitional reason.
	 */
	private volatile A_Function invalidMessageSendFunction =
		newCrashFunction(
			"failed method lookup",
			invalidMessageSendFunctionType
				.argsTupleType().tupleOfTypesFromTo(1, 3));

	/**
	 * Answer the {@linkplain FunctionDescriptor function} to invoke whenever a
	 * {@linkplain MethodDescriptor method} send fails for a definitional
	 * reason.
	 *
	 * @return The function to invoke whenever a message send fails dynamically
	 *         because of an ambiguous, invalid, or incomplete lookup.
	 */
	@ThreadSafe
	@ReferencedInGeneratedCode
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

	/**
	 * All {@linkplain A_Fiber fibers} that have not yet {@link
	 * ExecutionState#RETIRED retired} <em>or</em> been reclaimed by garbage
	 * collection.
	 */
	private final Set<A_Fiber> allFibers =
		Collections.synchronizedSet(
			Collections.newSetFromMap(new WeakHashMap<>()));

	/**
	 * Add the specified {@linkplain A_Fiber fiber} to this {@linkplain
	 * AvailRuntime runtime}.
	 *
	 * @param fiber
	 *        A fiber.
	 */
	public void registerFiber (final A_Fiber fiber)
	{
		allFibers.add(fiber);
	}

	/**
	 * Remove the specified {@linkplain A_Fiber fiber} from this {@linkplain
	 * AvailRuntime runtime}.  This should be done explicitly when a fiber
	 * retires, although the fact that {@link #allFibers} wraps a {@link
	 * WeakHashMap} ensures that fibers that are no longer referenced will still
	 * be cleaned up at some point.
	 *
	 * @param fiber
	 *        A fiber to unregister.
	 */
	void unregisterFiber (final A_Fiber fiber)
	{
		allFibers.remove(fiber);
	}

	/**
	 * Answer a {@link Set} of all {@link A_Fiber}s that have not yet
	 * {@linkplain ExecutionState#RETIRED retired}.  Retired fibers will be
	 * garbage collected when there are no remaining references.
	 *
	 * <p>Note that the result is a set which strongly holds all extant fibers,
	 * so holding this set indefinitely would keep the contained fibers from
	 * being garbage collected.</p>
	 *
	 * @return All fibers belonging to this {@code AvailRuntime}.
	 */
	public Set<A_Fiber> allFibers ()
	{
		return Collections.unmodifiableSet(new HashSet<>(allFibers));
	}

	/**
	 * Construct a new {@code AvailRuntime}.
	 *
	 * @param moduleNameResolver
	 *        The {@linkplain ModuleNameResolver module name resolver} that this
	 *        {@code AvailRuntime} should use to resolve unqualified {@linkplain
	 *        ModuleDescriptor module} names.
	 */
	public AvailRuntime (final ModuleNameResolver moduleNameResolver)
	{
		this.moduleNameResolver = moduleNameResolver;
		this.classLoader = AvailRuntime.class.getClassLoader();
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
		new AvailObject[170];

	/**
	 * An unmodifiable {@link List} of the {@linkplain AvailRuntime runtime}'s
	 * special objects.
	 */
	private static final List<AvailObject> specialObjectsList =
		Collections.unmodifiableList(asList(specialObjects));

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
		//noinspection AssignmentOrReturnOfFieldWithMutableType
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
	 * runtime as an {@linkplain Collections#unmodifiableList(List) immutable}
	 * {@linkplain List list}.
	 *
	 * @return The special atoms list.
	 */
	@ThreadSafe
	public static List<A_Atom> specialAtoms()
	{
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		return specialAtomsList;
	}

	static
	{
		// Set up the special objects.
		final A_BasicObject[] specials = specialObjects;
		specials[1] = ANY.o();
		specials[2] = booleanType();
		specials[3] = CHARACTER.o();
		specials[4] = mostGeneralFunctionType();
		specials[5] = functionMeta();
		specials[6] = mostGeneralCompiledCodeType();
		specials[7] = mostGeneralVariableType();
		specials[8] = variableMeta();
		specials[9] = mostGeneralContinuationType();
		specials[10] = continuationMeta();
		specials[11] = ATOM.o();
		specials[12] = DOUBLE.o();
		specials[13] = extendedIntegers();
		specials[14] = instanceMeta(zeroOrMoreOf(anyMeta()));
		specials[15] = FLOAT.o();
		specials[16] = NUMBER.o();
		specials[17] = integers();
		specials[18] = extendedIntegersMeta();
		specials[19] = mapMeta();
		specials[20] = MODULE.o();
		specials[21] = tupleFromIntegerList(allNumericCodes());
		specials[22] = mostGeneralObjectType();
		specials[23] = mostGeneralObjectMeta();
		specials[24] = exceptionType();
		specials[25] = mostGeneralFiberType();
		specials[26] = mostGeneralSetType();
		specials[27] = setMeta();
		specials[28] = stringType();
		specials[29] = bottom();
		specials[30] = instanceMeta(bottom());
		specials[31] = NONTYPE.o();
		specials[32] = mostGeneralTupleType();
		specials[33] = tupleMeta();
		specials[34] = topMeta();
		specials[35] = TOP.o();
		specials[36] = wholeNumbers();
		specials[37] = naturalNumbers();
		specials[38] = characterCodePoints();
		specials[39] = mostGeneralMapType();
		specials[40] = MESSAGE_BUNDLE.o();
		specials[41] = MESSAGE_BUNDLE_TREE.o();
		specials[42] = METHOD.o();
		specials[43] = DEFINITION.o();
		specials[44] = ABSTRACT_DEFINITION.o();
		specials[45] = FORWARD_DEFINITION.o();
		specials[46] = METHOD_DEFINITION.o();
		specials[47] = MACRO_DEFINITION.o();
		specials[48] = zeroOrMoreOf(mostGeneralFunctionType());
		specials[50] = PARSE_PHRASE.mostGeneralType();
		specials[51] = SEQUENCE_PHRASE.mostGeneralType();
		specials[52] = EXPRESSION_PHRASE.mostGeneralType();
		specials[53] = ASSIGNMENT_PHRASE.mostGeneralType();
		specials[54] = BLOCK_PHRASE.mostGeneralType();
		specials[55] = LITERAL_PHRASE.mostGeneralType();
		specials[56] = REFERENCE_PHRASE.mostGeneralType();
		specials[57] = SEND_PHRASE.mostGeneralType();
		specials[58] = instanceMeta(mostGeneralLiteralTokenType());
		specials[59] = LIST_PHRASE.mostGeneralType();
		specials[60] = VARIABLE_USE_PHRASE.mostGeneralType();
		specials[61] = DECLARATION_PHRASE.mostGeneralType();
		specials[62] = ARGUMENT_PHRASE.mostGeneralType();
		specials[63] = LABEL_PHRASE.mostGeneralType();
		specials[64] = LOCAL_VARIABLE_PHRASE.mostGeneralType();
		specials[65] = LOCAL_CONSTANT_PHRASE.mostGeneralType();
		specials[66] = MODULE_VARIABLE_PHRASE.mostGeneralType();
		specials[67] = MODULE_CONSTANT_PHRASE.mostGeneralType();
		specials[68] = PRIMITIVE_FAILURE_REASON_PHRASE.mostGeneralType();
		specials[69] = anyMeta();
		specials[70] = trueObject();
		specials[71] = falseObject();
		specials[72] = zeroOrMoreOf(stringType());
		specials[73] = zeroOrMoreOf(topMeta());
		specials[74] = zeroOrMoreOf(
			setTypeForSizesContentType(wholeNumbers(), stringType()));
		specials[75] = setTypeForSizesContentType(wholeNumbers(), stringType());
		specials[76] =
			functionType(tuple(naturalNumbers()), bottom());
		specials[77] = emptySet();
		specials[78] = negativeInfinity();
		specials[79] = positiveInfinity();
		specials[80] = mostGeneralPojoType();
		specials[81] = pojoBottom();
		specials[82] = nullPojo();
		specials[83] = pojoSelfType();
		specials[84] = instanceMeta(mostGeneralPojoType());
		specials[85] = instanceMeta(mostGeneralPojoArrayType());
		specials[86] = functionTypeReturning(mostGeneralPojoType());
		specials[87] = mostGeneralPojoArrayType();
		specials[88] = pojoSelfTypeAtom();
		specials[89] = pojoTypeForClass(Throwable.class);
		specials[90] =
			functionType(emptyTuple(), TOP.o());
		specials[91] =
			functionType(emptyTuple(), booleanType());
		specials[92] = variableTypeFor(mostGeneralContinuationType());
		specials[93] = mapTypeForSizesKeyTypeValueType(
			wholeNumbers(), ATOM.o(), ANY.o());
		specials[94] = mapTypeForSizesKeyTypeValueType(
			wholeNumbers(),ATOM.o(), anyMeta());
		specials[95] = tupleTypeForSizesTypesDefaultType(
			wholeNumbers(),
			emptyTuple(),
			tupleTypeForSizesTypesDefaultType(
				singleInt(2), emptyTuple(), ANY.o()));
		specials[96] = emptyMap();
		specials[97] = mapTypeForSizesKeyTypeValueType(
			naturalNumbers(), ANY.o(), ANY.o());
		specials[98] = instanceMeta(wholeNumbers());
		specials[99] = setTypeForSizesContentType(naturalNumbers(), ANY.o());
		specials[100] = tupleTypeForSizesTypesDefaultType(
			wholeNumbers(), emptyTuple(),
			mostGeneralTupleType());
		specials[101] = nybbles();
		specials[102] = zeroOrMoreOf(nybbles());
		specials[103] = unsignedShorts();
		specials[104] = emptyTuple();
		specials[105] =
			functionType(tuple(bottom()), TOP.o());
		specials[106] = instanceType(zero());
		specials[107] = functionTypeReturning(topMeta());
		specials[108] = tupleTypeForSizesTypesDefaultType(
			wholeNumbers(), emptyTuple(),
			functionTypeReturning(topMeta()));
		specials[109] = functionTypeReturning(PARSE_PHRASE.mostGeneralType());
		specials[110] = instanceType(two());
		specials[111] = fromDouble(Math.E);
		specials[112] = instanceType(fromDouble(Math.E));
		specials[113] = instanceMeta(PARSE_PHRASE.mostGeneralType());
		specials[114] = setTypeForSizesContentType(wholeNumbers(), ATOM.o());
		specials[115] = TOKEN.o();
		specials[116] = mostGeneralLiteralTokenType();
		specials[117] = zeroOrMoreOf(anyMeta());
		specials[118] = inclusive(zero(), positiveInfinity());
		specials[119] = zeroOrMoreOf(
			tupleTypeForSizesTypesDefaultType(
				singleInt(2), tuple(ATOM.o()), anyMeta()));
		specials[120] = zeroOrMoreOf(
			tupleTypeForSizesTypesDefaultType(
				singleInt(2), tuple(ATOM.o()), ANY.o()));
		specials[121] = zeroOrMoreOf(PARSE_PHRASE.mostGeneralType());
		specials[122] = zeroOrMoreOf(ARGUMENT_PHRASE.mostGeneralType());
		specials[123] = zeroOrMoreOf(DECLARATION_PHRASE.mostGeneralType());
		specials[124] = variableReadWriteType(TOP.o(), bottom());
		specials[125] = zeroOrMoreOf(EXPRESSION_PHRASE.create(ANY.o()));
		specials[126] = EXPRESSION_PHRASE.create(ANY.o());
		specials[127] =
			functionType(tuple(pojoTypeForClass(Throwable.class)), bottom());
		specials[128] = zeroOrMoreOf(
			setTypeForSizesContentType(wholeNumbers(), ATOM.o()));
		specials[129] = bytes();
		specials[130] = zeroOrMoreOf(zeroOrMoreOf(anyMeta()));
		specials[131] = variableReadWriteType(extendedIntegers(), bottom());
		specials[132] = fiberMeta();
		specials[133] = oneOrMoreOf(CHARACTER.o());
		specials[134] = setTypeForSizesContentType(
			wholeNumbers(), exceptionType());
		specials[135] = setTypeForSizesContentType(
			naturalNumbers(), stringType());
		specials[136] = setTypeForSizesContentType(naturalNumbers(), ATOM.o());
		specials[137] = oneOrMoreOf(ANY.o());
		specials[138] = zeroOrMoreOf(integers());
		specials[139] = tupleTypeForSizesTypesDefaultType(
			integerRangeType(fromInt(2), true, positiveInfinity(), false),
			emptyTuple(),
			ANY.o());
		// Some of these entries may need to be shuffled into earlier slots to
		// maintain reasonable topical consistency.
		specials[140] = FIRST_OF_SEQUENCE_PHRASE.mostGeneralType();
		specials[141] = PERMUTED_LIST_PHRASE.mostGeneralType();
		specials[142] = SUPER_CAST_PHRASE.mostGeneralType();
		specials[143] = SpecialAtom.CLIENT_DATA_GLOBAL_KEY.atom;
		specials[144] = SpecialAtom.COMPILER_SCOPE_MAP_KEY.atom;
		specials[145] = SpecialAtom.ALL_TOKENS_KEY.atom;
		specials[146] = int32();
		specials[147] = int64();
		specials[148] = STATEMENT_PHRASE.mostGeneralType();
		specials[149] = SpecialAtom.COMPILER_SCOPE_STACK_KEY.atom;
		specials[150] = EXPRESSION_AS_STATEMENT_PHRASE.mostGeneralType();
		specials[151] = oneOrMoreOf(naturalNumbers());
		specials[152] = zeroOrMoreOf(DEFINITION.o());
		specials[153] = mapTypeForSizesKeyTypeValueType(
			wholeNumbers(), stringType(), ATOM.o());
		specials[154] = SpecialAtom.MACRO_BUNDLE_KEY.atom;
		specials[155] = SpecialAtom.EXPLICIT_SUBCLASSING_KEY.atom;
		specials[156] = variableReadWriteType(mostGeneralMapType(), bottom());
		specials[157] = lexerFilterFunctionType();
		specials[158] = lexerBodyFunctionType();
		specials[159] = SpecialAtom.STATIC_TOKENS_KEY.atom;
		specials[160] = TokenType.END_OF_FILE.atom;
		specials[161] = TokenType.KEYWORD.atom;
		specials[162] = TokenType.LITERAL.atom;
		specials[163] = TokenType.OPERATOR.atom;
		specials[164] = TokenType.COMMENT.atom;
		specials[165] = TokenType.WHITESPACE.atom;
		specials[166] = inclusive(0, (1L << 32) - 1);
		specials[167] = inclusive(0, (1L << 28) - 1);

		// DO NOT CHANGE THE ORDER OF THESE ENTRIES!  Serializer compatibility
		// depends on the order of this list.
		assert specialAtomsList.isEmpty();
		specialAtomsList.addAll(asList(
			SpecialAtom.ALL_TOKENS_KEY.atom,
			SpecialAtom.CLIENT_DATA_GLOBAL_KEY.atom,
			SpecialAtom.COMPILER_SCOPE_MAP_KEY.atom,
			SpecialAtom.COMPILER_SCOPE_STACK_KEY.atom,
			SpecialAtom.EXPLICIT_SUBCLASSING_KEY.atom,
			SpecialAtom.FALSE.atom,
			SpecialAtom.FILE_KEY.atom,
			SpecialAtom.HERITABLE_KEY.atom,
			SpecialAtom.MACRO_BUNDLE_KEY.atom,
			SpecialAtom.MESSAGE_BUNDLE_KEY.atom,
			SpecialAtom.OBJECT_TYPE_NAME_PROPERTY_KEY.atom,
			SpecialAtom.SERVER_SOCKET_KEY.atom,
			SpecialAtom.SOCKET_KEY.atom,
			SpecialAtom.STATIC_TOKENS_KEY.atom,
			SpecialAtom.TRUE.atom,
			CompiledCodeDescriptor.lineNumberKeyAtom(),
			CompiledCodeDescriptor.methodNameKeyAtom(),
			SpecialMethodAtom.ABSTRACT_DEFINER.atom,
			SpecialMethodAtom.ADD_TO_MAP_VARIABLE.atom,
			SpecialMethodAtom.ALIAS.atom,
			SpecialMethodAtom.APPLY.atom,
			SpecialMethodAtom.ATOM_PROPERTY.atom,
			SpecialMethodAtom.CONTINUATION_CALLER.atom,
			SpecialMethodAtom.CRASH.atom,
			SpecialMethodAtom.CREATE_LITERAL_PHRASE.atom,
			SpecialMethodAtom.CREATE_LITERAL_TOKEN.atom,
			SpecialMethodAtom.DECLARE_STRINGIFIER.atom,
			SpecialMethodAtom.FORWARD_DEFINER.atom,
			SpecialMethodAtom.GET_VARIABLE.atom,
			SpecialMethodAtom.GRAMMATICAL_RESTRICTION.atom,
			SpecialMethodAtom.MACRO_DEFINER.atom,
			SpecialMethodAtom.METHOD_DEFINER.atom,
			SpecialMethodAtom.ADD_UNLOADER.atom,
			SpecialMethodAtom.PUBLISH_ATOMS.atom,
			SpecialMethodAtom.RESUME_CONTINUATION.atom,
			SpecialMethodAtom.RECORD_TYPE_NAME.atom,
			SpecialMethodAtom.CREATE_MODULE_VARIABLE.atom,
			SpecialMethodAtom.SEAL.atom,
			SpecialMethodAtom.SEMANTIC_RESTRICTION.atom,
			SpecialMethodAtom.LEXER_DEFINER.atom,
			exceptionAtom(),
			stackDumpAtom(),
			pojoSelfTypeAtom(),
			TokenType.END_OF_FILE.atom,
			TokenType.KEYWORD.atom,
			TokenType.LITERAL.atom,
			TokenType.OPERATOR.atom,
			TokenType.COMMENT.atom,
			TokenType.WHITESPACE.atom,
			P_CreateToken.tokenTypeOrdinalKey));

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
				assert !object.isAtom() || object.isAtomSpecial();
			}
		}
	}

	/**
	 * The loaded Avail {@linkplain ModuleDescriptor modules}: a
	 * {@linkplain MapDescriptor map} from {@linkplain TupleDescriptor module
	 * names} to {@linkplain ModuleDescriptor modules}.
	 */
	private A_Map modules = emptyMap();

	/**
	 * Add the specified {@linkplain ModuleDescriptor module} to the
	 * runtime.
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
	 * Does the runtime define a {@linkplain ModuleDescriptor module} with the
	 * specified {@linkplain TupleDescriptor name}?
	 *
	 * @param moduleName A {@linkplain TupleDescriptor name}.
	 * @return {@code true} if the runtime defines a {@linkplain
	 *         ModuleDescriptor module} with the specified {@linkplain
	 *         TupleDescriptor name}, {@code false} otherwise.
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
						SpecialAtom.MESSAGE_BUNDLE_KEY.atom,
						nil);
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
	 * Remove a grammatical restriction from the method associated with the
	 * given method name.
	 *
	 * @param restriction
	 *            A {@linkplain A_GrammaticalRestriction grammatical
	 *            restriction} that validates syntactic restrictions at call
	 *            sites.
	 */
	public void removeGrammaticalRestriction (
		final A_GrammaticalRestriction restriction)
	{
		runtimeLock.writeLock().lock();
		try
		{
			final A_Bundle bundle = restriction.restrictedBundle();
			bundle.removeGrammaticalRestriction(restriction);
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
	 * The {@linkplain ReentrantLock lock} that guards access to the Level One
	 * {@linkplain #levelOneSafeTasks -safe} and {@linkplain
	 * #levelOneUnsafeTasks -unsafe} queues and counters.
	 *
	 * <p>For example, an {@link L2Chunk} may not be {@linkplain
	 * L2Chunk#invalidate(Statistic) invalidated} while any {@linkplain
	 * FiberDescriptor fiber} is {@linkplain ExecutionState#RUNNING running} a
	 * Level Two chunk.  These two activities are mutually exclusive.</p>
	 */
	private final ReentrantLock levelOneSafeLock = new ReentrantLock();

	/**
	 * The {@linkplain Queue queue} of Level One-safe {@linkplain Runnable
	 * tasks}. A Level One-safe task requires that no {@linkplain
	 * #levelOneUnsafeTasks Level One-unsafe tasks} are running.
	 *
	 * <p>For example, a {@linkplain L2Chunk Level Two chunk} may not be
	 * {@linkplain L2Chunk#invalidate(Statistic) invalidated} while any
	 * {@linkplain FiberDescriptor fiber} is {@linkplain ExecutionState#RUNNING
	 * running} a Level Two chunk. These two activities are mutually exclusive.
	 * </p>
	 */
	private final Queue<AvailTask> levelOneSafeTasks = new ArrayDeque<>();

	/**
	 * The {@linkplain Queue queue} of Level One-unsafe {@linkplain
	 * Runnable tasks}. A Level One-unsafe task requires that no
	 * {@linkplain #levelOneSafeTasks Level One-safe tasks} are running.
	 */
	private final Queue<AvailTask> levelOneUnsafeTasks = new ArrayDeque<>();

	/**
	 * The number of {@linkplain #levelOneSafeTasks Level One-safe tasks} that
	 * have been {@linkplain #executor scheduled for execution} but have not
	 * yet reached completion.
	 */
	private int incompleteLevelOneSafeTasks = 0;

	/**
	 * The number of {@linkplain #levelOneUnsafeTasks Level One-unsafe tasks}
	 * that have been {@linkplain #executor scheduled for execution} but have
	 * not yet reached completion.
	 */
	private int incompleteLevelOneUnsafeTasks = 0;

	/**
	 * Has {@linkplain #whenLevelOneUnsafeDo(int, Continuation0)} Level One
	 * safety} been requested?
	 */
	private volatile boolean levelOneSafetyRequested = false;

	/**
	 * Has {@linkplain #whenLevelOneUnsafeDo(int, Continuation0)} Level One
	 * safety} been requested?
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
	 * @param priority
	 *        The priority of the {@link AvailTask} to queue.  It must be in the
	 *        range [0..255].
	 * @param unsafeAction
	 *        The {@link Continuation0} to perform when Level One safety is not
	 *        required.
	 */
	public void whenLevelOneUnsafeDo (
		final int priority,
		final Continuation0 unsafeAction)
	{
		final AvailTask wrapped = new AvailTask(
			priority,
			() ->
			{
				try
				{
					unsafeAction.value();
				}
				catch (final Exception e)
				{
					System.err.println(
						"Exception in level-one-unsafe task:\n"
							+ trace(e));
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
			});
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
				execute(wrapped);
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
	 * @param priority
	 *        The priority of the {@link AvailTask} to queue.  It must be in the
	 *        range [0..255].
	 * @param safeAction
	 *        The {@link Continuation0} to execute when Level One safety is
	 *        ensured.
	 */
	public void whenLevelOneSafeDo (
		final int priority,
		final Continuation0 safeAction)
	{
		final AvailTask task = new AvailTask(
			priority,
			() ->
			{
				try
				{
					safeAction.value();
				}
				catch (final Exception e)
				{
					System.err.println(
						"Exception in level-one-safe task:\n"
							+ trace(e));
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
							for (final AvailTask t : levelOneUnsafeTasks)
							{
								execute(t);
							}
							levelOneUnsafeTasks.clear();
						}
					}
					finally
					{
						levelOneSafeLock.unlock();
					}
				}
			});
		levelOneSafeLock.lock();
		try
		{
			levelOneSafetyRequested = true;
			if (incompleteLevelOneUnsafeTasks == 0)
			{
				incompleteLevelOneSafeTasks++;
				execute(task);
			}
			else
			{
				levelOneSafeTasks.add(task);
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
		modules = nil;
	}

	/**
	 * Capture the current time with nanosecond precision (but not necessarily
	 * accuracy).  If per-thread accounting is available, use it.
	 *
	 * @return The current value of the nanosecond counter, or if supported, the
	 *         number of nanoseconds of CPU time that the current thread has
	 *         consumed.
	 */
	public static long captureNanos ()
	{
		return System.nanoTime();
	}
}
