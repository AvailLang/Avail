/*
 * TypeRestriction.kt
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
package com.avail.interpreter.levelTwo.operand

import com.avail.descriptor.numbers.A_Number.Companion.equalsInt
import com.avail.descriptor.numbers.A_Number.Companion.lessOrEqual
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.A_Set.Companion.isSubsetOf
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.instance
import com.avail.descriptor.types.A_Type.Companion.instanceCount
import com.avail.descriptor.types.A_Type.Companion.instances
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.trimType
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import com.avail.descriptor.types.BottomTypeDescriptor
import com.avail.descriptor.types.InstanceMetaDescriptor
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import com.avail.descriptor.types.TypeDescriptor.Companion.isProperSubtype
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.IMMUTABLE_FLAG
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_FLOAT_FLAG
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_EQUALS_CONSTANT
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_CONSTANT
import com.avail.interpreter.levelTwo.register.L2BoxedRegister
import com.avail.interpreter.levelTwo.register.L2FloatRegister
import com.avail.interpreter.levelTwo.register.L2IntRegister
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind
import com.avail.optimizer.L2Synonym
import com.avail.utility.mapToSet
import java.util.EnumSet
import java.util.Objects

/**
 * This mechanism describes a restriction of a type without saying what it's to
 * be applied to.
 *
 * We capture an Avail [A_Type], and an optional exactly known value,
 * so that we can represent something that avoids the metacovariance weakness of
 * metatypes.
 *
 * We also capture negative type and negative instance information, to leverage
 * more advantage from the failure paths of type tests like
 * [L2_JUMP_IF_KIND_OF_CONSTANT] and [L2_JUMP_IF_EQUALS_CONSTANT].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Create a `TypeRestriction` from the already-canonicalized
 * arguments.
 *
 * @param type
 *   The Avail type that constrains some value somewhere.
 * @param constantOrNull
 *   Either `null` or the exact value that some value somewhere must equal.
 * @param excludedTypes
 *   A set of [A_Type]s to consider excluded.
 * @param excludedValues
 *   A set of values to consider excluded.
 * @param flags
 *   The encoded [flags] [Int].
 */
class TypeRestriction private constructor(
	type: A_Type,
	constantOrNull: A_BasicObject?,
	excludedTypes: Set<A_Type>,
	excludedValues: Set<A_BasicObject>,
	flags: Int)
{
	init {
		// Mixing boxed/unboxed in a restriction is now forbidden (Feb 2021).
		assert(flags == BOXED_FLAG.mask
			|| flags == (BOXED_FLAG.mask + IMMUTABLE_FLAG.mask)
			|| flags == UNBOXED_INT_FLAG.mask
			|| flags == UNBOXED_FLOAT_FLAG.mask)
	}

	/**
	 * The type of value that known to be in this register if this control
	 * flow path is taken.
	 */
	val type: A_Type

	/**
	 * The exact value that is known to be in this register if this control flow
	 * path is taken, or `null` if unknown.
	 */
	val constantOrNull: AvailObject?

	/**
	 * The set of types that are specifically excluded.  A value that satisfies
	 * one of these types does not satisfy this type restriction.  For the
	 * purpose of canonicalization, these types are all proper subtypes of the
	 * restriction's [type].  The [constantOrNull], if non-null,
	 * must not be a member of any of these types.
	 */
	val excludedTypes: Set<A_Type>

	/**
	 * The set of values that are specifically excluded.  A value in this set
	 * does not satisfy this type restriction.  For the purpose of
	 * canonicalization, these values must all be members of the restriction's
	 * [type], and must not contain the [constantOrNull], if
	 * non-null.
	 */
	val excludedValues: Set<A_BasicObject>

	/**
	 * An enumeration used to interpret the [flags] of a [TypeRestriction].  The
	 * sense of the flags is such that a bit-wise and can be used
	 */
	enum class RestrictionFlagEncoding
	{
		/** Whether the value is known to be immutable. */
		IMMUTABLE_FLAG,

		/**
		 * Whether the value is available in a boxed form in some
		 * [L2BoxedRegister].
		 */
		BOXED_FLAG,

		/**
		 * Whether the value is available in an unboxed form in some
		 * [L2IntRegister].
		 */
		UNBOXED_INT_FLAG,

		/**
		 * Whether the value is available in an unboxed form in some
		 * [L2FloatRegister].
		 */
		UNBOXED_FLOAT_FLAG;

		/** A pre-computed bit mask for this flag. */
		val mask = 1 shl ordinal

		companion object
		{
			/**
			 * A pre-computed bit mask for just the [RegisterKind]-related
			 * flags.
			 */
			val allKindsMask = (
				BOXED_FLAG.mask
					or UNBOXED_INT_FLAG.mask
					or UNBOXED_FLOAT_FLAG.mask)
		}
	}

	/**
	 * The flags that track intangible or supplemental properties of a value.
	 * These bits are indexed via the ordinals of elements of
	 * [RestrictionFlagEncoding].
	 *
	 * The semantics are chosen so that the intersection of two
	 * `TypeRestriction`s produces the bit-wise "and" (&) of the inputs' flags,
	 * and the union uses the bit-wise "or" (|).
	 */
	val flags: Int

	/** Answer whether the restricted value is known to be immutable. */
	val isImmutable: Boolean
		get() = flags and IMMUTABLE_FLAG.mask != 0

	/**
	 * Answer whether the restricted value is known to be boxed in an
	 * [L2BoxedRegister].
	 */
	val isBoxed: Boolean
		get() = flags and BOXED_FLAG.mask != 0

	/**
	 * Answer whether the restricted value is known to be unboxed in an
	 * [L2IntRegister].
	 */
	val isUnboxedInt: Boolean
		get() = flags and UNBOXED_INT_FLAG.mask != 0

	/**
	 * Answer whether the restricted value is known to be unboxed in an
	 * [L2FloatRegister].
	 */
	val isUnboxedFloat: Boolean
		get() = flags and UNBOXED_FLOAT_FLAG.mask != 0

	/**
	 * Answer whether the specified flag is set.
	 *
	 * @param restrictionFlag
	 *   The flag to test.
	 * @return
	 *   Whether the flag is set.
	 */
	fun hasFlag(restrictionFlag: RestrictionFlagEncoding): Boolean =
		flags and restrictionFlag.mask != 0

	/**
	 * Create a `TypeRestriction` from the already-canonicalized arguments.
	 *
	 * @param type
	 *   The Avail type that constrains some value somewhere.
	 * @param constantOrNull
	 *   Either `null` or the exact value that some value somewhere must equal.
	 * @param excludedTypes
	 *   A set of [A_Type]s to consider excluded.
	 * @param excludedValues
	 *   A set of values to consider excluded.
	 * @param isImmutable
	 *   Whether the value is known to be immutable.
	 * @param isBoxed
	 *   Whether this value is known to already reside in an [L2BoxedRegister].
	 * @param isUnboxedInt
	 *   Whether this value is known to already reside in an [L2IntRegister].
	 * @param isUnboxedFloat
	 *   Whether this value is known to already reside in an [L2FloatRegister].
	 */
	private constructor(
		type: A_Type,
		constantOrNull: A_BasicObject?,
		excludedTypes: Set<A_Type>,
		excludedValues: Set<A_BasicObject>,
		isImmutable: Boolean,
		isBoxed: Boolean,
		isUnboxedInt: Boolean,
		isUnboxedFloat: Boolean
	) : this(
		type,
		constantOrNull,
		excludedTypes,
		excludedValues,
		(if (isImmutable) IMMUTABLE_FLAG.mask else 0)
			or (if (isBoxed) BOXED_FLAG.mask else 0)
			or (if (isUnboxedInt) UNBOXED_INT_FLAG.mask else 0)
			or if (isUnboxedFloat) UNBOXED_FLOAT_FLAG.mask else 0)

	/**
	 * The receiver is a restriction for a register holding some value.  Answer
	 * the restriction for a register holding that value's type.
	 *
	 * @return
	 *   The restriction on the value's type.
	 */
	fun metaRestriction(): TypeRestriction
	{
		if (constantOrNull !== null)
		{
			// We're a constant, so the metaRestriction is also a constant type.
			return restrictionForConstant(type, BOXED_FLAG)
		}
		val resultExcludedValues = mutableSetOf<A_BasicObject>()
		// No object has exact type ⊥ or ⊤.
		resultExcludedValues.add(TOP.o)
		for (v in excludedValues)
		{
			resultExcludedValues.add(
				instanceTypeOrMetaOn(v))
		}
		val resultExcludedTypes = mutableSetOf<A_Type>()
		resultExcludedTypes.add(BottomTypeDescriptor.bottomMeta)
		for (t in excludedTypes)
		{
			resultExcludedTypes.add(InstanceMetaDescriptor.instanceMeta(t))
		}
		return restriction(
			InstanceMetaDescriptor.instanceMeta(type),
			null,
			resultExcludedTypes,
			resultExcludedValues,
			BOXED_FLAG.mask)
	}

	/**
	 * Create the union of the receiver and the other TypeRestriction.  This is
	 * the restriction that a register would have if it were assigned from one
	 * of two sources, each having one of the restrictions.
	 *
	 * @param other
	 *   The other `TypeRestriction` to combine with the receiver to produce the
	 *   output restriction.
	 * @return
	 *   The new type restriction.
	 */
	fun union(other: TypeRestriction): TypeRestriction
	{
		if (constantOrNull !== null && other.constantOrNull !== null
			&& constantOrNull.equals(other.constantOrNull)
			&& flags == other.flags)
		{
			// The two restrictions are equivalent, for the same constant value.
			return this
		}
		// We can only exclude types that were excluded in both restrictions.
		// Therefore find each intersection of an excluded type from the first
		// restriction and an excluded type from the second restriction.
		val mutualTypeIntersections = mutableSetOf<A_Type>()
		for (t1 in excludedTypes)
		{
			for (t2 in other.excludedTypes)
			{
				val intersection = t1.typeIntersection(t2)
				if (!intersection.isBottom)
				{
					mutualTypeIntersections.add(intersection)
				}
			}
		}
		// Figure out which excluded constants are also excluded in the other
		// restriction.
		val newExcludedValues = mutableSetOf<A_BasicObject>()
		for (value in excludedValues)
		{
			if (other.excludedValues.contains(value)
				|| other.excludedTypes.any { value.isInstanceOf(it) })
			{
				newExcludedValues.add(value)
			}
		}
		for (value in other.excludedValues)
		{
			if (excludedTypes.any { value.isInstanceOf(it) })
			{
				newExcludedValues.add(value)
			}
		}
		return restriction(
			type.typeUnion(other.type),
			null,
			mutualTypeIntersections,
			newExcludedValues,
			flags and other.flags)
	}

	/**
	 * Create the intersection of the receiver and the other TypeRestriction.
	 * This is the restriction that a register would have if it were already
	 * known to have the first restriction, and has been tested positively
	 * against the second restriction.
	 *
	 * @param other
	 *   The other `TypeRestriction` to combine with the receiver to produce the
	 *   intersected restriction.
	 * @return
	 *   The new type restriction.
	 */
	fun intersection(other: TypeRestriction) =
		if (constantOrNull !== null && other.constantOrNull !== null
			&& !constantOrNull.equals(other.constantOrNull))
		{
			// The restrictions are both constant, but disagree, so the
			// intersection is empty.
			bottomRestriction
		}
		else
		{
			restriction(
				type.typeIntersection(other.type),
				constantOrNull ?: other.constantOrNull,
				excludedTypes + other.excludedTypes,
				excludedValues + other.excludedValues,
				flags or other.flags
			)
		}

	/**
	 * Create the intersection of the receiver with the given A_Type.  This is
	 * the restriction that a register would have if it were already known to
	 * satisfy the receiver restriction, and has been tested positively against
	 * the given type.
	 *
	 * @param typeToIntersect
	 *   The [A_Type] to combine with the receiver to produce an intersected
	 *   restriction.
	 * @return
	 *   The new type restriction.
	 */
	fun intersectionWithType(typeToIntersect: A_Type) =
		restriction(
			type.typeIntersection(typeToIntersect),
			constantOrNull,
			excludedTypes,
			excludedValues,
			flags)

	/**
	 * Create the asymmetric difference of the receiver and the given A_Type.
	 * This is the restriction that a register would have if it held a value
	 * that satisfied the receiver, but failed a test against the given type.
	 *
	 * @param typeToExclude
	 *   The type to exclude from the receiver to create a new
	 *   `TypeRestriction`.
	 * @return
	 *   The new type restriction.
	 */
	fun minusType(typeToExclude: A_Type) =
		restriction(
			type,
			constantOrNull,
			excludedTypes + typeToExclude,
			excludedValues,
			flags)

	/**
	 * Create the asymmetric difference of the receiver and the given exact
	 * value.  This is the restriction that a register would have if it held a
	 * value that satisfied the receiver, but failed a value comparison against
	 * the given value.
	 *
	 * @param valueToExclude
	 *   The value to exclude from the receiver to create a new
	 *   `TypeRestriction`.
	 * @return
	 *   The new type restriction.
	 */
	fun minusValue(valueToExclude: A_BasicObject) =
		restriction(
			type,
			constantOrNull,
			excludedTypes,
			excludedValues + valueToExclude,
			flags)

	/**
	 * Answer true if this `TypeRestriction` contains every possible
	 * element of the given type.
	 *
	 * @param testType
	 *   The type to test is subsumed by this `TypeRestriction`.
	 * @return
	 *   True iff every instance of `testType` is a member of this
	 *   `TypeRestriction`.
	 */
	fun containsEntireType(testType: A_Type): Boolean
	{
		val constant = constantOrNull
		return when
		{
			constant === null ->
			{
				when
				{
					!testType.isSubtypeOf(type) -> false
					excludedTypes.any {
						!it.typeIntersection(testType).isBottom
					} -> false
					else -> excludedValues.none { it.isInstanceOf(testType) }
				}
			}
			// The value is known to be a type other than bottom, so there is no
			// possible testType that could contain just this constant as a
			// member.
			constant.isType && !constant.isBottom -> false
			// The value is not a type, or it's bottom.  Either way, the only
			// way the testType could be a subtype is if it's an instance (or
			// meta) type containing just that value.
			else -> testType.equals(instanceTypeOrMetaOn(constant))
		}
	}

	/**
	 * Answer true if this `TypeRestriction` only contains values that
	 * are within the given testType.
	 *
	 * @param testType
	 *   The type to check for complete coverage of this `TypeRestriction`.
	 * @return
	 *   True iff every instance of this `TypeRestriction` is also a member of
	 *   `testType`.
	 */
	fun containedByType(testType: A_Type): Boolean = type.isSubtypeOf(testType)

	/**
	 * Answer true if this `TypeRestriction` contains any values in common
	 * with the given type.  It uses the [A_Type.isVacuousType] test to
	 * determine whether any instances exist in the intersection.
	 *
	 * @param testType
	 *   The [A_Type] to intersect with this `TypeRestriction`
	 * @return
	 *   True iff there are any instances in common between the supplied type
	 *   and this `TypeRestriction`.
	 */
	fun intersectsType(testType: A_Type): Boolean
	{
		if (constantOrNull !== null)
		{
			return constantOrNull.isInstanceOf(testType)
		}
		val intersectedType = testType.typeIntersection(type)
		if (intersectedType.isVacuousType)
		{
			return false
		}
		if (excludedTypes.any {intersectedType.isSubtypeOf(it) })
		{
			// Even though the bare types intersect, the intersection was
			// explicitly excluded by the restriction.
			return false
		}
		return !(excludedValues.isNotEmpty()
			&& intersectedType.isEnumeration
			&& !intersectedType.isInstanceMeta
			&& intersectedType.instances().isSubsetOf(
				setFromCollection(excludedValues)))
	}

	/**
	 * Answer a restriction like the receiver, but with an additional flag set.
	 * If the flag is already set, answer the receiver.
	 *
	 * @param flagEncoding
	 *   The [RestrictionFlagEncoding] to set.
	 * @return
	 *   The new `TypeRestriction`, or the receiver.
	 */
	fun withFlag(flagEncoding: RestrictionFlagEncoding): TypeRestriction = when
	{
		flags and flagEncoding.mask != 0 -> this
		else -> restriction(
			type,
			constantOrNull,
			excludedTypes,
			excludedValues,
			flags or flagEncoding.mask)
	}

	/**
	 * Answer a restriction like the receiver, but for a boxed, mutable object.
	 * If the restriction is already for boxed objects, return the receiver,
	 * whether it's also marked with the immutable flag or not.
	 *
	 * @return
	 *   The new `TypeRestriction`, or the receiver.
	 */
	fun forBoxed(): TypeRestriction = when
	{
		hasFlag(BOXED_FLAG) -> this
		else -> restriction(
			type,
			constantOrNull,
			excludedTypes,
			excludedValues,
			BOXED_FLAG.mask)
	}

	/**
	 * Answer a restriction like the receiver, but for unboxed ints.  If the
	 * restriction is already for unboxed ints, return the receiver.
	 *
	 * @return
	 *   The new `TypeRestriction`, or the receiver.
	 */
	fun forUnboxedInt(): TypeRestriction = when
	{
		hasFlag(UNBOXED_INT_FLAG) -> this
		else -> restriction(
			type.typeIntersection(int32),
			constantOrNull,
			excludedTypes,
			excludedValues,
			UNBOXED_INT_FLAG.mask)
	}

	/**
	 * Answer a restriction like the receiver, but for unboxed floats.  If the
	 * restriction is already for unboxed floats, return the receiver.
	 *
	 * @return
	 *   The new `TypeRestriction`, or the receiver.
	 */
	fun forUnboxedFloat(): TypeRestriction = when
	{
		hasFlag(UNBOXED_FLOAT_FLAG) -> this
		else -> restriction(
			type.typeIntersection(Types.DOUBLE.o),
			constantOrNull,
			excludedTypes,
			excludedValues,
			UNBOXED_FLOAT_FLAG.mask)
	}

	/**
	 * Answer a restriction like the receiver, but with a flag cleared.
	 * If the flag is already clear, answer the receiver.
	 *
	 * @param flagEncoding
	 *   The [RestrictionFlagEncoding] to clear.
	 * @return
	 *   The new `TypeRestriction`, or the receiver.
	 */
	fun withoutFlag(flagEncoding: RestrictionFlagEncoding): TypeRestriction =
		if (flags and flagEncoding.mask == 0)
		{
			// Flag is already clear.
			this
		}
		else
		{
			restriction(
			type,
			constantOrNull,
			excludedTypes,
			excludedValues,
			flags and flagEncoding.mask.inv())
		}

	/**
	 * Answer a restriction like the receiver, but excluding
	 * [RegisterKind]-related flags that aren't set in the given
	 * `kindFlagEncoding`.
	 *
	 * @param kindFlagEncoding
	 *   The [RestrictionFlagEncoding] to clear.
	 * @return
	 *   The new `TypeRestriction`, or the receiver.
	 */
	fun restrictingKindsTo(kindFlagEncoding: Int): TypeRestriction
	{
		assert(kindFlagEncoding and RestrictionFlagEncoding.allKindsMask.inv()
			== 0)
		var newFlags = (flags and RestrictionFlagEncoding.allKindsMask.inv()) or
			kindFlagEncoding
		if (newFlags and IMMUTABLE_FLAG.mask != 0
			&& newFlags and BOXED_FLAG.mask == 0)
		{
			// It can't stay immutable if it's not also boxed.
			newFlags = newFlags and IMMUTABLE_FLAG.mask.inv()
		}
		if (newFlags == flags) return this
		return restriction(
			type, constantOrNull, excludedTypes, excludedValues, newFlags)
	}

	/**
	 * Answer a restriction like the receiver, but excluding
	 * [RegisterKind]-related flags that aren't set in the given
	 * `kindFlagEncoding`.
	 *
	 * @param kinds
	 *   The [RestrictionFlagEncoding] to clear.
	 * @return
	 *   The new `TypeRestriction`, or the receiver.
	 */
	fun restrictingKindsTo(kinds: EnumSet<RegisterKind>): TypeRestriction
	{
		return restrictingKindsTo(kinds.sumBy { it.restrictionFlag.mask })
	}

	/**
	 * If this restriction has only a finite set of possible values, and the
	 * number of such values is no more than the given maximum, answer an
	 * [A_Set] of them, otherwise `null`.
	 *
	 * @param maximumCount
	 *   The threshold above which `null` should be answered, even if there is a
	 *   finite set of potential values.
	 * @return
	 *   The [A_Set] of possible instances or `null`.
	 */
	fun enumerationValuesOrNull(maximumCount: Int): A_Set? =
		when
		{
			maximumCount >= 0 && this === bottomRestriction -> emptySet
			maximumCount >= 1 && constantOrNull !== null -> set(constantOrNull)
			type.isEnumeration && !type.isInstanceMeta
			   && type.instanceCount().lessOrEqual(fromInt(maximumCount)) ->
					type.instances()
			else -> null
		}

	/**
	 * Answer an [EnumSet] indicating which [RegisterKind]s are present in this
	 * restriction.
	 *
	 * @return
	 *   The [EnumSet] of [RegisterKind]s known to be available at some place
	 *   when an [L2Synonym] has this restriction.
	 */
	fun kinds(): EnumSet<RegisterKind> =
		RegisterKind.all.filterTo(EnumSet.noneOf(RegisterKind::class.java)) {
			(flags and it.restrictionFlag.mask) != 0
		}

	override fun equals(other: Any?): Boolean
	{
		if (other !is TypeRestriction)
		{
			return false
		}
		return (this === other
			|| (type.equals(other.type)
				&& constantOrNull === other.constantOrNull
				&& excludedTypes == other.excludedTypes
				&& excludedValues == other.excludedValues
				&& flags == other.flags))
	}

	override fun hashCode(): Int =
		Objects.hash(
			type, constantOrNull, excludedTypes, excludedValues, flags)

	/**
	 * Answer whether this `TypeRestriction` is a specialization of the given
	 * one.  That means every value that satisfies the receiver will also
	 * satisfy the argument.
	 *
	 * @param other
	 *   The other type restriction.
	 * @return
	 *   Whether the receiver is a specialization of the argument.
	 */
	@Suppress("unused")
	fun isStrongerThan(other: TypeRestriction): Boolean
	{
		if (flags.inv() and other.flags != 0)
		{
			return false
		}
		if (!type.isSubtypeOf(other.type))
		{
			return false
		}
		// I have to exclude at least every type excluded by the argument.
		for (otherExcludedType in other.excludedTypes)
		{
			if (excludedTypes.none { otherExcludedType.isSubtypeOf(it) })
			{
				return false
			}
		}
		// I also have to exclude every value excluded by the argument.
		for (otherExcludedValue in other.excludedValues)
		{
			if (!excludedValues.contains(otherExcludedValue)
				&& excludedTypes.none { otherExcludedValue.isInstanceOf(it) })
			{
				return false
			}
		}
		// Any additional exclusions that the receiver has are irrelevant, as
		// they only act to strengthen the restriction.
		return true
	}

	/**
	 * Answer a [String], possibly empty, suitable for displaying after a
	 * register, after a read/write of a register, or after any other place that
	 * this restriction might be applied.
	 *
	 * @return
	 *   The [String] describing this restriction, if interesting.
	 */
	fun suffixString(): String
	{
		val constant = constantOrNull
		if (constant !== null)
		{
			return "=" + constant.typeTag().name.replace(
				"_TAG", "")
		}
		return if (!type.equals(TOP.o))
		{
			":" + (type as AvailObject).typeTag().name
				.replace("_TAG", "")
		}
		else ""
	}

	override fun toString(): String =
		buildString {
			append("restriction(")
			if (constantOrNull !== null)
			{
				append("c=")
				var valueString = constantOrNull.toString()
				if (valueString.length > 50)
				{
					valueString = valueString.substring(0, 50) + '…'
				}
				valueString = valueString
					.replace("\n", "\\n")
					.replace("\t", "\\t")
				append(valueString)
			}
			else
			{
				append("t=")
				var typeString = type.toString()
				if (typeString.length > 50)
				{
					typeString = typeString.substring(0, 50) + '…'
				}
				append(typeString)
				if (excludedTypes.isNotEmpty())
				{
					append(", ex.t=")
					append(excludedTypes)
				}
				if (excludedValues.isNotEmpty())
				{
					append(", ex.v=")
					append(excludedValues)
				}
			}
			if (isImmutable)
			{
				append(", imm")
			}
			if (isBoxed)
			{
				append(", box")
			}
			if (isUnboxedInt)
			{
				append(", int")
			}
			if (isUnboxedFloat)
			{
				append(", float")
			}
			append(")")
		}

	companion object
	{
		/**
		 * The [TypeRestriction] for a register that holds [NilDescriptor.nil].
		 *
		 * It's marked as immutable because there is no way to create another
		 * [AvailObject] with a [NilDescriptor] as its descriptor.
		 */
		private val nilRestriction = TypeRestriction(
			TOP.o,
			NilDescriptor.nil,
			setOf(ANY.o),
			emptySet(),
			true,
			true,
			false,
			false)

		/**
		 * The [TypeRestriction] for a register that has any value whatsoever,
		 * including [NilDescriptor.nil], and is not known to be immutable.
		 */
		private val topRestriction = TypeRestriction(
			TOP.o,
			null,
			emptySet(),
			emptySet(),
			false,
			true,
			false,
			false)

		/**
		 * The [TypeRestriction] for a register that has any value whatsoever,
		 * including [NilDescriptor.nil], but is known to be immutable.
		 */
		private val topRestrictionImmutable = TypeRestriction(
			TOP.o,
			null,
			emptySet(),
			emptySet(),
			true,
			true,
			false,
			false)

		/**
		 * The [TypeRestriction] for a register that has any value whatsoever,
		 * excluding [NilDescriptor.nil], but it's not known to be immutable.
		 */
		val anyRestriction = TypeRestriction(
			ANY.o,
			null,
			emptySet(),
			emptySet(),
			false,
			true,
			false,
			false)

		/**
		 * The [TypeRestriction] for a register that has any value whatsoever,
		 * excluding [NilDescriptor.nil], but it's known to be immutable.
		 */
		private val anyRestrictionImmutable = TypeRestriction(
			ANY.o,
			null,
			emptySet(),
			emptySet(),
			true,
			true,
			false,
			false)

		/**
		 * The [TypeRestriction] for a register that cannot hold any value.
		 * This can be useful for cleanly dealing with unreachable code.
		 *
		 * It's marked as immutable because nothing can read from a register
		 * with this restriction.
		 */
		val bottomRestriction = TypeRestriction(
			BottomTypeDescriptor.bottom,
			null,
			emptySet(),
			emptySet(),
			true,
			true,  // Still considered boxed.
			false,
			false)

		/**
		 * The [TypeRestriction] for a register that can only hold the value
		 * bottom (i.e., the restriction type is bottom's type).  This is a
		 * sticky point in the type system, in that multiple otherwise unrelated
		 * type hierarchies share the (uninstantiable) type bottom as a
		 * descendant.
		 *
		 * Note that this restriction is marked as immutable because there is no
		 * way to create another [AvailObject] whose descriptor is a
		 * [BottomTypeDescriptor].
		 */
		private val bottomTypeRestriction = TypeRestriction(
			BottomTypeDescriptor.bottomMeta,
			BottomTypeDescriptor.bottom,
			emptySet(),
			emptySet(),
			true,
			true,
			false,
			false)

		/**
		 * Create or reuse an immutable `TypeRestriction` from the already
		 * mutually consistent, canonical arguments.
		 *
		 * @param givenType
		 *   The Avail type that constrains some value somewhere.
		 * @param givenConstantOrNull
		 *   Either `null` or the exact value that some value somewhere must
		 *   equal.
		 * @param givenExcludedTypes
		 *   A set of [A_Type]s to consider excluded.
		 * @param givenExcludedValues
		 *   A set of values to consider excluded.
		 * @param flags
		 *   The encoded [flags] [Int].
		 * @return
		 *   The new or existing canonical TypeRestriction.
		 */
		private fun fromCanonical(
			givenType: A_Type,
			givenConstantOrNull: A_BasicObject?,
			givenExcludedTypes: Set<A_Type>,
			givenExcludedValues: Set<A_BasicObject>,
			flags: Int): TypeRestriction
		{
			assert(BottomTypeDescriptor.bottom !in givenExcludedTypes)
			var type: A_Type = givenType.makeImmutable()
			givenExcludedTypes.forEach { it.makeImmutable() }
			givenExcludedValues.forEach { it.makeImmutable() }

			// Reduce the base type, if it knows how to trim itself for the
			// excluded types and instances.  For example, if the base type is
			// [5..10] and we exclude the type [3..6], the resulting type can be
			// reduced to [7..10].  If we also exclude [7..8], we have [9..10].
			// Note that if we ran these reductions in the reverse order, we
			// wouldn't be able to trim anything from the type when removing
			// [7..8] from [5..10], so we repeatedly iterate over the exclusions
			// until trimming makes no further change.
			do
			{
				val typeBefore = type.makeImmutable()
				givenExcludedTypes.forEach { excludedType ->
					type = type.trimType(excludedType)
				}
				givenExcludedValues.forEach { excludedValue ->
					// Due to conservative metacovariance, this value, a type,
					// must not be wrapped in an instanceMeta, since that would
					// exclude other instances that are not supposed to be
					// removed by this *value* exclusion.
					if (!excludedValue.isType
						|| (excludedValue as A_Type).isBottom)
					{
						type = type.trimType(
							instanceTypeOrMetaOn(excludedValue))
					}
				}
			}
			while (!type.equals(typeBefore))

			return when
			{
				givenConstantOrNull !== null ->
				{
					// A constant was specified.  Use it if it satisfies the
					// main type constraint and isn't specifically excluded,
					// otherwise use the bottomRestriction, which is the
					// impossible restriction.
					if (givenConstantOrNull.equalsNil())
					{
						nilRestriction
					}
					else
					{
						assert(givenConstantOrNull.isInstanceOf(type))
						assert(givenConstantOrNull !in givenExcludedValues)
						assert(
							givenExcludedTypes
								.none { givenConstantOrNull.isInstanceOf(it) })
						// No reason to exclude it, so use the constant.  We can
						// safely omit the excluded types and values as part of
						// canonicalization.
						TypeRestriction(
							instanceTypeOrMetaOn(givenConstantOrNull),
							givenConstantOrNull,
							emptySet(),
							emptySet(),
							flags)
					}
				}
				// Not a known constant.
				givenExcludedTypes.isEmpty() && givenExcludedValues.isEmpty() ->
				{
					if (type.equals(TOP.o))
					{
						if (flags and IMMUTABLE_FLAG.mask != 0)
						{
							topRestrictionImmutable
						}
						else
						{
							topRestriction
						}
					}
					else if (type.equals(ANY.o))
					{
						if (flags and IMMUTABLE_FLAG.mask != 0)
						{
							anyRestrictionImmutable
						}
						else
						{
							anyRestriction
						}
					}
					else if (type.instanceCount().equalsInt(1)
						&& !type.isInstanceMeta)
					{
						// This is a non-meta instance type, which should be
						// treated as a constant restriction.
						val instance = type.instance()
						if (instance.isBottom)
						{
							// Special case: bottom's type has one instance,
							// bottom.
							bottomTypeRestriction
						}
						else
						{
							TypeRestriction(
								type, instance, emptySet(), emptySet(), flags)
						}
					}
					else
					{
						TypeRestriction(
							type,
							null,
							givenExcludedTypes,
							givenExcludedValues,
							flags)
					}
				}
				else -> TypeRestriction(
					type, null, givenExcludedTypes, givenExcludedValues, flags)
			}
		}

		/**
		 * Create or reuse an immutable `TypeRestriction`, canonicalizing the
		 * arguments.
		 *
		 * @param type
		 *   The Avail type that constrains some value somewhere.
		 * @param constantOrNull
		 *   Either `null` or the exact value that some value somewhere must
		 *   equal.
		 * @param givenExcludedTypes
		 *   A set of [A_Type]s to consider excluded.
		 * @param givenExcludedValues
		 *   A set of values to consider excluded.
		 * @param isImmutable
		 *   Whether the value is known to be immutable.
		 * @param isBoxed
		 *   Whether this value is known to already reside in an
		 *   [L2BoxedRegister].
		 * @param isUnboxedInt
		 *   Whether this value is known to already reside in an
		 *   [L2IntRegister].
		 * @param isUnboxedFloat
		 *   Whether this value is known to already reside in an
		 *   [L2FloatRegister].
		 * @return
		 *   The new or existing canonical TypeRestriction.
		 */
		@JvmOverloads
		fun restriction(
			type: A_Type,
			constantOrNull: A_BasicObject?,
			givenExcludedTypes: Set<A_Type> = emptySet(),
			givenExcludedValues: Set<A_BasicObject> = emptySet(),
			isImmutable: Boolean = false,
			isBoxed: Boolean = true,
			isUnboxedInt: Boolean = false,
			isUnboxedFloat: Boolean = false
		): TypeRestriction
		{
			val flags = ((if (isImmutable) IMMUTABLE_FLAG.mask else 0)
				or (if (isBoxed) BOXED_FLAG.mask else 0)
				or (if (isUnboxedInt) UNBOXED_INT_FLAG.mask else 0)
				or if (isUnboxedFloat) UNBOXED_FLOAT_FLAG.mask else 0)
			return restriction(
				type,
				constantOrNull,
				givenExcludedTypes,
				givenExcludedValues,
				flags)
		}

		/**
		 * Create or reuse an immutable `TypeRestriction`, canonicalizing the
		 * arguments.
		 *
		 * @param type
		 *   The Avail type that constrains some value somewhere.
		 * @param constantOrNull
		 *   Either `null` or the exact value that some value somewhere must
		 *   equal.
		 * @param givenExcludedTypes
		 *   A set of [A_Type]s to consider excluded.
		 * @param givenExcludedValues
		 *   A set of values to consider excluded.
		 * @param flags
		 *   The encoded [flags] [Int].
		 * @return
		 *   The new or existing canonical `TypeRestriction`.
		 */
		fun restriction(
			type: A_Type,
			constantOrNull: A_BasicObject?,
			givenExcludedTypes: Set<A_Type>,
			givenExcludedValues: Set<A_BasicObject>,
			flags: Int): TypeRestriction
		{
			if (constantOrNull === null && type.isEnumeration
				&& (!type.isInstanceMeta || type.instance().isBottom))
			{
				// No constant was specified, but the type is a non-meta
				// enumeration (or bottom's type, which has only one instance,
				// bottom).  See if excluding disallowed types and values
				// happens to leave exactly zero or one possibility.
				val instances = type.instances().toMutableSet()
				instances.removeAll(givenExcludedValues)
				instances.removeIf { instance ->
					givenExcludedTypes.any { aType ->
						instance.isInstanceOf(aType)
					}
				}
				return when (instances.size)
				{
					0 -> bottomRestriction
					1 ->
					{
						val instance: A_BasicObject = instances.single()
						fromCanonical(
							instanceTypeOrMetaOn(instance),
							instance,
							emptySet(),
							emptySet(),
							flags)
					}
					else ->
					{
						// We've already applied the full effect of the excluded
						// types and values to the given type.
						TypeRestriction(
							enumerationWith(setFromCollection(instances)),
							null,
							emptySet(),
							emptySet(),
							flags)
					}
				}
			}
			if (constantOrNull !== null)
			{
				// A constant was specified.  Use it if it satisfies the main
				// type constraint and isn't specifically excluded, otherwise
				// use the bottomRestriction, which is the impossible
				// restriction.
				if (constantOrNull.equalsNil())
				{
					return nilRestriction
				}
				if (!constantOrNull.isInstanceOf(type)
					|| givenExcludedValues.contains(constantOrNull))
				{
					return bottomRestriction
				}
				for (excludedType in givenExcludedTypes)
				{
					if (constantOrNull.isInstanceOf(excludedType))
					{
						return bottomRestriction
					}
				}
				// No reason to exclude it, so use the constant.  We can safely
				// omit the excluded types and values as part of canonicalization.
				// Note that even though we make the constant immutable here, and
				// the value passing through registers at runtime will be equal to
				// it, it might be a different Java AvailObject that's still
				// mutable.
				constantOrNull.makeImmutable()
				return TypeRestriction(
					instanceTypeOrMetaOn(constantOrNull),
					constantOrNull,
					emptySet(),
					emptySet(),
					flags)
			}

			// Are we excluding the base type?
			if (givenExcludedTypes.any { type.isSubtypeOf(it) })
			{
				return bottomRestriction
			}

			// Eliminate excluded types that are proper subtypes of other excluded
			// types.  Note: this reduction is O(n^2) in the number of excluded
			// types.  We could use a LookupTree to speed this up.
			val excludedValues: MutableSet<A_BasicObject> =
				givenExcludedValues.toMutableSet()
			val excludedTypes =
				givenExcludedTypes.mapToSet { type.typeIntersection(it) }

			excludedTypes.remove(BottomTypeDescriptor.bottom)
			val iterator = excludedTypes.iterator()
			iterator.forEachRemaining { t: A_Type ->
				if (t.isEnumeration && !t.isInstanceMeta)
				{
					// Convert an excluded enumeration into individual excluded
					// values.
					for (v in t.instances())
					{
						excludedValues.add(v)
					}
				}
				else if (excludedTypes.any { isProperSubtype(t, it) })
				{
					iterator.remove()
				}
			}

			// Eliminate excluded values that are already under an excluded type, or
			// are not under the given type.
			excludedValues.removeIf { v: A_BasicObject ->
				!v.isInstanceOf(type) || excludedTypes.any { v.isInstanceOf(it) }
			}
			return if (type.equals(TOP.o)
					   && excludedTypes.isEmpty()
					   && excludedValues.isEmpty())
			{
				topRestriction
			}
			else
			{
				fromCanonical(
					type,
					null,
					excludedTypes,
					excludedValues,
					flags)
			}
		}

		/**
		 * Create or reuse a `TypeRestriction`, for which no constant
		 * information is provided (but might be deduced from the type).
		 *
		 * @param type
		 *   The Avail type that constrains some value somewhere.
		 * @param encoding
		 *   A [RestrictionFlagEncoding] indicating the type of register that
		 *   will hold this value ([BOXED_FLAG], [UNBOXED_INT_FLAG], or
		 *   [UNBOXED_FLOAT_FLAG]).
		 * @return
		 *   The new or existing canonical TypeRestriction.
		 */
		fun restrictionForType(
			type: A_Type,
			encoding: RestrictionFlagEncoding): TypeRestriction
		{
			return restriction(
				type,
				null,
				emptySet(),
				emptySet(),
				encoding.mask)
		}

		/**
		 * Create or reuse a `TypeRestriction`, for which no constant
		 * information is provided (but might be deduced from the type).
		 *
		 * If the requested register encoding is [BOXED_FLAG], also flag the
		 * restriction as [IMMUTABLE_FLAG].
		 *
		 * @param constant
		 *   The sole Avail value that this restriction permits.
		 * @param encoding
		 *   A [RestrictionFlagEncoding] indicating the type of register that
		 *   will hold this value ([BOXED_FLAG], [UNBOXED_INT_FLAG], or
		 *   [UNBOXED_FLOAT_FLAG]).
		 * @return
		 *   The new or existing canonical TypeRestriction.
		 */
		fun restrictionForConstant(
			constant: A_BasicObject,
			encoding: RestrictionFlagEncoding): TypeRestriction
		{
			assert(encoding == BOXED_FLAG
				   || encoding == UNBOXED_INT_FLAG
				   || encoding == UNBOXED_FLOAT_FLAG)
			constant.makeImmutable()
			return restriction(
				if (constant.equalsNil())
				{
					TOP.o
				}
				else
				{
					instanceTypeOrMetaOn(constant)
				},
				constant,
				emptySet(),
				emptySet(),
				encoding.mask
					or if (encoding == BOXED_FLAG)
					{
						IMMUTABLE_FLAG.mask
					}
					else
					{
						0
					})
		}
	}

	init
	{
		// Make the Avail objects immutable.  They'll be made Shared if they
		// survive the L2 translation and end up in an L2Chunk.
		this.type = type.makeImmutable()
		this.constantOrNull = constantOrNull?.makeImmutable()
		this.excludedTypes = excludedTypes.toSet()
		this.excludedValues = excludedValues.toSet()
		this.flags = flags
	}
}
