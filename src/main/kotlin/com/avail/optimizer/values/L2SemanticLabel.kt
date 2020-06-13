/*
 * L2SemanticLabel.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments

/**
 * A semantic value which represents a label continuation created for the
 * indicated [Frame].
 *
 * TODO MvG - It's unclear how to deal with replacement arguments provided by
 *  [P_RestartContinuationWithArguments].  Perhaps the approach is to create a
 *  duplicate Label using a new Frame.  It would have to merge control flow into
 *  a loop, so maybe this just falls under the general case of phis within
 *  loops. Or maybe it should use the very same Arguments, since a semantic
 *  value doesn't have a notion of value or register *directly* associated with
 *  it, only through a manifest.
 *
 * @constructor
 * Create a new `L2SemanticLabel` semantic value.
 *
 * @param frame
 *   The frame for which this represents a label.
 */
internal class L2SemanticLabel constructor(frame: Frame)
	: L2FrameSpecificSemanticValue(frame, 0x36B34F3D)
{
	override fun equalsSemanticValue(other: L2SemanticValue): Boolean =
		other is L2SemanticLabel && super.equalsSemanticValue(other)

	override fun transform(
		semanticValueTransformer: (L2SemanticValue) -> L2SemanticValue,
		frameTransformer: (Frame) -> Frame): L2SemanticValue =
			frameTransformer.invoke(frame).let {
				return if (it == frame) this else L2SemanticLabel(it)
			}

	override fun toString(): String = "Label for $frame"
}
