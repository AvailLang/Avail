/**
 * AbstractNumberDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import static com.avail.descriptor.AbstractNumberDescriptor.Order.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.avail.annotations.AvailMethod;
import com.avail.utility.MutableOrNull;
import org.jetbrains.annotations.Nullable;

/**
 * The abstract class {@code AbstractNumberDescriptor} serves as an abstraction
 * for numeric objects.  It currently includes the subclasses {@link
 * ExtendedIntegerDescriptor}, {@link FloatDescriptor}, and {@link
 * DoubleDescriptor}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class AbstractNumberDescriptor
extends Descriptor
{
	/**
	 * An enumeration used to describe the sign of a quantity.
	 */
	public enum Sign
	implements IntegerEnumSlotDescriptionEnum
	{
		/**
		 * The value is positive.
		 */
		POSITIVE (Double.POSITIVE_INFINITY),

		/**
		 * The value is negative.
		 */
		NEGATIVE (Double.NEGATIVE_INFINITY),

		/**
		 * The value is zero.
		 */
		ZERO (0.0d),

		/**
		 * The value is an indeterminate value (not-a-number).
		 */
		INDETERMINATE (Double.NaN);

		/**
		 * A value that represents the most extreme {@code double} with this
		 * sign.
		 */
		private final double limitDouble;

		/**
		 * A value that represents the most extreme {@code float} with this
		 * sign.
		 */
		private final float limitFloat;

		/**
		 * The {@link #limitDouble} as an Avail object.
		 */
		protected final A_Number limitDoubleObject;

		/**
		 *  Answer the Avail {@link DoubleDescriptor double-precision} value
		 *  that typifies this value.
		 *
		 * @return An Avail double.
		 */
		protected A_Number limitDoubleObject ()
		{
			return limitDoubleObject;
		}

		/**
		 * The {@link #limitFloat} as an Avail object.
		 */
		protected final A_Number limitFloatObject;

		/**
		 *  Answer the Avail {@link FloatDescriptor single-precision} value
		 *  that typifies this value.
		 *
		 * @return An Avail float.
		 */
		protected A_Number limitFloatObject ()
		{
			return limitFloatObject;
		}

		/**
		 * Construct a new {@link Sign}.
		 *
		 * @param limitDouble The most extreme {@code double} with this sign.
		 */
		private Sign (final double limitDouble)
		{
			this.limitDouble = limitDouble;
			this.limitDoubleObject =
				DoubleDescriptor.fromDouble(limitDouble).makeShared();
			this.limitFloat = (float)limitDouble;
			this.limitFloatObject =
				FloatDescriptor.fromFloat(limitFloat).makeShared();
		}

		/**
		 * Answer the most extreme {@code double} with this sign.  In
		 * particular, answer ±infinity, NaN, or zero.
		 *
		 * @return The extreme {@code double}.
		 */
		public double limitDouble ()
		{
			return limitDouble;
		}

		/**
		 * Answer the most extreme {@code float} with this sign.  In
		 * particular, answer ±infinity, NaN, or zero.
		 *
		 * @return The extreme {@code float}.
		 */
		public float limitFloat ()
		{
			return limitFloat;
		}
	}

	/**
	 * An {@code Order} is an indication of how two numbers are related
	 * numerically.
	 */
	public enum Order
	{
		/**
		 * The first number is less than the second number.
		 */
		LESS,

		/**
		 * The first number is greater than the second number.
		 */
		MORE,

		/**
		 * The first number is numerically equivalent to the second number.
		 */
		EQUAL,

		/**
		 * The first number is not comparable to the second number.  This is the
		 * case precisely when one of the values is a {@link Double#NaN
		 * not-a-number}.
		 */
		INCOMPARABLE;

		/**
		 * This {@code Order}'s inverse, which is the comparison between the
		 * same values that yielded the receiver, but with the arguments
		 * reversed.
		 */
		private @Nullable Order reverse;

		static
		{
			LESS.reverse = MORE;
			MORE.reverse = LESS;
			EQUAL.reverse = EQUAL;
			INCOMPARABLE.reverse = INCOMPARABLE;
		}

		/**
		 * Answer the {@code Order} which is to (y,x) as the receiver is to
		 * (x,y).
		 *
		 * @return The positional inverse of the receiver.
		 */
		public final Order reverse ()
		{
			final Order rev = reverse;
			assert rev != null;
			return rev;
		}

		/**
		 * Answer whether the first value is less than the second value.
		 *
		 * @return Whether the relation between the two values is {@link #LESS}.
		 */
		public boolean isLess ()
		{
			return this == LESS;
		}

		/**
		 * Answer whether the first value is less than or equivalent to the
		 * second value.
		 *
		 * @return Whether the relation between the two values is {@link #LESS}
		 *         or {@link #EQUAL}.
		 */
		public boolean isLessOrEqual ()
		{
			return this == LESS || this == EQUAL;
		}

		/**
		 * Answer whether the first value is more than the second value.
		 *
		 * @return Whether the relation between the two values is {@link #MORE}.
		 */
		public boolean isMore ()
		{
			return this == MORE;
		}

		/**
		 * Answer whether the first value is more than or equivalent to the
		 * second value.
		 *
		 * @return Whether the relation between the two values is {@link #MORE}
		 *         or {@link #EQUAL}.
		 */
		public boolean isMoreOrEqual ()
		{
			return this == MORE || this == EQUAL;
		}

		/**
		 * Answer whether the first value is numerically equivalent to the
		 * second value.
		 *
		 * @return Whether the relation between the two values is {@link #EQUAL}.
		 */
		public boolean isEqual ()
		{
			return this == EQUAL;
		}

		/**
		 * Answer whether the first value is numerically incomparable to the
		 * second value.  This is the case precisely when one of the values is a
		 * {@link Double#NaN} not-a-number
		 *
		 * @return Whether the relation between the two values is {@link #EQUAL}.
		 */
		public boolean isIncomparable ()
		{
			return this == INCOMPARABLE;
		}
	}

	/**
	 * Analyze a numeric type, updating the {@link MutableOrNull mutable}
	 * arguments and populating the {@code comparablesList}.  Answer whether to
	 * give up attempting to determine the possible results of a comparison
	 * against this type.
	 *
	 * @param type
	 *        The numeric type to analyze.
	 * @param possibleResults
	 *        The {@link Set} of possible {@link Order}s when comparing against
	 *        values of this type.  Only adds {@link Order#LESS} or {@link
	 *        Order#MORE} if this method returns true.
	 * @param min
	 *        The smallest comparable value encountered.
	 * @param max
	 *        The largest comparable value encountered.
	 * @param minInclusive
	 *        Whether the smallest value is inclusive for this type.
	 * @param maxInclusive
	 *        Whether the largest value is inclusive for this type.
	 * @param comparablesList
	 *        A list of all comparable values that were encountered, in
	 *        arbitrary order.
	 * @return Whether we should give up attempting to narrow the result of the
	 *         comparison and concede that any comparison result is possible.
	 */
	private static boolean analyzeType (
		final A_Type type,
		final Set<Order> possibleResults,
		final MutableOrNull<A_Number> min,
		final MutableOrNull<A_Number> max,
		final MutableOrNull<Boolean> minInclusive,
		final MutableOrNull<Boolean> maxInclusive,
		final List<A_Number> comparablesList)
	{
		if (type.isEnumeration())
		{
			// Note that this should work even if an enumeration contains
			// non-integers, or even NaNs.
			minInclusive.value = true;
			maxInclusive.value = true;
			final Iterator<AvailObject> firstInstances =
				type.instances().iterator();
			min.value = null;
			max.value = null;
			while (firstInstances.hasNext())
			{
				final A_Number value = firstInstances.next();
				if (value.numericCompare(value) == INCOMPARABLE)
				{
					possibleResults.add(INCOMPARABLE);
				}
				else
				{
					comparablesList.add(value);
					if (max.value == null || value.greaterThan(max.value()))
					{
						max.value = value;
					}
					if (min.value == null || value.lessThan(min.value()))
					{
						min.value = value;
					}
				}
			}
		}
		else if (type.isIntegerRangeType())
		{
			min.value = type.lowerBound();
			max.value = type.upperBound();
			minInclusive.value = type.lowerInclusive();
			maxInclusive.value = type.upperInclusive();
		}
		else
		{
			possibleResults.add(LESS);
			possibleResults.add(MORE);
			possibleResults.add(EQUAL);
			possibleResults.add(INCOMPARABLE);
			return true;
		}
		return false;
	}

	/**
	 * Answer a {@link Comparable} capable of ordering {@link A_Number} values,
	 * at least those which are comparable.
	 */
	final static Comparator<A_Number> numericComparator =
		new Comparator<A_Number>()
		{
			@Override
			public int compare (
				final @Nullable A_Number n1,
				final @Nullable A_Number n2)
			{
				assert n1 != null;
				assert n2 != null;
				switch (n1.numericCompare(n2))
				{
					case LESS: return -1;
					case MORE: return 1;
					case EQUAL: return 0;
					default:
					{
						assert false;
						return 0;
					}
				}
			}
		};

	/**
	 * Return the set of possible {@link Order}s that could be returned when
	 * comparing instances of {@code firstType} with instances of {@code
	 * secondType}.  The types are both subtypes of {@code number}.
	 *
	 * @param firstType The first numeric type.
	 * @param secondType The second numeric type.
	 * @return The set of possible {@code Order}s resulting from their
	 *         comparison.
	 */
	public static Set<Order> possibleOrdersWhenComparingInstancesOf (
		final A_Type firstType,
		final A_Type secondType)
	{
		assert !firstType.equals(BottomTypeDescriptor.bottom());
		assert !secondType.equals(BottomTypeDescriptor.bottom());
		final Set<Order> possibleResults = EnumSet.noneOf(Order.class);
		// Note that we can't intersect the two types to determine, in either
		// conservative sense, whether numeric equality is possible.  It fails
		// to detect some possible equalities because 0 is *numerically* equal
		// to 0.0f, 0.0d, -0.0f, and -0.0d.  Likewise, it includes the potential
		// for equality when no such possibility exists, in the case that the
		// overlapping values are all incomparables (things that always produce
		// INCOMPARABLE when numerically compared with anything).  So there's no
		// value in computing the type intersection.
		final MutableOrNull<A_Number> firstMin = new MutableOrNull<>();
		final MutableOrNull<A_Number> firstMax = new MutableOrNull<>();
		final MutableOrNull<Boolean> firstMinInclusive = new MutableOrNull<>();
		final MutableOrNull<Boolean> firstMaxInclusive = new MutableOrNull<>();
		final List<A_Number> firstComparablesList = new ArrayList<>();
		if (analyzeType(
			firstType,
			possibleResults,
			firstMin,
			firstMax,
			firstMinInclusive,
			firstMaxInclusive,
			firstComparablesList))
		{
			return possibleResults;
		}
		final MutableOrNull<A_Number> secondMin = new MutableOrNull<>();
		final MutableOrNull<A_Number> secondMax = new MutableOrNull<>();
		final MutableOrNull<Boolean> secondMinInclusive = new MutableOrNull<>();
		final MutableOrNull<Boolean> secondMaxInclusive = new MutableOrNull<>();
		final List<A_Number> secondComparablesList = new ArrayList<>();
		if (analyzeType(
			secondType,
			possibleResults,
			secondMin,
			secondMax,
			secondMinInclusive,
			secondMaxInclusive,
			secondComparablesList))
		{
			return possibleResults;
		}
		// Each is (independently) an enumeration or an integer range type.
		if (firstMin.value().numericCompare(secondMax.value()) == LESS)
		{
			possibleResults.add(LESS);
		}
		if (firstMax.value().numericCompare(secondMin.value()) == MORE)
		{
			possibleResults.add(MORE);
		}
		// From here down we only need to determine if EQUAL is possible.
		final boolean firstIsEnumeration = firstType.isEnumeration();
		final boolean secondIsEnumeration = secondType.isEnumeration();
		if (firstIsEnumeration && secondIsEnumeration)
		{
			// They're both actual enumerations.  Determine whether they can
			// ever be equal by iterating through both comparables lists in
			// numeric order, looking for corresponding equal values.
			Collections.sort(firstComparablesList, numericComparator);
			Collections.sort(secondComparablesList, numericComparator);
			int firstIndex = 0;
			int secondIndex = 0;
			while (true)
			{
				if (firstIndex >= firstComparablesList.size()
					|| secondIndex >= secondComparablesList.size())
				{
					// At least one list of values was exhausted.
					break;
				}
				final Order comparison =
					firstComparablesList.get(firstIndex).numericCompare(
						secondComparablesList.get(secondIndex));
				if (comparison == EQUAL)
				{
					possibleResults.add(EQUAL);
					// No point continuing the loop.
					break;
				}
				else if (comparison == LESS)
				{
					firstIndex++;
				}
				else
				{
					secondIndex++;
				}
			}
			return possibleResults;
		}
		if (firstIsEnumeration)
		{
			// The first is an enumeration and the second is an integer range.
			for (final A_Number firstValue : firstComparablesList)
			{
				if (firstValue.isFinite())
				{
					final Order compareMin =
						firstValue.numericCompare(secondMin.value());
					final Order compareMax =
						firstValue.numericCompare(secondMax.value());
					if (compareMin.isMoreOrEqual()
						&& compareMax.isLessOrEqual())
					{
						if (firstValue.isNumericallyIntegral())
						{
							// It's in range and equals an integer, so numeric
							// equality with a value from the integer range is
							// possible.
							possibleResults.add(EQUAL);
							break;
						}
					}
				}
				else
				{
					// The value is infinite.
					final A_Number integerInfinity =
						firstValue.isPositive()
							? InfinityDescriptor.positiveInfinity()
							: InfinityDescriptor.negativeInfinity();
					{
						if (integerInfinity.isInstanceOf(secondType))
						{
							possibleResults.add(EQUAL);
							break;
						}
					}
				}
			}
			return possibleResults;
		}
		if (secondIsEnumeration)
		{
			// The first is an integer range and the second is an enumeration.
			for (final A_Number secondValue : secondComparablesList)
			{
				if (secondValue.isFinite())
				{
					final Order compareMin =
						secondValue.numericCompare(firstMin.value());
					final Order compareMax =
						secondValue.numericCompare(firstMax.value());
					if (compareMin.isMoreOrEqual()
						&& compareMax.isLessOrEqual())
					{
						if (secondValue.isNumericallyIntegral())
						{
							// It's in range and equals an integer, so numeric
							// equality with a value from the integer range is
							// possible.
							possibleResults.add(EQUAL);
							break;
						}
					}
				}
				else
				{
					// The value is infinite.
					final A_Number integerInfinity =
						secondValue.isPositive()
							? InfinityDescriptor.positiveInfinity()
							: InfinityDescriptor.negativeInfinity();
					{
						if (integerInfinity.isInstanceOf(firstType))
						{
							possibleResults.add(EQUAL);
							break;
						}
					}
				}
			}
			return possibleResults;
		}
		// They're both integer ranges.  Just check for non-empty intersection.
		if (!firstType.typeIntersection(secondType).equals(
			BottomTypeDescriptor.bottom()))
		{
			possibleResults.add(EQUAL);
		}
		return possibleResults;
	}

	/**
	 * Apply the usual rules of type promotion for some unspecified binary
	 * numeric operation (like +, -, ×, ÷).
	 *
	 * @param aType One argument's numeric type.
	 * @param bType The other argument's numeric type.
	 * @return The strongest type we can determine for these input types without
	 *         analyzing actual integer ranges and instance types.
	 */
	public static A_Type binaryNumericOperationTypeBound (
		final A_Type aType,
		final A_Type bType)
	{
		A_Type union = BottomTypeDescriptor.bottom();
		if (!aType.typeIntersection(DOUBLE.o()).isBottom()
			|| !bType.typeIntersection(DOUBLE.o()).isBottom())
		{
			// One of the values might be a double.
			if (aType.isSubtypeOf(DOUBLE.o())
				|| bType.isSubtypeOf(DOUBLE.o()))
			{
				// One of the types is definitely a double, so the result *must*
				// be a double.
				return DOUBLE.o();
			}
			union = union.typeUnion(DOUBLE.o());
		}
		if (!aType.typeIntersection(FLOAT.o()).isBottom()
			|| !bType.typeIntersection(FLOAT.o()).isBottom())
		{
			// One of the values might be a float.
			if (aType.isSubtypeOf(FLOAT.o())
				|| bType.isSubtypeOf(FLOAT.o()))
			{
				// One is definitely a float.
				if (union.isBottom())
				{
					// Neither could be a double, but one is definitely a float.
					// Therefore the result must be a float.
					return FLOAT.o();
				}
			}
			// Add float as a possibility.
			union = union.typeUnion(FLOAT.o());
		}
		final A_Type extendedIntegers =
			IntegerRangeTypeDescriptor.extendedIntegers();
		if (!aType.typeIntersection(extendedIntegers).isBottom()
			&& !bType.typeIntersection(extendedIntegers).isBottom())
		{
			// *Both* could be extended integers, so the result could be also.
			if (aType.isSubtypeOf(extendedIntegers)
				&& bType.isSubtypeOf(extendedIntegers))
			{
				// Both are definitely extended integers, so the result must
				// also be an extended integer.
				return extendedIntegers;
			}
			union = union.typeUnion(extendedIntegers);
		}
		assert !union.isBottom();
		return union;
	}

	@Override @AvailMethod
	abstract boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another);

	@Override @AvailMethod
	abstract Order o_NumericCompare (
		final AvailObject object,
		final A_Number another);

	@Override @AvailMethod
	abstract boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType);

	@Override @AvailMethod
	abstract int o_Hash (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Number o_DivideCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_MinusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_PlusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_TimesCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy);


	// Double-dispatched operations.

	@Override @AvailMethod
	abstract Order o_NumericCompareToInteger (
		final AvailObject object,
		final A_Number anInteger);

	@Override @AvailMethod
	abstract Order o_NumericCompareToInfinity (
		final AvailObject object,
		final InfinityDescriptor.Sign sign);

	@Override @AvailMethod
	abstract Order o_NumericCompareToDouble (
		final AvailObject object,
		final double double1);

	@Override @AvailMethod
	abstract A_Number o_AddToInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_AddToIntegerCanDestroy (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_AddToDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_AddToFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_DivideIntoIntegerCanDestroy (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract A_Number o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract float o_ExtractFloat (AvailObject object);

	@Override @AvailMethod
	abstract double o_ExtractDouble (AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsNumericallyIntegral (AvailObject object);

	/**
	 * Construct a new {@link AbstractNumberDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param typeTag
	 *            The {@link TypeTag} to embed in the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	protected AbstractNumberDescriptor (
		final Mutability mutability,
		final TypeTag typeTag,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass);
	}
}
