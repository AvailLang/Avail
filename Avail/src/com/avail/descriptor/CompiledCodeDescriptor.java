/**
 * descriptor/CompiledCodeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

import static com.avail.descriptor.AvailObject.CanAllocateObjects;
import java.util.List;
import com.avail.annotations.*;
import com.avail.compiler.node.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.interpreter.*;
import com.avail.interpreter.levelOne.*;

/**
 * A {@link CompiledCodeDescriptor compiled code} object is created whenever a
 * block is compiled.  It contains instructions and literals that encode how to
 * perform the block.  In particular, its main feature is a {@linkplain
 * NybbleTupleDescriptor tuple} of nybbles that encode {@linkplain L1Instruction
 * level-one instructions}, which consist of {@linkplain L1Operation operations}
 * and their {@linkplain L1OperandType operands}.
 *
 * <p>
 * To refer to specific {@linkplain AvailObject Avail objects} from these
 * instructions, some operands act as indices into the {@linkplain
 * ObjectSlots#LITERAL_AT_ literals} that are stored within the compiled code
 * object.  There are also slots that keep track of the number of arguments that
 * this code expects to be invoked with, and the number of slots to allocate for
 * {@linkplain ContinuationDescriptor continuations} that represent invocations
 * of this code.
 * </p>
 *
 * <p>
 * Compiled code objects can not be directly invoked, as the block they
 * represent may refer to "outer" variables.  When this is the case, a
 * {@linkplain FunctionDescriptor function (closure)} must be constructed at
 * runtime to hold this information.  When no such outer variables are needed,
 * the function itself can be constructed at compile time and stored as a
 * literal.
 * </p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class CompiledCodeDescriptor
extends Descriptor
{

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		/**
		 * The hash value of this {@linkplain CompiledCodeDescriptor compiled
		 * code object}.  It is computed at construction time.
		 */
		HASH,

		/**
		 * A compound field consisting of the number of outer variables/values
		 * to be captured by my {@linkplain FunctionDescriptor functions}, and
		 * the variable number of slots that should be allocated for a
		 * {@linkplain ContinuationDescriptor continuation} running this code.
		 */
		@BitFields(describedBy=HiNumOutersLowFrameSlots.class)
		HI_NUM_OUTERS_LOW_FRAME_SLOTS,

		/**
		 * A compound field consisting of the number of locals variables and the
		 * number of arguments.
		 */
		@BitFields(describedBy=HiNumLocalsLowNumArgs.class)
		HI_NUM_LOCALS_LOW_NUM_ARGS,

		/**
		 * The primitive number or zero.  This does not correspond with the
		 * {@linkplain Enum#ordinal() ordinal} of the {@link Primitive}
		 * enumeration, but rather the value of its {@linkplain
		 * Primitive#primitiveNumber primitiveNumber}.  If a primitive is
		 * specified then an attempt is made to executed it before running any
		 * nybblecodes.  The nybblecode instructions are only run if the
		 * primitive was unsuccessful.
		 */
		PRIMITIVE_NUMBER,

		/**
		 * The remaining number of times to invoke this code before performing
		 * a reoptimization attempt.
		 */
		INVOCATION_COUNT
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@linkplain NybbleTupleDescriptor tuple of nybbles} that describe
		 * what {@linkplain L1Operation level one operations} to perform.
		 */
		NYBBLES,

		/**
		 * The {@linkplain FunctionTypeDescriptor type} of any function
		 * based on this {@linkplain CompiledCodeDescriptor compiled code}.
		 */
		FUNCTION_TYPE,

		/**
		 * The {@linkplain L2ChunkDescriptor level two chunk} to executed on
		 * behalf of this code.  The premise is that a level two chunk can be
		 * optimized and executed in place of a naive level one interpretation.
		 */
		/**
		 * The {@linkplain L2ChunkDescriptor level two chunk} that should be
		 * invoked whenever this code is started.  The chunk may no longer be
		 * {@linkplain L2ChunkDescriptor.NumObjectsAndFlags#VALID valid}, in
		 * which case the {@linkplain L2ChunkDescriptor#unoptimizedChunk()
		 * default chunk} will be substituted until the next reoptimization.
		 */
		STARTING_CHUNK,

		/**
		 * The literal objects that are referred to numerically by some of the
		 * operands of {@linkplain L1Instruction level one instructions} encoded
		 * in the {@linkplain #NYBBLES nybblecodes}.  This also includes
		 */
		LITERAL_AT_
	}

	/**
	 * Bit fields for the {@link IntegerSlots#HI_NUM_OUTERS_LOW_FRAME_SLOTS}
	 * integer slot.
	 */
	public static class HiNumOutersLowFrameSlots
	{
		/**
		 * The number of outer variables that must captured by my {@linkplain
		 * FunctionDescriptor functions}.
		 */
		@BitField(shift=16, bits=16)
		static final BitField NUM_OUTERS =
			bitField(HiNumOutersLowFrameSlots.class, "NUM_OUTERS");

		/**
		 * The number of {@linkplain
		 * ContinuationDescriptor.ObjectSlots#FRAME_AT_ frame slots} to allocate
		 * for continuations running this code.
		 */
		@BitField(shift=0, bits=16)
		static final BitField FRAME_SLOTS =
			bitField(HiNumOutersLowFrameSlots.class, "FRAME_SLOTS");
	}

	/**
	 * Bit fields for the {@link IntegerSlots#HI_NUM_LOCALS_LOW_NUM_ARGS}
	 * integer slot.
	 */
	public static class HiNumLocalsLowNumArgs
	{
		/**
		 * The number of local variables and constants declared in this code,
		 * not counting the arguments.  Also don't count locals in nested code.
		 */
		@BitField(shift=16, bits=16)
		static final BitField NUM_LOCALS =
			bitField(HiNumLocalsLowNumArgs.class, "NUM_LOCALS");

		/**
		 * The number of {@link DeclarationKind#ARGUMENT arguments} that this
		 * code expects.
		 */
		@BitField(shift=0, bits=16)
		static final BitField NUM_ARGS =
			bitField(HiNumLocalsLowNumArgs.class, "NUM_ARGS");
	}


	@Override
	public void o_Hash (
		final @NotNull AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.HASH, value);
	}

	@Override
	public void o_InvocationCount (
		final @NotNull AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.INVOCATION_COUNT, value);
	}

	@Override
	public @NotNull AvailObject o_LiteralAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.objectSlotAt(ObjectSlots.LITERAL_AT_, subscript);
	}

	@Override
	public @NotNull AvailObject o_FunctionType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.FUNCTION_TYPE);
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HASH);
	}

	@Override
	public int o_InvocationCount (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.INVOCATION_COUNT);
	}

	@Override
	public @NotNull AvailObject o_Nybbles (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NYBBLES);
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull Enum<?> e)
	{
		return e == ObjectSlots.STARTING_CHUNK
			|| e == IntegerSlots.INVOCATION_COUNT;
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsCompiledCode(object);
	}

	@Override
	public boolean o_EqualsCompiledCode (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCode)
	{
		if (object.sameAddressAs(aCompiledCode))
		{
			return true;
		}

		if (object.hash() != aCompiledCode.hash()
			|| object.numLiterals() != aCompiledCode.numLiterals()
			|| !object.nybbles().equals(aCompiledCode.nybbles())
			|| object.primitiveNumber() != aCompiledCode.primitiveNumber()
			|| object.numArgsAndLocalsAndStack()
				!= aCompiledCode.numArgsAndLocalsAndStack()
			|| object.numLocals() != aCompiledCode.numLocals()
			|| object.numArgs() != aCompiledCode.numArgs()
			|| !object.functionType().equals(aCompiledCode.functionType()))
		{
			return false;
		}
		for (int i = 1, end = object.numLiterals(); i <= end; i++)
		{
			if (!object.literalAt(i).equals(aCompiledCode.literalAt(i)))
			{
				return false;
			}
		}
		// They're equal, but occupy disjoint storage.  Replace one with an
		// indirection to the other to reduce storage costs and the need for
		// subsequent detailed comparisons.
		object.becomeIndirectionTo(aCompiledCode);
		// Now that there are at least two references to it.
		aCompiledCode.makeImmutable();
		return true;
	}

	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return CompiledCodeTypeDescriptor.forFunctionType(
			object.functionType());
	}

	@Override
	public boolean o_ContainsBlock (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunction)
	{
		// Answer true if either I am aFunction's code or I contain aFunction or
		// its code.
		if (object.sameAddressAs(aFunction.code().traversed()))
		{
			return true;
		}
		for (int i = 1, end = object.numLiterals(); i <= end; i++)
		{
			if (object.literalAt(i).containsBlock(aFunction))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public @NotNull AvailObject o_LocalTypeAt (
		final @NotNull AvailObject object,
		final int index)
	{
		assert 1 <= index && index <= object.numLocals();
		return object.literalAt(
			object.numLiterals()
			- object.numLocals()
			+ index);
	}

	@Override
	public @NotNull AvailObject o_OuterTypeAt (
		final @NotNull AvailObject object,
		final int index)
	{
		assert 1 <= index && index <= object.numOuters();
		return object.literalAt(
			object.numLiterals()
			- object.numLocals()
			- object.numOuters()
			+ index);
	}

	@Override
	public void o_StartingChunk (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(
			ObjectSlots.STARTING_CHUNK,
			value);
	}

	@Override
	public int o_MaxStackDepth (
		final @NotNull AvailObject object)
	{
		return
			object.numArgsAndLocalsAndStack()
			- object.numArgs()
			- object.numLocals();
	}

	@Override
	public int o_NumArgs (
		final @NotNull AvailObject object)
	{
		return (short)object.bitSlot(
			IntegerSlots.HI_NUM_LOCALS_LOW_NUM_ARGS,
			HiNumLocalsLowNumArgs.NUM_ARGS);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Answer the number of arguments + locals + stack slots to reserve in my
	 * continuations.
	 * </p>
	 */
	@Override
	public int o_NumArgsAndLocalsAndStack (
		final @NotNull AvailObject object)
	{
		return object.bitSlot(
			IntegerSlots.HI_NUM_OUTERS_LOW_FRAME_SLOTS,
			HiNumOutersLowFrameSlots.FRAME_SLOTS);
	}

	@Override
	public int o_NumLiterals (
		final @NotNull AvailObject object)
	{
		return object.variableObjectSlotsCount();
	}

	@Override
	public int o_NumLocals (
		final @NotNull AvailObject object)
	{
		return object.bitSlot(
			IntegerSlots.HI_NUM_LOCALS_LOW_NUM_ARGS,
			HiNumLocalsLowNumArgs.NUM_LOCALS);
	}

	@Override
	public int o_NumOuters (
		final @NotNull AvailObject object)
	{
		return object.bitSlot(
			IntegerSlots.HI_NUM_OUTERS_LOW_FRAME_SLOTS,
			HiNumOutersLowFrameSlots.NUM_OUTERS);
	}

	@Override
	public int o_PrimitiveNumber (
		final @NotNull AvailObject object)
	{
		//  Answer the primitive number I should try before falling back on
		//  the Avail code.  Zero indicates not-a-primitive.
		return object.integerSlot(IntegerSlots.PRIMITIVE_NUMBER);
	}

	@Override
	public @NotNull AvailObject o_StartingChunk (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.STARTING_CHUNK);
	}

	@Override
	public void o_PostFault (
		final @NotNull AvailObject object)
	{
		//  The object was just scanned, and its pointers converted into valid ToSpace pointers.
		//  Do any follow-up activities specific to the kind of object it is.
		//
		//  In particular, a CompiledCode object needs to bring its L2Chunk object into ToSpace and
		//  link it into the ring of saved chunks.  Chunks that are no longer accessed can be reclaimed,
		//  or at least their entries can be reclaimed, at flip time.

		final AvailObject chunk = object.startingChunk();
		if (chunk.isValid())
		{
			chunk.isSaved(true);
		}
		else
		{
			object.startingChunk(
				L2ChunkDescriptor.unoptimizedChunk());
			object.invocationCount(
				L2ChunkDescriptor.countdownForInvalidatedCode());
		}
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
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
		new L1Disassembler().disassemble(
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
	 * @return The new compiled code object.
	 */
	public static AvailObject create (
		final @NotNull AvailObject nybbles,
		final int locals,
		final int stack,
		final @NotNull AvailObject functionType,
		final int primitive,
		final @NotNull AvailObject literals,
		final @NotNull AvailObject localTypes,
		final @NotNull AvailObject outerTypes)
	{
		if (primitive != 0)
		{
			// Sanity check for primitive blocks.  Use this to hunt incorrectly
			// specified primitive signatures.
			assert primitive == (primitive & 0xFFFF);
			final Primitive prim = Primitive.byPrimitiveNumber(primitive);
			final AvailObject restrictionSignature =
				prim.blockTypeRestriction();
			assert restrictionSignature.isSubtypeOf(functionType);
		}

		assert localTypes.tupleSize() == locals;
		final AvailObject argCounts = functionType.argsTupleType().sizeRange();
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

		final AvailObject code = mutable().create(
			literalsSize + outersSize + locals);

		CanAllocateObjects(false);

		code.bitSlotPut(
			IntegerSlots.HI_NUM_LOCALS_LOW_NUM_ARGS,
			HiNumLocalsLowNumArgs.NUM_LOCALS,
			locals);
		code.bitSlotPut(
			IntegerSlots.HI_NUM_LOCALS_LOW_NUM_ARGS,
			HiNumLocalsLowNumArgs.NUM_ARGS,
			numArgs);
		code.bitSlotPut(
			IntegerSlots.HI_NUM_OUTERS_LOW_FRAME_SLOTS,
			HiNumOutersLowFrameSlots.FRAME_SLOTS,
			slotCount);
		code.bitSlotPut(
			IntegerSlots.HI_NUM_OUTERS_LOW_FRAME_SLOTS,
			HiNumOutersLowFrameSlots.NUM_OUTERS,
			outersSize);
		code.integerSlotPut(IntegerSlots.PRIMITIVE_NUMBER, primitive);
		code.objectSlotPut(ObjectSlots.NYBBLES, nybbles);
		code.objectSlotPut(ObjectSlots.FUNCTION_TYPE, functionType);
		code.startingChunk(L2ChunkDescriptor.unoptimizedChunk());
		code.invocationCount(L2ChunkDescriptor.countdownForNewCode());

		// Fill in the literals.
		int dest;
		for (dest = 1; dest <= literalsSize; dest++)
		{
			code.objectSlotAtPut(
				ObjectSlots.LITERAL_AT_,
				dest,
				literals.tupleAt(dest));
		}
		for (int i = 1; i <= outersSize; i++)
		{
			code.objectSlotAtPut(
				ObjectSlots.LITERAL_AT_,
				dest++,
				outerTypes.tupleAt(i));
		}
		for (int i = 1; i <= locals; i++)
		{
			code.objectSlotAtPut(
				ObjectSlots.LITERAL_AT_,
				dest++,
				localTypes.tupleAt(i));
		}
		assert dest == literalsSize + outersSize + locals + 1;

		// Compute the hash.
		int hash = 0x0B085B25 + code.objectSlotsCount() + nybbles.hash()
			^ numArgs * 4127;
		hash += locals * 1237 + stack * 9131 + primitive * 1151;
		hash ^= functionType.hash();
		for (int i = 1; i <= literalsSize; i++)
		{
			hash = hash * 2 + literals.tupleAt(i).hash() ^ 0x052B580B;
		}
		for (int i = 1; i <= outersSize; i++)
		{
			hash = hash * 3 + outerTypes.tupleAt(i).hash() ^ 0x015F5947;
		}
		for (int i = 1; i <= locals; ++ i)
		{
			hash = hash * 5 + localTypes.tupleAt(i).hash() ^ 0x01E37808;
		}
		code.integerSlotPut(IntegerSlots.HASH, hash);
		code.makeImmutable();

		CanAllocateObjects(true);

		return code;
	}

	/**
	 * Construct a new {@link CompiledCodeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected CompiledCodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link CompiledCodeDescriptor}.
	 */
	private final static CompiledCodeDescriptor mutable =
		new CompiledCodeDescriptor(true);

	/**
	 * Answer the mutable {@link CompiledCodeDescriptor}.
	 *
	 * @return The mutable {@link CompiledCodeDescriptor}.
	 */
	public static CompiledCodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link CompiledCodeDescriptor}.
	 */
	private final static CompiledCodeDescriptor immutable =
		new CompiledCodeDescriptor(false);

	/**
	 * Answer the immutable {@link CompiledCodeDescriptor}.
	 *
	 * @return The immutable {@link CompiledCodeDescriptor}.
	 */
	public static CompiledCodeDescriptor immutable ()
	{
		return immutable;
	}
}
