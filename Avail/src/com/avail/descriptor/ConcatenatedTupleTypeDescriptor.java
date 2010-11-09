/**
 * descriptor/ConcatenatedTupleTypeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this *   list of conditions and the following disclaimer.
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
import com.avail.descriptor.ObjectTupleDescriptor;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import static java.lang.Math.*;

@ObjectSlots({
	"firstTupleType", 
	"secondTupleType"
})
public class ConcatenatedTupleTypeDescriptor extends TypeDescriptor
{


	// GENERATED accessors

	void ObjectFirstTupleType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	void ObjectSecondTupleType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-8, value);
	}

	AvailObject ObjectFirstTupleType (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-4);
	}

	AvailObject ObjectSecondTupleType (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-8);
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsTupleType(object);
	}

	boolean ObjectEqualsTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Tuple types are equal iff their sizeRange, typeTuple, and defaultType match.

		if (object.sameAddressAs(aTupleType))
		{
			return true;
		}
		if (! object.sizeRange().equals(aTupleType.sizeRange()))
		{
			return false;
		}
		if (! object.defaultType().equals(aTupleType.defaultType()))
		{
			return false;
		}
		return object.typeTuple().equals(aTupleType.typeTuple());
	}

	boolean ObjectIsBetterRepresentationThan (
			final AvailObject object, 
			final AvailObject anotherObject)
	{
		//  Given two objects that are known to be equal, is the first one in a better form (more
		//  compact, more efficient, older generation) than the second one?

		//  I'm not a very efficient representation.
		return false;
	}

	boolean ObjectIsBetterRepresentationThanTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Given two objects that are known to be equal, the second of which is in the form of
		//  a tuple type, is the first one in a better form than the second one?

		//  I'm not a very efficient alternative representation of a tuple type.
		return false;
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return TypeDescriptor.tupleType();
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit long that is always the same for equal objects, but
		//  statistically different for different objects.  This requires an object creation, so
		//  don't call it from the garbage collector.

		object.becomeRealTupleType();
		return object.hash();
	}

	boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return false;
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return TypeDescriptor.tupleType();
	}



	// operations-tuple types

	AvailObject ObjectTypeAtIndex (
			final AvailObject object, 
			final int index)
	{
		//  Answer what type the given index would have in an object instance of me.  Answer
		//  terminates if the index is definitely out of bounds.

		if ((index <= 0))
		{
			return TypeDescriptor.terminates();
		}
		final AvailObject firstUpper = object.firstTupleType().sizeRange().upperBound();
		final AvailObject secondUpper = object.secondTupleType().sizeRange().upperBound();
		final AvailObject totalUpper = firstUpper.plusCanDestroy(secondUpper, false);
		if (totalUpper.isFinite())
		{
			final AvailObject indexObject = IntegerDescriptor.objectFromInt(index);
			if (indexObject.greaterThan(totalUpper))
			{
				return TypeDescriptor.terminates();
			}
		}
		final AvailObject firstLower = object.firstTupleType().sizeRange().lowerBound();
		if ((index <= firstLower.extractInt()))
		{
			return object.firstTupleType().typeAtIndex(index);
		}
		//  Besides possibly being at a fixed offset within the firstTupleType, the index
		//  might represent a range of possible indices of the secondTupleType, depending
		//  on the spread between the first tuple type's lower and upper bounds.  Compute
		//  the union of these types.
		final AvailObject unionType = object.firstTupleType().typeAtIndex(index);
		int startIndex;
		if (firstUpper.isFinite())
		{
			startIndex = max ((index - firstUpper.extractInt()), 1);
		}
		else
		{
			startIndex = 1;
		}
		final int endIndex = (index - firstLower.extractInt());
		assert (endIndex >= startIndex);
		return unionType.typeUnion(object.secondTupleType().unionOfTypesAtThrough(startIndex, endIndex));
	}

	AvailObject ObjectUnionOfTypesAtThrough (
			final AvailObject object, 
			final int startIndex, 
			final int endIndex)
	{
		//  Answer the union of the types that object's instances could have in the
		//  given range of indices.  Out-of-range indices are treated as terminates,
		//  which don't affect the union (unless all indices are out of range).

		assert (startIndex <= endIndex);
		if ((startIndex == endIndex))
		{
			return object.typeAtIndex(startIndex);
		}
		if ((endIndex <= 0))
		{
			return TypeDescriptor.terminates();
		}
		final AvailObject firstUpper = object.firstTupleType().sizeRange().upperBound();
		final AvailObject secondUpper = object.secondTupleType().sizeRange().upperBound();
		final AvailObject totalUpper = firstUpper.plusCanDestroy(secondUpper, false);
		if (totalUpper.isFinite())
		{
			if ((startIndex > totalUpper.extractInt()))
			{
				return TypeDescriptor.terminates();
			}
		}
		AvailObject unionType = object.firstTupleType().unionOfTypesAtThrough(startIndex, endIndex);
		final int startInSecond = (startIndex - firstUpper.extractInt());
		final int endInSecond = (endIndex - object.firstTupleType().sizeRange().lowerBound().extractInt());
		unionType = unionType.typeUnion(object.secondTupleType().unionOfTypesAtThrough(startInSecond, endInSecond));
		return unionType;
	}

	void ObjectBecomeRealTupleType (
			final AvailObject object)
	{
		//  Expand me into an actual TupleTypeDescriptor, converting my storage into
		//  an indirection object to the actual tupleType.  Answer void.

		final AvailObject part1 = object.firstTupleType();
		final AvailObject size1 = part1.sizeRange().upperBound();
		int limit1;
		if (size1.isFinite())
		{
			limit1 = size1.extractInt();
		}
		else
		{
			limit1 = max ((part1.typeTuple().tupleSize() + 1), part1.sizeRange().lowerBound().extractInt());
		}
		final AvailObject part2 = object.secondTupleType();
		final AvailObject size2 = part2.sizeRange().upperBound();
		int limit2;
		if (size2.isFinite())
		{
			limit2 = size2.extractInt();
		}
		else
		{
			limit2 = (part2.typeTuple().tupleSize() + 1);
		}
		final int total = (limit1 + limit2);
		final AvailObject typeTuple = AvailObject.newIndexedDescriptor(total, ObjectTupleDescriptor.mutableDescriptor());
		AvailObject.lock(typeTuple);
		for (int i = 1; i <= total; i++)
		{
			typeTuple.tupleAtPut(i, VoidDescriptor.voidObject());
		}
		//  Make it pointer-safe first.
		final int section1 = min (part1.sizeRange().lowerBound().extractInt(), limit1);
		for (int i = 1; i <= section1; i++)
		{
			typeTuple.tupleAtPut(i, part1.typeAtIndex(i));
		}
		for (int i = (section1 + 1); i <= total; i++)
		{
			typeTuple.tupleAtPut(i, object.typeAtIndex(i));
		}
		final AvailObject newObject = TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			object.sizeRange(),
			typeTuple,
			object.defaultType());
		AvailObject.unlock(typeTuple);
		object.becomeIndirectionTo(newObject);
	}

	AvailObject ObjectDefaultType (
			final AvailObject object)
	{
		//  Answer the type that my last element must have, if any.  Do not call
		//  this from within a garbage collection, as it may need to allocate space
		//  for computing a type union.

		final AvailObject a = object.firstTupleType();
		final AvailObject b = object.secondTupleType();
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
			highIndexInB = (b.typeTuple().tupleSize() + 1);
		}
		final AvailObject unionType = a.defaultType().typeUnion(b.unionOfTypesAtThrough(1, highIndexInB));
		return unionType;
	}

	AvailObject ObjectSizeRange (
			final AvailObject object)
	{
		//  Answer what range of tuple sizes my instances could be.  Note that this can not
		//  be asked during a garbage collection because it allocates space for its answer.

		final AvailObject a = object.firstTupleType().sizeRange();
		final AvailObject b = object.secondTupleType().sizeRange();
		final AvailObject upper = a.upperBound().plusCanDestroy(b.upperBound(), false);
		return IntegerRangeTypeDescriptor.lowerBoundInclusiveUpperBoundInclusive(
			a.lowerBound().plusCanDestroy(b.lowerBound(), false),
			true,
			upper,
			upper.isFinite());
	}

	AvailObject ObjectTypeTuple (
			final AvailObject object)
	{
		//  Since this is really tricky, just compute the TupleTypeDescriptor that I am
		//  shorthand for.  Answer that tupleType's typeTuple.  This is the leading types
		//  of the tupleType, up to but not including where they all have the same type.
		//  Don't run this from within a garbage collection, as it allocates objects.

		object.becomeRealTupleType();
		return object.typeTuple();
	}



	// operations-types

	boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfTupleType(object);
	}

	boolean ObjectIsSupertypeOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Tuple type A is a supertype of tuple type B iff all the *possible
		//  instances* of B would also be instances of A.  Types indistinguishable
		//  under these conditions are considered the same type.

		if (object.equals(aTupleType))
		{
			return true;
		}
		if (! aTupleType.sizeRange().isSubtypeOf(object.sizeRange()))
		{
			return false;
		}
		if (! aTupleType.defaultType().isSubtypeOf(object.defaultType()))
		{
			return false;
		}
		final AvailObject subTuple = aTupleType.typeTuple();
		final AvailObject superTuple = object.typeTuple();
		for (int i = 1, _end1 = max (subTuple.tupleSize(), superTuple.tupleSize()); i <= _end1; i++)
		{
			AvailObject subType;
			if ((i <= subTuple.tupleSize()))
			{
				subType = subTuple.tupleAt(i);
			}
			else
			{
				subType = aTupleType.defaultType();
			}
			AvailObject superType;
			if ((i <= superTuple.tupleSize()))
			{
				superType = superTuple.tupleAt(i);
			}
			else
			{
				superType = object.defaultType();
			}
			if (! subType.isSubtypeOf(superType))
			{
				return false;
			}
		}
		return true;
	}

	AvailObject ObjectTypeIntersection (
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
		return another.typeIntersectionOfTupleType(object);
	}

	AvailObject ObjectTypeIntersectionOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Answer the most general type that is still at least as specific as these.

		final AvailObject newSizesObject = object.sizeRange().typeIntersection(aTupleType.sizeRange());
		final AvailObject lead1 = object.typeTuple();
		final AvailObject lead2 = aTupleType.typeTuple();
		AvailObject newLeading;
		if ((lead1.tupleSize() > lead2.tupleSize()))
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
			if (intersectionObject.equals(TypeDescriptor.terminates()))
			{
				return TypeDescriptor.terminates();
			}
			newLeading = newLeading.tupleAtPuttingCanDestroy(
				i,
				intersectionObject,
				true);
		}
		//  Make sure entries in newLeading are immutable, as typeIntersection: can answer one
		//  of its arguments.
		newLeading.makeSubobjectsImmutable();
		final AvailObject newDefault = object.typeAtIndex((newLeadingSize + 1)).typeIntersection(aTupleType.typeAtIndex((newLeadingSize + 1)));
		//  safety until all primitives are destructive
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			newSizesObject,
			newLeading,
			newDefault.makeImmutable());
	}

	AvailObject ObjectTypeUnion (
			final AvailObject object, 
			final AvailObject another)
	{
		//  Answer the most specific type that is still at least as general as these.

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

	AvailObject ObjectTypeUnionOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Answer the most specific type that is still at least as general as these.

		final AvailObject newSizesObject = object.sizeRange().typeUnion(aTupleType.sizeRange());
		final AvailObject lead1 = object.typeTuple();
		final AvailObject lead2 = aTupleType.typeTuple();
		AvailObject newLeading;
		if ((lead1.tupleSize() > lead2.tupleSize()))
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
			final AvailObject unionObject = object.typeAtIndex(i).typeUnion(aTupleType.typeAtIndex(i));
			newLeading = newLeading.tupleAtPuttingCanDestroy(
				i,
				unionObject,
				true);
		}
		//  Make sure entries in newLeading are immutable, as typeUnion: can answer one
		//  of its arguments.
		newLeading.makeSubobjectsImmutable();
		final AvailObject newDefault = object.typeAtIndex((newLeadingSize + 1)).typeUnion(aTupleType.typeAtIndex((newLeadingSize + 1)));
		//  safety until all primitives are destructive
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			newSizesObject,
			newLeading,
			newDefault.makeImmutable());
	}

	boolean ObjectIsTupleType (
			final AvailObject object)
	{
		//  I am a tupleType, so answer true.

		return true;
	}





	/* Object creation */
	public static AvailObject concatenatingAnd (
			AvailObject firstObject,
			AvailObject secondObject)
	{
		//  Construct a lazy concateneated tuple type object to represent
		//  the type that is the concatenation of the two tuple types.  Make
		//  the objects be immutable, because the new type represents
		//  the concatenation of the objects *at the time it was built*.

		assert(firstObject.isTupleType() && secondObject.isTupleType());
		AvailObject result = AvailObject.newIndexedDescriptor(
			0,
			ConcatenatedTupleTypeDescriptor.mutableDescriptor());
		result.firstTupleType(firstObject.makeImmutable());
		result.secondTupleType(secondObject.makeImmutable());
		return result;
	};


	/* Descriptor lookup */
	public static ConcatenatedTupleTypeDescriptor mutableDescriptor()
	{
		return (ConcatenatedTupleTypeDescriptor) AllDescriptors [32];
	};
	public static ConcatenatedTupleTypeDescriptor immutableDescriptor()
	{
		return (ConcatenatedTupleTypeDescriptor) AllDescriptors [33];
	};

}
