/*
 * PostPhiData.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.optimizer

import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.values.L2SemanticValue

/**
 * Information extracted from the current [L2ValueManifest] just before the
 * end of generation of an [L2BasicBlock], before the final instruction
 * clones the manifest for each successor edge.
 *
 * @constructor
 *   Construct a new instance.
 * @property restrictionsBySynonym
 *   The (immutable) map from [L2Synonym] to [TypeRestriction].  This shape
 *   captures both the restrictions and the synonym structure.
 */
class PostPhiData
constructor(
	val restrictionsBySynonym: Map<L2Synonym, TypeRestriction>)
{
	/**
	 * Storage for the lazily computed [restrictionsBySemanticValue].
	 */
	private var restrictionsBySemanticValueOrNull:
		Map<L2SemanticValue, TypeRestriction>? = null

	/**
	 * A lazily computed map from each [L2SemanticValue] of the [L2Synonym]
	 * keys of [restrictionsBySynonym], to the corresponding
	 * [TypeRestriction].  In theory this speeds up subsequent semantic
	 * value lookups.
	 */
	fun restrictionsBySemanticValue(): Map<L2SemanticValue, TypeRestriction>
	{
		restrictionsBySemanticValueOrNull?.let { return it }
		restrictionsBySemanticValueOrNull = buildMap {
			restrictionsBySynonym.forEach { (synonym, restriction) ->
				synonym.semanticValues().forEach { value ->
					put(value, restriction)
				}
			}
		}
		return restrictionsBySemanticValueOrNull!!
	}

	operator fun get(synonym: L2Synonym): TypeRestriction =
		synonym.semanticValues()
			.firstNotNullOfOrNull { it.constantRestrictionOrNull }
			?: restrictionsBySynonym[synonym]!!

	operator fun get(semanticValue: L2SemanticValue): TypeRestriction =
		semanticValue.constantRestrictionOrNull
			?: restrictionsBySemanticValue()[semanticValue]!!

	val L2ReadOperand<*>.restriction: TypeRestriction
		get() = get(semanticValue())

	val L2ReadOperand<*>.type: A_Type
		get() = restriction.type

	val L2ReadOperand<*>.constantOrNull: AvailObject?
		get() = semanticValue().constant ?: restriction.constantOrNull
}
