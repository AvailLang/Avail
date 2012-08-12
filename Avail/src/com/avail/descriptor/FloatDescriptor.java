/**
 * FloatDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.TypeDescriptor.Types.FLOAT;
import java.util.List;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * A boxed, identityless Avail representation of IEEE-754 floating point values.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class FloatDescriptor
extends AbstractNumberDescriptor
{

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The Java {@code float} value, packed into an {@code int} field.
		 */
		RAW_INT
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
		final int intBits = object.slot(IntegerSlots.RAW_INT);
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


	@Override @AvailMethod
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append(object.extractFloat());
		aStream.append('f');
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		final boolean same = another.equalsFloat(getFloat(object));
		if (same)
		{
			object.becomeIndirectionTo(another);
		}
		return same;
	}

	@Override @AvailMethod
	boolean o_EqualsFloat (
		final AvailObject object,
		final float aFloat)
	{
		// Java float equality is irreflexive, and therefore useless to us,
		// since Avail sets (at least) require reflexive equality.  Compare the
		// exact bits instead.
		return Float.floatToRawIntBits(getFloat(object))
			== Float.floatToRawIntBits(aFloat);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(IntegerSlots.RAW_INT) ^ 0x16AE2BFD;
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		return FLOAT.o();
	}

	@Override @AvailMethod
	boolean o_IsFloat (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	float o_ExtractFloat (final AvailObject object)
	{
		return getFloat(object);
	}

	@Override @AvailMethod
	double o_ExtractDouble (final AvailObject object)
	{
		return getDouble(object);
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final AvailObject aType)
	{
		return aType.isSupertypeOfPrimitiveTypeWithOrdinal(FLOAT.ordinal());
	}

	@Override @AvailMethod
	AvailObject o_DivideCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return aNumber.divideIntoFloatCanDestroy(
			object,
			canDestroy);
	}

	@Override @AvailMethod
	AvailObject o_MinusCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return aNumber.subtractFromFloatCanDestroy(
			object,
			canDestroy);
	}

	@Override @AvailMethod
	AvailObject o_PlusCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return aNumber.addToFloatCanDestroy(
			object,
			canDestroy);
	}

	@Override @AvailMethod
	AvailObject o_TimesCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return aNumber.multiplyByFloatCanDestroy(
			object,
			canDestroy);
	}

	@Override @AvailMethod
	Order o_NumericCompareToInteger (
		final AvailObject object,
		final AvailObject anInteger)
	{
		final double thisDouble = getDouble(object);
		return DoubleDescriptor.compareDoubleAndInteger(thisDouble, anInteger);
	}

	@Override @AvailMethod
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

	@Override @AvailMethod
	AvailObject o_AddToInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return objectFromFloatRecycling(
			sign.limitFloat() + getFloat(object),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	AvailObject o_AddToIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		final double sum = DoubleDescriptor.addDoubleAndIntegerCanDestroy(
			getDouble(object),
			anInteger,
			canDestroy);
		return objectFromFloatRecycling((float)sum, object, canDestroy);
	}

	@Override @AvailMethod
	AvailObject o_AddToDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		return DoubleDescriptor.objectFromDoubleRecycling(
			getDouble(doubleObject) + getFloat(object),
			doubleObject,
			canDestroy);
	}

	@Override @AvailMethod
	AvailObject o_AddToFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		return objectFromFloatRecycling(
			floatObject.extractFloat() + getFloat(object),
			object,
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	AvailObject o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return FloatDescriptor.objectFromFloatRecycling(
			sign.limitFloat() / getFloat(object),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	AvailObject o_DivideIntoIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		// Do the math with doubles so that spurious overflows *can't* happen.
		// That is, conversion from an integer to a float might overflow even
		// though the quotient wouldn't, but the expanded range of a double
		// should safely hold any integer that wouldn't cause the quotient to go
		// out of finite float range.
		return objectFromFloatRecycling(
			(float)(anInteger.extractDouble() / getDouble(object)),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	public AvailObject o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		return DoubleDescriptor.objectFromDoubleRecycling(
			doubleObject.extractDouble() / getDouble(object),
			doubleObject,
			canDestroy);
	}

	@Override @AvailMethod
	public AvailObject o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		return objectFromFloatRecycling(
			floatObject.extractFloat() / getFloat(object),
			object,
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	AvailObject o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return objectFromFloatRecycling(
			(float)(sign.limitDouble() * getFloat(object)),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	AvailObject o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		// Do the math with doubles to avoid intermediate overflow of the
		// integer in the case that the product could still be represented as a
		// float.
		return objectFromFloatRecycling(
			(float)(anInteger.extractDouble() * getDouble(object)),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	public AvailObject o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		return DoubleDescriptor.objectFromDoubleRecycling(
			doubleObject.extractDouble() * getDouble(object),
			doubleObject,
			canDestroy);
	}

	@Override @AvailMethod
	public AvailObject o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		return objectFromFloatRecycling(
			floatObject.extractFloat() * getFloat(object),
			object,
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	AvailObject o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return objectFromFloatRecycling(
			((float)sign.limitDouble()) - getFloat(object),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	AvailObject o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return objectFromFloatRecycling(
			(float)(DoubleDescriptor.addDoubleAndIntegerCanDestroy(
				0.0d - getDouble(object),
				anInteger,
				canDestroy)),
			object,
			canDestroy);
	}

	@Override @AvailMethod
	public AvailObject o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		return DoubleDescriptor.objectFromDoubleRecycling(
			getDouble(doubleObject) - getFloat(object),
			doubleObject,
			canDestroy);
	}

	@Override @AvailMethod
	public AvailObject o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		return objectFromFloatRecycling(
			floatObject.extractFloat() - getFloat(object),
			object,
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	Order o_NumericCompare (
		final AvailObject object,
		final AvailObject another)
	{
		return another.numericCompareToDouble(getDouble(object)).reverse();
	}

	@Override @AvailMethod
	Order o_NumericCompareToDouble (
		final AvailObject object,
		final double double1)
	{
		final double thisDouble = getDouble(object);
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

	@Override
	@AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.FLOAT;
	}

	@Override
	Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return Float.valueOf(getFloat(object));
	}

	/**
	 * Construct an Avail boxed {@linkplain FloatDescriptor floating point
	 * object} from the passed {@code float}.
	 *
	 * @param aFloat The Java {@code float} to box.
	 * @return The boxed Avail {@linkplain FloatDescriptor float}.
	 */
	public static AvailObject privateFloat (final float aFloat)
	{
		final AvailObject result = mutable().create();
		result.setSlot(
			IntegerSlots.RAW_INT,
			Float.floatToRawIntBits(aFloat));
		return result;
	}

	/**
	 * Construct an Avail boxed {@linkplain FloatDescriptor floating point object}
	 * from the passed {@code float}.
	 *
	 * @param aFloat The Java {@code float} to box.
	 * @return The boxed Avail {@linkplain FloatDescriptor float}.
	 */
	public static AvailObject fromFloat (final float aFloat)
	{
		if (Float.isNaN(aFloat))
		{
			return notANumber();
		}
		if (Float.isInfinite(aFloat))
		{
			return aFloat > 0.0f ? positiveInfinity() : negativeInfinity();
		}
		return privateFloat(aFloat);
	}

	/**
	 * Construct an Avail boxed {@linkplain FloatDescriptor floating point object}
	 * from the passed {@code float}.
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
	public static AvailObject objectFromFloatRecycling (
		final float aFloat,
		final AvailObject recyclable1,
		final boolean canDestroy)
	{
		AvailObject result;
		if (canDestroy && recyclable1.descriptor() == mutable())
		{
			result = recyclable1;
		}
		else
		{
			result = mutable().create();
		}
		result.setSlot(
			IntegerSlots.RAW_INT,
			Float.floatToRawIntBits(aFloat));
		return result;
	}

	/**
	 * Construct an Avail boxed {@linkplain FloatDescriptor floating point object}
	 * from the passed {@code float}.
	 *
	 * @param aFloat
	 *            The Java {@code float} to box.
	 * @param recyclable1
	 *            A boxed float that may be reused if it's mutable.
	 * @param recyclable2
	 *            Another boxed float that may be reused if it's mutable.
	 * @param canDestroy
	 *            Whether one of the given floats can be reused if it's mutable.
	 * @return
	 *            The boxed Avail {@code FloatDescriptor floating point object}.
	 */
	public static AvailObject objectFromFloatRecycling (
		final float aFloat,
		final AvailObject recyclable1,
		final AvailObject recyclable2,
		final boolean canDestroy)
	{
		AvailObject result;
		if (canDestroy && recyclable1.descriptor() == mutable())
		{
			result = recyclable1;
		}
		else if (canDestroy && recyclable2.descriptor() == mutable())
		{
			result = recyclable2;
		}
		else
		{
			result = mutable().create();
		}
		result.setSlot(
			IntegerSlots.RAW_INT,
			Float.floatToRawIntBits(aFloat));
		return result;
	}

	/**
	 * Answer the Avail object representing {@code Float#POSITIVE_INFINITY}.
	 *
	 * @return The Avail object for float positive infinity.
	 */
	static AvailObject positiveInfinity ()
	{
		return Sign.POSITIVE.limitFloatObject;
	}

	/**
	 * Answer the Avail object representing {@code Float#NEGATIVE_INFINITY}.
	 *
	 * @return The Avail object for float negative infinity.
	 */
	static AvailObject negativeInfinity ()
	{
		return Sign.NEGATIVE.limitFloatObject;
	}

	/**
	 * Answer the Avail object representing {@code Float#NaN}.
	 *
	 * @return The Avail object for float not-a-number.
	 */
	static AvailObject notANumber ()
	{
		return Sign.INDETERMINATE.limitFloatObject;
	}

	/**
	 * Answer the Avail object representing {@code 0.0f}.
	 *
	 * @return The Avail object for float (positive) zero.
	 */
	static AvailObject zero ()
	{
		return Sign.ZERO.limitFloatObject;
	}

	/**
	 * Create the {@code float} special values.
	 */
	static void createWellKnownObjects ()
	{
		Sign.POSITIVE.limitFloatObject =
			privateFloat(Float.POSITIVE_INFINITY).makeImmutable();
		Sign.NEGATIVE.limitFloatObject =
			privateFloat(Float.NEGATIVE_INFINITY).makeImmutable();
		Sign.INDETERMINATE.limitFloatObject =
			privateFloat(Float.NaN).makeImmutable();
		Sign.ZERO.limitFloatObject =
			privateFloat(0.0f).makeImmutable();
	}

	/**
	 * Release the {@code float} special values.
	 */
	static void clearWellKnownObjects ()
	{
		Sign.POSITIVE.limitFloatObject = null;
		Sign.NEGATIVE.limitFloatObject = null;
		Sign.INDETERMINATE.limitFloatObject = null;
		Sign.ZERO.limitFloatObject = null;
	}


	/**
	 * Construct a new {@link FloatDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	private FloatDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link FloatDescriptor}.
	 */
	private static final FloatDescriptor mutable = new FloatDescriptor(true);

	/**
	 * Answer the mutable {@link FloatDescriptor}.
	 *
	 * @return The mutable {@link FloatDescriptor}.
	 */
	public static FloatDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link FloatDescriptor}.
	 */
	private static final FloatDescriptor immutable = new FloatDescriptor(false);

	/**
	 * Answer the immutable {@link FloatDescriptor}.
	 *
	 * @return The immutable {@link FloatDescriptor}.
	 */
	public static FloatDescriptor immutable ()
	{
		return immutable;
	}
}
