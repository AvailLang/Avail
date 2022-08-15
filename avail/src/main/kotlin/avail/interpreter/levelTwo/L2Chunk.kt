/*
 * L2Chunk.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.interpreter.levelTwo

import avail.AvailRuntime
import avail.AvailRuntimeSupport
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.setStartingChunkAndReoptimizationCountdown
import avail.descriptor.functions.CompiledCodeDescriptor
import avail.descriptor.methods.A_ChunkDependable
import avail.descriptor.methods.MethodDescriptor
import avail.descriptor.pojos.PojoDescriptor
import avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.levelTwo.L2Chunk.Generation
import avail.interpreter.levelTwo.L2Chunk.InvalidationReason.EVICTION
import avail.interpreter.levelTwo.L2JVMChunk.Companion.unoptimizedChunk
import avail.optimizer.ExecutableChunk
import avail.optimizer.jvm.JVMChunk
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.performance.Statistic
import avail.performance.StatisticReport.L2_OPTIMIZATION_TIME
import avail.utility.safeWrite
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.Collections.synchronizedSet
import java.util.Deque
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.logging.Level
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.withLock

/**
 * A Level Two chunk represents an optimized implementation of a
 * [compiled&#32;code&#32;object][CompiledCodeDescriptor].
 *
 * An [A_RawFunction] refers to the L2Chunk that it should run in its place.  An
 * [A_Continuation] also refers to the L2Chunk that allows the continuation to
 * be returned into, restarted, or resumed after an interrupt. The [Generation]
 * mechanism maintains approximate age information of chunks, in particular how
 * long it has been since a chunk was last used, so that the least recently used
 * chunks can be evicted when there are too many chunks in memory.
 *
 * A chunk also keeps track of the methods that it depends on, and the methods
 * keep track of which chunks depend on them.  New method definitions can be
 * added – or existing ones removed – only while all fiber execution is paused.
 * At this time, the chunks that depend on the changed method are marked as
 * invalid.  Each [A_RawFunction] associated (1:1) with an invalidated chunk has
 * its [A_RawFunction.startingChunk] reset to the default chunk.  Existing
 * continuations may still be referring to the invalid chunk – but not Java call
 * frames, since all fibers are paused.  When resuming a continuation, its
 * chunk's validity is immediately checked, and if it's invalid, the default
 * chunk is resumed at a suitable entry point instead.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property code
 *   The code that was translated to L2.  Null for the default (L1) chunk.
 * @property offsetAfterInitialTryPrimitive
 *   The level two offset at which to start if the corresponding [A_RawFunction]
 *   is a primitive, and it has already been attempted and failed.  If it's not
 *   a primitive, this is the offset of the start of the code (0).
 * @property contingentValues
 *   The set of [contingent&#32;values][A_ChunkDependable] on which this chunk
 *   depends. If one of these changes significantly, this chunk must be
 *   invalidated (at which time this set will be emptied).
 *
 * @constructor
 * Create a new [L2Chunk] with the given information.
 */
abstract class L2Chunk protected constructor(
	val code: A_RawFunction?,
	val offsetAfterInitialTryPrimitive: Int,
	private var contingentValues: A_Set)
{
	/** The [ExecutableChunk] to run. */
	abstract val executableChunk: ExecutableChunk

	/** The [L2AbstractInstruction]s constituting this chunk. */
	abstract val instructions: List<L2AbstractInstruction>

	/** The [WeakReference] that points to this [L2Chunk]. */
	@Suppress("LeakingThis")
	val weakReference = WeakReference(this)

	/**
	 * An indication of how recently this chunk has been accessed, expressed as
	 * a reference to a [Generation].
	 */
	@Volatile
	var generation: Generation? = Generation.newest

	/**
	 * A group of chunks with approximately equal most-recent access time.
	 */
	class Generation
	{
		/**
		 * The weak set of [L2Chunk]s in this generation.
		 */
		private val chunks = synchronizedSet(HashSet<WeakReference<L2Chunk>>())

		override fun toString(): String
		{
			return super.toString() + " (size=" + chunks.size + ")"
		}

		companion object
		{
			/**
			 * The [Deque] of [Generation]s.  New ones are added with
			 * [Deque.addFirst], and older ones are removed with
			 * [Deque.removeLast] (while invalidating the contained chunks).
			 */
			@GuardedBy("generationsLock")
			private val generations: Deque<Generation> = ArrayDeque()

			/** The lock for accessing the [Deque] of [Generation]s. */
			private val generationsLock = ReentrantReadWriteLock()

			/**
			 * A [Generation] that has not yet been added to the [generations]
			 * [Deque].  When this becomes fuller than approximately
			 * [maximumNewestGenerationSize], queue it and create a new one.
			 */
			@Volatile
			var newest = Generation()

			/**
			 * The maximum number of chunks to place in this generation before
			 * creating a newer one.  If the working set of chunks is larger
			 * than this, there is a risk of thrashing (invalidating and
			 * recompiling a lot of [L2Chunk]s), which is balanced against
			 * over-consumption of memory by chunks.
			 */
			private const val maximumNewestGenerationSize = 1_000

			/**
			 * The approximate maximum number of chunks that should exist at any
			 * time.  When there are significantly more chunks than this, the
			 * ones in the oldest generations will be invalidated.
			 */
			private const val maximumTotalChunkCount = 10_000

			/**
			 * Record a newly created chunk in the latest generation, triggering
			 * eviction of some of the least recently used chunks if necessary.
			 *
			 * @param newChunk
			 *   The new chunk to track.
			 */
			fun addNewChunk(newChunk: L2Chunk)
			{
				newChunk.generation = newest
				newest.chunks.add(newChunk.weakReference)
				if (newest.chunks.size > maximumNewestGenerationSize)
				{
					generationsLock.safeWrite {
						var lastGenerationToKeep = newest
						generations.addFirst(newest)
						newest = Generation()
						var liveCount = 0
						for (gen in generations)
						{
							val genSize = gen.chunks.size
							liveCount += genSize
							if (liveCount >= maximumTotalChunkCount) break
							lastGenerationToKeep = gen
						}
						// Remove the obsolete generations, gathering the chunks.
						val chunksToInvalidate =
							mutableListOf<WeakReference<L2Chunk>>()
						while (generations.last !== lastGenerationToKeep)
						{
							chunksToInvalidate.addAll(
								generations.removeLast().chunks)
						}
						// Remove empty generations that would otherwise be kept.
						val toKeep =
							generations.filter { it.chunks.isNotEmpty() }
						generations.clear()
						generations.addAll(toKeep)
						if (chunksToInvalidate.isNotEmpty())
						{
							// Queue a task to safely invalidate the evicted
							// chunks.
							AvailRuntime.currentRuntime().whenSafePointDo(
								FiberDescriptor.bulkL2InvalidationPriority)
							{
								invalidationLock.withLock {
									chunksToInvalidate.forEach {
										it.get()?.invalidate(EVICTION)
									}
								}
							}
						}
					}
				}
			}

			/**
			 * Deal with the fact that the given chunk has just been invoked,
			 * resumed, restarted, or otherwise continued.  Optimize for the
			 * most common case that the chunk is already in the newest
			 * generation, but also make it reasonably quick to move it there
			 * from an older generation.
			 *
			 * @param chunk
			 *   The [L2Chunk] that has just been used.
			 */
			fun usedChunk(chunk: L2Chunk)
			{
				val theNewest = newest
				val oldGen = chunk.generation
				if (oldGen === theNewest)
				{
					// The chunk is already in the newest generation, which
					// should be the most common case by far.  Do nothing.
					return
				}
				// Move the chunk to the newest generation.  Create a newer
				// generation if it fills up.
				oldGen?.run { chunks.remove(chunk.weakReference) }
				theNewest.chunks.add(chunk.weakReference)
				chunk.generation = theNewest
				if (theNewest.chunks.size > maximumNewestGenerationSize)
				{
					generationsLock.safeWrite {
						// Now that we have the lock, make sure a new generation
						// hasn't been introduced by some other thread.
						if (theNewest == newest)
						{
							generations.add(newest)
							newest = Generation()
							// Even though simply using a chunk doesn't exert
							// any cache pressure, we might accumulate a bunch
							// of empty generations that simply take up space.
							// Be generous and only bother scanning if there are
							// so many generations that there's definitely at
							// least one empty one.
							if (generations.size > maximumTotalChunkCount)
							{
								generations.removeIf { it.chunks.isEmpty() }
							}
						}
					}
				}
			}

			/**
			 * An [L2Chunk] has been invalidated. Remove it from its generation.
			 *
			 * @param chunk
			 *   The invalidated [L2Chunk] to remove from its generation.
			 */
			fun removeInvalidatedChunk(chunk: L2Chunk)
			{
				chunk.generation?.let {
					it.chunks.remove(chunk.weakReference)
					chunk.generation = null
				}
			}
		}
	}

	/**
	 * A flag indicating whether this chunk is valid or if it has been
	 * invalidated by the addition or removal of a method signature.  It doesn't
	 * have to be `volatile`, since it can only be set when Avail code
	 * execution is temporarily suspended in all fibers, which involves
	 * synchronization (and therefore memory coherence) before it can start
	 * running again.
	 */
	@get:ReferencedInGeneratedCode
	var isValid = true
		private set

	/**
	 * Answer the Avail [pojo][PojoDescriptor] associated with this L2Chunk.
	 */
	@Suppress("LeakingThis")
	val chunkPojo: AvailObject = identityPojo(this).makeShared()

	fun name(): String = name(code)

	override fun toString(): String
	{
		if (this == unoptimizedChunk)
		{
			return "Default chunk"
		}
		val builder = StringBuilder()
		if (!isValid)
		{
			builder.append("[INVALID] ")
		}
		builder.append(String.format(
			"Chunk #%08x",
			System.identityHashCode(this)))
		code?.run {
			builder.append(" for $methodName")
		}
		return builder.toString()
	}

	/**
	 * Called just before running the [JVMChunk] inside this [L2Chunk].
	 * This gives the opportunity for logging the chunk execution.
	 *
	 * @param offset
	 *   The L2 offset at which we're about to start or continue running this
	 *   chunk.
	 */
	fun beforeRunChunk(offset: Int)
	{
		if (Interpreter.debugL2)
		{
			log(
				Interpreter.loggerDebugL2,
				Level.INFO,
				"Running chunk {0} at offset {1}.",
				name(),
				offset)
		}
	}

	/**
	 * An enumeration of reasons why a chunk might be invalidated.
	 *
	 * @constructor
	 *
	 * @property countdownToNextOptimization
	 *   The number of invocations that must happen after this invalidation
	 *   before the code will be optimized into another chunk.
	 */
	enum class InvalidationReason constructor(
		val countdownToNextOptimization: Long)
	{
		/**
		 * The chunk is being invalidated because a method it depends on has
		 * changed.
		 */
		DEPENDENCY_CHANGED(200),

		/**
		 * The chunk is being invalidated due to it being evicted due to too
		 * many chunks being in existence.
		 */
		EVICTION(20000),

		/** The chunk is being invalidated to collect code coverage stats. */
		CODE_COVERAGE(200);

		/**
		 * [Statistic] for tracking the cost of invalidating chunks due to this
		 * reason.
		 */
		val statistic = Statistic(
			L2_OPTIMIZATION_TIME, "(invalidation from $name)")
	}

	/**
	 * Something that this `L2Chunk` depended on has changed. This must have
	 * been because it was optimized in a way that relied on some aspect of the
	 * available definitions (e.g., monomorphic inlining), so we need to
	 * invalidate the chunk now, so that an attempt to invoke it or return into
	 * it will be detected and converted into using the [unoptimizedChunk]. Also
	 * remove this chunk from the contingent set of each object on which it was
	 * depending.
	 *
	 * This can only happen when L2 execution is suspended, due to a method
	 * changing (TODO`MvG` - we'll have to consider dependent nearly-constant
	 * variables changing at some point).  The [invalidationLock] must be
	 * acquired by the caller to ensure safe manipulation of the dependency
	 * information.
	 *
	 * Note that all we do here is clear the valid flag and update the
	 * dependency information.  It's up to any re-entry points within this
	 * optimized code to determine that invalidation has happened,
	 * using the default chunk.
	 *
	 * @param reason
	 *   The [InvalidationReason] that indicates why this invalidation is
	 *   happening.
	 */
	fun invalidate(reason: InvalidationReason)
	{
		val before = AvailRuntimeSupport.captureNanos()
		assert(invalidationLock.isHeldByCurrentThread)
		AvailRuntime.currentRuntime().assertInSafePoint()
		assert(this !== unoptimizedChunk)
		isValid = false
		val contingents: A_Set = contingentValues.makeImmutable()
		contingentValues = emptySet
		for (value in contingents)
		{
			value.removeDependentChunk(this)
		}
		code?.setStartingChunkAndReoptimizationCountdown(
			unoptimizedChunk, reason.countdownToNextOptimization)
		Generation.removeInvalidatedChunk(this)
		val after = AvailRuntimeSupport.captureNanos()
		// Use interpreter #0, since the invalidationLock prevents concurrent
		// updates.
		reason.statistic.record(after - before, 0)
	}

	/**
	 * Dump the chunk to disk for debugging. This is expected to be called
	 * directly from the debugger, and should result in the production of three
	 * files: `JVMChunk_«uuid».l1`, `JVMChunk_«uuid».l2`, and
	 * `JVMChunk_«uuid».class`. This momentarily sets the
	 * [JVMTranslator.debugJVM] flag to `true`, but restores it to its original
	 * value on return.
	 *
	 * @return
	 *   The base name, i.e., `JVMChunk_«uuid»`, to allow location of the
	 *   generated files.
	 */
	@Suppress("unused")
	abstract fun dumpChunk(): String

	companion object
	{
		/**
		 * Answer a descriptive (non-unique) name for the specified
		 * [function][A_RawFunction].
		 *
		 * @param code
		 *   An arbitrary function, or `null` for the default `L2Chunk`.
		 * @return
		 *   The effective name of the function.
		 */
		fun name(code: A_RawFunction?): String =
			code?.methodName?.asNativeString() ?: "«default»"

		/**
		 * Each time an [A_RawFunction] is found to be the running code for some
		 * interpreter during periodic polling, atomically decrease its
		 * countdown by this amount, avoiding going below one (`1`).
		 *
		 * This temporal signal should be more effective at deciding what to
		 * optimize than just counting the number of times the code is called.
		 */
		const val decrementForPolledActiveCode: Long = 1000

		/**
		 * The [lock][ReentrantLock] that protects invalidation of chunks due to
		 * [method][MethodDescriptor] changes from interfering with each other.
		 * The alternative to a global lock seems to imply deadlock conditions.
		 */
		val invalidationLock = ReentrantLock()
	}
}
