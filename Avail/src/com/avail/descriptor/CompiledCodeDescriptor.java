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
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelOne.L1Disassembler;

public class CompiledCodeDescriptor extends Descriptor
{

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		HASH,
		HI_PRIM_LOW_FRAME_SLOTS,
		HI_NUM_LOCALS_LOW_NUM_ARGS,
		HI_STARTING_CHUNK_LOW_NUM_OUTERS,
		INVOCATION_COUNT
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		NYBBLES,
		CLOSURE_TYPE,
		LITERAL_AT_
	}


	// GENERATED accessors

	/**
	 * Setter for field closureType.
	 */
	@Override
	public void o_ClosureType (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CLOSURE_TYPE, value);
	}

	/**
	 * Setter for field hash.
	 */
	@Override
	public void o_Hash (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.HASH, value);
	}

	/**
	 * Setter for field hiNumLocalsLowNumArgs.
	 */
	@Override
	public void o_HiNumLocalsLowNumArgs (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.HI_NUM_LOCALS_LOW_NUM_ARGS, value);
	}

	/**
	 * Setter for field hiPrimitiveLowNumArgsAndLocalsAndStack.
	 */
	@Override
	public void o_HiPrimitiveLowNumArgsAndLocalsAndStack (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.HI_PRIM_LOW_FRAME_SLOTS, value);
	}

	/**
	 * Setter for field hiStartingChunkIndexLowNumOuters.
	 */
	@Override
	public void o_HiStartingChunkIndexLowNumOuters (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.HI_STARTING_CHUNK_LOW_NUM_OUTERS, value);
	}

	/**
	 * Setter for field invocationCount.
	 */
	@Override
	public void o_InvocationCount (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.INVOCATION_COUNT, value);
	}

	@Override
	public AvailObject o_LiteralAt (
			final AvailObject object,
			final int subscript)
	{
		return object.objectSlotAt(ObjectSlots.LITERAL_AT_, subscript);
	}

	@Override
	public void o_LiteralAtPut (
			final AvailObject object,
			final int subscript,
			final AvailObject value)
	{
		object.objectSlotAtPut(ObjectSlots.LITERAL_AT_, subscript, value);
	}

	/**
	 * Setter for field nybbles.
	 */
	@Override
	public void o_Nybbles (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.NYBBLES, value);
	}

	/**
	 * Getter for field closureType.
	 */
	@Override
	public AvailObject o_ClosureType (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CLOSURE_TYPE);
	}

	/**
	 * Getter for field hash.
	 */
	@Override
	public int o_Hash (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HASH);
	}

	/**
	 * Getter for field hiNumLocalsLowNumArgs.
	 */
	@Override
	public int o_HiNumLocalsLowNumArgs (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HI_NUM_LOCALS_LOW_NUM_ARGS);
	}

	/**
	 * Getter for field hiPrimitiveLowNumArgsAndLocalsAndStack.
	 */
	@Override
	public int o_HiPrimitiveLowNumArgsAndLocalsAndStack (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HI_PRIM_LOW_FRAME_SLOTS);
	}

	/**
	 * Getter for field hiStartingChunkIndexLowNumOuters.
	 */
	@Override
	public int o_HiStartingChunkIndexLowNumOuters (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HI_STARTING_CHUNK_LOW_NUM_OUTERS);
	}

	/**
	 * Getter for field invocationCount.
	 */
	@Override
	public int o_InvocationCount (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.INVOCATION_COUNT);
	}

	/**
	 * Getter for field nybbles.
	 */
	@Override
	public AvailObject o_Nybbles (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NYBBLES);
	}



	@Override
	public boolean allowsImmutableToMutableReferenceInField (
			final Enum<?> e)
	{
		if (e == IntegerSlots.HI_STARTING_CHUNK_LOW_NUM_OUTERS)
		{
			return true;
		}
		if (e == IntegerSlots.INVOCATION_COUNT)
		{
			return true;
		}
		return false;
	}



	// operations

	@Override
	public boolean o_Equals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsCompiledCode(object);
	}

	@Override
	public boolean o_EqualsCompiledCode (
			final AvailObject object,
			final AvailObject aCompiledCode)
	{
		if (object.sameAddressAs(aCompiledCode))
		{
			return true;
		}
		if (object.hash() != aCompiledCode.hash())
		{
			return false;
		}
		if (object.numLiterals() != aCompiledCode.numLiterals())
		{
			return false;
		}
		if (!object.nybbles().equals(aCompiledCode.nybbles()))
		{
			return false;
		}
		if (object.hiPrimitiveLowNumArgsAndLocalsAndStack() != aCompiledCode.hiPrimitiveLowNumArgsAndLocalsAndStack())
		{
			return false;
		}
		if (object.hiNumLocalsLowNumArgs() != aCompiledCode.hiNumLocalsLowNumArgs())
		{
			return false;
		}
		if (!object.closureType().equals(aCompiledCode.closureType()))
		{
			return false;
		}
		for (int i = 1, _end1 = object.numLiterals(); i <= _end1; i++)
		{
			if (!object.literalAt(i).equals(aCompiledCode.literalAt(i)))
			{
				return false;
			}
		}
		//  They're equal (but occupy disjoint storage).  Replace one with an indirection to the other
		//  to reduce storage costs and the frequency of detailed comparisons.
		object.becomeIndirectionTo(aCompiledCode);
		aCompiledCode.makeImmutable();
		//  Now that there are at least two references to it
		return true;
	}

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.compiledCode.object();
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.compiledCode.object();
	}



	// operations-closure

	@Override
	public boolean o_ContainsBlock (
			final AvailObject object,
			final AvailObject aClosure)
	{
		//  Answer true if either I am aClosure's code or I contain aClosure or its code.

		if (object.sameAddressAs(aClosure.code().traversed()))
		{
			return true;
		}
		for (int i = 1, _end1 = object.numLiterals(); i <= _end1; i++)
		{
			if (object.literalAt(i).containsBlock(aClosure))
			{
				return true;
			}
		}
		return false;
	}



	// operations-code

	@Override
	public void o_ArgsLocalsStackOutersPrimitive (
			final AvailObject object,
			final int args,
			final int locals,
			final int stack,
			final int outers,
			final int primitive)
	{
		//  Note - also zeroes the startingChunkIndex.

		assert 0 <= args && args <= 0xFFFF;
		assert 0 <= locals && locals <= 0xFFFF;
		final int slotCount = args + locals + stack;
		assert 0 <= slotCount && slotCount <= 0xFFFF;
		assert 0 <= outers && outers <= 0xFFFF;
		assert 0 <= primitive && primitive <= 0xFFFF;
		object.hiNumLocalsLowNumArgs(((locals << 16) + args));
		object.hiPrimitiveLowNumArgsAndLocalsAndStack(((primitive << 16) + slotCount));
		object.hiStartingChunkIndexLowNumOuters(outers);
	}

	@Override
	public AvailObject o_LocalTypeAt (
			final AvailObject object,
			final int index)
	{
		assert 1 <= index && index <= object.numLocals();
		return object.literalAt((object.numLiterals() - object.numLocals() + index));
	}

	@Override
	public AvailObject o_OuterTypeAt (
			final AvailObject object,
			final int index)
	{
		assert 1 <= index && index <= object.numOuters();
		return object.literalAt((object.numLiterals() - object.numLocals() - object.numOuters() + index));
	}

	@Override
	public void o_OuterTypesLocalTypes (
			final AvailObject object,
			final AvailObject tupleOfOuterTypes,
			final AvailObject tupleOfLocalContainerTypes)
	{
		//  The literal frame has the literals used by the code, followed by the outer types,
		//  followed by the local variable types.

		assert tupleOfOuterTypes.tupleSize() == object.numOuters() : "Wrong number of outer types.";
		assert tupleOfLocalContainerTypes.tupleSize() == object.numLocals() : "Wrong number of local types.";
		int src = 1;
		for (
				int
					dest = object.numLiterals() - object.numLocals() - object.numOuters() + 1,
					_end1 = object.numLiterals() - object.numLocals();
				dest <= _end1;
				dest++)
		{
			object.literalAtPut(dest, tupleOfOuterTypes.tupleAt(src));
			src++;
		}
		src = 1;
		for (
				int
					dest = object.numLiterals() - object.numLocals() + 1,
					_end2 = object.numLiterals();
				dest <= _end2;
				dest++)
		{
			object.literalAtPut(dest, tupleOfLocalContainerTypes.tupleAt(src));
			src++;
		}
	}

	@Override
	public void o_StartingChunkIndex (
			final AvailObject object,
			final int value)
	{
		object.hiStartingChunkIndexLowNumOuters(
			(object.hiStartingChunkIndexLowNumOuters() & 0xFFFF) + (value << 16));
	}

	@Override
	public short o_MaxStackDepth (
			final AvailObject object)
	{
		return (short)(object.numArgsAndLocalsAndStack() - object.numArgs() - object.numLocals());
	}

	@Override
	public short o_NumArgs (
			final AvailObject object)
	{
		return (short)(object.hiNumLocalsLowNumArgs() & 0xFFFF);
	}

	@Override
	public short o_NumArgsAndLocalsAndStack (
			final AvailObject object)
	{
		//  Answer the number of args + locals + stack slots to reserve in my continuations.

		return (short)(object.hiPrimitiveLowNumArgsAndLocalsAndStack() & 0xFFFF);
	}

	@Override
	public short o_NumLiterals (
			final AvailObject object)
	{
		//  Answer how many literals I have.

		return (short)(object.objectSlotsCount() - numberOfFixedObjectSlots);
	}

	@Override
	public short o_NumLocals (
			final AvailObject object)
	{
		return (short)(object.hiNumLocalsLowNumArgs() >>> 16);
	}

	@Override
	public short o_NumOuters (
			final AvailObject object)
	{
		return (short)(object.hiStartingChunkIndexLowNumOuters() & 0xFFFF);
	}

	@Override
	public short o_PrimitiveNumber (
			final AvailObject object)
	{
		//  Answer the primitive number I should try before falling back on
		//  the Avail code.  Zero indicates not-a-primitive.

		return (short)(object.hiPrimitiveLowNumArgsAndLocalsAndStack() >>> 16);
	}

	@Override
	public int o_StartingChunkIndex (
			final AvailObject object)
	{
		return object.hiStartingChunkIndexLowNumOuters() >>> 16;
	}



	// operations-faulting

	@Override
	public void o_PostFault (
			final AvailObject object)
	{
		//  The object was just scanned, and its pointers converted into valid ToSpace pointers.
		//  Do any follow-up activities specific to the kind of object it is.
		//
		//  In particular, a CompiledCode object needs to bring its L2Chunk object into ToSpace and
		//  link it into the ring of saved chunks.  Chunks that are no longer accessed can be reclaimed,
		//  or at least their entries can be reclaimed, at flip time.

		final AvailObject chunk = L2ChunkDescriptor.chunkFromId(object.startingChunkIndex());
		if (chunk.isValid())
		{
			chunk.isSaved(true);
		}
		else
		{
			object.startingChunkIndex(L2ChunkDescriptor.indexOfUnoptimizedChunk());
			object.invocationCount(L2ChunkDescriptor.countdownForInvalidatedCode());
		}
	}


	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
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
	 * @param numArgs The number of arguments.
	 * @param locals The number of local variables.
	 * @param stack The maximum stack depth.
	 * @param closureType The type that the code's closures will have.
	 * @param primitive Which primitive to invoke, or zero.
	 * @param literals A tuple of literals.
	 * @param localTypes A tuple of types of local variables.
	 * @param outerTypes A tuple of types of outer (captured) variables.
	 * @return The new compiled code object.
	 */
	public static AvailObject create (
			final AvailObject nybbles,
			final int numArgs,
			final int locals,
			final int stack,
			final AvailObject closureType,
			final int primitive,
			final AvailObject literals,
			final AvailObject localTypes,
			final AvailObject outerTypes)
	{
		assert localTypes.tupleSize() == locals;
		assert closureType.numArgs() == numArgs;
		final int literalsSize = literals.tupleSize();
		final int outersSize = outerTypes.tupleSize();
		final AvailObject code = mutable().create(
			literalsSize + outersSize + locals);

		CanAllocateObjects(false);
		code.nybbles(nybbles);
		code.argsLocalsStackOutersPrimitive(
			numArgs, locals, stack, outersSize, primitive);
		code.closureType(closureType);
		code.startingChunkIndex(L2ChunkDescriptor.indexOfUnoptimizedChunk());
		code.invocationCount(L2ChunkDescriptor.countdownForNewCode());
		int dest = 1;
		for (; dest <= literalsSize; dest++)
		{
			code.literalAtPut(dest, literals.tupleAt(dest));
		}
		for (int i = 1; i <= outersSize; i++, dest++)
		{
			code.literalAtPut(dest, outerTypes.tupleAt(i));
		}
		for (int i = 1; i <= locals; i++, dest++)
		{
			code.literalAtPut(dest, localTypes.tupleAt(i));
		}
		assert dest == literalsSize + outersSize + locals + 1;
		int hash = 0x0B085B25 + code.objectSlotsCount() + nybbles.hash()
			^ numArgs * 4127;
		hash += locals * 1237 + stack * 9131 + primitive * 1151;
		hash ^= closureType.hash();
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
		code.hash(hash);
		code.makeImmutable();
		CanAllocateObjects(true);

		return code;
	};

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
	private final static CompiledCodeDescriptor mutable = new CompiledCodeDescriptor(true);

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
	private final static CompiledCodeDescriptor immutable = new CompiledCodeDescriptor(false);

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
