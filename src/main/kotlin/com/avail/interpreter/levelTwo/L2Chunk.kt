/*
 * L2Chunk.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
*
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.interpreter.levelTwo

import com.avail.AvailRuntime
import com.avail.AvailRuntimeSupport
import com.avail.builder.ModuleName
import com.avail.builder.UnresolvedDependencyException
import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.functions.A_Continuation
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.ContinuationDescriptor
import com.avail.descriptor.methods.A_ChunkDependable
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.pojos.PojoDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.execution.Interpreter.Companion.log
import com.avail.interpreter.levelTwo.L2Chunk.Generation
import com.avail.interpreter.levelTwo.operation.L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO
import com.avail.interpreter.levelTwo.operation.L2_TRY_OPTIONAL_PRIMITIVE
import com.avail.interpreter.levelTwo.register.L2BoxedRegister
import com.avail.interpreter.levelTwo.register.L2FloatRegister
import com.avail.interpreter.levelTwo.register.L2IntRegister
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation
import com.avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import com.avail.optimizer.ExecutableChunk
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L2BasicBlock
import com.avail.optimizer.L2ControlFlowGraph
import com.avail.optimizer.L2ControlFlowGraph.ZoneType
import com.avail.optimizer.StackReifier
import com.avail.optimizer.jvm.JVMChunk
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.performance.Statistic
import com.avail.performance.StatisticReport
import java.util.*
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.logging.Level
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.write

/**
 * A Level Two chunk represents an optimized implementation of a [compiled&#32;code
 * object][CompiledCodeDescriptor].
 *
 *
 * An [A_RawFunction] refers to the L2Chunk that it should run in its place.  An
 * [A_Continuation] also refers to the L2Chunk that allows the continuation to
 * be returned into, restarted, or resumed after an interrupt. The [Generation]
 * mechanism maintains approximate age information of chunks, in particular how
 * long it has been since a chunk was last used, so that the least recently used
 * chunks can be evicted when there are too many chunks in memory.
 *
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
 * @property numObjects
 *   The number of [object&#32;registers][L2BoxedRegister] that this chunk uses
 *   (including the fixed registers).  Having the number of needed object
 *   registers stored separately allows the register list to be dynamically
 *   expanded as needed only when starting or resuming a
 *   [continuation][ContinuationDescriptor].
 * @property numIntegers
 *   The number of [integer&#32;registers][L2IntRegister] that are used by this
 *   chunk. Having this recorded separately allows the register list to be
 *   dynamically expanded as needed only when starting or resuming a
 *   continuation.
 * @property numDoubles
 *   The number of [floating&#32;point registers][L2FloatRegister] that are used
 *   by this chunk. Having this recorded separately allows the register list to
 *   be dynamically expanded as needed only when starting or resuming a
 *   continuation.
 * @property offsetAfterInitialTryPrimitive
 *   The level two offset at which to start if the corresponding [A_RawFunction]
 *   is a primitive, and it has already been attempted and failed.  If it's not
 *   a primitive, this is the offset of the start of the code (0).
 * @property controlFlowGraph
 *   The optimized, non-SSA [L2ControlFlowGraph] from which the chunk was
 *   created.  Useful for debugging.
 *
 * @constructor
 * Create a new `L2Chunk` with the given information.
 *
 * @param code
 *   The [[A_RawFunction] that this is for, or `null` for the default chunk.
 * @param numObjects
 *   The number of object registers needed.
 * @param numIntegers
 *   The number of integer registers needed.
 * @param offsetAfterInitialTryPrimitive
 *   The offset into my [instructions] at which to begin if this chunk's code
 *   was primitive and that primitive has already been attempted and failed.
 * @param instructions
 *   The instructions to execute.
 * @param controlFlowGraph
 *   The optimized, non-SSA [L2ControlFlowGraph].  Useful for debugging.
 *   Eventually we'll want to capture a copy of the graph prior to conversion
 *   from SSA to support inlining.
 * @param contingentValues
 *   The set of contingent [A_ChunkDependable].
 * @param executableChunk
 *   The [JVMChunk] permanently associated with this L2Chunk.
 */
class L2Chunk private constructor(
	val code: A_RawFunction?,
	val numObjects: Int,
	val numIntegers: Int,
	val numDoubles: Int,
	private val offsetAfterInitialTryPrimitive: Int,
	instructions: List<L2Instruction>,
	private val controlFlowGraph: L2ControlFlowGraph,
	contingentValues: A_Set,
	executableChunk: JVMChunk) : ExecutableChunk
{

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
		private val chunks =
			Collections.synchronizedSet(
				Collections.newSetFromMap(WeakHashMap<L2Chunk, Boolean>()))
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

			/** The lock for accessing the [Deque] of [Generation]s.  */
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
			private const val maximumNewestGenerationSize = 300

			/**
			 * The approximate maximum number of chunks that should exist at any
			 * time.  When there are significantly more chunks than this, the
			 * ones in the oldest generations will be invalidated.
			 */
			private const val maximumTotalChunkCount = 1000

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
				newest.chunks.add(newChunk)
				if (newest.chunks.size > maximumNewestGenerationSize)
				{
					generationsLock.write {
						var lastGenerationToKeep = newest
						generations.addFirst(newest)
						newest = Generation()
						var liveCount = 0
						for (gen in generations)
						{
							val genSize = gen.chunks.size
							liveCount += genSize
							lastGenerationToKeep =
								if (liveCount < maximumTotalChunkCount)
								{
									gen
								}
								else
								{
									break
								}
						}
						// Remove the obsolete generations, gathering the chunks.
						val chunksToInvalidate =
							mutableListOf<L2Chunk>()
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
							AvailRuntime.currentRuntime().whenLevelOneSafeDo(
								FiberDescriptor.bulkL2InvalidationPriority)
							{
								invalidationLock.lock()
								try
								{
									chunksToInvalidate.forEach {
										it.invalidate(invalidationsFromEviction)
									}
								}
								finally
								{
									invalidationLock.unlock()
								}
							}
						}
					}
				}
			}

			/**
			 * [Statistic] for tracking the cost of invalidating chunks due to
			 * cache eviction (to limit the number of [L2Chunk]s in memory).
			 */
			private val invalidationsFromEviction = Statistic(
				"(invalidation from eviction)",
				StatisticReport.L2_OPTIMIZATION_TIME)

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
					// The chunk is already in the newest generation, which should
					// be the most common case by far.  Do nothing.
					return
				}
				// Move the chunk to the newest generation.  Create a newer
				// generation if it fills up.
				oldGen?.chunks?.remove(chunk)
				theNewest.chunks.add(chunk)
				chunk.generation = theNewest
				if (theNewest.chunks.size > maximumNewestGenerationSize)
				{
					generationsLock.write {
						generations.add(newest)
						newest = Generation()
						// Even though simply using a chunk doesn't exert any cache
						// pressure, we might accumulate a bunch of empty
						// generations that simply take up space.  Be generous and
						// only bother scanning if there are so many generations
						// that there's definitely at least one empty one.
						if (generations.size > maximumTotalChunkCount)
						{
							val nonemptyGenerations =
								generations.filter { it.chunks.isNotEmpty() }
							generations.clear()
							generations.addAll(nonemptyGenerations)
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
				val gen = chunk.generation
				if (gen != null)
				{
					gen.chunks.remove(chunk)
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
	 * The set of [contingent&#32;values][A_ChunkDependable] on which this chunk
	 * depends. If one of these changes significantly, this chunk must be
	 * invalidated (at which time this set will be emptied).
	 */
	var contingentValues: A_Set private set

	/**
	 * The sequence of [L2Instruction]s that make up this L2Chunk.
	 */
	@JvmField
	val instructions: Array<L2Instruction> = instructions.toTypedArray()

	/**
	 * Answer the Avail [pojo][PojoDescriptor] associated with this L2Chunk.
	 */
	val chunkPojo: AvailObject =
		RawPojoDescriptor.identityPojo(this).makeShared()

	override fun name(): String = name(code)

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
		code?.let {
			val codeName = it.methodName()
			builder.append(" for ")
			builder.append(codeName)
		}
		return builder.toString()
	}

	/**
	 * An enumeration of different ways to enter or re-enter a continuation.
	 * In the event that the continuation's chunk has been invalidated, these
	 * enumeration values indicate the offset that should be used within the
	 * default chunk.
	 *
	 * @property offsetInDefaultChunk
	 *   The offset within the default chunk at which to continue if a chunk
	 *   has been invalidated.
	 * @constructor
	 * Create the enumeration value.
	 *
	 * @param offsetInDefaultChunk
	 *   An offset within the default chunk.
	 */
	enum class ChunkEntryPoint constructor(val offsetInDefaultChunk: Int)
	{
		/**
		 * The [unoptimizedChunk] entry point to jump to if a primitive was
		 * attempted but failed, and we need to run the (unoptimized, L1)
		 * alternative code.
		 */
		AFTER_TRY_PRIMITIVE(1),

		/**
		 * The entry point to jump to when continuing execution of a non-reified
		 * [unoptimized][unoptimizedChunk] frame after reifying its caller
		 * chain.
		 *
		 * It's hard-coded, but checked against the default chunk in
		 * [createDefaultChunk] when that chunk is created.
		 */
		AFTER_REIFICATION(3),

		/**
		 * The entry point to which to jump when returning into a continuation
		 * that's running the [unoptimizedChunk].
		 *
		 * It's hard-coded, but checked against the default chunk in
		 * [createDefaultChunk] when that chunk is created.
		 */
		TO_RETURN_INTO(4),

		/**
		 * The entry point to which to jump when returning from an interrupt
		 * into a continuation that's running the [unoptimizedChunk].
		 *
		 * It's hard-coded, but checked against the default chunk in
		 * [createDefaultChunk] when that chunk is created.
		 */
		TO_RESUME(6),

		/**
		 * An unreachable entry point.
		 */
		UNREACHABLE(8),

		/**
		 * The entry point to which to jump when restarting an unoptimized
		 * [A_Continuation] via [P_RestartContinuation] or
		 * [P_RestartContinuationWithArguments].  We skip the
		 * [L2_TRY_OPTIONAL_PRIMITIVE], but still do the
		 * [L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO] so that looped
		 * functions tend to get optimized.
		 *
		 * Note that we could just as easily start at 0, the entry point for
		 * *calling* an unoptimized function, but we can skip the
		 * primitive safely because primitives and labels are mutually
		 * exclusive.
		 *
		 * It's hard-coded, but checked against the default chunk in
		 * [createDefaultChunk] when that chunk is created.
		 */
		TO_RESTART(1),

		/**
		 * The chunk containing this entry point *can't* be invalid when
		 * it's entered.  Note that continuations that are created with this
		 * entry point type don't have to have any slots filled in, and can just
		 * contain a caller, function, chunk, offset, and register dump.
		 */
		TRANSIENT(-1);
	}

	/**
	 * The offset at which to start running this chunk if the code's primitive
	 * was already tried but failed.
	 *
	 * @return
	 *   An index into the chunk's [instructions].
	 */
	fun offsetAfterInitialTryPrimitive(): Int = offsetAfterInitialTryPrimitive

	/**
	 * Answer this chunk's control flow graph.  Do not modify it.
	 *
	 * @return
	 *   This chunk's [L2ControlFlowGraph].
	 */
	fun controlFlowGraph(): L2ControlFlowGraph = controlFlowGraph

	/**
	 * An [ExecutableChunk] that implements the logic of this [L2Chunk] more
	 * directly, and should be executed instead by [runChunk].
	 */
	private val executableChunk: ExecutableChunk
	override fun runChunk(
		interpreter: Interpreter,
		offset: Int): StackReifier?
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
		return executableChunk.runChunk(interpreter, offset)
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
	 * @param invalidationStatistic
	 *   The [Statistic] under which this invalidation should be recorded.
	 */
	fun invalidate(invalidationStatistic: Statistic)
	{
		val before = AvailRuntimeSupport.captureNanos()
		assert(invalidationLock.isHeldByCurrentThread)
		isValid = false
		val contingents: A_Set = contingentValues.makeImmutable()
		contingentValues = emptySet()
		for (value in contingents)
		{
			value.removeDependentChunk(this)
		}
		code?.setStartingChunkAndReoptimizationCountdown(
			unoptimizedChunk, countdownForInvalidatedCode().toLong())
		Generation.removeInvalidatedChunk(this)
		val after = AvailRuntimeSupport.captureNanos()
		// Use interpreter #0, since the invalidationLock prevents concurrent
		// updates.
		invalidationStatistic.record(after - before, 0)
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
	fun dumpChunk(): String
	{
		val translator = JVMTranslator(
			code, name(), null, controlFlowGraph, instructions)
		val savedDebugFlag = JVMTranslator.debugJVM
		JVMTranslator.debugJVM = true
		try
		{
			translator.translate()
		}
		finally
		{
			JVMTranslator.debugJVM = savedDebugFlag
		}
		return translator.className
	}

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
		@JvmStatic
		private fun name(code: A_RawFunction?): String =
			code?.methodName()?.asNativeString() ?: "«default»"

		/**
		 * Return the number of times to invoke a
		 * [compiled&#32;code][CompiledCodeDescriptor] object, *after an
		 * invalidation*, before attempting to optimize it again.
		 *
		 * @return
		 *   The number of invocations before post-invalidate reoptimization.
		 */
		private fun countdownForInvalidatedCode(): Int = 200

		/**
		 * Return the number of times to invoke a
		 * [compiled&#32;code][CompiledCodeDescriptor] object, *after creation*,
		 * before attempting to optimize it for the first time.
		 *
		 * @return
		 *   The number of invocations before initial optimization.
		 */
		fun countdownForNewCode(): Int = 1

		/**
		 * Return the number of times to invoke a
		 * [compiled&#32;code][CompiledCodeDescriptor] object, *after
		 * optimization*, before attempting to optimize it again with more
		 * effort.
		 *
		 * @return
		 *   The number of invocations before attempting to improve the
		 *   optimization.
		 */
		// TODO: [MvG] Set this to something sensible when optimization levels
		// are implemented.
		@JvmStatic
		fun countdownForNewlyOptimizedCode(): Int = 1000000000

		/**
		 * The [lock][ReentrantLock] that protects invalidation of chunks due to
		 * [method][MethodDescriptor] changes from interfering with each other.
		 * The alternative to a global lock seems to imply deadlock conditions.
		 */
		val invalidationLock = ReentrantLock()

		/**
		 * Allocate and set up a new `L2Chunk level two chunk` with the given
		 * information. If `code` is non-null, set it up to use the new chunk
		 * for subsequent invocations.
		 *
		 * @param code
		 *   The [code][CompiledCodeDescriptor] for which to use the new level
		 *   two chunk, or null for the initial unoptimized chunk.
		 * @param numObjects
		 *   The number of [object&#32;registers][L2BoxedRegister] that this
		 *   chunk will require.
		 * @param numIntegers
		 *   The number of [integer&#32;registers][L2IntRegister] that this
		 *   chunk will require.
		 * @param numFloats
		 *   The number of [floating&#32;point&#32;registers][L2FloatRegister]
		 *   that this chunk will require.
		 * @param offsetAfterInitialTryPrimitive
		 *   The offset into my [instructions] at which to begin if this chunk's
		 *   code was primitive and that primitive has already been attempted
		 *   and failed.
		 * @param theInstructions
		 *   A [List] of [L2Instruction]s that can be executed in place of the
		 *   level one nybblecodes.
		 * @param controlFlowGraph
		 *   The optimized, non-SSA [L2ControlFlowGraph].  Useful for debugging.
		 *   Eventually we'll want to capture a copy of the graph prior to
		 *   conversion from SSA to support inlining.
		 * @param contingentValues
		 *   A [Set] of [methods][MethodDescriptor] on which the level two chunk
		 *   depends.
		 * @return
		 *   The new level two chunk.
		 */
		@JvmStatic
		fun allocate(
			code: A_RawFunction?,
			numObjects: Int,
			numIntegers: Int,
			numFloats: Int,
			offsetAfterInitialTryPrimitive: Int,
			theInstructions: List<L2Instruction>,
			controlFlowGraph: L2ControlFlowGraph,
			contingentValues: A_Set): L2Chunk
		{
			assert(offsetAfterInitialTryPrimitive >= 0)
			var sourceFileName: String? = null
			code?.let {
				val module = it.module()
				if (!module.equalsNil())
				{
					try
					{
						val resolved =
							AvailRuntime.currentRuntime().moduleNameResolver()
								.resolve(
									ModuleName(
										module.moduleName().asNativeString()),
									null)
						sourceFileName = resolved.sourceReference.path
					}
					catch (e: UnresolvedDependencyException)
					{
						// Maybe the file was deleted.  Play nice.
					}
				}
			}
			val jvmTranslator = JVMTranslator(
				code,
				name(code),
				sourceFileName,
				controlFlowGraph,
				theInstructions.toTypedArray())
			jvmTranslator.translate()
			val chunk = L2Chunk(
				code,
				numObjects,
				numIntegers,
				numFloats,
				offsetAfterInitialTryPrimitive,
				theInstructions,
				controlFlowGraph,
				contingentValues,
				jvmTranslator.jvmChunk())
			code?.setStartingChunkAndReoptimizationCountdown(
				chunk, countdownForNewlyOptimizedCode().toLong())
			for (value in contingentValues)
			{
				value.addDependentChunk(chunk)
			}
			code?.let { Generation.addNewChunk(chunk) }
			return chunk
		}

		/**
		 * The special [level&#32;two&#32;chunk][L2Chunk] that is used to
		 * interpret level one nybblecodes until a piece of
		 * [compiled&#32;code][CompiledCodeDescriptor] has been executed some
		 * number of times (specified in [countdownForNewCode]).
		 */
		@JvmField
		@ReferencedInGeneratedCode
		val unoptimizedChunk = createDefaultChunk()

		/**
		 * Create a default `L2Chunk` that decrements a counter in an invoked
		 * [A_RawFunction], optimizing it into a new chunk when it hits zero,
		 * otherwise interpreting the raw function's nybblecodes.
		 *
		 * @return
		 *   An `L2Chunk` to use for code that has not yet been translated to
		 *   level two.
		 */
		private fun createDefaultChunk(): L2Chunk
		{
			val returnFromCallZone =
				ZoneType.PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
					"Return into L1 reified continuation from call")
			val resumeAfterInterruptZone =
				ZoneType.PROPAGATE_REIFICATION_FOR_INVOKE.createZone(
					"Resume L1 reified continuation after interrupt")
			val initialBlock = L2BasicBlock("Default entry")
			val reenterFromRestartBlock = L2BasicBlock("Default restart")
			val loopBlock = L2BasicBlock("Default loop", true, null)
			val reenterFromCallBlock =
				L2BasicBlock(
					"Default return from call",
					false,
					returnFromCallZone)
			val reenterFromInterruptBlock =
				L2BasicBlock(
					"Default reentry from interrupt",
					false,
					resumeAfterInterruptZone)
			val unreachableBlock = L2BasicBlock("Unreachable")
			val controlFlowGraph =
				L1Translator.generateDefaultChunkControlFlowGraph(
					initialBlock,
					reenterFromRestartBlock,
					loopBlock,
					reenterFromCallBlock,
					reenterFromInterruptBlock,
					unreachableBlock)
			val instructions = mutableListOf<L2Instruction>()
			controlFlowGraph.generateOn(instructions)
			val defaultChunk =
				allocate(
					null,
					0,
					0,
					0,
					reenterFromRestartBlock.offset(),
					instructions,
					controlFlowGraph,
					emptySet())
			assert(initialBlock.offset() == 0)
			assert(reenterFromRestartBlock.offset()
				   == ChunkEntryPoint.TO_RESTART.offsetInDefaultChunk)
			assert(loopBlock.offset() == 3)
			assert(reenterFromCallBlock.offset()
				   == ChunkEntryPoint.TO_RETURN_INTO.offsetInDefaultChunk)
			assert(reenterFromInterruptBlock.offset()
				   == ChunkEntryPoint.TO_RESUME.offsetInDefaultChunk)
			assert(unreachableBlock.offset()
				   == ChunkEntryPoint.UNREACHABLE.offsetInDefaultChunk)
			return defaultChunk
		}
	}
	init
	{
		// A new chunk starts out valid.
		this.contingentValues = contingentValues
		this.executableChunk = executableChunk
	}
}
