/*
 * L2SemanticConstant.kt
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

import avail.descriptor.representation.A_BasicObject

/**
 * A semantic value which is a particular actual constant value.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Create a new `L2SemanticConstant` semantic value.
 *
 * @param value
 *   The actual value of the constant.
 */
internal class L2SemanticConstant constructor(value: A_BasicObject) :
	L2SemanticValue(value.hashCode())
{
	/** The constant Avail value represented by this semantic value. */
	val value: A_BasicObject = value.makeImmutable()

	override fun equalsSemanticValue(other: L2SemanticValue): Boolean =
		other is L2SemanticConstant && value.equals(other.value)

	override fun transform(
		semanticValueTransformer: (L2SemanticValue) -> L2SemanticValue,
		frameTransformer: (Frame) -> Frame): L2SemanticValue = this

	override fun primaryVisualSortKey() = when
	{
		value.isNil -> PrimaryVisualSortKey.CONSTANT_NIL
		else -> PrimaryVisualSortKey.CONSTANT
	}

	override val isConstant: Boolean
		get() = true

	override fun toString(): String
	{
		var valueString = value.toString()
		if (valueString.length > 50)
		{
			valueString = valueString.substring(0, 50) + '…'
		}
		valueString = valueString
			.replace("\n", "\\n")
			.replace("\t", "\\t")
		return "Constant($valueString)"
	}
}
