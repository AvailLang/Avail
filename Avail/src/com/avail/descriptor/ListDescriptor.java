/**
 * descriptor/ListDescriptor.java
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

import com.avail.descriptor.ApproximateTypeDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ListTypeDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;

import java.util.List;

@ObjectSlots("tuple")
public class ListDescriptor extends Descriptor
{


	// GENERATED accessors

	/**
	 * Setter for field !T!uple.
	 */
	@Override
	public void ObjectTuple (
			final AvailObject object, 
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Getter for field !T!uple.
	 */
	@Override
	public AvailObject ObjectTuple (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}



	// java printing

	@Override
	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		aStream.append("(");
		object.tuple().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		aStream.append(") as list");
	}



	// operations

	@Override
	public boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsList(object);
	}

	@Override
	public boolean ObjectEqualsList (
			final AvailObject object, 
			final AvailObject aList)
	{
		if (object.sameAddressAs(aList))
		{
			return true;
		}
		//  Compare the embedded tuple.  The contentType must be equal if the tuples are.
		return object.tuple().equals(aList.tuple());
	}

	@Override
	public boolean ObjectIsInstanceOfSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Answer whether object is an instance of a subtype of aType.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.

		if (aType.equals(Types.voidType.object()))
		{
			return true;
		}
		if (!aType.isListType())
		{
			return false;
		}
		return object.tuple().isInstanceOfSubtypeOf(aType.tupleType());
	}

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return ListTypeDescriptor.listTypeForTupleType(object.tuple().type());
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer the object's hash.

		return ((object.tuple().hash() + 0x3622137) ^ 0x7896120);
	}

	@Override
	public boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return object.tuple().isHashAvailable();
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}



	// operations-lists

	@Override
	public boolean ObjectIsList (
			final AvailObject object)
	{
		return true;
	}

	/**
	 * Construct a new {@link ListDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param numberOfFixedObjectSlots
	 *        The number of fixed {@linkplain AvailObject object} slots.
	 * @param numberOfFixedIntegerSlots The number of fixed integer slots.
	 * @param hasVariableObjectSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable object slots?
	 * @param hasVariableIntegerSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable integer slots?
	 */
	protected ListDescriptor (
		final int myId,
		final boolean isMutable,
		final int numberOfFixedObjectSlots,
		final int numberOfFixedIntegerSlots,
		final boolean hasVariableObjectSlots,
		final boolean hasVariableIntegerSlots)
	{
		super(
			myId,
			isMutable,
			numberOfFixedObjectSlots,
			numberOfFixedIntegerSlots,
			hasVariableObjectSlots,
			hasVariableIntegerSlots);
	}

	public static ListDescriptor mutableDescriptor()
	{
		return (ListDescriptor) allDescriptors [100];
	}

	public static ListDescriptor immutableDescriptor()
	{
		return (ListDescriptor) allDescriptors [101];
	}
}
