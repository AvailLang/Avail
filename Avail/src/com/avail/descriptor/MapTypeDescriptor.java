/**
 * descriptor/MapTypeDescriptor.java
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
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.MapTypeDescriptor;
import com.avail.descriptor.TypeDescriptor;
import java.util.List;

public class MapTypeDescriptor extends TypeDescriptor
{

	public enum ObjectSlots
	{
		SIZE_RANGE,
		KEY_TYPE,
		VALUE_TYPE
	}


	// GENERATED accessors

	/**
	 * Setter for field keyType.
	 */
	@Override
	public void o_KeyType (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.KEY_TYPE, value);
	}

	/**
	 * Setter for field sizeRange.
	 */
	@Override
	public void o_SizeRange (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.SIZE_RANGE, value);
	}

	/**
	 * Setter for field valueType.
	 */
	@Override
	public void o_ValueType (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.VALUE_TYPE, value);
	}

	/**
	 * Getter for field keyType.
	 */
	@Override
	public AvailObject o_KeyType (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.KEY_TYPE);
	}

	/**
	 * Getter for field sizeRange.
	 */
	@Override
	public AvailObject o_SizeRange (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.SIZE_RANGE);
	}

	/**
	 * Getter for field valueType.
	 */
	@Override
	public AvailObject o_ValueType (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.VALUE_TYPE);
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object,
			final StringBuilder aStream,
			final List<AvailObject> recursionList,
			final int indent)
	{
		aStream.append("map ");
		object.sizeRange().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		aStream.append(" from ");
		object.keyType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		aStream.append(" to ");
		object.valueType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}



	// operations

	@Override
	public boolean o_Equals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsMapType(object);
	}

	@Override
	public boolean o_EqualsMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		//  Map types are equal iff their sizeRange, keyType, and valueType match.

		if (object.sameAddressAs(aMapType))
		{
			return true;
		}
		return (object.sizeRange().equals(aMapType.sizeRange()) && (object.keyType().equals(aMapType.keyType()) && object.valueType().equals(aMapType.valueType())));
	}

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.mapType.object();
	}

	@Override
	public int o_Hash (
			final AvailObject object)
	{
		//  Answer a 32-bit integer that is always the same for equal objects, but
		//  statistically different for different objects.

		return MapTypeDescriptor.computeHashForSizeRangeHashKeyTypeHashValueTypeHash(
			object.sizeRange().hash(),
			object.keyType().hash(),
			object.valueType().hash());
	}

	@Override
	public boolean o_IsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		if (!object.keyType().isHashAvailable())
		{
			return false;
		}
		if (!object.valueType().isHashAvailable())
		{
			return false;
		}
		if (!object.sizeRange().isHashAvailable())
		{
			return false;
		}
		return true;
	}

	@Override
	public boolean o_IsMapType (
			final AvailObject object)
	{
		return true;
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.mapType.object();
	}



	// operations-types

	@Override
	public boolean o_IsSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfMapType(object);
	}

	@Override
	public boolean o_IsSupertypeOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		//  Map type A is a subtype of B if and only if their size ranges are covariant
		//  and their key types and value types are each covariant.

		return (aMapType.sizeRange().isSubtypeOf(object.sizeRange()) && (aMapType.keyType().isSubtypeOf(object.keyType()) && aMapType.valueType().isSubtypeOf(object.valueType())));
	}

	@Override
	public AvailObject o_TypeIntersection (
			final AvailObject object,
			final AvailObject another)
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

	@Override
	public AvailObject o_TypeIntersectionOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
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

	@Override
	public AvailObject o_TypeUnion (
			final AvailObject object,
			final AvailObject another)
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

	@Override
	public AvailObject o_TypeUnionOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
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





	/* Hashing helper */

	static int computeHashForSizeRangeHashKeyTypeHashValueTypeHash (
			int sizesHash,
			int keyTypeHash,
			int valueTypeHash)
	{
		return ((sizesHash * 3) + (keyTypeHash * 5) + (valueTypeHash * 13));
	};

	/* Object creation */

	public static AvailObject mapTypeForSizesKeyTypeValueType (
			AvailObject sizes,
			AvailObject keyType,
			AvailObject valueType)
	{
		if (sizes.equals(Types.terminates.object()))
		{
			return Types.terminates.object();
		}
		assert(sizes.lowerBound().isFinite());
		assert(IntegerDescriptor.zero().lessOrEqual(sizes.lowerBound()));
		assert(sizes.upperBound().isFinite() || !sizes.upperInclusive());
		AvailObject result = AvailObject.newIndexedDescriptor(0, MapTypeDescriptor.mutableDescriptor());
		if (sizes.upperBound().equals(IntegerDescriptor.zero()))
		{
			result.sizeRange(sizes);
			result.keyType(Types.terminates.object());
			result.valueType(Types.terminates.object());
		}
		else if (keyType.equals(Types.terminates.object()) || valueType.equals(Types.terminates.object()))
		{
			result.sizeRange(IntegerRangeTypeDescriptor.singleInteger(IntegerDescriptor.zero()));
			result.keyType(Types.terminates.object());
			result.valueType(Types.terminates.object());
		}
		else
		{
			result.sizeRange(sizes);
			result.keyType(keyType);
			result.valueType(valueType);
		};
		return result;
	};

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
	private final static MapTypeDescriptor mutableDescriptor = new MapTypeDescriptor(true);

	/**
	 * Answer the mutable {@link MapTypeDescriptor}.
	 *
	 * @return The mutable {@link MapTypeDescriptor}.
	 */
	public static MapTypeDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link MapTypeDescriptor}.
	 */
	private final static MapTypeDescriptor immutableDescriptor = new MapTypeDescriptor(false);

	/**
	 * Answer the immutable {@link MapTypeDescriptor}.
	 *
	 * @return The immutable {@link MapTypeDescriptor}.
	 */
	public static MapTypeDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
