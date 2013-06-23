/**
 * InfinityDescriptor.java
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

import static com.avail.descriptor.InfinityDescriptor.IntegerSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.AbstractNumberDescriptor.Order.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.exceptions.*;
import com.avail.exceptions.ArithmeticException;

/**
 * I represent the {@linkplain ExtendedIntegerDescriptor extended integers}
 * positive infinity and negative infinity.  By supporting these as first-class
 * values in Avail we eliminate arbitrary limits, awkward duplication of effort,
 * and a host of other dangling singularities.  For example, it makes sense to
 * talk about iterating from 1 to infinity.  Infinities also play a key role in
 * {@linkplain IntegerRangeTypeDescriptor integer range types}, specifically by
 * their appearance as inclusive or exclusive bounds.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class InfinityDescriptor
extends ExtendedIntegerDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * A slot to indicate the sign of the infinity.
		 */
		@EnumField(describedBy=Sign.class)
		SIGN
	}

	/**
	 * Compute the {@linkplain AbstractNumberDescriptor.Sign sign} of the given
	 * {@linkplain InfinityDescriptor infinity}.
	 *
	 * @param anInfinity The infinity to examine.
	 * @return The {@code Sign} of the infinity.
	 */
	private static Sign getSign (final AvailObject anInfinity)
	{
		return anInfinity.slot(SIGN) == Sign.POSITIVE.ordinal()
			? Sign.POSITIVE
			: Sign.NEGATIVE;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		if (!object.isPositive())
		{
			aStream.append('-');
		}
		aStream.append('\u221E');
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsInfinity(getSign(object));
	}

	@Override @AvailMethod
	boolean o_EqualsInfinity (final AvailObject object, final Sign sign)
	{
		return object.slot(SIGN) == sign.ordinal();
	}

	@Override @AvailMethod
	Order o_NumericCompareToInteger (
		final AvailObject object,
		final A_Number anInteger)
	{
		// Infinities are either above or below all integer, depending on sign.
		return getSign(object) == Sign.POSITIVE ? MORE : LESS;
	}

	@Override @AvailMethod
	Order o_NumericCompareToInfinity (
		final AvailObject object,
		final Sign sign)
	{
		return DoubleDescriptor.compareDoubles(
			getSign(object).limitDouble(),
			sign.limitDouble());
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		if (aType.isSupertypeOfPrimitiveTypeEnum(NUMBER))
		{
			return true;
		}
		if (!aType.isIntegerRangeType())
		{
			return false;
		}
		if (object.isPositive())
		{
			return aType.upperBound().equals(object) && aType.upperInclusive();
		}
		return aType.lowerBound().equals(object) && aType.lowerInclusive();
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.isPositive() ? 0x14B326DA : 0xBF9302D;
	}

	@Override @AvailMethod
	boolean o_IsFinite (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return IntegerRangeTypeDescriptor.singleInteger(object);
	}

	@Override @AvailMethod
	double o_ExtractDouble (final AvailObject object)
	{
		return object.isPositive()
			? Double.POSITIVE_INFINITY
			: Double.NEGATIVE_INFINITY;
	}

	@Override @AvailMethod
	float o_ExtractFloat (
		final AvailObject object)
	{
		return object.isPositive()
			? Float.POSITIVE_INFINITY
			: Float.NEGATIVE_INFINITY;
	}

	@Override @AvailMethod
	A_Number o_DivideCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.divideIntoInfinityCanDestroy(
			getSign(object),
			canDestroy);
	}

	@Override @AvailMethod
	A_Number o_MinusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.subtractFromInfinityCanDestroy(
			getSign(object),
			canDestroy);
	}

	@Override @AvailMethod
	A_Number o_PlusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.addToInfinityCanDestroy(getSign(object), canDestroy);
	}

	@Override @AvailMethod
	A_Number o_TimesCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.multiplyByInfinityCanDestroy(
			getSign(object),
			canDestroy);
	}

	@Override @AvailMethod
	boolean o_IsPositive (final AvailObject object)
	{
		return object.slot(SIGN) == Sign.POSITIVE.ordinal();
	}

	@Override @AvailMethod
	A_Number o_AddToInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		if (sign == getSign(object))
		{
			return object;
		}
		throw new ArithmeticException(
			AvailErrorCode.E_CANNOT_ADD_UNLIKE_INFINITIES);
	}

	@Override @AvailMethod
	A_Number o_AddToIntegerCanDestroy (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return object;
	}

	@Override @AvailMethod
	A_Number o_AddToDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return DoubleDescriptor.objectFromDoubleRecycling(
			doubleObject.extractDouble() + getSign(object).limitDouble(),
			doubleObject,
			canDestroy);
	}

	@Override @AvailMethod
	A_Number o_AddToFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return FloatDescriptor.objectFromFloatRecycling(
			floatObject.extractFloat() + getSign(object).limitFloat(),
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	A_Number o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		throw new ArithmeticException(
			AvailErrorCode.E_CANNOT_DIVIDE_INFINITIES);
	}

	@Override @AvailMethod
	A_Number o_DivideIntoIntegerCanDestroy (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return IntegerDescriptor.zero();
	}

	@Override @AvailMethod
	public A_Number o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return DoubleDescriptor.objectFromDoubleRecycling(
			doubleObject.extractDouble() / getSign(object).limitDouble(),
			doubleObject,
			canDestroy);
	}

	@Override @AvailMethod
	public A_Number o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return FloatDescriptor.objectFromFloatRecycling(
			floatObject.extractFloat() / getSign(object).limitFloat(),
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	A_Number o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return (sign == Sign.POSITIVE) == object.isPositive()
			? InfinityDescriptor.positiveInfinity()
			: InfinityDescriptor.negativeInfinity();
	}

	@Override @AvailMethod
	A_Number o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		if (anInteger.equals(IntegerDescriptor.zero()))
		{
			throw new ArithmeticException(
				AvailErrorCode.E_CANNOT_MULTIPLY_ZERO_AND_INFINITY);
		}
		return anInteger.greaterThan(IntegerDescriptor.zero())
				^ object.isPositive()
			? InfinityDescriptor.negativeInfinity()
			: InfinityDescriptor.positiveInfinity();
	}

	@Override @AvailMethod
	public A_Number o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return DoubleDescriptor.objectFromDoubleRecycling(
			doubleObject.extractDouble() * getSign(object).limitDouble(),
			doubleObject,
			canDestroy);
	}

	@Override @AvailMethod
	public A_Number o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return FloatDescriptor.objectFromFloatRecycling(
			floatObject.extractFloat() * getSign(object).limitFloat(),
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	A_Number o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		if (sign != getSign(object))
		{
			return sign == Sign.POSITIVE
				? positiveInfinity()
				: negativeInfinity();
		}
		throw new ArithmeticException(
			AvailErrorCode.E_CANNOT_SUBTRACT_LIKE_INFINITIES);
	}

	@Override @AvailMethod
	A_Number o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return object.isPositive()
			? InfinityDescriptor.negativeInfinity()
			: InfinityDescriptor.positiveInfinity();
	}

	@Override @AvailMethod
	public A_Number o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return DoubleDescriptor.objectFromDoubleRecycling(
			doubleObject.extractDouble() - getSign(object).limitDouble(),
			doubleObject,
			canDestroy);
	}

	@Override @AvailMethod
	public A_Number o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return FloatDescriptor.objectFromFloatRecycling(
			floatObject.extractFloat() - getSign(object).limitFloat(),
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	Order o_NumericCompare (final AvailObject object, final A_Number another)
	{
		return another.numericCompareToInfinity(getSign(object)).reverse();
	}

	@Override @AvailMethod
	Order o_NumericCompareToDouble (
		final AvailObject object,
		final double double1)
	{
		return DoubleDescriptor.compareDoubles(
			getSign(object).limitDouble(),
			double1);
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		assert isShared();
		return object;
	}

	@Override @AvailMethod
	AvailObject o_MakeShared (final AvailObject object)
	{
		if (!isShared())
		{
			object.descriptor = shared;
		}
		return object;
	}

	/**
	 * Return an infinity with the given sign.  Only valid for {@link
	 * Sign#POSITIVE} and {@link Sign#NEGATIVE}.
	 *
	 * @param sign
	 * @return
	 */
	public static A_Number fromSign (final Sign sign)
	{
		if (sign == Sign.POSITIVE)
		{
			return positiveInfinity();
		}
		if (sign == Sign.NEGATIVE)
		{
			return negativeInfinity();
		}
		throw new RuntimeException("Invalid sign for infinity");
	}

	/**
	 * Construct a new {@link InfinityDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private InfinityDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link InfinityDescriptor}. */
	private static final InfinityDescriptor mutable =
		new InfinityDescriptor(Mutability.MUTABLE);

	@Override
	InfinityDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link InfinityDescriptor}. */
	private static final InfinityDescriptor shared =
		new InfinityDescriptor(Mutability.SHARED);

	@Override
	InfinityDescriptor immutable ()
	{
		// There isn't an immutable variant; answer the shared one.
		return shared;
	}

	@Override
	InfinityDescriptor shared ()
	{
		return shared;
	}

	/**
	 * The Avail {@linkplain ExtendedIntegerDescriptor extended integer}
	 * representing positive infinity.
	 */
	private static final A_Number positiveInfinity;

	static
	{
		final AvailObject positive = mutable.create();
		positive.setSlot(
			SIGN,
			Sign.POSITIVE.ordinal());
		positiveInfinity = positive.makeShared();
	}

	/**
	 * Answer the positive infinity object.
	 *
	 * @return Positive infinity.
	 */
	public static A_Number positiveInfinity ()
	{
		return positiveInfinity;
	}

	/**
	 * The Avail {@linkplain ExtendedIntegerDescriptor extended integer}
	 * representing negative infinity.
	 */
	private static final A_Number negativeInfinity;

	static
	{
		final AvailObject negative = mutable.create();
		negative.setSlot(
			SIGN,
			Sign.NEGATIVE.ordinal());
		negativeInfinity = negative.makeShared();
	}

	/**
	 * Answer the negative infinity object.
	 *
	 * @return Negative infinity.
	 */
	public static A_Number negativeInfinity ()
	{
		return negativeInfinity;
	}
}
