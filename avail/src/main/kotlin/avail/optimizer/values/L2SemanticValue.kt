/*
 * L2SemanticValue.kt
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

import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.TypeTag
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.register.RegisterKind
import avail.interpreter.primitive.Primitive
import avail.optimizer.L2Synonym
import avail.optimizer.ValueClass
import avail.optimizer.manifest.L2ValueManifest
import avail.utility.cast
import avail.utility.ifZero
import avail.utility.notNullAnd

/**
 * An [L2SemanticValue] represents a value stably computed from constants,
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
abstract class L2SemanticValue
protected constructor(
	val hash: Int
): Comparable<L2SemanticValue>
{
	override fun hashCode(): Int = hash

	override fun equals(other: Any?): Boolean =
		other is L2SemanticValue && equalsSemanticValue(other)

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
	 * Answer whether this is a constant and the constant is the given one.
	 *
	 * @param constant
	 *   The [AvailObject] to compare this semantic value's constant against, if
	 *   the semantic value is constant.
	 * @return
	 *   Whether this semantic value is a constant that matches the given
	 *   [constant].
	 */
	fun isConstant(constant: AvailObject): Boolean =
		constant.notNullAnd { equals(constant) }

	/**
	 * If this semantic value represents a constant, answer it, otherwise answer
	 * `null`.
	 */
	val constant: AvailObject?
		get() = constantRestrictionOrNull?.constantOrNull

	/**
	 * If this semantic value represents a constant, answer the constant-valued
	 * [TypeRestriction], otherwise `null`.  The restriction will have the
	 * appropriate flags set for the [RegisterKind] of this semantic value.
	 */
	open val constantRestrictionOrNull: TypeRestriction?
		get() = null

	/**
	 * Answer the restriction that this semantic value should have if nothing
	 * else is known about it.
	 */
	abstract val defaultRestriction: TypeRestriction

	/**
	 * This semantic value has just been bound to the given [ValueClass] in the
	 * given [L2ValueManifest].  If it describes some *other* value – as a
	 * [TypeTag] or an [ObjectLayoutVariant] id does – tell the manifest how the
	 * two classes are related.
	 *
	 * Subclasses that are derived from another value override this and call the
	 * manifest operation appropriate to the kind of derivation.  Deciding that
	 * by type-testing the semantic value at the manifest end would put knowledge
	 * of every subclass into the manifest; dispatching here keeps each semantic
	 * value responsible for describing itself, and leaves the manifest holding
	 * only the narrow operations it is asked to perform.
	 *
	 * @param manifest
	 *   The [L2ValueManifest] doing the binding.
	 * @param valueClass
	 *   The [ValueClass] this semantic value was just bound to.
	 */
	open fun recordDerivationIn(
		manifest: L2ValueManifest,
		valueClass: ValueClass)
	{
		// By default a semantic value is not derived from anything.
	}

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
		frameTransformer: (Frame) -> Frame
	): L2SemanticValue

	override fun compareTo(other: L2SemanticValue) =
		primaryVisualSortKey.ordinal
			.compareTo(other.primaryVisualSortKey.ordinal)
			.ifZero { secondaryCompare(other) }

	open fun secondaryCompare(other: L2SemanticValue): Int
	{
		return toStringForSynonym().compareTo(other.toStringForSynonym())
	}

	/**
	 * The primary criterion by which to sort (ascending) the semantic values in
	 * a synonym when presenting them visually.
	 */
	open val primaryVisualSortKey get() = PrimaryVisualSortKey.OTHER

	/**
	 * Produce a compact textual representation suitable for displaying within
	 * a synonym in a debugger or visualized control flow graph.
	 *
	 * @return
	 *   A short string representation of this semantic value.
	 */
	open fun toStringForSynonym(): String = toString()

	/**
	 * Answer true iff this semantic value should be enclosed in parentheses
	 * when appearing as a left or right argument of an
	 * [L2SemanticPrimitiveInvocation] that renders itself in infix.
	 */
	open fun requiresParentheses(): Boolean = false

	/**
	 * The major ordering of semantic values when printing an [L2Synonym].
	 * Synonyms and value manifests' contents sort by the ordinal, so rearrange
	 * the enum values to change this order.
	 */
	enum class PrimaryVisualSortKey
	{
		CONSTANT_NIL,
		CONSTANT,
		CALLER,
		LABEL,
		OUTER,
		PRIMITIVE_INVOCATION,
		NAMED_TEMP,
		TEMP,
		OTHER,
		NAMED_SLOT,
		SLOT;
	}

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
		fun constant(value: A_BasicObject): L2SemanticValue =
			L2SemanticConstant(value.makeImmutable())

		/**
		 * Answer the semantic value representing a particular constant value,
		 * first converted from an [Int] to an [AvailObject].
		 *
		 * @param value
		 *   The [Int] to convert to an [AvailObject] and then a semantic
		 *   constant.
		 * @return
		 *   A [L2SemanticConstant] representing the constant.
		 */
		fun constant(value: Int): L2SemanticValue =
			L2SemanticConstant(fromInt(value))
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
		fun primitiveInvocation(
			primitive: Primitive,
			argumentSemanticValues: List<L2SemanticValue>
		) = L2SemanticPrimitiveInvocation(
			primitive, argumentSemanticValues.cast())
	}
}
