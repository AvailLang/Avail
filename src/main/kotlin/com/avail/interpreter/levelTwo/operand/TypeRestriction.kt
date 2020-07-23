/*
 * TypeRestriction.kt
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
package com.avail.interpreter.levelTwo.operand

import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import com.avail.descriptor.sets.SetDescriptor.Companion.toSet
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import com.avail.descriptor.types.BottomTypeDescriptor
import com.avail.descriptor.types.InstanceMetaDescriptor
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeDescriptor.Companion.isProperSubtype
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_EQUALS_CONSTANT
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_CONSTANT
import com.avail.interpreter.levelTwo.register.L2BoxedRegister
import com.avail.interpreter.levelTwo.register.L2FloatRegister
import com.avail.interpreter.levelTwo.register.L2IntRegister
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind
import com.avail.optimizer.L2Synonym
import java.util.*

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
	/**
	 * The type of value that known to be in this register if this control
	 * flow path is taken.
	 */
	@JvmField
	val type: A_Type

	/**
	 * The exact value that is known to be in this register if this control flow
	 * path is taken, or `null` if unknown.
	 */
	@JvmField
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
		/** Whether the value is known to be immutable.  */
		IMMUTABLE,

		/**
		 * Whether the value is available in a boxed form in some
		 * [L2BoxedRegister].
		 */
		BOXED,

		/**
		 * Whether the value is available in an unboxed form in some
		 * [L2IntRegister].
		 */
		UNBOXED_INT,

		/**
		 * Whether the value is available in an unboxed form in some
		 * [L2FloatRegister].
		 */
		UNBOXED_FLOAT;

		/** A pre-computed bit mask for this flag.  */
		@JvmField
		val mask = 1 shl ordinal

		companion object
		{
			/**
			 * A pre-computed bit mask for just the [RegisterKind]-related
			 * flags.
			 */
			val allKindsMask =
				BOXED.mask or UNBOXED_INT.mask or UNBOXED_FLOAT.mask
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

	/** Answer whether the restricted value is known to be immutable.  */
	val isImmutable: Boolean
		get() = flags and RestrictionFlagEncoding.IMMUTABLE.mask != 0

	/**
	 * Answer whether the restricted value is known to be boxed in an
	 * [L2BoxedRegister].
	 */
	val isBoxed: Boolean
		get() = flags and RestrictionFlagEncoding.BOXED.mask != 0

	/**
	 * Answer whether the restricted value is known to be unboxed in an
	 * [L2IntRegister].
	 */
	val isUnboxedInt: Boolean
		get() = flags and RestrictionFlagEncoding.UNBOXED_INT.mask != 0

	/**
	 * Answer whether the restricted value is known to be unboxed in an
	 * [L2FloatRegister].
	 */
	val isUnboxedFloat: Boolean
		get() = flags and RestrictionFlagEncoding.UNBOXED_FLOAT.mask != 0

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
		isUnboxedFloat: Boolean) : this(
		type,
		constantOrNull,
		excludedTypes,
		excludedValues,
		(if (isImmutable) RestrictionFlagEncoding.IMMUTABLE.mask else 0)
			or (if (isBoxed) RestrictionFlagEncoding.BOXED.mask else 0)
			or (if (isUnboxedInt) RestrictionFlagEncoding.UNBOXED_INT.mask else 0)
			or if (isUnboxedFloat) RestrictionFlagEncoding.UNBOXED_FLOAT.mask else 0)

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
			return restrictionForConstant(type, RestrictionFlagEncoding.BOXED)
		}
		val resultExcludedValues: MutableSet<A_BasicObject> = HashSet()
		// No object has exact type ⊥ or ⊤.
		resultExcludedValues.add(Types.TOP.o())
		for (v in excludedValues)
		{
			resultExcludedValues.add(
				instanceTypeOrMetaOn(v))
		}
		val resultExcludedTypes: MutableSet<A_Type> = HashSet()
		resultExcludedTypes.add(BottomTypeDescriptor.bottomMeta())
		for (t in excludedTypes)
		{
			resultExcludedTypes.add(InstanceMetaDescriptor.instanceMeta(t))
		}
		return restriction(
			InstanceMetaDescriptor.instanceMeta(type),
			null,
			resultExcludedTypes,
			resultExcludedValues,
			RestrictionFlagEncoding.BOXED.mask)
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
		val mutualTypeIntersections: MutableSet<A_Type> = HashSet()
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
		val newExcludedValues: MutableSet<A_BasicObject> = HashSet()
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
	fun intersection(other: TypeRestriction): TypeRestriction
	{
		if (constantOrNull !== null && other.constantOrNull !== null
			&& !constantOrNull.equals(other.constantOrNull))
		{
			// The restrictions are both constant, but disagree, so the
			// intersection is empty.
			return bottomRestriction
		}
		val unionOfExcludedTypes: MutableSet<A_Type> = HashSet(excludedTypes)
		unionOfExcludedTypes.addAll(other.excludedTypes)
		val unionOfExcludedValues: MutableSet<A_BasicObject> =
			HashSet(excludedValues)
		unionOfExcludedValues.addAll(other.excludedValues)
		return restriction(
			type.typeIntersection(other.type),
			constantOrNull ?: other.constantOrNull,
			unionOfExcludedTypes,
			unionOfExcludedValues,
			flags or other.flags)
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
	fun intersectionWithType(typeToIntersect: A_Type): TypeRestriction
	{
		return restriction(
			type.typeIntersection(typeToIntersect),
			constantOrNull,
			excludedTypes,
			excludedValues,
			flags)
	}

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
	fun minusType(typeToExclude: A_Type): TypeRestriction
	{
		val augmentedExcludedTypes: MutableSet<A_Type> = HashSet(excludedTypes)
		augmentedExcludedTypes.add(typeToExclude)
		return restriction(
			type,
			constantOrNull,
			augmentedExcludedTypes,
			excludedValues,
			flags)
	}

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
	fun minusValue(valueToExclude: A_BasicObject): TypeRestriction
	{
		val augmentedExcludedValues: MutableSet<A_BasicObject> =
			HashSet(excludedValues)
		augmentedExcludedValues.add(valueToExclude)
		return restriction(
			type,
			constantOrNull,
			excludedTypes,
			augmentedExcludedValues,
			flags)
	}

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
		if (constantOrNull !== null)
		{
			return if (constantOrNull.isType && !constantOrNull.isBottom)
			{
				// The value is known to be a type other than bottom, so there
				// is no possible testType that could contain just this
				// constant as a member.
				false
			}
			else
			{
				testType.equals(
					instanceTypeOrMetaOn(
						constantOrNull))
			}
		}
		if (!testType.isSubtypeOf(type))
		{
			return false
		}
		for (excludedType in excludedTypes)
		{
			if (!excludedType.typeIntersection(testType).isBottom)
			{
				return false
			}
		}
		for (excludedValue in excludedValues)
		{
			if (excludedValue.isInstanceOf(testType))
			{
				return false
			}
		}
		return true
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
		if (excludedTypes.any(intersectedType::isSubtypeOf))
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
	fun withFlag(flagEncoding: RestrictionFlagEncoding): TypeRestriction =
		if (flags and flagEncoding.mask != 0)
		{
			// Flag is already set.
			this
		}
		else
		{
			restriction(
				type,
				constantOrNull,
				excludedTypes,
				excludedValues,
				flags or flagEncoding.mask)
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
	fun restrictingKindsTo(
		kindFlagEncoding: Int): TypeRestriction
	{
		assert(kindFlagEncoding and RestrictionFlagEncoding.allKindsMask.inv() == 0)
		val newFlags =
			flags and RestrictionFlagEncoding.allKindsMask.inv() or kindFlagEncoding
		return if (newFlags == flags)
		{
			this
		}
		else
		{
			restriction(
				type,
				constantOrNull,
				excludedTypes,
				excludedValues,
				newFlags)
		}
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
	fun kinds(): EnumSet<RegisterKind>
	{
		val set = EnumSet.noneOf(RegisterKind::class.java)
		if (isBoxed)
		{
			set.add(RegisterKind.BOXED)
		}
		if (isUnboxedInt)
		{
			set.add(RegisterKind.INTEGER)
		}
		if (isUnboxedFloat)
		{
			set.add(RegisterKind.FLOAT)
		}
		return set
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
		return if (!type.equals(Types.TOP.o()))
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
				append(type)
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
			Types.TOP.o(),
			NilDescriptor.nil,
			setOf(Types.ANY.o()),
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
			Types.TOP.o(),
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
			Types.TOP.o(),
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
		@JvmField
		val anyRestriction = TypeRestriction(
			Types.ANY.o(),
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
			Types.ANY.o(),
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
		@JvmField
		val bottomRestriction = TypeRestriction(
			BottomTypeDescriptor.bottom(),
			null,
			emptySet(),
			emptySet(),
			true,
			false,
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
			BottomTypeDescriptor.bottomMeta(),
			BottomTypeDescriptor.bottom(),
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
			assert(!givenExcludedTypes.contains(BottomTypeDescriptor.bottom()))
			givenExcludedTypes.forEach { it.makeImmutable() }
			givenExcludedValues.forEach { it.makeImmutable() }
			return when
			{
				givenConstantOrNull !== null ->
				{
					// A constant was specified.  Use it if it satisfies the main type
					// constraint and isn't specifically excluded, otherwise use the
					// bottomRestriction, which is the impossible restriction.
					if (givenConstantOrNull.equalsNil())
					{
						nilRestriction
					}
					else
					{
						assert(givenConstantOrNull.isInstanceOf(givenType))
						assert(!givenExcludedValues.contains(givenConstantOrNull))
						assert(givenExcludedTypes
							   .none { givenConstantOrNull.isInstanceOf(it) })
						// No reason to exclude it, so use the constant.  We can safely
						// omit the excluded types and values as part of canonicalization.
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
					if (givenType.equals(Types.TOP.o()))
					{
						if (flags and RestrictionFlagEncoding.IMMUTABLE.mask != 0)
						{
							topRestrictionImmutable
						}
						else
						{
							topRestriction
						}
					}
					else if (givenType.equals(Types.ANY.o()))
					{
						if (flags and RestrictionFlagEncoding.IMMUTABLE.mask != 0)
						{
							anyRestrictionImmutable
						}
						else
						{
							anyRestriction
						}
					}
					else if (givenType.instanceCount().equalsInt(1)
						&& !givenType.isInstanceMeta)
					{
						// This is a non-meta instance type, which should be treated
						// as a constant restriction.
						val instance = givenType.instance()
						if (instance.isBottom)
						{
							// Special case: bottom's type has one instance, bottom.
							bottomTypeRestriction
						}
						else
						{
							TypeRestriction(
								givenType,
								instance,
								emptySet(),
								emptySet(),
								flags)
						}
					}
					else
					{
						TypeRestriction(
							givenType,
							null,
							givenExcludedTypes,
							givenExcludedValues,
							flags)
					}
				}
				else -> TypeRestriction(
					givenType,
					null,
					givenExcludedTypes,
					givenExcludedValues,
					flags)
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
			givenExcludedTypes: Set<A_Type> =
				emptySet(),
			givenExcludedValues: Set<A_BasicObject> =
				emptySet(),
			isImmutable: Boolean =
				false,
			isBoxed: Boolean =
				true,
			isUnboxedInt: Boolean =
				false,
			isUnboxedFloat: Boolean =
				false): TypeRestriction
		{
			val flags = ((if (isImmutable) RestrictionFlagEncoding.IMMUTABLE.mask else 0)
				or (if (isBoxed) RestrictionFlagEncoding.BOXED.mask else 0)
				or (if (isUnboxedInt) RestrictionFlagEncoding.UNBOXED_INT.mask else 0)
				or if (isUnboxedFloat) RestrictionFlagEncoding.UNBOXED_FLOAT.mask else 0)
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
				// No constant was specified, but the type is a non-meta enumeration
				// (or bottom's type, which has only one instance, bottom).  See if
				// excluding disallowed types and values happens to leave exactly
				// zero or one possibility.
				val instances =
					toSet(type.instances()).toMutableSet()
				instances.removeAll(givenExcludedValues)
				instances.removeIf { instance: AvailObject ->
					givenExcludedTypes.any { aType: A_Type ->
							instance.isInstanceOf(aType)
					}
				}
				return when (instances.size)
				{
					0 -> bottomRestriction
					1 ->
					{
						val instance: A_BasicObject =
							instances.iterator().next()
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
				// A constant was specified.  Use it if it satisfies the main type
				// constraint and isn't specifically excluded, otherwise use the
				// bottomRestriction, which is the impossible restriction.
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
				givenExcludedTypes.map { type.typeIntersection(it) }.toMutableSet()

			excludedTypes.remove(BottomTypeDescriptor.bottom())
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
			return if (type.equals(Types.TOP.o())
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
		 *   will hold this value ([RestrictionFlagEncoding.BOXED],
		 *   [RestrictionFlagEncoding.UNBOXED_INT], or
		 *   [RestrictionFlagEncoding.UNBOXED_FLOAT]).
		 * @return
		 *   The new or existing canonical TypeRestriction.
		 */
		@JvmStatic
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
		 * If the requested register encoding is
		 * [RestrictionFlagEncoding.BOXED], also flag the restriction as
		 * [RestrictionFlagEncoding.IMMUTABLE].
		 *
		 * @param constant
		 *   The sole Avail value that this restriction permits.
		 * @param encoding
		 *   A [RestrictionFlagEncoding] indicating the type of register that
		 *   will hold this value ([RestrictionFlagEncoding.BOXED],
		 *   [RestrictionFlagEncoding.UNBOXED_INT], or
		 *   [RestrictionFlagEncoding.UNBOXED_FLOAT]).
		 * @return
		 * The new or existing canonical TypeRestriction.
		 */
		@JvmStatic
		fun restrictionForConstant(
			constant: A_BasicObject,
			encoding: RestrictionFlagEncoding): TypeRestriction
		{
			assert(encoding == RestrictionFlagEncoding.BOXED
				   || encoding == RestrictionFlagEncoding.UNBOXED_INT
				   || encoding == RestrictionFlagEncoding.UNBOXED_FLOAT)
			constant.makeImmutable()
			return restriction(
				if (constant.equalsNil())
				{
					Types.TOP.o()
				}
				else
				{
					instanceTypeOrMetaOn(constant)
				},
				constant,
				emptySet(),
				emptySet(),
				encoding.mask
					or if (encoding == RestrictionFlagEncoding.BOXED)
					{
						RestrictionFlagEncoding.IMMUTABLE.mask
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
		val typesSize = excludedTypes.size
		this.excludedTypes = when (typesSize)
		{
			0 ->
			{
				emptySet()
			}
			1 ->
			{
				setOf(excludedTypes.iterator().next())
			}
			else ->
			{
				Collections.unmodifiableSet(HashSet(excludedTypes))
			}
		}
		val constantsSize = excludedValues.size
		this.excludedValues =
			when (constantsSize)
			{
				0 ->
				{
					emptySet()
				}
				1 -> setOf(excludedValues.iterator().next())
				else -> Collections.unmodifiableSet(HashSet(excludedValues))
			}
		this.flags = flags
	}
}
