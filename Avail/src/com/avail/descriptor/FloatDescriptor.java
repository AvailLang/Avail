/**
 * descriptor/FloatDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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
import com.avail.annotations.NotNull;

/**
 * A boxed, identityless Avail representation of IEEE-754 floating point values.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class FloatDescriptor
extends Descriptor
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

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append(object.extractFloat());
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsFloat(object);
	}

	@Override
	public boolean o_EqualsFloat (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFloatObject)
	{
		// Java float equality is irreflexive, and therefore useless to us,
		// since Avail sets (at least) require reflexive equality.  Compare the
		// exact bits instead.
		if (Float.floatToRawIntBits(object.extractFloat())
			!= Float.floatToRawIntBits(aFloatObject.extractFloat()))
		{
			return false;
		}
		object.becomeIndirectionTo(aFloatObject);
		return true;
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.RAW_INT) ^ 0x16AE2BFD;
	}

	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return FLOAT.o();
	}

	@Override
	public float o_ExtractFloat (
		final @NotNull AvailObject object)
	{
		//  Extract a Smalltalk Float from object.

		final int castAsInt = object.integerSlot(IntegerSlots.RAW_INT);
		return Float.intBitsToFloat(castAsInt);
	}

	/**
	 * Construct an Avail boxed {@link FloatDescriptor floating point object}
	 * from the passed {@code float}.
	 *
	 * @param aFloat The Java {@code float} to box.
	 * @return The boxed Avail {@code FloatDescriptor floating point object}.
	 */
	public static AvailObject objectFromFloat (final float aFloat)
	{
		final AvailObject result = mutable().create();
		result.integerSlotPut(
			IntegerSlots.RAW_INT,
			Float.floatToRawIntBits(aFloat));;
		return result;
	}

	/**
	 * Construct an Avail boxed {@link FloatDescriptor floating point object}
	 * from the passed {@code float}.
	 *
	 * @param aFloat
	 *            The Java {@code float} to box.
	 * @param recyclable1
	 *            A boxed float that may be reused if it's mutable.
	 * @return
	 *            The boxed Avail {@code FloatDescriptor floating point object}.
	 */
	public static AvailObject objectFromFloatRecycling (
		final float aFloat,
		final AvailObject recyclable1)
	{
		AvailObject result;
		if (recyclable1.descriptor() == mutable())
		{
			result = recyclable1;
		}
		else
		{
			result = mutable().create();
		}
		result.integerSlotPut(
			IntegerSlots.RAW_INT,
			Float.floatToRawIntBits(aFloat));
		return result;
	}

	/**
	 * Construct an Avail boxed {@link FloatDescriptor floating point object}
	 * from the passed {@code float}.
	 *
	 * @param aFloat
	 *            The Java {@code float} to box.
	 * @param recyclable1
	 *            A boxed float that may be reused if it's mutable.
	 * @param recyclable2
	 *            Another boxed float that may be reused if it's mutable.
	 * @return
	 *            The boxed Avail {@code FloatDescriptor floating point object}.
	 */
	public static AvailObject objectFromFloatRecycling (
		final float aFloat,
		final AvailObject recyclable1,
		final AvailObject recyclable2)
	{
		AvailObject result;
		if (recyclable1.descriptor() == mutable())
		{
			result = recyclable1;
		}
		else if (recyclable2.descriptor() == mutable())
		{
			result = recyclable2;
		}
		else
		{
			result = mutable().create();
		}
		result.integerSlotPut(
			IntegerSlots.RAW_INT,
			Float.floatToRawIntBits(aFloat));
		return result;
	}

	/**
	 * Construct a new {@link FloatDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected FloatDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link FloatDescriptor}.
	 */
	private final static FloatDescriptor mutable = new FloatDescriptor(true);

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
	private final static FloatDescriptor immutable = new FloatDescriptor(false);

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
