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

public class ContainerDescriptor extends Descriptor
{

	enum IntegerSlots
	{
		hashOrZero
	}

	enum ObjectSlots
	{
		value,
		type
	}


	// GENERATED accessors

	/**
	 * Setter for field hashOrZero.
	 */
	@Override
	public void ObjectHashOrZero (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.hashOrZero, value);
	}

	/**
	 * Setter for field type.
	 */
	@Override
	public void ObjectType (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.type, value);
	}

	/**
	 * Setter for field value.
	 */
	@Override
	public void ObjectValue (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.value, value);
	}

	/**
	 * Getter for field hashOrZero.
	 */
	@Override
	public int ObjectHashOrZero (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.hashOrZero);
	}

	/**
	 * Getter for field type.
	 */
	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.type);
	}

	/**
	 * Getter for field value.
	 */
	@Override
	public AvailObject ObjectValue (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.value);
	}



	// GENERATED special mutable slots

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
			final Enum<?> e)
	{
		//  GENERATED special mutable slots method.

		if (e == ObjectSlots.value)
		{
			return true;
		}
		if (e == IntegerSlots.hashOrZero)
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
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ContainerDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ContainerDescriptor}.
	 */
	private final static ContainerDescriptor mutableDescriptor = new ContainerDescriptor(true);

	/**
	 * Answer the mutable {@link ContainerDescriptor}.
	 *
	 * @return The mutable {@link ContainerDescriptor}.
	 */
	public static ContainerDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link ContainerDescriptor}.
	 */
	private final static ContainerDescriptor immutableDescriptor = new ContainerDescriptor(false);

	/**
	 * Answer the immutable {@link ContainerDescriptor}.
	 *
	 * @return The immutable {@link ContainerDescriptor}.
	 */
	public static ContainerDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
