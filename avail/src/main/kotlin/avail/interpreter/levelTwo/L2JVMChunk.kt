/*
 * L2JVMChunk.kt
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
import avail.builder.ModuleName
import avail.builder.UnresolvedDependencyException
import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.functions.CompiledCodeDescriptor
import avail.descriptor.methods.A_ChunkDependable
import avail.descriptor.methods.MethodDescriptor
import avail.descriptor.module.A_Module.Companion.moduleNameNative
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.interpreter.levelTwo.L2Chunk.Generation
import avail.interpreter.levelTwo.operation.L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO
import avail.interpreter.levelTwo.operation.L2_TRY_OPTIONAL_PRIMITIVE
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import avail.optimizer.L1Translator
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2ControlFlowGraph.ZoneType
import avail.optimizer.OptimizationLevel
import avail.optimizer.jvm.JVMChunk
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.jvm.ReferencedInGeneratedCode

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
 * @property controlFlowGraph
 *   The optimized, non-SSA [L2ControlFlowGraph] from which the chunk was
 *   created.  Useful for debugging.
 * @property contingentValues
 *   The set of [contingent&#32;values][A_ChunkDependable] on which this chunk
 *   depends. If one of these changes significantly, this chunk must be
 *   invalidated (at which time this set will be emptied).
 * @property executableChunk
 *   The [JVMChunk] permanently associated with this L2Chunk.
 *
 * @constructor
 * Create a new `L2Chunk` with the given information.
 *
 * @param code
 *   The [A_RawFunction] that this is for, or `null` for the default chunk.
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
 *   The set of [contingent&#32;values][A_ChunkDependable] on which this chunk
 *   depends. If one of these changes significantly, this chunk must be
 *   invalidated (at which time this set will be emptied).
 * @param executableChunk
 *   The [JVMChunk] permanently associated with this L2Chunk.
 */
class L2JVMChunk private
constructor(
	code: A_RawFunction?,
	offsetAfterInitialTryPrimitive: Int,
	override val instructions: List<L2Instruction>,
	private val controlFlowGraph: L2ControlFlowGraph,
	contingentValues: A_Set,
	override val executableChunk: JVMChunk
) : L2Chunk(code, offsetAfterInitialTryPrimitive, contingentValues)
{
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
		@Suppress("unused")
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
	 * Answer this chunk's control flow graph.  Do not modify it.
	 *
	 * @return
	 *   This chunk's [L2ControlFlowGraph].
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun controlFlowGraph(): L2ControlFlowGraph = controlFlowGraph

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
	override fun dumpChunk(): String
	{
		val translator = JVMTranslator(
			code, name, null, controlFlowGraph, instructions)
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
		 * Allocate and set up a new [L2JVMChunk] with the given information. If
		 * [code] is non-null, set it up to use the new chunk for subsequent
		 * invocations.
		 *
		 * @param code
		 *   The [code][CompiledCodeDescriptor] for which to use the new level
		 *   two chunk, or null for the initial unoptimized chunk.
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
		fun allocate(
			code: A_RawFunction?,
			offsetAfterInitialTryPrimitive: Int,
			theInstructions: List<L2Instruction>,
			controlFlowGraph: L2ControlFlowGraph,
			contingentValues: A_Set): L2JVMChunk
		{
			assert(offsetAfterInitialTryPrimitive >= 0)
			var sourceFileName: String? = null
			code?.let {
				val module = it.module
				if (module.notNil)
				{
					try
					{
						val resolved =
							AvailRuntime.currentRuntime().moduleNameResolver
								.resolve(
									ModuleName(module.moduleNameNative),
									null)
						sourceFileName =
							resolved.resolverReference.uri.toString()
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
				theInstructions)
			jvmTranslator.translate()
			val chunk = L2JVMChunk(
				code,
				offsetAfterInitialTryPrimitive,
				theInstructions,
				controlFlowGraph,
				contingentValues,
				jvmTranslator.jvmChunk())
			for (value in contingentValues)
			{
				value.addDependentChunk(chunk)
			}
			code?.let { Generation.addNewChunk(chunk) }
			return chunk
		}

		/**
		 * Create a default `L2Chunk` that decrements a counter in an invoked
		 * [A_RawFunction], optimizing it into a new chunk when it hits zero,
		 * otherwise interpreting the raw function's nybblecodes.
		 *
		 * @return
		 *   An `L2Chunk` to use for code that has not yet been translated to
		 *   level two.
		 */
		private fun createDefaultChunk(): L2JVMChunk
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
			val unreachableBlock = L2BasicBlock("unreachable")
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
			val defaultChunk = allocate(
				null,
				reenterFromRestartBlock.offset(),
				instructions,
				controlFlowGraph,
				emptySet
			)
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

		/**
		 * The special [level&#32;two&#32;chunk][L2JVMChunk] that is used to
		 * interpret level one nybblecodes until a piece of
		 * [compiled&#32;code][CompiledCodeDescriptor] has been executed some
		 * number of times (See [OptimizationLevel.countdown]).
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		val unoptimizedChunk = createDefaultChunk()
	}
}
