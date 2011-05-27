/**
 * descriptor/SetTypeDescriptor.java
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
import com.avail.annotations.NotNull;

public class SetTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		SIZE_RANGE,
		CONTENT_TYPE
	}

	@Override
	public void o_ContentType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CONTENT_TYPE, value);
	}

	@Override
	public void o_SizeRange (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.SIZE_RANGE, value);
	}

	@Override
	public @NotNull AvailObject o_ContentType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CONTENT_TYPE);
	}

	@Override
	public @NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.SIZE_RANGE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append("set");
		if (!object.sizeRange().equals(IntegerRangeTypeDescriptor.wholeNumbers()))
		{
			aStream.append(" ");
			object.sizeRange().printOnAvoidingIndent(
				aStream,
				recursionList,
				(indent + 1));
		}
		aStream.append(" of ");
		object.contentType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsSetType(object);
	}

	@Override
	public boolean o_EqualsSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		//  Set types are equal iff both their sizeRange and contentType match.

		if (object.sameAddressAs(aSetType))
		{
			return true;
		}
		return object.sizeRange().equals(aSetType.sizeRange()) && object.contentType().equals(aSetType.contentType());
	}

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return SET_TYPE.o();
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer a 32-bit integer that is always the same for equal objects, but
		//  statistically different for different objects.

		return object.sizeRange().hash() * 11 + object.contentType().hash() * 5;
	}

	@Override
	public boolean o_IsHashAvailable (
		final @NotNull AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		if (!object.sizeRange().isHashAvailable())
		{
			return false;
		}
		if (!object.contentType().isHashAvailable())
		{
			return false;
		}
		return true;
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return SET_TYPE.o();
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfSetType(object);
	}

	@Override
	public boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		//  Set type A is a subtype of B if and only if their size ranges are covariant
		//  and their content types are covariant.

		return aSetType.sizeRange().isSubtypeOf(object.sizeRange()) && aSetType.contentType().isSubtypeOf(object.contentType());
	}

	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		//  Answer the most general type that is still at least as specific as these.

		if (object.isSubtypeOf(another))
		{
			return object;
		}
		if (another.isSubtypeOf(object))
		{
			return another;
		}
		return another.typeIntersectionOfSetType(object);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return SetTypeDescriptor.setTypeForSizesContentType(object.sizeRange().typeIntersection(aSetType.sizeRange()), object.contentType().typeIntersection(aSetType.contentType()));
	}

	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		//  Answer the most specific type that is still at least as general as these.

		if (object.equals(another))
		{
			return object;
		}
		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		return another.typeUnionOfSetType(object);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return SetTypeDescriptor.setTypeForSizesContentType(object.sizeRange().typeUnion(aSetType.sizeRange()), object.contentType().typeUnion(aSetType.contentType()));
	}

	@Override
	public boolean o_IsSetType (
		final @NotNull AvailObject object)
	{
		return true;
	}

	public static @NotNull AvailObject setTypeForSizesContentType (
		final @NotNull AvailObject sizeRange,
		final @NotNull AvailObject contentType)
	{
		if (sizeRange.equals(TERMINATES.o()))
		{
			return TERMINATES.o();
		}
		assert sizeRange.lowerBound().isFinite();
		assert IntegerDescriptor.zero().lessOrEqual(sizeRange.lowerBound());
		assert sizeRange.upperBound().isFinite() || !sizeRange.upperInclusive();
		AvailObject result = mutable().create();
		if (sizeRange.upperBound().equals(IntegerDescriptor.zero()))
		{
			result.sizeRange(sizeRange);
			result.contentType(TERMINATES.o());
		}
		else if (contentType.equals(TERMINATES.o()))
		{
			if (sizeRange.lowerBound().equals(IntegerDescriptor.zero()))
			{
				//  sizeRange includes at least 0 and 1, but the content type is terminates, so no contents exist.
				result.sizeRange(
					IntegerRangeTypeDescriptor.singleInteger(
						IntegerDescriptor.zero()));
				result.contentType(TERMINATES.o());
			}
			else
			{
				//  sizeRange does not include 0, and terminates is not the content type, so the whole type is inconsistent.  Answer terminates
				return TERMINATES.o();
			}
		}
		else
		{
			result.sizeRange(sizeRange);
			result.contentType(contentType);
		}
		return result;
	}

	/**
	 * Construct a new {@link SetTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected SetTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link SetTypeDescriptor}.
	 */
	private final static SetTypeDescriptor mutable = new SetTypeDescriptor(true);

	/**
	 * Answer the mutable {@link SetTypeDescriptor}.
	 *
	 * @return The mutable {@link SetTypeDescriptor}.
	 */
	public static SetTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link SetTypeDescriptor}.
	 */
	private final static SetTypeDescriptor immutable = new SetTypeDescriptor(false);

	/**
	 * Answer the immutable {@link SetTypeDescriptor}.
	 *
	 * @return The immutable {@link SetTypeDescriptor}.
	 */
	public static SetTypeDescriptor immutable ()
	{
		return immutable;
	}
}
