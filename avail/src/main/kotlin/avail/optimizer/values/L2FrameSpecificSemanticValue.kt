/*
 * L2FrameSpecificSemanticValue.kt
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

import avail.descriptor.representation.AvailObject

/**
 * A semantic value which is specific to a [Frame].
 *
 * @property frame
 *   The frame in which this is a semantic value.
 *
 * @constructor
 * Create a new instance.
 *
 * @param frame
 *   The frame for which this is a semantic value.
 * @param hash
 *   A hash value of this semantic value, which this constructor will combine
 *   with the frame's hash.
 */
internal abstract class L2FrameSpecificSemanticValue constructor(
	val frame: Frame,
	hash: Int
) : L2SemanticBoxedValue(hash + frame.hashCode() * AvailObject.multiplier)
{
	/**
	 * Answer the [Frame] in which this invocation takes place.
	 *
	 * @return
	 *   The frame.
	 */
	fun frame(): Frame = frame

	override fun equalsSemanticValue(other: L2SemanticValue<*>): Boolean =
		other is L2FrameSpecificSemanticValue && frame() == other.frame()

	abstract override fun toString(): String

}
