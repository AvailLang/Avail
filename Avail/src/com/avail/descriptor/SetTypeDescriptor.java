/**
 * SetTypeDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import static com.avail.descriptor.SetTypeDescriptor.ObjectSlots.*;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * A {@code SetTypeDescriptor} object instance is a type that some {@linkplain
 * SetDescriptor sets} may conform to. It is built up from a {@linkplain
 * ObjectSlots#SIZE_RANGE range of sizes} that the sets may be, and the
 * {@linkplain ObjectSlots#CONTENT_TYPE content type} that the set's elements
 * would have to conform to.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class SetTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * An {@linkplain IntegerRangeTypeDescriptor integer range type} which
		 * limits the sizes of {@linkplain SetDescriptor set}s that may be
		 * instances of this type.
		 */
		SIZE_RANGE,

		/**
		 * A {@linkplain TypeDescriptor type} which limits the objects which may
		 * be members of {@linkplain SetDescriptor set}s if they purport to be
		 * of this set type.
		 */
		CONTENT_TYPE
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		if (object.slot(CONTENT_TYPE).equals(ANY.o())
			&& object.slot(SIZE_RANGE).equals(
				IntegerRangeTypeDescriptor.wholeNumbers()))
		{
			aStream.append("set");
			return;
		}
		aStream.append('{');
		object.slot(CONTENT_TYPE).printOnAvoidingIndent(
			aStream, recursionList, indent + 1);
		aStream.append('|');
		final AvailObject sizeRange = object.slot(SIZE_RANGE);
		if (sizeRange.equals(IntegerRangeTypeDescriptor.wholeNumbers()))
		{
			aStream.append('}');
			return;
		}
		sizeRange.lowerBound().printOnAvoidingIndent(
			aStream, recursionList, indent + 1);
		if (!sizeRange.lowerBound().equals(sizeRange.upperBound()))
		{
			aStream.append("..");
			sizeRange.upperBound().printOnAvoidingIndent(
				aStream, recursionList, indent + 1);
		}
		aStream.append('}');
	}

	@Override @AvailMethod
	AvailObject o_ContentType (final AvailObject object)
	{
		return object.slot(CONTENT_TYPE);
	}

	@Override @AvailMethod
	AvailObject o_SizeRange (final AvailObject object)
	{
		return object.slot(SIZE_RANGE);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final AvailObject another)
	{
		return another.equalsSetType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		// Set types are equal iff both their sizeRange and contentType match.
		if (object.sameAddressAs(aSetType))
		{
			return true;
		}
		return object.slot(SIZE_RANGE).equals(aSetType.slot(SIZE_RANGE))
			&& object.slot(CONTENT_TYPE).equals(aSetType.slot(CONTENT_TYPE));
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		// Answer a 32-bit integer that is always the same for equal objects,
		// but statistically different for different objects.
		return object.sizeRange().hash() * 11 + object.contentType().hash() * 5;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final AvailObject object,
		final AvailObject aType)
	{
		// Check if object (a type) is a subtype of aType (should also be a
		// type).
		return aType.isSupertypeOfSetType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		// Set type A is a subtype of B if and only if their size ranges are
		// covariant and their content types are covariant.
		return
			aSetType.slot(SIZE_RANGE).isSubtypeOf(
				object.slot(SIZE_RANGE))
			&& aSetType.slot(CONTENT_TYPE).isSubtypeOf(
				object.slot(CONTENT_TYPE));
	}

	@Override @AvailMethod
	AvailObject o_TypeIntersection (
		final AvailObject object,
		final AvailObject another)
	{
		// Answer the most general type that is still at least as specific as
		// these.
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
	AvailObject o_TypeIntersectionOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		return SetTypeDescriptor.setTypeForSizesContentType(
			object.slot(SIZE_RANGE).typeIntersection(
				aSetType.slot(SIZE_RANGE)),
			object.slot(CONTENT_TYPE).typeIntersection(
				aSetType.slot(CONTENT_TYPE)));
	}

	@Override @AvailMethod
	AvailObject o_TypeUnion (
		final AvailObject object,
		final AvailObject another)
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
	AvailObject o_TypeUnionOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		return SetTypeDescriptor.setTypeForSizesContentType(
			object.slot(SIZE_RANGE).typeUnion(aSetType.slot(SIZE_RANGE)),
			object.slot(CONTENT_TYPE).typeUnion(aSetType.slot(CONTENT_TYPE)));
	}

	@Override @AvailMethod
	boolean o_IsSetType (final AvailObject object)
	{
		return true;
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.SET_TYPE;
	}

	@Override
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Make the object shared, since there isn't an immutable choice.
			object.makeShared();
		}
		return object;
	}

	/** The most general set type. */
	private static AvailObject mostGeneralType;

	/**
	 * Answer the most general set type.
	 *
	 * @return The most general set type.
	 */
	public static AvailObject mostGeneralType ()
	{
		return mostGeneralType;
	}

	/**
	 * The metatype for all set types.
	 */
	private static AvailObject meta;

	/**
	 * Answer the metatype for all set types.
	 *
	 * @return The statically referenced metatype.
	 */
	public static AvailObject meta ()
	{
		return meta;
	}

	/**
	 * Create objects statically well-known to the {@linkplain AvailRuntime
	 * Avail runtime}.
	 */
	public static void createWellKnownObjects ()
	{
		mostGeneralType = setTypeForSizesContentType(
			IntegerRangeTypeDescriptor.wholeNumbers(),
			ANY.o());
		mostGeneralType.makeShared();
		meta = InstanceMetaDescriptor.on(mostGeneralType);
		meta.makeShared();
	}

	/**
	 * Destroy or reset objects statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime}.
	 */
	public static void clearWellKnownObjects ()
	{
		mostGeneralType = null;
		meta = null;
	}

	/**
	 * Create a set type with the given range of sizes and content type.
	 *
	 * @param sizeRange
	 *        The allowed sizes of my instances.
	 * @param contentType
	 *        The type that constrains my instances' elements.
	 * @return An immutable set type as specified.
	 */
	public static AvailObject setTypeForSizesContentType (
		final AvailObject sizeRange,
		final AvailObject contentType)
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
		final AvailObject result = mutable.create();
		result.setSlot(ObjectSlots.SIZE_RANGE, newSizeRange);
		result.setSlot(ObjectSlots.CONTENT_TYPE, newContentType);
		result.makeShared();
		return result;
	}

	/**
	 * Construct a new {@link SetTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private SetTypeDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link SetTypeDescriptor}. */
	private static final SetTypeDescriptor mutable =
		new SetTypeDescriptor(Mutability.MUTABLE);

	@Override
	SetTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link SetTypeDescriptor}. */
	private static final SetTypeDescriptor shared =
		new SetTypeDescriptor(Mutability.SHARED);

	@Override
	SetTypeDescriptor immutable ()
	{
		// There isn't an immutable descriptor, just the shared one.
		return shared;
	}

	@Override
	SetTypeDescriptor shared ()
	{
		return shared;
	}
}
