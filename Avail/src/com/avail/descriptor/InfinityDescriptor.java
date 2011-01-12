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

public class InfinityDescriptor extends ExtendedNumberDescriptor
{

	/**
	 * The layout of integer slots for my instances
	 */
	public enum IntegerSlots
	{
		WHICH_ONE
	}


	// GENERATED accessors

	/**
	 * Setter for field whichOne.
	 */
	@Override
	public void o_WhichOne (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.WHICH_ONE, value);
	}

	/**
	 * Getter for field whichOne.
	 */
	@Override
	public int o_WhichOne (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.WHICH_ONE);
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object,
			final StringBuilder aStream,
			final List<AvailObject> recursionList,
			final int indent)
	{
		if (!object.isPositive())
		{
			aStream.append('-');
		}
		aStream.append('\u221E');
	}



	// operations

	@Override
	public boolean o_Equals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsInfinity(object);
	}

	@Override
	public boolean o_EqualsInfinity (
			final AvailObject object,
			final AvailObject anInfinity)
	{
		//  Compare infinities by their whichOne fields.

		return (object.whichOne() == anInfinity.whichOne());
	}

	@Override
	public boolean o_GreaterThanInteger (
			final AvailObject object,
			final AvailObject another)
	{
		return object.isPositive();
	}

	@Override
	public boolean o_GreaterThanSignedInfinity (
			final AvailObject object,
			final AvailObject another)
	{
		//  1=+inf, 2=-inf
		return (object.whichOne() < another.whichOne());
	}

	@Override
	public boolean o_IsInstanceOfSubtypeOf (
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
		if (!aType.isIntegerRangeType())
		{
			return false;
		}
		if (object.isPositive())
		{
			return (aType.upperBound().equals(object) && aType.upperInclusive());
		}
		return (aType.lowerBound().equals(object) && aType.lowerInclusive());
	}

	@Override
	public boolean o_LessThan (
			final AvailObject object,
			final AvailObject another)
	{
		return another.greaterThanSignedInfinity(object);
	}

	@Override
	public boolean o_TypeEquals (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Answer whether object's type is equal to aType (known to be a type).
		//  Since my implementation of o_CanComputeHashOfType: answers
		//  true, I'm not allowed to allocate objects to figure this out.

		if (!aType.isIntegerRangeType())
		{
			return false;
		}
		if (!aType.lowerBound().equals(object))
		{
			return false;
		}
		if (!aType.lowerInclusive())
		{
			return false;
		}
		if (!aType.upperBound().equals(object))
		{
			return false;
		}
		if (!aType.upperInclusive())
		{
			return false;
		}
		//  ...(inclusive).
		return true;
	}

	@Override
	public boolean o_CanComputeHashOfType (
			final AvailObject object)
	{
		//  Answer whether object supports the #hashOfType protocol.

		return true;
	}

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return IntegerRangeTypeDescriptor.singleInteger(object);
	}

	@Override
	public int o_Hash (
			final AvailObject object)
	{
		//  Answer the object's hash value.

		return (object.whichOne() == 1) ? 0x14B326DA : (0xBF9302D);
	}

	@Override
	public int o_HashOfType (
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

	@Override
	public boolean o_IsFinite (
			final AvailObject object)
	{
		return false;
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		//  Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}



	// operations-numbers

	@Override
	public AvailObject o_DivideCanDestroy (
			final AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
	{
		//  Double-dispatch it.

		return aNumber.divideIntoInfinityCanDestroy(object, canDestroy);
	}

	@Override
	public AvailObject o_MinusCanDestroy (
			final AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
	{
		//  Double-dispatch it.

		return aNumber.subtractFromInfinityCanDestroy(object, canDestroy);
	}

	@Override
	public AvailObject o_PlusCanDestroy (
			final AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
	{
		//  Double-dispatch it.

		return aNumber.addToInfinityCanDestroy(object, canDestroy);
	}

	@Override
	public AvailObject o_TimesCanDestroy (
			final AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
	{
		//  Double-dispatch it.

		return aNumber.multiplyByInfinityCanDestroy(object, canDestroy);
	}

	@Override
	public boolean o_IsPositive (
			final AvailObject object)
	{
		//  Double-dispatch it.

		return (object.whichOne() == 1);
	}



	// private-arithmetic

	@Override
	public AvailObject o_AddToInfinityCanDestroy (
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

	@Override
	public AvailObject o_AddToIntegerCanDestroy (
			final AvailObject object,
			final AvailObject anInteger,
			final boolean canDestroy)
	{
		return object;
	}

	@Override
	public AvailObject o_DivideIntoInfinityCanDestroy (
			final AvailObject object,
			final AvailObject anInfinity,
			final boolean canDestroy)
	{
		error("Can't divide infinities", object);
		return VoidDescriptor.voidObject();
	}

	@Override
	public AvailObject o_DivideIntoIntegerCanDestroy (
			final AvailObject object,
			final AvailObject anInteger,
			final boolean canDestroy)
	{
		return IntegerDescriptor.zero();
	}

	@Override
	public AvailObject o_MultiplyByInfinityCanDestroy (
			final AvailObject object,
			final AvailObject anInfinity,
			final boolean canDestroy)
	{
		return ((anInfinity.isPositive() == object.isPositive()) ? InfinityDescriptor.positiveInfinity() : InfinityDescriptor.negativeInfinity());
	}

	@Override
	public AvailObject o_MultiplyByIntegerCanDestroy (
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

	@Override
	public AvailObject o_SubtractFromInfinityCanDestroy (
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

	@Override
	public AvailObject o_SubtractFromIntegerCanDestroy (
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
		Positive = mutable().create();
		Positive.whichOne(1);
		//  See #positiveInfinity
		Positive.makeImmutable();
		Negative = mutable().create();
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
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected InfinityDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link InfinityDescriptor}.
	 */
	private final static InfinityDescriptor mutable = new InfinityDescriptor(true);

	/**
	 * Answer the mutable {@link InfinityDescriptor}.
	 *
	 * @return The mutable {@link InfinityDescriptor}.
	 */
	public static InfinityDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link InfinityDescriptor}.
	 */
	private final static InfinityDescriptor immutable = new InfinityDescriptor(false);

	/**
	 * Answer the immutable {@link InfinityDescriptor}.
	 *
	 * @return The immutable {@link InfinityDescriptor}.
	 */
	public static InfinityDescriptor immutable ()
	{
		return immutable;
	}
}
