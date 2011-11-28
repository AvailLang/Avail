/**
 * descriptor/ConcatenatedTupleTypeDescriptor.java
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
import static java.lang.Math.*;
import com.avail.annotations.NotNull;

public class ConcatenatedTupleTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		FIRST_TUPLE_TYPE,
		SECOND_TUPLE_TYPE
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsTupleType(object);
	}

	@Override
	public boolean o_EqualsTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		//  Tuple types are equal iff their sizeRange, typeTuple, and defaultType match.

		if (object.sameAddressAs(aTupleType))
		{
			return true;
		}
		if (!object.sizeRange().equals(aTupleType.sizeRange()))
		{
			return false;
		}
		if (!object.defaultType().equals(aTupleType.defaultType()))
		{
			return false;
		}
		return object.typeTuple().equals(aTupleType.typeTuple());
	}

	@Override
	public boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anotherObject)
	{
		//  Given two objects that are known to be equal, is the first one in a better form (more
		//  compact, more efficient, older generation) than the second one?

		//  I'm not a very efficient representation.
		return false;
	}

	@Override
	public boolean o_IsBetterRepresentationThanTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		//  Given two objects that are known to be equal, the second of which is in the form of
		//  a tuple type, is the first one in a better form than the second one?

		//  I'm not a very efficient alternative representation of a tuple type.
		return false;
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer a 32-bit long that is always the same for equal objects, but
		//  statistically different for different objects.  This requires an object creation, so
		//  don't call it from the garbage collector.

		becomeRealTupleType(object);
		return object.hash();
	}

	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return TYPE.o();
	}

	@Override
	public @NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		// Answer what type the given index would have in an object instance of
		// me. Answer bottom if the index is definitely out of bounds.

		if (index <= 0)
		{
			return BottomTypeDescriptor.bottom();
		}

		final AvailObject firstUpper =
			object.objectSlot(ObjectSlots.FIRST_TUPLE_TYPE).sizeRange().upperBound();
		final AvailObject secondUpper =
			object.objectSlot(ObjectSlots.SECOND_TUPLE_TYPE).sizeRange().upperBound();
		final AvailObject totalUpper = firstUpper.noFailPlusCanDestroy(
			secondUpper, false);
		if (totalUpper.isFinite())
		{
			final AvailObject indexObject = IntegerDescriptor.fromInt(index);
			if (indexObject.greaterThan(totalUpper))
			{
				return BottomTypeDescriptor.bottom();
			}
		}
		final AvailObject firstLower =
			object.objectSlot(ObjectSlots.FIRST_TUPLE_TYPE).sizeRange().lowerBound();
		if (index <= firstLower.extractInt())
		{
			return object.objectSlot(ObjectSlots.FIRST_TUPLE_TYPE).typeAtIndex(index);
		}
		// Besides possibly being at a fixed offset within the firstTupleType,
		// the index might represent a range of possible indices of the
		// secondTupleType, depending on the spread between the first tuple
		// type's lower and upper bounds. Compute the union of these types.
		final AvailObject unionType =
			object.objectSlot(ObjectSlots.FIRST_TUPLE_TYPE).typeAtIndex(index);
		int startIndex;
		if (firstUpper.isFinite())
		{
			startIndex = max((index - firstUpper.extractInt()), 1);
		}
		else
		{
			startIndex = 1;
		}
		final int endIndex = index - firstLower.extractInt();
		assert endIndex >= startIndex;
		return unionType.typeUnion(
			object.objectSlot(ObjectSlots.SECOND_TUPLE_TYPE).unionOfTypesAtThrough(
				startIndex, endIndex));
	}

	@Override
	public @NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		// Answer the union of the types that object's instances could have in
		// the given range of indices. Out-of-range indices are treated as
		// bottom, which don't affect the union (unless all indices are out
		// of range).

		assert startIndex <= endIndex;
		if (startIndex == endIndex)
		{
			return object.typeAtIndex(startIndex);
		}
		if (endIndex <= 0)
		{
			return BottomTypeDescriptor.bottom();
		}
		final AvailObject firstUpper =
			object.objectSlot(ObjectSlots.FIRST_TUPLE_TYPE)
				.sizeRange().upperBound();
		final AvailObject secondUpper =
			object.objectSlot(ObjectSlots.SECOND_TUPLE_TYPE)
				.sizeRange().upperBound();
		final AvailObject totalUpper = firstUpper.noFailPlusCanDestroy(
			secondUpper, false);
		if (totalUpper.isFinite())
		{
			if (startIndex > totalUpper.extractInt())
			{
				return BottomTypeDescriptor.bottom();
			}
		}
		AvailObject unionType =
			object.objectSlot(ObjectSlots.FIRST_TUPLE_TYPE)
				.unionOfTypesAtThrough(startIndex, endIndex);
		final int startInSecond = startIndex - firstUpper.extractInt();
		final int endInSecond = endIndex
			- object.objectSlot(ObjectSlots.FIRST_TUPLE_TYPE)
				.sizeRange().lowerBound().extractInt();
		unionType = unionType.typeUnion(
			object.objectSlot(ObjectSlots.SECOND_TUPLE_TYPE)
				.unionOfTypesAtThrough(startInSecond, endInSecond));
		return unionType;
	}

	/**
	 * Expand me into an actual TupleTypeDescriptor, converting my storage into
	 * an indirection object to the actual tupleType.  Answer void.
	 *
	 * @param object
	 *            The {@link ConcatenatedTupleTypeDescriptor concatenated tuple
	 *            type} to transform
	 */
	private void becomeRealTupleType (
		final @NotNull AvailObject object)
	{
		final AvailObject part1 = object.objectSlot(
			ObjectSlots.FIRST_TUPLE_TYPE);
		final AvailObject size1 = part1.sizeRange().upperBound();
		int limit1;
		if (size1.isFinite())
		{
			limit1 = size1.extractInt();
		}
		else
		{
			limit1 = max(
				part1.typeTuple().tupleSize() + 1,
				part1.sizeRange().lowerBound().extractInt());
		}
		final AvailObject part2 = object.objectSlot(
			ObjectSlots.SECOND_TUPLE_TYPE);
		final AvailObject size2 = part2.sizeRange().upperBound();
		int limit2;
		if (size2.isFinite())
		{
			limit2 = size2.extractInt();
		}
		else
		{
			limit2 = part2.typeTuple().tupleSize() + 1;
		}
		final int total = limit1 + limit2;
		final AvailObject typeTuple =
			ObjectTupleDescriptor.mutable().create(total);
//		AvailObject.lock(typeTuple);
		//  Make it pointer-safe first.
		for (int i = 1; i <= total; i++)
		{
			typeTuple.tupleAtPut(i, NullDescriptor.nullObject());
		}
		final int section1 = min(
			part1.sizeRange().lowerBound().extractInt(),
			limit1);
		for (int i = 1; i <= section1; i++)
		{
			typeTuple.tupleAtPut(i, part1.typeAtIndex(i));
		}
		for (int i = section1 + 1; i <= total; i++)
		{
			typeTuple.tupleAtPut(i, object.typeAtIndex(i));
		}
		final AvailObject newObject =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				object.sizeRange(),
				typeTuple,
				object.defaultType());
//		AvailObject.unlock(typeTuple);
		object.becomeIndirectionTo(newObject);
	}

	@Override
	public @NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object)
	{
		//  Answer the type that my last element must have, if any.  Do not call
		//  this from within a garbage collection, as it may need to allocate space
		//  for computing a type union.

		final AvailObject a = object.objectSlot(ObjectSlots.FIRST_TUPLE_TYPE);
		final AvailObject b = object.objectSlot(ObjectSlots.SECOND_TUPLE_TYPE);
		final AvailObject bRange = b.sizeRange();
		if (bRange.upperBound().equals(IntegerDescriptor.zero()))
		{
			return a.defaultType();
		}
		if (a.sizeRange().upperBound().isFinite())
		{
			return b.defaultType();
		}
		int highIndexInB;
		if (bRange.upperBound().isFinite())
		{
			highIndexInB = bRange.upperBound().extractInt();
		}
		else
		{
			highIndexInB = b.typeTuple().tupleSize() + 1;
		}
		final AvailObject unionType = a.defaultType().typeUnion(
			b.unionOfTypesAtThrough(1, highIndexInB));
		return unionType;
	}

	@Override
	public @NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object)
	{
		// Answer what range of tuple sizes my instances could be. Note that
		// this can not be asked during a garbage collection because it
		// allocates space for its answer.

		final AvailObject a = object.objectSlot(ObjectSlots.FIRST_TUPLE_TYPE).sizeRange();
		final AvailObject b = object.objectSlot(ObjectSlots.SECOND_TUPLE_TYPE).sizeRange();
		final AvailObject upper = a.upperBound().noFailPlusCanDestroy(
			b.upperBound(), false);
		return IntegerRangeTypeDescriptor.create(
			a.lowerBound().noFailPlusCanDestroy(b.lowerBound(), false),
			true,
			upper,
			upper.isFinite());
	}

	@Override
	public @NotNull AvailObject o_TypeTuple (
		final @NotNull AvailObject object)
	{
		//  Since this is really tricky, just compute the TupleTypeDescriptor that I am
		//  shorthand for.  Answer that tupleType's typeTuple.  This is the leading types
		//  of the tupleType, up to but not including where they all have the same type.
		//  Don't run this from within a garbage collection, as it allocates objects.

		becomeRealTupleType(object);
		return object.typeTuple();
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfTupleType(object);
	}

	@Override
	public boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		//  Tuple type A is a supertype of tuple type B iff all the *possible
		//  instances* of B would also be instances of A.  Types indistinguishable
		//  under these conditions are considered the same type.

		if (object.equals(aTupleType))
		{
			return true;
		}
		if (!aTupleType.sizeRange().isSubtypeOf(object.sizeRange()))
		{
			return false;
		}
		if (!aTupleType.defaultType().isSubtypeOf(object.defaultType()))
		{
			return false;
		}
		final AvailObject subTuple = aTupleType.typeTuple();
		final AvailObject superTuple = object.typeTuple();
		for (
			int i = 1, end = max(subTuple.tupleSize(), superTuple.tupleSize());
			i <= end;
			i++)
		{
			AvailObject subType;
			if (i <= subTuple.tupleSize())
			{
				subType = subTuple.tupleAt(i);
			}
			else
			{
				subType = aTupleType.defaultType();
			}
			AvailObject superType;
			if (i <= superTuple.tupleSize())
			{
				superType = superTuple.tupleAt(i);
			}
			else
			{
				superType = object.defaultType();
			}
			if (!subType.isSubtypeOf(superType))
			{
				return false;
			}
		}
		return true;
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
		return another.typeIntersectionOfTupleType(object);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		//  Answer the most general type that is still at least as specific as these.

		final AvailObject newSizesObject = object.sizeRange().typeIntersection(aTupleType.sizeRange());
		final AvailObject lead1 = object.typeTuple();
		final AvailObject lead2 = aTupleType.typeTuple();
		AvailObject newLeading;
		if (lead1.tupleSize() > lead2.tupleSize())
		{
			newLeading = lead1;
		}
		else
		{
			newLeading = lead2;
		}
		newLeading.makeImmutable();
		//  Ensure first write attempt will force copying.
		final int newLeadingSize = newLeading.tupleSize();
		for (int i = 1; i <= newLeadingSize; i++)
		{
			final AvailObject intersectionObject = object.typeAtIndex(i).typeIntersection(aTupleType.typeAtIndex(i));
			if (intersectionObject.equals(BottomTypeDescriptor.bottom()))
			{
				return BottomTypeDescriptor.bottom();
			}
			newLeading = newLeading.tupleAtPuttingCanDestroy(
				i,
				intersectionObject,
				true);
		}
		//  Make sure entries in newLeading are immutable, as typeIntersection: can answer one
		//  of its arguments.
		newLeading.makeSubobjectsImmutable();
		final AvailObject newDefault = object.typeAtIndex(newLeadingSize + 1).typeIntersection(aTupleType.typeAtIndex(newLeadingSize + 1));
		//  safety until all primitives are destructive
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			newSizesObject,
			newLeading,
			newDefault.makeImmutable());
	}

	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		return another.typeUnionOfTupleType(object);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		final AvailObject newSizesObject = object.sizeRange().typeUnion(
			aTupleType.sizeRange());
		final AvailObject lead1 = object.typeTuple();
		final AvailObject lead2 = aTupleType.typeTuple();
		AvailObject newLeading;
		if (lead1.tupleSize() > lead2.tupleSize())
		{
			newLeading = lead1;
		}
		else
		{
			newLeading = lead2;
		}
		newLeading.makeImmutable();
		//  Ensure first write attempt will force copying.
		final int newLeadingSize = newLeading.tupleSize();
		for (int i = 1; i <= newLeadingSize; i++)
		{
			final AvailObject unionObject = object.typeAtIndex(i).typeUnion(
				aTupleType.typeAtIndex(i));
			newLeading = newLeading.tupleAtPuttingCanDestroy(
				i,
				unionObject,
				true);
		}
		// Make sure entries in newLeading are immutable, as typeUnion can
		// answer one of its arguments.
		newLeading.makeSubobjectsImmutable();
		final AvailObject newDefault =
			object.typeAtIndex(newLeadingSize + 1).typeUnion(
				aTupleType.typeAtIndex(newLeadingSize + 1));
		//  safety until all primitives are destructive
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			newSizesObject,
			newLeading,
			newDefault.makeImmutable());
	}

	@Override
	public boolean o_IsTupleType (
		final @NotNull AvailObject object)
	{
		//  I am a tupleType, so answer true.

		return true;
	}

	/**
	 * Construct a lazy concatenated tuple type object to represent the type
	 * that is the concatenation of the two tuple types.  Make the objects be
	 * immutable, because the new type represents the concatenation of the
	 * objects <em>at the time it was built</em>.
	 *
	 * @param firstObject
	 *            The first tuple type to concatenate.
	 * @param secondObject
	 *            The second tuple type to concatenate.
	 * @return
	 *            An object repr
	 */
	public static @NotNull AvailObject concatenatingAnd (
		final @NotNull AvailObject firstObject,
		final @NotNull AvailObject secondObject)
	{
		assert firstObject.isTupleType() && secondObject.isTupleType();
		final AvailObject result = mutable().create();
		result.objectSlotPut(
			ObjectSlots.FIRST_TUPLE_TYPE,
			firstObject.makeImmutable());
		result.objectSlotPut(
			ObjectSlots.SECOND_TUPLE_TYPE,
			secondObject.makeImmutable());
		return result;
	}

	/**
	 * Construct a new {@link ConcatenatedTupleTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ConcatenatedTupleTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ConcatenatedTupleTypeDescriptor}.
	 */
	private final static ConcatenatedTupleTypeDescriptor mutable =
		new ConcatenatedTupleTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ConcatenatedTupleTypeDescriptor}.
	 *
	 * @return The mutable {@link ConcatenatedTupleTypeDescriptor}.
	 */
	public static ConcatenatedTupleTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ConcatenatedTupleTypeDescriptor}.
	 */
	private final static ConcatenatedTupleTypeDescriptor immutable =
		new ConcatenatedTupleTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ConcatenatedTupleTypeDescriptor}.
	 *
	 * @return The immutable {@link ConcatenatedTupleTypeDescriptor}.
	 */
	public static ConcatenatedTupleTypeDescriptor immutable ()
	{
		return immutable;
	}
}
