/**
 * AvailRuntime.java
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

package com.avail;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.avail.annotations.*;
import com.avail.builder.*;
import com.avail.descriptor.*;
import com.avail.exceptions.*;

/**
 * An {@code AvailRuntime} comprises the {@linkplain ModuleDescriptor
 * modules}, {@linkplain MethodDescriptor methods}, and {@linkplain
 * #specialObject(int) special objects} that define an Avail system. It also
 * manages global resources, such as file connections.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class AvailRuntime
implements ThreadFactory
{
	/**
	 * The {@linkplain InheritableThreadLocal thread-local} {@linkplain
	 * AvailRuntime Avail runtime}
	 */
	private static final InheritableThreadLocal<AvailRuntime> current =
		new InheritableThreadLocal<AvailRuntime>();

	/**
	 * Set the {@linkplain #current() current} {@linkplain AvailRuntime Avail
	 * runtime} for the {@linkplain Thread#currentThread() current} {@linkplain
	 * AvailThread thread}.
	 *
	 * @param runtime
	 *        An Avail runtime.
	 */
	static final void setCurrent (final AvailRuntime runtime)
	{
		current.set(runtime);
	}

	/**
	 * Answer the {@linkplain AvailRuntime Avail runtime} associated with the
	 * current {@linkplain Thread thread}. If the {@linkplain
	 * Thread#currentThread() current thread} is not an {@link AvailThread},
	 * then answer {@code null}.
	 *
	 * @return The current Avail runtime, or {@code null} if the the current
	 *         thread is not an {@code AvailThread}.
	 */
	public static final AvailRuntime current ()
	{
		return current.get();
	}

	@Override
	public AvailThread newThread (final @Nullable Runnable runnable)
	{
		assert runnable != null;
		return new AvailThread(this, runnable);
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
		final PrintStream outputStream,
		final PrintStream errorStream,
		final InputStream inputStream)
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
		addMethod(MethodDescriptor.vmDefinerMethod());
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
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
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
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
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
		// 31
		specialObjects[32] = TupleTypeDescriptor.mostGeneralType();
		specialObjects[33] = TupleTypeDescriptor.meta();
		specialObjects[34] = InstanceMetaDescriptor.topMeta();
		specialObjects[35] = TOP.o();
		specialObjects[36] = IntegerRangeTypeDescriptor.wholeNumbers();
		specialObjects[37] = IntegerRangeTypeDescriptor.naturalNumbers();
		specialObjects[38] = IntegerRangeTypeDescriptor.characterCodePoints();
		specialObjects[39] = MapTypeDescriptor.mostGeneralType();
		specialObjects[40] = MESSAGE_BUNDLE.o();
		specialObjects[41] = SIGNATURE.o();
		specialObjects[42] = ABSTRACT_SIGNATURE.o();
		specialObjects[43] = FORWARD_SIGNATURE.o();
		specialObjects[44] = METHOD_SIGNATURE.o();
		specialObjects[45] = MESSAGE_BUNDLE_TREE.o();
		specialObjects[46] = METHOD.o();
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
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				TupleTypeDescriptor.stringTupleType());
		specialObjects[73] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				InstanceMetaDescriptor.topMeta());
		specialObjects[74] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
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
			TupleDescriptor.from(),
			TOP.o());
		specialObjects[91] = FunctionTypeDescriptor.create(
			TupleDescriptor.from(),
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
				TupleDescriptor.from(),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.singleInt(2),
					TupleDescriptor.from(),
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
				TupleDescriptor.from(),
				TupleTypeDescriptor.mostGeneralType());
		specialObjects[101] = IntegerRangeTypeDescriptor.nybbles();
		specialObjects[102] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
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
				TupleDescriptor.from(),
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
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				InstanceMetaDescriptor.anyMeta());
		specialObjects[118] =
			IntegerRangeTypeDescriptor.create(
				IntegerDescriptor.zero(),
				true,
				InfinityDescriptor.positiveInfinity(),
				true);
		specialObjects[119] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.singleInt(2),
					TupleDescriptor.from(ATOM.o()),
					InstanceMetaDescriptor.anyMeta()));
		specialObjects[120] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.singleInt(2),
					TupleDescriptor.from(ATOM.o()),
					ANY.o()));
		specialObjects[121] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				PARSE_NODE.mostGeneralType());
		specialObjects[122] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				ARGUMENT_NODE.mostGeneralType());
		specialObjects[123] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				DECLARATION_NODE.mostGeneralType());
		specialObjects[124] =
			VariableTypeDescriptor.fromReadAndWriteTypes(
				TOP.o(),
				EXPRESSION_NODE.create(BottomTypeDescriptor.bottom()));
		specialObjects[125] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				EXPRESSION_NODE.create(ANY.o()));
		specialObjects[126] = EXPRESSION_NODE.create(ANY.o());
		specialObjects[127] =
			FunctionTypeDescriptor.create(
				TupleDescriptor.from(
					PojoTypeDescriptor.forClass(Throwable.class)),
				BottomTypeDescriptor.bottom());


		// Declare all special atoms
		specialAtoms[0] = AtomDescriptor.trueObject();
		specialAtoms[1] = AtomDescriptor.falseObject();
		specialAtoms[2] = PojoTypeDescriptor.selfAtom();
		specialAtoms[3] = ObjectTypeDescriptor.exceptionAtom();
		specialAtoms[4] = MethodDescriptor.vmCrashAtom();
		specialAtoms[5] = MethodDescriptor.vmFunctionApplyAtom();
		specialAtoms[6] = MethodDescriptor.vmDefinerAtom();
		specialAtoms[7] = MethodDescriptor.vmPublishAtomsAtom();
		specialAtoms[8] = AtomDescriptor.moduleHeaderSectionAtom();
		specialAtoms[9] = AtomDescriptor.moduleBodySectionAtom();

		assert specialAtomsSet == null;
		specialAtomsSet = new HashSet<AvailObject>(specialAtomsList);
		specialAtomsSet.remove(null);
		specialAtomsSet = Collections.unmodifiableSet(specialAtomsSet);
		for (final AvailObject object : specialObjects)
		{
			if (object != null && object.isAtom())
			{
				assert specialAtomsSet.contains(object);
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
	 * @param methodName A {@linkplain AtomDescriptor method name}.
	 * @return An {@linkplain MethodDescriptor method}.
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
	 * NullDescriptor the null object}.
	 *
	 * @param selector
	 *            A {@linkplain AtomDescriptor selector}.
	 * @return
	 *            A {@linkplain MethodDescriptor method}
	 *            or {@linkplain NullDescriptor the null object}.
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
			return NullDescriptor.nullObject();
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
				method.removeImplementation(implementation);
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
	 * A mapping from {@linkplain AtomDescriptor keys} to {@link
	 * RandomAccessFile}s open for reading.
	 */
	private final Map<AvailObject, RandomAccessFile> openReadableFiles =
		new WeakHashMap<AvailObject, RandomAccessFile>();

	/**
	 * A mapping from {@linkplain AtomDescriptor keys} to {@link
	 * RandomAccessFile}s open for writing.
	 */
	private final Map<AvailObject, RandomAccessFile> openWritableFiles =
		new WeakHashMap<AvailObject, RandomAccessFile>();

	/**
	 * Answer the open readable {@linkplain RandomAccessFile file} associated
	 * with the specified {@linkplain AtomDescriptor handle}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 * @return The open {@linkplain RandomAccessFile file} associated with the
	 *         {@linkplain AtomDescriptor atom}, or {@code null} if no such
	 *         association exists.
	 */
	public @Nullable RandomAccessFile getReadableFile (final AvailObject handle)
	{
		assert handle.isAtom();
		return openReadableFiles.get(handle);
	}

	/**
	 * Associate the specified {@linkplain AtomDescriptor handle} with
	 * the open readable {@linkplain RandomAccessFile file}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 * @param file An open {@linkplain RandomAccessFile file}.
	 */
	public void putReadableFile (
		final AvailObject handle,
		final RandomAccessFile file)
	{
		assert handle.isAtom();
		openReadableFiles.put(handle, file);
	}

	/**
	 * Remove the association between the specified {@linkplain
	 * AtomDescriptor handle} and its open readable {@linkplain
	 * RandomAccessFile file}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 */
	public void forgetReadableFile (final AvailObject handle)
	{
		assert handle.isAtom();
		openReadableFiles.remove(handle);
	}

	/**
	 * Answer the open writable {@linkplain RandomAccessFile file} associated
	 * with the specified {@linkplain AtomDescriptor handle}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 * @return The open {@linkplain RandomAccessFile file} associated with the
	 *         {@linkplain AtomDescriptor atom}, or {@code null} if no such
	 *         association exists.
	 */
	public @Nullable RandomAccessFile getWritableFile (final AvailObject handle)
	{
		assert handle.isAtom();
		return openWritableFiles.get(handle);
	}

	/**
	 * Associate the specified {@linkplain AtomDescriptor handle} with
	 * the open writable {@linkplain RandomAccessFile file}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 * @param file An open {@linkplain RandomAccessFile file}.
	 */
	public void putWritableFile (
		final AvailObject handle,
		final RandomAccessFile file)
	{
		assert handle.isAtom();
		openWritableFiles.put(handle, file);
	}

	/**
	 * Remove the association between the specified {@linkplain
	 * AtomDescriptor handle} and its open writable {@linkplain
	 * RandomAccessFile file}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 */
	public void forgetWritableFile (final AvailObject handle)
	{
		assert handle.isAtom();
		openWritableFiles.remove(handle);
	}

	/**
	 * Answer the open {@linkplain RandomAccessFile file} associated with the
	 * specified {@linkplain AtomDescriptor handle}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 * @return The open {@linkplain RandomAccessFile file} associated with the
	 *         {@linkplain AtomDescriptor atom}, or {@code null} if no such
	 *         association exists.
	 */
	public @Nullable RandomAccessFile getOpenFile (final AvailObject handle)
	{
		assert handle.isAtom();
		final RandomAccessFile file = openReadableFiles.get(handle);
		if (file != null)
		{
			return file;
		}

		return openWritableFiles.get(handle);
	}

	/**
	 * Destroy all data structures used by this {@code AvailRuntime}.  Also
	 * disassociate it from the current {@link Thread}'s local storage.
	 */
	public void destroy ()
	{
		current.set(null);
		clearWellKnownObjects();
		moduleNameResolver = null;
		modules = null;
		methods = null;
		rootBundleTree = null;
		openReadableFiles.clear();
		openWritableFiles.clear();
	}
}
