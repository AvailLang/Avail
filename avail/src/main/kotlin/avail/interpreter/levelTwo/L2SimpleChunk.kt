/*
 * L2SimpleChunk.kt
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

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.CompiledCodeDescriptor
import avail.descriptor.methods.A_ChunkDependable
import avail.descriptor.methods.MethodDescriptor
import avail.descriptor.sets.A_Set
import avail.interpreter.levelTwoSimple.L2SimpleExecutableChunk
import avail.interpreter.levelTwoSimple.L2SimpleInstruction
import avail.optimizer.OptimizationLevel
import avail.optimizer.jvm.JVMChunk
import avail.optimizer.jvm.JVMTranslator

/**
 * A Level Two chunk represents a simply optimized implementation of an
 * [A_RawFunction].  It's composed of [L2SimpleInstruction]s, and doesn't have
 * any deep optimizations.  It does, however, attempt to eliminate dispatches
 * for monomorphic call sites, as well as return type checks, whenever possible.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @param instructions
 *   The [L2SimpleInstruction]s to execute.
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
 * @param contingentValues
 *   The set of [contingent&#32;values][A_ChunkDependable] on which this chunk
 *   depends. If one of these changes significantly, this chunk must be
 *   invalidated (at which time this set will be emptied).
 * @param executableChunk
 *   The [L2SimpleExecutableChunk] permanently associated with this
 *   [L2SimpleChunk].
 */
class L2SimpleChunk private constructor(
	code: A_RawFunction,
	offsetAfterInitialTryPrimitive: Int,
	override val instructions: List<L2SimpleInstruction>,
	contingentValues: A_Set,
	override val executableChunk: L2SimpleExecutableChunk
) : L2Chunk(code, offsetAfterInitialTryPrimitive, contingentValues)
{
	/**
	 * Dump the chunk to disk for debugging. This is expected to be called
	 * directly from the Kotlin debugger, and should result in the production of
	 * three files: `JVMChunk_«uuid».l1`, `JVMChunk_«uuid».l2`, and
	 * `JVMChunk_«uuid».class`. This momentarily sets the
	 * [JVMTranslator.debugJVM] flag to `true`, but restores it to its original
	 * value on return.
	 *
	 * NOTE: [L2SimpleChunk] currently does not dump anything.
	 *
	 * @return
	 *   The base name, i.e., `JVMChunk_«uuid»`, to allow location of the
	 *   generated files.
	 */
	@Suppress("unused")
	override fun dumpChunk(): String
	{
		// Do nothing for now.
		return "L2SimpleChunk has no writable form"
	}

	companion object
	{
		/**
		 * Allocate and set up a new [L2SimpleChunk] with the given information.
		 * If [code] is non-null, set it up to use the new chunk for subsequent
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
		 *   A [List] of [L2SimpleInstruction]s that can be executed in place of
		 *   the level one nybblecodes.
		 * @param contingentValues
		 *   A [Set] of [methods][MethodDescriptor] on which the level two chunk
		 *   depends.
		 * @return
		 *   The new level two chunk.
		 */
		fun allocate(
			code: A_RawFunction,
			offsetAfterInitialTryPrimitive: Int,
			theInstructions: List<L2SimpleInstruction>,
			contingentValues: A_Set,
			nextOptimizationLevel: OptimizationLevel
		): L2SimpleChunk
		{
			val chunk = L2SimpleChunk(
				code,
				offsetAfterInitialTryPrimitive,
				theInstructions,
				contingentValues,
				L2SimpleExecutableChunk(
					code,
					theInstructions.toTypedArray(),
					nextOptimizationLevel))
			contingentValues.forEach { it.addDependentChunk(chunk) }
			Generation.addNewChunk(chunk)
			return chunk
		}
	}
}
