/*
 * A_ChunkDependable.kt
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
package avail.descriptor.methods

import avail.descriptor.representation.A_BasicObject
import avail.interpreter.levelTwo.L2Chunk

/**
 * [A_ChunkDependable] is an interface that specifies behavior specific to
 * values on which [chunks][L2Chunk] can depend.  When those objects change in
 * some way, dependent chunks must be invalidated.  The rate of change of such
 * objects is expected to be very low compared to the number of times the
 * dependent chunks are invoked.
 *
 * @author Todd L Smith <&lt;>todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_ChunkDependable : A_BasicObject {
	/**
	 * Add the specified [chunk][L2Chunk] to the receiver's set of dependent
	 * chunks.
	 *
	 * @param chunk
	 *   The chunk to add.
	 */
	fun addDependentChunk(chunk: L2Chunk)

	/**
	 * Remove the specified [chunk][L2Chunk] from the receiver's set of
	 * dependent chunks.
	 *
	 * @param chunk
	 *   The chunk to remove.
	 */
	fun removeDependentChunk(chunk: L2Chunk)
}
