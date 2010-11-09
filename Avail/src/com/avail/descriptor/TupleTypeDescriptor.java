/**
 * descriptor/TupleTypeDescriptor.java
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
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.descriptor.TypeDescriptor;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;
import static java.lang.Math.*;

@ObjectSlots({
	"sizeRange", 
	"typeTuple", 
	"defaultType"
})
public class TupleTypeDescriptor extends TypeDescriptor
{


	// GENERATED accessors

	void ObjectDefaultType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-12, value);
	}

	void ObjectSizeRange (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	void ObjectTypeTuple (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-8, value);
	}

	AvailObject ObjectDefaultType (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-12);
	}

	AvailObject ObjectSizeRange (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-4);
	}

	AvailObject ObjectTypeTuple (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-8);
	}



	// java printing

	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		//  Be nice about it and use special forms for common cases...

		if ((object.typeTuple().tupleSize() == 0))
		{
			if (object.sizeRange().equals(IntegerRangeTypeDescriptor.wholeNumbers()))
			{
				if (object.defaultType().equals(TypeDescriptor.all()))
				{
					aStream.append("tuple");
					return;
				}
				if (object.defaultType().equals(TypeDescriptor.character()))
				{
					aStream.append("string");
					return;
				}
				//  Ok, it's homogenous and of arbitrary size...
				aStream.append("tuple of ");
				object.defaultType().printOnAvoidingIndent(
					aStream,
					recursionList,
					(indent + 1));
				return;
			}
		}
		if (object.sizeRange().upperBound().lessOrEqual(IntegerDescriptor.objectFromByte(((byte)(10)))))
		{
			if (object.sizeRange().upperBound().equals(object.sizeRange().lowerBound()))
			{
				aStream.append("tuple like <");
				for (int i = 1, _end1 = object.sizeRange().upperBound().extractInt(); i <= _end1; i++)
				{
					if ((i > 1))
					{
						aStream.append(", ");
					}
					object.typeAtIndex(i).printOnAvoidingIndent(
						aStream,
						recursionList,
						(indent + 1));
				}
				aStream.append(">");
				return;
			}
		}
		//  Default case...
		aStream.append("tuple ");
		object.sizeRange().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		aStream.append(" like ");
		object.typeTuple().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		aStream.append(" default (");
		object.defaultType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		aStream.append(")");
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

		return (! anotherObject.isBetterRepresentationThanTupleType(object));
	}

	boolean ObjectIsBetterRepresentationThanTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Given two objects that are known to be equal, the second of which is in the form of
		//  a tuple type, is the first one in a better form than the second one?

		//  I'm a pretty good representation
		return true;
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
		//  Answer a 32-bit integer that is always the same for equal objects, but
		//  statistically different for different objects.

		return TupleTypeDescriptor.hashOfTupleTypeWithSizesHashTypesHashDefaultTypeHash(
			object.sizeRange().hash(),
			object.typeTuple().hash(),
			object.defaultType().hash());
	}

	boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		if (! object.sizeRange().isHashAvailable())
		{
			return false;
		}
		if (! object.typeTuple().isHashAvailable())
		{
			return false;
		}
		if (! object.defaultType().isHashAvailable())
		{
			return false;
		}
		return true;
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
		//  terminates if the index is out of bounds.

		if ((index <= 0))
		{
			return TypeDescriptor.terminates();
		}
		final AvailObject upper = object.sizeRange().upperBound();
		if (upper.lessThan(IntegerDescriptor.objectFromInt(index)))
		{
			return TypeDescriptor.terminates();
		}
		final AvailObject leading = object.typeTuple();
		if ((index <= leading.tupleSize()))
		{
			return leading.tupleAt(index);
		}
		return object.defaultType();
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
		final AvailObject upper = object.sizeRange().upperBound();
		if ((upper.isFinite() && (startIndex > upper.extractInt())))
		{
			return TypeDescriptor.terminates();
		}
		final AvailObject leading = object.typeTuple();
		final int interestingLimit = (leading.tupleSize() + 1);
		final int clipStart = max (min (startIndex, interestingLimit), 1);
		final int clipEnd = max (min (endIndex, interestingLimit), 1);
		AvailObject unionType = object.typeAtIndex(clipStart);
		for (int i = (clipStart + 1); i <= clipEnd; i++)
		{
			unionType = unionType.typeUnion(object.typeAtIndex(i));
		}
		return unionType;
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

		AvailObject newSizesObject = object.sizeRange().typeIntersection(aTupleType.sizeRange());
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
		if (newDefault.equals(TypeDescriptor.terminates()))
		{
			final AvailObject newLeadingSizeObject = IntegerDescriptor.objectFromInt(newLeadingSize);
			if (newLeadingSizeObject.lessThan(newSizesObject.lowerBound()))
			{
				return TypeDescriptor.terminates();
			}
			if (newLeadingSizeObject.lessThan(newSizesObject.upperBound()))
			{
				newSizesObject = IntegerRangeTypeDescriptor.lowerBoundInclusiveUpperBoundInclusive(
					newSizesObject.lowerBound(),
					newSizesObject.lowerInclusive(),
					newLeadingSizeObject,
					true);
			}
		}
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





	/* Descriptor lookup */
	public static AvailObject tupleTypeForSizesTypesDefaultType(
			AvailObject sizeRange,
			AvailObject typeTuple,
			AvailObject defaultType)
	{
		if (sizeRange.equals(TypeDescriptor.terminates()))
			return TypeDescriptor.terminates();
		assert(sizeRange.lowerBound().isFinite());
		assert(sizeRange.upperBound().isFinite() || !sizeRange.upperInclusive());
		assert(IntegerDescriptor.objectFromInt(typeTuple.tupleSize()).lessOrEqual(sizeRange.upperBound()));
		if (sizeRange.lowerBound().equals(IntegerDescriptor.objectFromByte((byte)0))
				&& sizeRange.upperBound().equals(IntegerDescriptor.objectFromByte((byte)0)))
		{
			assert(typeTuple.tupleSize() == 0);
			return privateTupleTypeForSizesTypesDefaultType(
				sizeRange, typeTuple, TypeDescriptor.terminates());
		}
		if (sizeRange.upperInclusive() && sizeRange.upperBound().extractInt() == typeTuple.tupleSize())
		{
			//  The (nonempty) tuple hits the end of the range- disregard the passed defaultType and
			//  use the final element of the tuple as the defaultType, while removing it from the tuple.
			//  Recurse for further reductions.
			return tupleTypeForSizesTypesDefaultType(
				sizeRange,
				typeTuple.copyTupleFromToCanDestroy(1, typeTuple.tupleSize() - 1, false),
				typeTuple.tupleAt(typeTuple.tupleSize()).makeImmutable());
		}
		if (typeTuple.tupleSize() > 0 && typeTuple.tupleAt(typeTuple.tupleSize()).equals(defaultType))
		{
			//  See how many other redundant entries we can drop.
			int index = typeTuple.tupleSize() - 1;
			while (index > 0 && typeTuple.tupleAt(index).equals(defaultType))
				--index;
			return tupleTypeForSizesTypesDefaultType(
				sizeRange,
				typeTuple.copyTupleFromToCanDestroy(1, index, false),
				defaultType);
		};
		return privateTupleTypeForSizesTypesDefaultType(
			sizeRange, typeTuple, defaultType);
	};

	static AvailObject privateTupleTypeForSizesTypesDefaultType (
			AvailObject sizeRange,
			AvailObject typeTuple,
			AvailObject defaultType)
	{
		assert(sizeRange.lowerBound().isFinite());
		assert(sizeRange.upperBound().isFinite() || !sizeRange.upperInclusive());
		assert(sizeRange.lowerBound().extractInt() >= 0);
		if (sizeRange.lowerBound().extractInt() > typeTuple.tupleSize())
			if (defaultType.equals(TypeDescriptor.terminates()))
				error("Illegal tuple type construction (the defaultType)");
		final int limit = min(sizeRange.lowerBound().extractInt(), typeTuple.tupleSize());
		for (int i = 1; i <= limit; i++)
			if (typeTuple.tupleAt(i).equals(TypeDescriptor.terminates()))
				error("Illegal tuple type construction (some element type)");
		AvailObject result = AvailObject.newIndexedDescriptor(0, TupleTypeDescriptor.mutableDescriptor());
		result.sizeRange(sizeRange);
		result.typeTuple(typeTuple);
		result.defaultType(defaultType);
		return result;
	};

	public static AvailObject mostGeneralTupleType ()
	{
		return tupleTypeForSizesTypesDefaultType(
			IntegerRangeTypeDescriptor.lowerBoundInclusiveUpperBoundInclusive(
				IntegerDescriptor.zero(),
				true,
				InfinityDescriptor.positiveInfinity(),
				false),
			TupleDescriptor.empty(),
			TypeDescriptor.all());
		// Note that non-all elements (i.e., lists) are not allowed inside a tuple.
	}

	public static AvailObject stringTupleType ()
	{
		return tupleTypeForSizesTypesDefaultType(
			IntegerRangeTypeDescriptor.lowerBoundInclusiveUpperBoundInclusive(
				IntegerDescriptor.zero(),
				true,
				InfinityDescriptor.positiveInfinity(),
				false),
			TupleDescriptor.empty(),
			TypeDescriptor.character());
	}

	static int hashOfTupleTypeWithSizesHashTypesHashDefaultTypeHash (
			int sizesHash,
			int typeTupleHash,
			int defaultTypeHash)
	{
		return ((sizesHash *13) + (defaultTypeHash * 11) + (typeTupleHash * 7)) & HashMask;
	};


	/* Descriptor lookup */
	public static TupleTypeDescriptor mutableDescriptor()
	{
		return (TupleTypeDescriptor) AllDescriptors [154];
	};
	public static TupleTypeDescriptor immutableDescriptor()
	{
		return (TupleTypeDescriptor) AllDescriptors [155];
	};

}
