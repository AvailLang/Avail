/**
 * InfinityDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.AbstractNumberDescriptor.Order.*;
import java.util.IdentityHashMap;

import com.avail.annotations.AvailMethod;
import com.avail.exceptions.*;
import com.avail.exceptions.ArithmeticException;
import com.avail.utility.json.JSONWriter;

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
	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
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
		return another.equalsInfinity(sign);
	}

	@Override @AvailMethod
	boolean o_EqualsInfinity (final AvailObject object, final Sign theSign)
	{
		return sign == theSign;
	}

	@Override @AvailMethod
	Order o_NumericCompareToInteger (
		final AvailObject object,
		final A_Number anInteger)
	{
		// Infinities are either above or below all integer, depending on sign.
		return sign == Sign.POSITIVE ? MORE : LESS;
	}

	@Override @AvailMethod
	Order o_NumericCompareToInfinity (
		final AvailObject object,
		final Sign theSign)
	{
		return DoubleDescriptor.compareDoubles(
			sign.limitDouble(), theSign.limitDouble());
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
		if (sign == Sign.POSITIVE)
		{
			return aType.upperBound().equals(object) && aType.upperInclusive();
		}
		return aType.lowerBound().equals(object) && aType.lowerInclusive();
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return sign == Sign.POSITIVE ? 0x14B326DA : 0xBF9302D;
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
		return aNumber.divideIntoInfinityCanDestroy(sign, canDestroy);
	}

	@Override @AvailMethod
	A_Number o_MinusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.subtractFromInfinityCanDestroy(sign, canDestroy);
	}

	@Override @AvailMethod
	A_Number o_PlusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.addToInfinityCanDestroy(sign, canDestroy);
	}

	@Override @AvailMethod
	A_Number o_TimesCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.multiplyByInfinityCanDestroy(sign, canDestroy);
	}

	@Override @AvailMethod
	boolean o_IsPositive (final AvailObject object)
	{
		return sign == Sign.POSITIVE;
	}

	@Override @AvailMethod
	A_Number o_AddToInfinityCanDestroy (
		final AvailObject object,
		final Sign theSign,
		final boolean canDestroy)
	{
		if (theSign == sign)
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
			doubleObject.extractDouble() + sign.limitDouble(),
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
			floatObject.extractFloat() + sign.limitFloat(),
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	A_Number o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final Sign theSign,
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
			doubleObject.extractDouble() / sign.limitDouble(),
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
			floatObject.extractFloat() / sign.limitFloat(),
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	A_Number o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final Sign theSign,
		final boolean canDestroy)
	{
		return (theSign == Sign.POSITIVE) == object.isPositive()
			? InfinityDescriptor.positiveInfinity()
			: InfinityDescriptor.negativeInfinity();
	}

	@Override @AvailMethod
	A_Number o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		if (anInteger.equalsInt(0))
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
			doubleObject.extractDouble() * sign.limitDouble(),
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
			floatObject.extractFloat() * sign.limitFloat(),
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	A_Number o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final Sign theSign,
		final boolean canDestroy)
	{
		if (theSign != sign)
		{
			return theSign == Sign.POSITIVE
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
			doubleObject.extractDouble() - sign.limitDouble(),
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
			floatObject.extractFloat() - sign.limitFloat(),
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	Order o_NumericCompare (final AvailObject object, final A_Number another)
	{
		return another.numericCompareToInfinity(sign).reverse();
	}

	@Override @AvailMethod
	Order o_NumericCompareToDouble (
		final AvailObject object,
		final double double1)
	{
		return DoubleDescriptor.compareDoubles(
			sign.limitDouble(),
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
			object.descriptor = shared();
		}
		return object;
	}

	@Override
	boolean o_IsNumericallyIntegral (final AvailObject object)
	{
		// Not finite, so not numerically equal to an integer.
		return false;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.write(sign == Sign.POSITIVE
			? Double.POSITIVE_INFINITY
			: Double.NEGATIVE_INFINITY);
	}

	/**
	 * Construct a new {@link InfinityDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param sign
	 *            The {@link Sign} of the infinity for this descriptor.
	 */
	private InfinityDescriptor (
		final Mutability mutability,
		final Sign sign)
	{
		super(
			mutability,
			sign == Sign.POSITIVE
				? TypeTag.POSITIVE_INFINITY_TAG
				: TypeTag.NEGATIVE_INFINITY_TAG,
			null,
			null);
		assert sign == Sign.POSITIVE || sign == Sign.NEGATIVE;
		this.sign = sign;
	}

	/** The {@link Sign} of infinity that my instance represents. */
	private final Sign sign;

	/** The mutable {@link InfinityDescriptor} for positive infinity. */
	private static final InfinityDescriptor mutablePositive =
		new InfinityDescriptor(Mutability.MUTABLE, Sign.POSITIVE);

	/** The mutable {@link InfinityDescriptor} for negative infinity. */
	private static final InfinityDescriptor mutableNegative =
		new InfinityDescriptor(Mutability.MUTABLE, Sign.NEGATIVE);

	/** The shared {@link InfinityDescriptor} for positive infinity. */
	private static final InfinityDescriptor sharedPositive =
		new InfinityDescriptor(Mutability.SHARED, Sign.POSITIVE);

	/** The shared {@link InfinityDescriptor} for negative infinity. */
	private static final InfinityDescriptor sharedNegative =
		new InfinityDescriptor(Mutability.SHARED, Sign.NEGATIVE);

	@Override
	AbstractDescriptor mutable ()
	{
		return sign == Sign.POSITIVE ? mutablePositive : mutableNegative;
	}

	@Override
	InfinityDescriptor immutable ()
	{
		// There isn't an immutable variant; answer a shared one.
		return shared();
	}

	@Override
	InfinityDescriptor shared ()
	{
		return sign == Sign.POSITIVE ? sharedPositive : sharedNegative;
	}

	/**
	 * The Avail {@linkplain ExtendedIntegerDescriptor extended integer}
	 * representing positive infinity.
	 */
	private static final A_Number positiveInfinity =
		mutablePositive.create().makeShared();

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
	private static final A_Number negativeInfinity =
		mutableNegative.create().makeShared();

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
