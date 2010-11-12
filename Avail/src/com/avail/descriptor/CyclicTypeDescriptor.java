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

@IntegerSlots("hashOrZero")
@ObjectSlots("name")
public class CyclicTypeDescriptor extends TypeDescriptor
{


	// GENERATED accessors

	void ObjectHashOrZero (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(4, value);
	}

	void ObjectName (
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

	AvailObject ObjectName (
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

		if ((index == 4))
		{
			return true;
		}
		return false;
	}



	// java printing

	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		aStream.append('$');
		String nativeName = object.name().asNativeString();
		if (! nativeName.matches("\\w+"))
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

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  The neat thing about cyclic types is that they're their own types.  The
		//  problem is that when you ask its type it has to become immutable in
		//  case the original (the 'instance') is also being held onto.

		return object.makeImmutable();
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

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  The neat thing about cyclic types is that they're their own types.  The
		//  problem is that when you ask its type it has to become immutable in
		//  case the original (the 'instance') is also being held onto.

		return object.makeImmutable();
	}



	// operations-types

	boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfCyclicType(object);
	}

	boolean ObjectIsSupertypeOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		//  Two cyclic types are identical if and only if they are at the same address in
		//  memory (i.e., after traversal of indirections they are the same object under ==).
		//  This means cyclic types have identity, so the hash should be a random value
		//  for good distribution, especially when many cyclic types have the same name.

		return object.sameAddressAs(aCyclicType);
	}

	AvailObject ObjectTypeIntersection (
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

	AvailObject ObjectTypeIntersectionOfCyclicType (
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

	AvailObject ObjectTypeIntersectionOfMeta (
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

	AvailObject ObjectTypeUnion (
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

	AvailObject ObjectTypeUnionOfCyclicType (
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

	boolean ObjectIsCyclicType (
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
			CyclicTypeDescriptor.immutableDescriptor());
		cyc.name(aTupleObject);
		cyc.hashOrZero(0);
		return cyc;
	};

	static Random hashGenerator = new Random();


	/* Descriptor lookup */
	public static CyclicTypeDescriptor mutableDescriptor()
	{
		return (CyclicTypeDescriptor) allDescriptors [42];
	};
	public static CyclicTypeDescriptor immutableDescriptor()
	{
		return (CyclicTypeDescriptor) allDescriptors [43];
	};

}
