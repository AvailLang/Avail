/*
 * AbstractNumberDescriptor.kt
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
package avail.descriptor.numbers

import avail.descriptor.numbers.A_Number.Companion.greaterThan
import avail.descriptor.numbers.A_Number.Companion.isNumericallyIntegral
import avail.descriptor.numbers.A_Number.Companion.isPositive
import avail.descriptor.numbers.A_Number.Companion.lessThan
import avail.descriptor.numbers.A_Number.Companion.numericCompare
import avail.descriptor.numbers.AbstractNumberDescriptor.Order.EQUAL
import avail.descriptor.numbers.AbstractNumberDescriptor.Order.INCOMPARABLE
import avail.descriptor.numbers.AbstractNumberDescriptor.Order.LESS
import avail.descriptor.numbers.AbstractNumberDescriptor.Order.MORE
import avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.lowerInclusive
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.A_Type.Companion.upperInclusive
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.DOUBLE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.FLOAT
import avail.descriptor.types.TypeTag
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.utility.Mutable
import java.util.Comparator
import java.util.EnumSet.noneOf

/**
 * The abstract class `AbstractNumberDescriptor` serves as an abstraction for
 * numeric objects.  It currently includes the subclasses
 * [ExtendedIntegerDescriptor], [FloatDescriptor], and [DoubleDescriptor].
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] to embed in the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no integer slots.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class AbstractNumberDescriptor protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?
) : Descriptor(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass)
{
	/**
	 * An enumeration used to describe the sign of a quantity.
	 *
	 * @property limitDouble
	 *   A value that represents the most extreme `double` with this sign.
	 */
	enum class Sign constructor(
		private val limitDouble: Double)
	{
		/** The value is positive. */
		POSITIVE(Double.POSITIVE_INFINITY),

		/** The value is negative. */
		NEGATIVE(Double.NEGATIVE_INFINITY),

		/** The value is zero. */
		ZERO(0.0),

		/** The value is an indeterminate value (not-a-number). */
		INDETERMINATE(Double.NaN);

		/** A value that represents the most extreme `float` with this sign. */
		private val limitFloat: Float = limitDouble.toFloat()

		/** The [limitDouble] as an Avail object. */
		private val limitDoubleObject: A_Number =
			fromDouble(limitDouble).makeShared()

		/** The [limitFloat] as an Avail object. */
		private val limitFloatObject: A_Number =
			FloatDescriptor.fromFloat(limitFloat).makeShared()

		/**
		 * Answer the Avail [double-precision][DoubleDescriptor] value that
		 * typifies this value.
		 *
		 * @return
		 *   An Avail `double`.
		 */
		fun limitDoubleObject(): A_Number = limitDoubleObject

		/**
		 * Answer the Avail [single-precision][FloatDescriptor] value that
		 * typifies this value.
		 *
		 * @return
		 *   An Avail `float`.
		 */
		fun limitFloatObject(): A_Number = limitFloatObject

		/**
		 * Answer the most extreme `double` with this sign.  In particular,
		 * answer ±infinity, NaN, or zero.
		 *
		 * @return
		 *   The extreme `double`.
		 */
		fun limitDouble(): Double = limitDouble

		/**
		 * Answer the most extreme `float` with this sign.  In particular,
		 * answer ±infinity, NaN, or zero.
		 *
		 * @return
		 *   The extreme `float`.
		 */
		fun limitFloat(): Float = limitFloat
	}

	/**
	 * An `Order` is an indication of how two numbers are related numerically.
	 */
	enum class Order {
		/** The first number is less than the second number. */
		LESS,

		/** The first number is greater than the second number. */
		MORE,

		/** The first number is numerically equivalent to the second number. */
		EQUAL,

		/**
		 * The first number is not comparable to the second number.  This is the
		 * case precisely when one of the values is a not-a-number.
		 */
		INCOMPARABLE;

		/**
		 * This `Order`'s inverse, which is the comparison between the same
		 * values that yielded the receiver, but with the arguments reversed.
		 */
		private lateinit var reverse: Order

		companion object {
			/** The [CheckedMethod] for [isLess]. */
			val isLessMethod = instanceMethod(
				Order::class.java,
				Order::isLess.name,
				Boolean::class.javaPrimitiveType!!)

			/** The [CheckedMethod] for [isLessOrEqual]. */
			val isLessOrEqualMethod = instanceMethod(
				Order::class.java,
				Order::isLessOrEqual.name,
				Boolean::class.javaPrimitiveType!!)

			/** The [CheckedMethod] for [isMore]. */
			val isMoreMethod = instanceMethod(
				Order::class.java,
				Order::isMore.name,
				Boolean::class.javaPrimitiveType!!)

			/** The [CheckedMethod] for [isMoreOrEqual]. */
			val isMoreOrEqualMethod = instanceMethod(
				Order::class.java,
				Order::isMoreOrEqual.name,
				Boolean::class.javaPrimitiveType!!)

			init {
				LESS.reverse = MORE
				MORE.reverse = LESS
				EQUAL.reverse = EQUAL
				INCOMPARABLE.reverse = INCOMPARABLE
			}
		}

		/**
		 * Answer the `Order` which is to (y,x) as the receiver is to (x,y).
		 *
		 * @return
		 *   The positional inverse of the receiver.
		 */
		fun reverse(): Order = reverse

		/**
		 * Answer whether the first value is less than the second value.
		 *
		 * @return
		 *   Whether the relation between the two values is [LESS].
		 */
		@ReferencedInGeneratedCode
		fun isLess(): Boolean = this == LESS

		/**
		 * Answer whether the first value is less than or equivalent to the
		 * second value.
		 *
		 * @return
		 *   Whether the relation between the two values is [LESS] or [EQUAL].
		 */
		@ReferencedInGeneratedCode
		fun isLessOrEqual(): Boolean = this == LESS || this == EQUAL

		/**
		 * Answer whether the first value is more than the second value.
		 *
		 * @return
		 *   Whether the relation between the two values is [MORE].
		 */
		@ReferencedInGeneratedCode
		fun isMore(): Boolean = this == MORE

		/**
		 * Answer whether the first value is more than or equivalent to the
		 * second value.
		 *
		 * @return
		 *   Whether the relation between the two values is [MORE] or [EQUAL].
		 */
		@ReferencedInGeneratedCode
		fun isMoreOrEqual(): Boolean = this == MORE || this == EQUAL

		/**
		 * Answer whether the first value is numerically equivalent to the
		 * second value.
		 *
		 * @return
		 *   Whether the relation between the two values is [EQUAL].
		 */
		fun isEqual(): Boolean = this == EQUAL

		/**
		 * Answer whether the first value is numerically incomparable to the
		 * second value.  This is the case precisely when one of the values is a
		 * not-a-number.
		 *
		 * @return
		 *   Whether the relation between the two values is [INCOMPARABLE].
		 */
		fun isIncomparable(): Boolean = this == INCOMPARABLE
	}

	abstract override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject
	): Boolean

	abstract override fun o_NumericCompare(
		self: AvailObject,
		another: A_Number
	): Order

	abstract override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type
	): Boolean

	abstract override fun o_Hash(self: AvailObject): Int

	abstract override fun o_DivideCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_MinusCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_PlusCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_TimesCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number

	// Double-dispatched operations.
	abstract override fun o_NumericCompareToInteger(
		self: AvailObject,
		anInteger: AvailObject
	): Order

	abstract override fun o_NumericCompareToInfinity(
		self: AvailObject,
		sign: Sign
	): Order

	abstract override fun o_NumericCompareToDouble(
		self: AvailObject,
		aDouble: Double
	): Order

	abstract override fun o_AddToInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_AddToIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_AddToDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_AddToFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_DivideIntoInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_DivideIntoIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_MultiplyByInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_MultiplyByIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_SubtractFromInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_SubtractFromIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_SubtractFromDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_SubtractFromFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_MultiplyByDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_MultiplyByFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_DivideIntoDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_DivideIntoFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number

	abstract override fun o_ExtractFloat(self: AvailObject): Float

	abstract override fun o_ExtractDouble(self: AvailObject): Double

	abstract override fun o_IsNumericallyIntegral(self: AvailObject): Boolean

	companion object {
		/**
		 * Analyze a numeric type, updating the [mutable][Mutable] arguments and
		 * populating the [comparablesList].  Answer whether to give up
		 * attempting to determine the possible results of a comparison against
		 * this type.
		 *
		 * @param type
		 *   The numeric type to analyze.
		 * @param possibleResults
		 *   The [Set] of possible [Order]s when comparing against values of
		 *   this type.  Only adds [Order.LESS] or [Order.MORE] if this method
		 *   returns true.
		 * @param min
		 *   The smallest comparable value encountered.
		 * @param max
		 *   The largest comparable value encountered.
		 * @param minInclusive
		 *   Whether the smallest value is inclusive for this type.
		 * @param maxInclusive
		 *   Whether the largest value is inclusive for this type.
		 * @param comparablesList
		 *   A list of all comparable values that were encountered, in arbitrary
		 *   order.
		 * @return
		 *   Whether we should give up attempting to narrow the result of the
		 *   comparison and concede that any comparison result is possible.
		 */
		private fun analyzeType(
			type: A_Type,
			possibleResults: MutableSet<Order>,
			min: Mutable<A_Number?>,
			max: Mutable<A_Number?>,
			minInclusive: Mutable<Boolean?>,
			maxInclusive: Mutable<Boolean?>,
			comparablesList: MutableList<A_Number>
		) = when {
			type.isEnumeration -> {
				// Note that this should work even if an enumeration contains
				// non-integers, or even NaNs.
				minInclusive.value = true
				maxInclusive.value = true
				min.value = null
				max.value = null
				type.instances.forEach { value: A_Number ->
					if (value.numericCompare(value).isIncomparable()) {
						possibleResults.add(INCOMPARABLE)
					}
					else
					{
						comparablesList.add(value)
						if (max.value === null
							|| value.greaterThan(max.value!!))
						{
							max.value = value
						}
						if (min.value === null || value.lessThan(min.value!!))
						{
							min.value = value
						}
					}
				}
				false
			}
			type.isIntegerRangeType ->
			{
				min.value = type.lowerBound
				max.value = type.upperBound
				minInclusive.value = type.lowerInclusive
				maxInclusive.value = type.upperInclusive
				false
			}
			else ->
			{
				possibleResults.add(LESS)
				possibleResults.add(MORE)
				possibleResults.add(EQUAL)
				possibleResults.add(INCOMPARABLE)
				true
			}
		}

		/**
		 * Answer a [Comparable] capable of ordering [A_Number] values, at least
		 * those which are comparable.
		 */
		private val numericComparator: Comparator<A_Number> = Comparator {
				n1, n2 ->
			when (n1.numericCompare(n2))
			{
				LESS -> -1
				MORE -> 1
				EQUAL -> 0
				else -> throw RuntimeException("Attempting to sort NaNs")
			}
		}

		/**
		 * Return the set of possible [Order]s that could be returned when
		 * comparing instances of `firstType` with instances of `secondType`.
		 * The types are both subtypes of `number`.
		 *
		 * @param firstType
		 *   The first numeric type.
		 * @param secondType
		 *   The second numeric type.
		 * @return
		 *   The set of possible `Order`s resulting from their comparison.
		 */
		fun possibleOrdersWhenComparingInstancesOf(
			firstType: A_Type,
			secondType: A_Type
		): Set<Order> {
			assert(!firstType.isBottom)
			assert(!secondType.isBottom)
			val possibleResults: MutableSet<Order> = noneOf(Order::class.java)
			// Note that we can't intersect the two types to determine, in
			// either conservative sense, whether numeric equality is possible.
			// It fails to detect some possible equalities because 0 is
			// *numerically* equal to 0.0f, 0.0d, -0.0f, and -0.0d.  Likewise,
			// it includes the potential for equality when no such possibility
			// exists, in the case that the overlapping values are all
			// incomparables (things that always produce INCOMPARABLE when
			// numerically compared with anything).  So there's no value in
			// computing the type intersection.
			val firstMin = Mutable<A_Number?>(null)
			val firstMax = Mutable<A_Number?>(null)
			val firstMinInclusive = Mutable<Boolean?>(null)
			val firstMaxInclusive = Mutable<Boolean?>(null)
			val firstComparablesList = mutableListOf<A_Number>()
			if (analyzeType(
					firstType,
					possibleResults,
					firstMin,
					firstMax,
					firstMinInclusive,
					firstMaxInclusive,
					firstComparablesList)) {
				return possibleResults
			}
			val secondMin = Mutable<A_Number?>(null)
			val secondMax = Mutable<A_Number?>(null)
			val secondMinInclusive = Mutable<Boolean?>(null)
			val secondMaxInclusive = Mutable<Boolean?>(null)
			val secondComparablesList = mutableListOf<A_Number>()
			if (analyzeType(
					secondType,
					possibleResults,
					secondMin,
					secondMax,
					secondMinInclusive,
					secondMaxInclusive,
					secondComparablesList)) {
				return possibleResults
			}
			// Each is (independently) an enumeration or an integer range type.
			if (firstMin.value!!.numericCompare(secondMax.value!!) == LESS) {
				possibleResults.add(LESS)
			}
			if (firstMax.value!!.numericCompare(secondMin.value!!) == MORE) {
				possibleResults.add(MORE)
			}
			// From here down we only need to determine if EQUAL is possible.
			val firstIsEnumeration = firstType.isEnumeration
			val secondIsEnumeration = secondType.isEnumeration
			if (firstIsEnumeration && secondIsEnumeration) {
				// They're both actual enumerations.  Determine whether they can
				// ever be equal by iterating through both comparables lists in
				// numeric order, looking for corresponding equal values.
				firstComparablesList.sortWith(numericComparator)
				secondComparablesList.sortWith(numericComparator)
				var firstIndex = 0
				var secondIndex = 0
				while (firstIndex < firstComparablesList.size
					&& secondIndex < secondComparablesList.size)
				{
					val comparison = firstComparablesList[firstIndex]
						.numericCompare(secondComparablesList[secondIndex])
					when (comparison) {
						EQUAL -> {
							possibleResults.add(EQUAL)
							// No point continuing the loop.
							return possibleResults
						}
						LESS -> firstIndex++
						else -> secondIndex++
					}
				}
			}
			if (firstIsEnumeration) {
				// The first is an enumeration and the second is an integer
				// range.
				for (firstValue in firstComparablesList) {
					if (firstValue.isFinite) {
						val compareMin =
							firstValue.numericCompare(secondMin.value!!)
						val compareMax =
							firstValue.numericCompare(secondMax.value!!)
						if (compareMin.isMoreOrEqual()
							&& compareMax.isLessOrEqual()
							&& firstValue.isNumericallyIntegral)
						{
							// It's in range and equals an integer, so
							// numeric equality with a value from the
							// integer range is possible.
							possibleResults.add(EQUAL)
							return possibleResults
						}
					}
					else
					{
						// The value is infinite.
						val integerInfinity: A_Number =
							if (firstValue.isPositive) positiveInfinity
							else negativeInfinity
						if (integerInfinity.isInstanceOf(secondType)) {
							possibleResults.add(EQUAL)
							return possibleResults
						}
					}
				}
				return possibleResults
			}
			if (secondIsEnumeration) {
				// The first is an integer range and the second is an
				// enumeration.
				for (secondValue in secondComparablesList) {
					if (secondValue.isFinite) {
						val compareMin =
							secondValue.numericCompare(firstMin.value!!)
						val compareMax =
							secondValue.numericCompare(firstMax.value!!)
						if (compareMin.isMoreOrEqual()
							&& compareMax.isLessOrEqual()
							&& secondValue.isNumericallyIntegral)
						{
							// It's in range and equals an integer, so numeric
							// equality with a value from the integer range is
							// possible.
							possibleResults.add(EQUAL)
							return possibleResults
						}
					}
					else
					{
						// The value is infinite.
						val integerInfinity: A_Number =
							if (secondValue.isPositive) positiveInfinity
							else negativeInfinity
						if (integerInfinity.isInstanceOf(firstType)) {
							possibleResults.add(EQUAL)
							return possibleResults
						}
					}
				}
				return possibleResults
			}
			// They're both integer ranges.  Just check for non-empty
			// intersection.
			if (!firstType.typeIntersection(secondType).isBottom) {
				possibleResults.add(EQUAL)
			}
			return possibleResults
		}

		/**
		 * Apply the usual rules of type promotion for some unspecified binary
		 * numeric operation (like +, -, &times;, &divide;).
		 *
		 * @param aType
		 *   One argument's numeric type.
		 * @param bType
		 *   The other argument's numeric type.
		 * @return
		 *   The strongest type we can determine for these input types without
		 *   analyzing actual integer ranges and instance types.
		 */
		fun binaryNumericOperationTypeBound(
			aType: A_Type,
			bType: A_Type
		): A_Type {
			var union = bottom
			if (!aType.typeIntersection(DOUBLE.o).isBottom
				|| !bType.typeIntersection(DOUBLE.o).isBottom)
			{
				// One of the values might be a double.
				if (aType.isSubtypeOf(DOUBLE.o)
					|| bType.isSubtypeOf(DOUBLE.o)) {
					// One of the types is definitely a double, so the result
					// *must* be a double.
					return DOUBLE.o
				}
				union = union.typeUnion(DOUBLE.o)
			}
			if (!aType.typeIntersection(FLOAT.o).isBottom
				|| !bType.typeIntersection(FLOAT.o).isBottom)
			{
				// One of the values might be a float.
				if (aType.isSubtypeOf(FLOAT.o)
					|| bType.isSubtypeOf(FLOAT.o))
				{
					// One is definitely a float.
					if (union.isBottom) {
						// Neither could be a double, but one is definitely a
						// float. Therefore the result must be a float.
						return FLOAT.o
					}
				}
				// Add float as a possibility.
				union = union.typeUnion(FLOAT.o)
			}
			if (!aType.typeIntersection(extendedIntegers).isBottom
				&& !bType.typeIntersection(extendedIntegers).isBottom) {
				// *Both* could be extended integers, so the result could be
				// also.
				if (aType.isSubtypeOf(extendedIntegers)
					&& bType.isSubtypeOf(extendedIntegers)) {
					// Both are definitely extended integers, so the result must
					// also be an extended integer.
					return extendedIntegers
				}
				union = union.typeUnion(extendedIntegers)
			}
			assert(!union.isBottom)
			return union
		}
	}
}
