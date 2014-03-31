/**
 * AbstractNumberDescriptor.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import com.avail.annotations.*;
import com.avail.optimizer.RegisterSet;

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
		protected @Nullable A_Number limitDoubleObject;

		/**
		 *  Answer the Avail {@link DoubleDescriptor double-precision} value
		 *  that typifies this value.
		 *
		 * @return An Avail double.
		 */
		protected A_Number limitDoubleObject ()
		{
			final A_Number limit = limitDoubleObject;
			assert limit != null;
			return limit;
		}

		/**
		 * The {@link #limitFloat} as an Avail object.
		 */
		protected @Nullable AvailObject limitFloatObject;

		/**
		 *  Answer the Avail {@link FloatDescriptor single-precision} value
		 *  that typifies this value.
		 *
		 * @return An Avail float.
		 */
		protected A_Number limitFloatObject ()
		{
			final A_Number limit = limitFloatObject;
			assert limit != null;
			return limit;
		}

		/**
		 * Construct a new {@link Sign}.
		 *
		 * @param limitDouble The most extreme {@code double} with this sign.
		 */
		private Sign (final double limitDouble)
		{
			this.limitDouble = limitDouble;
			this.limitFloat = (float)limitDouble;
		}

		/**
		 * Answer the most extreme {@code double} with this sign.  In
		 * particular, answer ±infinity, NaN, or zero.
		 *
		 * @return
		 */
		public double limitDouble ()
		{
			return limitDouble;
		}

		/**
		 * Answer the most extreme {@code float} with this sign.  In
		 * particular, answer ±infinity, NaN, or zero.
		 *
		 * @return
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
		final Set<Order> possibleResults = EnumSet.<Order>noneOf(Order.class);
		if (!firstType.typeIntersection(secondType).equals(
			BottomTypeDescriptor.bottom()))
		{
			// Note that this is not authoritative, since 0 is *numerically*
			// equal to 0.0f, 0.0d, -0.0f, and -0.0d.  However, if the types
			// have non-vacuous intersection then numeric equality is possible,
			// although it may be possible in other cases as well.
			possibleResults.add(EQUAL);
		}
		A_Number firstMin;
		A_Number firstMax;
		A_Number secondMin;
		A_Number secondMax;
		final boolean firstMinInclusive;
		final boolean firstMaxInclusive;
		final boolean secondMinInclusive;
		final boolean secondMaxInclusive;
		final List<A_Number> firstComparablesList = new ArrayList<>();
		if (firstType.isEnumeration())
		{
			// Note that this should work even if an enumeration contains
			// non-integers, or even NaNs.
			firstMinInclusive = true;
			firstMaxInclusive = true;
			final Iterator<AvailObject> firstInstances =
				firstType.instances().iterator();
			firstMin = firstMax = null;
			while (firstInstances.hasNext())
			{
				final A_Number value = firstInstances.next();
				if (value.numericCompare(value) == INCOMPARABLE)
				{
					possibleResults.add(INCOMPARABLE);
				}
				else
				{
					firstComparablesList.add(value);
					if (firstMin == null)
					{
						firstMin = firstMax = value;
					}
					else if (value.lessThan(firstMin))
					{
						firstMin = value;
					}
					else if (value.greaterThan(firstMax))
					{
						firstMax = value;
					}
				}
			}
		}
		else if (firstType.isIntegerRangeType())
		{
			firstMin = firstType.lowerBound();
			firstMax = firstType.upperBound();
			firstMinInclusive = firstType.lowerInclusive();
			firstMaxInclusive = firstType.upperInclusive();
		}
		else
		{
			possibleResults.add(LESS);
			possibleResults.add(MORE);
			possibleResults.add(EQUAL);
			possibleResults.add(INCOMPARABLE);
			return possibleResults;
		}
		final List<A_Number> secondComparablesList = new ArrayList<>();
		if (secondType.isEnumeration())
		{
			// Note that this should work even if an enumeration contains
			// non-integers, or even NaNs.
			secondMinInclusive = true;
			secondMaxInclusive = true;
			final Iterator<AvailObject> secondInstances =
				secondType.instances().iterator();
			secondMin = secondMax = null;
			while (secondInstances.hasNext())
			{
				final A_Number value = secondInstances.next();
				if (value.numericCompare(value) == INCOMPARABLE)
				{
					possibleResults.add(INCOMPARABLE);
				}
				else
				{
					secondComparablesList.add(value);
					if (secondMin == null)
					{
						secondMin = secondMax = value;
					}
					else if (value.lessThan(secondMin))
					{
						secondMin = value;
					}
					else if (value.greaterThan(secondMax))
					{
						secondMax = value;
					}
				}
			}
		}
		else if (secondType.isIntegerRangeType())
		{
			secondMin = secondType.lowerBound();
			secondMax = secondType.upperBound();
			secondMinInclusive = secondType.lowerInclusive();
			secondMaxInclusive = secondType.upperInclusive();
		}
		else
		{
			possibleResults.add(LESS);
			possibleResults.add(MORE);
			possibleResults.add(EQUAL);
			possibleResults.add(INCOMPARABLE);
			return possibleResults;
		}
		if (firstType.isEnumeration() && secondType.isEnumeration())
		{
			// Sort the enumerations and iterate over them in numeric order to
			// determine if there are any numerically equal values between the
			// two ordered sequences.
			final Comparator<A_Number> comparator = new Comparator<A_Number>()
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
			Collections.sort(firstComparablesList, comparator);
			Collections.sort(secondComparablesList, comparator);
			final Iterator<A_Number> firstIterator =
				firstComparablesList.iterator();
			final Iterator<A_Number> secondIterator =
				secondComparablesList.iterator();
			A_Number currentFirst = firstIterator.next();
			A_Number currentSecond = secondIterator.next();
			while (firstIterator.hasNext() && secondIterator.hasNext())
			{
				switch (currentFirst.numericCompare(currentSecond))
				{
					case LESS:
					{
						possibleResults.add(LESS);
						currentFirst = firstIterator.next();
						break;
					}
					case MORE:
					{
						possibleResults.add(MORE);
						currentSecond = secondIterator.next();
						break;
					}
					case EQUAL:
					{
						possibleResults.add(EQUAL);
						currentFirst = firstIterator.next();
						currentSecond = secondIterator.next();
						break;
					}
					default:
					{
						assert false;
					}
				}
			}
			if (firstIterator.hasNext())
			{
				possibleResults.add(MORE);
			} else if (secondIterator.hasNext())
			{
				possibleResults.add(LESS);
			}
			return possibleResults;
		}
		// Compare the effective ranges.  We can detect overlap of ranges by
		// considering two intervals A..B and C..D, and determining if
		// A ≤ D & C ≤ B.  This works when the ranges are inclusive, but we can
		// improve the precision by taking account of inclusive/exclusive
		// boundaries.
		if (firstMin == null || secondMin == null)
		{
			// One of the types contains only NaNs.  The result of the
			// comparison will always end up being INCOMPARABLE.
			assert possibleResults.contains(INCOMPARABLE);
			return possibleResults;
		}
		final Order compare1 = firstMin.numericCompare(secondMax);
		if (compare1 == LESS || compare1 == MORE)
		{
			possibleResults.add(compare1);
		}
		else if (compare1 == EQUAL)
		{
			if (firstMinInclusive && secondMaxInclusive)
			{
				possibleResults.add(EQUAL);
			}
		}
		final Order compare2 = secondMin.numericCompare(firstMax);
		if (compare2 == LESS || compare2 == MORE)
		{
			possibleResults.add(compare2.reverse());
		}
		else if (compare2 == EQUAL)
		{
			if (secondMinInclusive && firstMaxInclusive)
			{
				possibleResults.add(EQUAL);
			}
		}
		if (possibleResults.contains(LESS) && possibleResults.contains(MORE))
		{
			possibleResults.add(EQUAL);
		}
		return possibleResults;
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

	/**
	 * Construct a new {@link AbstractNumberDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
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
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, objectSlotsEnumClass, integerSlotsEnumClass);
	}
}
