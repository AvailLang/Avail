/**
 * descriptor/ContinuationDescriptor.java
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

import static com.avail.descriptor.AvailObject.error;
import java.util.List;

public class ContinuationDescriptor extends Descriptor
{

	public enum IntegerSlots
	{
		PC,
		STACK_POINTER,
		HI_LEVEL_TWO_CHUNK_LOW_OFFSET
	}

	public enum ObjectSlots
	{
		CALLER,
		CLOSURE,
		FRAME_AT_
	}


	/**
	 * Setter for field caller.
	 */
	@Override
	public void o_Caller (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CALLER, value);
	}

	/**
	 * Setter for field closure.
	 */
	@Override
	public void o_Closure (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CLOSURE, value);
	}

	/**
	 * Setter for field hiLevelTwoChunkLowOffset.
	 */
	@Override
	public void o_HiLevelTwoChunkLowOffset (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.HI_LEVEL_TWO_CHUNK_LOW_OFFSET, value);
	}

	@Override
	public AvailObject o_LocalOrArgOrStackAt (
			final AvailObject object,
			final int subscript)
	{
		return object.objectSlotAt(ObjectSlots.FRAME_AT_, subscript);
	}

	@Override
	public void o_LocalOrArgOrStackAtPut (
			final AvailObject object,
			final int subscript,
			final AvailObject value)
	{
		object.objectSlotAtPut(
			ObjectSlots.FRAME_AT_,
			subscript,
			value);
	}

	/**
	 * Setter for field pc.
	 */
	@Override
	public void o_Pc (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.PC, value);
	}

	/**
	 * Setter for field stackp.
	 */
	@Override
	public void o_Stackp (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.STACK_POINTER, value);
	}

	/**
	 * Getter for field caller.
	 */
	@Override
	public AvailObject o_Caller (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CALLER);
	}

	/**
	 * Getter for field closure.
	 */
	@Override
	public AvailObject o_Closure (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CLOSURE);
	}

	/**
	 * Getter for field hiLevelTwoChunkLowOffset.
	 */
	@Override
	public int o_HiLevelTwoChunkLowOffset (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HI_LEVEL_TWO_CHUNK_LOW_OFFSET);
	}

	/**
	 * Getter for field pc.
	 */
	@Override
	public int o_Pc (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.PC);
	}

	/**
	 * Getter for field stackp.
	 */
	@Override
	public int o_Stackp (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.STACK_POINTER);
	}



	@Override
	public boolean o_Equals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsContinuation(object);
	}

	@Override
	public boolean o_EqualsContinuation (
			final AvailObject object,
			final AvailObject aContinuation)
	{
		if (object.sameAddressAs(aContinuation))
		{
			return true;
		}
		if (!object.caller().equals(aContinuation.caller()))
		{
			return false;
		}
		if (!object.closure().equals(aContinuation.closure()))
		{
			return false;
		}
		if (object.pc() != aContinuation.pc())
		{
			return false;
		}
		if (object.stackp() != aContinuation.stackp())
		{
			return false;
		}
		for (int i = object.numLocalsOrArgsOrStack(); i >= 1; i--)
		{
			if (!object.localOrArgOrStackAt(i)
					.equals(aContinuation.localOrArgOrStackAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		return ContinuationTypeDescriptor.continuationTypeForClosureType(
			object.closure().type());
	}

	@Override
	public int o_Hash (
			final AvailObject object)
	{
		int h = 0x593599A;
		h ^= object.caller().hash();
		h = h + object.closure().hash() + object.pc() * object.stackp();
		for (int i = object.numLocalsOrArgsOrStack(); i >= 1; i--)
		{
			h = h * 23 + 0x221C9 ^ object.localOrArgOrStackAt(i).hash();
		}
		return h;
	}

	@Override
	public boolean o_IsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		if (!object.closure().isHashAvailable())
		{
			return false;
		}
		if (!object.caller().isHashAvailable())
		{
			return false;
		}
		for (int i = 1, _end1 = object.numLocalsOrArgsOrStack(); i <= _end1; i++)
		{
			if (!object.localOrArgOrStackAt(i).isHashAvailable())
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		//  Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}



	// operations-continuations

	@Override
	public void o_LevelTwoChunkIndexOffset (
			final AvailObject object,
			final int index,
			final int offset)
	{
		//  Set my chunk index and offset.

		object.hiLevelTwoChunkLowOffset((index * 0x10000 + offset));
	}

	/**
	 * Read from the stack at the given subscript, which is one-relative and
	 * based on just the stack area.
	 */
	@Override
	public AvailObject o_StackAt (
			final AvailObject object,
			final int subscript)
	{
		return object.objectSlotAt(
			ObjectSlots.FRAME_AT_,
			subscript);
	}

	/**
	 * Write to the stack at the given subscript, which is one-relative and
	 * based on just the stack area.
	 */
	@Override
	public void o_StackAtPut (
			final AvailObject object,
			final int subscript,
			final AvailObject anObject)
	{
		object.objectSlotAtPut(
			ObjectSlots.FRAME_AT_,
			subscript,
			anObject);
	}

	@Override
	public AvailObject o_EnsureMutable (
			final AvailObject object)
	{
		//  If immutable, copy the object as mutable, otherwise answer the original mutable.

		return isMutable ? object : object.copyAsMutableContinuation();
	}

	@Override
	public int o_LevelTwoChunkIndex (
			final AvailObject object)
	{
		//  Answer the chunk index (without the offset).

		return object.hiLevelTwoChunkLowOffset() >>> 16;
	}

	@Override
	public int o_LevelTwoOffset (
			final AvailObject object)
	{
		//  Answer the wordcode offset into the chunk.

		return object.hiLevelTwoChunkLowOffset() & 0xFFFF;
	}

	@Override
	public int o_NumLocalsOrArgsOrStack (
			final AvailObject object)
	{
		//  Answer the number of slots allocated for locals, arguments, and stack entries.

		return object.objectSlotsCount() - numberOfFixedObjectSlots;
	}



	// operations-faulting

	@Override
	public void o_PostFault (
			final AvailObject object)
	{
		//  The object was just scanned, and its pointers converted into valid ToSpace pointers.
		//  Do any follow-up activities specific to the kind of object it is.
		//
		//  In particular, a Continuation object needs to bring its L2Chunk object into ToSpace and
		//  link it into the ring of saved chunks.  Chunks that are no longer accessed can be reclaimed,
		//  or at least their entries can be reclaimed, at flip time.

		final AvailObject chunk = L2ChunkDescriptor.chunkFromId(object.levelTwoChunkIndex());
		if (chunk.isValid())
		{
			chunk.isSaved(true);
		}
		else
		{
			object.levelTwoChunkIndexOffset(L2ChunkDescriptor.indexOfUnoptimizedChunk(), L2ChunkDescriptor.offsetToContinueUnoptimizedChunk());
		}
	}



	// private-copying

	@Override
	public AvailObject o_CopyAsMutableContinuation (
			final AvailObject object)
	{
		//  Answer a fresh mutable copy of the given continuation object.

		if (isMutable)
		{
			object.makeSubobjectsImmutable();
		}
		final AvailObject result = AvailObject.newIndexedDescriptor((object.objectSlotsCount() - numberOfFixedObjectSlots), ContinuationDescriptor.mutableDescriptor());
		assert result.objectSlotsCount() == object.objectSlotsCount();
		result.caller(object.caller());
		result.closure(object.closure());
		result.pc(object.pc());
		result.stackp(object.stackp());
		result.hiLevelTwoChunkLowOffset(object.hiLevelTwoChunkLowOffset());
		for (int i = 1, _end1 = object.numLocalsOrArgsOrStack(); i <= _end1; i++)
		{
			result.localOrArgOrStackAtPut(i, object.localOrArgOrStackAt(i));
		}
		return result;
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
			final Enum<?> e)
	{
		if (e == IntegerSlots.HI_LEVEL_TWO_CHUNK_LOW_OFFSET)
		{
			return true;
		}
		return false;
	}

	/**
	 * Create a new continuation with the given data.  The continuation should
	 * represent the state upon entering the new context - i.e., set the pc to
	 * the first instruction (skipping the primitive indicator if necessary),
	 * clear the stack, and set up all local variables.
	 *
	 * @param closure The closure being invoked.
	 * @param caller The calling continuation.
	 * @param startingChunkIndex The index of the level two chunk to invoke.
	 * @param args The List of arguments
	 * @return The new continuation.
	 */
	public static AvailObject create (
			final AvailObject closure,
			final AvailObject caller,
			final int startingChunkIndex,
			final List<AvailObject> args)
	{
		ContinuationDescriptor descriptor = mutableDescriptor();
		final AvailObject code = closure.code();
		final AvailObject cont = AvailObject.newIndexedDescriptor(
			code.numArgsAndLocalsAndStack(),
			descriptor);
		cont.caller(caller);
		cont.closure(closure);
		cont.pc(1);
		cont.stackp(
			cont.objectSlotsCount() + 1 - descriptor.numberOfFixedObjectSlots);
		cont.hiLevelTwoChunkLowOffset((startingChunkIndex << 16) + 1);
		for (int i = code.numArgsAndLocalsAndStack(); i >= 1; i--)
		{
			cont.localOrArgOrStackAtPut(i, VoidDescriptor.voidObject());
		}
		//  Set up arguments...
		final int nArgs = args.size();
		if (nArgs != code.numArgs())
		{
			error("Wrong number of arguments");
			return VoidDescriptor.voidObject();
		}
		for (int i = 1; i <= nArgs; i++)
		{
			//  arguments area
			cont.localOrArgOrStackAtPut(i, args.get(i - 1));
		}
		for (int i = 1, _end2 = code.numLocals(); i <= _end2; i++)
		{
			//  non-argument locals
			cont.localOrArgOrStackAtPut(
				nArgs + i,
				ContainerDescriptor.newContainerWithOuterType(
					code.localTypeAt(i)));
		}
		return cont;
	}

	/**
	 * Construct a new {@link ContinuationDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ContinuationDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ContinuationDescriptor}.
	 */
	private final static ContinuationDescriptor mutableDescriptor = new ContinuationDescriptor(true);

	/**
	 * Answer the mutable {@link ContinuationDescriptor}.
	 *
	 * @return The mutable {@link ContinuationDescriptor}.
	 */
	public static ContinuationDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link ContinuationDescriptor}.
	 */
	private final static ContinuationDescriptor immutableDescriptor = new ContinuationDescriptor(false);

	/**
	 * Answer the immutable {@link ContinuationDescriptor}.
	 *
	 * @return The immutable {@link ContinuationDescriptor}.
	 */
	public static ContinuationDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
