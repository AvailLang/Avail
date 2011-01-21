/**
 * descriptor/DoubleDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;

public class DoubleDescriptor extends Descriptor
{

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		RAW_QUAD_1,
		RAW_QUAD_2
	}


	// GENERATED accessors

	/**
	 * Setter for field rawQuad1.
	 */
	@Override
	public void o_RawQuad1 (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.RAW_QUAD_1, value);
	}

	/**
	 * Setter for field rawQuad2.
	 */
	@Override
	public void o_RawQuad2 (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.RAW_QUAD_2, value);
	}

	/**
	 * Getter for field rawQuad1.
	 */
	@Override
	public int o_RawQuad1 (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.RAW_QUAD_1);
	}

	/**
	 * Getter for field rawQuad2.
	 */
	@Override
	public int o_RawQuad2 (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.RAW_QUAD_2);
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object,
			final StringBuilder aStream,
			final List<AvailObject> recursionList,
			final int indent)
	{
		aStream.append(object.extractDouble());
	}



	// operations

	@Override
	public boolean o_Equals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsDouble(object);
	}

	@Override
	public boolean o_EqualsDouble (
			final AvailObject object,
			final AvailObject aDoubleObject)
	{
		if (object.extractDouble() != aDoubleObject.extractDouble())
		{
			return false;
		}
		object.becomeIndirectionTo(aDoubleObject);
		return true;
	}

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		return DOUBLE.o();
	}

	@Override
	public int o_Hash (
			final AvailObject object)
	{
		//  Answer a 32-bit long that is always the same for equal objects, but
		//  statistically different for different objects.

		return (object.rawQuad1() ^ 0x16AE2BFD) - (object.rawQuad2() ^ 0x7C453FD);
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		return DOUBLE.o();
	}



	// operations-doubles

	@Override
	public double o_ExtractDouble (
			final AvailObject object)
	{
		//  Extract a Smalltalk Double from object.

		long castAsLong = object.rawQuad1() + ((long)object.rawQuad2() << 32);
		return Double.longBitsToDouble(castAsLong);
	}





	/* Special instance accessing */
	public static AvailObject objectFromDouble(final double aDouble)
	{
		// We cannot return by reference if a new object might be constructed.
		AvailObject result = mutable().create();
		long castAsLong = Double.doubleToRawLongBits(aDouble);
		result.rawQuad1((int)castAsLong);
		result.rawQuad2((int)(castAsLong >> 32));
		return result;
	};

	public static AvailObject objectFromDoubleRecycling(final double aDouble, final AvailObject recyclable1)
	{
		AvailObject result;
		if (recyclable1.descriptor().isMutable())
		{
			result = recyclable1;
		}
		else
		{
			result = mutable().create();
		}
		long castAsLong = Double.doubleToRawLongBits(aDouble);
		result.rawQuad1((int)castAsLong);
		result.rawQuad2((int)(castAsLong >> 32));
		return result;
	};

	public static AvailObject objectFromDoubleRecyclingOr(final double aDouble, final AvailObject recyclable1, final AvailObject recyclable2)
	{
		AvailObject result;
		if (recyclable1.descriptor().isMutable())
		{
			result = recyclable1;
		}
		else
		{
			if (recyclable2.descriptor().isMutable())
			{
				result = recyclable2;
			}
			else
			{
				result = mutable().create();
			}
		}
		long castAsLong = Double.doubleToRawLongBits(aDouble);
		result.rawQuad1((int)castAsLong);
		result.rawQuad2((int)(castAsLong >> 32));
		return result;
	};

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
