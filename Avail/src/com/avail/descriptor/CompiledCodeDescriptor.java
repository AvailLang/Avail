/**
 * CompiledCodeDescriptor.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static com.avail.descriptor.CompiledCodeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.CompiledCodeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.MODULE;
import com.avail.annotations.*;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.*;

/**
 * A {@linkplain CompiledCodeDescriptor compiled code} object is created
 * whenever a block is compiled. It contains instructions and literals that
 * encode how to perform the block. In particular, its main feature is a
 * {@linkplain NybbleTupleDescriptor tuple} of nybbles that encode {@linkplain
 * L1Instruction level-one instructions}, which consist of {@linkplain
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
public class CompiledCodeDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public static enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The hash value of this {@linkplain CompiledCodeDescriptor compiled
		 * code object}. It is computed at construction time.
		 */
		@HideFieldInDebugger
		HASH,

		/**
		 * A compound field consisting of the number of outer variables/values
		 * to be captured by my {@linkplain FunctionDescriptor functions}, and
		 * the variable number of slots that should be allocated for a
		 * {@linkplain ContinuationDescriptor continuation} running this code.
		 */
		HI_NUM_OUTERS_LOW_FRAME_SLOTS,

		/**
		 * A compound field consisting of the number of locals variables and the
		 * number of arguments.
		 */
		HI_NUM_LOCALS_LOW_NUM_ARGS,

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
		PRIMITIVE_NUMBER;

		/**
		 * The number of outer variables that must captured by my {@linkplain
		 * FunctionDescriptor functions}.
		 */
		static final BitField NUM_OUTERS = bitField(
			HI_NUM_OUTERS_LOW_FRAME_SLOTS,
			16,
			16);

		/**
		 * The number of {@linkplain
		 * ContinuationDescriptor.ObjectSlots#FRAME_AT_ frame slots} to allocate
		 * for continuations running this code.
		 */
		static final BitField FRAME_SLOTS = bitField(
			HI_NUM_OUTERS_LOW_FRAME_SLOTS,
			0,
			16);

		/**
		 * The number of local variables and constants declared in this code,
		 * not counting the arguments. Also don't count locals in nested code.
		 */
		static final BitField NUM_LOCALS = bitField(
			HI_NUM_LOCALS_LOW_NUM_ARGS,
			16,
			16);

		/**
		 * The number of {@link DeclarationKind#ARGUMENT arguments} that this
		 * code expects.
		 */
		static final BitField NUM_ARGS = bitField(
			HI_NUM_LOCALS_LOW_NUM_ARGS,
			0,
			16);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public static enum ObjectSlots
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
		@HideFieldJustForPrinting
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
		 * operands of {@linkplain L1Instruction level one instructions} encoded
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
	@InnerAccess static class InvocationStatistic
	{
		/**
		 * An {@link AtomicLong} holding a count of the total number of times
		 * this code has been invoked.  This statistic can be useful during
		 * optimization.
		 */
		volatile AtomicLong totalInvocations = new AtomicLong(0);

		/**
		 * An {@link AtomicLong} that indicates how many more invocations can
		 * take place before the corresponding {@link L2Chunk} should be
		 * re-optimized.
		 */
		volatile AtomicLong countdownToReoptimize = new AtomicLong(0);
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == STARTING_CHUNK
			|| e == PROPERTY_ATOM;
	}

	/**
	 * Used for describing logical aspects of the code in the Eclipse debugger.
	 */
	public static enum FakeSlots
	implements ObjectSlotsEnum
	{
		/** Used for showing the types of local variables. */
		LOCAL_TYPE_,

		/** Used for showing the types of captured variables and constants. */
		OUTER_TYPE_
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
	final static InvocationStatistic getInvocationStatistic (
		final AvailObject object)
	{
		final AvailObject pojo = object.slot(INVOCATION_STATISTIC);
		return (InvocationStatistic) pojo.javaObject();
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
				// been reset yet by repotimization, or (2) it has been
				// reoptimized, the counter was reset to something positive,
				// but it has already been decremented back below zero.
				// Either way, reoptimize now.
				if (invocationStatistic.countdownToReoptimize.get() < 0)
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
		return CompiledCodeTypeDescriptor.forFunctionType(
			object.functionType());
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
	int o_PrimitiveNumber (final AvailObject object)
	{
		// Answer the primitive number I should try before falling back on
		// the Avail code.  Zero indicates not-a-primitive.
		return object.slot(PRIMITIVE_NUMBER);
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
		getInvocationStatistic(object).totalInvocations.incrementAndGet();
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
			else if (literal.isInstanceOf(
				CompiledCodeTypeDescriptor.mostGeneralType()))
			{
				subCode = literal;
			}
			else
			{
				subCode = null;
			}
			if (subCode != null)
			{
				final String prefix = String.format(
					"[#%d] of ",
					counter);
				counter++;
				final A_Tuple newName =
					StringDescriptor.from(prefix).concatenateWith(
						methodName,
						true);
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
			return StringDescriptor.from("Unknown function");
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
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		super.printObjectOnAvoidingIndent(
			object,
			builder,
			recursionList,
			indent);
		builder.append('\n');
		for (int i = indent; i > 0; i--)
		{
			builder.append('\t');
		}
		builder.append("Nybblecodes:\n");
		L1Disassembler.disassemble(
			object,
			builder,
			recursionList,
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
	 * @param module The module in which the code, or nil.
	 * @param lineNumber The module line number on which this code starts.
	 * @return The new compiled code object.
	 */
	public static AvailObject create (
		final A_Tuple nybbles,
		final int locals,
		final int stack,
		final A_Type functionType,
		final int primitive,
		final A_Tuple literals,
		final A_Tuple localTypes,
		final A_Tuple outerTypes,
		final A_BasicObject module,
		final int lineNumber)
	{
		if (primitive != 0)
		{
			// Sanity check for primitive blocks.  Use this to hunt incorrectly
			// specified primitive signatures.
			assert primitive == (primitive & 0xFFFF);
			final Primitive prim = Primitive.byPrimitiveNumberOrFail(primitive);
			final A_Type restrictionSignature = prim.blockTypeRestriction();
			assert restrictionSignature.isSubtypeOf(functionType);
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
		assert 0 <= primitive && primitive <= 0xFFFF;

		assert module.equalsNil() || module.isInstanceOf(MODULE.o());
		assert lineNumber >= 0;

		final AvailObject code = mutable.create(
			literalsSize + outersSize + locals);

		final InvocationStatistic statistic =
			new InvocationStatistic();
		statistic.countdownToReoptimize.set(L2Chunk.countdownForNewCode());
		final AvailObject statisticPojo =
			RawPojoDescriptor.identityWrap(statistic);

		code.setSlot(NUM_LOCALS, locals);
		code.setSlot(NUM_ARGS, numArgs);
		code.setSlot(FRAME_SLOTS, slotCount);
		code.setSlot(NUM_OUTERS, outersSize);
		code.setSlot(PRIMITIVE_NUMBER, primitive);
		code.setSlot(NYBBLES, nybbles);
		code.setSlot(FUNCTION_TYPE, functionType);
		code.setSlot(PROPERTY_ATOM, NilDescriptor.nil());
		code.setSlot(STARTING_CHUNK, L2Chunk.unoptimizedChunk().chunkPojo);
		code.setSlot(INVOCATION_STATISTIC, statisticPojo);

		// Fill in the literals.
		int dest;
		for (dest = 1; dest <= literalsSize; dest++)
		{
			code.setSlot(LITERAL_AT_, dest, literals.tupleAt(dest));
		}
		for (int i = 1; i <= outersSize; i++)
		{
			code.setSlot(LITERAL_AT_, dest++, outerTypes.tupleAt(i));
		}
		for (int i = 1; i <= locals; i++)
		{
			code.setSlot(LITERAL_AT_, dest++, localTypes.tupleAt(i));
		}
		assert dest == literalsSize + outersSize + locals + 1;

		final A_Atom propertyAtom = AtomWithPropertiesDescriptor.create(
			TupleDescriptor.empty(),
			module);
		code.setSlot(PROPERTY_ATOM, propertyAtom);
		propertyAtom.setAtomProperty(
			lineNumberKeyAtom(),
			IntegerDescriptor.fromInt(lineNumber));
		final int hash = propertyAtom.hash() ^ -0x3087B215;
		code.setSlot(HASH, hash);
		code.makeImmutable();
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
		super(mutability, ObjectSlots.class, IntegerSlots.class);
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
		AtomDescriptor.createSpecialAtom("code method name key");

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
		AtomDescriptor.createSpecialAtom("code line number key");

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
}
