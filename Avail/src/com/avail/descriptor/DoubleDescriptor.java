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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TypeDescriptor.Types;

import java.lang.Double;
import java.util.List;

@IntegerSlots({
	"rawQuad1",
	"rawQuad2"
})
public class DoubleDescriptor extends Descriptor
{


	// GENERATED accessors

	/**
	 * Setter for field rawQuad1.
	 */
	@Override
	public void ObjectRawQuad1 (
			final AvailObject object,
			final int value)
	{
		object.integerSlotAtByteIndexPut(4, value);
	}

	/**
	 * Setter for field rawQuad2.
	 */
	@Override
	public void ObjectRawQuad2 (
			final AvailObject object,
			final int value)
	{
		object.integerSlotAtByteIndexPut(8, value);
	}

	/**
	 * Getter for field rawQuad1.
	 */
	@Override
	public int ObjectRawQuad1 (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(4);
	}

	/**
	 * Getter for field rawQuad2.
	 */
	@Override
	public int ObjectRawQuad2 (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(8);
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
	public boolean ObjectEquals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsDouble(object);
	}

	@Override
	public boolean ObjectEqualsDouble (
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
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		return Types.doubleObject.object();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit long that is always the same for equal objects, but
		//  statistically different for different objects.

		return ((object.rawQuad1() ^ 0x16AE2BFD) - (object.rawQuad2() ^ 0x7C453FD));
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		return Types.doubleObject.object();
	}



	// operations-doubles

	@Override
	public double ObjectExtractDouble (
			final AvailObject object)
	{
		//  Extract a Smalltalk Double from object.

		long castAsLong = object.rawQuad1() + (((long)object.rawQuad2()) << 32);
		return Double.longBitsToDouble(castAsLong);
	}





	/* Special instance accessing */
	public static AvailObject objectFromDouble(double aDouble)
	{
		// We cannot return by reference if a new object might be constructed.
		AvailObject result = AvailObject.newIndexedDescriptor(0, DoubleDescriptor.mutableDescriptor());
		long castAsLong = Double.doubleToRawLongBits(aDouble);
		result.rawQuad1((int)castAsLong);
		result.rawQuad2((int)(castAsLong >> 32));
		return result;
	};

	public static AvailObject objectFromDoubleRecycling(double aDouble, AvailObject recyclable1)
	{
		AvailObject result;
		if (recyclable1.descriptor().isMutable())
		{
			result = recyclable1;
		}
		else
		{
			result = AvailObject.newIndexedDescriptor(0, DoubleDescriptor.mutableDescriptor());
		}
		long castAsLong = Double.doubleToRawLongBits(aDouble);
		result.rawQuad1((int)castAsLong);
		result.rawQuad2((int)(castAsLong >> 32));
		return result;
	};

	public static AvailObject objectFromDoubleRecyclingOr(double aDouble, AvailObject recyclable1, AvailObject recyclable2)
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
				result = AvailObject.newIndexedDescriptor(0, DoubleDescriptor.mutableDescriptor());
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
	private final static DoubleDescriptor mutableDescriptor = new DoubleDescriptor(true);

	/**
	 * Answer the mutable {@link DoubleDescriptor}.
	 *
	 * @return The mutable {@link DoubleDescriptor}.
	 */
	public static DoubleDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link DoubleDescriptor}.
	 */
	private final static DoubleDescriptor immutableDescriptor = new DoubleDescriptor(false);

	/**
	 * Answer the immutable {@link DoubleDescriptor}.
	 *
	 * @return The immutable {@link DoubleDescriptor}.
	 */
	public static DoubleDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
