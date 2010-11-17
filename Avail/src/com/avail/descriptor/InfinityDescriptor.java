/**
 * descriptor/InfinityDescriptor.java
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

import com.avail.descriptor.ApproximateTypeDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.InfinityDescriptor;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.VoidDescriptor;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

@IntegerSlots("whichOne")
public class InfinityDescriptor extends ExtendedNumberDescriptor
{


	// GENERATED accessors

	void ObjectWhichOne (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(4, value);
	}

	int ObjectWhichOne (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(4);
	}



	// java printing

	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		if (! object.isPositive())
		{
			aStream.append('-');
		}
		aStream.append('\u221E');
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsInfinity(object);
	}

	boolean ObjectEqualsInfinity (
			final AvailObject object, 
			final AvailObject anInfinity)
	{
		//  Compare infinities by their whichOne fields.

		return (object.whichOne() == anInfinity.whichOne());
	}

	boolean ObjectGreaterThanInteger (
			final AvailObject object, 
			final AvailObject another)
	{
		return object.isPositive();
	}

	boolean ObjectGreaterThanSignedInfinity (
			final AvailObject object, 
			final AvailObject another)
	{
		//  1=+inf, 2=-inf
		return (object.whichOne() < another.whichOne());
	}

	boolean ObjectIsInstanceOfSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Answer whether object is an instance of a subtype of aType.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.

		if (aType.equals(Types.voidType.object()))
		{
			return true;
		}
		if (aType.equals(Types.all.object()))
		{
			return true;
		}
		if (! aType.isIntegerRangeType())
		{
			return false;
		}
		if (object.isPositive())
		{
			return (aType.upperBound().equals(object) && aType.upperInclusive());
		}
		return (aType.lowerBound().equals(object) && aType.lowerInclusive());
	}

	boolean ObjectLessThan (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.greaterThanSignedInfinity(object);
	}

	boolean ObjectTypeEquals (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Answer whether object's type is equal to aType (known to be a type).
		//  Since my implementation of ObjectCanComputeHashOfType: answers
		//  true, I'm not allowed to allocate objects to figure this out.

		if (! aType.isIntegerRangeType())
		{
			return false;
		}
		if (! aType.lowerBound().equals(object))
		{
			return false;
		}
		if (! aType.lowerInclusive())
		{
			return false;
		}
		if (! aType.upperBound().equals(object))
		{
			return false;
		}
		if (! aType.upperInclusive())
		{
			return false;
		}
		//  ...(inclusive).
		return true;
	}

	boolean ObjectCanComputeHashOfType (
			final AvailObject object)
	{
		//  Answer whether object supports the #hashOfType protocol.

		return true;
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return IntegerRangeTypeDescriptor.singleInteger(object);
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer the object's hash value.

		return ((object.whichOne() == 1) ? (0x14B326DA & HashMask) : (0xBF9302D & HashMask));
	}

	int ObjectHashOfType (
			final AvailObject object)
	{
		//  Answer my type's hash value (without creating any objects).

		final int objectHash = object.hash();
		return IntegerRangeTypeDescriptor.computeHashFromLowerBoundHashUpperBoundHashLowerInclusiveUpperInclusive(
			objectHash,
			objectHash,
			true,
			true);
	}

	boolean ObjectIsFinite (
			final AvailObject object)
	{
		return false;
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}



	// operations-numbers

	AvailObject ObjectDivideCanDestroy (
			final AvailObject object, 
			final AvailObject aNumber, 
			final boolean canDestroy)
	{
		//  Double-dispatch it.

		return aNumber.divideIntoInfinityCanDestroy(object, canDestroy);
	}

	AvailObject ObjectMinusCanDestroy (
			final AvailObject object, 
			final AvailObject aNumber, 
			final boolean canDestroy)
	{
		//  Double-dispatch it.

		return aNumber.subtractFromInfinityCanDestroy(object, canDestroy);
	}

	AvailObject ObjectPlusCanDestroy (
			final AvailObject object, 
			final AvailObject aNumber, 
			final boolean canDestroy)
	{
		//  Double-dispatch it.

		return aNumber.addToInfinityCanDestroy(object, canDestroy);
	}

	AvailObject ObjectTimesCanDestroy (
			final AvailObject object, 
			final AvailObject aNumber, 
			final boolean canDestroy)
	{
		//  Double-dispatch it.

		return aNumber.multiplyByInfinityCanDestroy(object, canDestroy);
	}

	boolean ObjectIsPositive (
			final AvailObject object)
	{
		//  Double-dispatch it.

		return (object.whichOne() == 1);
	}



	// private-arithmetic

	AvailObject ObjectAddToInfinityCanDestroy (
			final AvailObject object, 
			final AvailObject anInfinity, 
			final boolean canDestroy)
	{
		if ((anInfinity.isPositive() == object.isPositive()))
		{
			return object;
		}
		error("Can't add negative and positive infinities", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectAddToIntegerCanDestroy (
			final AvailObject object, 
			final AvailObject anInteger, 
			final boolean canDestroy)
	{
		return object;
	}

	AvailObject ObjectDivideIntoInfinityCanDestroy (
			final AvailObject object, 
			final AvailObject anInfinity, 
			final boolean canDestroy)
	{
		error("Can't divide infinities", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectDivideIntoIntegerCanDestroy (
			final AvailObject object, 
			final AvailObject anInteger, 
			final boolean canDestroy)
	{
		return IntegerDescriptor.zero();
	}

	AvailObject ObjectMultiplyByInfinityCanDestroy (
			final AvailObject object, 
			final AvailObject anInfinity, 
			final boolean canDestroy)
	{
		return ((anInfinity.isPositive() == object.isPositive()) ? InfinityDescriptor.positiveInfinity() : InfinityDescriptor.negativeInfinity());
	}

	AvailObject ObjectMultiplyByIntegerCanDestroy (
			final AvailObject object, 
			final AvailObject anInteger, 
			final boolean canDestroy)
	{
		if (anInteger.equals(IntegerDescriptor.zero()))
		{
			error("Can't multiply infinity by zero", object);
			return VoidDescriptor.voidObject();
		}
		return ((anInteger.greaterThan(IntegerDescriptor.zero()) ^ object.isPositive()) ? InfinityDescriptor.negativeInfinity() : InfinityDescriptor.positiveInfinity());
	}

	AvailObject ObjectSubtractFromInfinityCanDestroy (
			final AvailObject object, 
			final AvailObject anInfinity, 
			final boolean canDestroy)
	{
		if ((anInfinity.isPositive() ^ object.isPositive()))
		{
			return anInfinity;
		}
		error("Can't subtract infinity from same signed infinity", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectSubtractFromIntegerCanDestroy (
			final AvailObject object, 
			final AvailObject anInteger, 
			final boolean canDestroy)
	{
		return (object.isPositive() ? InfinityDescriptor.negativeInfinity() : InfinityDescriptor.positiveInfinity());
	}




	// Startup/shutdown

	static AvailObject Negative;


	static AvailObject Positive;

	static void createWellKnownObjects ()
	{
		Positive = AvailObject.newIndexedDescriptor(0, InfinityDescriptor.mutableDescriptor());
		Positive.whichOne(1);
		//  See #positiveInfinity
		Positive.makeImmutable();
		Negative = AvailObject.newIndexedDescriptor(0, InfinityDescriptor.mutableDescriptor());
		Negative.whichOne(2);
		//  See #negativeInfinity
		Negative.makeImmutable();
	}

	static void clearWellKnownObjects ()
	{
		Positive = null;
		Negative = null;
	}



	/* Value conversion... */
	public static AvailObject positiveInfinity ()
	{
		return Positive;
	};
	public static AvailObject negativeInfinity ()
	{
		return Negative;
	};

	/**
	 * Construct a new {@link InfinityDescriptor}.
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
	protected InfinityDescriptor (
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
	
	public static InfinityDescriptor mutableDescriptor()
	{
		return (InfinityDescriptor) allDescriptors [76];
	}
	
	public static InfinityDescriptor immutableDescriptor()
	{
		return (InfinityDescriptor) allDescriptors [77];
	}
}
