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

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.TERMINATES;
import static java.lang.Math.*;
import java.util.List;
import com.avail.annotations.NotNull;

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
public class ApproximateTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@linkplain AvailObject object} for which I am the {@linkplain
		 * ApproximateTypeDescriptor approximate type}.
		 */
		INSTANCE
	}

	@Override
	public void o_Instance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.INSTANCE, value);
	}

	@Override
	public @NotNull AvailObject o_Instance (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.INSTANCE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
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
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		//  Check if object and another are equal.  Since I'm an ApproximateType, I have two
		//  choices for how to proceed.  If my instance claims it canComputeHashOfType, then
		//  it must also support the hashOfType: and typeEquals: messages without creating
		//  any instances.  Otherwise, I must create the exact type and let it do the equality
		//  check.

		final AvailObject inst = object.instance();
		return inst.canComputeHashOfType() ? inst.typeEquals(another) : object.becomeExactType().equals(another);
	}

	@Override
	public boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance)
	{
		//  The potentialInstance is a user-defined object.  See if it is an instance of me.

		return object.becomeExactType().hasObjectInstance(potentialInstance);
	}

	@Override
	public boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anotherObject)
	{
		//  Given two objects that are known to be equal, is the first one in a better form (more
		//  compact, more efficient, older generation) than the second one?

		//  I'm quick for some things, but if a more 'solid' object exists, use it.
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
	public @NotNull AvailObject o_BecomeExactType (
		final @NotNull AvailObject object)
	{
		//  Transform object into an indirection to its exact equivalent.

		final AvailObject exact = object.instance().exactType();
		object.becomeIndirectionTo(exact);
		return exact;
	}

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		//  Answer this object's type (which will be a meta).

		return object.becomeExactType().exactType();
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer a 32-bit hash value that is always the same for equal objects, but
		//  statistically different for different objects.

		final AvailObject inst = object.instance();
		return inst.canComputeHashOfType() ? inst.hashOfType() : object.becomeExactType().hash();
	}

	@Override
	public boolean o_IsHashAvailable (
		final @NotNull AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.  We ask our instance
		//  if it can compute #hashOfType or not.

		return object.instance().canComputeHashOfType();
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		//  Answer this object's type (which will be a meta).

		return object.becomeExactType().type();
	}

	// operations-faulting

	@Override
	public boolean o_EqualsClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		return object.instance().canComputeHashOfType() ? object.instance().typeEquals(aClosureType) : object.becomeExactType().equalsClosureType(aClosureType);
	}

	@Override
	public boolean o_EqualsContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		return object.instance().canComputeHashOfType() ? object.instance().typeEquals(aContainerType) : object.becomeExactType().equalsContainerType(aContainerType);
	}

	@Override
	public boolean o_EqualsContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
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
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		//  This is always false here, but we can leave that to the type propagating Java VM generator to deduce.

		return object.instance().canComputeHashOfType() ? object.instance().typeEquals(aGeneralizedClosureType) : object.becomeExactType().equalsGeneralizedClosureType(aGeneralizedClosureType);
	}

	@Override
	public boolean o_EqualsIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		return object.instance().canComputeHashOfType() ? object.instance().typeEquals(anIntegerRangeType) : object.becomeExactType().equalsIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public boolean o_EqualsMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return object.instance().canComputeHashOfType() ? object.instance().typeEquals(aMapType) : object.becomeExactType().equalsMapType(aMapType);
	}

	@Override
	public boolean o_EqualsPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPrimitiveType)
	{
		return object.instance().canComputeHashOfType() ? object.instance().typeEquals(aPrimitiveType) : object.becomeExactType().equalsPrimitiveType(aPrimitiveType);
	}

	@Override
	public boolean o_EqualsSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return object.instance().canComputeHashOfType() ? object.instance().typeEquals(aSetType) : object.becomeExactType().equalsSetType(aSetType);
	}

	@Override
	public boolean o_EqualsTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return object.instance().canComputeHashOfType() ? object.instance().typeEquals(aTupleType) : object.becomeExactType().equalsTupleType(aTupleType);
	}

	@Override
	public boolean o_IsSupertypeOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfClosureType(aClosureType);
	}

	@Override
	public boolean o_IsSupertypeOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfContainerType(aContainerType);
	}

	@Override
	public boolean o_IsSupertypeOfCyclicType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCyclicType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfCyclicType(aCyclicType);
	}

	@Override
	public boolean o_IsSupertypeOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	@Override
	public boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		//  Redirect it to the exact type to be more precise.

		if (object.instance().isExtendedInteger())
		{
			return anIntegerRangeType.lowerBound().equals(object.instance()) && anIntegerRangeType.upperBound().equals(object.instance());
		}
		return object.becomeExactType().isSupertypeOfIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfMapType(aMapType);
	}

	@Override
	public boolean o_IsSupertypeOfObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfObjectMeta(anObjectMeta);
	}

	@Override
	public boolean o_IsSupertypeOfObjectMetaMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMetaMeta)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfObjectMetaMeta(anObjectMetaMeta);
	}

	@Override
	public boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfObjectType(anObjectType);
	}

	@Override
	public boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPrimitiveType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfPrimitiveType(aPrimitiveType);
	}

	@Override
	public boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfSetType(aSetType);
	}

	@Override
	public boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		//  Redirect it to the exact type to be more precise.

		return object.becomeExactType().isSupertypeOfTupleType(aTupleType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersection(another);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfClosureType(aClosureType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfContainerType(aContainerType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfCyclicType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCyclicType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfCyclicType(aCyclicType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfMapType(aMapType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject someMeta)
	{
		//  Answer the most general type that is still at least as specific as these.
		//  Since metas intersect at terminatesType rather than terminates, we must
		//  be very careful to overide this properly.

		return object.becomeExactType().typeIntersectionOfMeta(someMeta);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfObjectMeta(anObjectMeta);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectMetaMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMetaMeta)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfObjectMetaMeta(anObjectMetaMeta);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfObjectType(anObjectType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfSetType(aSetType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		//  Answer the most general type that is still at least as specific as these.  Make it exact first.

		return object.becomeExactType().typeIntersectionOfTupleType(aTupleType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		//  Answer the most specific type that still includes both of these.  Make it exact first.

		return object.becomeExactType().typeUnion(another);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfClosureType(aClosureType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfContainerType(aContainerType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfCyclicType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCyclicType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfCyclicType(aCyclicType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfMapType(aMapType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfObjectMeta(anObjectMeta);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfObjectType(anObjectType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfSetType(aSetType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.becomeExactType().typeUnionOfTupleType(aTupleType);
	}

	@Override
	public @NotNull AvailObject o_FieldTypeMap (
		final @NotNull AvailObject object)
	{
		return object.becomeExactType().fieldTypeMap();
	}

	// operations-integer range

	@Override
	public @NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return object.instance();
		}
		return object.becomeExactType().lowerBound();
	}

	@Override
	public boolean o_LowerInclusive (
		final @NotNull AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return true;
		}
		return object.becomeExactType().lowerInclusive();
	}

	@Override
	public @NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return object.instance();
		}
		return object.becomeExactType().upperBound();
	}

	@Override
	public boolean o_UpperInclusive (
		final @NotNull AvailObject object)
	{
		if (object.instance().isExtendedInteger())
		{
			return true;
		}
		return object.becomeExactType().upperInclusive();
	}

	// operations-tupleType

	@Override
	public @NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
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
		if (1 <= index && index <= object.instance().tupleSize())
		{
			return object.instance().tupleAt(index).type();
		}
		return TERMINATES.o();
	}

	@Override
	public @NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		//  Answer the union of the types that object's instances could have in the
		//  given range of indices.  Out-of-range indices are treated as terminates,
		//  which don't affect the union (unless all indices are out of range).

		assert startIndex <= endIndex;
		assert object.isTupleType();
		if (startIndex == endIndex)
		{
			return object.typeAtIndex(startIndex);
		}
		if (endIndex <= 0)
		{
			return TERMINATES.o();
		}
		final AvailObject tupleObject = object.instance();
		final int upperIndex = tupleObject.tupleSize();
		if (startIndex > upperIndex)
		{
			return TERMINATES.o();
		}
		AvailObject unionType = TERMINATES.o();
		for (int i = max(startIndex, 1), _end1 = min(endIndex, upperIndex); i <= _end1; i++)
		{
			unionType = unionType.typeUnion(tupleObject.tupleAt(i).type());
		}
		return unionType;
	}

	@Override
	public @NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object)
	{
		if (!object.isTupleType())
		{
			error("Don't send defaultType to a non-tupleType", object);
			return VoidDescriptor.voidObject();
		}
		if (object.instance().tupleSize() == 0)
		{
			return TERMINATES.o();
		}
		return object.instance().tupleAt(object.instance().tupleSize()).type();
	}

	@Override
	public @NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object)
	{
		if (!object.isTupleType())
		{
			error("Don't send sizeRange to a non-tupleType", object);
			return VoidDescriptor.voidObject();
		}
		return IntegerDescriptor.fromInt(object.instance().tupleSize()).type();
	}

	@Override
	public @NotNull AvailObject o_TypeTuple (
		final @NotNull AvailObject object)
	{
		//  I can't keep pretending forever.  The encapsulation of tupleType has leaked enough
		//  already.  Fault in the real type.

		return object.becomeExactType().typeTuple();
	}

	// operations-types

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return object.instance().isInstanceOfSubtypeOf(aType);
	}

	@Override
	public boolean o_IsIntegerRangeType (
		final @NotNull AvailObject object)
	{
		return object.instance().isExtendedInteger();
	}

	@Override
	public boolean o_IsMapType (
		final @NotNull AvailObject object)
	{
		return object.instance().isMap();
	}

	@Override
	public boolean o_IsSetType (
		final @NotNull AvailObject object)
	{
		return object.instance().isSet();
	}

	@Override
	public boolean o_IsTupleType (
		final @NotNull AvailObject object)
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
	static AvailObject withInstance (final AvailObject instance)
	{
		AvailObject result = mutable().create();
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
	private final static ApproximateTypeDescriptor mutable = new ApproximateTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ApproximateTypeDescriptor}.
	 *
	 * @return The mutable {@link ApproximateTypeDescriptor}.
	 */
	public static ApproximateTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ApproximateTypeDescriptor}.
	 */
	private final static ApproximateTypeDescriptor immutable = new ApproximateTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ApproximateTypeDescriptor}.
	 *
	 * @return The immutable {@link ApproximateTypeDescriptor}.
	 */
	public static ApproximateTypeDescriptor immutable ()
	{
		return immutable;
	}
}
