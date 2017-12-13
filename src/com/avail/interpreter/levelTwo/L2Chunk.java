/**
 * L2Chunk.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.operation
	.L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO;
import com.avail.interpreter.levelTwo.operation.L2_TRY_PRIMITIVE;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation;
import com.avail.interpreter.primitive.controlflow
	.P_RestartContinuationWithArguments;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Translator;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

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
	 * The optimized, non-SSA {@link L2ControlFlowGraph} from which the chunk
	 * was created.  Useful for debugging.
	 */
	final L2ControlFlowGraph controlFlowGraph;

	/** The code that was translated to L2.  Null for the default (L1) chunk. */
	final @Nullable A_RawFunction code;

	/**
	 * The number of {@linkplain L2ObjectRegister object registers} that
	 * this chunk uses (including the fixed registers).  Having the number of
	 * needed object registers stored separately allows the register list to be
	 * dynamically expanded as needed only when starting or resuming a
	 * {@link ContinuationDescriptor continuation}.
	 */
	final int numObjects;

	/**
	 * The number of {@linkplain L2IntegerRegister integer registers} that
	 * are used by this chunk. Having this recorded separately allows the
	 * register list to be dynamically expanded as needed only when starting
	 * or resuming a continuation.
	 */
	final int numIntegers;

	/**
	 * The number of {@linkplain L2FloatRegister floating point registers}
	 * that are used by this chunk. Having this recorded separately allows
	 * the register list to be dynamically expanded as needed only when
	 * starting or resuming a continuation.
	 */
	final int numDoubles;

	/**
	 * The level two offset at which to start if the corresponding {@link
	 * A_RawFunction} is a primitive, and it has already been attempted and
	 * failed.  If it's not a primitive, this is the offset of the start of the
	 * code (0).
	 */
	final int offsetAfterInitialTryPrimitive;

	/**
	 * An indication of how recently this chunk has been accessed, expressed as
	 * a reference to a {@link Generation}.
	 */
	volatile @Nullable Generation generation = Generation.newest;

	public static class Generation
	{
		/**
		 * The {@link Deque} of {@link Generation}s.  New ones are added with
		 * {@link Deque#addFirst(Object)}, and older ones are removed with
		 * {@link Deque#removeLast()} (while invalidating the contained chunks).
		 */
		@GuardedBy("generationsLock")
		private static final Deque<Generation> generations = new ArrayDeque<>();

		/** The lock for accessing the {@link Deque} of {@link Generation}s. */
		private static final ReadWriteLock generationsLock =
			new ReentrantReadWriteLock();

		/**
		 * A {@link Generation} that has not yet been added to the {@link
		 * #generations} {@link Deque}.  When this becomes fuller than
		 * approximately {@link #maximumNewestGenerationSize}, queue it and
		 * create a new one.
		 */
		public static volatile Generation newest = new Generation();

		/**
		 * The maximum number of chunks to place in this generation before
		 * creating a newer one.  If the working set of chunks is larger than
		 * this, there is a risk of thrashing (invalidating and recompiling
		 * a lot of {@link L2Chunk}s), which is balanced against overconsumption
		 * of memory by chunks.
		 */
		private static final int maximumNewestGenerationSize = 300;

		/**
		 * The approximate maximum number of chunks that should exist at any
		 * time.  When there are significantly more chunks than this, the ones
		 * in the oldest generations will be invalidated.
		 */
		private static final int maximumTotalChunkCount = 2000;

		/**
		 * The weak set of {@link L2Chunk}s in this generation.
		 */
		private final Set<L2Chunk> chunks =
			Collections.synchronizedSet(
				Collections.newSetFromMap(
					new WeakHashMap<L2Chunk, Boolean>()));

		/**
		 * Record a newly created chunk in the latest generation, triggering
		 * eviction of some of the least recently used chunks if necessary.
		 *
		 * @param newChunk The new chunk to track.
		 */
		public static void addNewChunk (final L2Chunk newChunk)
		{
			newChunk.generation = newest;
			newest.chunks.add(newChunk);
			if (newest.chunks.size() > maximumNewestGenerationSize)
			{
				generationsLock.writeLock().lock();
				try
				{
					Generation lastGenerationToKeep = newest;
					generations.addFirst(newest);
					newest = new Generation();
					int liveCount = 0;
					for (final Generation gen : generations)
					{
						final int genSize = gen.chunks.size();
						liveCount += genSize;
						if (liveCount < maximumTotalChunkCount)
						{
							lastGenerationToKeep = gen;
						}
						else
						{
							break;
						}
					}
					// Remove the obsolete generations, gathering the chunks.
					final List<L2Chunk> chunksToInvalidate = new ArrayList<>();
					while (generations.getLast() != lastGenerationToKeep)
					{
						chunksToInvalidate.addAll(
							generations.removeLast().chunks);
					}
					// Remove empty generations that would otherwise be kept.
					final List<Generation> toKeep = generations.stream()
						.filter(g -> !g.chunks.isEmpty())
						.collect(toList());
					generations.clear();
					generations.addAll(toKeep);

					if (!chunksToInvalidate.isEmpty())
					{
						// Queue a task to safely invalidate the evicted chunks.
						currentRuntime().whenLevelOneSafeDo(
							FiberDescriptor.bulkL2InvalidationPriority,
							() ->
							{
								L2Chunk.invalidationLock.lock();
								try
								{
									chunksToInvalidate.forEach(
										c -> c.invalidate(
											invalidationsFromEviction));
								}
								finally
								{
									L2Chunk.invalidationLock.unlock();
								}
							});
					}
				}
				finally
				{
					generationsLock.writeLock().unlock();
				}
			}
		}

		/**
		 * {@link Statistic} for tracking the cost of invalidating chunks due to
		 * cache eviction (to limit the number of {@link L2Chunk}s in memory).
		 * */
		private static final Statistic invalidationsFromEviction =
			new Statistic(
				"(invalidation from eviction)",
				StatisticReport.L2_OPTIMIZATION_TIME);

		/**
		 * Deal with the fact that the given chunk has just been invoked,
		 * resumed, restarted, or otherwise continued.  Optimize for the most
		 * common case that the chunk is already in the newest generation, but
		 * also make it reasonably quick to move it there from an older
		 * generation.
		 *
		 * @param chunk The {@link L2Chunk} that has just been used.
		 */
		public static void usedChunk (final L2Chunk chunk)
		{
			final Generation theNewest = newest;
			final @Nullable Generation oldGen = chunk.generation;
			if (chunk.generation == theNewest)
			{
				// The chunk is already in the newest generation, which should
				// be the most common case by far.  Do nothing.
				return;
			}
			// Move the chunk to the newest generation.  Create a newer
			// generation if it fills up.
			if (oldGen != null)
			{
				oldGen.chunks.remove(chunk);
			}
			theNewest.chunks.add(chunk);
			chunk.generation = theNewest;
			if (theNewest.chunks.size() > maximumNewestGenerationSize)
			{
				generationsLock.writeLock().lock();
				try
				{
					generations.add(newest);
					newest = new Generation();
					// Even though simply using a chunk doesn't exert any cache
					// pressure, we might accumulate a bunch of empty
					// generations that simply take up space.  Be generous and
					// only bother scanning if there are so many generations
					// that there's definitely at least one empty one.
					if (generations.size() > maximumTotalChunkCount)
					{
						final List<Generation> nonemptyGenerations =
							generations.stream()
								.filter(g -> !g.chunks.isEmpty())
								.collect(toList());
						generations.clear();
						generations.addAll(nonemptyGenerations);
					}
				}
				finally
				{
					generationsLock.writeLock().unlock();
				}
			}
		}

		/**
		 * An {@link L2Chunk} has been invalidated.  Remove it from its
		 * generation.
		 *
		 * @param chunk
		 *        The invalidated {@link L2Chunk} to remove from its generation.
		 */
		public static void removeInvalidatedChunk (final L2Chunk chunk)
		{
			final @Nullable Generation gen = chunk.generation;
			if (gen != null)
			{
				gen.chunks.remove(chunk);
				chunk.generation = null;
			}
		}

		@Override
		public String toString ()
		{
			return super.toString() + " (size=" + chunks.size() + ")";
		}

	}

	/**
	 * A flag indicating whether this chunk is valid or if it has been
	 * invalidated by the addition or removal of a method signature.  It doesn't
	 * have to be {@code volatile}, since it can only be set when Avail code
	 * execution is temporarily suspended in all fibers, which involves
	 * synchronization (and therefore memory coherence) before it can start
	 * running again.
	 */
	boolean valid;

	/**
	 * The set of {@linkplain A_ChunkDependable contingent values} on which
	 * this chunk depends. If one of these changes significantly, this chunk
	 * must be invalidated (at which time this set will be emptied).
	 */
	A_Set contingentValues;

	/**
	 * The sequence of {@link L2Instruction}s that make up this L2Chunk.
	 */
	public final L2Instruction[] instructions;

	/**
	 * Answer the Avail {@linkplain PojoDescriptor pojo} associated with this
	 * L2Chunk.
	 */
	@SuppressWarnings("ThisEscapedInObjectConstruction")
	public final AvailObject chunkPojo = identityPojo(this).makeShared();

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
		if (this == unoptimizedChunk)
		{
			return "Default chunk";
		}
		final StringBuilder builder = new StringBuilder();
		if (!isValid())
		{
			builder.append("[INVALID] ");
		}
		builder.append(
			format(
				"Chunk #%08x",
				System.identityHashCode(this)));
		if (code != null)
		{
			final A_String codeName = code.methodName();
			builder.append(" for ");
			builder.append(codeName);
		}
		return builder.toString();
	}

	/**
	 * An enumeration of different ways to enter or re-enter a continuation.
	 * In the event that the continuation's chunk has been invalidated, these
	 * enumeration values indicate the offset that should be used within the
	 * default chunk.
	 */
	public enum ChunkEntryPoint
	{
		/**
		 * The {@link #unoptimizedChunk()} entry point to jump to if a primitive
		 * was attempted but failed, and we need to run the (unoptimized, L1)
		 * alternative code.
		 */
		AFTER_TRY_PRIMITIVE(1),

		/**
		 * The entry point to jump to when continuing execution of a non-reified
		 * {@link #unoptimizedChunk() unoptimized} frame after reifying its
		 * caller chain.
		 *
		 * <p>It's hard-coded, but checked against the default chunk in {@link
		 * L2Translator#L2Translator()} when that chunk is created.</p>
		 */
		AFTER_REIFICATION(3),

		/**
		 * The entry point to which to jump when returning into a continuation
		 * that's running the {@link #unoptimizedChunk()}.
		 *
		 * <p>It's hard-coded, but checked against the default chunk in {@link
		 * L2Translator#L2Translator()} when that chunk is created.</p>
		 */
		TO_RETURN_INTO(4),

		/**
		 * The entry point to which to jump when returning from an interrupt
		 * into a continuation that's running the {@link #unoptimizedChunk}.
		 *
		 * <p>It's hard-coded, but checked against the default chunk in {@link
		 * L2Translator#L2Translator()} when that chunk is created.</p>
		 */
		TO_RESUME(6),

		/**
		 * The entry point to which to jump when restarting an unoptimized
		 * {@link A_Continuation} via {@link P_RestartContinuation} or {@link
		 * P_RestartContinuationWithArguments}.  We skip the {@link
		 * L2_TRY_PRIMITIVE}, but still do the {@link
		 * L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO} so that looped functions
		 * tend to get optimized.
		 *
		 * <p>Note that we could just as easily start at 0, the entry point for
		 * <em>calling</em> an unoptimized function, but we can skip the
		 * primitive safely because primitives and labels are mutually
		 * exclusive.</p>
		 *
		 * <p>It's hard-coded, but checked against the default chunk in {@link
		 * L2Translator#L2Translator()} when that chunk is created.</p>
		 */
		TO_RESTART(1);

		/**
		 * The offset within the default chunk at which to continue if a chunk
		 * has been invalidated.
		 */
		public final int offsetInDefaultChunk;

		/**
		 * Create the enumeration value.
		 *
		 * @param offsetInDefaultChunk
		 *        An offset within the default chunk.
		 */
		ChunkEntryPoint (final int offsetInDefaultChunk)
		{
			this.offsetInDefaultChunk = offsetInDefaultChunk;
		}
	}

	/**
	 * The offset at which to start running this chunk if the code's primitive
	 * was already tried but failed.
	 *
	 * @return An index into the chunk's {@link #instructions}.
	 */
	public int offsetAfterInitialTryPrimitive ()
	{
		return offsetAfterInitialTryPrimitive;
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
		return 1_000_000_000;
	}

	/**
	 * The {@linkplain ReentrantLock lock} that protects invalidation of chunks
	 * due to {@linkplain MethodDescriptor method} changes from interfering
	 * with each other.  The alternative to a global lock seems to imply
	 * deadlock conditions.
	 */
	public static final ReentrantLock invalidationLock = new ReentrantLock();

	/**
	 * Allocate and set up a new {@code L2Chunk level two chunk} with the
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
	 * @param offsetAfterInitialTryPrimitive
	 *        The offset into my {@link #instructions} at which to
	 *        begin if this chunk's code was primitive and that primitive has
	 *        already been attempted and failed.
	 * @param theInstructions
	 *        A {@link List} of {@link L2Instruction}s that can be executed in
	 *        place of the level one nybblecodes.
	 * @param controlFlowGraph
	 *        The optimized, non-SSA {@link L2ControlFlowGraph}.  Useful for
	 *        debugging.  Eventually we'll want to capture a copy of the graph
	 *        prior to conversion from SSA to support inlining.
	 * @param contingentValues
	 *        A {@link Set} of {@linkplain MethodDescriptor methods} on which
	 *        the level two chunk depends.
	 * @return The new level two chunk.
	 */
	public static L2Chunk allocate (
		final @Nullable A_RawFunction code,
		final int numObjects,
		final int numIntegers,
		final int numFloats,
		final int offsetAfterInitialTryPrimitive,
		final List<L2Instruction> theInstructions,
		final L2ControlFlowGraph controlFlowGraph,
		final A_Set contingentValues)
	{
		final L2Chunk chunk = new L2Chunk(
			code,
			numObjects,
			numIntegers,
			numFloats,
			offsetAfterInitialTryPrimitive,
			theInstructions,
			controlFlowGraph,
			contingentValues);
		final boolean codeNotNull = code != null;
		if (codeNotNull)
		{
			code.setStartingChunkAndReoptimizationCountdown(
				chunk,
				L2Chunk.countdownForNewlyOptimizedCode());
		}
		for (final A_ChunkDependable value : contingentValues)
		{
			value.addDependentChunk(chunk);
		}
		if (codeNotNull)
		{
			Generation.addNewChunk(chunk);
		}
		return chunk;
	}

	/**
	 * Create a new {@code L2Chunk} with the given information.
	 *
	 * @param numObjects
	 *        The number of object registers needed.
	 * @param numIntegers
	 *        The number of integer registers needed.
	 * @param numFloats
	 *        The number of float registers needed.
	 * @param offsetAfterInitialTryPrimitive
	 *        The offset into my {@link #instructions} at which to
	 *        begin if this chunk's code was primitive and that primitive has
	 *        already been attempted and failed.
	 * @param instructions
	 *        The instructions to execute.
	 * @param controlFlowGraph
	 *        The optimized, non-SSA {@link L2ControlFlowGraph}.  Useful for
	 *        debugging.  Eventually we'll want to capture a copy of the graph
	 *        prior to conversion from SSA to support inlining.
	 * @param contingentValues
	 *        The set of contingent {@link A_ChunkDependable}.
	 */
	private L2Chunk (
		final @Nullable A_RawFunction code,
		final int numObjects,
		final int numIntegers,
		final int numFloats,
		final int offsetAfterInitialTryPrimitive,
		final List<L2Instruction> instructions,
		final L2ControlFlowGraph controlFlowGraph,
		final A_Set contingentValues)
	{
		// A new chunk starts out valid.
		this.valid = true;
		this.code = code;
		this.numObjects = numObjects;
		this.numIntegers = numIntegers;
		this.numDoubles = numFloats;
		this.offsetAfterInitialTryPrimitive = offsetAfterInitialTryPrimitive;
		this.instructions = instructions.toArray(
			new L2Instruction[instructions.size()]);
		this.controlFlowGraph = controlFlowGraph;
		this.contingentValues = contingentValues;
	}

	/**
	 * Something that this {@code L2Chunk} depended on has changed. This must
	 * have been because it was optimized in a way that relied on some aspect of
	 * the available definitions (e.g., monomorphic inlining), so we need to
	 * invalidate the chunk now, so that an attempt to invoke it or return into
	 * it will be detected and converted into using the {@link
	 * #unoptimizedChunk}. Also remove this chunk from the contingent set of
	 * each object on which it was depending.
	 *
	 * <p>This can only happen when L2 execution is suspended, due to a method
	 * changing (TODO[MvG] - we'll have to consider dependent nearly-constant
	 * variables changing at some point).  The {@link #invalidationLock} must be
	 * acquired by the caller to ensure safe manipulation of the dependency
	 * information.</p>
	 *
	 * <p>Note that all we do here is clear the valid flag and update the
	 * dependency information.  It's up to any re-entry points within this
	 * optimized code to determine that invalidation has happened,
	 * using the default chunk.</p>
	 *
	 * @param invalidationStatistic
	 *        The {@link Statistic} under which this invalidation should be
	 *        recorded.
	 */
	public void invalidate (final Statistic invalidationStatistic)
	{
		final long before = System.nanoTime();
		assert invalidationLock.isHeldByCurrentThread();
		valid = false;
		final A_Set contingents = contingentValues.makeImmutable();
		contingentValues = emptySet();
		for (final A_ChunkDependable value : contingents)
		{
			value.removeDependentChunk(this);
		}
		if (code != null)
		{
			// Unlink this invalid chunk from the compiled code that referred to
			// it as its entry point.  Continuations can't be efficiently
			// updated the same way, so the re-entry points have to check for
			// validity (jumping to a suitable L1 entry point instead).
			code.setStartingChunkAndReoptimizationCountdown(
				unoptimizedChunk(), countdownForInvalidatedCode());
		}
		Generation.removeInvalidatedChunk(this);
		final long after = System.nanoTime();
		// Use interpreter #0, since the invalidationLock prevents concurrent
		// updates.
		invalidationStatistic.record(after - before, 0);
	}

	/**
	 * The special {@linkplain L2Chunk level two chunk} that is used to
	 * interpret level one nybblecodes until a piece of {@linkplain
	 * CompiledCodeDescriptor compiled code} has been executed some number of
	 * times.
	 */
	private static final L2Chunk unoptimizedChunk =
		L2Translator.createChunkForFirstInvocation();

	/**
	 * Return the special {@code L2Chunk} that is used to interpret level one
	 * nybblecodes until a piece of {@linkplain CompiledCodeDescriptor compiled
	 * code} has been executed some threshold number of times and optimized.
	 *
	 * @return The special {@linkplain #unoptimizedChunk unoptimized chunk}.
	 */
	public static L2Chunk unoptimizedChunk ()
	{
		return unoptimizedChunk;
	}
}
