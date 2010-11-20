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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TypeDescriptor.Types;

import java.util.List;

@IntegerSlots("rawQuad1")
public class FloatDescriptor extends Descriptor
{


	// GENERATED accessors

	/**
	 * Setter for field !R!awQuad1.
	 */
	@Override
	public void ObjectRawQuad1 (
			final AvailObject object, 
			final int value)
	{
		object.integerSlotAtByteIndexPut(4, value);
	}

	/**
	 * Getter for field !R!awQuad1.
	 */
	@Override
	public int ObjectRawQuad1 (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(4);
	}



	// java printing

	@Override
	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		aStream.append(object.extractFloat());
	}



	// operations

	@Override
	public boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsFloat(object);
	}

	@Override
	public boolean ObjectEqualsFloat (
			final AvailObject object, 
			final AvailObject aFloatObject)
	{
		if (object.extractFloat() != aFloatObject.extractFloat())
		{
			return false;
		}
		object.becomeIndirectionTo(aFloatObject);
		return true;
	}

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		return Types.floatObject.object();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit long that is always the same for equal objects, but
		//  statistically different for different objects.

		return (object.rawQuad1() ^ 0x16AE2BFD);
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		return Types.floatObject.object();
	}



	// operations-floats

	@Override
	public float ObjectExtractFloat (
			final AvailObject object)
	{
		//  Extract a Smalltalk Float from object.

		int castAsInt = object.rawQuad1();
		return Float.intBitsToFloat(castAsInt);
	}





	/* Special instance accessing */
	public static AvailObject objectFromFloat(float aFloat)
	{
		AvailObject result = AvailObject.newIndexedDescriptor(0, FloatDescriptor.mutableDescriptor());
		result.rawQuad1(Float.floatToRawIntBits(aFloat));
		return result;
	};

	public static AvailObject objectFromFloatRecycling(float aFloat, AvailObject recyclable1)
	{
		AvailObject result;
		if (recyclable1.descriptor().isMutable())
		{
			result = recyclable1;
		}
		else
		{
			result = AvailObject.newIndexedDescriptor(0, FloatDescriptor.mutableDescriptor());
		}
		result.rawQuad1(Float.floatToRawIntBits(aFloat));
		return result;
	};

	public static AvailObject objectFromFloatRecyclingOr(float aFloat, AvailObject recyclable1, AvailObject recyclable2)
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
				result = AvailObject.newIndexedDescriptor(0, FloatDescriptor.mutableDescriptor());
			}
		}
		result.rawQuad1(Float.floatToRawIntBits(aFloat));
		return result;
	};

	/**
	 * Construct a new {@link FloatDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param numberOfFixedObjectSlots
	 *        The number of fixed {@linkplain AvailObject object} slots.
	 * @param numberOfFixedIntegerSlots The number of fixed integer slots.
	 * @param hasVariableObjectSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable object slots?
	 * @param hasVariableIntegerSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable integer slots?
	 */
	protected FloatDescriptor (
		final int myId,
		final boolean isMutable,
		final int numberOfFixedObjectSlots,
		final int numberOfFixedIntegerSlots,
		final boolean hasVariableObjectSlots,
		final boolean hasVariableIntegerSlots)
	{
		super(
			myId,
			isMutable,
			numberOfFixedObjectSlots,
			numberOfFixedIntegerSlots,
			hasVariableObjectSlots,
			hasVariableIntegerSlots);
	}

	public static FloatDescriptor mutableDescriptor()
	{
		return (FloatDescriptor) allDescriptors [52];
	}

	public static FloatDescriptor immutableDescriptor()
	{
		return (FloatDescriptor) allDescriptors [53];
	}
}
