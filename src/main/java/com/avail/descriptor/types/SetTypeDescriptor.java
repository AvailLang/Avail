/*
 * SetTypeDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.descriptor.types;

import com.avail.annotations.AvailMethod;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.sets.SetDescriptor;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;

import static com.avail.descriptor.numbers.IntegerDescriptor.one;
import static com.avail.descriptor.numbers.IntegerDescriptor.zero;
import static com.avail.descriptor.types.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.types.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.singleInteger;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.types.SetTypeDescriptor.ObjectSlots.CONTENT_TYPE;
import static com.avail.descriptor.types.SetTypeDescriptor.ObjectSlots.SIZE_RANGE;
import static com.avail.descriptor.types.TypeDescriptor.Types.ANY;

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
	public enum ObjectSlots implements ObjectSlotsEnumJava
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
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		if (object.slot(CONTENT_TYPE).equals(ANY.o())
			&& object.slot(SIZE_RANGE).equals(wholeNumbers()))
		{
			aStream.append("set");
			return;
		}
		aStream.append('{');
		object.slot(CONTENT_TYPE).printOnAvoidingIndent(
			aStream, recursionMap, indent + 1);
		aStream.append('|');
		final A_Type sizeRange = object.slot(SIZE_RANGE);
		if (sizeRange.equals(wholeNumbers()))
		{
			aStream.append('}');
			return;
		}
		sizeRange.lowerBound().printOnAvoidingIndent(
			aStream, recursionMap, indent + 1);
		if (!sizeRange.lowerBound().equals(sizeRange.upperBound()))
		{
			aStream.append("..");
			sizeRange.upperBound().printOnAvoidingIndent(
				aStream, recursionMap, indent + 1);
		}
		aStream.append('}');
	}

	@Override @AvailMethod
	protected A_Type o_ContentType (final AvailObject object)
	{
		return object.slot(CONTENT_TYPE);
	}

	@Override @AvailMethod
	protected A_Type o_SizeRange (final AvailObject object)
	{
		return object.slot(SIZE_RANGE);
	}

	@Override @AvailMethod
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsSetType(object);
	}

	@Override @AvailMethod
	protected boolean o_EqualsSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		// Set types are equal iff both their sizeRange and contentType match.
		if (object.sameAddressAs(aSetType))
		{
			return true;
		}
		return object.slot(SIZE_RANGE).equals(aSetType.sizeRange())
			&& object.slot(CONTENT_TYPE).equals(aSetType.contentType());
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		// Answer a 32-bit integer that is always the same for equal objects,
		// but statistically different for different objects.
		return object.sizeRange().hash() * 11 + object.contentType().hash() * 5;
	}

	@Override @AvailMethod
	protected boolean o_IsSubtypeOf (
		final AvailObject object,
		final A_Type aType)
	{
		// Check if object (a type) is a subtype of aType (should also be a
		// type).
		return aType.isSupertypeOfSetType(object);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfSetType (
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

	@Override
	protected boolean o_IsVacuousType (final AvailObject object)
	{
		return
			!object.slot(SIZE_RANGE).lowerBound().equalsInt(0)
				&& object.slot(CONTENT_TYPE).isVacuousType();
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
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
	protected A_Type o_TypeIntersectionOfSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		return setTypeForSizesContentType(
			object.slot(SIZE_RANGE).typeIntersection(aSetType.sizeRange()),
			object.slot(CONTENT_TYPE).typeIntersection(aSetType.contentType()));
	}

	@Override @AvailMethod
	protected A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
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
	protected A_Type o_TypeUnionOfSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		return setTypeForSizesContentType(
			object.slot(SIZE_RANGE).typeUnion(aSetType.sizeRange()),
			object.slot(CONTENT_TYPE).typeUnion(aSetType.contentType()));
	}

	@Override @AvailMethod
	protected boolean o_IsSetType (final AvailObject object)
	{
		return true;
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.SET_TYPE;
	}

	@Override
	protected AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Make the object shared, since there isn't an immutable choice.
			object.makeShared();
		}
		return object;
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("set type");
		writer.write("content type");
		object.slot(CONTENT_TYPE).writeTo(writer);
		writer.write("cardinality");
		object.slot(SIZE_RANGE).writeTo(writer);
		writer.endObject();
	}

	@Override
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("set type");
		writer.write("content type");
		object.slot(CONTENT_TYPE).writeSummaryTo(writer);
		writer.write("cardinality");
		object.slot(SIZE_RANGE).writeTo(writer);
		writer.endObject();
	}

	/**
	 * Create a set type with the given range of sizes and content type.
	 * Normalize it certain ways:
	 *
	 * <ul>
	 * <li>An enumeration for the size range is weakened to a kind.</li>
	 * <li>A ⊥ content type implies exactly zero elements, or ⊥ as the resulting
	 * set type if zero is not an allowed size.</li>
	 * <li>At most zero elements implies a ⊥ content type.</li>
	 * <li>A non-meta enumeration for the content bounds the maximum size of the
	 * set (e.g., a set of booleans has at most 2 elements).</li>
	 * <li>Similarly, an integral range for the content type bounds the maximum
	 * size of the set (you can't have a 1000-element set of bytes).</li>
	 * </ul>
	 *
	 * @param sizeRange
	 *        The allowed sizes of my instances.
	 * @param contentType
	 *        The type that constrains my instances' elements.
	 * @return An immutable set type as specified.
	 */
	public static A_Type setTypeForSizesContentType (
		final A_Type sizeRange,
		final A_Type contentType)
	{
		if (sizeRange.isBottom())
		{
			return bottom();
		}
		assert sizeRange.lowerBound().isFinite();
		assert zero().lessOrEqual(sizeRange.lowerBound());
		assert sizeRange.upperBound().isFinite() || !sizeRange.upperInclusive();

		final A_Type sizeRangeKind = sizeRange.isEnumeration()
			? sizeRange.computeSuperkind()
			: sizeRange;

		final A_Type newSizeRange;
		final A_Type newContentType;
		if (sizeRangeKind.upperBound().equalsInt(0))
		{
			newSizeRange = sizeRangeKind;
			newContentType = bottom();
		}
		else if (contentType.isBottom())
		{
			if (sizeRangeKind.lowerBound().equalsInt(0))
			{
				// sizeRange includes at least 0 and 1, but the content type is
				// bottom, so no contents exist.
				newSizeRange = singleInteger(zero());
				newContentType = bottom();
			}
			else
			{
				// sizeRange does not include 0, and bottom is not the
				// content type, so the whole type is inconsistent.  Answer
				// bottom.
				return bottom();
			}
		}
		else
		{
			final A_Type contentRestrictedSizes;
			if (contentType.isEnumeration() && !contentType.isInstanceMeta())
			{
				// There can't ever be more elements in the set than there are
				// distinct possible values.
				contentRestrictedSizes = IntegerRangeTypeDescriptor.inclusive(
					zero(), contentType.instanceCount());
			}
			else if (contentType.isIntegerRangeType()
				&& (contentType.lowerBound().isFinite()
					|| contentType.upperBound().isFinite()
					|| contentType.lowerBound().equals(
						contentType.upperBound())))
			{
				// We had already ruled out ⊥, and the latest test rules out
				// [-∞..∞], [-∞..∞), (-∞..∞], and (-∞..∞), allowing safe
				// subtraction.
				contentRestrictedSizes = IntegerRangeTypeDescriptor.inclusive(
					zero(),
					contentType.upperBound().minusCanDestroy(
							contentType.lowerBound(), false)
						.plusCanDestroy(one(), false));
			}
			else
			{
				// Otherwise don't narrow the size range.
				contentRestrictedSizes = wholeNumbers();
			}
			newSizeRange = sizeRangeKind.typeIntersection(
				contentRestrictedSizes);
			newContentType = contentType;
		}
		final AvailObject result = mutable.create();
		result.setSlot(SIZE_RANGE, newSizeRange);
		result.setSlot(CONTENT_TYPE, newContentType);
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
		super(mutability, TypeTag.SET_TYPE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link SetTypeDescriptor}. */
	private static final SetTypeDescriptor mutable =
		new SetTypeDescriptor(Mutability.MUTABLE);

	@Override
	public SetTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link SetTypeDescriptor}. */
	private static final SetTypeDescriptor shared =
		new SetTypeDescriptor(Mutability.SHARED);

	@Override
	public SetTypeDescriptor immutable ()
	{
		// There isn't an immutable descriptor, just the shared one.
		return shared;
	}

	@Override
	public SetTypeDescriptor shared ()
	{
		return shared;
	}

	/** The most general set type. */
	private static final A_Type mostGeneralType =
		setTypeForSizesContentType(wholeNumbers(), ANY.o()).makeShared();

	/**
	 * Answer the most general set type.
	 *
	 * @return The most general set type.
	 */
	public static A_Type mostGeneralSetType ()
	{
		return mostGeneralType;
	}

	/**
	 * The metatype for all set types.
	 */
	private static final A_Type meta = instanceMeta(mostGeneralType);

	/**
	 * Answer the metatype for all set types.
	 *
	 * @return The statically referenced metatype.
	 */
	public static A_Type setMeta ()
	{
		return meta;
	}
}
