/*
 * L2Synonym.kt
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
package com.avail.optimizer

import com.avail.descriptor.representation.AvailObject
import com.avail.optimizer.values.Frame
import com.avail.optimizer.values.L2SemanticValue

/**
 * An `L2Synonym` is a set of [L2SemanticValue]s known to represent the same
 * value in some [L2ValueManifest].  The manifest at each instruction includes a
 * set of synonyms which partition the semantic values.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Create a synonym.
 *
 * @param semanticValues
 *   The non-empty collection of [L2SemanticValue]s bound to this synonym.
 */
class L2Synonym constructor(semanticValues: Collection<L2SemanticValue>)
	: Comparable<L2Synonym>
{
	/**
	 * The [L2SemanticValue]s for which this synonym's registers hold the (same)
	 * value.
	 */
	private val semanticValues: Set<L2SemanticValue> =
		semanticValues.toSet().also { assert(semanticValues.isNotEmpty()) }

	/**
	 * The eagerly computed, permanent hash value of this synonym.  Note that it
	 * must produce the same value even if the set of semantic values is
	 * traversed in a different order.
	 */
	private val hash = semanticValues.fold(0x5C278E78) { acc, sv ->
		((sv.hashCode() xor 0x53B478BB) * AvailObject.multiplier) xor acc
	}

	override fun hashCode() = hash

	override fun equals(other: Any?) =
		when
		{
			other === null -> false
			this === other -> true
			other !is L2Synonym -> false
			other.hash != hash -> false
			other.semanticValues == semanticValues -> true
			else -> false
		}

	/**
	 * Answer the immutable set of [L2SemanticValue]s of this synonym.
	 *
	 * @return
	 *   The [L2SemanticValue]s in this synonym.
	 */
	fun semanticValues(): Set<L2SemanticValue> = semanticValues

	/**
	 * Choose one of the [L2SemanticValue]s from this `L2Synonym`.
	 *
	 * @return
	 *   An arbitrary [L2SemanticValue] of this synonym.
	 */
	fun pickSemanticValue(): L2SemanticValue = semanticValues.iterator().next()

	/**
	 * Transform the [Frame]s and [L2SemanticValue]s within this synonym to
	 * produce a new synonym.
	 *
	 * @param semanticValueTransformer
	 *   How to transform each [L2SemanticValue].
	 * @return
	 *   The transformed synonym, or the original if there was no change.
	 */
	fun transform(
		semanticValueTransformer: (L2SemanticValue) -> L2SemanticValue)
		: L2Synonym
	{
		val newSemanticValues = mutableSetOf<L2SemanticValue>()
		var changed = false
		for (semanticValue in semanticValues)
		{
			val newSemanticValue = semanticValueTransformer(semanticValue)
			newSemanticValues.add(newSemanticValue)
			changed = changed or (newSemanticValue != semanticValue)
		}
		return if (changed) L2Synonym(newSemanticValues) else this
	}

	override fun toString(): String
	{
		val sortedStrings = semanticValues
			.sorted()
			.map(L2SemanticValue::toStringForSynonym)
		val builder = StringBuilder()
		builder.append('〖')
		var first = true
		var column = 1
		for (string in sortedStrings)
		{
			if (column > 75)
			{
				builder.append("\n       ")
				column = 8
			}
			if (!first)
			{
				builder.append(" & ")
				column += 3
			}
			builder.append(string)
			column += string.codePointCount(0, string.length)
			first = false
		}
		builder.append('〗')
		return builder.toString()
	}

	override fun compareTo(other: L2Synonym) =
		semanticValues.min()!!.compareTo(other.semanticValues.min()!!)
}
