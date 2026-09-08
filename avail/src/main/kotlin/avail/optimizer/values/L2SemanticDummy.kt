/*
 * L2SemanticDummy.kt
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

import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.register.L2Register

/**
 * An [L2SemanticValue] which should only be present after the control flow
 * graph has been transformed to only respect the connections of [L2Register]s.
 * There should be no writes of a dummy semantic value, and each read should be
 * of a distinct one, although that's not important.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *   Create a new [L2SemanticDummy].
 * @param index
 *   An [Int] that uniquely identifies this dummy semantic value in the graph.
 * @param comment
 *   An optional [String] describing the purpose of this dummy semantic value.
 */
class L2SemanticDummy
internal constructor(
	val index: Int,
	val comment: String? = null
) : L2SemanticValue(index.hashCode())
{
	override fun equalsSemanticValue(other: L2SemanticValue) =
		other === this

	override fun toString(): String = when (comment)
	{
		null -> "Dummy#$index"
		else -> "Dummy#$index($comment)"
	}

	override fun transform(
		semanticValueTransformer:
			(L2SemanticValue) -> L2SemanticValue,
		frameTransformer: (Frame) -> Frame
	): L2SemanticValue = this

	override val defaultRestriction: TypeRestriction
		get() = TypeRestriction.topRestriction

	/**
	 * It shouldn't mix in the same graph with anything else, but for safety
	 * put it at the top.
	 */
	override val primaryVisualSortKey get() =
		PrimaryVisualSortKey.CONSTANT_NIL
}
