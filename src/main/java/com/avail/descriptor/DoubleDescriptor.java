/*
 * DoubleDescriptor.java
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

package com.avail.descriptor;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.optimizer.jvm.CheckedMethod;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.AbstractNumberDescriptor.Sign.*;
import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.DoubleDescriptor.IntegerSlots.LONG_BITS;
import static com.avail.descriptor.InfinityDescriptor.negativeInfinity;
import static com.avail.descriptor.InfinityDescriptor.positiveInfinity;
import static com.avail.descriptor.IntegerDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.DOUBLE;
import static com.avail.optimizer.jvm.CheckedMethod.staticMethod;
import static java.lang.Math.*;

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
		//noinspection FloatingPointEquality
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
	 *        The {@code double} value to compare numerically.
	 * @param anInteger
	 *        An {@link IntegerDescriptor Avail integer} to compare against.
	 * @return The {@link Order} of the double and integer.
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
			return zero().numericCompare(anInteger);
		}
		// The integer is beyond an int's range.  Perhaps even beyond a double.
		// For boundary purposes, check now if it's exactly integral.
		final double floorD = floor(aDouble);
		// Produce an Avail integer with the exact value from floorD.  If it's
		// more than about 2^60, scale it down to have about 60 bits of data.
		// Since the mantissa is only 53 bits, this will be exact.  Since floorD
		// is an integer, it also can't lose any information if it's *not*
		// scaled before being converted to a long.
		final int exponent = getExponent(aDouble);
		final int exponentAdjustment = max(exponent - 60, 0);
		final double normalD = scalb(floorD, -exponentAdjustment);
		assert Long.MIN_VALUE < normalD && normalD < Long.MAX_VALUE;
		A_Number integer = fromLong((long) normalD);
		if (exponentAdjustment > 0)
		{
			integer = integer.bitShift(
				fromInt(exponentAdjustment), true);
		}
		// We now have an Avail integer representing the exact same quantity as
		// floorD.
		final Order integerOrder = integer.numericCompare(anInteger);
		@SuppressWarnings("FloatingPointEquality")
		final boolean isIntegral = aDouble == floorD;
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
		final double adjustment = floor(aDouble);
		final A_Number adjustmentAsInteger = truncatedFromDouble(adjustment);
		final A_Number adjustedInteger =
			anInteger.minusCanDestroy(adjustmentAsInteger, canDestroy);
		final double adjustedIntegerAsDouble =
			adjustedInteger.extractDouble();
		return (aDouble - adjustment) + adjustedIntegerAsDouble;
	}

	@Override
	protected void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append(getDouble(object));
	}

	/**
	 * Construct an Avail double-precision floating point object from the passed
	 * {@code double}.  Do not answer an existing object.
	 *
	 * @param aDouble
	 *            The Java {@code double} to box.
	 * @return
	 *            The boxed Avail {@code DoubleDescriptor double-precision
	 *            floating point object}.
	 */
	@ReferencedInGeneratedCode
	public static A_Number fromDouble (final double aDouble)
	{
		final AvailObject result = mutable.create();
		final long castAsLong = Double.doubleToRawLongBits(aDouble);
		result.setSlot(LONG_BITS, castAsLong);
		return result;
	}
	/** The {@link CheckedMethod} for {@link #fromDouble(double)}. */
	public static final CheckedMethod fromDoubleMethod = staticMethod(
		DoubleDescriptor.class, "fromDouble", A_Number.class, double.class);

	/**
	 * Construct an Avail boxed double-precision floating point object from the
	 * passed {@code double}.
	 *
	 * @param aDouble
	 *        The Java {@code double} to box.
	 * @param recyclable1
	 *        An Avail {@code double} that may be reused if it's mutable.
	 * @param canDestroy
	 *        Whether the passed recyclable can be replaced if it's mutable.
	 * @return
	 *        The boxed Avail {@code DoubleDescriptor double-precision floating
	 *        point object}.
	 */
	public static A_Number fromDoubleRecycling (
		final double aDouble,
		final A_Number recyclable1,
		final boolean canDestroy)
	{
		final AvailObject result =
			canDestroy && recyclable1.descriptor().isMutable()
			? (AvailObject) recyclable1
			: mutable.create();
		final long castAsLong = Double.doubleToRawLongBits(aDouble);
		result.setSlot(LONG_BITS, castAsLong);
		return result;
	}

	/**
	 * Construct an Avail boxed double-precision floating point object from the
	 * passed {@code double}.
	 *
	 * @param aDouble
	 *        The Java {@code double} to box.
	 * @param recyclable1
	 *        An Avail {@code double} that may be reused if it's mutable.
	 * @param recyclable2
	 *        Another Avail {@code double} that may be reused if it's mutable.
	 * @param canDestroy
	 *        Whether one of the passed recyclables can be replaced if it's
	 *        mutable.
	 * @return The boxed Avail {@code double}.
	 */
	public static A_Number objectFromDoubleRecycling (
		final double aDouble,
		final A_Number recyclable1,
		final A_Number recyclable2,
		final boolean canDestroy)
	{
		final AvailObject result;
		if (canDestroy && recyclable1.descriptor().isMutable())
		{
			result = (AvailObject) recyclable1;
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
	 * Given a {@code double}, produce the {@code extended integer} that is
	 * nearest it but rounding toward zero.  Double infinities are converted to
	 * extended integer {@linkplain InfinityDescriptor infinities}.  Do not call
	 * with a {@code NaN} value.
	 *
	 * @param inputD The input {@code double}.
	 * @return The output {@code extended integer}.
	 */
	public static A_Number doubleTruncatedToExtendedInteger (
		final double inputD)
	{
		assert !Double.isNaN(inputD);
		if (inputD >= Long.MIN_VALUE && inputD <= Long.MAX_VALUE)
		{
			// Common case -- it fits in a long.
			return fromLong((long) inputD);
		}
		final boolean neg = inputD < 0.0d;
		if (Double.isInfinite(inputD))
		{
			// Return the corresponding integral infinity.
			return neg ? negativeInfinity() : positiveInfinity();
		}
		double d = abs(inputD);
		final int exponent = getExponent(d);
		final int slots = (exponent + 33) >> 5;  // probably needs work
		A_Number out = createUninitializedInteger(slots);
		d = scalb(d, (1 - slots) << 5);
		// In theory we could extract just the top three 32-bit sections.  That
		// would guarantee 65 bits of mantissa, which is more than a double
		// actually captures.
		for (int i = slots; i >= 1; --i)
		{
			final long unsignedIntSlice = (long) d;
			out.rawUnsignedIntegerAtPut(i, (int) unsignedIntSlice);
			d -= unsignedIntSlice;
			d = scalb(d, 32);
		}
		out.trimExcessInts();
		if (neg)
		{
			out = zero().noFailMinusCanDestroy(out, true);
		}
		return out;
	}

	/**
	 * Answer the Avail object representing {@code Double#POSITIVE_INFINITY}.
	 *
	 * @return The Avail object for double-precision positive infinity.
	 */
	public static A_Number doublePositiveInfinity ()
	{
		return POSITIVE.limitDoubleObject();
	}

	/**
	 * Answer the Avail object representing {@code Double#NEGATIVE_INFINITY}.
	 *
	 * @return The Avail object for double-precision negative infinity.
	 */
	public static A_Number doubleNegativeInfinity ()
	{
		return NEGATIVE.limitDoubleObject();
	}

	/**
	 * Answer the Avail object representing {@code Double#NaN}.
	 *
	 * @return The Avail object for double-precision not-a-number.
	 */
	public static A_Number doubleNotANumber ()
	{
		return INDETERMINATE.limitDoubleObject();
	}

	/**
	 * Answer the Avail object representing {@code 0.0d}.
	 *
	 * @return The Avail object for double-precision (positive) zero.
	 */
	public static A_Number doubleZero ()
	{
		return ZERO.limitDoubleObject();
	}

	@Override
	protected A_Number o_AddToInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			sign.limitDouble() + getDouble(object),
			object,
			canDestroy);
	}

	@Override
	protected A_Number o_AddToIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		final double sum = addDoubleAndIntegerCanDestroy(
			getDouble(object),
			anInteger,
			canDestroy);
		return fromDoubleRecycling(sum, object, canDestroy);
	}

	@Override
	protected A_Number o_AddToDoubleCanDestroy (
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
	protected A_Number o_AddToFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			getDouble(object) + floatObject.extractDouble(),
			object,
			canDestroy);
	}

	@Override
	protected A_Number o_DivideCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.divideIntoDoubleCanDestroy(
			object,
			canDestroy);
	}

	@Override
	protected A_Number o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			sign.limitDouble() / getDouble(object),
			object,
			canDestroy);
	}

	@Override
	protected A_Number o_DivideIntoIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			anInteger.extractDouble() / getDouble(object),
			object,
			canDestroy);
	}

	@Override
	protected A_Number o_DivideIntoDoubleCanDestroy (
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
	protected A_Number o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			floatObject.extractDouble() / getDouble(object),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	protected boolean o_Equals (
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
	protected boolean o_EqualsDouble (
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
	protected double o_ExtractDouble (final AvailObject object)
	{
		return getDouble(object);
	}

	@Override @AvailMethod
	protected float o_ExtractFloat (final AvailObject object)
	{
		return (float) getDouble(object);
	}

	@Override @AvailMethod
	protected int o_Hash (final AvailObject object)
	{
		final long bits = object.slot(LONG_BITS);
		final int low = (int) (bits >> 32);
		final int high = (int) bits;
		return (low ^ 0x29F2EAB8) * multiplier - (high ^ 0x47C453FD);
	}

	@Override @AvailMethod
	protected boolean o_IsDouble (final AvailObject object)
	{
		return true;
	}

	@Override
	protected boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		return aType.isSupertypeOfPrimitiveTypeEnum(DOUBLE);
	}

	@Override
	protected boolean o_IsNumericallyIntegral (final AvailObject object)
	{
		final double value = getDouble(object);
		if (Double.isInfinite(value) || Double.isNaN(value))
		{
			return false;
		}
		//noinspection FloatingPointEquality
		return floor(value) == value;
	}

	@Override @AvailMethod
	protected A_Type o_Kind (final AvailObject object)
	{
		return DOUBLE.o();
	}

	@Override
	protected Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return getDouble(object);
	}

	@Override
	protected A_Number o_MinusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.subtractFromDoubleCanDestroy(
			object,
			canDestroy);
	}

	@Override
	protected A_Number o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			sign.limitDouble() * getDouble(object),
			object,
			canDestroy);
	}

	@Override
	protected A_Number o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			anInteger.extractDouble() * getDouble(object),
			object,
			canDestroy);
	}

	@Override
	protected A_Number o_MultiplyByDoubleCanDestroy (
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
	protected A_Number o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			floatObject.extractDouble() * getDouble(object),
			object,
			canDestroy);
	}

	@Override
	protected Order o_NumericCompare (
		final AvailObject object,
		final A_Number another)
	{
		return another.numericCompareToDouble(getDouble(object)).reverse();
	}

	@Override
	protected Order o_NumericCompareToInfinity (
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
	protected Order o_NumericCompareToInteger (
		final AvailObject object,
		final AvailObject anInteger)
	{
		return compareDoubleAndInteger(getDouble(object), anInteger);
	}

	@Override
	protected Order o_NumericCompareToDouble (
		final AvailObject object,
		final double aDouble)
	{
		return compareDoubles(getDouble(object), aDouble);
	}

	@Override
	protected A_Number o_PlusCanDestroy (
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
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.DOUBLE;
	}

	@Override
	protected A_Number o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			sign.limitDouble() - getDouble(object),
			object,
			canDestroy);
	}

	@Override
	protected A_Number o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			addDoubleAndIntegerCanDestroy(
				0.0d - getDouble(object),
				anInteger,
				canDestroy),
			object,
			canDestroy);
	}

	@Override
	protected A_Number o_SubtractFromDoubleCanDestroy (
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
	protected A_Number o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			floatObject.extractDouble() - getDouble(object),
			object,
			canDestroy);
	}

	@Override
	protected A_Number o_TimesCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.multiplyByDoubleCanDestroy(
			object,
			canDestroy);
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.write(getDouble(object));
	}

	/**
	 * Construct a new {@code DoubleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private DoubleDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.DOUBLE_TAG, null, IntegerSlots.class);
	}

	/** The mutable {@link DoubleDescriptor}. */
	private static final DoubleDescriptor mutable =
		new DoubleDescriptor(Mutability.MUTABLE);

	@Override
	protected DoubleDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link DoubleDescriptor}. */
	private static final DoubleDescriptor immutable =
		new DoubleDescriptor(Mutability.IMMUTABLE);

	@Override
	protected DoubleDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link DoubleDescriptor}. */
	private static final DoubleDescriptor shared =
		new DoubleDescriptor(Mutability.SHARED);

	@Override
	protected DoubleDescriptor shared ()
	{
		return shared;
	}
}
