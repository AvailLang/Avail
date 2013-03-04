/**
 * MapTypeDescriptor.java
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

import static com.avail.descriptor.MapTypeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * An object instance of {@code MapTypeDescriptor} is a type which maps may
 * conform to. The map type has a {@linkplain ObjectSlots#SIZE_RANGE size
 * range}, and {@linkplain ObjectSlots#KEY_TYPE key} and {@linkplain
 * ObjectSlots#VALUE_TYPE value} types. For a map to conform to a map type, it
 * must be within the indicates size range and have keys and values of the
 * specified {@linkplain TypeDescriptor types}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class MapTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
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

	@Override @AvailMethod
	A_Type o_KeyType (final AvailObject object)
	{
		return object.slot(KEY_TYPE);
	}

	@Override @AvailMethod
	A_Type o_SizeRange (final AvailObject object)
	{
		return object.slot(SIZE_RANGE);
	}

	@Override @AvailMethod
	A_Type o_ValueType (final AvailObject object)
	{
		return object.slot(VALUE_TYPE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		if (object.slot(KEY_TYPE).equals(ANY.o())
			&& object.slot(VALUE_TYPE).equals(ANY.o())
			&& object.slot(SIZE_RANGE).equals(
				IntegerRangeTypeDescriptor.wholeNumbers()))
		{
			aStream.append("map");
			return;
		}
		aStream.append('{');
		object.keyType().printOnAvoidingIndent(
			aStream, recursionList, indent + 1);
		aStream.append('→');
		object.valueType().printOnAvoidingIndent(
			aStream, recursionList, indent + 1);
		aStream.append('|');
		final A_Type sizeRange = object.slot(SIZE_RANGE);
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
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsMapType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsMapType (
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

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		// Answer a 32-bit integer that is always the same for equal objects,
		// but statistically different for different objects.
		return MapTypeDescriptor
			.computeHashForSizeRangeHashKeyTypeHashValueTypeHash(
				object.slot(SIZE_RANGE).hash(),
				object.slot(KEY_TYPE).hash(),
				object.slot(VALUE_TYPE).hash());
	}

	@Override @AvailMethod
	boolean o_IsMapType (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		// Check if object (a type) is a subtype of aType (should also be a
		// type).
		return aType.isSupertypeOfMapType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		// Map type A is a subtype of B if and only if their size ranges are
		// covariant and their key types and value types are each covariant.
		return aMapType.slot(SIZE_RANGE).isSubtypeOf(object.slot(SIZE_RANGE))
			&& aMapType.slot(KEY_TYPE).isSubtypeOf(object.slot(KEY_TYPE))
			&& aMapType.slot(VALUE_TYPE).isSubtypeOf(object.slot(VALUE_TYPE));
	}

	@Override @AvailMethod
	A_Type o_TypeIntersection (
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

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		// Answer the most general type that is still at least as specific as
		// these.
		//
		// Note that the subcomponents must be made immutable in case one of the
		// input mapTypes is mutable (and may be destroyed *recursively* by
		// post-primitive code).
		return MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			object.slot(SIZE_RANGE).typeIntersection(
				aMapType.slot(SIZE_RANGE)).makeImmutable(),
			object.slot(KEY_TYPE).typeIntersection(
				aMapType.slot(KEY_TYPE)).makeImmutable(),
			object.slot(VALUE_TYPE).typeIntersection(
				aMapType.slot(VALUE_TYPE)).makeImmutable());
	}

	@Override @AvailMethod
	A_Type o_TypeUnion (
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

	@Override @AvailMethod
	A_Type o_TypeUnionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		// Answer the most specific type that is still at least as general as
		// these.
		//
		// Note that the subcomponents must be made immutable in case one of the
		// input mapTypes is mutable (and may be destroyed *recursively* by
		// post-primitive code).
		return MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			object.slot(SIZE_RANGE).typeUnion(
				aMapType.slot(SIZE_RANGE)).makeImmutable(),
			object.slot(KEY_TYPE).typeUnion(
				aMapType.slot(KEY_TYPE)).makeImmutable(),
			object.slot(VALUE_TYPE).typeUnion(
				aMapType.slot(VALUE_TYPE)).makeImmutable());
	}

	@Override @AvailMethod
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.MAP_TYPE;
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Make the object shared, since there isn't an immutable
			// descriptor.
			return object.makeShared();
		}
		return object;
	}

	/**
	 * Compute what the map type's hash would be, given the hashes of its
	 * constituent parts.
	 *
	 * @param sizesHash
	 *        The hash of the {@linkplain IntegerRangeTypeDescriptor integer
	 *        range type} that constrains the map size.
	 * @param keyTypeHash
	 *        The hash of the key type.
	 * @param valueTypeHash
	 *        The hash of the value type.
	 * @return The hash of the resulting map type.
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
	 * Canonicalize the values for singularities, such as the size range being
	 * zero (in which case the key type and value type are reduced to
	 * bottom).
	 *
	 * @param sizeRange
	 *        An {@linkplain IntegerRangeTypeDescriptor integer range type}
	 *        specifying the permitted sizes of a map of the proposed type.
	 * @param keyType
	 *        The type of all keys of maps of the proposed type.
	 * @param valueType
	 *        The type of all values of maps of the proposed type.
	 * @return The requested map type.
	 */
	public static A_Type mapTypeForSizesKeyTypeValueType (
		final A_Type sizeRange,
		final A_Type keyType,
		final A_Type valueType)
	{
		if (sizeRange.equals(BottomTypeDescriptor.bottom()))
		{
			return BottomTypeDescriptor.bottom();
		}

		assert sizeRange.lowerBound().isFinite();
		assert IntegerDescriptor.zero().lessOrEqual(sizeRange.lowerBound());
		assert sizeRange.upperBound().isFinite() || !sizeRange.upperInclusive();

		final A_Type sizeRangeKind = sizeRange.isEnumeration()
			? sizeRange.computeSuperkind()
			: sizeRange;

		final A_Type newSizeRange;
		final A_Type newKeyType;
		final A_Type newValueType;
		if (sizeRangeKind.upperBound().equals(IntegerDescriptor.zero()))
		{
			newSizeRange = sizeRangeKind;
			newKeyType = BottomTypeDescriptor.bottom();
			newValueType = BottomTypeDescriptor.bottom();
		}
		else if (keyType.equals(BottomTypeDescriptor.bottom())
			|| valueType.equals(BottomTypeDescriptor.bottom()))
		{
			newSizeRange = IntegerRangeTypeDescriptor.singleInteger(
				IntegerDescriptor.zero());
			newKeyType = BottomTypeDescriptor.bottom();
			newValueType = BottomTypeDescriptor.bottom();
		}
		else
		{
			newSizeRange = sizeRangeKind;
			newKeyType = keyType;
			newValueType = valueType;
		}

		final A_Type result = mutable.create();
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
		super(mutability);
	}

	/** The mutable {@link MapTypeDescriptor}. */
	private static final MapTypeDescriptor mutable =
		new MapTypeDescriptor(Mutability.MUTABLE);

	@Override
	MapTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link MapTypeDescriptor}. */
	private static final MapTypeDescriptor shared =
		new MapTypeDescriptor(Mutability.SHARED);

	@Override
	MapTypeDescriptor immutable ()
	{
		// There is no immutable descriptor.
		return shared;
	}

	@Override
	MapTypeDescriptor shared ()
	{
		return shared;
	}

	/** The most general map type. */
	private static final A_Type mostGeneralType =
		mapTypeForSizesKeyTypeValueType(
			IntegerRangeTypeDescriptor.wholeNumbers(),
			ANY.o(),
			ANY.o()).makeShared();

	/**
	 * Answer the most general {@linkplain MapTypeDescriptor map type}.
	 *
	 * @return The most general map type.
	 */
	public static A_Type mostGeneralType ()
	{
		return mostGeneralType;
	}

	/**
	 * The metatype for all map types.
	 */
	private static final A_Type meta =
		InstanceMetaDescriptor.on(mostGeneralType).makeShared();

	/**
	 * Answer the metatype for all map types.
	 *
	 * @return The statically referenced metatype.
	 */
	public static A_Type meta ()
	{
		return meta;
	}
}
