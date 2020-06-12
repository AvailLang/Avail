/*
 * L2SemanticValue.java
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

import com.avail.descriptor.representation.A_BasicObject
import com.avail.interpreter.Primitive
import com.avail.optimizer.L2Entity

/**
 * An `L2SemanticValue` represents a value stably computed from constants,
 * arguments, and potentially unstable values acquired by specific previous
 * instructions – e.g., fetching the current time at a specific position in a
 * sequence of L2 instructions, or the result of a non-primitive call to another
 * function.
 *
 * @property hash
 *   The permanent hash value of this `L2SemanticValue`.
 *
 * @constructor
 * Create a new instance, with the given pre-computed hash.
 *
 * @param hash
 *   The pre-computed hash value to use for this semantic value.
 */
abstract class L2SemanticValue protected constructor(val hash: Int) : L2Entity
{
	override fun hashCode(): Int = hash

	override fun equals(other: Any?): Boolean =
		(other is L2SemanticValue
			&& equalsSemanticValue(other))

	open fun equalsSemanticValue(other: L2SemanticValue) = this === other

	/**
	 * Answer whether this semantic value corresponds with the notion of a
	 * semantic constant.
	 *
	 * @return
	 *   Whether this represents a constant.
	 */
	open val isConstant: Boolean
		get() = false

	/**
	 * Transform the receiver.  If it's composed of parts, transform them with
	 * the supplied [Function]s.
	 *
	 * @param semanticValueTransformer
	 *   How to transform `L2SemanticValue` parts of the receiver, (not the
	 *   receiver itself).
	 * @param frameTransformer
	 *   How to transform [Frame] parts of the receiver.
	 * @return
	 *   The transformed `L2SemanticValue`, possibly the receiver if the result
	 *   of the transformation would have been an equal value.
	 */
	abstract fun transform(
		semanticValueTransformer: (L2SemanticValue) -> L2SemanticValue,
		frameTransformer: (Frame) -> Frame): L2SemanticValue

	/**
	 * Produce a compact textual representation suitable for displaying within
	 * a synonym in a debugger or visualized control flow graph.
	 *
	 * @return
	 *   A short string representation of this semantic value.
	 */
	open fun toStringForSynonym(): String = toString()

	companion object
	{
		/**
		 * Answer the semantic value representing a particular constant value.
		 *
		 * @param value
		 *   The actual Avail value.
		 * @return
		 *   A [L2SemanticConstant] representing the constant.
		 */
		@JvmStatic
		fun constant(value: A_BasicObject): L2SemanticValue =
			L2SemanticConstant(value.makeImmutable())

		/**
		 * Answer a semantic value representing the result of invoking a
		 * foldable primitive.
		 *
		 * @param primitive
		 *   The [Primitive] that was executed.
		 * @param argumentSemanticValues
		 *   `L2SemanticValue`s that supplied the arguments to the primitive.
		 * @return
		 *   The semantic value representing the primitive result.
		 */
		@JvmStatic
		fun primitiveInvocation(
				primitive: Primitive,
				argumentSemanticValues: List<L2SemanticValue>)
			: L2SemanticPrimitiveInvocation =
				L2SemanticPrimitiveInvocation(primitive, argumentSemanticValues)
	}
}