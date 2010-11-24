/**
 * descriptor/ApproximateTypeDescriptor.java
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
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;
import static java.lang.Math.*;

/**
 * My instances are cheaply constructed type proxies, representing the type of a
 * particular provided object.  Some type operations are easily translated into
 * a form that can be fielded directly by the provided object, while others
 * require an answer that is too difficult for the object to deal with.  For the
 * latter case we ask the provided object for its {@link AvailObject#exactType()
 * exactType()} (which must never be an {@code ApproximateTypeDescriptor}), and
 * then change the {@code ApproximateTypeDescriptor} into an {@linkplain
 * IndirectionDescriptor indirection} to the exact type.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
@ObjectSlots("instance")
public class ApproximateTypeDescriptor extends TypeDescriptor
{


	/**
	 * Setter for field instance.
	 */
	@Override
	public void ObjectInstance (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Getter for field instance.
	 */
	@Override
	public AvailObject ObjectInstance (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}


	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object,
			final StringBuilder aStream,
			final List<AvailObject> recursionList,
			final int indent)
	{
		aStream.append("ApproximateType of ");
		object.instance().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}


	@Override
	public boolean ObjectEquals (
			final AvailObject object,
			final AvailObject another)
	{
		//  Check if object and another are equal.  Since I'm an ApproximateType, I have two
		//  choices for how to proceed.  If my instance claims it canComputeHashOfType, then
		//  it must also support the hashOfType: and typeEquals: messages without creating
		//  any instances.  Otherwise, I must create the exact type and let it do the equality
		//  check.

		final AvailObject inst = object.instance();
		return (inst.canComputeHashOfType() ? inst.typeEquals(another) : object.becomeExactType().equals(another));
	}

	@Override
	public boolean ObjectHasObjectInstance (
			final AvailObject object,
			final AvailObject potentialInstance)
	{
		//  The potentialInstance is a user-defined object.  See if it is an instance of me.

		return object.becomeExactType().hasObjectInstance(potentialInstance);
	}

	@Override
	public boolean ObjectIsBetterRepresentationThan (
			final AvailObject object,
			final AvailObject anotherObject)
	{
		//  Given two objects that are known to be equal, is the first one in a better form (more
		//  compact, more efficient, older generation) than the second one?

		//  I'm quick for some things, but if a more 'solid' object exists, use it.
		return false;
	}

	@Override
	public boolean ObjectIsBetterRepresentationThanTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		//  Given two objects that are known to be equal, the second of which is in the form of
		//  a tuple type, is the first one in a better form than the second one?

		//  I'm not a very efficient alternative representation of a tuple type.
		return false;
	}

	@Override
	public AvailObject ObjectBecomeExactType (
			final AvailObject object)
	{
		//  Transform object into an indirection to its exact equivalent.

		final AvailObject exact = object.instance().exactType();
		object.becomeIndirectionTo(exact);
		return exact;
	}

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer this object's type (which will be a meta).

		return object.becomeExactType().exactType();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value that is always the same for equal objects, but
		//  statistically different for different objects.

		final AvailObject inst = object.instance();
		return (inst.canComputeHashOfType() ? inst.hashOfType() : object.becomeExactType().hash());
	}

	@Override
	public boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.  We ask our instance
		//  if it can compute #hashOfType or not.

		return object.instance().canComputeHashOfType();
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer this object's type (which will be a meta).

		return object.becomeExactType().type();
	}



	// operations-faulting

	@Override
	public boolean ObjectEqualsClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aClosureType) : object.becomeExactType().equalsClosureType(aClosureType));
	}

	@Override
	public boolean ObjectEqualsContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aContainerType) : object.becomeExactType().equalsContainerType(aContainerType));
	}

	@Override
	public boolean ObjectEqualsContinuationType (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Continuation types compare for equality by comparing their closureTypes.

		if (object.sameAddressAs(aType))
		{
			return true;
		}
		return object.instance().closure().type().equals(aType.closureType());
	}

	@Override
	public boolean ObjectEqualsGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  This is always false here, but we can leave that to the type propagating Java VM generator to deduce.

		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aGeneralizedClosureType) : object.becomeExactType().equalsGeneralizedClosureType(aGeneralizedClosureType));
	}

	@Override
	public boolean ObjectEqualsIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(anIntegerRangeType) : object.becomeExactType().equalsIntegerRangeType(anIntegerRangeType));
	}

	@Override
	public boolean ObjectEqualsListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		if (object.instance().isList())
		{
			return object.instance().tuple().type().equals(aListType.tupleType());
		}
		return false;
	}

	@Override
	public boolean ObjectEqualsMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aMapType) : object.becomeExactType().equalsMapType(aMapType));
	}

	@Override
	public boolean ObjectEqualsPrimitiveType (
			final AvailObject object,
			final AvailObject aPrimitiveType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aPrimitiveType) : object.becomeExactType().equalsPrimitiveType(aPrimitiveType));
	}

	@Override
	public boolean ObjectEqualsSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aSetType) : object.becomeExactType().equalsSetType(aSetType));
	}

	@Override
	public boolean ObjectEqualsTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aTupleType) : object.becomeExactType().equalsTupleType(aTupleType));
	}

	@Override
	public boolean ObjectIsSupertypeOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfClosureType(aClosureType);
	}

	@Override
	public boolean ObjectIsSupertypeOfContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfContainerType(aContainerType);
	}

	@Override
	public boolean ObjectIsSupertypeOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfCyclicType(aCyclicType);
	}

	@Override
	public boolean ObjectIsSupertypeOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	@Override
	public boolean ObjectIsSupertypeOfIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		//  Redirect it to the exact type to be more precise.

		if (object.instance().isExtendedInteger())
		{
			return (anIntegerRangeType.lowerBound().equals(object.instance()) && anIntegerRangeType.upperBound().equals(object.instance()));
		}
		return object.becomeExactType().isSupertypeOfIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public boolean ObjectIsSupertypeOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		//  Redirect it to the exact type to be more precise.

		if (object.instance().isList())
		{
			return object.instance().tuple().type().isSupertypeOfTupleType(aListType.tupleType());
		}
		return false;
	}

	@Override
	public boolean ObjectIsSupertypeOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfMapType(aMapType);
	}

	@Override
	public boolean ObjectIsSupertypeOfObjectMeta (
			final AvailObject object,
			final AvailObject anObjectMeta)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfObjectMeta(anObjectMeta);
	}

	@Override
	public boolean ObjectIsSupertypeOfObjectMetaMeta (
			final AvailObject object,
			final AvailObject anObjectMetaMeta)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfObjectMetaMeta(anObjectMetaMeta);
	}

	@Override
	public boolean ObjectIsSupertypeOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfObjectType(anObjectType);
	}

	@Override
	public boolean ObjectIsSupertypeOfPrimitiveType (
			final AvailObject object,
			final AvailObject aPrimitiveType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfPrimitiveType(aPrimitiveType);
	}

	@Override
	public boolean ObjectIsSupertypeOfSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfSetType(aSetType);
	}

	@Override
	public boolean ObjectIsSupertypeOfTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfTupleType(aTupleType);
	}

	@Override
	public AvailObject ObjectTypeIntersection (
			final AvailObject object,
			final AvailObject another)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersection(another);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfClosureType(aClosureType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfContainerType(aContainerType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfCyclicType(aCyclicType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfListType(aListType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfMapType(aMapType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfMeta (
			final AvailObject object,
			final AvailObject someMeta)
	{
		//  Answer the most general type that is still at least as specific as these.
		//  Since metas intersect at terminatesType rather than terminates, we must
		//  be very careful to overide this properly.

		return object.becomeExactType().typeIntersectionOfMeta(someMeta);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfObjectMeta (
			final AvailObject object,
			final AvailObject anObjectMeta)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfObjectMeta(anObjectMeta);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfObjectMetaMeta (
			final AvailObject object,
			final AvailObject anObjectMetaMeta)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfObjectMetaMeta(anObjectMetaMeta);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfObjectType(anObjectType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfSetType(aSetType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfTupleType(aTupleType);
	}

	@Override
	public AvailObject ObjectTypeUnion (
			final AvailObject object,
			final AvailObject another)
	{
		//  Answer the most specific type that still includes both of these.  Make it exact first.

		return object.becomeExactType().typeUnion(another);
	}

	@Override
	public AvailObject ObjectTypeUnionOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfClosureType(aClosureType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfContainerType(aContainerType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfCyclicType(aCyclicType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfListType(aListType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfMapType(aMapType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfObjectMeta (
			final AvailObject object,
			final AvailObject anObjectMeta)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfObjectMeta(anObjectMeta);
	}

	@Override
	public AvailObject ObjectTypeUnionOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfObjectType(anObjectType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfSetType(aSetType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfTupleType(aTupleType);
	}

	@Override
	public AvailObject ObjectFieldTypeMap (
			final AvailObject object)
	{
		return object.becomeExactType().fieldTypeMap();
	}



	// operations-integer range

	@Override
	public AvailObject ObjectLowerBound (
			final AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return object.instance();
		}
		return object.becomeExactType().lowerBound();
	}

	@Override
	public boolean ObjectLowerInclusive (
			final AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return true;
		}
		return object.becomeExactType().lowerInclusive();
	}

	@Override
	public AvailObject ObjectUpperBound (
			final AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return object.instance();
		}
		return object.becomeExactType().upperBound();
	}

	@Override
	public boolean ObjectUpperInclusive (
			final AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return true;
		}
		return object.becomeExactType().upperInclusive();
	}



	// operations-lists

	@Override
	public AvailObject ObjectTupleType (
			final AvailObject object)
	{
		//  I must support this in case I represent a listType.  I simply go to my
		//  instance, get its tuple, and take its approximate type.

		return object.instance().tuple().type();
	}



	// operations-tupleType

	@Override
	public AvailObject ObjectTypeAtIndex (
			final AvailObject object,
			final int index)
	{
		//  This is only intended for a TupleType stand-in.
		//
		//  Answer what type the given index would have in an object instance of me.  Answer
		//  terminates if the index is out of bounds.

		if (!object.isTupleType())
		{
			error("Don't send typeAtIndex: to a non-tupleType", object);
			return VoidDescriptor.voidObject();
		}
		if ((1 <= index && index <= object.instance().tupleSize()))
		{
			return object.instance().tupleAt(index).type();
		}
		return Types.terminates.object();
	}

	@Override
	public AvailObject ObjectUnionOfTypesAtThrough (
			final AvailObject object,
			final int startIndex,
			final int endIndex)
	{
		//  Answer the union of the types that object's instances could have in the
		//  given range of indices.  Out-of-range indices are treated as terminates,
		//  which don't affect the union (unless all indices are out of range).

		assert (startIndex <= endIndex);
		assert object.isTupleType();
		if (startIndex == endIndex)
		{
			return object.typeAtIndex(startIndex);
		}
		if (endIndex <= 0)
		{
			return Types.terminates.object();
		}
		final AvailObject tupleObject = object.instance();
		final int upperIndex = tupleObject.tupleSize();
		if (startIndex > upperIndex)
		{
			return Types.terminates.object();
		}
		AvailObject unionType = Types.terminates.object();
		for (int i = max(startIndex, 1), _end1 = min(endIndex, upperIndex); i <= _end1; i++)
		{
			unionType = unionType.typeUnion(tupleObject.tupleAt(i).type());
		}
		return unionType;
	}

	@Override
	public AvailObject ObjectDefaultType (
			final AvailObject object)
	{
		if (!object.isTupleType())
		{
			error("Don't send defaultType to a non-tupleType", object);
			return VoidDescriptor.voidObject();
		}
		if ((object.instance().tupleSize() == 0))
		{
			return Types.terminates.object();
		}
		return object.instance().tupleAt(object.instance().tupleSize()).type();
	}

	@Override
	public AvailObject ObjectSizeRange (
			final AvailObject object)
	{
		if (!object.isTupleType())
		{
			error("Don't send sizeRange to a non-tupleType", object);
			return VoidDescriptor.voidObject();
		}
		return IntegerDescriptor.objectFromInt(object.instance().tupleSize()).type();
	}

	@Override
	public AvailObject ObjectTypeTuple (
			final AvailObject object)
	{
		//  I can't keep pretending forever.  The encapsulation of tupleType has leaked enough
		//  already.  Fault in the real type.

		return object.becomeExactType().typeTuple();
	}



	// operations-types

	@Override
	public boolean ObjectIsSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return object.instance().isInstanceOfSubtypeOf(aType);
	}

	@Override
	public boolean ObjectIsIntegerRangeType (
			final AvailObject object)
	{
		return object.instance().isExtendedInteger();
	}

	@Override
	public boolean ObjectIsListType (
			final AvailObject object)
	{
		return object.instance().isList();
	}

	@Override
	public boolean ObjectIsMapType (
			final AvailObject object)
	{
		return object.instance().isMap();
	}

	@Override
	public boolean ObjectIsSetType (
			final AvailObject object)
	{
		return object.instance().isSet();
	}

	@Override
	public boolean ObjectIsTupleType (
			final AvailObject object)
	{
		return object.instance().isTuple();
	}





	/**
	 * Answer a new instance of this descriptor based on some object whose type
	 * it will represent.
	 * 
	 * @param instance The object whose type to represent.
	 * @return An {@link AvailObject} representing the type of the argument.
	 */
	static AvailObject withInstance (AvailObject instance)
	{
		AvailObject result = AvailObject.newIndexedDescriptor(0, ApproximateTypeDescriptor.mutableDescriptor());
		result.instance(instance.makeImmutable());
		return result;
	};

	/**
	 * Construct a new {@link ApproximateTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ApproximateTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ApproximateTypeDescriptor}.
	 */
	private final static ApproximateTypeDescriptor mutableDescriptor = new ApproximateTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ApproximateTypeDescriptor}.
	 *
	 * @return The mutable {@link ApproximateTypeDescriptor}.
	 */
	public static ApproximateTypeDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link ApproximateTypeDescriptor}.
	 */
	private final static ApproximateTypeDescriptor immutableDescriptor = new ApproximateTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ApproximateTypeDescriptor}.
	 *
	 * @return The immutable {@link ApproximateTypeDescriptor}.
	 */
	public static ApproximateTypeDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
