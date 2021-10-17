/*
 * Frame.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package avail.optimizer.values

import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.CompiledCodeDescriptor
import avail.interpreter.levelTwo.L2Chunk

/**
 * An abstract representation of an invocation.  Note that this is not itself an
 * [L2SemanticValue], but is used by some specific kinds of semantic values.
 * The outermost `Frame` has an [outerFrame] of `null`, and all other frames
 * have a non-null outer frame.  Frames compare by identity.
 *
 * @property outerFrame
 *   The frame that was active at the site of the invocation that this frame
 *   represents.
 * @property code
 *   The actual [raw&#32;function][CompiledCodeDescriptor] that's associated
 *   with semantic values tied to this frame.
 * @property debugName
 *   The symbolic name to use to describe this frame.  Note that it does not
 *   affect the identity of the frame, which is what's used for comparing and
 *   hashing.
 *
 * @constructor
 * Construct a new `Frame` representing a call within the given frame.
 *
 * @param outerFrame
 *   The frame that was active at the point where an invocation of this frame
 *   occurred, or `null` if this is the outermost frame.
 * @param code
 *   The actual [A_RawFunction] that has the L1 code for this frame.
 * @param debugName
 *   What to name this frame.
 */
class Frame constructor(
	val outerFrame: Frame?,
	val code: A_RawFunction,
	val debugName: String)
{
	/**
	 * Answer the depth of this frame, which is how many invocations deep it
	 * is relative to the outermost frame represented by an [L2Chunk].
	 * Note that frames compare by identity, so two frames with the same depth
	 * are not necessarily equal.
	 *
	 * @return
	 *   The depth of the frame, where `1` is the outermost frame of a chunk.
	 */
	fun depth(): Int
	{
		var f = outerFrame
		var depth = 1
		while (f !== null)
		{
			depth++
			f = f.outerFrame
		}
		return depth
	}

	override fun toString(): String = debugName

	/**
	 * Answer the [L2SemanticValue] representing this frame's function.
	 *
	 * @return
	 *   This frame's [L2SemanticFunction].
	 */
	fun function(): L2SemanticValue = L2SemanticFunction(this)

	/**
	 * Answer the [L2SemanticValue] representing this frame's label.
	 *
	 * @return
	 *   This frame's [L2SemanticLabel].
	 */
	fun label(): L2SemanticValue = L2SemanticLabel(this)

	/**
	 * Answer the [L2SemanticValue] representing one of this frame's
	 * function's captured outer values.
	 *
	 * @param outerIndex
	 *   The subscript of the outer value to retrieve from the function running
	 *   for this frame.
	 * @param optionalName
	 *   Either a [String] providing a naming hint for this outer, or `null`.
	 * @return
	 *   The [L2SemanticValue] representing the specified outer.
	 */
	fun outer(outerIndex: Int, optionalName: String?): L2SemanticValue =
		L2SemanticOuter(this, outerIndex, optionalName)

	/**
	 * Answer the [L2SemanticValue] representing one of this frame's slots, as
	 * of just after the particular nybblecode that wrote it.
	 *
	 * @param slotIndex
	 *   The subscript of the slot to retrieve from the virtual continuation
	 *   running for this frame.
	 * @param afterPc
	 *   The level-one [A_Continuation.pc] just after the nybblecode instruction
	 *   that produced the value in this slot.
	 * @param optionalName
	 *   Either a [String] providing a naming hint for this slot, or `null`.
	 * @return
	 *   The [L2SemanticValue] representing the specified slot.
	 */
	fun slot(
		slotIndex: Int,
		afterPc: Int,
		optionalName: String?
	): L2SemanticValue =
		L2SemanticSlot(this, slotIndex, afterPc, optionalName)

	/**
	 * Answer the [L2SemanticValue] representing the return result from this
	 * frame.
	 *
	 * @return
	 *   The [L2SemanticValue] representing the return result.
	 */
	fun result(): L2SemanticValue = L2SemanticResult(this)

	/**
	 * Answer the semantic value representing a new temporary value.
	 *
	 * @param uniqueId
	 *   The unique identifier used to identify this temporary value within its
	 *   frame.
	 * @return
	 *   An [L2SemanticTemp] representing the temporary value, generalized to an
	 *   [L2SemanticValue].
	 */
	fun temp(uniqueId: Int): L2SemanticValue = L2SemanticTemp(this, uniqueId)

	/**
	 * Answer an [L2SemanticValue] that represents the reified caller
	 * continuation.
	 *
	 * @return
	 *   The reified caller (an [A_Continuation] at runtime) of this  frame.
	 */
	fun reifiedCaller(): L2SemanticValue = L2SemanticCaller(this)

	/**
	 * Transform the receiver via the given [Function].
	 *
	 * @param topFrameReplacement
	 *   The `Frame` to substitute for the top frame of the code being inlined.
	 * @param frameTransformer
	 *   How to transform `Frame` parts of the receiver.
	 * @return
	 *   The transformed `Frame`, possibly the receiver if the result of the
	 *   transformation would have been an equal value.
	 */
	fun transform(
		topFrameReplacement: Frame,
		frameTransformer: (Frame) -> Frame): Frame
	{
		if (outerFrame === null)
		{
			return topFrameReplacement
		}
		val newOuterFrame = frameTransformer(outerFrame)
		return if (newOuterFrame == outerFrame)
		{
			this
		}
		else Frame(newOuterFrame, code, "$debugName (inlined)")
	}

}
