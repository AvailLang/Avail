/**
 * L2ChunkDescriptor.java
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

package com.avail.descriptor;

import java.lang.ref.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.instruction.L2Instruction;
import com.avail.interpreter.levelTwo.register.*;

/**
 * A Level Two chunk represents an optimized implementation of a {@linkplain
 * CompiledCodeDescriptor compiled code object}.
 *
 * <p>
 * The chunks are held onto by weak references in a list (keyed by chunk index).
 * When a chunk expires due to not being referred to by any code or
 * continuations, its weak reference is added to a queue from which chunk index
 * recycling takes place.  The weak references also keep track of the contingent
 * methods.  The methods maintain the reverse relation
 * by keeping track of the indices of all chunks that depend on them.  When an
 * method changes (due to a method being added or removed), the
 * dependent chunks can be marked as invalid and eviscerated (to reclaim
 * memory).  When an attempt is made to use an invalidated chunk by invoking a
 * compiled code object or returning into a continuation, the reference to the
 * chunk is replaced by a reference to the default chunk (and a continuation
 * gets its offset set to the level one dispatch loop).  When all references to
 * the chunk have been so replaced, the chunk's weak reference will appear on
 * the ReferenceQueue, allowing the index to be recycled.
 * </p>
 *
 * <p>
 * Eventually we can limit the number of valid chunks by linking the weak
 * references together into an LRU ring.  When adding a new chunk to a "full"
 * ring, the oldest element can simply be invalidated, removing it from the
 * ring.  The level two interpreter is already instrumented to call
 * moveToHead() at appropriate times.
 * </p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class L2ChunkDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The unique integer that identifies this chunk.  Weak references are
		 * used to determine when it is safe to recycle an index for a new
		 * chunk.
		 */
		INDEX,

		/**
		 * A compound field that contains information about how many {@linkplain
		 * L2ObjectRegister object registers} are needed by this
		 * chunk, as well as some flags.  Having the number of needed object
		 * registers stored separately allows the register list to be
		 * dynamically expanded as needed only when starting or resuming a
		 * continuation.
		 */
		NUM_OBJECTS_AND_FLAGS,

		/**
		 * A compound field containing the number of {@linkplain
		 * L2IntegerRegister integer registers} and the number of {@linkplain
		 * L2FloatRegister floating point registers} that are used by this
		 * chunk.  Having this recorded separately allows the register list to
		 * be dynamically expanded as needed only when starting or resuming a
		 * continuation.
		 */
		NUM_INTEGERS_AND_DOUBLES;


		/**
		 * The number of {@linkplain L2ObjectRegister object registers} that
		 * this chunk uses.
		 */
		static final BitField NUM_OBJECTS = bitField(
			NUM_OBJECTS_AND_FLAGS,
			0,
			30);

		/**
		 * A flag indicating whether this chunk has been reached by the garbage
		 * collector in the current scavenge cycle.  If it's still clear at flip
		 * time, the chunk is unreferenced and can be reclaimed.
		 *
		 * TODO: [MvG] This is not used by the current (2011.05.11)
		 * Avail-on-Java VM.
		 */
		static final BitField SAVED = bitField(
			NUM_OBJECTS_AND_FLAGS,
			30,
			1);

		/**
		 * A flag indicating whether this chunk is valid or if it has been
		 * invalidated by the addition or removal of a method signature.
		 */
		static final BitField VALID = bitField(
			NUM_OBJECTS_AND_FLAGS,
			31,
			1);

		/**
		 * The number of {@linkplain L2IntegerRegister integer registers} that are
		 * used by this chunk.  Having this recorded separately allows the
		 * register list to be dynamically expanded as needed only when starting
		 * or resuming a continuation.
		 */
		static final BitField NUM_INTEGERS = bitField(
			NUM_INTEGERS_AND_DOUBLES,
			0,
			16);

		/**
		 * The number of {@linkplain L2FloatRegister floating point registers} that
		 * are used by this chunk.  Having this recorded separately allows the
		 * register list to be dynamically expanded as needed only when starting
		 * or resuming a continuation.
		 */
		static final BitField NUM_DOUBLES = bitField(
			NUM_INTEGERS_AND_DOUBLES,
			16,
			16);

	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain L2Instruction level two instructions} encoded as a
		 * tuple of integers.
		 */
		WORDCODES,

		/**
		 * A {@linkplain TupleDescriptor tuple} of tuples of integers.  Each
		 * integer represents an object register, so each tuple of integers acts
		 * like a list of registers to be processed together, such as supplying
		 * arguments to a method invocation.
		 */
		VECTORS,

		/**
		 * The literal objects that the {@linkplain #WORDCODES wordcodes} refer
		 * to via encoded operands of type {@link L2OperandType#CONSTANT} or
		 * {@link L2OperandType#SELECTOR}.
		 */
		LITERAL_AT_
	}

	@Override @AvailMethod
	void o_Index (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(IntegerSlots.INDEX, value);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_LiteralAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.slot(ObjectSlots.LITERAL_AT_, subscript);
	}

	@Override @AvailMethod
	int o_Index (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.INDEX);
	}

	@Override @AvailMethod
	int o_NumDoubles (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.NUM_DOUBLES);
	}

	@Override @AvailMethod
	int o_NumIntegers (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.NUM_INTEGERS);
	}

	@Override @AvailMethod
	int o_NumObjects (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.NUM_OBJECTS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Vectors (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.VECTORS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Wordcodes (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.WORDCODES);
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == ObjectSlots.WORDCODES
			|| e == ObjectSlots.VECTORS
			|| e == ObjectSlots.LITERAL_AT_
			|| e == IntegerSlots.NUM_OBJECTS_AND_FLAGS;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		//  Print this chunk on the given stream.

		if (object.index() == 0)
		{
			aStream.append("Default chunk #0");
			return;
		}
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

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	void o_IsSaved (
		final @NotNull AvailObject object,
		final boolean aBoolean)
	{
		object.setSlot(IntegerSlots.SAVED, aBoolean ? 1 : 0);
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		return IntegerDescriptor.computeHashOfInt(object.index());
	}

	@Override @AvailMethod
	boolean o_IsSaved (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.SAVED) != 0;
	}

	@Override @AvailMethod
	boolean o_IsValid (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.VALID) != 0;
	}


	/**
	 * A {@link WeakChunkReference} is the mechanism by which {@linkplain
	 * L2ChunkDescriptor level two chunks} are recycled by the Java garbage
	 * collector.  When a chunk is only weakly reachable (i.e., there are no
	 * strong or soft references to it), the index reserved for that chunk
	 * becomes eligible for recycling.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	static class WeakChunkReference extends WeakReference<AvailObject>
	{
		/**
		 * The finalization queue onto which {@linkplain L2ChunkDescriptor level
		 * two chunks}' {@linkplain WeakChunkReference weak references} will be
		 * placed upon expiration.  There is no special process to remove them
		 * from here.  Rather, an element of this queue is consumed when needed
		 * for {@linkplain L2ChunkDescriptor#allocate(AvailObject, List, List,
		 * int, int, int, List, Set) allocation} of a new chunk.  If this queue
		 * is empty, a fresh index is allocated.
		 *
		 * <p>
		 * Note that this is not defined as final, since we must ensure that an
		 * invocation of the virtual machine does not interfere with subsequent
		 * invocations (for clean tests), and we construct a new {@link
		 * ReferenceQueue} as part of {@link
		 * L2ChunkDescriptor#createWellKnownObjects()}.
		 */
		static ReferenceQueue<AvailObject> RecyclingQueue =
			new ReferenceQueue<AvailObject>();

		/**
		 * The {@linkplain L2ChunkDescriptor.IntegerSlots#INDEX index} of the
		 * {@linkplain L2ChunkDescriptor level two chunk} to which this
		 * reference either refers or once referred.
		 */
		final int index;

		/**
		 * The list of {@linkplain MethodDescriptor methods} on which the referent chunk depends.  If one of these
		 * methods changes (due to adding or removing a
		 * {@linkplain ImplementationDescriptor method implementation}), this chunk
		 * will be immediately invalidated.
		 */
		final Set<AvailObject> contingentMethods;

		/**
		 * Construct a new {@link WeakChunkReference}.
		 *
		 * @param chunk
		 *            The chunk to be wrapped with a weak reference.
		 * @param contingentMethods
		 *            The {@linkplain MethodDescriptor methods} on which this chunk depends.
		 */
		public WeakChunkReference (
			final AvailObject chunk,
			final Set<AvailObject> contingentMethods)
		{
			super(chunk, RecyclingQueue);
			this.index = chunk.index();
			this.contingentMethods = contingentMethods;
		}
	}

	/**
	 * A list of {@linkplain WeakChunkReference weak chunk references} to every
	 * {@linkplain L2ChunkDescriptor level two chunk}.  The chunks are wrapped
	 * within a {@link WeakChunkReference} and placed in the list at their
	 * {@linkplain IntegerSlots#INDEX index}, which the weak chunk reference
	 * also records.  Some of the weak references may have a null {@linkplain
	 * WeakReference#get() referent}, indicating the chunk at that position has
	 * either been reclaimed or will appear on the {@link
	 * WeakChunkReference#RecyclingQueue finalization queue} shortly.
	 */
	private static final List<WeakChunkReference> AllChunksWeakly =
		new ArrayList<WeakChunkReference>(100);

	/**
	 * The special {@linkplain L2ChunkDescriptor level two chunk} that is used
	 * to interpret level one nybblecodes until a piece of {@linkplain
	 * CompiledCodeDescriptor compiled code} has been executed some number of
	 * times.
	 */
	private static AvailObject UnoptimizedChunk;


	/**
	 * Create any statically well-known instances that are needed by the Avail
	 * runtime system.
	 */
	static void createWellKnownObjects ()
	{
		WeakChunkReference.RecyclingQueue = new ReferenceQueue<AvailObject>();
		assert AllChunksWeakly.isEmpty();
		UnoptimizedChunk = new L2Translator().createChunkForFirstInvocation();
		assert AllChunksWeakly.size() == 1;
		assert UnoptimizedChunk.index() == 0;
	}

	/**
	 * Disconnect any statically well-known instances that were needed by the
	 * Avail runtime system.
	 */
	static void clearWellKnownObjects ()
	{
		UnoptimizedChunk = null;
		AllChunksWeakly.clear();
		WeakChunkReference.RecyclingQueue = null;
	}

	/**
	 * Return the special {@linkplain L2ChunkDescriptor level two chunk} that is
	 * used to interpret level one nybblecodes until a piece of {@linkplain
	 * CompiledCodeDescriptor compiled code} has been executed some threshold
	 * number of times.
	 *
	 * @return The special {@linkplain #UnoptimizedChunk unoptimized chunk}.
	 */
	public static AvailObject unoptimizedChunk ()
	{
		return UnoptimizedChunk;
	}

	/**
	 * The level two wordcode offset to which to jump when returning into a
	 * continuation that's running the {@linkplain #UnoptimizedChunk unoptimized
	 * chunk}.
	 *
	 * @return A level two wordcode offset.
	 */
	public static int offsetToContinueUnoptimizedChunk ()
	{
		// This is hard-coded, but cross-checked by
		// L2Translator#createChunkForFirstInvocation().
		return 3;
	}

	/**
	 * Return the number of times to invoke a {@linkplain CompiledCodeDescriptor
	 * compiled code} object, <em>after an invalidation</em>, before attempting
	 * to optimize it again.
	 *
	 * @return The number of invocations before post-invalidate reoptimization.
	 */
	public static int countdownForInvalidatedCode ()
	{
		return 100;
	}

	/**
	 * Return the number of times to invoke a {@linkplain CompiledCodeDescriptor
	 * compiled code} object, <em>after creation</em>, before attempting to
	 * optimize it for the first time.
	 *
	 * @return The number of invocations before initial optimization.
	 */
	public static int countdownForNewCode ()
	{
		return 20;
	}

	/**
	 * Return the number of times to invoke a {@linkplain CompiledCodeDescriptor
	 * compiled code} object, <em>after optimization</em>, before attempting to
	 * optimize it again with more effort.
	 *
	 * @return The number of invocations before attempting to improve the
	 *         optimization.
	 */
	public static int countdownForNewlyOptimizedCode ()
	{
		return 1000000000;
	}

	/**
	 * The specified chunk has just been used.  Use this fact if possible to
	 * balance an LRU cache of chunks in such a way that the least recently used
	 * ones are more likely to be evicted.
	 *
	 * @param chunk A {@linkplain L2ChunkDescriptor Level Two chunk}.
	 */
	public static void moveToHead (final @NotNull AvailObject chunk)
	{
		// Do nothing for now.
		// TODO: [MvG] Move it to the head of a ring that holds chunks weakly
		// but explicitly evicts & invalidates the oldest entry sometimes.
	}

	/**
	 * Allocate and set up a new {@linkplain L2ChunkDescriptor level two chunk}
	 * with the given information.  If {@code code} is non-null, set it up to
	 * use the new chunk for subsequent invocations.
	 *
	 * @param code
	 *            The {@linkplain CompiledCodeDescriptor code} for which to use
	 *            the new level two chunk, or null for the initial unoptimized
	 *            chunk.
	 * @param listOfLiterals
	 *            The {@link List} of literal objects used by the new chunk.
	 * @param listOfVectors
	 *            The {@link List} of vectors, each of which is a list of
	 *            {@linkplain Integer}s denoting an {@link L2ObjectRegister}.
	 * @param numObjects
	 *            The number of {@linkplain L2ObjectRegister object registers} that
	 *            this chunk will require.
	 * @param numIntegers
	 *            The number of {@linkplain L2IntegerRegister integer registers} that
	 *            this chunk will require.
	 * @param numFloats
	 *            The number of {@linkplain L2FloatRegister floating point registers}
	 *            that this chunk will require.
	 * @param theWordcodes
	 *            A {@link List} of {@linkplain Integer}s that encode the
	 *            {@linkplain L2Instruction}s to execute in place of the level
	 *            one nybblecodes.
	 * @param contingentSets
	 *            A {@link Set} of {@linkplain MethodDescriptor
	 *            methods} on which the level two chunk depends.
	 * @return
	 *            The new level two chunk.
	 */
	public static AvailObject allocate(
		final AvailObject code,
		final List<AvailObject> listOfLiterals,
		final List<List<Integer>> listOfVectors,
		final int numObjects,
		final int numIntegers,
		final int numFloats,
		final List<Integer> theWordcodes,
		final Set<AvailObject> contingentSets)
	{
		final List<AvailObject> vectorTuples =
			new ArrayList<AvailObject>(listOfVectors.size());
		for (final List<Integer> vector : listOfVectors)
		{
			final AvailObject vectorTuple =
				TupleDescriptor.fromIntegerList(vector);
			vectorTuple.makeImmutable();
			vectorTuples.add(vectorTuple);
		}
		final AvailObject vectorTuplesTuple =
			TupleDescriptor.fromCollection(vectorTuples);
		vectorTuplesTuple.makeImmutable();
		final AvailObject wordcodesTuple =
			TupleDescriptor.fromIntegerList(theWordcodes);
		wordcodesTuple.makeImmutable();
		final AvailObject chunk = mutable().create(listOfLiterals.size());
//		AvailObject.lock(chunk);
		// A new chunk starts out saved.
		chunk.setSlot(
			IntegerSlots.SAVED,
			1);
		// A new chunk starts out valid.
		chunk.setSlot(
			IntegerSlots.VALID,
			1);
		chunk.setSlot(ObjectSlots.VECTORS, vectorTuplesTuple);
		chunk.setSlot(IntegerSlots.NUM_OBJECTS, numObjects);
		chunk.setSlot(IntegerSlots.NUM_INTEGERS, numIntegers);
		chunk.setSlot(IntegerSlots.NUM_DOUBLES, numFloats);
		chunk.setSlot(ObjectSlots.WORDCODES, wordcodesTuple);
		for (int i = 1; i <= listOfLiterals.size(); i++)
		{
			chunk.setSlot(
				ObjectSlots.LITERAL_AT_,
				i,
				listOfLiterals.get(i - 1));
		}

		final int index;
		final Reference<? extends AvailObject> weaklyTypedRecycledReference =
			WeakChunkReference.RecyclingQueue.poll();
		if (weaklyTypedRecycledReference != null)
		{
			// Recycle the reference.  Nobody referred to the chunk, so it has
			// already been garbage collected and nulled from its weak
			// reference.  It may or may not have been invalidated already, so
			// clean it up if necessary.
			final WeakChunkReference oldReference =
				(WeakChunkReference)weaklyTypedRecycledReference;
			for (final AvailObject impSet
				: oldReference.contingentMethods)
			{
				impSet.removeDependentChunkIndex(oldReference.index);
			}
			oldReference.contingentMethods.clear();
			index = oldReference.index;
		}
		else
		{
			// Nothing available for recycling.  Make room for it at the end.
			index = AllChunksWeakly.size();
			AllChunksWeakly.add(null);
		}
		chunk.index(index);
		final WeakChunkReference newReference = new WeakChunkReference(
			chunk,
			contingentSets);
		AllChunksWeakly.set(chunk.index(), newReference);

		// Now that the index has been assigned, connect the dependency.  Since
		// connecting the dependency may grow some sets, make sure the Avail GC
		// (not yet implemented in Java) can be invoked safely.  To assist this,
		// make sure the code is referring to the chunk being set up, to avoid
		// having it garbage collected before we have a chance to install it.
		if (code != null)
		{
			code.startingChunk(chunk);
			code.invocationCount(
				L2ChunkDescriptor.countdownForNewlyOptimizedCode());
		}
		for (final AvailObject impSet : contingentSets)
		{
			impSet.addDependentChunkIndex(index);
		}
//		AvailObject.unlock(chunk);
		chunk.makeImmutable();
		moveToHead(chunk);
		return chunk;
	}

	/**
	 * A method has changed.  This means a method definition (or a
	 * forward or an abstract declaration) has been added or removed from the
	 * method, and the specified chunk previously expressed an
	 * interest in change notifications.  This must have been because it was
	 * optimized in a way that relied on some aspect of the available
	 * implementations (e.g., monomorphic inlining), so we need to invalidate
	 * the chunk now, so that an attempt to invoke it or return into it will be
	 * detected and converted into using the {@linkplain #UnoptimizedChunk
	 * unoptimized chunk}.  Also remove this chunk's index from all
	 * methods on which it was depending.  Do not add the chunk's
	 * reference to the reference queue, since it may still be referenced by
	 * code or continuations that need to detect that it is now invalid.
	 *
	 * @param chunkIndex The index of the chunk to invalidate.
	 */
	public static void invalidateChunkAtIndex (
		final int chunkIndex)
	{
		final WeakChunkReference ref = AllChunksWeakly.get(chunkIndex);
		assert ref.index == chunkIndex;
		final AvailObject chunk = ref.get();
		if (chunk != null)
		{
			chunk.setSlot(IntegerSlots.VALID, 0);
			chunk.setSlot(ObjectSlots.WORDCODES, TupleDescriptor.empty());
			chunk.setSlot(ObjectSlots.VECTORS, TupleDescriptor.empty());
			for (int i = chunk.variableObjectSlotsCount(); i >= 1; i--)
			{
				chunk.setSlot(
					ObjectSlots.LITERAL_AT_,
					i,
					NullDescriptor.nullObject());
			}
		}
		final Set<AvailObject> impSets = ref.contingentMethods;
		for (final AvailObject impSet : impSets)
		{
			impSet.removeDependentChunkIndex(chunkIndex);
		}
		ref.contingentMethods.clear();
	}

	/**
	 * Construct a new {@link L2ChunkDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	private L2ChunkDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link L2ChunkDescriptor}.
	 */
	private static final L2ChunkDescriptor mutable = new L2ChunkDescriptor(true);

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
	private static final L2ChunkDescriptor immutable = new L2ChunkDescriptor(false);

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
