/**
 * descriptor/MessageBundleDescriptor.java
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
import com.avail.descriptor.SetDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.VoidDescriptor;
import java.util.List;

@ObjectSlots({
	"message", 
	"messageParts", 
	"myRestrictions"
})
public class MessageBundleDescriptor extends Descriptor
{


	// accessing

	@Override
	public void ObjectAddRestrictions (
			final AvailObject object, 
			final AvailObject restrictions)
	{
		assert restrictions.isTuple();
		restrictions.makeImmutable();
		AvailObject merged = object.myRestrictions();
		if (merged.equalsVoid())
		{
			object.myRestrictions(restrictions);
			return;
		}
		for (int i = 1, _end1 = merged.tupleSize(); i <= _end1; i++)
		{
			merged = merged.tupleAtPuttingCanDestroy(
				i,
				merged.tupleAt(i).setUnionCanDestroy(restrictions.tupleAt(i), true),
				true);
		}
		object.myRestrictions(merged);
	}

	@Override
	public void ObjectRemoveRestrictions (
			final AvailObject object, 
			final AvailObject obsoleteRestrictions)
	{
		assert obsoleteRestrictions.isTuple();
		AvailObject reduced = object.myRestrictions();
		if (reduced.equals(obsoleteRestrictions))
		{
			object.myRestrictions(VoidDescriptor.voidObject());
			return;
		}
		for (int i = 1, _end1 = reduced.tupleSize(); i <= _end1; i++)
		{
			reduced = reduced.tupleAtPuttingCanDestroy(
				i,
				reduced.tupleAt(i).setMinusCanDestroy(obsoleteRestrictions.tupleAt(i), true),
				true);
		}
		object.myRestrictions(reduced);
	}

	@Override
	public boolean ObjectHasRestrictions (
			final AvailObject object)
	{
		if (object.myRestrictions().equalsVoid())
		{
			return false;
		}
		for (int i = 1, _end1 = object.myRestrictions().tupleSize(); i <= _end1; i++)
		{
			if ((object.myRestrictions().tupleAt(i).setSize() > 0))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public void ObjectRemoveRestrictions (
			final AvailObject object)
	{
		object.myRestrictions(VoidDescriptor.voidObject());
	}

	@Override
	public AvailObject ObjectRestrictions (
			final AvailObject object)
	{
		AvailObject restrictions = object.myRestrictions();
		if (restrictions.equalsVoid())
		{
			final AvailObject parts = object.messageParts();
			int count = 0;
			for (int partIndex = 1, _end1 = parts.tupleSize(); partIndex <= _end1; partIndex++)
			{
				final AvailObject part = parts.tupleAt(partIndex);
				if (part.equals(TupleDescriptor.underscoreTuple()))
				{
					count++;
				}
			}
			restrictions = parts.copyTupleFromToCanDestroy(
				1,
				count,
				false);
			for (int index = 1; index <= count; index++)
			{
				restrictions = restrictions.tupleAtPuttingCanDestroy(
					index,
					SetDescriptor.empty(),
					true);
			}
			object.myRestrictions(restrictions.makeImmutable());
		}
		return restrictions;
	}



	// GENERATED accessors

	/**
	 * Setter for field message.
	 */
	@Override
	public void ObjectMessage (
			final AvailObject object, 
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Setter for field messageParts.
	 */
	@Override
	public void ObjectMessageParts (
			final AvailObject object, 
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-8, value);
	}

	/**
	 * Setter for field myRestrictions.
	 */
	@Override
	public void ObjectMyRestrictions (
			final AvailObject object, 
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-12, value);
	}

	/**
	 * Getter for field message.
	 */
	@Override
	public AvailObject ObjectMessage (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}

	/**
	 * Getter for field messageParts.
	 */
	@Override
	public AvailObject ObjectMessageParts (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-8);
	}

	/**
	 * Getter for field myRestrictions.
	 */
	@Override
	public AvailObject ObjectMyRestrictions (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-12);
	}



	// GENERATED special mutable slots

	@Override
	public boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		if (index == -12)
		{
			return true;
		}
		return false;
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		//  The existing implementations are also printed in parentheses to help distinguish
		//  polymorphism from occurrences of non-polymorphic homonyms.

		if (isMutable)
		{
			aStream.append("(mut)");
		}
		aStream.append("bundle\"");
		aStream.append(object.message().name().asNativeString());
		aStream.append("\"");
	}



	// operations

	@Override
	public boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.

		return Types.messageBundle.object();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value.

		return (object.message().hash() ^ 0x312CAB9);
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.messageBundle.object();
	}





	/* Object creation */
	static AvailObject newMessageParts(AvailObject message, AvailObject parts)
	{
		AvailObject result = AvailObject.newIndexedDescriptor(0, MessageBundleDescriptor.mutableDescriptor());
		result.message(message);
		result.messageParts(parts);
		result.myRestrictions(VoidDescriptor.voidObject());
		result.makeImmutable();
		return result;
	};

	/**
	 * Construct a new {@link MessageBundleDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected MessageBundleDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	final static MessageBundleDescriptor mutableDescriptor = new MessageBundleDescriptor(true);

	public static MessageBundleDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	final static MessageBundleDescriptor immutableDescriptor = new MessageBundleDescriptor(false);

	public static MessageBundleDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
