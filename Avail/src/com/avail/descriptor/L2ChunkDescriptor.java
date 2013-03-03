/**
 * L2ChunkDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.L2ChunkDescriptor.IntegerSlots.*;
import static com.avail.descriptor.L2ChunkDescriptor.ObjectSlots.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import com.avail.annotations.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.optimizer.L2Translator;
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
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2ChunkDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The unique integer that identifies this chunk. Weak references are
		 * used to determine when it is safe to recycle an index for a new
		 * chunk.
		 */
		INDEX,

		/**
		 * A compound field that contains information about how many {@linkplain
		 * L2ObjectRegister object registers} are needed by this
		 * chunk, as well as some flags. Having the number of needed object
		 * registers stored separately allows the register list to be
		 * dynamically expanded as needed only when starting or resuming a
		 * continuation.
		 */
		NUM_OBJECTS_AND_FLAGS,

		/**
		 * A compound field containing the number of {@linkplain
		 * L2IntegerRegister integer registers} and the number of {@linkplain
		 * L2FloatRegister floating point registers} that are used by this
		 * chunk. Having this recorded separately allows the register list to
		 * be dynamically expanded as needed only when starting or resuming a
		 * continuation.
		 */
		NUM_INTEGERS_AND_DOUBLES;

		/**
		 * The number of {@linkplain L2ObjectRegister object registers} that
		 * this chunk uses (including the fixed registers).
		 */
		static final BitField NUM_OBJECTS = bitField(
			NUM_OBJECTS_AND_FLAGS,
			0,
			30);

		/**
		 * A flag indicating whether this chunk has been reached by the garbage
		 * collector in the current scavenge cycle. If it's still clear at flip
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
		 * The number of {@linkplain L2IntegerRegister integer registers} that
		 * are used by this chunk. Having this recorded separately allows the
		 * register list to be dynamically expanded as needed only when starting
		 * or resuming a continuation.
		 */
		static final BitField NUM_INTEGERS = bitField(
			NUM_INTEGERS_AND_DOUBLES,
			0,
			16);

		/**
		 * The number of {@linkplain L2FloatRegister floating point registers}
		 * that are used by this chunk. Having this recorded separately allows
		 * the register list to be dynamically expanded as needed only when
		 * starting or resuming a continuation.
		 */
		static final BitField NUM_DOUBLES = bitField(
			NUM_INTEGERS_AND_DOUBLES,
			16,
			16);

	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain L2Instruction level two instructions} encoded as a
		 * tuple of integers.
		 */
		WORDCODES,

		/**
		 * A {@linkplain TupleDescriptor tuple} of tuples of integers. Each
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

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == WORDCODES
			|| e == VECTORS
			|| e == LITERAL_AT_
			|| e == NUM_OBJECTS_AND_FLAGS;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		if (object.slot(INDEX) == 0)
		{
			aStream.append("Default chunk #0");
			return;
		}
		aStream.append("Chunk #");
		aStream.append(object.slot(INDEX));
		aStream.append("\n");
		final StringBuilder tabStream = new StringBuilder();
		for (int t = 1; t <= indent; t++)
		{
			tabStream.append("\t");
		}
		final String tabString = tabStream.toString();

		if (!object.isValid())
		{
			aStream.append(tabString);
			aStream.append("(INVALID)\n");
		}
		final A_Tuple words = object.slot(WORDCODES);
		final L2RawInstructionDescriber describer =
			new L2RawInstructionDescriber();
		for (int i = 1, limit = words.tupleSize(); i <= limit; )
		{
			aStream.append(String.format("%s\t#%-3d ", tabString, i));
			final L2Operation operation =
				L2Operation.values()[words.tupleIntAt(i)];
			i++;
			final int[] operands = new int[operation.operandTypes().length];
			for (int opIndex = 0; opIndex < operands.length; opIndex++, i++)
			{
				operands[opIndex] = words.tupleIntAt(i);
			}
			final L2RawInstruction rawInstruction =
				new L2RawInstruction(operation, operands);
			final StringBuilder tempStream = new StringBuilder(100);
			describer.describe(rawInstruction, object, tempStream);
			aStream.append(
				tempStream.toString().replace("\n", "\n\t\t" + tabString));
			aStream.append("\n");
		}
	}

	@Override @AvailMethod
	int o_Index (final AvailObject object)
	{
		return object.slot(INDEX);
	}

	@Override @AvailMethod
	void o_Index (final AvailObject object, final int value)
	{
		object.setSlot(INDEX, value);
	}

	@Override @AvailMethod
	int o_NumIntegers (final AvailObject object)
	{
		return object.slot(NUM_INTEGERS);
	}

	@Override @AvailMethod
	int o_NumDoubles (final AvailObject object)
	{
		return object.slot(NUM_DOUBLES);
	}

	@Override @AvailMethod
	int o_NumObjects (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return object.slot(NUM_OBJECTS);
			}
		}
		return object.slot(NUM_OBJECTS);
	}

	@Override @AvailMethod @Deprecated
	boolean o_IsSaved (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return object.slot(SAVED) != 0;
			}
		}
		return object.slot(SAVED) != 0;
	}

	@Override @AvailMethod @Deprecated
	void o_IsSaved (final AvailObject object, final boolean aBoolean)
	{
		if (isShared())
		{
			synchronized (object)
			{
				object.setSlot(SAVED, aBoolean ? 1 : 0);
			}
		}
		else
		{
			object.setSlot(SAVED, aBoolean ? 1 : 0);
		}
	}

	@Override @AvailMethod
	boolean o_IsValid (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return object.slot(VALID) != 0;
			}
		}
		return object.slot(VALID) != 0;
	}

	@Override @AvailMethod
	A_Tuple o_Wordcodes (final AvailObject object)
	{
		return object.mutableSlot(WORDCODES);
	}

	@Override @AvailMethod
	A_Tuple o_Vectors (final AvailObject object)
	{
		return object.mutableSlot(VECTORS);
	}

	@Override @AvailMethod
	AvailObject o_LiteralAt (final AvailObject object, final int subscript)
	{
		return object.mutableSlot(LITERAL_AT_, subscript);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return IntegerDescriptor.computeHashOfInt(object.slot(INDEX));
	}

	/**
	 * A {@link WeakChunkReference} is the mechanism by which {@linkplain
	 * L2ChunkDescriptor level two chunks} are recycled by the Java garbage
	 * collector. When a chunk is only weakly reachable (i.e., there are no
	 * strong or soft references to it), the index reserved for that chunk
	 * becomes eligible for recycling.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	static class WeakChunkReference
	extends WeakReference<AvailObject>
	{
		/**
		 * The finalization queue onto which {@linkplain L2ChunkDescriptor level
		 * two chunks}' {@linkplain WeakChunkReference weak references} will be
		 * placed upon expiration. There is no special process to remove them
		 * from here. Rather, an element of this queue is consumed when needed
		 * for {@linkplain L2ChunkDescriptor#allocate(A_RawFunction, List, List,
		 * int, int, int, List, Set) allocation} of a new chunk. If this queue
		 * is empty, a fresh index is allocated.
		 *
		 * <p>
		 * Note that this is not defined as final, since we must ensure that an
		 * invocation of the virtual machine does not interfere with subsequent
		 * invocations (for clean tests), and we construct a new {@link
		 * ReferenceQueue} as part of {@link
		 * L2ChunkDescriptor#createWellKnownObjects()}.
		 * </p>
		 */
		static @Nullable ReferenceQueue<AvailObject> recyclingQueue =
			new ReferenceQueue<AvailObject>();

		/**
		 * The {@linkplain L2ChunkDescriptor.IntegerSlots#INDEX index} of the
		 * {@linkplain L2ChunkDescriptor level two chunk} to which this
		 * reference either refers or once referred.
		 */
		final int index;

		/**
		 * The list of {@linkplain MethodDescriptor methods} on which the
		 * referent chunk depends. If one of these methods changes (due to
		 * adding or removing a {@linkplain DefinitionDescriptor method
		 * implementation}), this chunk will be immediately invalidated.
		 */
		final Set<AvailObject> contingentMethods;

		/**
		 * Construct a new {@link WeakChunkReference}.
		 *
		 * @param chunk
		 *        The chunk to be wrapped with a weak reference.
		 * @param contingentMethods
		 *        The {@linkplain MethodDescriptor methods} on which this chunk
		 *        depends.
		 */
		public WeakChunkReference (
			final AvailObject chunk,
			final Set<AvailObject> contingentMethods)
		{
			super(chunk, recyclingQueue);
			this.index = chunk.slot(INDEX);
			this.contingentMethods = contingentMethods;
		}
	}

	/**
	 * A list of {@linkplain WeakChunkReference weak chunk references} to every
	 * {@linkplain L2ChunkDescriptor level two chunk}. The chunks are wrapped
	 * within a {@link WeakChunkReference} and placed in the list at their
	 * {@linkplain IntegerSlots#INDEX index}, which the weak chunk reference
	 * also records. Some of the weak references may have a null {@linkplain
	 * WeakReference#get() referent}, indicating the chunk at that position has
	 * either been reclaimed or will appear on the {@link
	 * WeakChunkReference#recyclingQueue finalization queue} shortly.
	 */
	private static final List<WeakChunkReference> allChunksWeakly =
		new ArrayList<WeakChunkReference>(100);

	/**
	 * The {@linkplain ReentrantLock lock} that guards access to the table of
	 * {@linkplain L2ChunkDescriptor chunks}.
	 */
	private static final ReentrantLock chunksLock = new ReentrantLock();

	/**
	 * The special {@linkplain L2ChunkDescriptor level two chunk} that is used
	 * to interpret level one nybblecodes until a piece of {@linkplain
	 * CompiledCodeDescriptor compiled code} has been executed some number of
	 * times.
	 */
	private static @Nullable A_Chunk unoptimizedChunk;

	/**
	 * Return the special {@linkplain L2ChunkDescriptor level two chunk} that is
	 * used to interpret level one nybblecodes until a piece of {@linkplain
	 * CompiledCodeDescriptor compiled code} has been executed some threshold
	 * number of times.
	 *
	 * @return The special {@linkplain #unoptimizedChunk unoptimized chunk}.
	 */
	public static A_Chunk unoptimizedChunk ()
	{
		final A_Chunk chunk = unoptimizedChunk;
		assert chunk != null;
		return chunk;
	}

	/**
	 * Create any statically well-known instances that are needed by the Avail
	 * runtime system.
	 */
	static void createWellKnownObjects ()
	{
		WeakChunkReference.recyclingQueue = new ReferenceQueue<AvailObject>();
		assert allChunksWeakly.isEmpty();
		final AvailObject unoptimized =
			new L2Translator(null).createChunkForFirstInvocation().makeShared();
		assert unoptimized.slot(INDEX) == 0;
		assert allChunksWeakly.size() == 1;
		unoptimizedChunk = unoptimized;
	}

	/**
	 * Disconnect any statically well-known instances that were needed by the
	 * Avail runtime system.
	 */
	static void clearWellKnownObjects ()
	{
		unoptimizedChunk = null;
		allChunksWeakly.clear();
		WeakChunkReference.recyclingQueue = null;
	}

	/**
	 * The level two wordcode offset to which to jump when returning into a
	 * continuation that's running the {@linkplain #unoptimizedChunk unoptimized
	 * chunk}.
	 *
	 * @return A level two wordcode offset.
	 */
	public static int offsetToContinueUnoptimizedChunk ()
	{
		// This is hard-coded, but cross-checked by
		// L2Translator#createChunkForFirstInvocation().
		return 8;
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
		return 10;
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
		// TODO: [MvG] Set this to something sensible when optimization exists.
		return 1000000000;
	}

	/**
	 * The specified chunk has just been used. Use this fact if possible to
	 * balance an LRU cache of chunks in such a way that the least recently used
	 * ones are more likely to be evicted.
	 *
	 * @param chunk A {@linkplain L2ChunkDescriptor Level Two chunk}.
	 */
	public static void moveToHead (final A_BasicObject chunk)
	{
		chunksLock.lock();
		try
		{
			// Do nothing for now.
			// TODO: [MvG] Move it to the head of a ring that holds chunks
			// weakly but explicitly evicts & invalidates the oldest entry
			// sometimes.
		}
		finally
		{
			chunksLock.unlock();
		}
	}

	/**
	 * Allocate and set up a new {@linkplain L2ChunkDescriptor level two chunk}
	 * with the given information. If {@code code} is non-null, set it up to
	 * use the new chunk for subsequent invocations.
	 *
	 * @param code
	 *        The {@linkplain CompiledCodeDescriptor code} for which to use the
	 *        new level two chunk, or null for the initial unoptimized chunk.
	 * @param listOfLiterals
	 *        The {@link List} of literal objects used by the new chunk.
	 * @param listOfVectors
	 *        The {@link List} of vectors, each of which is a list of
	 *        {@linkplain Integer}s denoting an {@link L2ObjectRegister}.
	 * @param numObjects
	 *        The number of {@linkplain L2ObjectRegister object registers} that
	 *        this chunk will require.
	 * @param numIntegers
	 *        The number of {@linkplain L2IntegerRegister integer registers}
	 *        that this chunk will require.
	 * @param numFloats
	 *        The number of {@linkplain L2FloatRegister floating point
	 *        registers} that this chunk will require.
	 * @param theWordcodes
	 *        A {@link List} of {@linkplain Integer}s that encode the
	 *        {@linkplain L2Instruction}s to execute in place of the level
	 *        one nybblecodes.
	 * @param contingentSets
	 *        A {@link Set} of {@linkplain MethodDescriptor methods} on which
	 *        the level two chunk depends.
	 * @return The new level two chunk.
	 */
	public static AvailObject allocate (
		final @Nullable A_RawFunction code,
		final List<A_BasicObject> listOfLiterals,
		final List<List<Integer>> listOfVectors,
		final int numObjects,
		final int numIntegers,
		final int numFloats,
		final List<Integer> theWordcodes,
		final Set<AvailObject> contingentSets)
	{
		final List<A_BasicObject> vectorTuples =
			new ArrayList<A_BasicObject>(listOfVectors.size());
		for (final List<Integer> vector : listOfVectors)
		{
			final A_Tuple vectorTuple =
				TupleDescriptor.fromIntegerList(vector);
			vectorTuple.makeImmutable();
			vectorTuples.add(vectorTuple);
		}
		final A_Tuple vectorTuplesTuple =
			TupleDescriptor.fromList(vectorTuples);
		vectorTuplesTuple.makeImmutable();
		final A_Tuple wordcodesTuple =
			TupleDescriptor.fromIntegerList(theWordcodes);
		wordcodesTuple.makeImmutable();
		final AvailObject chunk = mutable.create(listOfLiterals.size());
		// A new chunk starts out saved and valid.
		chunk.setSlot(SAVED, 1);
		chunk.setSlot(VALID, 1);
		chunk.setSlot(VECTORS, vectorTuplesTuple);
		chunk.setSlot(NUM_OBJECTS, numObjects);
		chunk.setSlot(NUM_INTEGERS, numIntegers);
		chunk.setSlot(NUM_DOUBLES, numFloats);
		chunk.setSlot(WORDCODES, wordcodesTuple);
		for (int i = 1; i <= listOfLiterals.size(); i++)
		{
			chunk.setSlot(LITERAL_AT_, i, listOfLiterals.get(i - 1));
		}

		final int index;
		chunksLock.lock();
		try
		{
			final ReferenceQueue<AvailObject> queue =
				WeakChunkReference.recyclingQueue;
			assert queue != null;
			final Reference<? extends AvailObject> recycledReference =
				queue.poll();
			if (recycledReference != null)
			{
				// Recycle the reference. Nobody referred to the chunk, so it
				// has already been garbage collected and nulled from its weak
				// reference. It may or may not have been invalidated already,
				// so clean it up if necessary.
				final WeakChunkReference oldReference =
					(WeakChunkReference) recycledReference;
				for (final A_Method method : oldReference.contingentMethods)
				{
					method.removeDependentChunkIndex(oldReference.index);
				}
				oldReference.contingentMethods.clear();
				index = oldReference.index;
			}
			else
			{
				// Nothing available for recycling. Make room for it at the end.
				index = allChunksWeakly.size();
				allChunksWeakly.add(null);
			}
			chunk.setSlot(INDEX, index);
			final WeakChunkReference newReference = new WeakChunkReference(
				chunk,
				contingentSets);
			allChunksWeakly.set(index, newReference);
			chunk.makeImmutable();
			moveToHead(chunk);
		}
		finally
		{
			chunksLock.unlock();
		}

		// Now that the index has been assigned, connect the dependency. Since
		// connecting the dependency may grow some sets, make sure the Avail GC
		// (not yet implemented in Java) can be invoked safely. To assist this,
		// make sure the code is referring to the chunk being set up, to avoid
		// having it garbage collected before we have a chance to install it.
		if (code != null)
		{
			code.setStartingChunkAndReoptimizationCountdown(
				chunk,
				L2ChunkDescriptor.countdownForNewlyOptimizedCode());
		}
		for (final A_Method method : contingentSets)
		{
			method.addDependentChunkIndex(index);
		}

		return chunk;
	}

	/**
	 * A method has changed. This means a method definition (or a forward or an
	 * abstract declaration) has been added or removed from the method, and the
	 * specified chunk previously expressed an interest in change notifications.
	 * This must have been because it was optimized in a way that relied on some
	 * aspect of the available definitions (e.g., monomorphic inlining), so we
	 * need to invalidate the chunk now, so that an attempt to invoke it or
	 * return into it will be detected and converted into using the {@linkplain
	 * #unoptimizedChunk unoptimized chunk}. Also remove this chunk's index from
	 * all methods on which it was depending.  Do not add the chunk's reference
	 * to the reference queue, since it may still be referenced by code or
	 * continuations that need to detect that it is now invalid.
	 *
	 * @param chunkIndex The index of the chunk to invalidate.
	 */
	public static void invalidateChunkAtIndex (final int chunkIndex)
	{
		chunksLock.lock();
		try
		{
			final WeakChunkReference ref = allChunksWeakly.get(chunkIndex);
			assert ref.index == chunkIndex;
			final AvailObject chunk = ref.get();
			if (chunk != null)
			{
				chunk.setSlot(VALID, 0);
				// The empty tuple is already shared, so we don't need to share
				// it here.
				chunk.setSlot(WORDCODES, TupleDescriptor.empty());
				chunk.setSlot(VECTORS, TupleDescriptor.empty());
				// Nil is likewise shared.
				for (int i = chunk.variableObjectSlotsCount(); i >= 1; i--)
				{
					chunk.setSlot(
						LITERAL_AT_,
						i,
						NilDescriptor.nil());
				}
			}
			final Set<AvailObject> impSets = ref.contingentMethods;
			for (final A_Method method : impSets)
			{
				method.removeDependentChunkIndex(chunkIndex);
			}
			ref.contingentMethods.clear();
		}
		finally
		{
			chunksLock.unlock();
		}
	}

	/**
	 * Construct a new {@link L2ChunkDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private L2ChunkDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link L2ChunkDescriptor}. */
	private static final L2ChunkDescriptor mutable =
		new L2ChunkDescriptor(Mutability.MUTABLE);

	@Override
	L2ChunkDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link L2ChunkDescriptor}. */
	private static final L2ChunkDescriptor immutable =
		new L2ChunkDescriptor(Mutability.IMMUTABLE);

	@Override
	L2ChunkDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link L2ChunkDescriptor}. */
	private static final L2ChunkDescriptor shared =
		new L2ChunkDescriptor(Mutability.SHARED);

	@Override
	L2ChunkDescriptor shared ()
	{
		return shared;
	}
}
