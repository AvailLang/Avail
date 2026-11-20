/*
 * L2SemanticLabel.kt
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

import avail.descriptor.functions.LabelDescriptor
import avail.descriptor.types.LabelTypeDescriptor.Companion.mostGeneralLabelType
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments

/**
 * A semantic value which represents the [label][LabelDescriptor] created for
 * the indicated [Frame].  A label holds only the frame's caller and function,
 * not its arguments, so restarting it with replacement arguments via
 * [P_RestartContinuationWithArguments] leaves the label itself unchanged.
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
	override fun equalsSemanticValue(other: L2SemanticValue) =
		other is L2SemanticLabel && super.equalsSemanticValue(other)

	override fun transform(
		semanticValueTransformer:
			(L2SemanticValue) -> L2SemanticValue,
		frameTransformer: (Frame) -> Frame
	): L2SemanticValue =
		frameTransformer(frame).let { newFrame ->
			return if (newFrame == frame) this else L2SemanticLabel(newFrame)
		}

	override val defaultRestriction: TypeRestriction
		get() = labelRestriction

	override val primaryVisualSortKey get() = PrimaryVisualSortKey.LABEL

	override fun toString(): String = "Label for $frame"

	companion object
	{
		/** The default restriction for labels. */
		private val labelRestriction = restrictionForType(mostGeneralLabelType)
	}
}
