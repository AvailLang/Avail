/*
 * L2SemanticOuter.kt
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
/**
 * A semantic value which represents a numbered outer variable in the function
 * of some [Frame].
 *
 * @property outerIndex
 *   The one-based index of the outer of the function.
 *
 * @constructor
 * Create a new `L2SemanticOuter` semantic value.
 *
 * @param frame
 *   The frame for which this represents an outer.
 * @param outerIndex
 *   The one-based index of the outer in the frame's function.
 * @param optionalName
 *   Either a [String] providing a naming hint, or `null`.
 */
internal class L2SemanticOuter
constructor(
	frame: Frame,
	val outerIndex: Int,
	private val optionalName: String?
) : L2FrameSpecificSemanticValue(frame, outerIndex xor -0x22fc3786)
{
	// Ignore the optionalName.
	override fun equalsSemanticValue(other: L2SemanticValue): Boolean =
		(other is L2SemanticOuter
			&& super.equalsSemanticValue(other)
			&& outerIndex == other.outerIndex)

	override fun transform(
		semanticValueTransformer: (L2SemanticValue) -> L2SemanticValue,
		frameTransformer: (Frame) -> Frame
	): L2SemanticValue = when (val newFrame = frameTransformer(frame))
	{
		frame -> this
		else -> L2SemanticOuter(newFrame, outerIndex, optionalName)
	}

	override fun primaryVisualSortKey() = PrimaryVisualSortKey.OUTER

	override fun toString(): String = buildString {
		when (optionalName)
		{
			null -> append("Outer#$outerIndex")
			else -> append("Outer#$outerIndex($optionalName)")
		}
		if (frame.depth() > 1) append(" [$frame]")
	}
}
