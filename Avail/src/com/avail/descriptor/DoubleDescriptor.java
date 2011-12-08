/**
 * DoubleDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.DOUBLE;
import java.util.List;
import com.avail.annotations.*;

/**
 * A boxed, identityless Avail representation of IEEE-754 double-precision
 * floating point values.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class DoubleDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The low 32 bits of a packed Java {@code double} value.
		 */
		LOW_INT,

		/**
		 * The high 32 bits of a packed Java {@code double} value.
		 */
		HIGH_INT
	}


	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append(object.extractDouble());
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsDouble(object);
	}

	@Override @AvailMethod
	boolean o_EqualsDouble (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aDoubleObject)
	{
		// Java double equality is irreflexive, and therefore useless to us,
		// since Avail sets (at least) require reflexive equality.  Compare the
		// exact bits instead.
		if (Double.doubleToRawLongBits(object.extractDouble())
			!= Double.doubleToRawLongBits(aDoubleObject.extractDouble()))
		{
			return false;
		}
		object.becomeIndirectionTo(aDoubleObject);
		return true;
	}

	@Override @AvailMethod
	int o_Hash (final @NotNull AvailObject object)
	{
		final int low = object.integerSlot(IntegerSlots.LOW_INT);
		final int high = object.integerSlot(IntegerSlots.HIGH_INT);
		return (low ^ 0x29F2EAB8) - (high ^ 0x07C453FD);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (final @NotNull AvailObject object)
	{
		return DOUBLE.o();
	}

	@Override @AvailMethod
	boolean o_IsDouble (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	double o_ExtractDouble (final @NotNull AvailObject object)
	{
		final int low = object.integerSlot(IntegerSlots.LOW_INT);
		final int high = object.integerSlot(IntegerSlots.HIGH_INT);
		final long castAsLong = (low & 0xFFFFFFFFL) + (high << 32L);
		return Double.longBitsToDouble(castAsLong);
	}

	/**
	 * Construct an Avail boxed {@linkplain DoubleDescriptor double-precision
	 * floating point object} from the passed {@code double}.
	 *
	 * @param aDouble
	 *            The Java {@code double} to box.
	 * @return
	 *            The boxed Avail {@code DoubleDescriptor double-precision
	 *            floating point object}.
	 */
	public static AvailObject fromDouble (
		final double aDouble)
	{
		final AvailObject result = mutable().create();
		final long castAsLong = Double.doubleToRawLongBits(aDouble);
		result.integerSlotPut(
			IntegerSlots.LOW_INT,
			(int)castAsLong);
		result.integerSlotPut(
			IntegerSlots.HIGH_INT,
			(int)(castAsLong >> 32));
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
	 * @return
	 *            The boxed Avail {@code DoubleDescriptor double-precision
	 *            floating point object}.
	 */
	public static AvailObject objectFromDoubleRecycling (
		final double aDouble,
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
		final long castAsLong = Double.doubleToRawLongBits(aDouble);
		result.integerSlotPut(
			IntegerSlots.LOW_INT,
			(int)castAsLong);
		result.integerSlotPut(
			IntegerSlots.HIGH_INT,
			(int)(castAsLong >> 32));
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
	 *            Another {@linkplain DoubleDescriptor boxed Avail double} that may
	 *            be reused if it's mutable.
	 * @return
	 *            The boxed Avail {@code DoubleDescriptor double-precision
	 *            floating point object}.
	 */
	public static AvailObject objectFromDoubleRecycling (
		final double aDouble,
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
		final long castAsLong = Double.doubleToRawLongBits(aDouble);
		result.integerSlotPut(
			IntegerSlots.LOW_INT,
			(int)castAsLong);
		result.integerSlotPut(
			IntegerSlots.HIGH_INT,
			(int)(castAsLong >> 32));
		return result;
	}

	/**
	 * Construct a new {@link DoubleDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected DoubleDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link DoubleDescriptor}.
	 */
	private final static DoubleDescriptor mutable = new DoubleDescriptor(true);

	/**
	 * Answer the mutable {@link DoubleDescriptor}.
	 *
	 * @return The mutable {@link DoubleDescriptor}.
	 */
	public static DoubleDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link DoubleDescriptor}.
	 */
	private final static DoubleDescriptor immutable = new DoubleDescriptor(false);

	/**
	 * Answer the immutable {@link DoubleDescriptor}.
	 *
	 * @return The immutable {@link DoubleDescriptor}.
	 */
	public static DoubleDescriptor immutable ()
	{
		return immutable;
	}
}
