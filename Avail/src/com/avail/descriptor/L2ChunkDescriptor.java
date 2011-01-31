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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.interpreter.levelTwo.*;

public class L2ChunkDescriptor extends Descriptor
{

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		INDEX,
		VALIDITY,
		NUM_OBJECTS,
		NUM_INTEGERS,
		NUM_DOUBLES,
		NEXT_INDEX,
		PREVIOUS_INDEX
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		CONTINGENT_IMP_SETS,
		WORDCODES,
		VECTORS,
		LITERAL_AT_
	}


	// GENERATED accessors

	/**
	 * Setter for field contingentImpSets.
	 */
	@Override
	public void o_ContingentImpSets (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CONTINGENT_IMP_SETS, value);
	}

	/**
	 * Setter for field index.
	 */
	@Override
	public void o_Index (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.INDEX, value);
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
	 * Setter for field nextIndex.
	 */
	@Override
	public void o_NextIndex (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.NEXT_INDEX, value);
	}

	/**
	 * Setter for field numFloats.
	 */
	@Override
	public void o_NumFloats (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.NUM_DOUBLES, value);
	}

	/**
	 * Setter for field numIntegers.
	 */
	@Override
	public void o_NumIntegers (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.NUM_INTEGERS, value);
	}

	/**
	 * Setter for field numObjects.
	 */
	@Override
	public void o_NumObjects (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.NUM_OBJECTS, value);
	}

	/**
	 * Setter for field previousIndex.
	 */
	@Override
	public void o_PreviousIndex (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.PREVIOUS_INDEX, value);
	}

	/**
	 * Setter for field validity.
	 */
	@Override
	public void o_Validity (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.VALIDITY, value);
	}

	/**
	 * Setter for field vectors.
	 */
	@Override
	public void o_Vectors (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.VECTORS, value);
	}

	/**
	 * Setter for field wordcodes.
	 */
	@Override
	public void o_Wordcodes (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.WORDCODES, value);
	}

	/**
	 * Getter for field contingentImpSets.
	 */
	@Override
	public AvailObject o_ContingentImpSets (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CONTINGENT_IMP_SETS);
	}

	/**
	 * Getter for field index.
	 */
	@Override
	public int o_Index (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.INDEX);
	}

	/**
	 * Getter for field nextIndex.
	 */
	@Override
	public int o_NextIndex (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.NEXT_INDEX);
	}

	/**
	 * Getter for field numFloats.
	 */
	@Override
	public int o_NumDoubles (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.NUM_DOUBLES);
	}

	/**
	 * Getter for field numIntegers.
	 */
	@Override
	public int o_NumIntegers (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.NUM_INTEGERS);
	}

	/**
	 * Getter for field numObjects.
	 */
	@Override
	public int o_NumObjects (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.NUM_OBJECTS);
	}

	/**
	 * Getter for field previousIndex.
	 */
	@Override
	public int o_PreviousIndex (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.PREVIOUS_INDEX);
	}

	/**
	 * Getter for field validity.
	 */
	@Override
	public int o_Validity (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.VALIDITY);
	}

	/**
	 * Getter for field vectors.
	 */
	@Override
	public AvailObject o_Vectors (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.VECTORS);
	}

	/**
	 * Getter for field wordcodes.
	 */
	@Override
	public AvailObject o_Wordcodes (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.WORDCODES);
	}



	@Override
	public boolean allowsImmutableToMutableReferenceInField (
			final Enum<?> e)
	{
		return e == ObjectSlots.CONTINGENT_IMP_SETS
			|| e == ObjectSlots.WORDCODES
			|| e == ObjectSlots.VECTORS
			|| e == ObjectSlots.LITERAL_AT_
			|| e == IntegerSlots.INDEX
			|| e == IntegerSlots.VALIDITY
			|| e == IntegerSlots.NEXT_INDEX
			|| e == IntegerSlots.PREVIOUS_INDEX;
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object,
			final StringBuilder aStream,
			final List<AvailObject> recursionList,
			final int indent)
	{
		//  Print this chunk on the given stream.

		aStream.append("Chunk #");
		aStream.append(object.index());
		aStream.append("\n");
		if (!object.isValid())
		{
			for (int t = 1; t <= indent; t++)
			{
				aStream.append("\t");
			}
			aStream.append("(INVALID)\n");
		}
		final AvailObject words = object.wordcodes();
		final L2RawInstructionDescriber describer = new L2RawInstructionDescriber();
		for (int i = 1, limit = words.tupleSize(); i <= limit; )
		{
			for (int t = 1; t <= indent + 1; t++)
			{
				aStream.append("\t");
			}
			aStream.append(String.format("#%-3d ", i));
			final L2Operation operation = L2Operation.values()[words.tupleAt(i).extractInt()];
			i++;
			final int[] operands = new int[operation.operandTypes().length];
			for (int opIndex = 0; opIndex < operands.length; opIndex++, i++)
			{
				operands[opIndex] = words.tupleAt(i).extractInt();
			}
			final L2RawInstruction rawInstruction = new L2RawInstruction(operation, operands);
			describer.describe(rawInstruction, object, aStream);
			aStream.append("\n");
		}
	}



	// operations-L2Chunk

	@Override
	public boolean o_Equals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override
	public void o_IsSaved (
			final AvailObject object,
			final boolean aBoolean)
	{
		object.validity(((object.validity() & -3) + (aBoolean ? 2 : 0)));
	}

	@Override
	public void o_IsValid (
			final AvailObject object,
			final boolean aBoolean)
	{
		object.validity(((object.validity() & -2) + (aBoolean ? 1 : 0)));
	}

	@Override
	public void o_NecessaryImplementationSetChanged (
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
			if (!imp.equals(anImplementationSet))
			{
				imp.removeDependentChunkId(object.index());
			}
		}
		object.contingentImpSets(TupleDescriptor.empty());
		for (int i = 1, _end2 = object.objectSlotsCount() - numberOfFixedObjectSlots(); i <= _end2; i++)
		{
			object.literalAtPut(i, VoidDescriptor.voidObject());
		}
		object.vectors(TupleDescriptor.empty());
		//  Make sure LRU queue doesn't refer to it.
		object.removeFromQueue();
	}

	@Override
	public void o_Next (
			final AvailObject object,
			final AvailObject nextChunk)
	{
		//  Set my successor in whatever ring I'm in.

		object.nextIndex(nextChunk.index());
	}

	@Override
	public void o_Previous (
			final AvailObject object,
			final AvailObject previousChunk)
	{
		//  Set my predecessor in whatever ring I'm in.

		object.previousIndex(previousChunk.index());
	}

	@Override
	public void o_EvictedByGarbageCollector (
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
		for (int i = 1, _end2 = object.objectSlotsCount() - numberOfFixedObjectSlots(); i <= _end2; i++)
		{
			object.literalAtPut(i, VoidDescriptor.voidObject());
		}
		object.vectors(VoidDescriptor.voidObject());
		object.removeFromQueue();
		//  Make sure LRU queue doesn't refer to it.
		object.index(-666);
		object.nextIndex(-666);
		object.previousIndex(-666);
	}

	@Override
	public int o_Hash (
			final AvailObject object)
	{
		//  Answer a 32-bit integer that is always the same for equal objects, but
		//  statistically different for different objects.

		return IntegerDescriptor.computeHashOfInt(object.index());
	}

	@Override
	public boolean o_IsSaved (
			final AvailObject object)
	{
		return (object.validity() & 2) == 2;
	}

	@Override
	public boolean o_IsValid (
			final AvailObject object)
	{
		return (object.validity() & 1) == 1;
	}

	@Override
	public void o_MoveToHead (
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

	@Override
	public AvailObject o_Next (
			final AvailObject object)
	{
		//  Answer my successor in whatever ring I'm in.

		final int index = object.nextIndex();
		return index == 0 ? L2ChunkDescriptor.headOfRing() : L2ChunkDescriptor.allChunks().getValue().tupleAt(index);
	}

	@Override
	public AvailObject o_Previous (
			final AvailObject object)
	{
		//  Answer my predecessor in whatever ring I'm in.

		final int index = object.previousIndex();
		return index == 0 ? L2ChunkDescriptor.headOfRing() : L2ChunkDescriptor.allChunks().getValue().tupleAt(index);
	}

	@Override
	public void o_RemoveFromQueue (
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
			Collections.<List<Integer>>emptyList(),
			0,
			0,
			0,
			Collections.<Integer>emptyList(),
			SetDescriptor.empty());
		head.next(head);
		head.previous(head);
		assert head.index() == 0;
		assert head.nextIndex() == 0;
		assert head.previousIndex() == 0;
		final AvailObject allChunks = ContainerDescriptor.forInnerType(
			ALL.o());
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
	public static AvailObject chunkFromId (final int anId)
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
		final boolean allocateIndex,
		final AvailObject code,
		final List<AvailObject> arrayOfLiterals,
		final List<List<Integer>> arrayOfVectors,
		final int nObjs,
		final int nInts,
		final int nFloats,
		final List<Integer> theWordcodes,
		final AvailObject contingentSets)
	{
		final List<AvailObject> vectorTuples = new ArrayList<AvailObject>(arrayOfVectors.size());
		for (final List<Integer> vector : arrayOfVectors)
		{
			final AvailObject vectorTuple = TupleDescriptor.fromIntegerList(vector);
			vectorTuple.makeImmutable();
			vectorTuples.add(vectorTuple);
		}
		final AvailObject vectorTuplesTuple = TupleDescriptor.fromList(
			vectorTuples);
		vectorTuplesTuple.makeImmutable();
		final AvailObject wordcodesTuple =
			TupleDescriptor.fromIntegerList(theWordcodes);
		wordcodesTuple.makeImmutable();
		final AvailObject chunk = mutable().create(
			arrayOfLiterals.size());
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
				final AvailObject oldChunks = allChunks().getValue();
				final AvailObject newChunks = ObjectTupleDescriptor.mutable().create(
					oldChunks.tupleSize() * 2 + 10);
				for (int i = 1; i <= oldChunks.tupleSize(); i++)
				{
					newChunks.tupleAtPut(i, oldChunks.tupleAt(i));
				}
				for (int i = newChunks.tupleSize(); i > oldChunks.tupleSize(); i--)
				{
					newChunks.tupleAtPut(i, IntegerDescriptor.fromInt(NextFreeChunkIndex));
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
			final AvailObject contingentSetsTuple = contingentSets.asTuple();
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

	/**
	 * Construct a new {@link L2ChunkDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected L2ChunkDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link L2ChunkDescriptor}.
	 */
	private final static L2ChunkDescriptor mutable = new L2ChunkDescriptor(true);

	/**
	 * Answer the mutable {@link L2ChunkDescriptor}.
	 *
	 * @return The mutable {@link L2ChunkDescriptor}.
	 */
	public static L2ChunkDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link L2ChunkDescriptor}.
	 */
	private final static L2ChunkDescriptor immutable = new L2ChunkDescriptor(false);

	/**
	 * Answer the immutable {@link L2ChunkDescriptor}.
	 *
	 * @return The immutable {@link L2ChunkDescriptor}.
	 */
	public static L2ChunkDescriptor immutable ()
	{
		return immutable;
	}
}
