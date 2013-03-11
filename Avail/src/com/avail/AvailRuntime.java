/**
 * AvailRuntime.java
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

package com.avail;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;
import com.avail.annotations.*;
import com.avail.builder.*;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.exceptions.*;
import com.avail.utility.Continuation0;

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
	 * explicit synchronization here is not required.</p>
	 *
	 * @return A 32-bit pseudo-random number.
	 */
	@ThreadSafe
	public static int nextHash ()
	{
		return rng.nextInt();
	}

	/**
	 * The source of {@linkplain FiberDescriptor fiber} identifiers.
	 */
	private static final AtomicInteger fiberIdGenerator = new AtomicInteger(1);

	/**
	 * Answer the next unused {@linkplain FiberDescriptor fiber} identifier.
	 * Fiber identifiers will not repeat for 2^32-1 invocations.
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
	private final ThreadFactory threadFactory =
		new ThreadFactory()
		{
			@Override
			public AvailThread newThread (final @Nullable Runnable runnable)
			{
				assert runnable != null;
				return new AvailThread(AvailRuntime.this, runnable);
			}
		};

	/** The number of available processors. */
	private static final int availableProcessors =
		Runtime.getRuntime().availableProcessors();

	/**
	 * The {@linkplain ThreadPoolExecutor thread pool executor} for
	 * this {@linkplain AvailRuntime Avail runtime}.
	 */
	private final ThreadPoolExecutor executor =
		new ThreadPoolExecutor(
			availableProcessors << 2,
			availableProcessors << 2,
			30000L,
			TimeUnit.MILLISECONDS,
			new PriorityBlockingQueue<Runnable>(100),
			threadFactory,
			new ThreadPoolExecutor.CallerRunsPolicy());

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
			availableProcessors << 1,
			30000L,
			TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>(),
			threadFactory,
			new ThreadPoolExecutor.CallerRunsPolicy());

	/**
	 * Open an {@linkplain AsynchronousFileChannel asynchronous file channel}
	 * for the specified {@linkplain Path path} and {@linkplain OpenOption
	 * open options}.
	 *
	 * @param path A path.
	 * @param openOptions The open options.
	 * @return An asynchronous file channel.
	 * @throws IOException
	 *         If the open fails for any reason.
	 */
	public AsynchronousFileChannel openFile (
			final Path path,
			final OpenOption... openOptions)
		throws IOException
	{
		return AsynchronousFileChannel.open(
			path,
			new HashSet<OpenOption>(Arrays.asList(openOptions)),
			fileExecutor);
	}

	/**
	 * The {@linkplain ThreadPoolExecutor thread pool executor} for asynchronous
	 * socket operations performed on behalf of this {@linkplain AvailRuntime
	 * Avail runtime}.
	 */
	private final ThreadPoolExecutor socketExecutor =
		new ThreadPoolExecutor(
			availableProcessors,
			availableProcessors << 1,
			30000L,
			TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>(),
			threadFactory,
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
	public volatile long clock = 0L;

	{
		// Schedule a fixed-rate timer task to increment the runtime clock.
		timer.scheduleAtFixedRate(
			new TimerTask()
			{
				@Override
				public void run ()
				{
					clock++;
				}
			},
			1,
			1);
	}

	/**
	 * The {@linkplain ModuleNameResolver module name resolver} that this
	 * {@linkplain AvailRuntime runtime} should use to resolve unqualified
	 * {@linkplain ModuleDescriptor module} names.
	 */
	private ModuleNameResolver moduleNameResolver;

	/**
	 * Answer the {@linkplain ModuleNameResolver module name resolver} that this
	 * {@linkplain AvailRuntime runtime} should use to resolve unqualified
	 * {@linkplain ModuleDescriptor module} names.
	 *
	 * @return A {@linkplain ModuleNameResolver module name resolver}.
	 */
	public ModuleNameResolver moduleNameResolver ()
	{
		return moduleNameResolver;
	}

	/**
	 * Answer the Avail {@linkplain ModuleRoots module roots}.
	 *
	 * @return The Avail {@linkplain ModuleRoots module roots}.
	 */
	@ThreadSafe
	public ModuleRoots moduleRoots ()
	{
		return moduleNameResolver.moduleRoots();
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

	/** The {@linkplain PrintStream standard output stream}. */
	private PrintStream standardOutputStream = System.out;

	/**
	 * Answer the {@linkplain PrintStream standard output stream}.
	 *
	 * @return The standard output stream.
	 */
	@ThreadSafe
	public PrintStream standardOutputStream ()
	{
		runtimeLock.readLock().lock();
		try
		{
			return standardOutputStream;
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/** The {@linkplain PrintStream standard error stream}. */
	private PrintStream standardErrorStream = System.err;

	/**
	 * Answer the {@linkplain PrintStream standard error stream}.
	 *
	 * @return The standard error stream.
	 */
	@ThreadSafe
	public PrintStream standardErrorStream ()
	{
		runtimeLock.readLock().lock();
		try
		{
			return standardErrorStream;
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/** The {@linkplain InputStream standard input stream}. */
	private InputStream standardInputStream = System.in;

	/**
	 * Answer the {@linkplain PrintStream standard input stream}.
	 *
	 * @return The standard input stream.
	 */
	@ThreadSafe
	public InputStream standardInputStream ()
	{
		runtimeLock.readLock().lock();
		try
		{
			return standardInputStream;
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/** The {@linkplain Reader standard input reader}. */
	private Reader standardInputReader = new BufferedReader(
		new InputStreamReader(standardInputStream));

	/**
	 * Answer the {@linkplain Reader standard input reader}.
	 *
	 * @return The standard input reader.
	 */
	@ThreadSafe
	public Reader standardInputReader ()
	{
		runtimeLock.readLock().lock();
		try
		{
			return standardInputReader;
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Replace one or more of the standard I/O streams used by this {@linkplain
	 * AvailRuntime Avail runtime}.
	 *
	 * @param outputStream
	 *        The new {@linkplain PrintStream standard output stream}, or
	 *        {@code null} if the standard output stream should not be replaced.
	 * @param errorStream
	 *        The new standard error stream, or {@code null} if the standard
	 *        error stream should not be replaced.
	 * @param inputStream
	 *        The new {@linkplain InputStream standard input stream}, or {@code
	 *        null} if the standard input stream should not be replaced.
	 */
	@ThreadSafe
	public void setStandardStreams (
		final @Nullable PrintStream outputStream,
		final @Nullable PrintStream errorStream,
		final @Nullable InputStream inputStream)
	{
		runtimeLock.writeLock().lock();
		try
		{
			if (outputStream != null)
			{
				standardOutputStream = outputStream;
			}
			if (errorStream != null)
			{
				standardErrorStream = errorStream;
			}
			if (inputStream != null)
			{
				standardInputStream = inputStream;
				standardInputReader = new BufferedReader(
					new InputStreamReader(inputStream));
			}
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
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
		addMethod(MethodDescriptor.vmCrashMethod());
		addMethod(MethodDescriptor.vmFunctionApplyMethod());
		addMethod(MethodDescriptor.vmMethodDefinerMethod());
		addMethod(MethodDescriptor.vmMacroDefinerMethod());
		addMethod(MethodDescriptor.vmPublishAtomsMethod());
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
		new AvailObject[150];

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
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@ThreadSafe
	public static AvailObject specialObject (final int ordinal)
		throws ArrayIndexOutOfBoundsException
	{
		return specialObjects[ordinal];
	}

	/**
	 * The {@linkplain AtomDescriptor special atoms} known to the {@linkplain
	 * AvailRuntime runtime}.
	 */
	private static final AvailObject[] specialAtoms =
		new AvailObject[20];

	/**
	 * The {@linkplain AtomDescriptor special atoms} known to the {@linkplain
	 * AvailRuntime runtime}.
	 */
	private static final List<AvailObject> specialAtomsList =
		Collections.unmodifiableList(Arrays.asList(specialAtoms));

	/**
	 * The {@link Set} of special {@linkplain AtomDescriptor atoms}.
	 */
	private static Set<AvailObject> specialAtomsSet;

	/**
	 * Answer the {@linkplain AtomDescriptor special atoms} known to the
	 * {@linkplain AvailRuntime runtime} as an {@linkplain
	 * Collections#unmodifiableList(List) immutable} {@linkplain List list}.
	 *
	 * @return The special atoms list.
	 */
	@ThreadSafe
	public static List<AvailObject> specialAtoms()
	{
		return specialAtomsList;
	}

	/**
	 * Is the specified {@linkplain AtomDescriptor atom} one of the {@linkplain
	 * #specialAtoms() special atoms} known to the {@linkplain AvailRuntime
	 * runtime}?
	 *
	 * @param atom An atom.
	 * @return {@code true} if the specified atom is one of the special atoms,
	 *         {@code false} otherwise.
	 */
	@ThreadSafe
	public static boolean isSpecialAtom (final AvailObject atom)
	{
		return specialAtomsSet.contains(atom);
	}

	/**
	 * Set up the special objects table.
	 */
	public static void createWellKnownObjects ()
	{
		// Set up the special objects.
		specialObjects[1] = ANY.o();
		specialObjects[2] = EnumerationTypeDescriptor.booleanObject();
		specialObjects[3] = CHARACTER.o();
		specialObjects[4] = FunctionTypeDescriptor.mostGeneralType();
		specialObjects[5] = FunctionTypeDescriptor.meta();
		specialObjects[6] = CompiledCodeTypeDescriptor.mostGeneralType();
		specialObjects[7] = VariableTypeDescriptor.mostGeneralType();
		specialObjects[8] = VariableTypeDescriptor.meta();
		specialObjects[9] = ContinuationTypeDescriptor.mostGeneralType();
		specialObjects[10] = ContinuationTypeDescriptor.meta();
		specialObjects[11] = ATOM.o();
		specialObjects[12] = DOUBLE.o();
		specialObjects[13] = IntegerRangeTypeDescriptor.extendedIntegers();
		specialObjects[14] = InstanceMetaDescriptor.on(
			TupleTypeDescriptor.zeroOrMoreOf(
				InstanceMetaDescriptor.anyMeta()));
		specialObjects[15] = FLOAT.o();
		specialObjects[16] = NUMBER.o();
		specialObjects[17] = IntegerRangeTypeDescriptor.integers();
		specialObjects[18] = IntegerRangeTypeDescriptor.meta();
		specialObjects[19] = MapTypeDescriptor.meta();
		specialObjects[20] = MODULE.o();
		specialObjects[21] = TupleDescriptor.fromIntegerList(
			AvailErrorCode.allNumericCodes());
		specialObjects[22] = ObjectTypeDescriptor.mostGeneralType();
		specialObjects[23] = ObjectTypeDescriptor.meta();
		specialObjects[24] = ObjectTypeDescriptor.exceptionType();
		specialObjects[25] = FIBER.o();
		specialObjects[26] = SetTypeDescriptor.mostGeneralType();
		specialObjects[27] = SetTypeDescriptor.meta();
		specialObjects[28] = TupleTypeDescriptor.stringTupleType();
		specialObjects[29] = BottomTypeDescriptor.bottom();
		specialObjects[30] = InstanceMetaDescriptor.on(
			BottomTypeDescriptor.bottom());
		specialObjects[31] = NONTYPE.o();
		specialObjects[32] = TupleTypeDescriptor.mostGeneralType();
		specialObjects[33] = TupleTypeDescriptor.meta();
		specialObjects[34] = InstanceMetaDescriptor.topMeta();
		specialObjects[35] = TOP.o();
		specialObjects[36] = IntegerRangeTypeDescriptor.wholeNumbers();
		specialObjects[37] = IntegerRangeTypeDescriptor.naturalNumbers();
		specialObjects[38] = IntegerRangeTypeDescriptor.characterCodePoints();
		specialObjects[39] = MapTypeDescriptor.mostGeneralType();
		specialObjects[40] = MESSAGE_BUNDLE.o();
		specialObjects[41] = MESSAGE_BUNDLE_TREE.o();
		specialObjects[42] = METHOD.o();
		specialObjects[43] = DEFINITION.o();
		specialObjects[44] = ABSTRACT_DEFINITION.o();
		specialObjects[45] = FORWARD_DEFINITION.o();
		specialObjects[46] = METHOD_DEFINITION.o();
		specialObjects[47] = MACRO_DEFINITION.o();
		specialObjects[50] = PARSE_NODE.mostGeneralType();
		specialObjects[51] = SEQUENCE_NODE.mostGeneralType();
		specialObjects[52] = EXPRESSION_NODE.mostGeneralType();
		specialObjects[53] = ASSIGNMENT_NODE.mostGeneralType();
		specialObjects[54] = BLOCK_NODE.mostGeneralType();
		specialObjects[55] = LITERAL_NODE.mostGeneralType();
		specialObjects[56] = REFERENCE_NODE.mostGeneralType();
		specialObjects[57] = SEND_NODE.mostGeneralType();
		specialObjects[58] = InstanceMetaDescriptor.on(
			LiteralTokenTypeDescriptor.mostGeneralType());
		specialObjects[59] = LIST_NODE.mostGeneralType();
		specialObjects[60] = VARIABLE_USE_NODE.mostGeneralType();
		specialObjects[61] = DECLARATION_NODE.mostGeneralType();
		specialObjects[62] = ARGUMENT_NODE.mostGeneralType();
		specialObjects[63] = LABEL_NODE.mostGeneralType();
		specialObjects[64] = LOCAL_VARIABLE_NODE.mostGeneralType();
		specialObjects[65] = LOCAL_CONSTANT_NODE.mostGeneralType();
		specialObjects[66] = MODULE_VARIABLE_NODE.mostGeneralType();
		specialObjects[67] = MODULE_CONSTANT_NODE.mostGeneralType();
		specialObjects[68] = PRIMITIVE_FAILURE_REASON_NODE.mostGeneralType();
		specialObjects[69] = InstanceMetaDescriptor.anyMeta();
		specialObjects[70] = AtomDescriptor.trueObject();
		specialObjects[71] = AtomDescriptor.falseObject();
		specialObjects[72] =
			TupleTypeDescriptor.zeroOrMoreOf(
				TupleTypeDescriptor.stringTupleType());
		specialObjects[73] =
			TupleTypeDescriptor.zeroOrMoreOf(
				InstanceMetaDescriptor.topMeta());
		specialObjects[74] =
			TupleTypeDescriptor.zeroOrMoreOf(
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleTypeDescriptor.stringTupleType()));
		specialObjects[75] =
			SetTypeDescriptor.setTypeForSizesContentType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleTypeDescriptor.stringTupleType());
		specialObjects[76] =
			FunctionTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.naturalNumbers()),
				BottomTypeDescriptor.bottom());
		specialObjects[77] = SetDescriptor.empty();
		specialObjects[78] = InfinityDescriptor.negativeInfinity();
		specialObjects[79] = InfinityDescriptor.positiveInfinity();
		specialObjects[80] = PojoTypeDescriptor.mostGeneralType();
		specialObjects[81] = PojoTypeDescriptor.pojoBottom();
		specialObjects[82] = PojoDescriptor.nullObject();
		specialObjects[83] = PojoTypeDescriptor.selfType();
		specialObjects[84] = InstanceMetaDescriptor.on(
			PojoTypeDescriptor.mostGeneralType());
		specialObjects[85] = InstanceMetaDescriptor.on(
			PojoTypeDescriptor.mostGeneralArrayType());
		specialObjects[86] = FunctionTypeDescriptor.forReturnType(
			PojoTypeDescriptor.mostGeneralType());
		specialObjects[87] = PojoTypeDescriptor.mostGeneralArrayType();
		specialObjects[88] = PojoTypeDescriptor.selfAtom();
		specialObjects[89] = PojoTypeDescriptor.forClass(Throwable.class);
		specialObjects[90] = FunctionTypeDescriptor.create(
			TupleDescriptor.empty(),
			TOP.o());
		specialObjects[91] = FunctionTypeDescriptor.create(
			TupleDescriptor.empty(),
			EnumerationTypeDescriptor.booleanObject());
		specialObjects[92] = VariableTypeDescriptor.wrapInnerType(
			ContinuationTypeDescriptor.mostGeneralType());
		specialObjects[93] = MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			IntegerRangeTypeDescriptor.wholeNumbers(),
			ATOM.o(),
			ANY.o());
		specialObjects[94] = MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			IntegerRangeTypeDescriptor.wholeNumbers(),
			ATOM.o(),
			InstanceMetaDescriptor.anyMeta());
		specialObjects[95] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.singleInt(2),
					TupleDescriptor.empty(),
					ANY.o()));
		specialObjects[96] = MapDescriptor.empty();
		specialObjects[97] = MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			IntegerRangeTypeDescriptor.naturalNumbers(),
			ANY.o(),
			ANY.o());
		specialObjects[98] = InstanceMetaDescriptor.on(
			IntegerRangeTypeDescriptor.wholeNumbers());
		specialObjects[99] = SetTypeDescriptor.setTypeForSizesContentType(
			IntegerRangeTypeDescriptor.naturalNumbers(),
			ANY.o());
		specialObjects[100] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				TupleTypeDescriptor.mostGeneralType());
		specialObjects[101] = IntegerRangeTypeDescriptor.nybbles();
		specialObjects[102] =
			TupleTypeDescriptor.zeroOrMoreOf(
				IntegerRangeTypeDescriptor.nybbles());
		specialObjects[103] = IntegerRangeTypeDescriptor.unsignedShorts();
		specialObjects[104] = TupleDescriptor.empty();
		specialObjects[105] = FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				BottomTypeDescriptor.bottom()),
			TOP.o());
		specialObjects[106] = InstanceTypeDescriptor.on(
			IntegerDescriptor.zero());
		specialObjects[107] = FunctionTypeDescriptor.forReturnType(
			InstanceMetaDescriptor.topMeta());
		specialObjects[108] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				FunctionTypeDescriptor.forReturnType(
					InstanceMetaDescriptor.topMeta()));
		specialObjects[109] = FunctionTypeDescriptor.forReturnType(
			PARSE_NODE.mostGeneralType());
		specialObjects[110] = InstanceTypeDescriptor.on(
			IntegerDescriptor.two());
		specialObjects[111] = DoubleDescriptor.fromDouble(Math.E);
		specialObjects[112] = InstanceTypeDescriptor.on(
			DoubleDescriptor.fromDouble(Math.E));
		specialObjects[113] = InstanceMetaDescriptor.on(
			PARSE_NODE.mostGeneralType());
		specialObjects[114] = SetTypeDescriptor.setTypeForSizesContentType(
			IntegerRangeTypeDescriptor.wholeNumbers(),
			ATOM.o());
		specialObjects[115] = TOKEN.o();
		specialObjects[116] = LiteralTokenTypeDescriptor.mostGeneralType();
		specialObjects[117] =
			TupleTypeDescriptor.zeroOrMoreOf(
				InstanceMetaDescriptor.anyMeta());
		specialObjects[118] =
			IntegerRangeTypeDescriptor.create(
				IntegerDescriptor.zero(),
				true,
				InfinityDescriptor.positiveInfinity(),
				true);
		specialObjects[119] =
			TupleTypeDescriptor.zeroOrMoreOf(
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.singleInt(2),
					TupleDescriptor.from(ATOM.o()),
					InstanceMetaDescriptor.anyMeta()));
		specialObjects[120] =
			TupleTypeDescriptor.zeroOrMoreOf(
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.singleInt(2),
					TupleDescriptor.from(ATOM.o()),
					ANY.o()));
		specialObjects[121] =
			TupleTypeDescriptor.zeroOrMoreOf(
				PARSE_NODE.mostGeneralType());
		specialObjects[122] =
			TupleTypeDescriptor.zeroOrMoreOf(
				ARGUMENT_NODE.mostGeneralType());
		specialObjects[123] =
			TupleTypeDescriptor.zeroOrMoreOf(
				DECLARATION_NODE.mostGeneralType());
		specialObjects[124] =
			VariableTypeDescriptor.fromReadAndWriteTypes(
				TOP.o(),
				EXPRESSION_NODE.create(BottomTypeDescriptor.bottom()));
		specialObjects[125] =
			TupleTypeDescriptor.zeroOrMoreOf(
				EXPRESSION_NODE.create(ANY.o()));
		specialObjects[126] = EXPRESSION_NODE.create(ANY.o());
		specialObjects[127] =
			FunctionTypeDescriptor.create(
				TupleDescriptor.from(
					PojoTypeDescriptor.forClass(Throwable.class)),
				BottomTypeDescriptor.bottom());
		specialObjects[128] =
			TupleTypeDescriptor.zeroOrMoreOf(
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ATOM.o()));
		specialObjects[129] = IntegerRangeTypeDescriptor.bytes();
		specialObjects[130] =
			TupleTypeDescriptor.zeroOrMoreOf(
				TupleTypeDescriptor.zeroOrMoreOf(
					InstanceMetaDescriptor.anyMeta()));
		specialObjects[131] =
			VariableTypeDescriptor.fromReadAndWriteTypes(
				IntegerRangeTypeDescriptor.extendedIntegers(),
				BottomTypeDescriptor.bottom());

		// Declare all special atoms
		specialAtoms[0] = AtomDescriptor.trueObject();
		specialAtoms[1] = AtomDescriptor.falseObject();
		specialAtoms[2] = PojoTypeDescriptor.selfAtom();
		specialAtoms[3] = ObjectTypeDescriptor.exceptionAtom();
		specialAtoms[4] = MethodDescriptor.vmCrashAtom();
		specialAtoms[5] = MethodDescriptor.vmFunctionApplyAtom();
		specialAtoms[6] = MethodDescriptor.vmMethodDefinerAtom();
		specialAtoms[7] = MethodDescriptor.vmMacroDefinerAtom();
		specialAtoms[8] = MethodDescriptor.vmPublishAtomsAtom();
		specialAtoms[9] = AtomDescriptor.moduleHeaderSectionAtom();
		specialAtoms[10] = AtomDescriptor.moduleBodySectionAtom();
		specialAtoms[11] = ObjectTypeDescriptor.stackDumpAtom();
		specialAtoms[12] = AtomDescriptor.fileKey();
		specialAtoms[13] = AtomDescriptor.fileModeReadKey();
		specialAtoms[14] = AtomDescriptor.fileModeWriteKey();
		specialAtoms[15] = CompiledCodeDescriptor.methodNameKeyAtom();
		specialAtoms[16] = CompiledCodeDescriptor.lineNumberKeyAtom();

		assert specialAtomsSet == null;
		specialAtomsSet = new HashSet<AvailObject>(specialAtomsList);
		specialAtomsSet.remove(null);
		specialAtomsSet = Collections.unmodifiableSet(specialAtomsSet);
		for (int i = 0; i < specialObjects.length; i++)
		{
			final AvailObject object = specialObjects[i];
			if (object != null)
			{
				specialObjects[i] = object.makeShared();
				if (object.isAtom())
				{
					assert specialAtomsSet.contains(object);
				}
			}
		}
		for (int i = 0; i < specialAtoms.length; i++)
		{
			final AvailObject object = specialAtoms[i];
			if (object != null)
			{
				specialAtoms[i] = object.makeShared();
			}
		}
	}

	/**
	 * Release any statically held objects.
	 */
	public static void clearWellKnownObjects ()
	{
		Arrays.fill(specialObjects, null);
		Arrays.fill(specialAtoms, null);
		specialAtomsSet = null;
	}

	/**
	 * The loaded Avail {@linkplain ModuleDescriptor modules}: a
	 * {@linkplain MapDescriptor map} from {@linkplain TupleDescriptor module
	 * names} to {@linkplain ModuleDescriptor modules}.
	 */
	private AvailObject modules = MapDescriptor.empty();

	/**
	 * Add the specified {@linkplain ModuleDescriptor module} to the
	 * {@linkplain AvailRuntime runtime}.
	 *
	 * @param aModule A {@linkplain ModuleDescriptor module}.
	 */
	@ThreadSafe
	public void addModule (final AvailObject aModule)
	{
		runtimeLock.writeLock().lock();
		try
		{
			assert !modules.hasKey(aModule.name());

			// Add all visible message bundles to the root message bundle tree.
			for (final AvailObject name : aModule.visibleNames())
			{
				assert name.isAtom();
				final AvailObject rootBundle = rootBundleTree.includeBundle(
					MessageBundleDescriptor.newBundle(name));
				final AvailObject bundle =
					aModule.filteredBundleTree().includeBundle(
						MessageBundleDescriptor.newBundle(name));
				rootBundle.addGrammaticalRestrictions(
					bundle.grammaticalRestrictions());
			}

			// Finally add the module to the map of loaded modules.
			modules = modules.mapAtPuttingCanDestroy(
				aModule.name(), aModule, true);
		}
		catch (final SignatureException e)
		{
			// Shouldn't happen.
			throw new RuntimeException(e);
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
	public boolean includesModuleNamed (final AvailObject moduleName)
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
	 * Answer the {@linkplain ModuleDescriptor module} with the specified
	 * {@linkplain TupleDescriptor name}.
	 *
	 * @param moduleName A {@linkplain TupleDescriptor name}.
	 * @return A {@linkplain ModuleDescriptor module}.
	 */
	@ThreadSafe
	public AvailObject moduleAt (final AvailObject moduleName)
	{
		assert moduleName.isString();

		runtimeLock.readLock().lock();
		try
		{
			assert includesModuleNamed(moduleName);
			return modules.mapAt(moduleName);
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * The {@linkplain MethodDescriptor methods} currently known to the
	 * {@linkplain AvailRuntime runtime}: a {@linkplain MapDescriptor map} from
	 * {@linkplain AtomDescriptor method name} to {@linkplain
	 * MethodDescriptor method}.
	 */
	private AvailObject methods = MapDescriptor.empty();

	/**
	 * Are there any {@linkplain MethodDescriptor methods} bound to
	 * the specified {@linkplain AtomDescriptor selector}?
	 *
	 * @param selector A {@linkplain AtomDescriptor selector}.
	 * @return {@code true} if there are {@linkplain MethodDescriptor
	 *         methods} bound to the specified {@linkplain
	 *         AtomDescriptor selector}, {@code false} otherwise.
	 */
	@ThreadSafe
	public boolean hasMethodsAt (final AvailObject selector)
	{
		assert selector.isAtom();

		runtimeLock.readLock().lock();
		try
		{
			return methods.hasKey(selector);
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Add a {@linkplain MethodDescriptor method} to the runtime.
	 *
	 * @param method A {@linkplain MethodDescriptor method}.
	 */
	@ThreadSafe
	public void addMethod (
		final AvailObject method)
	{
		runtimeLock.writeLock().lock();
		try
		{
			final AvailObject methodName = method.name();
			assert !methods.hasKey(methodName);
			methods = methods.mapAtPuttingCanDestroy(
				methodName,
				method,
				true);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Answer the {@linkplain MethodDescriptor method} bound to the specified
	 * {@linkplain AtomDescriptor method name}. If necessary, then create a new
	 * method and bind it.
	 *
	 * @param methodName An {@linkplain AtomDescriptor atom} naming the method.
	 * @return The corresponding {@linkplain MethodDescriptor method}.
	 */
	@ThreadSafe
	public AvailObject methodFor (
		final AvailObject methodName)
	{
		runtimeLock.writeLock().lock();
		try
		{
			final AvailObject method;
			if (methods.hasKey(methodName))
			{
				method = methods.mapAt(methodName);
			}
			else
			{
				method = MethodDescriptor.newMethodWithName(methodName);
				methods = methods.mapAtPuttingCanDestroy(
					methodName,
					method,
					true);
			}
			return method;
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Answer the {@linkplain MethodDescriptor method}
	 * bound to the specified {@linkplain AtomDescriptor selector}.  If
	 * there is no method with that selector, answer {@linkplain
	 * NilDescriptor nil}.
	 *
	 * @param selector
	 *            A {@linkplain AtomDescriptor selector}.
	 * @return
	 *            A {@linkplain MethodDescriptor method}
	 *            or {@linkplain NilDescriptor nil}.
	 */
	@ThreadSafe
	public AvailObject methodsAt (final AvailObject selector)
	{
		assert selector.isAtom();

		runtimeLock.readLock().lock();
		try
		{
			if (methods.hasKey(selector))
			{
				return methods.mapAt(selector);
			}
			return NilDescriptor.nil();
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Unbind the specified implementation from the {@linkplain
	 * AtomDescriptor selector}. If no implementations remain in the
	 * {@linkplain MethodDescriptor method}, then forget the selector from the
	 * method dictionary and the {@linkplain #rootBundleTree() root message
	 * bundle tree}.
	 *
	 * @param selector A {@linkplain AtomDescriptor selector}.
	 * @param implementation An implementation.
	 */
	@ThreadSafe
	public void removeMethod (
		final AvailObject selector,
		final AvailObject implementation)
	{
		assert selector.isAtom();

		runtimeLock.writeLock().lock();
		try
		{
			if (methods.hasKey(selector))
			{
				final AvailObject method = methods.mapAt(selector);
				method.removeDefinition(implementation);
				if (method.isMethodEmpty())
				{
					methods = methods.mapWithoutKeyCanDestroy(selector, true);
					rootBundleTree.removeBundle(
						MessageBundleDescriptor.newBundle(selector));
				}
				if (method.isMethodEmpty())
				{
					methods = methods.mapWithoutKeyCanDestroy(
						selector,
						true);
				}
			}
		}
		catch (final SignatureException e)
		{
			// Shouldn't happen.
			throw new RuntimeException(e);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Add a type restriction to the method associated with the
	 * given method name.
	 *
	 * @param methodName
	 *            The method name, an {@linkplain AtomDescriptor atom}.
	 * @param typeRestrictionFunction
	 *            A {@linkplain FunctionDescriptor function} that validates the
	 *            static types of arguments at call sites.
	 */
	public void addTypeRestriction (
		final AvailObject methodName,
		final AvailObject typeRestrictionFunction)
	{
		assert methodName.isAtom();
		assert typeRestrictionFunction.isFunction();

		runtimeLock.writeLock().lock();
		try
		{
			final AvailObject method = methodFor(methodName);
			method.addTypeRestriction(typeRestrictionFunction);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Remove a type restriction from the method associated with the
	 * given method name.
	 *
	 * @param methodName
	 *            The method name, an {@linkplain AtomDescriptor atom}.
	 * @param typeRestrictionFunction
	 *            A {@linkplain FunctionDescriptor function} that validates the
	 *            static types of arguments at call sites.
	 */
	public void removeTypeRestriction (
		final AvailObject methodName,
		final AvailObject typeRestrictionFunction)
	{
		assert methodName.isAtom();
		assert typeRestrictionFunction.isFunction();
		runtimeLock.writeLock().lock();
		try
		{
			final AvailObject method = methodFor(methodName);
			method.removeTypeRestriction(typeRestrictionFunction);
			if (method.isMethodEmpty())
			{
				methods = methods.mapWithoutKeyCanDestroy(methodName, true);
			}
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
	 *        The signature at which to seal the method.
	 */
	public void addSeal (
		final AvailObject methodName,
		final AvailObject sealSignature)
	{
		assert methodName.isAtom();
		assert sealSignature.isTuple();
		runtimeLock.writeLock().lock();
		try
		{
			final AvailObject method = methodFor(methodName);
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
	 */
	public void removeSeal (
		final AvailObject methodName,
		final AvailObject sealSignature)
	{
		assert methodName.isAtom();
		assert sealSignature.isTuple();
		runtimeLock.writeLock().lock();
		try
		{
			final AvailObject method = methodFor(methodName);
			method.removeSealedArgumentsType(sealSignature);
			if (method.isMethodEmpty())
			{
				methods = methods.mapWithoutKeyCanDestroy(methodName, true);
			}
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * The root {@linkplain MessageBundleTreeDescriptor message bundle tree}. It
	 * contains the {@linkplain MessageBundleDescriptor message bundles}
	 * exported by all loaded {@linkplain ModuleDescriptor modules}.
	 */
	private AvailObject rootBundleTree = MessageBundleTreeDescriptor.newPc(1);

	/**
	 * Answer a copy of the root {@linkplain MessageBundleTreeDescriptor message
	 * bundle tree}.
	 *
	 * @return A {@linkplain MessageBundleTreeDescriptor message bundle tree}
	 *         that contains the {@linkplain MessageBundleDescriptor message
	 *         bundles} exported by all loaded {@linkplain ModuleDescriptor
	 *         modules}.
	 */
	@ThreadSafe
	public AvailObject rootBundleTree ()
	{
		runtimeLock.readLock().lock();
		try
		{
			final AvailObject copy = MessageBundleTreeDescriptor.newPc(1);
			rootBundleTree.copyToRestrictedTo(copy, methods.keysAsSet());
			return copy;
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * The {@linkplain ReentrantLock lock} that guards access to the Level One
	 * {@linkplain #levelOneSafeTasks -safe} and {@linkplain
	 * #levelOneUnsafeTasks -unsafe} queues and counters.
	 *
	 * <p>For example, a {@linkplain L2ChunkDescriptor Level Two chunk} may not
	 * be {@linkplain L2ChunkDescriptor#invalidateChunkAtIndex(int) invalidated}
	 * while any {@linkplain FiberDescriptor fiber} is {@linkplain
	 * ExecutionState#RUNNING running} a Level Two chunk. These two activities
	 * are mutually exclusive.</p>
	 */
	@InnerAccess final ReentrantLock levelOneSafeLock = new ReentrantLock();

	/**
	 * The {@linkplain Queue queue} of Level One-safe {@linkplain Runnable
	 * tasks}. A Level One-safe task requires that no {@linkplain
	 * #levelOneUnsafeTasks Level One-unsafe tasks} are running.
	 *
	 * <p>For example, a {@linkplain L2ChunkDescriptor Level Two chunk} may not
	 * be {@linkplain L2ChunkDescriptor#invalidateChunkAtIndex(int) invalidated}
	 * while any {@linkplain FiberDescriptor fiber} is {@linkplain
	 * ExecutionState#RUNNING running} a Level Two chunk. These two activities
	 * are mutually exclusive.</p>
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
	 * executed as a Level One-safe task at such a time as there are no Level
	 * One-unsafe tasks running.
	 *
	 * @param safeTask
	 *        What to do when Level One safety is ensured.
	 */
	public void whenLevelOneSafeDo (final AvailTask safeTask)
	{
		final AvailTask wrapped = new AvailTask(
			safeTask.priority,
			new Continuation0()
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
			});
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
	 * Request that the specified {@linkplain Continuation0 continuation} be
	 * executed as a Level One-unsafe task at such a time as there are no Level
	 * One-safe tasks running.
	 *
	 * @param unsafeTask
	 *        What to do when Level One safety is not required.
	 */
	public void whenLevelOneUnsafeDo (final AvailTask unsafeTask)
	{
		final AvailTask wrapped = new AvailTask(
			unsafeTask.priority,
			new Continuation0()
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
	 * Destroy all data structures used by this {@code AvailRuntime}.  Also
	 * disassociate it from the current {@link Thread}'s local storage.
	 */
	public void destroy ()
	{
		clearWellKnownObjects();
		moduleNameResolver = null;
		modules = null;
		methods = null;
		rootBundleTree = null;
		timer.cancel();
		executor.shutdownNow();
		fileExecutor.shutdownNow();
		socketExecutor.shutdownNow();
		try
		{
			executor.awaitTermination(100, TimeUnit.MILLISECONDS);
		}
		catch (final InterruptedException e)
		{
			// Ignore.
		}
		try
		{
			fileExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		}
		catch (final InterruptedException e)
		{
			// Ignore.
		}
		try
		{
			socketGroup.awaitTermination(100, TimeUnit.MILLISECONDS);
		}
		catch (final InterruptedException e)
		{
			// Ignore.
		}
	}
}
