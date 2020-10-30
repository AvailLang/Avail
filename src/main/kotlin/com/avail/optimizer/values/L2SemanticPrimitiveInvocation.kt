/*
 * L2SemanticPrimitiveInvocation.kt
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

import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.Primitive

/**
 * An [L2SemanticValue] which represents the result produced by a [Primitive]
 * when supplied a list of argument [L2SemanticValue]s.  The primitive must be
 * stable (same result), pure (no side-effects), and successful for the supplied
 * arguments.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Create a new `L2SemanticPrimitiveInvocation` semantic value.
 *
 * @property primitive
 *   The [Primitive] whose invocation is being represented.
 * @property argumentSemanticValues
 *   The [List] of [L2SemanticValue]s that represent the arguments to the
 *   invocation of the primitive.
 * @param primitive
 *   The primitive whose invocation is being represented.
 * @param argumentSemanticValues
 *   The semantic values supplied as arguments.
 */
class L2SemanticPrimitiveInvocation internal constructor(
	@JvmField val primitive: Primitive,
	@JvmField val argumentSemanticValues: List<L2SemanticValue>
) : L2SemanticValue(computeHash(primitive, argumentSemanticValues))
{
	init
	{
		assert(primitive.hasFlag(Primitive.Flag.CanFold))
	}

	override fun equalsSemanticValue(other: L2SemanticValue): Boolean =
		(other is L2SemanticPrimitiveInvocation
			&& primitive === other.primitive
			&& argumentSemanticValues == other.argumentSemanticValues)

	override fun toString(): String = buildString {
		append(primitive.name)
		append(argumentSemanticValues.joinToString(
			separator = ", ", prefix = "(", postfix = ")" ))
	}

	override fun transform(
		semanticValueTransformer: (L2SemanticValue) -> L2SemanticValue,
		frameTransformer: (Frame) -> Frame): L2SemanticValue
	{
		val numArgs = argumentSemanticValues.size
		val newArguments =
			argumentSemanticValues.mapTo(mutableListOf()) {
				it.transform(semanticValueTransformer, frameTransformer)
			}

		if ((0 until numArgs).all { newArguments[it] == argumentSemanticValues[it] })
		{
			return this
		}
		return L2SemanticPrimitiveInvocation(primitive, newArguments)
	}

	companion object
	{
		/**
		 * Compute the permanent hash for this semantic value.
		 *
		 * @param primitive
		 *   The primitive that was invoked.
		 * @param argumentSemanticValues
		 *   The semantic values of arguments that were supplied to the
		 *   primitive.
		 * @return
		 *   A hash of the inputs.
		 */
		private fun computeHash(
			primitive: Primitive,
			argumentSemanticValues: List<L2SemanticValue>): Int
		{
			var h = primitive.name.hashCode() xor 0x72C5FD8B
			for (argument in argumentSemanticValues)
			{
				h *= AvailObject.multiplier
				h = h xor argument.hashCode()
			}
			return h
		}
	}
}
