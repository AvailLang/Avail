/**
 * descriptor/L2ChunkDescriptor.java
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
import com.avail.descriptor.ContainerDescriptor;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.L2ChunkDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.L2RawInstruction;
import com.avail.interpreter.levelTwo.L2RawInstructionDescriber;
import com.avail.interpreter.levelTwo.L2Translator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@IntegerSlots({
	"index", 
	"validity", 
	"numObjects", 
	"numIntegers", 
	"numFloats", 
	"nextIndex", 
	"previousIndex"
})
@ObjectSlots({
	"contingentImpSets", 
	"wordcodes", 
	"vectors", 
	"literalAt#"
})
public class L2ChunkDescriptor extends Descriptor
{


	// GENERATED accessors

	void ObjectContingentImpSets (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	void ObjectIndex (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(4, value);
	}

	AvailObject ObjectLiteralAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED getter method (indexed).

		return object.objectSlotAtByteIndex(((index * -4) + -12));
	}

	void ObjectLiteralAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED setter method (indexed).

		object.objectSlotAtByteIndexPut(((index * -4) + -12), value);
	}

	void ObjectNextIndex (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(24, value);
	}

	void ObjectNumFloats (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(20, value);
	}

	void ObjectNumIntegers (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(16, value);
	}

	void ObjectNumObjects (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(12, value);
	}

	void ObjectPreviousIndex (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(28, value);
	}

	void ObjectValidity (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(8, value);
	}

	void ObjectVectors (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-12, value);
	}

	void ObjectWordcodes (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-8, value);
	}

	AvailObject ObjectContingentImpSets (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-4);
	}

	int ObjectIndex (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(4);
	}

	int ObjectNextIndex (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(24);
	}

	int ObjectNumFloats (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(20);
	}

	int ObjectNumIntegers (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(16);
	}

	int ObjectNumObjects (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(12);
	}

	int ObjectPreviousIndex (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(28);
	}

	int ObjectValidity (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(8);
	}

	AvailObject ObjectVectors (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-12);
	}

	AvailObject ObjectWordcodes (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-8);
	}



	// GENERATED special mutable slots

	boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		if ((index == -4))
		{
			return true;
		}
		if ((index == -8))
		{
			return true;
		}
		if ((index == -12))
		{
			return true;
		}
		if ((index <= -16))
		{
			return true;
		}
		if ((index == 4))
		{
			return true;
		}
		if ((index == 8))
		{
			return true;
		}
		if ((index == 24))
		{
			return true;
		}
		if ((index == 28))
		{
			return true;
		}
		return false;
	}



	// java printing

	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		//  Print this chunk on the given stream.

		aStream.append("Chunk #");
		aStream.append(object.index());
		aStream.append("\n");
		if (! object.isValid())
		{
			for (int t = 1; t <= indent; t++)
			{
				aStream.append("\t");
			}
			aStream.append("(INVALID)\n");
		}
		AvailObject words = object.wordcodes();
		L2RawInstructionDescriber describer = new L2RawInstructionDescriber();
		for (int i = 1, limit = words.tupleSize(); i <= limit; )
		{
			for (int t = 1; t <= indent + 1; t++)
			{
				aStream.append("\t");
			}
			aStream.append(String.format("#%-3d ", i));
			L2Operation operation = L2Operation.values()[words.tupleAt(i).extractInt()];
			i++;
			int[] operands = new int[operation.operandTypes().length];
			for (int opIndex = 0; opIndex < operands.length; opIndex++, i++)
			{
				operands[opIndex] = words.tupleAt(i).extractInt();
			}
			L2RawInstruction rawInstruction = new L2RawInstruction(operation, operands);
			describer.describe(rawInstruction, object, aStream);
			aStream.append("\n");
		}
	}



	// operations-L2Chunk

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	void ObjectIsSaved (
			final AvailObject object, 
			final boolean aBoolean)
	{
		object.validity(((object.validity() & -3) + (aBoolean ? 2 : 0)));
	}

	void ObjectIsValid (
			final AvailObject object, 
			final boolean aBoolean)
	{
		object.validity(((object.validity() & -2) + (aBoolean ? 1 : 0)));
	}

	void ObjectNecessaryImplementationSetChanged (
			final AvailObject object, 
			final AvailObject anImplementationSet)
	{
		//  An implementation set has changed.  This means a method definition (or a forward or
		//  an abstract declaration) has been added or removed from the implementation set, and
		//  the receiver previously expressed an interest in change notifications.  This must have
		//  been because it was optimized in a way that relied on some aspect of the available
		//  implementations (e.g., monomorphic inlining), so I need to invalidate myself.  Make me
		//  into a chunk that looks just like the one that would be there if the compiledCode
		//  associated with this chunk had never run.  Remove myself from all the implementation
		//  sets I depend on, once I have been invalidated.
		//  Mark myself as invalid so that if anyone wants to start up some code that uses me as
		//  its chunk, or return into a continuation that uses me, they will immediately realize (by
		//  checking) that I am invalid.  At that point, the method will be reoptimized.  Note that in
		//  the case of returning into a continuation, we must either omit any optimization or take
		//  care to optimize in such a way that the correct onramp is created and used.

		object.isValid(false);
		object.wordcodes(TupleDescriptor.empty());
		final AvailObject impSets = object.contingentImpSets();
		for (int i = 1, _end1 = impSets.tupleSize(); i <= _end1; i++)
		{
			final AvailObject imp = impSets.tupleAt(i);
			if (! imp.equals(anImplementationSet))
			{
				imp.removeDependentChunkId(object.index());
			}
		}
		object.contingentImpSets(TupleDescriptor.empty());
		for (int i = 1, _end2 = (object.objectSlotsCount() - numberOfFixedObjectSlots()); i <= _end2; i++)
		{
			object.literalAtPut(i, VoidDescriptor.voidObject());
		}
		object.vectors(TupleDescriptor.empty());
		//  Make sure LRU queue doesn't refer to it.
		object.removeFromQueue();
	}

	void ObjectNext (
			final AvailObject object, 
			final AvailObject nextChunk)
	{
		//  Set my successor in whatever ring I'm in.

		object.nextIndex(nextChunk.index());
	}

	void ObjectPrevious (
			final AvailObject object, 
			final AvailObject previousChunk)
	{
		//  Set my predecessor in whatever ring I'm in.

		object.previousIndex(previousChunk.index());
	}

	void ObjectEvictedByGarbageCollector (
			final AvailObject object)
	{
		//  The garbage collector has evicted me.  Since it is supposed to ensure I will never be
		//  invoked again, I can remove myself from the implementation sets I used to depend on,
		//  and tag myself as unusable.

		object.isValid(false);
		object.isSaved(false);
		//  Nobody should be interested in this state any more.
		object.wordcodes(VoidDescriptor.voidObject());
		final AvailObject impSets = object.contingentImpSets();
		for (int i = 1, _end1 = impSets.tupleSize(); i <= _end1; i++)
		{
			impSets.tupleAt(i).removeDependentChunkId(object.index());
		}
		object.contingentImpSets(VoidDescriptor.voidObject());
		for (int i = 1, _end2 = (object.objectSlotsCount() - numberOfFixedObjectSlots()); i <= _end2; i++)
		{
			object.literalAtPut(i, VoidDescriptor.voidObject());
		}
		object.vectors(VoidDescriptor.voidObject());
		object.removeFromQueue();
		//  Make sure LRU queue doesn't refer to it.
		object.index(-0x29A);
		object.nextIndex(-0x29A);
		object.previousIndex(-0x29A);
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit integer that is always the same for equal objects, but
		//  statistically different for different objects.

		return IntegerDescriptor.computeHashOfInt(object.index());
	}

	boolean ObjectIsSaved (
			final AvailObject object)
	{
		return ((object.validity() & 2) == 2);
	}

	boolean ObjectIsValid (
			final AvailObject object)
	{
		return ((object.validity() & 1) == 1);
	}

	void ObjectMoveToHead (
			final AvailObject object)
	{
		//  Maintain the LRU queue so that some percentage of the most recently used chunks
		//  will survive a garbage collection.

		object.next().previousIndex(object.previousIndex());
		object.previous().nextIndex(object.nextIndex());
		//  Now link it in at the head of the ring.
		final AvailObject head = L2ChunkDescriptor.headOfRing();
		final AvailObject headNext = head.next();
		object.nextIndex(headNext.index());
		object.previousIndex(head.index());
		headNext.previousIndex(object.index());
		head.nextIndex(object.index());
	}

	AvailObject ObjectNext (
			final AvailObject object)
	{
		//  Answer my successor in whatever ring I'm in.

		final int index = object.nextIndex();
		return ((index == 0) ? L2ChunkDescriptor.headOfRing() : L2ChunkDescriptor.allChunks().getValue().tupleAt(index));
	}

	AvailObject ObjectPrevious (
			final AvailObject object)
	{
		//  Answer my predecessor in whatever ring I'm in.

		final int index = object.previousIndex();
		return ((index == 0) ? L2ChunkDescriptor.headOfRing() : L2ChunkDescriptor.allChunks().getValue().tupleAt(index));
	}

	void ObjectRemoveFromQueue (
			final AvailObject object)
	{
		//  Unlink this no longer valid entry from the LRU queue.

		object.next().previousIndex(object.previousIndex());
		object.previous().nextIndex(object.nextIndex());
		object.previousIndex(object.index());
		object.nextIndex(object.index());
	}




	// Startup/shutdown

	static AvailObject HeadOfRing;


	static AvailObject AllChunks;


	static int NextFreeChunkIndex;

	static void createWellKnownObjects ()
	{
		AvailObject head;
		head = L2ChunkDescriptor.allocateIndexCodeLiteralsVectorsNumObjectsNumIntegersNumFloatsWordcodesContingentImpSets(
			false,
			VoidDescriptor.voidObject(),
			Collections.<AvailObject>emptyList(),
			Collections.<ArrayList<Integer>>emptyList(),
			0,
			0,
			0,
			Collections.<Integer>emptyList(),
			SetDescriptor.empty());
		head.next(head);
		head.previous(head);
		assert (head.index() == 0);
		assert (head.nextIndex() == 0);
		assert (head.previousIndex() == 0);
		final AvailObject allChunks = ContainerDescriptor.newContainerWithInnerType(TypeDescriptor.all());
		allChunks.setValue(TupleDescriptor.empty());
		HeadOfRing = head;
		AllChunks = allChunks;
		NextFreeChunkIndex = -1;
		new L2Translator().createChunkForFirstInvocation();
	}

	static void clearWellKnownObjects ()
	{
		HeadOfRing = null;
		AllChunks = null;
		NextFreeChunkIndex = -1;
	}



	/* L2ChunkDescriptor ring accessing */
	public static AvailObject headOfRing ()
	{
		return HeadOfRing;
	}
	public static AvailObject allChunks ()
	{
		return AllChunks;
	}
	public static AvailObject chunkFromId (int anId)
	{
		return allChunks().getValue().tupleAt(anId);
	}
	public static int indexOfUnoptimizedChunk ()
	{
		return 1;
	}
	public static int offsetOfUnoptimizedChunk ()
	{
		return 1;
	}
	public static int offsetToSingleStepUnoptimizedChunk ()
	{
		return 2;
	}
	public static int offsetToContinueUnoptimizedChunk ()
	{
		return 4;
	}
	public static int offsetToPauseUnoptimizedChunk ()
	{
		return 5;
	}

	public static int countdownForInvalidatedCode ()
	{
		return 100;
	}
	public static int countdownForNewCode ()
	{
		return 20;
	}
	public static int countdownForNewlyOptimizedCode ()
	{
		return 200;
	}

	public static AvailObject allocateIndexCodeLiteralsVectorsNumObjectsNumIntegersNumFloatsWordcodesContingentImpSets(
		boolean allocateIndex,
		AvailObject code,
		List<AvailObject> arrayOfLiterals,
		List<ArrayList<Integer>> arrayOfVectors,
		int nObjs,
		int nInts,
		int nFloats,
		List<Integer> theWordcodes,
		AvailObject contingentSets)
	{
		ArrayList<AvailObject> vectorTuples = new ArrayList<AvailObject>(arrayOfVectors.size());
		for (ArrayList<Integer> vector : arrayOfVectors)
		{
			AvailObject vectorTuple = TupleDescriptor.mutableCompressedFromIntegerArray(vector);
			vectorTuple.makeImmutable();
			vectorTuples.add(vectorTuple);
		}
		AvailObject vectorTuplesTuple = TupleDescriptor.mutableObjectFromArray(vectorTuples);
		vectorTuplesTuple.makeImmutable();
		AvailObject wordcodesTuple = TupleDescriptor.mutableCompressedFromIntegerArray(theWordcodes);
		wordcodesTuple.makeImmutable();
		AvailObject chunk = AvailObject.newIndexedDescriptor(
			arrayOfLiterals.size(),
			L2ChunkDescriptor.mutableDescriptor());
		AvailObject.lock(chunk);
		chunk.vectors(vectorTuplesTuple);
		chunk.numObjects(nObjs);
		chunk.numIntegers(nInts);
		chunk.numFloats(nFloats);
		chunk.wordcodes(wordcodesTuple);
		for (int i = 1; i <= arrayOfLiterals.size(); i++)
		{
			chunk.literalAtPut(i, arrayOfLiterals.get(i - 1));
		}
		chunk.contingentImpSets(TupleDescriptor.empty());   // in case of GC below
		if (allocateIndex)
		{
			int index;
			chunk.isValid(true);
			chunk.isSaved(true);   // it starts saved by default
			if (NextFreeChunkIndex == -1)
			{
				// Allocate room for more chunks in the tuple of all chunks.
				AvailObject oldChunks = allChunks().getValue();
				AvailObject newChunks = AvailObject.newIndexedDescriptor(
					oldChunks.tupleSize() * 2 + 10,
					ObjectTupleDescriptor.mutableDescriptor());
				for (int i = 1; i <= oldChunks.tupleSize(); i++)
				{
					newChunks.tupleAtPut(i, oldChunks.tupleAt(i));
				}
				for (int i = newChunks.tupleSize(); i > oldChunks.tupleSize(); i--)
				{
					newChunks.tupleAtPut(i, IntegerDescriptor.objectFromInt(NextFreeChunkIndex));
					NextFreeChunkIndex = i;
				}
				allChunks().setValue(newChunks);
			}
			index = NextFreeChunkIndex;
			NextFreeChunkIndex = allChunks().getValue().tupleIntAt(index);
			allChunks().setValue(allChunks().getValue().tupleAtPuttingCanDestroy(
				index,
				chunk,
				true));
			chunk.index(index);
			// Ring pointers should (initially) be aimed back at the chunk.
			chunk.nextIndex(index);
			chunk.previousIndex(index);
			// Now that the index has been assigned, connect the dependency.  Since connecting
			// the dependency may grow some sets, make sure the GC can be invoked safely.  To
			// assist this, make sure the code is referring to the chunk being set up, to avoid having
			// it garbage collected before we have a chance to install it.
			if (code != null)
			{
				code.startingChunkIndex(index);
				code.invocationCount(L2ChunkDescriptor.countdownForNewlyOptimizedCode());
			}
			AvailObject contingentSetsTuple = contingentSets.asTuple();
			chunk.contingentImpSets(contingentSetsTuple);
			for (int i = 1; i <= contingentSetsTuple.tupleSize(); i++)
			{
				contingentSetsTuple.tupleAt(i).addDependentChunkId(index);
			}
		}
		else
		{
			// This is a special permanent chunk, so it should not be contingent on anything.  Also,
			// its index should be zero, and it should not be in the allChunks() collection.
			chunk.isValid(false);
			chunk.isSaved(true);
			chunk.index(0);
			chunk.nextIndex(0);
			chunk.previousIndex(0);
			chunk.contingentImpSets(TupleDescriptor.empty());
			assert contingentSets.setSize() == 0;
		}
		AvailObject.unlock(chunk);
		chunk.makeImmutable();
		return chunk;
	}


	/* Descriptor lookup */
	public static L2ChunkDescriptor mutableDescriptor()
	{
		return (L2ChunkDescriptor) allDescriptors [82];
	};
	public static L2ChunkDescriptor immutableDescriptor()
	{
		return (L2ChunkDescriptor) allDescriptors [83];
	};

}
