/**
 * descriptor/ContainerDescriptor.java
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

	void ObjectHashOrZero (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(4, value);
	}

	void ObjectType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-8, value);
	}

	void ObjectValue (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	int ObjectHashOrZero (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(4);
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-8);
	}

	AvailObject ObjectValue (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-4);
	}



	// GENERATED special mutable slots

	boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		if ((index == -4))
		{
			return true;
		}
		if ((index == 4))
		{
			return true;
		}
		return false;
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsContainer(object);
	}

	boolean ObjectEqualsContainer (
			final AvailObject object, 
			final AvailObject aContainer)
	{
		//  Containers compare by address (Smalltalk object identity).  No need to traverse the
		//  objects before comparing addresses, because this message was a double-dispatch
		//  that would have skipped (and stripped) the indirection objects in either path.

		return object.sameAddressAs(aContainer);
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.

		return object.type();
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value.

		int hash = object.hashOrZero();
		while (hash == 0)
		{
			hash = (hashGenerator.nextInt()) & HashMask;
		}
		object.hashOrZero(hash);
		return hash;
	}

	AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  If I am being frozen (a container), I don't need to freeze my current value.
		//  I do, on the other hand, have to freeze my type object.

		object.descriptor(ContainerDescriptor.immutableDescriptor());
		object.type().makeImmutable();
		return object;
	}



	// operations-containers

	void ObjectInnerType (
			final AvailObject object, 
			final AvailObject innerType)
	{
		//  Initialize my type based on the given inner type.

		object.type(ContainerTypeDescriptor.containerTypeForInnerType(innerType));
	}

	void ObjectSetValue (
			final AvailObject object, 
			final AvailObject newValue)
	{
		assert newValue.isInstanceOfSubtypeOf(object.type().innerType()) : "Container can't hold that value (wrong type)";
		object.value(newValue);
	}

	void ObjectClearValue (
			final AvailObject object)
	{
		//  Clears the container (makes it have no current value).

		//  Eventually, the previous contents should drop a reference.
		object.value(VoidDescriptor.voidObject());
	}

	AvailObject ObjectGetValue (
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

	void ObjectReleaseVariableOrMakeContentsImmutable (
			final AvailObject object)
	{
		//  If I'm mutable, release me.  Otherwise make my contents immutable.  This
		//  is used when a variable is being read 'for the last time', but it's unknown
		//  whether the variable has already been shared.

		if (_isMutable)
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

	static Random hashGenerator = new Random();


	/* Descriptor lookup */
	public static ContainerDescriptor mutableDescriptor()
	{
		return (ContainerDescriptor) AllDescriptors [34];
	};
	public static ContainerDescriptor immutableDescriptor()
	{
		return (ContainerDescriptor) AllDescriptors [35];
	};

}
