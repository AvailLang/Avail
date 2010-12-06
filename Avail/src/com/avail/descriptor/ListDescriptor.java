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

public class ListDescriptor extends Descriptor
{

	public enum ObjectSlots
	{
		TUPLE
	}


	// GENERATED accessors

	/**
	 * Setter for field tuple.
	 */
	@Override
	public void o_Tuple (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.TUPLE, value);
	}

	/**
	 * Getter for field tuple.
	 */
	@Override
	public AvailObject o_Tuple (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.TUPLE);
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
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
	public boolean o_Equals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsList(object);
	}

	@Override
	public boolean o_EqualsList (
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
	public boolean o_IsInstanceOfSubtypeOf (
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
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return ListTypeDescriptor.listTypeForTupleType(object.tuple().type());
	}

	@Override
	public int o_Hash (
			final AvailObject object)
	{
		//  Answer the object's hash.

		return ((object.tuple().hash() + 0x3622137) ^ 0x7896120);
	}

	@Override
	public boolean o_IsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return object.tuple().isHashAvailable();
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		//  Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}



	// operations-lists

	@Override
	public boolean o_IsList (
			final AvailObject object)
	{
		return true;
	}

	/**
	 * Construct a new {@link ListDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ListDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ListDescriptor}.
	 */
	private final static ListDescriptor mutableDescriptor = new ListDescriptor(true);

	/**
	 * Answer the mutable {@link ListDescriptor}.
	 *
	 * @return The mutable {@link ListDescriptor}.
	 */
	public static ListDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link ListDescriptor}.
	 */
	private final static ListDescriptor immutableDescriptor = new ListDescriptor(false);

	/**
	 * Answer the immutable {@link ListDescriptor}.
	 *
	 * @return The immutable {@link ListDescriptor}.
	 */
	public static ListDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
