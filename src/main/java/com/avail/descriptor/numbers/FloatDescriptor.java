/*
 * FloatDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.descriptor.numbers;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.BitField;
import com.avail.descriptor.IntegerSlotsEnum;
import com.avail.descriptor.Mutability;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.TypeTag;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.numbers.AbstractNumberDescriptor.Sign.*;
import static com.avail.descriptor.numbers.DoubleDescriptor.*;
import static com.avail.descriptor.numbers.FloatDescriptor.IntegerSlots.RAW_INT;
import static com.avail.descriptor.types.TypeDescriptor.Types.FLOAT;
import static java.lang.Float.floatToRawIntBits;

/**
 * A boxed, identityless Avail representation of IEEE-754 floating point values.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class FloatDescriptor
extends AbstractNumberDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * Only the low 32 bits are used for the {@link #RAW_INT}.
		 */
		@HideFieldInDebugger
		RAW_INT_AND_MORE;

		/**
		 * The Java {@code float} value, packed into an {@code int} field.
		 */
		static final BitField RAW_INT = bitField(RAW_INT_AND_MORE, 0, 32);
	}

	/**
	 * Extract a Java {@code float} from the argument, an {@link
	 * FloatDescriptor Avail float}.
	 *
	 * @param object An Avail single-precision floating point number.
	 * @return The corresponding Java float.
	 */
	private static float getFloat (
		final AvailObject object)
	{
		final int intBits = object.slot(RAW_INT);
		return Float.intBitsToFloat(intBits);
	}

	/**
	 * Extract a Java {@code double} from the argument, an {@link
	 * FloatDescriptor Avail float}.
	 *
	 * @param object An Avail single-precision floating point number.
	 * @return The corresponding Java double.
	 */
	private static double getDouble (
		final AvailObject object)
	{
		return getFloat(object);
	}

	/**
	 * Construct an Avail boxed floating point object from the passed {@code
	 * float}.  Don't answer an existing object.
	 *
	 * @param aFloat The Java {@code float} to box.
	 * @return The boxed Avail {@code float}.
	 */
	public static A_Number fromFloat (final float aFloat)
	{
		final AvailObject result = mutable.create();
		result.setSlot(
			RAW_INT,
			floatToRawIntBits(aFloat));
		return result;
	}

	/**
	 * Construct an Avail boxed floating point object from the passed {@code
	 * float}.
	 *
	 * @param aFloat
	 *            The Java {@code float} to box.
	 * @param recyclable1
	 *            A boxed float that may be reused if it's mutable.
	 * @param canDestroy
	 *            Whether the given float can be reused if it's mutable.
	 * @return
	 *            The boxed Avail {@code FloatDescriptor floating point object}.
	 */
	public static A_Number fromFloatRecycling (
		final float aFloat,
		final A_Number recyclable1,
		final boolean canDestroy)
	{
		final AvailObject result =
			canDestroy && recyclable1.descriptor().isMutable()
			? (AvailObject) recyclable1
			: mutable.create();
		result.setSlot(
			RAW_INT,
			floatToRawIntBits(aFloat));
		return result;
	}

	/**
	 * Construct an Avail boxed floating point object from the passed {@code
	 * float}.
	 *
	 * @param aFloat
	 *        The Java {@code float} to box.
	 * @param recyclable1
	 *        A boxed Avail {@code float} that may be reused if it's mutable.
	 * @param recyclable2
	 *        Another boxed Avail {@code float} that may be reused if it's
	 *        mutable.
	 * @param canDestroy
	 *        Whether one of the given boxed Avail floats can be reused if it's
	 *        mutable.
	 * @return The boxed Avail {@code float}.
	 */
	public static A_Number objectFromFloatRecycling (
		final float aFloat,
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
		result.setSlot(
			RAW_INT,
			floatToRawIntBits(aFloat));
		return result;
	}

	/**
	 * Answer the Avail object representing {@code Float#POSITIVE_INFINITY}.
	 *
	 * @return The Avail object for float positive infinity.
	 */
	public static A_Number floatPositiveInfinity ()
	{
		return POSITIVE.limitFloatObject();
	}

	/**
	 * Answer the Avail object representing {@code Float#NEGATIVE_INFINITY}.
	 *
	 * @return The Avail object for float negative infinity.
	 */
	public static A_Number floatNegativeInfinity ()
	{
		return NEGATIVE.limitFloatObject();
	}

	/**
	 * Answer the Avail object representing {@code Float#NaN}.
	 *
	 * @return The Avail object for float not-a-number.
	 */
	public static A_Number floatNotANumber ()
	{
		return INDETERMINATE.limitFloatObject();
	}

	/**
	 * Answer the Avail object representing {@code 0.0f}.
	 *
	 * @return The Avail object for float (positive) zero.
	 */
	public static A_Number floatZero ()
	{
		return ZERO.limitFloatObject();
	}

	@Override @AvailMethod
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append(object.extractFloat());
	}

	@Override @AvailMethod
	protected A_Number o_AddToInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return fromFloatRecycling(
			sign.limitFloat() + getFloat(object),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_AddToIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		final double sum = addDoubleAndIntegerCanDestroy(
			getDouble(object),
			anInteger,
			canDestroy);
		return fromFloatRecycling((float) sum, object, canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_AddToDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			doubleObject.extractDouble() + getFloat(object),
			doubleObject,
			canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_AddToFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return objectFromFloatRecycling(
			floatObject.extractFloat() + getFloat(object),
			object,
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_DivideCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.divideIntoFloatCanDestroy(
			object,
			canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return fromFloatRecycling(
			sign.limitFloat() / getFloat(object),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_DivideIntoIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		// Do the math with doubles so that spurious overflows *can't* happen.
		// That is, conversion from an integer to a float might overflow even
		// though the quotient wouldn't, but the expanded range of a double
		// should safely hold any integer that wouldn't cause the quotient to go
		// out of finite float range.
		return fromFloatRecycling(
			(float) (anInteger.extractDouble() / getDouble(object)),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	public A_Number o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			doubleObject.extractDouble() / getDouble(object),
			doubleObject,
			canDestroy);
	}

	@Override @AvailMethod
	public A_Number o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return objectFromFloatRecycling(
			floatObject.extractFloat() / getFloat(object),
			object,
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	protected float o_ExtractFloat (final AvailObject object)
	{
		return getFloat(object);
	}

	@Override @AvailMethod
	protected double o_ExtractDouble (final AvailObject object)
	{
		return getDouble(object);
	}

	@Override @AvailMethod
	protected boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another)
	{
		final boolean same = another.equalsFloat(getFloat(object));
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
	protected boolean o_EqualsFloat (
		final AvailObject object,
		final float aFloat)
	{
		// Java float equality is irreflexive, and therefore useless to us,
		// since Avail sets (at least) require reflexive equality.  Compare the
		// exact bits instead.
		return floatToRawIntBits(getFloat(object)) == floatToRawIntBits(aFloat);
	}

	@Override @AvailMethod
	protected int o_Hash (final AvailObject object)
	{
		return (object.slot(RAW_INT) ^ 0x16AE2BFD) * multiplier;
	}

	@Override @AvailMethod
	protected boolean o_IsFloat (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	protected boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		return aType.isSupertypeOfPrimitiveTypeEnum(FLOAT);
	}

	@Override
	protected boolean o_IsNumericallyIntegral (final AvailObject object)
	{
		final float value = getFloat(object);
		if (Float.isInfinite(value) || Float.isNaN(value))
		{
			return false;
		}
		//noinspection FloatingPointEquality
		return Math.floor(value) == value;
	}

	@Override @AvailMethod
	protected A_Type o_Kind (final AvailObject object)
	{
		return FLOAT.o();
	}

	@Override
	protected Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return getFloat(object);
	}

	@Override @AvailMethod
	protected A_Number o_MinusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.subtractFromFloatCanDestroy(
			object,
			canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return fromFloatRecycling(
			(float) (sign.limitDouble() * getFloat(object)),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		// Do the math with doubles to avoid intermediate overflow of the
		// integer in the case that the product could still be represented as a
		// float.
		return fromFloatRecycling(
			(float) (anInteger.extractDouble() * getDouble(object)),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	public A_Number o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			doubleObject.extractDouble() * getDouble(object),
			doubleObject,
			canDestroy);
	}

	@Override @AvailMethod
	public A_Number o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return objectFromFloatRecycling(
			floatObject.extractFloat() * getFloat(object),
			object,
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	protected Order o_NumericCompare (
		final AvailObject object, final A_Number another)
	{
		return another.numericCompareToDouble(getDouble(object)).reverse();
	}

	@Override @AvailMethod
	protected Order o_NumericCompareToDouble (
		final AvailObject object,
		final double double1)
	{
		final double thisDouble = getDouble(object);
		//noinspection FloatingPointEquality
		if (thisDouble == double1)
		{
			return Order.EQUAL;
		}
		if (thisDouble < double1)
		{
			return Order.LESS;
		}
		if (thisDouble > double1)
		{
			return Order.MORE;
		}
		return Order.INCOMPARABLE;
	}

	@Override @AvailMethod
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

	@Override @AvailMethod
	protected Order o_NumericCompareToInteger (
		final AvailObject object,
		final AvailObject anInteger)
	{
		final double thisDouble = getDouble(object);
		return compareDoubleAndInteger(thisDouble, anInteger);
	}

	@Override @AvailMethod
	protected A_Number o_PlusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.addToFloatCanDestroy(
			object,
			canDestroy);
	}

	@Override
	@AvailMethod @ThreadSafe
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.FLOAT;
	}

	@Override @AvailMethod
	protected A_Number o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return fromFloatRecycling(
			((float) sign.limitDouble()) - getFloat(object),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return fromFloatRecycling(
			(float) (addDoubleAndIntegerCanDestroy(
				0.0d - getDouble(object), anInteger, canDestroy)),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	public A_Number o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return fromDoubleRecycling(
			doubleObject.extractDouble() - getFloat(object),
			doubleObject,
			canDestroy);
	}

	@Override @AvailMethod
	public A_Number o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return objectFromFloatRecycling(
			floatObject.extractFloat() - getFloat(object),
			object,
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_TimesCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.multiplyByFloatCanDestroy(
			object,
			canDestroy);
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.write(getFloat(object));
	}

	/**
	 * Construct a new {@code FloatDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private FloatDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.FLOAT_TAG, null, IntegerSlots.class);
	}

	/** The mutable {@link FloatDescriptor}. */
	private static final FloatDescriptor mutable =
		new FloatDescriptor(Mutability.MUTABLE);

	@Override
	public FloatDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link FloatDescriptor}. */
	private static final FloatDescriptor immutable =
		new FloatDescriptor(Mutability.IMMUTABLE);

	@Override
	public FloatDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link FloatDescriptor}. */
	private static final FloatDescriptor shared =
		new FloatDescriptor(Mutability.SHARED);

	@Override
	public FloatDescriptor shared ()
	{
		return shared;
	}
}
