/**
 * CompiledCodeDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import com.avail.AvailTask;
import com.avail.annotations.AvailMethod;
import com.avail.annotations.EnumField;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.InnerAccess;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.L1Disassembler;
import com.avail.interpreter.levelOne.L1OperandType;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.Strings;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.descriptor.AtomDescriptor.createSpecialAtom;
import static com.avail.descriptor.AtomWithPropertiesDescriptor
	.createAtomWithProperties;
import static com.avail.descriptor.CompiledCodeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.CompiledCodeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.CompiledCodeTypeDescriptor
	.compiledCodeTypeForFunctionType;
import static com.avail.descriptor.CompiledCodeTypeDescriptor
	.mostGeneralCompiledCodeType;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerDescriptor.zero;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tupleFromList;
import static com.avail.descriptor.TypeDescriptor.Types.MODULE;
import static java.lang.String.format;

/**
 * A {@linkplain CompiledCodeDescriptor compiled code} object is created
 * whenever a block is compiled. It contains instructions and literals that
 * encode how to perform the block. In particular, its main feature is a
 * {@linkplain NybbleTupleDescriptor tuple} of nybbles that encode {@linkplain
 * L1Operation operations} and their {@linkplain L1OperandType operands}.
 *
 * <p>
 * To refer to specific {@linkplain AvailObject Avail objects} from these
 * instructions, some operands act as indices into the {@linkplain
 * ObjectSlots#LITERAL_AT_ literals} that are stored within the compiled code
 * object. There are also slots that keep track of the number of arguments that
 * this code expects to be invoked with, and the number of slots to allocate for
 * {@linkplain ContinuationDescriptor continuations} that represent invocations
 * of this code.
 * </p>
 *
 * <p>
 * Compiled code objects can not be directly invoked, as the block they
 * represent may refer to "outer" variables. When this is the case, a
 * {@linkplain FunctionDescriptor function (closure)} must be constructed at
 * runtime to hold this information. When no such outer variables are needed,
 * the function itself can be constructed at compile time and stored as a
 * literal.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class CompiledCodeDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * A compound field consisting of the hash value, computed at
		 * construction time, and the {@link Primitive} number or zero.
		 */
		HASH_AND_PRIMITIVE,

		/**
		 * A compound field consisting of the number of outer variables/values
		 * to be captured by my {@linkplain FunctionDescriptor functions}, the
		 * variable number of slots that should be allocated for a {@linkplain
		 * ContinuationDescriptor continuation} running this code, the number of
		 * local variables, and the number of arguments.
		 */
		NUM_OUTERS_FRAMES_LOCALS_AND_ARGS;

		/**
		 * The hash value of this {@linkplain CompiledCodeDescriptor compiled
		 * code object}. It is computed at construction time.
		 */
		@HideFieldInDebugger
		static final BitField HASH = bitField(HASH_AND_PRIMITIVE, 0, 32);

		/**
		 * The primitive number or zero. This does not correspond with the
		 * {@linkplain Enum#ordinal() ordinal} of the {@link Primitive}
		 * enumeration, but rather the value of its {@linkplain
		 * Primitive#primitiveNumber primitiveNumber}. If a primitive is
		 * specified then an attempt is made to executed it before running any
		 * nybblecodes. The nybblecode instructions are only run if the
		 * primitive was unsuccessful.
		 */
		@EnumField(
			describedBy=Primitive.class,
			lookupMethodName="byPrimitiveNumberOrNull")
		static final BitField PRIMITIVE = bitField(HASH_AND_PRIMITIVE, 32, 32);

		/**
		 * The number of outer variables that must captured by my {@linkplain
		 * FunctionDescriptor functions}.
		 */
		static final BitField NUM_OUTERS = bitField(
			NUM_OUTERS_FRAMES_LOCALS_AND_ARGS, 48, 16);

		/**
		 * The number of {@linkplain
		 * ContinuationDescriptor.ObjectSlots#FRAME_AT_ frame slots} to allocate
		 * for continuations running this code.
		 */
		static final BitField FRAME_SLOTS = bitField(
			NUM_OUTERS_FRAMES_LOCALS_AND_ARGS, 32, 16);

		/**
		 * The number of local variables and constants declared in this code,
		 * not counting the arguments. Also don't count locals in nested code.
		 */
		static final BitField NUM_LOCALS = bitField(
			NUM_OUTERS_FRAMES_LOCALS_AND_ARGS, 16, 16);

		/**
		 * The number of {@link DeclarationKind#ARGUMENT arguments} that this
		 * code expects.
		 */
		static final BitField NUM_ARGS = bitField(
			NUM_OUTERS_FRAMES_LOCALS_AND_ARGS, 0, 16);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain NybbleTupleDescriptor tuple of nybbles} that describe
		 * what {@linkplain L1Operation level one operations} to perform.
		 */
		@HideFieldInDebugger
		NYBBLES,

		/**
		 * The {@linkplain FunctionTypeDescriptor type} of any function
		 * based on this {@linkplain CompiledCodeDescriptor compiled code}.
		 */
		FUNCTION_TYPE,

		/**
		 * The {@linkplain L2Chunk level two chunk} that should be invoked
		 * whenever this code is started. The chunk may no longer be {@link
		 * L2Chunk#isValid() valid}, in which case the {@linkplain
		 * L2Chunk#unoptimizedChunk() default chunk} will be substituted until
		 * the next reoptimization.
		 */
//		@HideFieldJustForPrinting
		STARTING_CHUNK,

		/**
		 * An {@link AtomDescriptor atom} unique to this {@linkplain
		 * CompiledCodeDescriptor compiled code}, in which to record information
		 * such as the file and line number of source code.
		 */
		@HideFieldInDebugger
		PROPERTY_ATOM,

		/**
		 * A {@link RawPojoDescriptor raw pojo} holding an {@link
		 * InvocationStatistic} that tracks invocations of this code.
		 */
		@HideFieldInDebugger
		INVOCATION_STATISTIC,

		/**
		 * The literal objects that are referred to numerically by some of the
		 * operands of {@linkplain L1Operation level one instructions} encoded
		 * in the {@linkplain #NYBBLES nybblecodes}.
		 */
		@HideFieldInDebugger
		LITERAL_AT_
	}

	/**
	 * A helper class that tracks invocation information in {@link
	 * AtomicLong}s.  Since these require neither locks nor complete memory
	 * barriers, they're ideally suited for this purpose.
	 */
	@InnerAccess
	static class InvocationStatistic
	{
		/**
		 * An {@link AtomicLong} holding a count of the total number of times
		 * this code has been invoked.  This statistic can be useful during
		 * optimization.
		 */
		final AtomicLong totalInvocations = new AtomicLong(0);

		/**
		 * An {@link AtomicLong} that indicates how many more invocations can
		 * take place before the corresponding {@link L2Chunk} should be
		 * re-optimized.
		 */
		final AtomicLong countdownToReoptimize = new AtomicLong(0);

		/**
		 * A boolean indicating whether the current {@linkplain
		 * CompiledCodeDescriptor compiled code object} has been run during the
		 * current code coverage session.
		 */
		@InnerAccess volatile boolean hasRun = false;
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == STARTING_CHUNK;
	}

	/**
	 * Used for describing logical aspects of the code in the Eclipse debugger.
	 */
	private enum FakeSlots
	implements ObjectSlotsEnum
	{
		/** Used for showing the types of local variables. */
		LOCAL_TYPE_,

		/** Used for showing the types of captured variables and constants. */
		OUTER_TYPE_,

		/** Used for showing an L1 disassembly of the code. */
		L1_DISASSEMBLY,

		/** Used for showing a tuple of all literals of the code. They're
		 * grouped together under one literal to reduce the amount of
		 * spurious (excruciatingly slow) computation done in the Eclipse
		 * debugger.  Keeping this entry collapsed avoids having to compute the
		 * print representations of the literals.
		 */
		ALL_LITERALS;
	}

	/**
	 * {@inheritDoc}
	 *
	 * Show the types of local variables and outer variables.
	 */
	@Override
	AvailObjectFieldHelper[] o_DescribeForDebugger (
		final AvailObject object)
	{
		final List<AvailObjectFieldHelper> fields = new ArrayList<>();
		fields.addAll(Arrays.asList(super.o_DescribeForDebugger(object)));
		for (int i = 1, end = object.numOuters(); i <= end; i++)
		{
			fields.add(
				new AvailObjectFieldHelper(
					object,
					FakeSlots.OUTER_TYPE_,
					i,
					object.outerTypeAt(i)));
		}
		for (int i = 1, end = object.numLocals(); i <= end; i++)
		{
			fields.add(
				new AvailObjectFieldHelper(
					object,
					FakeSlots.LOCAL_TYPE_,
					i,
					object.localTypeAt(i)));
		}
		final StringBuilder disassembled = new StringBuilder();
		object.printOnAvoidingIndent(
			disassembled, new IdentityHashMap<>(), 0);
		final String[] content = disassembled.toString().split("\n");
		fields.add(
			new AvailObjectFieldHelper(
				object,
				FakeSlots.L1_DISASSEMBLY,
				-1,
				content));
		final List<AvailObject> allLiterals = new ArrayList<>();
		for (int i = 1; i <= object.numLiterals(); i++)
		{
			allLiterals.add(object.literalAt(i));
		}
		fields.add(
			new AvailObjectFieldHelper(
				object,
				FakeSlots.ALL_LITERALS,
				-1,
				tupleFromList(allLiterals)));
		return fields.toArray(new AvailObjectFieldHelper[fields.size()]);
	}

	/**
	 * Answer the {@link InvocationStatistic} associated with the
	 * specified {@link CompiledCodeDescriptor raw function}.
	 *
	 * @param object
	 *        The {@link A_RawFunction} from which to extract the invocation
	 *        statistics helper.
	 * @return The code's invocation statistics.
	 */
	static InvocationStatistic getInvocationStatistic (
		final AvailObject object)
	{
		final AvailObject pojo = object.slot(INVOCATION_STATISTIC);
		return (InvocationStatistic) pojo.javaObject();
	}

	/** The set of all active {@link CompiledCodeDescriptor raw functions}. */
	@InnerAccess static final Set<A_RawFunction> activeRawFunctions =
		Collections.synchronizedSet(
			Collections.newSetFromMap(
				new WeakHashMap<A_RawFunction, Boolean>()));

	/**
	 * Reset the code coverage details of all {@link CompiledCodeDescriptor raw
	 * functions} by discarding their L2 optimized chunks and clearing their
	 * flags. When complete, resume the supplied {@link Continuation0
	 * continuation}.
	 *
	 * @param resume
	 *        The {@link Continuation0 continuation to be executed upon
	 *        completion}.
	 */
	@AvailMethod
	public static void resetCodeCoverageDetailsThen (final Continuation0 resume)
	{
		currentRuntime().whenLevelOneSafeDo(
			new AvailTask(FiberDescriptor.commandPriority)
			{
				@Override
				public void value ()
				{
					L2Chunk.invalidationLock.lock();
					try
					{
						// Loop over each instance, setting the touched flag to
						// false and discarding optimizations.
						for (final A_RawFunction function : activeRawFunctions)
						{
							final AvailObject object = (AvailObject) function;
							getInvocationStatistic(object).hasRun = false;
							if (!function.module().equalsNil())
							{
								object.startingChunk().invalidate();
							}
						}
						currentRuntime().whenLevelOneUnsafeDo(
							new AvailTask(FiberDescriptor.commandPriority)
							{
								@Override
								public void value ()
								{
									resume.value();
								}
							});
					}
					finally
					{
						L2Chunk.invalidationLock.unlock();
					}
				}
			});
	}

	/**
	 * Contains and presents the details of this raw function pertinent to code
	 * coverage reporting.
	 *
	 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
	 */
	public static class CodeCoverageReport
	implements Comparable<CodeCoverageReport>
	{
		/**
		 * Whether this raw function has been run during this code coverage
		 * session.
		 */
		private final boolean hasRun;

		/**
		 * Whether this raw function has been translated during this code
		 * coverage session.
		 */
		private final boolean isTranslated;

		/** The starting line number of this raw function. */
		public final int startingLineNumber;

		/** The module this raw function appears in. */
		public final String moduleName;

		/** The method this raw function appears in. */
		public final String methodName;

		/**
		 * Construct a new {@link CodeCoverageReport}.
		 *
		 * @param hasRun
		 *        Whether this raw function has been run during this code
		 *        coverage session.
		 * @param isTranslated
		 *        Whether this raw function has been translated during this code
		 *        coverage session.
		 * @param startingLineNumber
		 *        The starting line number of this raw function.
		 * @param moduleName
		 *        The module this raw function appears in.
		 * @param methodName
		 *        The method this raw function appears in.
		 */
		@InnerAccess CodeCoverageReport (
			final boolean hasRun,
			final boolean isTranslated,
			final int startingLineNumber,
			final String moduleName,
			final String methodName)
		{
			this.hasRun = hasRun;
			this.isTranslated = isTranslated;
			this.startingLineNumber = startingLineNumber;
			this.moduleName = moduleName;
			this.methodName = methodName;
		}

		@Override
		public int compareTo (final @Nullable CodeCoverageReport o)
		{
			assert o != null;

			final int moduleComp = this.moduleName.compareTo(o.moduleName);
			if (moduleComp != 0)
			{
				return moduleComp;
			}
			final int lineComp =
				Integer.compare(this.startingLineNumber, o.startingLineNumber);
			if (lineComp != 0)
			{
				return lineComp;
			}
			return this.methodName.compareTo(o.methodName);
		}

		@Override
		public String toString ()
		{
			return format(
				"%c %c  m: %s,  l: %d,  f: %s",
				hasRun ? 'r' : ' ',
				isTranslated ? 't' : ' ',
				moduleName,
				startingLineNumber,
				methodName);
		}
	}

	/**
	 * Collect and return the code coverage reports for all the raw functions.
	 *
	 * @param resume
	 *        The continuation to pass the return value to.
	 */
	public static void codeCoverageReportsThen (
		final Continuation1NotNull<List<CodeCoverageReport>> resume)
	{
		currentRuntime().whenLevelOneSafeDo(
			new AvailTask(FiberDescriptor.commandPriority)
			{
				@Override
				public void value ()
				{
					final List<CodeCoverageReport> reports =
						new ArrayList<>(activeRawFunctions.size());

					// Loop over each instance, creating its report object.
					for (final A_RawFunction function : activeRawFunctions)
					{
						final A_Module module = function.module();
						if (!module.equalsNil())
						{
							final CodeCoverageReport report =
								new CodeCoverageReport(
									getInvocationStatistic(
										(AvailObject) function).hasRun,
									function.startingChunk()
										!= L2Chunk.unoptimizedChunk(),
									function.startingLineNumber(),
									module.moduleName().asNativeString(),
									function.methodName().asNativeString());
							if (!reports.contains(report))
							{
								reports.add(report);
							}
						}
					}
					currentRuntime().whenLevelOneUnsafeDo(
						new AvailTask(FiberDescriptor.commandPriority)
						{
							@Override
							public void value ()
							{
								resume.value(reports);
							}
						});
				}
			});
	}

	@Override @AvailMethod
	void o_CountdownToReoptimize (final AvailObject object, final int value)
	{
		getInvocationStatistic(object).countdownToReoptimize.set(value);
	}

	@Override @AvailMethod
	long o_TotalInvocations (final AvailObject object)
	{
		return getInvocationStatistic(object).totalInvocations.get();
	}

	@Override @AvailMethod
	AvailObject o_LiteralAt (final AvailObject object, final int subscript)
	{
		return object.slot(LITERAL_AT_, subscript);
	}

	@Override @AvailMethod
	A_Type o_FunctionType (final AvailObject object)
	{
		return object.slot(FUNCTION_TYPE);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(HASH);
	}

	@Override @AvailMethod
	void o_DecrementCountdownToReoptimize (
		final AvailObject object,
		final Continuation0 continuation)
	{
		final InvocationStatistic invocationStatistic =
			getInvocationStatistic(object);
		final long newCount =
			invocationStatistic.countdownToReoptimize.decrementAndGet();
		if (newCount <= 0)
		{
			// Either we just decremented past zero or someone else did.  Race
			// for a lock on the object.  First one through reoptimizes while
			// the others wait.
			synchronized (object)
			{
				// If the counter is still negative then either (1) it hasn't
				// been reset yet by reoptimization, or (2) it has been
				// reoptimized, the counter was reset to something positive,
				// but it has already been decremented back below zero.
				// Either way, reoptimize now.
				if (invocationStatistic.countdownToReoptimize.get() <= 0)
				{
					continuation.value();
				}
			}
		}
	}

	@Override @AvailMethod
	A_Tuple o_Nybbles (final AvailObject object)
	{
		return object.slot(NYBBLES);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsCompiledCode(object);
	}

	@Override @AvailMethod
	boolean o_EqualsCompiledCode (
		final AvailObject object,
		final A_RawFunction aCompiledCode)
	{
		// Compiled code now (2012.06.14) compares by identity because it may
		// have to track references to the source code.
		return object.sameAddressAs(aCompiledCode);
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return compiledCodeTypeForFunctionType(object.functionType());
	}

	@Override @AvailMethod
	A_Type o_LocalTypeAt (final AvailObject object, final int index)
	{
		assert 1 <= index && index <= object.numLocals();
		return object.literalAt(
			object.numLiterals()
			- object.numLocals()
			+ index);
	}

	@Override @AvailMethod
	A_Type o_OuterTypeAt (final AvailObject object, final int index)
	{
		assert 1 <= index && index <= object.numOuters();
		return object.literalAt(
			object.numLiterals()
			- object.numLocals()
			- object.numOuters()
			+ index);
	}

	@Override @AvailMethod
	void o_SetStartingChunkAndReoptimizationCountdown (
		final AvailObject object,
		final L2Chunk chunk,
		final long countdown)
	{
		final AtomicLong atomicCounter =
			getInvocationStatistic(object).countdownToReoptimize;
		if (isShared())
		{
			synchronized (object)
			{
				object.setSlot(STARTING_CHUNK, chunk.chunkPojo);
			}
			// Must be outside the synchronized section to ensure the write of
			// the new chunk is committed before the counter reset is visible.
			atomicCounter.set(countdown);
		}
		else
		{
			object.setSlot(STARTING_CHUNK, chunk.chunkPojo);
			atomicCounter.set(countdown);
		}
	}

	@Override @AvailMethod
	int o_MaxStackDepth (final AvailObject object)
	{
		return
			object.numArgsAndLocalsAndStack()
			- object.numArgs()
			- object.numLocals();
	}

	@Override @AvailMethod
	int o_NumArgs (final AvailObject object)
	{
		return object.slot(NUM_ARGS);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Answer the number of arguments + locals + stack slots to reserve in my
	 * continuations.
	 * </p>
	 */
	@Override @AvailMethod
	int o_NumArgsAndLocalsAndStack (final AvailObject object)
	{
		return object.slot(FRAME_SLOTS);
	}

	@Override @AvailMethod
	int o_NumLiterals (final AvailObject object)
	{
		return object.variableObjectSlotsCount();
	}

	@Override @AvailMethod
	int o_NumLocals (final AvailObject object)
	{
		return object.slot(NUM_LOCALS);
	}

	@Override @AvailMethod
	int o_NumOuters (final AvailObject object)
	{
		return object.slot(NUM_OUTERS);
	}

	@Override @AvailMethod
	@Nullable Primitive o_Primitive (final AvailObject object)
	{
		return Primitive.byNumber(object.slot(PRIMITIVE));
	}

	@Override @AvailMethod
	int o_PrimitiveNumber (final AvailObject object)
	{
		// Answer the primitive number I should try before falling back on
		// the Avail code.  Zero indicates not-a-primitive.
		return object.slot(PRIMITIVE);
	}

	@Override @AvailMethod
	L2Chunk o_StartingChunk (final AvailObject object)
	{
		final AvailObject pojo = object.mutableSlot(STARTING_CHUNK);
		return (L2Chunk)pojo.javaObject();
	}

	@Override @AvailMethod
	void o_TallyInvocation (final AvailObject object)
	{
		final InvocationStatistic invocationStatistic =
			getInvocationStatistic(object);
		invocationStatistic.totalInvocations.incrementAndGet();
		invocationStatistic.hasRun = true;
	}

	/**
	 * Answer the starting line number for this block of code.
	 */
	@Override @AvailMethod
	int o_StartingLineNumber (final AvailObject object)
	{
		final A_Atom properties = object.mutableSlot(PROPERTY_ATOM);
		final A_Number lineInteger =
			properties.getAtomProperty(lineNumberKeyAtom());
		return lineInteger.equalsNil()
			? 0
			: lineInteger.extractInt();
	}

	@Override @AvailMethod
	A_Phrase o_OriginatingPhrase (final AvailObject object)
	{
		final A_Atom properties = object.mutableSlot(PROPERTY_ATOM);
		return properties.getAtomProperty(originatingPhraseKeyAtom());
	}

	/**
	 * Answer the module in which this code occurs.
	 */
	@Override @AvailMethod
	A_Module o_Module (final AvailObject object)
	{
		final A_Atom properties = object.mutableSlot(PROPERTY_ATOM);
		return properties.issuingModule();
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation(final AvailObject object)
	{
		return SerializerOperation.COMPILED_CODE;
	}

	@Override @AvailMethod
	void o_SetMethodName (
		final AvailObject object,
		final A_String methodName)
	{
		assert methodName.isString();
		methodName.makeImmutable();
		final A_Atom propertyAtom = object.mutableSlot(PROPERTY_ATOM);
		propertyAtom.setAtomProperty(methodNameKeyAtom(), methodName);
		// Now scan all sub-blocks. Some literals will be functions and some
		// will be compiled code objects.
		int counter = 1;
		for (int i = 1, limit = object.numLiterals(); i <= limit; i++)
		{
			final AvailObject literal = object.literalAt(i);
			final A_RawFunction subCode;
			if (literal.isFunction())
			{
				subCode = literal.code();
			}
			else if (literal.isInstanceOf(mostGeneralCompiledCodeType()))
			{
				subCode = literal;
			}
			else
			{
				subCode = null;
			}
			if (subCode != null)
			{
				final String suffix = format("[%d]", counter);
				counter++;
				final A_Tuple newName = methodName.concatenateWith(
					stringFrom(suffix), true);
				subCode.setMethodName((A_String)newName);
			}
		}
	}

	@Override @AvailMethod
	A_String o_MethodName (final AvailObject object)
	{
		final A_Atom propertyAtom = object.mutableSlot(PROPERTY_ATOM);
		final A_String methodName =
			propertyAtom.getAtomProperty(methodNameKeyAtom());
		if (methodName.equalsNil())
		{
			return stringFrom("Unknown function");
		}
		return methodName;
	}

	@Override
	String o_NameForDebugger (final AvailObject object)
	{
		return super.o_NameForDebugger(object) + ": " + object.methodName();
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return false;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("function implementation");
		writer.write("outers");
		writer.write(object.slot(NUM_OUTERS));
		writer.write("arguments");
		writer.write(object.slot(NUM_ARGS));
		writer.write("locals");
		writer.write(object.slot(NUM_LOCALS));
		writer.write("maximum stack depth");
		writer.write(object.slot(FRAME_SLOTS));
		writer.write("nybbles");
		object.slot(NYBBLES).writeTo(writer);
		writer.write("function type");
		object.slot(FUNCTION_TYPE).writeTo(writer);
		writer.write("method");
		object.methodName().writeTo(writer);
		final A_Module module = object.module();
		if (!module.equalsNil())
		{
			writer.write("module");
			object.module().moduleName().writeTo(writer);
		}
		writer.write("starting line number");
		writer.write(object.startingLineNumber());
		writer.write("literals");
		writer.startArray();
		for (int i = 1, limit = object.variableObjectSlotsCount();
			i <= limit;
			i++)
		{
			A_BasicObject literal = object.slot(LITERAL_AT_, i);
			if (literal.equalsNil())
			{
				literal = zero();
			}
			literal.writeSummaryTo(writer);
		}
		writer.endArray();
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("function implementation");
		writer.write("outers");
		writer.write(object.slot(NUM_OUTERS));
		writer.write("arguments");
		writer.write(object.slot(NUM_ARGS));
		writer.write("locals");
		writer.write(object.slot(NUM_LOCALS));
		writer.write("maximum stack depth");
		writer.write(object.slot(FRAME_SLOTS));
		writer.write("nybbles");
		object.slot(NYBBLES).writeTo(writer);
		writer.write("function type");
		object.slot(FUNCTION_TYPE).writeSummaryTo(writer);
		writer.write("method");
		object.methodName().writeTo(writer);
		writer.write("module");
		object.module().moduleName().writeTo(writer);
		writer.write("starting line number");
		writer.write(object.startingLineNumber());
		writer.write("literals");
		writer.startArray();
		for (int i = 1, limit = object.variableObjectSlotsCount();
			i <= limit;
			i++)
		{
			A_BasicObject literal = object.slot(LITERAL_AT_, i);
			if (literal.equalsNil())
			{
				literal = zero();
			}
			literal.writeSummaryTo(writer);
		}
		writer.endArray();
		writer.endObject();
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		super.printObjectOnAvoidingIndent(
			object,
			builder,
			recursionMap,
			indent);
		Strings.newlineTab(builder, indent);
		builder.append("Nybblecodes:\n");
		L1Disassembler.disassemble(
			object,
			builder,
			recursionMap,
			indent + 1);
	}

	/**
	 * Create a new compiled code object with the given properties.
	 *
	 * @param nybbles The nybblecodes.
	 * @param locals The number of local variables.
	 * @param stack The maximum stack depth.
	 * @param functionType The type that the code's functions will have.
	 * @param primitive Which primitive to invoke, or zero.
	 * @param literals A tuple of literals.
	 * @param localTypes A tuple of types of local variables.
	 * @param outerTypes A tuple of types of outer (captured) variables.
	 * @param module The module in which the code occurs, or nil.
	 * @param lineNumber The module line number on which this code starts.
	 * @return The new compiled code object.
	 */
	public static AvailObject newCompiledCode (
		final A_Tuple nybbles,
		final int locals,
		final int stack,
		final A_Type functionType,
		final @Nullable Primitive primitive,
		final A_Tuple literals,
		final A_Tuple localTypes,
		final A_Tuple outerTypes,
		final A_Module module,
		final int lineNumber,
		final A_Phrase originatingPhrase)
	{
		if (primitive != null)
		{
			// Sanity check for primitive blocks.  Use this to hunt incorrectly
			// specified primitive signatures.
			final boolean canHaveCode = primitive.canHaveNybblecodes();
			assert canHaveCode == (nybbles.tupleSize() > 0);
			final A_Type restrictionSignature =
				primitive.blockTypeRestriction();
			assert restrictionSignature.isSubtypeOf(functionType);
		}
		else
		{
			assert nybbles.tupleSize() > 0;
		}

		assert localTypes.tupleSize() == locals;
		final A_Type argCounts = functionType.argsTupleType().sizeRange();
		final int numArgs = argCounts.lowerBound().extractInt();
		assert argCounts.upperBound().extractInt() == numArgs;
		final int literalsSize = literals.tupleSize();
		final int outersSize = outerTypes.tupleSize();

		assert 0 <= numArgs && numArgs <= 0xFFFF;
		assert 0 <= locals && locals <= 0xFFFF;
		final int slotCount = numArgs + locals + stack;
		assert 0 <= slotCount && slotCount <= 0xFFFF;
		assert 0 <= outersSize && outersSize <= 0xFFFF;

		assert module.equalsNil() || module.isInstanceOf(MODULE.o());
		assert lineNumber >= 0;

		final AvailObject code = mutable.create(
			literalsSize + outersSize + locals);

		final InvocationStatistic statistic = new InvocationStatistic();
		statistic.countdownToReoptimize.set(L2Chunk.countdownForNewCode());
		final AvailObject statisticPojo = identityPojo(statistic);

		code.setSlot(NUM_LOCALS, locals);
		code.setSlot(NUM_ARGS, numArgs);
		code.setSlot(FRAME_SLOTS, slotCount);
		code.setSlot(NUM_OUTERS, outersSize);
		code.setSlot(
			PRIMITIVE, primitive == null ? 0 : primitive.primitiveNumber);
		code.setSlot(NYBBLES, nybbles.makeShared());
		code.setSlot(FUNCTION_TYPE, functionType.makeShared());
		code.setSlot(PROPERTY_ATOM, nil());
		code.setSlot(STARTING_CHUNK, L2Chunk.unoptimizedChunk().chunkPojo);
		code.setSlot(INVOCATION_STATISTIC, statisticPojo);

		// Fill in the literals.
		int dest;
		for (dest = 1; dest <= literalsSize; dest++)
		{
			code.setSlot(
				LITERAL_AT_, dest, literals.tupleAt(dest).makeShared());
		}
		for (int i = 1; i <= outersSize; i++)
		{
			code.setSlot(
				LITERAL_AT_, dest++, outerTypes.tupleAt(i).makeShared());
		}
		for (int i = 1; i <= locals; i++)
		{
			code.setSlot(
				LITERAL_AT_, dest++, localTypes.tupleAt(i).makeShared());
		}
		assert dest == literalsSize + outersSize + locals + 1;

		final A_Atom propertyAtom = createAtomWithProperties(
			emptyTuple(), module);
		propertyAtom.setAtomProperty(lineNumberKeyAtom(), fromInt(lineNumber));
		if (!originatingPhrase.equalsNil())
		{
			propertyAtom.setAtomProperty(
				originatingPhraseKeyAtom(), originatingPhrase);
		}
		code.setSlot(PROPERTY_ATOM, propertyAtom.makeShared());
		final int hash = propertyAtom.hash() ^ -0x3087B215;
		code.setSlot(HASH, hash);
		code.makeShared();

		// Add the newborn raw function to the weak set being used for code
		// coverage tracking.
		activeRawFunctions.add(code);

		return code;
	}

	/**
	 * Construct a new {@link CompiledCodeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private CompiledCodeDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.RAW_FUNCTION_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link CompiledCodeDescriptor}. */
	private static final CompiledCodeDescriptor mutable =
		new CompiledCodeDescriptor(Mutability.MUTABLE);

	@Override
	CompiledCodeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link CompiledCodeDescriptor}. */
	private static final CompiledCodeDescriptor immutable =
		new CompiledCodeDescriptor(Mutability.IMMUTABLE);

	@Override
	CompiledCodeDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link CompiledCodeDescriptor}. */
	private static final CompiledCodeDescriptor shared =
		new CompiledCodeDescriptor(Mutability.SHARED);

	@Override
	CompiledCodeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * The key used to track a method name associated with the code. This
	 * name is presented in stack traces.
	 */
	private static final A_Atom methodNameKeyAtom =
		createSpecialAtom("code method name key");

	/**
	 * Answer the key used to track a method name associated with the code. This
	 * name is presented in stack traces.
	 *
	 * @return A special atom.
	 */
	public static A_Atom methodNameKeyAtom ()
	{
		return methodNameKeyAtom;
	}

	/**
	 * The key used to track the first line number within the module on which
	 * this code occurs.
	 */
	private static final A_Atom lineNumberKeyAtom =
		createSpecialAtom("code line number key");

	/**
	 * Answer the key used to track the first line number within the module on
	 * which this code occurs.
	 *
	 * @return A special atom.
	 */
	public static A_Atom lineNumberKeyAtom ()
	{
		return lineNumberKeyAtom;
	}

	/**
	 * The key used to track the {@link ParseNodeDescriptor phrase} that a raw
	 * function was created from.
	 */
	private static final A_Atom originatingPhraseKeyAtom =
		createSpecialAtom("originating phrase key");

	/**
	 * Answer the key used to track the {@link ParseNodeDescriptor phrase} that
	 * a raw function was created from.
	 *
	 * @return A special atom.
	 */
	public static A_Atom originatingPhraseKeyAtom ()
	{
		return originatingPhraseKeyAtom;
	}
}
