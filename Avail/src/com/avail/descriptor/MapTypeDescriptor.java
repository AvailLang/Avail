/**
 * MapTypeDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

/**
 * An object instance of {@code MapTypeDescriptor} is a type which maps may
 * conform to.  The map type has a {@linkplain ObjectSlots#SIZE_RANGE size
 * range}, and {@linkplain ObjectSlots#KEY_TYPE key} and {@linkplain
 * ObjectSlots#VALUE_TYPE value} types.  For a map to conform to a map type, it
 * must be within the indicates size range and have keys and values of the
 * specified {@linkplain TypeDescriptor types}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class MapTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
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
	@NotNull AvailObject o_KeyType (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.KEY_TYPE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.SIZE_RANGE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ValueType (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.VALUE_TYPE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		if (object.keyType().equals(ANY.o())
			&& object.valueType().equals(ANY.o())
			&& object.sizeRange().equals(
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
		final AvailObject sizeRange = object.sizeRange();
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
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsMapType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		//  Map types are equal iff their sizeRange, keyType, and valueType match.

		if (object.sameAddressAs(aMapType))
		{
			return true;
		}
		return object.sizeRange().equals(aMapType.sizeRange()) && object.keyType().equals(aMapType.keyType()) && object.valueType().equals(aMapType.valueType());
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer a 32-bit integer that is always the same for equal objects, but
		//  statistically different for different objects.

		return MapTypeDescriptor.computeHashForSizeRangeHashKeyTypeHashValueTypeHash(
			object.sizeRange().hash(),
			object.keyType().hash(),
			object.valueType().hash());
	}

	@Override @AvailMethod
	boolean o_IsMapType (
		final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		// Check if object (a type) is a subtype of aType (should also be a
		// type).
		return aType.isSupertypeOfMapType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		//  Map type A is a subtype of B if and only if their size ranges are covariant
		//  and their key types and value types are each covariant.

		return aMapType.sizeRange().isSubtypeOf(object.sizeRange()) && aMapType.keyType().isSubtypeOf(object.keyType()) && aMapType.valueType().isSubtypeOf(object.valueType());
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
		return another.typeIntersectionOfMapType(object);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		//  Answer the most general type that is still at least as specific as these.
		//
		//  Note that the subcomponents must be made immutable in case one of the
		//  input mapTypes is mutable (and may be destroyed *recursively* by
		//  post-primitive code).

		return MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			object.sizeRange().typeIntersection(aMapType.sizeRange()).makeImmutable(),
			object.keyType().typeIntersection(aMapType.keyType()).makeImmutable(),
			object.valueType().typeIntersection(aMapType.valueType()).makeImmutable());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnion (
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
		return another.typeUnionOfMapType(object);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		//  Answer the most specific type that is still at least as general as these.
		//
		//  Note that the subcomponents must be made immutable in case one of the
		//  input mapTypes is mutable (and may be destroyed *recursively* by
		//  post-primitive code).

		return MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			object.sizeRange().typeUnion(aMapType.sizeRange()).makeImmutable(),
			object.keyType().typeUnion(aMapType.keyType()).makeImmutable(),
			object.valueType().typeUnion(aMapType.valueType()).makeImmutable());
	}

	/**
	 * Compute what the map type's hash would be, given the hashes of its
	 * constituent parts.
	 *
	 * @param sizesHash
	 *            The hash of the {@linkplain IntegerRangeTypeDescriptor integer
	 *            range type} that constrains the map size.
	 * @param keyTypeHash
	 *            The hash of the key type.
	 * @param valueTypeHash
	 *            The hash of the value type.
	 * @return
	 *            The hash of the resulting map type.
	 */
	private static int computeHashForSizeRangeHashKeyTypeHashValueTypeHash (
		final int sizesHash,
		final int keyTypeHash,
		final int valueTypeHash)
	{
		return sizesHash * 3 + keyTypeHash * 5 + valueTypeHash * 13;
	}


	/**
	 * The most general map type.
	 */
	private static AvailObject mostGeneralType;

	/**
	 * Answer the most general {@linkplain MapTypeDescriptor map type}.
	 *
	 * @return The most general map type.
	 */
	public static AvailObject mostGeneralType ()
	{
		return mostGeneralType;
	}

	/**
	 * The metatype for all map types.
	 */
	private static AvailObject meta;

	/**
	 * Answer the metatype for all map types.
	 *
	 * @return The statically referenced metatype.
	 */
	public static AvailObject meta ()
	{
		return meta;
	}

	public static void clearWellKnownObjects ()
	{
		mostGeneralType = null;
		meta = null;
	}

	public static void createWellKnownObjects ()
	{
		mostGeneralType = mapTypeForSizesKeyTypeValueType(
			IntegerRangeTypeDescriptor.wholeNumbers(),
			ANY.o(),
			ANY.o());
		mostGeneralType.makeImmutable();
		meta = InstanceTypeDescriptor.on(mostGeneralType);
		meta.makeImmutable();
	}


	/**
	 * Construct a new map type with the specified permitted range of number of
	 * elements, the specified types of keys, and the specified types of values.
	 * Canonicalize the values for singularities, such as the size range being
	 * zero (in which case the key type and value type are reduced to
	 * bottom).
	 *
	 * @param sizeRange
	 *            An {@linkplain IntegerRangeTypeDescriptor integer range type}
	 *            specifying the permitted sizes of a map of the proposed type.
	 * @param keyType
	 *            The type of all keys of maps of the proposed type.
	 * @param valueType
	 *            The type of all values of maps of the proposed type.
	 * @return The requested map type.
	 */
	public static AvailObject mapTypeForSizesKeyTypeValueType (
		final @NotNull AvailObject sizeRange,
		final @NotNull AvailObject keyType,
		final @NotNull AvailObject valueType)
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
		final AvailObject newKeyType;
		final AvailObject newValueType;
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

		final AvailObject result = mutable().create();
		result.setSlot(ObjectSlots.SIZE_RANGE, newSizeRange);
		result.setSlot(ObjectSlots.KEY_TYPE, newKeyType);
		result.setSlot(ObjectSlots.VALUE_TYPE, newValueType);
		return result;
	}

	/**
	 * Construct a new {@link MapTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected MapTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link MapTypeDescriptor}.
	 */
	private static final MapTypeDescriptor mutable = new MapTypeDescriptor(true);

	/**
	 * Answer the mutable {@link MapTypeDescriptor}.
	 *
	 * @return The mutable {@link MapTypeDescriptor}.
	 */
	public static MapTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link MapTypeDescriptor}.
	 */
	private static final MapTypeDescriptor immutable = new MapTypeDescriptor(false);

	/**
	 * Answer the immutable {@link MapTypeDescriptor}.
	 *
	 * @return The immutable {@link MapTypeDescriptor}.
	 */
	public static MapTypeDescriptor immutable ()
	{
		return immutable;
	}
}
