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
 * The synonyms and restrictions of an [L2BasicBlock]'s [L2ValueManifest] as
 * they stood before the block's final instruction, without registers or
 * postponed instructions.  Regeneration replays it at the top of the
 * corresponding new block, and once the graph is complete it is the block's
 * settled record of what each value is known to be
 * (doc/Optimization/Level_Two/Post_phi_data.md §Content).
 *
 * @property restrictionsBySynonym
 *   The (immutable) map from [L2Synonym] to [TypeRestriction].  This shape
 *   captures both the restrictions and the synonym structure.
 * @constructor
 *   Construct a new instance.
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

	/**
	 * Answer the [TypeRestriction] of the given [L2Synonym]: the restriction
	 * of its semantic constant if it has one, since a synonym holding only a
	 * constant is not recorded, otherwise the recorded restriction.
	 *
	 * @param synonym
	 *   The [L2Synonym] to look up.
	 * @return
	 *   The synonym's restriction.
	 */
	operator fun get(synonym: L2Synonym): TypeRestriction =
		synonym.semanticValues()
			.firstNotNullOfOrNull { it.constantRestrictionOrNull }
			?: restrictionsBySynonym[synonym]!!

	/**
	 * Answer the [TypeRestriction] of the given [L2SemanticValue]: the
	 * constant's restriction if it is a semantic constant, otherwise the
	 * restriction recorded for its synonym.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @return
	 *   The value's restriction.
	 */
	operator fun get(semanticValue: L2SemanticValue): TypeRestriction =
		semanticValue.constantRestrictionOrNull
			?: restrictionsBySemanticValue()[semanticValue]!!

	/**
	 * The [TypeRestriction] this data records for the read's semantic value,
	 * for code running with this [PostPhiData] as a receiver.
	 */
	val L2ReadOperand<*>.restriction: TypeRestriction
		get() = get(semanticValue())

	/** The type of the read's [restriction] in this data. */
	val L2ReadOperand<*>.type: A_Type
		get() = restriction.type

	/**
	 * The read's value, if its semantic value is a constant or this data
	 * restricts it to a single value, otherwise `null`.
	 */
	val L2ReadOperand<*>.constantOrNull: AvailObject?
		get() = semanticValue().constant ?: restriction.constantOrNull
}
