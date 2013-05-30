/**
 * L2Chunk.java
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

package com.avail.interpreter.levelTwo;

import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import com.avail.annotations.*;
import com.avail.descriptor.*;
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
public final class L2Chunk
{
	/**
	 * The unique integer that identifies this chunk. Weak references are
	 * used to determine when it is safe to recycle an index for a new
	 * chunk.
	 */
	int index;

	/**
	 * The number of {@linkplain L2ObjectRegister object registers} that
	 * this chunk uses (including the fixed registers).  Having the number of
	 * needed object registers stored separately allows the register list to be
	 * dynamically expanded as needed only when starting or resuming a
	 * {@link ContinuationDescriptor continuation}.
	 */
	int numObjects;

	/**
	 * The number of {@linkplain L2IntegerRegister integer registers} that
	 * are used by this chunk. Having this recorded separately allows the
	 * register list to be dynamically expanded as needed only when starting
	 * or resuming a continuation.
	 */
	int numIntegers;

	/**
	 * The number of {@linkplain L2FloatRegister floating point registers}
	 * that are used by this chunk. Having this recorded separately allows
	 * the register list to be dynamically expanded as needed only when
	 * starting or resuming a continuation.
	 */
	int numDoubles;

	/**
	 * A flag indicating whether this chunk has been reached by the garbage
	 * collector in the current scavenge cycle. If it's still clear at flip
	 * time, the chunk is unreferenced and can be reclaimed.
	 *
	 * TODO: [MvG] This is not used by the current (2011.05.11)
	 * Avail-on-Java VM.
	 */
	boolean saved;

	/**
	 * A flag indicating whether this chunk is valid or if it has been
	 * invalidated by the addition or removal of a method signature.
	 */
	boolean valid;

	/**
	 * The sequence of {@link L2Instruction}s that make up this L2Chunk.
	 */
	@SuppressWarnings("null")
	public L2Instruction [] instructions;

	/**
	 * Answer the Avail {@linkplain PojoDescriptor pojo} associated with this
	 * L2Chunk.
	 */
	public final AvailObject chunkPojo = PojoDescriptor.newPojo(
			RawPojoDescriptor.identityWrap(this),
			PojoTypeDescriptor.forClass(this.getClass()))
		.makeShared();

	/**
	 * Answer an integer that uniquely identifies this chunk.  This allows a
	 * chunk to be invalidated given only its index and {@link
	 * #allChunksWeakly}.  In fact, the L2Chunk itself may already have been
	 * garbage collected by that point!
	 *
	 * @return The chunk's identifying index.
	 */
	public int index ()
	{
		return index;
	}

	/**
	 * Answer the number of floating point registers used by this chunk.
	 *
	 * @return The count of float registers.
	 */
	public int numDoubles ()
	{
		return numDoubles;
	}

	/**
	 * Answer the number of integer registers used by this chunk.
	 *
	 * @return The count of integer registers.
	 */
	public int numIntegers ()
	{
		return numIntegers;
	}

	/**
	 * Answer the number of object registers used by this chunk.
	 *
	 * @return The count of object registers.
	 */
	public int numObjects ()
	{
		return numObjects;
	}

	/**
	 * Answer whether this chunk is still valid.  A {@linkplain
	 * ContinuationDescriptor continuation} or {@linkplain
	 * CompiledCodeDescriptor raw function} may refer to an invalid chunk, but
	 * attempts to resume or invoke (respectively) such a chunk are detected and
	 * cause the {@link #unoptimizedChunk()} to be substituted instead.  We
	 * don't have to worry about an Interpreter holding onto a chunk when it
	 * becomes invalid, because invalidation can only happen when the runtime
	 * temporarily inhibits running Avail code, and all fibers have had their
	 * continuation reified to a level-one-coherent state.
	 *
	 * @return Whether this chunk is still valid.
	 */
	public boolean isValid ()
	{
		return valid;
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		if (index() == 0)
		{
			return "Default chunk #0";
		}
		builder.append("Chunk #");
		builder.append(index());
		builder.append("\n");
		if (!isValid())
		{
			builder.append("\t(INVALID)\n");
		}
		final L2InstructionDescriber describer =
			new L2InstructionDescriber();
		int offset = 0;
		for (final L2Instruction instruction : instructions)
		{
			builder.append(String.format("\t#%-3d ", offset));
			final StringBuilder tempStream = new StringBuilder(100);
			describer.describe(instruction, this, tempStream);
			builder.append(
				tempStream.toString().replace("\n", "\n\t\t"));
			builder.append("\n");
			offset++;
		}
		return builder.toString();
	}

	/**
	 * A {@link WeakChunkReference} is the mechanism by which {@linkplain
	 * L2Chunk level two chunks} are recycled by the Java garbage collector.
	 * When a chunk is only weakly reachable (i.e., there are no strong or soft
	 * references to it), the index reserved for that chunk becomes eligible for
	 * recycling.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	static class WeakChunkReference
	extends WeakReference<L2Chunk>
	{
		/**
		 * The finalization queue onto which {@linkplain L2Chunk level two
		 * chunks}' {@linkplain WeakChunkReference weak references} will be
		 * placed upon expiration. There is no special process to remove them
		 * from here. Rather, an element of this queue is consumed when needed
		 * for {@linkplain L2Chunk#allocate(A_RawFunction, int, int, int, List,
		 * Set) allocation} of a new chunk. If this queue is empty, a fresh
		 * index is allocated.
		 */
		static final ReferenceQueue<L2Chunk> recyclingQueue =
			new ReferenceQueue<>();

		/**
		 * The {@linkplain L2Chunk#index} of the {@linkplain L2Chunk level two
		 * chunk} to which this reference either refers or once referred.
		 */
		final int index;

		/**
		 * The list of {@linkplain MethodDescriptor methods} on which the
		 * referent chunk depends. If one of these methods changes (due to
		 * adding or removing a {@linkplain DefinitionDescriptor method
		 * implementation}), this chunk will be immediately invalidated.
		 */
		final Set<A_Method> contingentMethods;

		/**
		 * Construct a new {@link WeakChunkReference}.
		 *
		 * @param chunk
		 *        The chunk to be wrapped with a weak reference.
		 * @param contingentMethods
		 *        The {@link Set} of {@linkplain MethodDescriptor methods} on
		 *        which this chunk depends.
		 */
		public WeakChunkReference (
			final L2Chunk chunk,
			final Set<A_Method> contingentMethods)
		{
			super(chunk, recyclingQueue);
			this.index = chunk.index();
			this.contingentMethods = contingentMethods;
		}
	}

	/**
	 * A list of {@linkplain WeakChunkReference weak chunk references} to every
	 * {@linkplain L2Chunk level two chunk}. The chunks are wrapped within a
	 * {@link WeakChunkReference} and placed in the list at their {@linkplain
	 * L2Chunk#index}, which the weak chunk reference also records. Some of the
	 * weak references may have a null {@linkplain WeakReference#get()
	 * referent}, indicating the chunk at that position has either been
	 * reclaimed or will appear on the {@link WeakChunkReference#recyclingQueue
	 * finalization queue} shortly.
	 */
	private static final List<WeakChunkReference> allChunksWeakly =
		new ArrayList<>(100);

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
		return 7;
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
		return 10;
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
		// TODO: [MvG] Set this to something sensible when optimization levels
		// are implemented.
		return 1000000000;
	}

	/**
	 * The {@linkplain ReentrantLock lock} that guards access to the table of
	 * {@linkplain L2Chunk chunks}.
	 */
	private static final ReentrantLock chunksLock = new ReentrantLock();

	/**
	 * Allocate and set up a new {@linkplain L2Chunk level two chunk} with the
	 * given information. If {@code code} is non-null, set it up to use the new
	 * chunk for subsequent invocations.
	 *
	 * @param code
	 *        The {@linkplain CompiledCodeDescriptor code} for which to use the
	 *        new level two chunk, or null for the initial unoptimized chunk.
	 * @param numObjects
	 *        The number of {@linkplain L2ObjectRegister object registers} that
	 *        this chunk will require.
	 * @param numIntegers
	 *        The number of {@linkplain L2IntegerRegister integer registers}
	 *        that this chunk will require.
	 * @param numFloats
	 *        The number of {@linkplain L2FloatRegister floating point
	 *        registers} that this chunk will require.
	 * @param theInstructions
	 *        A {@link List} of {@link L2Instruction}s that prescribe what to do
	 *        in place of the level one nybblecodes.
	 * @param contingentMethods
	 *        A {@link Set} of {@linkplain MethodDescriptor methods} on which
	 *        the level two chunk depends.
	 * @return The new level two chunk.
	 */
	public static L2Chunk allocate (
		final @Nullable A_RawFunction code,
		final int numObjects,
		final int numIntegers,
		final int numFloats,
		final List<L2Instruction> theInstructions,
		final Set<A_Method> contingentMethods)
	{
		final L2Chunk chunk = create(
			numObjects,
			numIntegers,
			numFloats,
			theInstructions);
		final int index;
		chunksLock.lock();
		try
		{
			final ReferenceQueue<L2Chunk> queue =
				WeakChunkReference.recyclingQueue;
			final Reference<? extends L2Chunk> recycledReference =
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
			chunk.index = index;
			final WeakChunkReference newReference = new WeakChunkReference(
				chunk,
				contingentMethods);
			allChunksWeakly.set(index, newReference);
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
				L2Chunk.countdownForNewlyOptimizedCode());
		}
		for (final A_Method method : contingentMethods)
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
			final L2Chunk chunk = ref.get();
			if (chunk != null)
			{
				chunk.valid = false;
				// The empty tuple is already shared, so we don't need to share
				// it here.
				chunk.instructions = new L2Instruction[0];
			}
			final Set<A_Method> methods = ref.contingentMethods;
			for (final A_Method method : methods)
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
	 * Create a new {@linkplain L2Chunk level two chunk} with the given
	 * information.
	 *
	 * @param numObjects
	 * @param numIntegers
	 * @param numFloats
	 * @param theInstructions
	 * @return
	 */
	private static L2Chunk create (
		final int numObjects,
		final int numIntegers,
		final int numFloats,
		final List<L2Instruction> theInstructions)
	{
		final L2Chunk chunk = new L2Chunk();
		// A new chunk starts out saved and valid.
		chunk.saved = true;
		chunk.valid = true;
		chunk.numObjects = numObjects;
		chunk.numIntegers = numIntegers;
		chunk.numDoubles = numFloats;
		chunk.instructions =
			theInstructions.toArray(new L2Instruction[theInstructions.size()]);
		return chunk;
	}

	/**
	 * The special {@linkplain L2Chunk level two chunk} that is used to
	 * interpret level one nybblecodes until a piece of {@linkplain
	 * CompiledCodeDescriptor compiled code} has been executed some number of
	 * times.
	 */
	private static final L2Chunk unoptimizedChunk =
		L2Translator.createChunkForFirstInvocation();

	static
	{
		assert unoptimizedChunk.index() == 0;
		assert allChunksWeakly.size() == 1;
	}

	/**
	 * Return the special {@linkplain L2Chunk level two chunk} that is used to
	 * interpret level one nybblecodes until a piece of {@linkplain
	 * CompiledCodeDescriptor compiled code} has been executed some threshold
	 * number of times.
	 *
	 * @return The special {@linkplain #unoptimizedChunk unoptimized chunk}.
	 */
	public static L2Chunk unoptimizedChunk ()
	{
		return unoptimizedChunk;
	}
}
