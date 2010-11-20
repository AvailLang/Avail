/**
 * descriptor/ContainerDescriptor.java
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
import com.avail.descriptor.ContainerDescriptor;
import com.avail.descriptor.ContainerTypeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import java.util.Random;
import static com.avail.descriptor.AvailObject.*;

@IntegerSlots("hashOrZero")
@ObjectSlots({
	"value", 
	"type"
})
public class ContainerDescriptor extends Descriptor
{


	// GENERATED accessors

	/**
	 * Setter for field !H!ashOrZero.
	 */
	@Override
	public void ObjectHashOrZero (
			final AvailObject object, 
			final int value)
	{
		object.integerSlotAtByteIndexPut(4, value);
	}

	/**
	 * Setter for field !T!ype.
	 */
	@Override
	public void ObjectType (
			final AvailObject object, 
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-8, value);
	}

	/**
	 * Setter for field !V!alue.
	 */
	@Override
	public void ObjectValue (
			final AvailObject object, 
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Getter for field !H!ashOrZero.
	 */
	@Override
	public int ObjectHashOrZero (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(4);
	}

	/**
	 * Getter for field !T!ype.
	 */
	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-8);
	}

	/**
	 * Getter for field !V!alue.
	 */
	@Override
	public AvailObject ObjectValue (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}



	// GENERATED special mutable slots

	@Override
	boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		if (index == -4)
		{
			return true;
		}
		if (index == 4)
		{
			return true;
		}
		return false;
	}



	// operations

	@Override
	public boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsContainer(object);
	}

	@Override
	public boolean ObjectEqualsContainer (
			final AvailObject object, 
			final AvailObject aContainer)
	{
		//  Containers compare by address (Smalltalk object identity).  No need to traverse the
		//  objects before comparing addresses, because this message was a double-dispatch
		//  that would have skipped (and stripped) the indirection objects in either path.

		return object.sameAddressAs(aContainer);
	}

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.

		return object.type();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value.

		int hash = object.hashOrZero();
		while (hash == 0)
		{
			hash = hashGenerator.nextInt();
		}
		object.hashOrZero(hash);
		return hash;
	}

	@Override
	public AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  If I am being frozen (a container), I don't need to freeze my current value.
		//  I do, on the other hand, have to freeze my type object.

		object.descriptor(ContainerDescriptor.immutableDescriptor());
		object.type().makeImmutable();
		return object;
	}



	// operations-containers

	@Override
	public void ObjectInnerType (
			final AvailObject object, 
			final AvailObject innerType)
	{
		//  Initialize my type based on the given inner type.

		object.type(ContainerTypeDescriptor.containerTypeForInnerType(innerType));
	}

	@Override
	public void ObjectSetValue (
			final AvailObject object, 
			final AvailObject newValue)
	{
		assert newValue.isInstanceOfSubtypeOf(object.type().innerType()) : "Container can't hold that value (wrong type)";
		object.value(newValue);
	}

	@Override
	public void ObjectClearValue (
			final AvailObject object)
	{
		//  Clears the container (makes it have no current value).

		//  Eventually, the previous contents should drop a reference.
		object.value(VoidDescriptor.voidObject());
	}

	@Override
	public AvailObject ObjectGetValue (
			final AvailObject object)
	{
		//  Answer the current value of the container.  Fail if no value assigned.

		final AvailObject value = object.value();
		if (value.equalsVoid())
		{
			error("That container has no value yet", object);
			return VoidDescriptor.voidObject();
		}
		return value;
	}

	@Override
	public void ObjectReleaseVariableOrMakeContentsImmutable (
			final AvailObject object)
	{
		//  If I'm mutable, release me.  Otherwise make my contents immutable.  This
		//  is used when a variable is being read 'for the last time', but it's unknown
		//  whether the variable has already been shared.

		if (isMutable)
		{
			object.assertObjectUnreachableIfMutableExcept(object.value());
		}
		else
		{
			object.value().makeImmutable();
		}
	}





	/* Object creation */

	public static AvailObject newContainerWithInnerType (AvailObject innerType)
	{
		return ContainerDescriptor.newContainerWithOuterType(
			ContainerTypeDescriptor.containerTypeForInnerType(innerType));
	};

	public static AvailObject newContainerWithOuterType (AvailObject outerType)
	{
		AvailObject result = AvailObject.newIndexedDescriptor (
			0,
			ContainerDescriptor.mutableDescriptor ());
		result.type (outerType);
		result.hashOrZero (0);
		result.value (VoidDescriptor.voidObject());
		return result;
	};

	private static Random hashGenerator = new Random();

	/**
	 * Construct a new {@link ContainerDescriptor}.
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
	protected ContainerDescriptor (
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

	public static ContainerDescriptor mutableDescriptor()
	{
		return (ContainerDescriptor) allDescriptors [34];
	}

	public static ContainerDescriptor immutableDescriptor()
	{
		return (ContainerDescriptor) allDescriptors [35];
	}
}
