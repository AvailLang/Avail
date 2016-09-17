/**
 * DoubleDescriptor.java
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

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.DoubleDescriptor.IntegerSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.IdentityHashMap;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.AbstractNumberDescriptor.*;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;
import org.jetbrains.annotations.Nullable;

/**
 * A boxed, identityless Avail representation of IEEE-754 double-precision
 * floating point values.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class DoubleDescriptor
extends AbstractNumberDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * A {@code long} whose bits are to be interpreted as a {@code double}.
		 */
		LONG_BITS;
	}

	/**
	 * Extract the Java {@code double} from the argument, an {@link
	 * DoubleDescriptor Avail double}.
	 *
	 * @param object An Avail double-precision floating point number.
	 * @return The corresponding Java double.
	 */
	private static double getDouble (final AvailObject object)
	{
		return Double.longBitsToDouble(object.slot(LONG_BITS));
	}

	/**
	 * Compare two Java double-precision floating point numbers.
	 *
	 * @param double1 The first double.
	 * @param double2 The second double.
	 * @return An {@link Order} describing how double1 compares to double2.
	 */
	static Order compareDoubles (
		final double double1,
		final double double2)
	{
		if (double1 == double2)
		{
			return Order.EQUAL;
		}
		if (double1 < double2)
		{
			return Order.LESS;
		}
		if (double1 > double2)
		{
			return Order.MORE;
		}
		return Order.INCOMPARABLE;
	}

	/**
	 * @param aDouble
	 * @param anInteger
	 * @return
	 */
	static Order compareDoubleAndInteger (
		final double aDouble,
		final A_Number anInteger)
	{
		if (Double.isNaN(aDouble))
		{
			return Order.INCOMPARABLE;
		}
		if (Double.isInfinite(aDouble))
		{
			// Compare double precision infinity to a finite integer.  Easy, as
			// negative double infinity is below all integers and positive
			// double infinity is above them all.
			return compareDoubles(aDouble, 0.0d);
		}
		if (anInteger.isInt())
		{
			// Doubles can exactly represent every int (but not every long).
			return compareDoubles(aDouble, anInteger.extractInt());
		}
		if (aDouble == 0.0)
		{
			// Zeros are easy.
			return IntegerDescriptor.zero().numericCompare(anInteger);
		}
		// The integer is beyond an int's range.  Perhaps even beyond a double.
		// For boundary purposes, check now if it's exactly integral.
		final double floorD = Math.floor(aDouble);
		final boolean isIntegral = aDouble == floorD;
		// Produce an Avail integer with the exact value from floorD.  If it's
		// more than about 2^60, scale it down to have about 60 bits of data.
		// Since the mantissa is only 53 bits, this will be exact.  Since floorD
		// is an integer, it also can't lose any information if it's *not*
		// scaled before being converted to a long.
		final int exponent = Math.getExponent(aDouble);
		final int exponentAdjustment = Math.max(exponent - 60, 0);
		final double normalD = Math.scalb(floorD, -exponentAdjustment);
		assert Long.MIN_VALUE < normalD && normalD < Long.MAX_VALUE;
		final A_Number normalInteger =
			IntegerDescriptor.fromLong((long)normalD);
		A_Number integer = normalInteger;
		if (exponentAdjustment > 0)
		{
			integer = integer.bitShift(
				IntegerDescriptor.fromInt(exponentAdjustment), true);
		}
		// We now have an Avail integer representing the exact same quantity as
		// floorD.
		final Order integerOrder = integer.numericCompare(anInteger);
		if (!isIntegral && integerOrder == Order.EQUAL)
		{
			// d is actually a fraction of a unit bigger than the integer we
			// built, so if that integer and another happen to be equal, the
			// double argument must be considered bigger.
			return Order.MORE;
		}
		return integerOrder;
	}

	/**
	 * Compute the sum of a Java {@code double} and an Avail {@linkplain
	 * IntegerDescriptor integer}.  Answer the double nearest this ideal value.
	 *
	 * @param aDouble A {@code double} value.
	 * @param anInteger An Avail integer to add.
	 * @param canDestroy Whether anInteger can be destroyed (if it's mutable).
	 * @return The sum as a {@code double}.
	 */
	static double addDoubleAndIntegerCanDestroy (
		final double aDouble,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		if (Double.isInfinite(aDouble))
		{
			// The other value is finite, so it doesn't affect the sum.
			return aDouble;
		}
		final double anIntegerAsDouble = anInteger.extractDouble();
		if (!Double.isInfinite(anIntegerAsDouble))
		{
			// Both values are representable as finite doubles.  Easy.
			return anIntegerAsDouble + aDouble;
		}
		// The integer is too big for a double, but the sum isn't
		// necessarily.  Split the double operand into truncation toward
		// zero and residue,  Add the truncation (as an integer) to the
		// integer, convert to double, and add the residue (-1<r<1).
		final double adjustment = Math.floor(aDouble);
		final A_Number adjustmentAsInteger =
			IntegerDescriptor.truncatedFromDouble(adjustment);
		final A_Number adjustedInteger =
			anInteger.minusCanDestroy(adjustmentAsInteger, canDestroy);
		final double adjustedIntegerAsDouble =
			adjustedInteger.extractDouble();
		return (aDouble - adjustment) + adjustedIntegerAsDouble;
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append(getDouble(object));
	}

	/**
	 * Construct an Avail boxed {@linkplain DoubleDescriptor double-precision
	 * floating point object} from the passed {@code double}.  Do not answer an
	 * existing object.
	 *
	 * @param aDouble
	 *            The Java {@code double} to box.
	 * @return
	 *            The boxed Avail {@code DoubleDescriptor double-precision
	 *            floating point object}.
	 */
	public static A_Number fromDouble (final double aDouble)
	{
		final AvailObject result = mutable.create();
		final long castAsLong = Double.doubleToRawLongBits(aDouble);
		result.setSlot(LONG_BITS, castAsLong);
		return result;
	}

	/**
	 * Construct an Avail boxed {@linkplain DoubleDescriptor double-precision
	 * floating point object} from the passed {@code double}.
	 *
	 * @param aDouble
	 *            The Java {@code double} to box.
	 * @param recyclable1
	 *            A {@linkplain DoubleDescriptor boxed Avail double} that may be
	 *            reused if it's mutable.
	 * @param canDestroy
	 *            Whether the passed recyclable can be replaced if it's mutable.
	 * @return
	 *            The boxed Avail {@code DoubleDescriptor double-precision
	 *            floating point object}.
	 */
	public static A_Number objectFromDoubleRecycling (
		final double aDouble,
		final A_Number recyclable1,
		final boolean canDestroy)
	{
		final AvailObject result =
			canDestroy && recyclable1.descriptor().isMutable()
			? (AvailObject)recyclable1
			: mutable.create();
		final long castAsLong = Double.doubleToRawLongBits(aDouble);
		result.setSlot(LONG_BITS, castAsLong);
		return result;
	}

	/**
	 * Construct an Avail boxed {@linkplain DoubleDescriptor double-precision
	 * floating point object} from the passed {@code double}.
	 *
	 * @param aDouble
	 *            The Java {@code double} to box.
	 * @param recyclable1
	 *            A {@linkplain DoubleDescriptor boxed Avail double} that may be
	 *            reused if it's mutable.
	 * @param recyclable2
	 *            Another {@linkplain DoubleDescriptor boxed Avail double} that
	 *            may be reused if it's mutable.
	 * @param canDestroy
	 *            Whether one of the passed recyclables can be replaced if it's
	 *            mutable.
	 * @return
	 *            The boxed Avail {@code DoubleDescriptor double-precision
	 *            floating point object}.
	 */
	public static A_Number objectFromDoubleRecycling (
		final double aDouble,
		final A_Number recyclable1,
		final A_Number recyclable2,
		final boolean canDestroy)
	{
		AvailObject result;
		if (canDestroy && recyclable1.descriptor().isMutable())
		{
			result = (AvailObject)recyclable1;
		}
		else if (canDestroy && recyclable2.descriptor().isMutable())
		{
			result = (AvailObject) recyclable2;
		}
		else
		{
			result = mutable.create();
		}
		final long castAsLong = Double.doubleToRawLongBits(aDouble);
		result.setSlot(LONG_BITS, castAsLong);
		return result;
	}

	/**
	 * Answer the Avail object representing {@code Double#POSITIVE_INFINITY}.
	 *
	 * @return The Avail object for double-precision positive infinity.
	 */
	public static A_Number positiveInfinity ()
	{
		return Sign.POSITIVE.limitDoubleObject();
	}

	/**
	 * Answer the Avail object representing {@code Double#NEGATIVE_INFINITY}.
	 *
	 * @return The Avail object for double-precision negative infinity.
	 */
	public static A_Number negativeInfinity ()
	{
		return Sign.NEGATIVE.limitDoubleObject();
	}

	/**
	 * Answer the Avail object representing {@code Double#NaN}.
	 *
	 * @return The Avail object for double-precision not-a-number.
	 */
	public static A_Number notANumber ()
	{
		return Sign.INDETERMINATE.limitDoubleObject();
	}

	/**
	 * Answer the Avail object representing {@code 0.0d}.
	 *
	 * @return The Avail object for double-precision (positive) zero.
	 */
	public static A_Number zero ()
	{
		return Sign.ZERO.limitDoubleObject();
	}

	@Override
	A_Number o_AddToInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			sign.limitDouble() + getDouble(object),
			object,
			canDestroy);
	}

	@Override
	A_Number o_AddToIntegerCanDestroy (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		final double sum = addDoubleAndIntegerCanDestroy(
			getDouble(object),
			anInteger,
			canDestroy);
		return objectFromDoubleRecycling(sum, object, canDestroy);
	}

	@Override
	A_Number o_AddToDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			getDouble(object) + doubleObject.extractDouble(),
			object,
			doubleObject,
			canDestroy);
	}

	@Override
	A_Number o_AddToFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			getDouble(object) + floatObject.extractDouble(),
			object,
			canDestroy);
	}

	@Override
	A_Number o_DivideCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.divideIntoDoubleCanDestroy(
			object,
			canDestroy);
	}

	@Override
	A_Number o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			sign.limitDouble() / getDouble(object),
			object,
			canDestroy);
	}

	@Override
	A_Number o_DivideIntoIntegerCanDestroy (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			anInteger.extractDouble() / getDouble(object),
			object,
			canDestroy);
	}

	@Override
	A_Number o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			doubleObject.extractDouble() / getDouble(object),
			object,
			doubleObject,
			canDestroy);
	}

	@Override
	A_Number o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			floatObject.extractDouble() / getDouble(object),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another)
	{
		final boolean same = another.equalsDouble(getDouble(object));
		if (same)
		{
			if (!isShared())
			{
				another.makeImmutable();
				object.becomeIndirectionTo(another);
			}
			else if (!another.descriptor().isShared())
			{
				object.makeImmutable();
				another.becomeIndirectionTo(object);
			}
		}
		return same;
	}

	@Override @AvailMethod
	boolean o_EqualsDouble (
		final AvailObject object,
		final double aDouble)
	{
		// Java double equality is irreflexive, and therefore useless to us,
		// since Avail sets (at least) require reflexive equality. Compare the
		// exact bits instead.
		return Double.doubleToRawLongBits(getDouble(object))
			== Double.doubleToRawLongBits(aDouble);
	}

	@Override @AvailMethod
	double o_ExtractDouble (final AvailObject object)
	{
		return getDouble(object);
	}

	@Override @AvailMethod
	float o_ExtractFloat (final AvailObject object)
	{
		return (float)getDouble(object);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		final long bits = object.slot(LONG_BITS);
		final int low = (int)(bits >> 32);
		final int high = (int)bits;
		return (low ^ 0x29F2EAB8) * multiplier - (high ^ 0x47C453FD);
	}

	@Override @AvailMethod
	boolean o_IsDouble (final AvailObject object)
	{
		return true;
	}

	@Override
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		return aType.isSupertypeOfPrimitiveTypeEnum(DOUBLE);
	}

	@Override
	boolean o_IsNumericallyIntegral (final AvailObject object)
	{
		final double value = getDouble(object);
		if (Double.isInfinite(value) || Double.isNaN(value))
		{
			return false;
		}
		return Math.floor(value) == value;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return DOUBLE.o();
	}

	@Override
	@Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return Double.valueOf(getDouble(object));
	}

	@Override
	A_Number o_MinusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.subtractFromDoubleCanDestroy(
			object,
			canDestroy);
	}

	@Override
	A_Number o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			sign.limitDouble() * getDouble(object),
			object,
			canDestroy);
	}

	@Override
	A_Number o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			anInteger.extractDouble() * getDouble(object),
			object,
			canDestroy);
	}

	@Override
	A_Number o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			doubleObject.extractDouble() * getDouble(object),
			object,
			doubleObject,
			canDestroy);
	}

	@Override
	A_Number o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			floatObject.extractDouble() * getDouble(object),
			object,
			canDestroy);
	}

	@Override
	Order o_NumericCompare (
		final AvailObject object,
		final A_Number another)
	{
		return another.numericCompareToDouble(getDouble(object)).reverse();
	}

	@Override
	Order o_NumericCompareToInfinity (
		final AvailObject object,
		final Sign sign)
	{
		final double thisDouble = getDouble(object);
		if (Double.isNaN(thisDouble))
		{
			return Order.INCOMPARABLE;
		}
		final int comparison = Double.compare(thisDouble, sign.limitDouble());
		if (comparison < 0)
		{
			return Order.LESS;
		}
		if (comparison > 0)
		{
			return Order.MORE;
		}
		return Order.EQUAL;
	}

	@Override
	Order o_NumericCompareToInteger (
		final AvailObject object,
		final A_Number anInteger)
	{
		return compareDoubleAndInteger(getDouble(object), anInteger);
	}

	@Override
	Order o_NumericCompareToDouble (
		final AvailObject object,
		final double aDouble)
	{
		return compareDoubles(getDouble(object), aDouble);
	}

	@Override
	A_Number o_PlusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.addToDoubleCanDestroy(
			object,
			canDestroy);
	}

	@Override
	@AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.DOUBLE;
	}

	@Override
	A_Number o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			sign.limitDouble() - getDouble(object),
			object,
			canDestroy);
	}

	@Override
	A_Number o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			addDoubleAndIntegerCanDestroy(
				0.0d - getDouble(object),
				anInteger,
				canDestroy),
			object,
			canDestroy);
	}

	@Override
	A_Number o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			doubleObject.extractDouble() - getDouble(object),
			object,
			doubleObject,
			canDestroy);
	}

	@Override
	A_Number o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return objectFromDoubleRecycling(
			floatObject.extractDouble() - getDouble(object),
			object,
			canDestroy);
	}

	@Override
	A_Number o_TimesCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.multiplyByDoubleCanDestroy(
			object,
			canDestroy);
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.write(getDouble(object));
	}

	/**
	 * Construct a new {@link DoubleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private DoubleDescriptor (final Mutability mutability)
	{
		super(mutability, null, IntegerSlots.class);
	}

	/** The mutable {@link DoubleDescriptor}. */
	private static final DoubleDescriptor mutable =
		new DoubleDescriptor(Mutability.MUTABLE);

	@Override
	DoubleDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link DoubleDescriptor}. */
	private static final DoubleDescriptor immutable =
		new DoubleDescriptor(Mutability.IMMUTABLE);

	@Override
	DoubleDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link DoubleDescriptor}. */
	private static final DoubleDescriptor shared =
		new DoubleDescriptor(Mutability.SHARED);

	@Override
	DoubleDescriptor shared ()
	{
		return shared;
	}
}
