/*
 * MapTypeDescriptor.java
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

import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.representation.Mutability;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;

import static com.avail.descriptor.numbers.IntegerDescriptor.one;
import static com.avail.descriptor.numbers.IntegerDescriptor.zero;
import static com.avail.descriptor.types.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.types.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.singleInteger;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.types.MapTypeDescriptor.ObjectSlots.KEY_TYPE;
import static com.avail.descriptor.types.MapTypeDescriptor.ObjectSlots.SIZE_RANGE;
import static com.avail.descriptor.types.MapTypeDescriptor.ObjectSlots.VALUE_TYPE;
import static com.avail.descriptor.types.TypeDescriptor.Types.ANY;

/**
 * An object instance of {@code MapTypeDescriptor} is a type which maps may conform to. The map type has a {@linkplain ObjectSlots#SIZE_RANGE size&#32;range}, and {@linkplain ObjectSlots#KEY_TYPE key} and {@linkplain ObjectSlots#VALUE_TYPE value} types. For a map to conform to a map type, it must be within the indicates size range and have keys and values of the specified {@linkplain TypeDescriptor types}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class MapTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/**
		 * The number of elements that a map can have while conforming to this
		 * map type.
		 */
		SIZE_RANGE,

		/**
		 * The types of keys that a map can have while conforming to this map
		 * type.
		 */
		KEY_TYPE,

		/**
		 * The types of values that a map can have while conforming to this map
		 * type.
		 */
		VALUE_TYPE
	}

	@Override
	public A_Type o_KeyType (final AvailObject object)
	{
		return object.slot(KEY_TYPE);
	}

	@Override
	public A_Type o_SizeRange (final AvailObject object)
	{
		return object.slot(SIZE_RANGE);
	}

	@Override
	public A_Type o_ValueType (final AvailObject object)
	{
		return object.slot(VALUE_TYPE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		if (object.slot(KEY_TYPE).equals(ANY.o())
			&& object.slot(VALUE_TYPE).equals(ANY.o())
			&& object.slot(SIZE_RANGE).equals(wholeNumbers()))
		{
			aStream.append("map");
			return;
		}
		aStream.append('{');
		object.keyType().printOnAvoidingIndent(
			aStream, recursionMap, indent + 1);
		aStream.append('→');
		object.valueType().printOnAvoidingIndent(
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

	@Override
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsMapType(object);
	}

	@Override
	public boolean o_EqualsMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		if (object.sameAddressAs(aMapType))
		{
			return true;
		}
		return object.slot(SIZE_RANGE).equals(aMapType.sizeRange())
			&& object.slot(KEY_TYPE).equals(aMapType.keyType())
			&& object.slot(VALUE_TYPE).equals(aMapType.valueType());
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		// Answer a 32-bit integer that is always the same for equal objects,
		// but statistically different for different objects.
		return computeHashForSizeRangeHashKeyTypeHashValueTypeHash(
			object.slot(SIZE_RANGE).hash(),
			object.slot(KEY_TYPE).hash(),
			object.slot(VALUE_TYPE).hash());
	}

	@Override
	public boolean o_IsMapType (final AvailObject object)
	{
		return true;
	}

	@Override
	public boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		// Check if object (a type) is a subtype of aType (should also be a
		// type).
		return aType.isSupertypeOfMapType(object);
	}

	@Override
	public boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		// Map type A is a subtype of B if and only if their size ranges are
		// covariant and their key types and value types are each covariant.
		return aMapType.slot(SIZE_RANGE).isSubtypeOf(object.slot(SIZE_RANGE))
			&& aMapType.slot(KEY_TYPE).isSubtypeOf(object.slot(KEY_TYPE))
			&& aMapType.slot(VALUE_TYPE).isSubtypeOf(object.slot(VALUE_TYPE));
	}

	@Override
	public boolean o_IsVacuousType (final AvailObject object)
	{
		return
			!object.slot(SIZE_RANGE).lowerBound().equalsInt(0)
				&& (object.slot(KEY_TYPE).isVacuousType()
					|| object.slot(VALUE_TYPE).isVacuousType());
	}

	@Override
	public A_Type o_TypeIntersection (
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
		return another.typeIntersectionOfMapType(object);
	}

	@Override
	public A_Type o_TypeIntersectionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		// Answer the most general type that is still at least as specific as
		// these.
		//
		// Note that the subcomponents must be made immutable in case one of the
		// input mapTypes is mutable (and may be destroyed *recursively* by
		// post-primitive code).
		return mapTypeForSizesKeyTypeValueType(
			object.slot(SIZE_RANGE).typeIntersection(
				aMapType.sizeRange()).makeImmutable(),
			object.slot(KEY_TYPE).typeIntersection(
				aMapType.keyType()).makeImmutable(),
			object.slot(VALUE_TYPE).typeIntersection(
				aMapType.valueType()).makeImmutable());
	}

	@Override
	public A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
	{
		// Answer the most specific type that is still at least as general as
		// these.
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
		return another.typeUnionOfMapType(object);
	}

	@Override
	public A_Type o_TypeUnionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		// Answer the most specific type that is still at least as general as
		// these.
		//
		// Note that the subcomponents must be made immutable in case one of the
		// input mapTypes is mutable (and may be destroyed *recursively* by
		// post-primitive code).
		return mapTypeForSizesKeyTypeValueType(
			object.slot(SIZE_RANGE).typeUnion(
				aMapType.sizeRange()).makeImmutable(),
			object.slot(KEY_TYPE).typeUnion(
				aMapType.keyType()).makeImmutable(),
			object.slot(VALUE_TYPE).typeUnion(
				aMapType.valueType()).makeImmutable());
	}

	@Override
	public SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.MAP_TYPE;
	}

	@Override
	public AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Make the object shared, since there isn't an immutable
			// descriptor.
			return object.makeShared();
		}
		return object;
	}

	@Override
	public void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("map type");
		writer.write("key type");
		object.slot(KEY_TYPE).writeTo(writer);
		writer.write("value type");
		object.slot(VALUE_TYPE).writeTo(writer);
		writer.write("cardinality");
		object.slot(SIZE_RANGE).writeTo(writer);
		writer.endObject();
	}

	@Override
	public void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("map type");
		writer.write("key type");
		object.slot(KEY_TYPE).writeSummaryTo(writer);
		writer.write("value type");
		object.slot(VALUE_TYPE).writeSummaryTo(writer);
		writer.write("cardinality");
		object.slot(SIZE_RANGE).writeTo(writer);
		writer.endObject();
	}

	/**
	 * Compute what the map type's hash would be, given the hashes of its
	 * constituent parts.
	 *
	 * @param sizesHash
	 *        The hash of the {@linkplain IntegerRangeTypeDescriptor integer&#32;range&#32;type} that constrains the map size.
	 * @param keyTypeHash
	 *        The hash of the key type.
	 * @param valueTypeHash
	 *        The hash of the value type.
	 * @return
	 *
	 * The hash of the resulting map type.
	 */
	private static int computeHashForSizeRangeHashKeyTypeHashValueTypeHash (
		final int sizesHash,
		final int keyTypeHash,
		final int valueTypeHash)
	{
		return sizesHash * 3 + keyTypeHash * 5 + valueTypeHash * 13;
	}

	/**
	 * Construct a new map type with the specified permitted range of number of
	 * elements, the specified types of keys, and the specified types of values.
	 * Canonicalize the values for singularities:
	 *
	 * such as the size range being
	 * zero (in which case the key type and value type are reduced to
	 * bottom).
	 *
	 * <ul>
	 * <li>An enumeration for the size range is weakened to a kind.</li>
	 * <li>A ⊥ key type or value type implies exactly zero elements, or ⊥ as the
	 * resulting map type if zero is not an allowed size.</li>
	 * <li>At most zero elements implies ⊥ key and value types.</li>
	 * <li>A non-meta enumeration for the key type bounds the maximum size of
	 * the map (e.g., a map from booleans has at most 2 elements).</li>
	 * <li>Similarly, an integral range for the key type bounds the maximum size
	 * of the map (you can't have a 1000-element map from bytes).</li>
	 * </ul>
	 *
	 * @param sizeRange
	 *        An {@linkplain IntegerRangeTypeDescriptor integer&#32;range&#32;type} specifying the permitted sizes of a map of the proposed type.
	 * @param keyType
	 *        The type of all keys of maps of the proposed type.
	 * @param valueType
	 *        The type of all values of maps of the proposed type.
	 * @return
	 * The requested map type.
	 */
	public static A_Type mapTypeForSizesKeyTypeValueType (
		final A_Type sizeRange,
		final A_Type keyType,
		final A_Type valueType)
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
		final A_Type newKeyType;
		final A_Type newValueType;
		if (sizeRangeKind.upperBound().equalsInt(0))
		{
			newSizeRange = sizeRangeKind;
			newKeyType = bottom();
			newValueType = bottom();
		}
		else if (keyType.isBottom() || valueType.isBottom())
		{
			newSizeRange = singleInteger(zero());
			newKeyType = bottom();
			newValueType = bottom();
		}
		else
		{
			final A_Type contentRestrictedSizes;
			if (keyType.isEnumeration() && !keyType.isInstanceMeta())
			{
				// There can't ever be more entries in the map than there are
				// distinct possible keys.
				contentRestrictedSizes =
					IntegerRangeTypeDescriptor.inclusive(zero(), keyType.instanceCount());
			}
			else if (keyType.isIntegerRangeType()
				&& (keyType.lowerBound().isFinite()
					|| keyType.upperBound().isFinite()
					|| keyType.lowerBound().equals(
						keyType.upperBound())))
			{
				// We had already ruled out ⊥ for the keys (and also for the
				// values), and the latest test rules out [-∞..∞], [-∞..∞),
				// (-∞..∞], and (-∞..∞), allowing safe subtraction.
				contentRestrictedSizes = IntegerRangeTypeDescriptor.inclusive(
					zero(),
					keyType.upperBound().minusCanDestroy(
							keyType.lowerBound(), false)
						.plusCanDestroy(one(), false));
			}
			else
			{
				// Otherwise don't narrow the size range.
				contentRestrictedSizes = wholeNumbers();
			}
			newSizeRange = sizeRangeKind.typeIntersection(
				contentRestrictedSizes);
			newKeyType = keyType;
			newValueType = valueType;
		}

		final AvailObject result = mutable.create();
		result.setSlot(SIZE_RANGE, newSizeRange);
		result.setSlot(KEY_TYPE, newKeyType);
		result.setSlot(VALUE_TYPE, newValueType);
		return result;
	}

	/**
	 * Construct a new {@link MapTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private MapTypeDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.MAP_TYPE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link MapTypeDescriptor}. */
	private static final MapTypeDescriptor mutable =
		new MapTypeDescriptor(Mutability.MUTABLE);

	@Override
	public MapTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link MapTypeDescriptor}. */
	private static final MapTypeDescriptor shared =
		new MapTypeDescriptor(Mutability.SHARED);

	@Override
	public MapTypeDescriptor immutable ()
	{
		// There is no immutable descriptor.
		return shared;
	}

	@Override
	public MapTypeDescriptor shared ()
	{
		return shared;
	}

	/** The most general map type. */
	private static final A_Type mostGeneralType =
		mapTypeForSizesKeyTypeValueType(
			wholeNumbers(), ANY.o(), ANY.o()
		).makeShared();

	/**
	 * Answer the most general {@linkplain MapTypeDescriptor map type}.
	 *
	 * @return
	 * The most general map type.
	 */
	public static A_Type mostGeneralMapType ()
	{
		return mostGeneralType;
	}

	/**
	 * The metatype for all map types.
	 */
	private static final A_Type meta =
		instanceMeta(mostGeneralType).makeShared();

	/**
	 * Answer the metatype for all map types.
	 *
	 * @return
	 * The statically referenced metatype.
	 */
	public static A_Type mapMeta ()
	{
		return meta;
	}
}
