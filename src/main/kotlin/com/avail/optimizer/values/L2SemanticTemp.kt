/*
 * L2SemanticTemp.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.optimizer.values

/**
 * A semantic value which holds a temporary value in a [Frame].  The scope
 * of this value is usually local to a section of Java code that both produces
 * and consumes the value, and it might have no meaning beyond this simple
 * correlation of production and use.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property uniqueId
 *   An integer which should be unique across all other instances for the same
 *   [Frame].
 * @constructor
 * Create a new `L2SemanticTemp` semantic value.
 *
 * @param frame
 *   The frame for which this represents a temporary value.
 * @param uniqueId
 *   An integer which should be unique across all other instances of this class
 *   created for this [Frame].
 */
internal class L2SemanticTemp constructor(frame: Frame, val uniqueId: Int)
	: L2FrameSpecificSemanticValue(frame, uniqueId xor -0x5d6360e4)
{
	override fun equalsSemanticValue(other: L2SemanticValue): Boolean =
		(other is L2SemanticTemp
			&& super.equalsSemanticValue(other)
			&& uniqueId == other.uniqueId)

	override fun transform(
		semanticValueTransformer: (L2SemanticValue) -> L2SemanticValue,
		frameTransformer: (Frame) -> Frame): L2SemanticValue =
			frameTransformer(frame).let {
				if (it == frame) this else L2SemanticTemp(it, uniqueId)
			}


	override fun primaryVisualSortKey() = PrimaryVisualSortKey.TEMP

	override fun toString(): String =
		"Temp#$uniqueId${if (frame.depth() == 1) "" else "Temp#$uniqueId in $frame"}"
}
