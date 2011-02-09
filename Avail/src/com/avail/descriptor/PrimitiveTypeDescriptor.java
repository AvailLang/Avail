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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.NotNull;

public class PrimitiveTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		HASH
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		NAME,
		PARENT,
		MY_TYPE
	}

	@Override
	public void o_Hash (
		final @NotNull AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.HASH, value);
	}

	@Override
	public void o_MyType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.MY_TYPE, value);
	}

	@Override
	public void o_Name (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.NAME, value);
	}

	@Override
	public void o_Parent (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.PARENT, value);
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HASH);
	}

	@Override
	public @NotNull AvailObject o_MyType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.MY_TYPE);
	}

	@Override
	public @NotNull AvailObject o_Name (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NAME);
	}

	@Override
	public @NotNull AvailObject o_Parent (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.PARENT);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append(object.name().asNativeString());
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsPrimitiveType(object);
	}

	@Override
	public boolean o_EqualsPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		//  Primitive types compare by identity.

		return object.sameAddressAs(aType);
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfPrimitiveType(object);
	}

	@Override
	public boolean o_IsSupertypeOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		//  This primitive type is a supertype of aClosureType if and only if this
		//  primitive type is a supertype of 'closure'.

		return CLOSURE.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		//  A primitive type is a supertype of a container type if it is a supertype of container.

		return CONTAINER.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		//  A primitive type is a supertype of a continuation type if it is a supertype of continuation.

		return CONTINUATION.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfCyclicType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCyclicType)
	{
		//  Only the primitive type 'cyclicType' and its ancestors are ancestors of a cyclic type.

		return CYCLIC_TYPE.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		//  This primitive type is a supertype of aGeneralizedClosureType if and only if this
		//  primitive type is a supertype of all, the parent of '[...]->void'.

		return ALL.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		//  Parent of the top integer range type is all, so continue searching there.

		return ALL.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		//  This primitive type is a supertype of aMapType if and only if this
		//  primitive type is a supertype of 'all'.

		return ALL.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		//  Check if I'm a supertype of the given lazy object meta.  Only type and its
		//  ancestors are supertypes of an object meta.

		return TYPE.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfObjectMetaMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMetaMeta)
	{
		//  Check if I'm a supertype of the given object meta meta.  Only meta and its
		//  ancestors are supertypes of an object meta meta.

		return META.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anEagerObjectType)
	{
		//  Check if I'm a supertype of the given eager object type.  Only all and its
		//  ancestors are supertypes of an object type.

		return ALL.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPrimitiveType)
	{
		//  Check if object (a primitive type) is a supertype of aPrimitiveType (also a primitive type).

		if (object.equals(aPrimitiveType))
		{
			return true;
		}
		return aPrimitiveType.parent().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		//  This primitive type is a supertype of aSetType if and only if this
		//  primitive type is a supertype of 'all'.

		return ALL.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		//  This primitive type is a supertype of aTupleType if and only if this
		//  primitive type is a supertype of 'all'.

		return ALL.o().isSubtypeOf(object);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
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
		if (object.isSubtypeOf(TYPE.o()) && another.isSubtypeOf(TYPE.o()))
		{
			return TERMINATES_TYPE.o();
		}
		return TERMINATES.o();
	}

	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
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
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.

		return object.myType();
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
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
	final private static PrimitiveTypeDescriptor mutable = new PrimitiveTypeDescriptor(true);

	/**
	 * Answer the descriptor instance that describes a mutable primitive type.
	 *
	 * @return a PrimitiveTypeDescriptor for mutable objects.
	 */
	public static PrimitiveTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The descriptor instance that describes an immutable primitive type.
	 */
	final private static PrimitiveTypeDescriptor immutable = new PrimitiveTypeDescriptor(false);

	/**
	 * Answer the descriptor instance that describes an immutable primitive
	 * type.
	 *
	 * @return a PrimitiveTypeDescriptor for immutable objects.
	 */
	public static PrimitiveTypeDescriptor immutable ()
	{
		return immutable;
	}
}
