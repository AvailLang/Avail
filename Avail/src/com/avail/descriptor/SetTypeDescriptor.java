/**
 * SetTypeDescriptor.java
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
import com.avail.annotations.*;

public class SetTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		SIZE_RANGE,
		CONTENT_TYPE
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ContentType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CONTENT_TYPE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_SizeRange (
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

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsSetType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsSetType (
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

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer a 32-bit integer that is always the same for equal objects, but
		//  statistically different for different objects.

		return object.sizeRange().hash() * 11 + object.contentType().hash() * 5;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return TYPE.o();
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfSetType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		//  Set type A is a subtype of B if and only if their size ranges are covariant
		//  and their content types are covariant.

		return aSetType.sizeRange().isSubtypeOf(object.sizeRange()) && aSetType.contentType().isSubtypeOf(object.contentType());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersection (
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

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return SetTypeDescriptor.setTypeForSizesContentType(
			object.sizeRange().typeIntersection(aSetType.sizeRange()),
			object.contentType().typeIntersection(aSetType.contentType()));
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
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

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return SetTypeDescriptor.setTypeForSizesContentType(
			object.sizeRange().typeUnion(aSetType.sizeRange()),
			object.contentType().typeUnion(aSetType.contentType()));
	}

	@Override @AvailMethod
	boolean o_IsSetType (
		final @NotNull AvailObject object)
	{
		return true;
	}


	/**
	 * The most general set type.
	 */
	private static AvailObject MostGeneralType;

	/**
	 * Answer the most general set type.
	 *
	 * @return The most general set type.
	 */
	public static AvailObject mostGeneralType ()
	{
		return MostGeneralType;
	}

	/**
	 * The metatype for all set types.
	 */
	private static AvailObject Meta;

	/**
	 * Answer the metatype for all set types.
	 *
	 * @return The statically referenced metatype.
	 */
	public static AvailObject meta ()
	{
		return Meta;
	}

	public static void clearWellKnownObjects ()
	{
		MostGeneralType = null;
		Meta = null;
	}

	public static void createWellKnownObjects ()
	{
		MostGeneralType = setTypeForSizesContentType(
			IntegerRangeTypeDescriptor.wholeNumbers(),
			ANY.o());
		MostGeneralType.makeImmutable();
		Meta = InstanceTypeDescriptor.on(MostGeneralType);
		Meta.makeImmutable();
	}


	/**
	 * Create a set type with the given range of sizes and content type.
	 *
	 * @param sizeRange
	 *            The allowed sizes of my instances.
	 * @param contentType
	 *            The type that constrains my instances' elements.
	 * @return
	 *            An immutable set type as specified.
	 */
	public static @NotNull AvailObject setTypeForSizesContentType (
		final @NotNull AvailObject sizeRange,
		final @NotNull AvailObject contentType)
	{
		if (sizeRange.equals(BottomTypeDescriptor.bottom()))
		{
			return BottomTypeDescriptor.bottom();
		}
		assert sizeRange.lowerBound().isFinite();
		assert IntegerDescriptor.zero().lessOrEqual(sizeRange.lowerBound());
		assert sizeRange.upperBound().isFinite() || !sizeRange.upperInclusive();

		final AvailObject sizeRangeKind = sizeRange.isEnumeration()
			? sizeRange.computeSuperkind()
			: sizeRange;

		final AvailObject newSizeRange;
		final AvailObject newContentType;
		if (sizeRangeKind.upperBound().equals(IntegerDescriptor.zero()))
		{
			newSizeRange = sizeRangeKind;
			newContentType = BottomTypeDescriptor.bottom();
		}
		else if (contentType.equals(BottomTypeDescriptor.bottom()))
		{
			if (sizeRangeKind.lowerBound().equals(IntegerDescriptor.zero()))
			{
				// sizeRange includes at least 0 and 1, but the content type is
				// bottom, so no contents exist.
				newSizeRange = IntegerRangeTypeDescriptor.singleInteger(
					IntegerDescriptor.zero());
				newContentType = BottomTypeDescriptor.bottom();
			}
			else
			{
				// sizeRange does not include 0, and bottom is not the
				// content type, so the whole type is inconsistent.  Answer
				// bottom.
				return BottomTypeDescriptor.bottom();
			}
		}
		else
		{
			newSizeRange = sizeRangeKind;
			newContentType = contentType;
		}
		final AvailObject result = mutable().create();
		result.objectSlotPut(ObjectSlots.SIZE_RANGE, newSizeRange);
		result.objectSlotPut(ObjectSlots.CONTENT_TYPE, newContentType);
		result.makeImmutable();
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
