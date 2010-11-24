/**
 * descriptor/PrimitiveTypeDescriptor.java
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

@IntegerSlots("hash")
@ObjectSlots({
	"name",
	"parent",
	"myType"
})
public class PrimitiveTypeDescriptor extends TypeDescriptor
{


	// GENERATED accessors

	/**
	 * Setter for field hash.
	 */
	@Override
	public void ObjectHash (
			final AvailObject object,
			final int value)
	{
		object.integerSlotAtByteIndexPut(4, value);
	}

	/**
	 * Setter for field myType.
	 */
	@Override
	public void ObjectMyType (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-12, value);
	}

	/**
	 * Setter for field name.
	 */
	@Override
	public void ObjectName (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Setter for field parent.
	 */
	@Override
	public void ObjectParent (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-8, value);
	}

	/**
	 * Getter for field hash.
	 */
	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(4);
	}

	/**
	 * Getter for field myType.
	 */
	@Override
	public AvailObject ObjectMyType (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-12);
	}

	/**
	 * Getter for field name.
	 */
	@Override
	public AvailObject ObjectName (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}

	/**
	 * Getter for field parent.
	 */
	@Override
	public AvailObject ObjectParent (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-8);
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object,
			final StringBuilder aStream,
			final List<AvailObject> recursionList,
			final int indent)
	{
		aStream.append(object.name().asNativeString());
	}



	// operations

	@Override
	public boolean ObjectEquals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsPrimitiveType(object);
	}

	@Override
	public boolean ObjectEqualsPrimitiveType (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Primitive types compare by identity.

		return object.sameAddressAs(aType);
	}



	// operations-types

	@Override
	public boolean ObjectIsSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfPrimitiveType(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		//  This primitive type is a supertype of aClosureType if and only if this
		//  primitive type is a supertype of 'closure'.

		return Types.closure.object().isSubtypeOf(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		//  A primitive type is a supertype of a container type if it is a supertype of container.

		return Types.container.object().isSubtypeOf(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfContinuationType (
			final AvailObject object,
			final AvailObject aContinuationType)
	{
		//  A primitive type is a supertype of a continuation type if it is a supertype of continuation.

		return Types.continuation.object().isSubtypeOf(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		//  Only the primitive type 'cyclicType' and its ancestors are ancestors of a cyclic type.

		return Types.cyclicType.object().isSubtypeOf(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  This primitive type is a supertype of aGeneralizedClosureType if and only if this
		//  primitive type is a supertype of all, the parent of '[...]->void'.

		return Types.all.object().isSubtypeOf(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		//  Parent of the top integer range type is all, so continue searching there.

		return Types.all.object().isSubtypeOf(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		//  A list type's supertypes are all instances of listType until they reach void.  Note that
		//  'all' is not in the hierarchy.  Thus object, a primitive type, is a supertype of aListType iff
		//  object is a supertype of (i.e., equal to) void.

		return Types.voidType.object().isSubtypeOf(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		//  This primitive type is a supertype of aMapType if and only if this
		//  primitive type is a supertype of 'all'.

		return Types.all.object().isSubtypeOf(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfObjectMeta (
			final AvailObject object,
			final AvailObject anObjectMeta)
	{
		//  Check if I'm a supertype of the given lazy object meta.  Only type and its
		//  ancestors are supertypes of an object meta.

		return Types.type.object().isSubtypeOf(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfObjectMetaMeta (
			final AvailObject object,
			final AvailObject anObjectMetaMeta)
	{
		//  Check if I'm a supertype of the given object meta meta.  Only meta and its
		//  ancestors are supertypes of an object meta meta.

		return Types.meta.object().isSubtypeOf(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfObjectType (
			final AvailObject object,
			final AvailObject anEagerObjectType)
	{
		//  Check if I'm a supertype of the given eager object type.  Only all and its
		//  ancestors are supertypes of an object type.

		return Types.all.object().isSubtypeOf(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfPrimitiveType (
			final AvailObject object,
			final AvailObject aPrimitiveType)
	{
		//  Check if object (a primitive type) is a supertype of aPrimitiveType (also a primitive type).

		if (object.equals(aPrimitiveType))
		{
			return true;
		}
		return aPrimitiveType.parent().isSubtypeOf(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		//  This primitive type is a supertype of aSetType if and only if this
		//  primitive type is a supertype of 'all'.

		return Types.all.object().isSubtypeOf(object);
	}

	@Override
	public boolean ObjectIsSupertypeOfTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		//  This primitive type is a supertype of aTupleType if and only if this
		//  primitive type is a supertype of 'all'.

		return Types.all.object().isSubtypeOf(object);
	}

	@Override
	public AvailObject ObjectTypeIntersection (
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
		if ((object.isSubtypeOf(Types.type.object()) && another.isSubtypeOf(Types.type.object())))
		{
			return Types.terminatesType.object();
		}
		return Types.terminates.object();
	}

	@Override
	public AvailObject ObjectTypeUnion (
			final AvailObject object,
			final AvailObject another)
	{
		//  Answer the most specific type that still includes both of these.

		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		return object.parent().typeUnion(another);
	}

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.

		return object.myType();
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer object's type.

		return object.myType();
	}

	/**
	 * Construct a new {@link PrimitiveTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected PrimitiveTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The descriptor instance that describes a mutable primitive type.
	 */
	final private static PrimitiveTypeDescriptor mutableDescriptor = new PrimitiveTypeDescriptor(true);

	/**
	 * Answer the descriptor instance that describes a mutable primitive type.
	 * 
	 * @return a PrimitiveTypeDescriptor for mutable objects.
	 */
	public static PrimitiveTypeDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The descriptor instance that describes an immutable primitive type.
	 */
	final private static PrimitiveTypeDescriptor immutableDescriptor = new PrimitiveTypeDescriptor(false);

	/**
	 * Answer the descriptor instance that describes an immutable primitive
	 * type.
	 * 
	 * @return a PrimitiveTypeDescriptor for immutable objects.
	 */
	public static PrimitiveTypeDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
