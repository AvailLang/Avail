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

import com.avail.descriptor.ApproximateTypeDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ContainerDescriptor;
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.descriptor.ContinuationTypeDescriptor;
import com.avail.descriptor.L2ChunkDescriptor;
import com.avail.descriptor.VoidDescriptor;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

@IntegerSlots({
	"pc", 
	"stackp", 
	"hiLevelTwoChunkLowOffset"
})
@ObjectSlots({
	"caller", 
	"closure", 
	"localOrArgOrStackAt#"
})
public class ContinuationDescriptor extends Descriptor
{


	// GENERATED accessors

	void ObjectCaller (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	void ObjectClosure (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-8, value);
	}

	void ObjectHiLevelTwoChunkLowOffset (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(12, value);
	}

	AvailObject ObjectLocalOrArgOrStackAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED getter method (indexed).

		return object.objectSlotAtByteIndex(((index * -4) + -8));
	}

	void ObjectLocalOrArgOrStackAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED setter method (indexed).

		object.objectSlotAtByteIndexPut(((index * -4) + -8), value);
	}

	void ObjectPc (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(4, value);
	}

	void ObjectStackp (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(8, value);
	}

	AvailObject ObjectCaller (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-4);
	}

	AvailObject ObjectClosure (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-8);
	}

	int ObjectHiLevelTwoChunkLowOffset (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(12);
	}

	int ObjectPc (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(4);
	}

	int ObjectStackp (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(8);
	}



	// GENERATED special mutable slots

	boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		if (index == 12)
		{
			return true;
		}
		return false;
	}



	// object creation

	public AvailObject newObjectToInvokeCallerLevelTwoChunkIndexArgs (
			final AvailObject closure, 
			final AvailObject caller, 
			final int startingChunkIndex, 
			final List<AvailObject> args)
	{
		//  Create a new continuation with the given data.  The continuation should represent
		//  the state upon entering the new context - i.e., set the pc to the first instruction
		//  (skipping the primitive indicator if necessary), clear the stack, and set up all
		//  local variables.

		assert isMutable();
		final AvailObject code = closure.code();
		final AvailObject cont = AvailObject.newIndexedDescriptor(code.numArgsAndLocalsAndStack(), this);
		cont.caller(caller);
		cont.closure(closure);
		cont.pc(1);
		cont.stackp((cont.objectSlotsCount() + 1));
		cont.hiLevelTwoChunkLowOffset(((startingChunkIndex << 16) + 1));
		for (int i = 1, _end1 = code.numArgsAndLocalsAndStack(); i <= _end1; i++)
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
			cont.localOrArgOrStackAtPut(nArgs + i, ContainerDescriptor.newContainerWithOuterType(code.localTypeAt(i)));
		}
		return cont;
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsContinuation(object);
	}

	boolean ObjectEqualsContinuation (
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
		for (int i = 1, _end1 = object.numLocalsOrArgsOrStack(); i <= _end1; i++)
		{
			if (!object.localOrArgOrStackAt(i).equals(aContinuation.localOrArgOrStackAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return ContinuationTypeDescriptor.continuationTypeForClosureType(object.closure().type());
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit long that is always the same for equal objects, but
		//  statistically different for different objects.

		int h = 0x593599A;
		h ^= object.caller().hash();
		h = ((h + object.closure().hash()) + (object.pc() * object.stackp()));
		for (int i = 1, _end1 = object.numLocalsOrArgsOrStack(); i <= _end1; i++)
		{
			h = (((h * 23) + 0x221C9) ^ object.localOrArgOrStackAt(i).hash());
		}
		return h;
	}

	boolean ObjectIsHashAvailable (
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

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}



	// operations-continuations

	void ObjectLevelTwoChunkIndexOffset (
			final AvailObject object, 
			final int index, 
			final int offset)
	{
		//  Set my chunk index and offset.

		object.hiLevelTwoChunkLowOffset(((index * 0x10000) + offset));
	}

	AvailObject ObjectStackAt (
			final AvailObject object, 
			final int slotIndex)
	{
		//  Read from the stack at the given slot index (relative to the object, not the stack area).

		return object.objectSlotAtByteIndex(slotIndex * -4);
	}

	void ObjectStackAtPut (
			final AvailObject object, 
			final int slotIndex, 
			final AvailObject anObject)
	{
		//  Write to the stack at the given slot index (relative to the object, not the stack area).

		object.objectSlotAtByteIndexPut(slotIndex * -4, anObject);
	}

	AvailObject ObjectEnsureMutable (
			final AvailObject object)
	{
		//  If immutable, copy the object as mutable, otherwise answer the original mutable.

		return (isMutable ? object : object.copyAsMutableContinuation());
	}

	int ObjectLevelTwoChunkIndex (
			final AvailObject object)
	{
		//  Answer the chunk index (without the offset).

		return (object.hiLevelTwoChunkLowOffset() >>> 16);
	}

	int ObjectLevelTwoOffset (
			final AvailObject object)
	{
		//  Answer the wordcode offset into the chunk.

		return (object.hiLevelTwoChunkLowOffset() & 0xFFFF);
	}

	int ObjectNumLocalsOrArgsOrStack (
			final AvailObject object)
	{
		//  Answer the number of slots allocated for locals, arguments, and stack entries.

		return (object.objectSlotsCount() - numberOfFixedObjectSlots);
	}



	// operations-faulting

	void ObjectPostFault (
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

	AvailObject ObjectCopyAsMutableContinuation (
			final AvailObject object)
	{
		//  Answer a fresh mutable copy of the given continuation object.

		if (isMutable)
		{
			object.makeSubobjectsImmutable();
		}
		final AvailObject result = AvailObject.newIndexedDescriptor((object.objectSlotsCount() - numberOfFixedObjectSlots), ContinuationDescriptor.mutableDescriptor());
		assert (result.objectSlotsCount() == object.objectSlotsCount());
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

	/**
	 * Construct a new {@link ContinuationDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param numberOfFixedObjectSlots
	 *        The number of fixed {@linkplain AvailObject object} slots.
	 * @param numberOfFixedIntegerSlots The number of fixed integer slots.
	 * @param hasVariableObjectSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable object slots?
	 * @param hasVariableIntegerSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable integer slots?
	 */
	protected ContinuationDescriptor (
		final int myId,
		final boolean isMutable,
		final int numberOfFixedObjectSlots,
		final int numberOfFixedIntegerSlots,
		final boolean hasVariableObjectSlots,
		final boolean hasVariableIntegerSlots)
	{
		super(
			myId,
			isMutable,
			numberOfFixedObjectSlots,
			numberOfFixedIntegerSlots,
			hasVariableObjectSlots,
			hasVariableIntegerSlots);
	}

	/* Descriptor lookup */
	public static ContinuationDescriptor mutableDescriptor()
	{
		return (ContinuationDescriptor) allDescriptors [38];
	}

	public static ContinuationDescriptor immutableDescriptor()
	{
		return (ContinuationDescriptor) allDescriptors [39];
	}
}
