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
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.RegisterKind
import avail.interpreter.primitive.Primitive
import avail.optimizer.L2Entity
import avail.optimizer.L2Entity.PrimaryVisualSortKey
import avail.optimizer.L2ValueManifest
import avail.optimizer.L2ValueManifest.Constraint
import avail.optimizer.L2ValueManifest.Representation
import avail.optimizer.ValueClass
import avail.utility.cast
import avail.utility.ifZero
import avail.utility.notNullAnd

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
abstract class L2SemanticValue<K: RegisterKind<K>>
protected constructor(
	val hash: Int
) : L2Entity<K>
{
	override fun hashCode(): Int = hash

	override fun equals(other: Any?): Boolean =
		other is L2SemanticValue<*> && equalsSemanticValue(other)

	open fun equalsSemanticValue(other: L2SemanticValue<*>) = this === other

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

	abstract val toBoxed: L2SemanticBoxedValue

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
	 * Answer whether a move that adds this to a synonym should be kept around
	 * just to make the synonym visible for reuse, even though the register
	 * written by the move is dead.
	 */
	open val isUsefulForGlobalValueNumbering: Boolean get() = false

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
	 * Answer whether the given [L2ValueManifest] holds this value *in the
	 * representation that this semantic value names*.
	 *
	 * Knowing `x` in a boxed register says nothing about there being an int
	 * register for it, so this cannot be answered by the presence of a
	 * [ValueClass] alone: it is a question about one [RegisterKind]'s
	 * [Representation] of that class.  Which one is the receiver's business, not
	 * the manifest's, so each subclass names its own [RegisterKind] here and the
	 * manifest merely performs the lookup it is handed.
	 *
	 * @param manifest
	 *   The [L2ValueManifest] to interrogate.
	 * @return
	 *   Whether the manifest holds this value in this value's representation.
	 */
	internal abstract fun hasRepresentationIn(
		manifest: L2ValueManifest
	): Boolean

	/**
	 * Answer how the given [L2ValueManifest] describes this value *in the
	 * representation that this semantic value names*, or `null` if the manifest
	 * does not know the value at all.
	 *
	 * This is the same dispatch as [hasRepresentationIn], for the case where the
	 * caller wants the [Constraint] rather than merely its existence.  Every
	 * kind-specific answer the manifest can give – the registers, the postponed
	 * instruction, the restriction, the synonym – is reached through the
	 * constraint this answers, so this is the single point at which a semantic
	 * value selects its own representation.
	 *
	 * @param manifest
	 *   The [L2ValueManifest] to interrogate.
	 * @return
	 *   The kind-scoped [Constraint], or `null` if the value is unknown.
	 */
	internal abstract fun constraintIn(
		manifest: L2ValueManifest
	): Constraint<*>?

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
		semanticValueTransformer:
			(L2SemanticValue<BOXED_KIND>) -> L2SemanticValue<BOXED_KIND>,
		frameTransformer: (Frame) -> Frame
	): L2SemanticValue<K>

	override fun compareTo(other: L2Entity<*>) =
		primaryVisualSortKey.ordinal.compareTo(
				other.primaryVisualSortKey.ordinal)
			.ifZero {
				// Order them within the primary category.
				secondaryCompare(other as L2SemanticValue<*>)
			}

	override fun secondaryCompare(other: L2Entity<*>): Int
	{
		if (other !is L2SemanticValue)
			return super.secondaryCompare(other)
		return toStringForSynonym().compareTo(other.toStringForSynonym())
	}

	/**
	 * The primary criterion by which to sort (ascending) the semantic values in
	 * a synonym when presenting them visually.
	 */
	override val primaryVisualSortKey get() = PrimaryVisualSortKey.OTHER

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
	 * A helper function to assist Kotlin's type deduction.  Create an
	 * [L2ReadOperand] that produces the value of this semantic value.
	 *
	 * @param manifest
	 *   The active [L2ValueManifest] at the current code generation site.
	 * @return
	 *   The new [L2ReadOperand], parameterized with [K].
	 */
	fun createRead(manifest: L2ValueManifest): L2ReadOperand<K>
	{
		return kind.createRead(this, manifest)
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
		fun constant(value: A_BasicObject): L2SemanticBoxedValue =
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
		fun constant(value: Int): L2SemanticBoxedValue =
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
			argumentSemanticValues: List<L2SemanticValue<BOXED_KIND>>
		) = L2SemanticPrimitiveInvocation(
			primitive, argumentSemanticValues.cast())
	}
}
