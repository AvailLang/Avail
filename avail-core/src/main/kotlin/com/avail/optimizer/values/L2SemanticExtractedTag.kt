/*
 * L2SemanticExtractedTag.kt
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
package com.avail.optimizer.values

import com.avail.descriptor.types.TypeTag
import com.avail.interpreter.levelTwo.register.L2BoxedRegister

/**
 * A semantic value which represents the [TypeTag] extracted from some [base]
 * semantic value.  To keep unboxed ints homogenous, this will always be wrapped
 * inside an [L2SemanticUnboxedInt], even though the boxed value generally will
 * not occur in any [L2BoxedRegister].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Create a new `L2SemanticExtractedTag` semantic value.
 *
 * @param base
 *   The semantic value holding the value for which a [TypeTag] has been
 *   extracted.
 */
class L2SemanticExtractedTag constructor(val base: L2SemanticValue)
	: L2SemanticValue(base.hash + 0x53408C24)
{
	override fun equalsSemanticValue(other: L2SemanticValue): Boolean =
		other is L2SemanticExtractedTag && base.equalsSemanticValue(other.base)

	override fun transform(
		semanticValueTransformer: (L2SemanticValue) -> L2SemanticValue,
		frameTransformer: (Frame) -> Frame): L2SemanticValue =
			semanticValueTransformer(base).let {
				if (it == base) this else L2SemanticExtractedTag(it)
		}

	override fun toString(): String = "Tag($base)"
}
