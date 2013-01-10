/**
 * AbstractNumberDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import com.avail.annotations.*;

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
		protected AvailObject limitDoubleObject;

		/**
		 * The {@link #limitFloat} as an Avail object.
		 */
		protected AvailObject limitFloatObject;

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
		private Order reverse;

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
			return reverse;
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

	@Override @AvailMethod
	abstract boolean o_Equals (
		final AvailObject object,
		final AvailObject another);

	@Override @AvailMethod
	abstract Order o_NumericCompare (
		final AvailObject object,
		final AvailObject another);

	@Override @AvailMethod
	abstract boolean o_IsInstanceOfKind (
		final AvailObject object,
		final AvailObject aType);

	@Override @AvailMethod
	abstract int o_Hash (
		final AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_DivideCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_MinusCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_PlusCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_TimesCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy);


	// Double-dispatched operations.

	@Override @AvailMethod
	abstract Order o_NumericCompareToInteger (
		final AvailObject object,
		final AvailObject anInteger);

	@Override @AvailMethod
	abstract Order o_NumericCompareToInfinity (
		final AvailObject object,
		final InfinityDescriptor.Sign sign);

	@Override @AvailMethod
	abstract Order o_NumericCompareToDouble (
		final AvailObject object,
		final double double1);

	@Override @AvailMethod
	abstract AvailObject o_AddToInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_AddToIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_AddToDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_AddToFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_DivideIntoIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract AvailObject o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract float o_ExtractFloat (AvailObject object);

	@Override @AvailMethod
	abstract double o_ExtractDouble (AvailObject object);

	/**
	 * Construct a new {@link AbstractNumberDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	protected AbstractNumberDescriptor (final Mutability mutability)
	{
		super(mutability);
	}
}
