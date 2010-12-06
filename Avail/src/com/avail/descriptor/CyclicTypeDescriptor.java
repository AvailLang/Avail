/**
 * descriptor/CyclicTypeDescriptor.java
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
import com.avail.descriptor.TypeDescriptor;
import java.util.List;
import java.util.Random;

public class CyclicTypeDescriptor extends TypeDescriptor
{

	public enum IntegerSlots
	{
		HASH_OR_ZERO
	}

	public enum ObjectSlots
	{
		NAME
	}


	/**
	 * Setter for field hashOrZero.
	 */
	@Override
	public void o_HashOrZero (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.HASH_OR_ZERO, value);
	}

	/**
	 * Setter for field name.
	 */
	@Override
	public void o_Name (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.NAME, value);
	}

	/**
	 * Getter for field hashOrZero.
	 */
	@Override
	public int o_HashOrZero (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HASH_OR_ZERO);
	}

	/**
	 * Getter for field name.
	 */
	@Override
	public AvailObject o_Name (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NAME);
	}



	@Override
	public boolean allowsImmutableToMutableReferenceInField (
			final Enum<?> e)
	{
		if (e == IntegerSlots.HASH_OR_ZERO)
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
		aStream.append('$');
		String nativeName = object.name().asNativeString();
		if (!nativeName.matches("\\w+"))
		{
			aStream.append('"');
			aStream.append(nativeName);
			aStream.append('"');
		}
		else
		{
			aStream.append(nativeName);
		}
	}



	// operations

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		//  The neat thing about cyclic types is that they're their own types.  The
		//  problem is that when you ask its type it has to become immutable in
		//  case the original (the 'instance') is also being held onto.

		return object.makeImmutable();
	}

	@Override
	public int o_Hash (
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
	public AvailObject o_Type (
			final AvailObject object)
	{
		//  The neat thing about cyclic types is that they're their own types.  The
		//  problem is that when you ask its type it has to become immutable in
		//  case the original (the 'instance') is also being held onto.

		return object.makeImmutable();
	}



	// operations-types

	@Override
	public boolean o_IsSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfCyclicType(object);
	}

	@Override
	public boolean o_IsSupertypeOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		//  Two cyclic types are identical if and only if they are at the same address in
		//  memory (i.e., after traversal of indirections they are the same object under ==).
		//  This means cyclic types have identity, so the hash should be a random value
		//  for good distribution, especially when many cyclic types have the same name.

		return object.sameAddressAs(aCyclicType);
	}

	@Override
	public AvailObject o_TypeIntersection (
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
		return another.typeIntersectionOfCyclicType(object);
	}

	@Override
	public AvailObject o_TypeIntersectionOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		//  Answer the most general type that is still at least as specific as these.

		if (object.sameAddressAs(aCyclicType))
		{
			return object;
		}
		return Types.terminatesType.object();
	}

	@Override
	public AvailObject o_TypeIntersectionOfMeta (
			final AvailObject object,
			final AvailObject someMeta)
	{
		//  Answer the most general type that is still at least as specific as these.
		//  Since metas intersect at terminatesType rather than terminates, we must
		//  be very careful to overide this properly.  Note that the cases of the types
		//  being equal or one being a subtype of the other have already been dealt
		//  with (in Object:typeIntersection:), so don't test for them here.

		return Types.terminatesType.object();
	}

	@Override
	public AvailObject o_TypeUnion (
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
		return another.typeUnionOfCyclicType(object);
	}

	@Override
	public AvailObject o_TypeUnionOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		//  Answer the most specific type that is still at least as general as these.

		if (object.sameAddressAs(aCyclicType))
		{
			return object;
		}
		return Types.cyclicType.object();
	}

	@Override
	public boolean o_IsCyclicType (
			final AvailObject object)
	{
		return true;
	}





	/* Object creation */
	public static AvailObject newCyclicTypeWithName (AvailObject aTupleObject)
	{
		aTupleObject.makeImmutable();
		AvailObject cyc = AvailObject.newIndexedDescriptor (
			0,
			CyclicTypeDescriptor.mutableDescriptor());
		cyc.name(aTupleObject);
		cyc.hashOrZero(0);
		cyc.makeImmutable();
		return cyc;
	};

	/**
	 * A random generator used for creating hash values as needed.
	 */
	static Random hashGenerator = new Random();

	/**
	 * Construct a new {@link CyclicTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected CyclicTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/* Descriptor lookup */
	private final static CyclicTypeDescriptor mutableDescriptor = new CyclicTypeDescriptor(true);

	/**
	 * Answer the mutable {@link CyclicTypeDescriptor}.
	 *
	 * @return The mutable {@link CyclicTypeDescriptor}.
	 */
	public static CyclicTypeDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link CyclicTypeDescriptor}.
	 */
	private final static CyclicTypeDescriptor immutableDescriptor = new CyclicTypeDescriptor(false);

	/**
	 * Answer the immutable {@link CyclicTypeDescriptor}.
	 *
	 * @return The immutable {@link CyclicTypeDescriptor}.
	 */
	public static CyclicTypeDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
