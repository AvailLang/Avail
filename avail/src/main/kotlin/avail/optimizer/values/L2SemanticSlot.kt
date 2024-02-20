/*
 * L2SemanticSlot.kt
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
package avail.optimizer.values

import avail.descriptor.functions.A_Continuation
import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.register.BOXED_KIND

/**
 * A semantic value which represents a slot of some [Frame]'s effective
 * [A_Continuation].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @property slotIndex
 *   The one-based index of the slot in its [Frame].
 * @property pcAfter
 *   The level one [A_Continuation.pc] at the position just after the nybblecode
 *   instruction that produced this value.  This serves to distinguish semantic
 *   slots at the same index but at different times, allowing a correct SSA
 *   graph and the reordering that it supports.
 *
 * @constructor
 * Create a new `L2SemanticSlot` semantic value.
 *
 * @param frame
 *   The frame for which this represents a slot of a virtualized continuation.
 * @param slotIndex
 *   The one-based index of the slot in that frame.
 * @param pcAfter
 *   The L1 program counter just after the instruction responsible for
 *   effectively writing this value in the frame's slot.
 * @param optionalName
 *   Either a [String] that provides a useful naming hint for this slot, or
 *   `null`.
 */
internal class L2SemanticSlot constructor(
	frame: Frame,
	val slotIndex: Int,
	val pcAfter: Int,
	private val optionalName: String?
) : L2FrameSpecificSemanticValue(
	frame, slotIndex * AvailObject.multiplier xor pcAfter)
{
	init
	{
		assert(slotIndex >= 1)
	}

	// Note: Ignore the optionalName.
	override fun equalsSemanticValue(other: L2SemanticValue<*>) =
		(other is L2SemanticSlot
			&& super.equalsSemanticValue(other)
			&& slotIndex == other.slotIndex
			&& pcAfter == other.pcAfter)

	override fun transform(
		semanticValueTransformer:
			(L2SemanticValue<BOXED_KIND>) -> L2SemanticValue<BOXED_KIND>,
		frameTransformer: (Frame) -> Frame
	): L2SemanticBoxedValue =
		frameTransformer(frame()).let {
			if (it == frame()) this
			else L2SemanticSlot(it, slotIndex, pcAfter, optionalName)
		}

	override fun primaryVisualSortKey() = PrimaryVisualSortKey.SLOT

	override fun toString(): String = buildString {
		when (optionalName)
		{
			null -> append(slotIndex)
			else -> append(optionalName)
		}
		append("-")
		append(pcAfter)
		if (frame.depth() > 1) append("[$frame]")
	}


	override fun toStringForSynonym(): String = buildString {
		when (optionalName)
		{
			null -> append(slotIndex)
			else -> append(optionalName)
		}
		append("-")
		append(pcAfter)
		if (frame.depth() > 1) append("[$frame]")
	}
}
