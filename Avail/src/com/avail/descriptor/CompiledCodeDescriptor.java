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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.L2ChunkDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;

import static com.avail.descriptor.AvailObject.*;

@IntegerSlots({
	"hash", 
	"hiPrimitiveLowNumArgsAndLocalsAndStack", 
	"hiNumLocalsLowNumArgs", 
	"hiStartingChunkIndexLowNumOuters", 
	"invocationCount"
})
@ObjectSlots({
	"nybbles", 
	"closureType", 
	"literalAt#"
})
public class CompiledCodeDescriptor extends Descriptor
{


	// GENERATED accessors

	void ObjectClosureType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-8, value);
	}

	void ObjectHash (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(4, value);
	}

	void ObjectHiNumLocalsLowNumArgs (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(12, value);
	}

	void ObjectHiPrimitiveLowNumArgsAndLocalsAndStack (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(8, value);
	}

	void ObjectHiStartingChunkIndexLowNumOuters (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(16, value);
	}

	void ObjectInvocationCount (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(20, value);
	}

	AvailObject ObjectLiteralAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED getter method (indexed).

		return object.objectSlotAtByteIndex(((index * -4) + -8));
	}

	void ObjectLiteralAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED setter method (indexed).

		object.objectSlotAtByteIndexPut(((index * -4) + -8), value);
	}

	void ObjectNybbles (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	AvailObject ObjectClosureType (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-8);
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(4);
	}

	int ObjectHiNumLocalsLowNumArgs (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(12);
	}

	int ObjectHiPrimitiveLowNumArgsAndLocalsAndStack (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(8);
	}

	int ObjectHiStartingChunkIndexLowNumOuters (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(16);
	}

	int ObjectInvocationCount (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(20);
	}

	AvailObject ObjectNybbles (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-4);
	}



	// GENERATED special mutable slots

	boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		if (index == 16)
		{
			return true;
		}
		if (index == 20)
		{
			return true;
		}
		return false;
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsCompiledCode(object);
	}

	boolean ObjectEqualsCompiledCode (
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

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.compiledCode.object();
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.compiledCode.object();
	}



	// operations-closure

	boolean ObjectContainsBlock (
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

	void ObjectArgsLocalsStackOutersPrimitive (
			final AvailObject object, 
			final int args, 
			final int locals, 
			final int stack, 
			final int outers, 
			final int primitive)
	{
		//  Note - also zeroes the startingChunkIndex.

		assert (0 <= args && args <= 0xFFFF);
		assert (0 <= locals && locals <= 0xFFFF);
		final int slotCount = (args + locals) + stack;
		assert (0 <= slotCount && slotCount <= 0xFFFF);
		assert (0 <= outers && outers <= 0xFFFF);
		assert (0 <= primitive && primitive <= 0xFFFF);
		object.hiNumLocalsLowNumArgs(((locals << 16) + args));
		object.hiPrimitiveLowNumArgsAndLocalsAndStack(((primitive << 16) + slotCount));
		object.hiStartingChunkIndexLowNumOuters(outers);
	}

	AvailObject ObjectLocalTypeAt (
			final AvailObject object, 
			final int index)
	{
		assert (1 <= index && index <= object.numLocals());
		return object.literalAt(((object.numLiterals() - object.numLocals()) + index));
	}

	AvailObject ObjectOuterTypeAt (
			final AvailObject object, 
			final int index)
	{
		assert (1 <= index && index <= object.numOuters());
		return object.literalAt((((object.numLiterals() - object.numLocals()) - object.numOuters()) + index));
	}

	void ObjectOuterTypesLocalTypes (
			final AvailObject object, 
			final AvailObject tupleOfOuterTypes, 
			final AvailObject tupleOfLocalContainerTypes)
	{
		//  The literal frame has the literals used by the code, followed by the outer types,
		//  followed by the local variable types.

		assert (tupleOfOuterTypes.tupleSize() == object.numOuters()) : "Wrong number of outer types.";
		assert (tupleOfLocalContainerTypes.tupleSize() == object.numLocals()) : "Wrong number of local types.";
		int src = 1;
		for (int dest = (((object.numLiterals() - object.numLocals()) - object.numOuters()) + 1), _end1 = (object.numLiterals() - object.numLocals()); dest <= _end1; dest++)
		{
			object.literalAtPut(dest, tupleOfOuterTypes.tupleAt(src));
			src++;
		}
		src = 1;
		for (int dest = ((object.numLiterals() - object.numLocals()) + 1), _end2 = object.numLiterals(); dest <= _end2; dest++)
		{
			object.literalAtPut(dest, tupleOfLocalContainerTypes.tupleAt(src));
			src++;
		}
	}

	void ObjectStartingChunkIndex (
			final AvailObject object, 
			final int value)
	{
		object.hiStartingChunkIndexLowNumOuters(((object.hiStartingChunkIndexLowNumOuters() & 0xFFFF) + (value << 16)));
	}

	short ObjectMaxStackDepth (
			final AvailObject object)
	{
		return ((short)(((object.numArgsAndLocalsAndStack() - object.numArgs()) - object.numLocals())));
	}

	short ObjectNumArgs (
			final AvailObject object)
	{
		return ((short)((object.hiNumLocalsLowNumArgs() & 0xFFFF)));
	}

	short ObjectNumArgsAndLocalsAndStack (
			final AvailObject object)
	{
		//  Answer the number of args + locals + stack slots to reserve in my continuations.

		return ((short)((object.hiPrimitiveLowNumArgsAndLocalsAndStack() & 0xFFFF)));
	}

	short ObjectNumLiterals (
			final AvailObject object)
	{
		//  Answer how many literals I have.

		return ((short)((object.objectSlotsCount() - numberOfFixedObjectSlots)));
	}

	short ObjectNumLocals (
			final AvailObject object)
	{
		return ((short)((object.hiNumLocalsLowNumArgs() >>> 16)));
	}

	short ObjectNumOuters (
			final AvailObject object)
	{
		return ((short)((object.hiStartingChunkIndexLowNumOuters() & 0xFFFF)));
	}

	short ObjectPrimitiveNumber (
			final AvailObject object)
	{
		//  Answer the primitive number I should try before falling back on
		//  the Avail code.  Zero indicates not-a-primitive.

		return ((short)((object.hiPrimitiveLowNumArgsAndLocalsAndStack() >>> 16)));
	}

	int ObjectStartingChunkIndex (
			final AvailObject object)
	{
		return (object.hiStartingChunkIndexLowNumOuters() >>> 16);
	}



	// operations-faulting

	void ObjectPostFault (
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





	/* Object creation */

	public static AvailObject newCompiledCodeWithNybblesNumArgsLocalsStackClosureTypePrimitiveLiteralsLocalTypesOuterTypes (
			AvailObject nybbles,
			int numArgs,
			int locals,
			int stack,
			AvailObject closureType,
			int primitive,
			AvailObject literals,
			AvailObject localTypes,
			AvailObject outerTypes)
	{
		assert localTypes.tupleSize() == locals;
		assert closureType.numArgs() == numArgs;
		int literalsSize = literals.tupleSize();
		int outersSize = outerTypes.tupleSize();
		AvailObject code = AvailObject.newIndexedDescriptor (
			literalsSize + outersSize + locals,
			CompiledCodeDescriptor.mutableDescriptor());

		CanAllocateObjects(false);
		code.nybbles(nybbles);
		code.argsLocalsStackOutersPrimitive(numArgs, locals, stack, outersSize, primitive);
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
		int hash = (0x0B085B25 + code.objectSlotsCount() + nybbles.hash()) ^ (numArgs * 4127);
		hash += (locals * 1237) + (stack * 9131) + (primitive * 1151);
		hash ^= closureType.hash();
		for (int i = 1; i <= literalsSize; i++)
		{
			hash = (hash * 2 + literals.tupleAt(i).hash()) ^ 0x052B580B;
		}
		for (int i = 1; i <= outersSize; i++)
		{
			hash = (hash * 3 + outerTypes.tupleAt(i).hash()) ^ 0x015F5947;
		}
		for (int i = 1; i <= locals; ++ i)
		{
			hash = (hash * 5 + localTypes.tupleAt(i).hash()) ^ 0x01E37808;
		}
		code.hash(hash);
		code.makeImmutable();
		CanAllocateObjects(true);

		return code;
	};

	/**
	 * Construct a new {@link CompiledCodeDescriptor}.
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
	protected CompiledCodeDescriptor (
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

	public static CompiledCodeDescriptor mutableDescriptor()
	{
		return (CompiledCodeDescriptor) allDescriptors [30];
	}

	public static CompiledCodeDescriptor immutableDescriptor()
	{
		return (CompiledCodeDescriptor) allDescriptors [31];
	}
}
