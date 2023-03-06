/*
 * IntegerRangeTypeDescriptor.kt
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
package avail.descriptor.types

import avail.annotations.ThreadSafe
import avail.descriptor.character.CharacterDescriptor.Companion.maxCodePointInt
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.equalsInfinity
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.greaterThan
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.A_Number.Companion.isLong
import avail.descriptor.numbers.A_Number.Companion.isPositive
import avail.descriptor.numbers.A_Number.Companion.lessThan
import avail.descriptor.numbers.A_Number.Companion.noFailMinusCanDestroy
import avail.descriptor.numbers.A_Number.Companion.noFailPlusCanDestroy
import avail.descriptor.numbers.AbstractNumberDescriptor.Sign.NEGATIVE
import avail.descriptor.numbers.AbstractNumberDescriptor.Sign.POSITIVE
import avail.descriptor.numbers.InfinityDescriptor
import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractDescriptor
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine3
import avail.descriptor.representation.AvailObject.Companion.error
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.Mutability.IMMUTABLE
import avail.descriptor.representation.Mutability.MUTABLE
import avail.descriptor.representation.Mutability.SHARED
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfIntegerRangeType
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.lowerInclusive
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfIntegerRangeType
import avail.descriptor.types.A_Type.Companion.typeUnionOfIntegerRangeType
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.A_Type.Companion.upperInclusive
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.ObjectSlots.LOWER_BOUND
import avail.descriptor.types.IntegerRangeTypeDescriptor.ObjectSlots.UPPER_BOUND
import avail.descriptor.types.PojoTypeDescriptor.Companion.byteRange
import avail.descriptor.types.PojoTypeDescriptor.Companion.charRange
import avail.descriptor.types.PojoTypeDescriptor.Companion.intRange
import avail.descriptor.types.PojoTypeDescriptor.Companion.longRange
import avail.descriptor.types.PojoTypeDescriptor.Companion.shortRange
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.serialization.SerializerOperation
import avail.utility.structures.EnumMap
import avail.utility.structures.EnumMap.Companion.enumMap
import org.availlang.json.JSONWriter
import java.math.BigInteger
import java.util.IdentityHashMap

/**
 * My instances represent the types of one or more extended integers. There are
 * lower and upper bounds, and flags to indicate whether those bounds are to be
 * treated as inclusive or exclusive of the bounds themselves.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property lowerInclusive
 *   When true, my object instances (i.e., instances of [AvailObject]) are
 *   considered to include their lower bound.
 * @property upperInclusive
 *   When true, my object instances (i.e., instances of [AvailObject]) are
 *   considered to include their upper bound.
 * @constructor
 * Construct a new [IntegerRangeTypeDescriptor].
 *
 * @param mutability
 *   The [Mutability] of the descriptor.
 * @param lowerInclusive
 *   Do my object instances include their lower bound?
 * @param upperInclusive
 *   Do my object instances include their upper bound?
 */
class IntegerRangeTypeDescriptor
private constructor(
	mutability: Mutability,
	private val lowerInclusive: Boolean,
	private val upperInclusive: Boolean
) : TypeDescriptor(
	mutability,
	TypeTag.EXTENDED_INTEGER_TYPE_TAG,
	TypeTag.UNKNOWN_TAG,
	ObjectSlots::class.java,
	null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The extended integer which is the lower bound of this range. It is
		 * either inclusive or exclusive depending on the
		 * [A_Type.lowerInclusive] flag.
		 */
		LOWER_BOUND,

		/**
		 * The extended integer which is the upper bound of this range. It is
		 * either inclusive or exclusive depending on the
		 * [A_Type.upperInclusive] flag.
		 */
		UPPER_BOUND
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append(if (lowerInclusive) '[' else '(')
		self[LOWER_BOUND].printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
		builder.append("..")
		self[UPPER_BOUND].printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
		builder.append(if (upperInclusive) ']' else ')')
	}

	override fun o_ComputeInstanceTag(self: AvailObject): TypeTag
	{
		val lower = self[LOWER_BOUND]
		val upper = self[UPPER_BOUND]
		return when
		{
			!lower.isFinite && lowerInclusive -> TypeTag.EXTENDED_INTEGER_TAG
			!upper.isFinite && upperInclusive -> TypeTag.EXTENDED_INTEGER_TAG
			lower.greaterThan(zero) -> TypeTag.NATURAL_NUMBER_TAG
			lower.equalsInt(0) -> TypeTag.WHOLE_NUMBER_TAG
			else -> TypeTag.INTEGER_TAG
		}
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsIntegerRangeType(self)

	override fun o_EqualsIntegerRangeType(
		self: AvailObject,
		another: A_Type
	): Boolean = (self[LOWER_BOUND].equals(another.lowerBound)
		&& self[UPPER_BOUND].equals(another.upperBound)
		&& lowerInclusive == another.lowerInclusive
		&& upperInclusive == another.upperInclusive)

	/**
	 * {@inheritDoc}
	 *
	 * Answer the object's hash value.  Be careful, as the range (10..20) is the
	 * same type as the range [11..19], so they should hash the same.  Actually,
	 * this is taken care of during instance creation - if an exclusive bound is
	 * finite, it is converted to its inclusive equivalent.  Otherwise, asking
	 * for one of the bounds would yield a value which is either inside or
	 * outside depending on something that should not be observable (because it
	 * serves to distinguish two representations of equal objects).
	 */
	override fun o_Hash(self: AvailObject): Int =
		computeHash(
			self[LOWER_BOUND].hash(),
			self[UPPER_BOUND].hash(),
			lowerInclusive,
			upperInclusive)

	override fun o_IsIntegerRangeType(self: AvailObject): Boolean = true

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfIntegerRangeType(self)

	/**
	 * Integer range types compare like the subsets they represent. The only
	 * elements that matter in the comparisons are within one unit of the four
	 * boundary conditions (because these are the only places where the type
	 * memberships can change), so just use these. In particular, use the value
	 * just inside and the value just outside each boundary. If the subtype's
	 * constraints don't logically imply the supertype's constraints then the
	 * subtype is not actually a subtype. Make use of the fact that integer
	 * range types have their bounds canonized into inclusive form, if finite,
	 * at range type creation time.
	 */
	override fun o_IsSupertypeOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): Boolean
	{
		val subMinObject = anIntegerRangeType.lowerBound
		val superMinObject = self[LOWER_BOUND]
		if (subMinObject.lessThan(superMinObject))
		{
			return false
		}
		if (subMinObject.equals(superMinObject)
			&& anIntegerRangeType.lowerInclusive
			&& !lowerInclusive)
		{
			return false
		}
		val subMaxObject = anIntegerRangeType.upperBound
		val superMaxObject: A_Number = self[UPPER_BOUND]
		return if (superMaxObject.lessThan(subMaxObject))
		{
			false
		}
		else !superMaxObject.equals(subMaxObject)
			|| !anIntegerRangeType.upperInclusive
			|| upperInclusive
	}

	override fun o_LowerBound(self: AvailObject): A_Number =
		self[LOWER_BOUND]

	override fun o_LowerInclusive(self: AvailObject): Boolean =
		lowerInclusive

	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?
	): Any? = when
	{
		self.isSubtypeOf(byteRange()) ->
			java.lang.Byte::class.javaPrimitiveType
		self.isSubtypeOf(charRange()) ->
			java.lang.Character::class.javaPrimitiveType
		self.isSubtypeOf(shortRange()) ->
			java.lang.Short::class.javaPrimitiveType
		self.isSubtypeOf(intRange()) ->
			java.lang.Integer::class.javaPrimitiveType
		self.isSubtypeOf(longRange()) ->
			java.lang.Long::class.javaPrimitiveType
		// If the integer range type is something else, then treat the type as
		// opaque.
		self.isSubtypeOf(integers) -> BigInteger::class.java
		else -> super.o_MarshalToJava(self, classHint)
	}

	override fun o_RangeIncludesLong(self: AvailObject, aLong: Long): Boolean
	{
		val lower: A_Number = self[LOWER_BOUND]
		val asInteger: A_Number
		when
		{
			lower.isLong && aLong < lower.extractLong -> return false
			!lower.isFinite && lower.isPositive -> return false
			else ->
			{
				asInteger = fromLong(aLong)
				if (asInteger.lessThan(lower))
				{
					return false
				}
			}
		}
		val upper: A_Number = self[UPPER_BOUND]
		return when
		{
			upper.isLong -> aLong <= upper.extractLong
			!upper.isFinite -> upper.isPositive
			else -> !upper.lessThan(asInteger)
		}
	}

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.INTEGER_RANGE_TYPE

	override fun o_TrimType(self: AvailObject, typeToRemove: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(typeToRemove) -> bottom
			!typeToRemove.isIntegerRangeType -> self
			// Don't use a conservative union of the values' types.
			typeToRemove.isEnumeration &&
				!typeToRemove.instanceCount.equalsInt(1) -> self
			(!lowerInclusive
					&& typeToRemove.lowerBound.equals(negativeInfinity))
				|| self.lowerBound.isInstanceOf(typeToRemove) ->
			{
				// The x&&y part of the condition means it starts at -∞
				// exclusive.  The other clause allows a lower bound that's a
				// member of typeToRemove.
				//
				// (-∞..m] - (-∞..q]           -> (q..m]
				// (-∞..m] - [-∞..q]           -> (q..m]
				// [-∞..-∞] - (-∞..q]          -> excluded (not element)
				// [-∞..-∞] - [-∞..q]          -> excluded (subtype)
				// [-∞..-∞] - [p..q]           -> excluded (not element)
				// [-∞..m] - (-∞..q]           -> excluded (not element)
				// [-∞..m] - [-∞..q]           -> (q..m]
				// [-∞..m] - [p..q]            -> excluded (not element)
				// [m..n] - [p..q] where p≤m≤q -> (q..m]
				integerRangeType(
					typeToRemove.upperBound,
					typeToRemove.upperBound.equalsInfinity(POSITIVE),
					positiveInfinity,
					true
				).typeIntersection(self)
			}
			(!upperInclusive
					&& typeToRemove.upperBound.equals(positiveInfinity))
				|| self.upperBound.isInstanceOf(typeToRemove) ->
			{
				// By symmetry on the other bound.
				integerRangeType(
					negativeInfinity,
					true,
					typeToRemove.lowerBound,
					typeToRemove.lowerBound.equalsInfinity(NEGATIVE)
				).typeIntersection(self)
			}
			else -> self
		}

	override fun o_TypeIntersection(
		self: AvailObject,
		another: A_Type
	): A_Type = when
	{
		self.isSubtypeOf(another) -> self
		another.isSubtypeOf(self) -> another
		else -> another.typeIntersectionOfIntegerRangeType(self)
	}

	override fun o_TypeIntersectionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type
	{
		var minObject: A_Number = self[LOWER_BOUND]
		var isMinInc = lowerInclusive
		if (anIntegerRangeType.lowerBound.equals(minObject))
		{
			isMinInc = isMinInc && anIntegerRangeType.lowerInclusive
		}
		else if (minObject.lessThan(anIntegerRangeType.lowerBound))
		{
			minObject = anIntegerRangeType.lowerBound
			isMinInc = anIntegerRangeType.lowerInclusive
		}
		var maxObject: A_Number = self[UPPER_BOUND]
		var isMaxInc = upperInclusive
		if (anIntegerRangeType.upperBound.equals(maxObject))
		{
			isMaxInc = isMaxInc && anIntegerRangeType.upperInclusive
		}
		else if (anIntegerRangeType.upperBound.lessThan(maxObject))
		{
			maxObject = anIntegerRangeType.upperBound
			isMaxInc = anIntegerRangeType.upperInclusive
		}
		// At least two references now.
		return integerRangeType(
			minObject.makeImmutable(),
			isMinInc,
			maxObject.makeImmutable(),
			isMaxInc)
	}

	override fun o_TypeIntersectionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			if (Types.NUMBER.superTests[primitiveTypeEnum.ordinal]) self
			else bottom

	override fun o_TypeUnion(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(another) -> another
			another.isSubtypeOf(self) -> self
			else -> another.typeUnionOfIntegerRangeType(self)
		}

	override fun o_TypeUnionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type
	{
		var minObject: A_Number = self[LOWER_BOUND]
		var isMinInc = lowerInclusive
		if (anIntegerRangeType.lowerBound.equals(minObject))
		{
			isMinInc = isMinInc || anIntegerRangeType.lowerInclusive
		}
		else if (anIntegerRangeType.lowerBound.lessThan(minObject))
		{
			minObject = anIntegerRangeType.lowerBound
			isMinInc = anIntegerRangeType.lowerInclusive
		}
		var maxObject: A_Number = self[UPPER_BOUND]
		var isMaxInc = upperInclusive
		if (anIntegerRangeType.upperBound.equals(maxObject))
		{
			isMaxInc = isMaxInc || anIntegerRangeType.upperInclusive
		}
		else if (maxObject.lessThan(anIntegerRangeType.upperBound))
		{
			maxObject = anIntegerRangeType.upperBound
			isMaxInc = anIntegerRangeType.upperInclusive
		}
		return integerRangeType(minObject, isMinInc, maxObject, isMaxInc)
	}

	override fun o_TypeUnionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			Types.NUMBER.unionTypes[primitiveTypeEnum.ordinal]!!

	override fun o_UpperBound(self: AvailObject): A_Number =
		self[UPPER_BOUND]

	override fun o_UpperInclusive(self: AvailObject): Boolean =
		upperInclusive

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("integer type")
		writer.write("lower bound")
		self[LOWER_BOUND].writeTo(writer)
		writer.write("upper bound")
		self[UPPER_BOUND].writeTo(writer)
		writer.endObject()
	}

	companion object
	{
		/**
		 * Compute the hash of the [IntegerRangeTypeDescriptor] that has the
		 * specified information.
		 *
		 * @param lowerBoundHash
		 *   The hash of the lower bound.
		 * @param upperBoundHash
		 *   The hash of the upper bound.
		 * @param lowerInclusive
		 *   Whether the lower bound is inclusive.
		 * @param upperInclusive
		 *   Whether the upper bound is inclusive.
		 * @return
		 *   The hash value.
		 */
		private fun computeHash(
			lowerBoundHash: Int,
			upperBoundHash: Int,
			lowerInclusive: Boolean,
			upperInclusive: Boolean): Int
		{
			val flagsHash = when
			{
				lowerInclusive && upperInclusive -> 0x1503045E
				lowerInclusive -> 0x753A6C17
				upperInclusive -> 0x1DB2D751
				else -> 0x1130427D
			}
			return combine3(lowerBoundHash, upperBoundHash, flagsHash)
		}

		/**
		 * Return a range consisting of a single [integer][IntegerDescriptor] or
		 * [infinity][InfinityDescriptor].
		 *
		 * @param integerObject
		 *   An Avail integer or infinity.
		 * @return
		 *   A [range][IntegerRangeTypeDescriptor] containing a single value.
		 */
		fun singleInteger(integerObject: A_Number): A_Type
		{
			integerObject.makeImmutable()
			return integerRangeType(integerObject, true, integerObject, true)
		}

		/**
		 * Return a range consisting of a single integer or infinity.
		 *
		 * @param anInt
		 *   A Java `int`.
		 * @return
		 *   A range containing a single value.
		 */
		fun singleInt(anInt: Int): A_Type
		{
			val integerObject: A_Number = fromInt(anInt).makeImmutable()
			return integerRangeType(integerObject, true, integerObject, true)
		}

		/**
		 * Create an integer range type.  Normalize it as necessary, converting
		 * exclusive finite bounds into equivalent inclusive bounds.  An empty
		 * range is always converted to [bottom][BottomTypeDescriptor].
		 *
		 * @param lowerBound
		 *   The lowest value inside (or just outside) the range.
		 * @param lowerInclusive
		 *   Whether to include the lowerBound.
		 * @param upperBound
		 *   The highest value inside (or just outside) the range.
		 * @param upperInclusive
		 *   Whether to include the upperBound.
		 * @return
		 *   The new normalized integer range type.
		 */
		fun integerRangeType(
			lowerBound: A_Number,
			lowerInclusive: Boolean,
			upperBound: A_Number,
			upperInclusive: Boolean): A_Type
		{
			if (lowerBound.sameAddressAs(upperBound))
			{
				if (lowerBound.descriptor().isMutable)
				{
					error("Don't plug in a mutable object as two distinct " +
						"construction parameters")
				}
			}
			var low = lowerBound
			var lowInc = lowerInclusive
			if (!lowInc)
			{
				// Try to rewrite (if possible) as inclusive boundary.
				if (low.isFinite)
				{
					low = low.noFailPlusCanDestroy(one, false)
					lowInc = true
				}
			}
			var high = upperBound
			var highInc = upperInclusive
			if (!highInc)
			{
				// Try to rewrite (if possible) as inclusive boundary.
				if (high.isFinite)
				{
					high = high.noFailMinusCanDestroy(one, false)
					highInc = true
				}
			}
			if (high.lessThan(low))
			{
				return bottom
			}
			if (high.equals(low) && (!highInc || !lowInc))
			{
				// Unusual cases such as [INF..INF) give preference to exclusion
				// over inclusion.
				return bottom
			}
			if (low.isInt && high.isInt)
			{
				assert(lowInc && highInc)
				val lowInt = low.extractInt
				val highInt = high.extractInt
				if (lowInt in 0 until smallRangeLimit
					&& highInt in 0 until smallRangeLimit)
				{
					return smallRanges[highInt][lowInt]
				}
			}
			return lookupDescriptor(MUTABLE, lowInc, highInc).create {
				setSlot(LOWER_BOUND, low)
				setSlot(UPPER_BOUND, high)
			}
		}

		/**
		 * Create an inclusive-inclusive range with the given endpoints.
		 *
		 * @param lowerBound
		 *   The low end, inclusive, of the range.
		 * @param upperBound
		 *   The high end, inclusive, of the range.
		 * @return
		 *   The integral type containing the bounds and all integers between.
		 */
		fun inclusive(lowerBound: A_Number, upperBound: A_Number): A_Type =
			integerRangeType(lowerBound, true, upperBound, true)

		/**
		 * Create an inclusive-inclusive range with the given endpoints.
		 *
		 * @param lowerBound
		 *   The low end, inclusive, of the range.
		 * @param upperBound
		 *   The high end, inclusive, of the range.
		 * @return
		 *   The integral type containing the bounds and all integers between.
		 */
		fun inclusive(lowerBound: Long, upperBound: Long): A_Type =
			integerRangeType(
				fromLong(lowerBound), true, fromLong(upperBound), true)

		/**
		 * The [EnumMap] keyed on [Mutability], where the value is an [Array] of
		 * four descriptors, where the low (1) bit of the index is whether the
		 * lower bound is included, and the second (2) bit is whether the upper
		 * bound is included.
		 */
		private val descriptors = enumMap { mutability: Mutability ->
			Array(4) {
				IntegerRangeTypeDescriptor(
					mutability, it and 1 != 0, it and 2 != 0)
			}
		}

		/**
		 * Answer the descriptor with the three specified boolean properties.
		 *
		 * @param mutability
		 *   The [Mutability] of the descriptor.
		 * @param lowerInclusive
		 *   Whether the descriptor's objects include the lower bound.
		 * @param upperInclusive
		 *   Whether the descriptor's objects include the upper bound.
		 * @return
		 *   The requested [IntegerRangeTypeDescriptor].
		 */
		private fun lookupDescriptor(
			mutability: Mutability,
			lowerInclusive: Boolean,
			upperInclusive: Boolean
		): IntegerRangeTypeDescriptor
		{
			val subscript =
				(if (lowerInclusive) 1 else 0) or
				(if (upperInclusive) 2 else 0)
			return descriptors[mutability]!![subscript]
		}

		/** One past the maximum lower or upper bound of a pre-built range. */
		private const val smallRangeLimit = 20

		/**
		 * An array of arrays of small inclusive-inclusive ranges.  The first
		 * index is the upper bound, and must be in `[0..smallRangeLimit-1]`.
		 * The second index is the lower bound, and must be in the range
		 * `[0..upper bound]`. This scheme allows both indices to start at zero
		 * and not include any degenerate elements.
		 *
		 * Use of these pre-built ranges is not mandatory, but is generally
		 * recommended for performance.  The [create] operation uses them
		 * whenever possible.
		 */
		private val smallRanges = Array(smallRangeLimit) { upper ->
			Array<A_Type>(upper + 1) { lower ->
				lookupDescriptor(MUTABLE, true, true).createShared {
					setSlot(UPPER_BOUND, fromInt(upper))
					setSlot(LOWER_BOUND, fromInt(lower))
				}
			}
		}

		/** The range [0..255]. */
		val bytes: A_Type = inclusive(0, 255).makeShared()

		/** The range of Unicode code points, [0..1114111]. */
		val characterCodePoints: A_Type =
			inclusive(0, maxCodePointInt.toLong()).makeShared()

		/** The range of integers including infinities, [-∞..∞]. */
		val extendedIntegers: A_Type =
			inclusive(negativeInfinity, positiveInfinity).makeShared()

		/** The range of integers not including infinities, (∞..∞). */
		val integers: A_Type = integerRangeType(
			negativeInfinity, false, positiveInfinity, false).makeShared()

		/** The range of natural numbers, [1..∞). */
		val naturalNumbers: A_Type =
			integerRangeType(one, true, positiveInfinity, false).makeShared()

		/** The range [0..15]. */
		val nybbles: A_Type = inclusive(0, 15).makeShared()

		/** The range [0..65535]. */
		val unsignedShorts: A_Type =
			inclusive(0, 65535).makeShared()

		/** The range of whole numbers, [0..∞). */
		val wholeNumbers: A_Type =
			integerRangeType(zero, true, positiveInfinity, false)
				.makeShared()

		/** The range of a signed 32-bit `int`, `[-2^31..2^31)`. */
		val int32: A_Type =
			inclusive(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
				.makeShared()

		/** The non-negative part of the range of int32s, `[0..2^31)`. */
		val nonnegativeInt32: A_Type =
			inclusive(0, Int.MAX_VALUE.toLong()).makeShared()

		/** The range of a signed 64-bit `long`, [-2^63..2^63). */
		val int64: A_Type =
			inclusive(Long.MIN_VALUE, Long.MAX_VALUE).makeShared()

		/**
		 * The metatype for integers. This is an
		 * [instance&#32;type][InstanceTypeDescriptor] whose base instance
		 * is [extended&#32;integer][extendedIntegers], and therefore has
		 * all integer range types as instances.
		 */
		val extendedIntegersMeta: A_Type =
			instanceMeta(extendedIntegers).makeShared()

		/** The range [0..1]. */
		val zeroOrOne = smallRanges[1][0]
	}

	override fun mutable(): AbstractDescriptor =
		lookupDescriptor(MUTABLE, lowerInclusive, upperInclusive)

	override fun immutable(): AbstractDescriptor =
		lookupDescriptor(IMMUTABLE, lowerInclusive, upperInclusive)

	override fun shared(): AbstractDescriptor =
		lookupDescriptor(SHARED, lowerInclusive, upperInclusive)
}
