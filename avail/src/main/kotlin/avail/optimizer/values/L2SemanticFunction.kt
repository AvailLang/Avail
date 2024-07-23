/*
 * L2SemanticFunction.kt
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

import avail.interpreter.levelTwo.register.BOXED_KIND

/**
 * A semantic value which represents the current function while running code for
 * a particular [Frame].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Create a new `L2SemanticFunction` semantic value.
 *
 * @param frame
 *   The frame for which this represents the invoked function.
 */
internal class L2SemanticFunction constructor(frame: Frame)
	: L2FrameSpecificSemanticValue(frame, -0xe519ffd)
{
	override fun equalsSemanticValue(other: L2SemanticValue<*>) =
		other is L2SemanticFunction && super.equalsSemanticValue(other)

	override fun transform(
		semanticValueTransformer:
			(L2SemanticValue<BOXED_KIND>) -> L2SemanticValue<BOXED_KIND>,
		frameTransformer: (Frame) -> Frame
	): L2SemanticBoxedValue =
		frameTransformer(frame).let {
			if (it == frame) this else L2SemanticFunction(it)
		}

	override fun toString(): String =
		"CurrentFunction${if (frame.depth() == 1) "" else "[$frame]"}"
}
