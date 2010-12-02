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
public class ApproximateTypeDescriptor extends TypeDescriptor
{

	enum ObjectSlots
	{
		INSTANCE
	}


	/**
	 * Setter for field instance.
	 */
	@Override
	public void o_Instance (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.INSTANCE, value);
	}

	/**
	 * Getter for field instance.
	 */
	@Override
	public AvailObject o_Instance (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.INSTANCE);
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
	public boolean o_Equals (
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
	public boolean o_HasObjectInstance (
			final AvailObject object,
			final AvailObject potentialInstance)
	{
		//  The potentialInstance is a user-defined object.  See if it is an instance of me.

		return object.becomeExactType().hasObjectInstance(potentialInstance);
	}

	@Override
	public boolean o_IsBetterRepresentationThan (
			final AvailObject object,
			final AvailObject anotherObject)
	{
		//  Given two objects that are known to be equal, is the first one in a better form (more
		//  compact, more efficient, older generation) than the second one?

		//  I'm quick for some things, but if a more 'solid' object exists, use it.
		return false;
	}

	@Override
	public boolean o_IsBetterRepresentationThanTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		//  Given two objects that are known to be equal, the second of which is in the form of
		//  a tuple type, is the first one in a better form than the second one?

		//  I'm not a very efficient alternative representation of a tuple type.
		return false;
	}

	@Override
	public AvailObject o_BecomeExactType (
			final AvailObject object)
	{
		//  Transform object into an indirection to its exact equivalent.

		final AvailObject exact = object.instance().exactType();
		object.becomeIndirectionTo(exact);
		return exact;
	}

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		//  Answer this object's type (which will be a meta).

		return object.becomeExactType().exactType();
	}

	@Override
	public int o_Hash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value that is always the same for equal objects, but
		//  statistically different for different objects.

		final AvailObject inst = object.instance();
		return (inst.canComputeHashOfType() ? inst.hashOfType() : object.becomeExactType().hash());
	}

	@Override
	public boolean o_IsHashAvailable (
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
	public AvailObject o_Type (
			final AvailObject object)
	{
		//  Answer this object's type (which will be a meta).

		return object.becomeExactType().type();
	}



	// operations-faulting

	@Override
	public boolean o_EqualsClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aClosureType) : object.becomeExactType().equalsClosureType(aClosureType));
	}

	@Override
	public boolean o_EqualsContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aContainerType) : object.becomeExactType().equalsContainerType(aContainerType));
	}

	@Override
	public boolean o_EqualsContinuationType (
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
	public boolean o_EqualsGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  This is always false here, but we can leave that to the type propagating Java VM generator to deduce.

		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aGeneralizedClosureType) : object.becomeExactType().equalsGeneralizedClosureType(aGeneralizedClosureType));
	}

	@Override
	public boolean o_EqualsIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(anIntegerRangeType) : object.becomeExactType().equalsIntegerRangeType(anIntegerRangeType));
	}

	@Override
	public boolean o_EqualsListType (
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
	public boolean o_EqualsMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aMapType) : object.becomeExactType().equalsMapType(aMapType));
	}

	@Override
	public boolean o_EqualsPrimitiveType (
			final AvailObject object,
			final AvailObject aPrimitiveType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aPrimitiveType) : object.becomeExactType().equalsPrimitiveType(aPrimitiveType));
	}

	@Override
	public boolean o_EqualsSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aSetType) : object.becomeExactType().equalsSetType(aSetType));
	}

	@Override
	public boolean o_EqualsTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		return (object.instance().canComputeHashOfType() ? object.instance().typeEquals(aTupleType) : object.becomeExactType().equalsTupleType(aTupleType));
	}

	@Override
	public boolean o_IsSupertypeOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfClosureType(aClosureType);
	}

	@Override
	public boolean o_IsSupertypeOfContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfContainerType(aContainerType);
	}

	@Override
	public boolean o_IsSupertypeOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfCyclicType(aCyclicType);
	}

	@Override
	public boolean o_IsSupertypeOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	@Override
	public boolean o_IsSupertypeOfIntegerRangeType (
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
	public boolean o_IsSupertypeOfListType (
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
	public boolean o_IsSupertypeOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfMapType(aMapType);
	}

	@Override
	public boolean o_IsSupertypeOfObjectMeta (
			final AvailObject object,
			final AvailObject anObjectMeta)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfObjectMeta(anObjectMeta);
	}

	@Override
	public boolean o_IsSupertypeOfObjectMetaMeta (
			final AvailObject object,
			final AvailObject anObjectMetaMeta)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfObjectMetaMeta(anObjectMetaMeta);
	}

	@Override
	public boolean o_IsSupertypeOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfObjectType(anObjectType);
	}

	@Override
	public boolean o_IsSupertypeOfPrimitiveType (
			final AvailObject object,
			final AvailObject aPrimitiveType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfPrimitiveType(aPrimitiveType);
	}

	@Override
	public boolean o_IsSupertypeOfSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfSetType(aSetType);
	}

	@Override
	public boolean o_IsSupertypeOfTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfTupleType(aTupleType);
	}

	@Override
	public AvailObject o_TypeIntersection (
			final AvailObject object,
			final AvailObject another)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersection(another);
	}

	@Override
	public AvailObject o_TypeIntersectionOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfClosureType(aClosureType);
	}

	@Override
	public AvailObject o_TypeIntersectionOfContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfContainerType(aContainerType);
	}

	@Override
	public AvailObject o_TypeIntersectionOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfCyclicType(aCyclicType);
	}

	@Override
	public AvailObject o_TypeIntersectionOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	@Override
	public AvailObject o_TypeIntersectionOfIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public AvailObject o_TypeIntersectionOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfListType(aListType);
	}

	@Override
	public AvailObject o_TypeIntersectionOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfMapType(aMapType);
	}

	@Override
	public AvailObject o_TypeIntersectionOfMeta (
			final AvailObject object,
			final AvailObject someMeta)
	{
		//  Answer the most general type that is still at least as specific as these.
		//  Since metas intersect at terminatesType rather than terminates, we must
		//  be very careful to overide this properly.

		return object.becomeExactType().typeIntersectionOfMeta(someMeta);
	}

	@Override
	public AvailObject o_TypeIntersectionOfObjectMeta (
			final AvailObject object,
			final AvailObject anObjectMeta)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfObjectMeta(anObjectMeta);
	}

	@Override
	public AvailObject o_TypeIntersectionOfObjectMetaMeta (
			final AvailObject object,
			final AvailObject anObjectMetaMeta)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfObjectMetaMeta(anObjectMetaMeta);
	}

	@Override
	public AvailObject o_TypeIntersectionOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfObjectType(anObjectType);
	}

	@Override
	public AvailObject o_TypeIntersectionOfSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfSetType(aSetType);
	}

	@Override
	public AvailObject o_TypeIntersectionOfTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfTupleType(aTupleType);
	}

	@Override
	public AvailObject o_TypeUnion (
			final AvailObject object,
			final AvailObject another)
	{
		//  Answer the most specific type that still includes both of these.  Make it exact first.

		return object.becomeExactType().typeUnion(another);
	}

	@Override
	public AvailObject o_TypeUnionOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfClosureType(aClosureType);
	}

	@Override
	public AvailObject o_TypeUnionOfContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfContainerType(aContainerType);
	}

	@Override
	public AvailObject o_TypeUnionOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfCyclicType(aCyclicType);
	}

	@Override
	public AvailObject o_TypeUnionOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	@Override
	public AvailObject o_TypeUnionOfIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public AvailObject o_TypeUnionOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfListType(aListType);
	}

	@Override
	public AvailObject o_TypeUnionOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfMapType(aMapType);
	}

	@Override
	public AvailObject o_TypeUnionOfObjectMeta (
			final AvailObject object,
			final AvailObject anObjectMeta)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfObjectMeta(anObjectMeta);
	}

	@Override
	public AvailObject o_TypeUnionOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfObjectType(anObjectType);
	}

	@Override
	public AvailObject o_TypeUnionOfSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfSetType(aSetType);
	}

	@Override
	public AvailObject o_TypeUnionOfTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfTupleType(aTupleType);
	}

	@Override
	public AvailObject o_FieldTypeMap (
			final AvailObject object)
	{
		return object.becomeExactType().fieldTypeMap();
	}



	// operations-integer range

	@Override
	public AvailObject o_LowerBound (
			final AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return object.instance();
		}
		return object.becomeExactType().lowerBound();
	}

	@Override
	public boolean o_LowerInclusive (
			final AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return true;
		}
		return object.becomeExactType().lowerInclusive();
	}

	@Override
	public AvailObject o_UpperBound (
			final AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return object.instance();
		}
		return object.becomeExactType().upperBound();
	}

	@Override
	public boolean o_UpperInclusive (
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
	public AvailObject o_TupleType (
			final AvailObject object)
	{
		//  I must support this in case I represent a listType.  I simply go to my
		//  instance, get its tuple, and take its approximate type.

		return object.instance().tuple().type();
	}



	// operations-tupleType

	@Override
	public AvailObject o_TypeAtIndex (
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
	public AvailObject o_UnionOfTypesAtThrough (
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
	public AvailObject o_DefaultType (
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
	public AvailObject o_SizeRange (
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
	public AvailObject o_TypeTuple (
			final AvailObject object)
	{
		//  I can't keep pretending forever.  The encapsulation of tupleType has leaked enough
		//  already.  Fault in the real type.

		return object.becomeExactType().typeTuple();
	}



	// operations-types

	@Override
	public boolean o_IsSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return object.instance().isInstanceOfSubtypeOf(aType);
	}

	@Override
	public boolean o_IsIntegerRangeType (
			final AvailObject object)
	{
		return object.instance().isExtendedInteger();
	}

	@Override
	public boolean o_IsListType (
			final AvailObject object)
	{
		return object.instance().isList();
	}

	@Override
	public boolean o_IsMapType (
			final AvailObject object)
	{
		return object.instance().isMap();
	}

	@Override
	public boolean o_IsSetType (
			final AvailObject object)
	{
		return object.instance().isSet();
	}

	@Override
	public boolean o_IsTupleType (
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
