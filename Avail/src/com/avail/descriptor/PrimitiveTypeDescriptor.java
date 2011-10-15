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
	public boolean o_IsSupertypeOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		//  This primitive type is a supertype of aFunctionType if and only if this
		//  primitive type is a supertype of ANY.

		return ANY.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		// A primitive type is a supertype of a container type if it is a
		// supertype of ANY.
		return ANY.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		// A primitive type is a supertype of a continuation type if it is a
		// supertype of ANY.
		return ANY.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		// A primitive type is a supertype of a compiled code type if it is a
		// supertype of ANY.
		return ANY.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		//  Parent of the top integer range type is all, so continue searching there.

		return ANY.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		//  This primitive type is a supertype of aMapType if and only if this
		//  primitive type is a supertype of ANY.

		return ANY.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anEagerObjectType)
	{
		//  Check if I'm a supertype of the given eager object type.  Only all and its
		//  ancestors are supertypes of an object type.

		return ANY.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		return ANY.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPrimitiveType)
	{
		// Check if object (a primitive type) is a supertype of aPrimitiveType
		// (also a primitive type).
		AvailObject type = aPrimitiveType;
		while (true)
		{
			if (object.equals(type))
			{
				return true;
			}
			type = type.parent();
			if (type.equalsNull())
			{
				return false;
			}
		}
	}

	@Override
	public boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		//  This primitive type is a supertype of aSetType if and only if this
		//  primitive type is a supertype of ANY.

		return ANY.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		//  This primitive type is a supertype of aTupleType if and only if this
		//  primitive type is a supertype of ANY.

		return ANY.o().isSubtypeOf(object);
	}

	@Override
	public boolean o_IsSupertypeOfUnionMeta (
		final @NotNull AvailObject object,
		final AvailObject aUnionMeta)
	{
		if (aUnionMeta.innerKind().isSubtypeOf(TYPE.o()))
		{
			return META.o().isSubtypeOf(object);
		}
		return TYPE.o().isSubtypeOf(object);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		if (object.isSubtypeOf(another))
		{
			return object;
		}
		if (another.isSubtypeOf(object))
		{
			return another;
		}
		if (object.equals(META.o()))
		{
			return another.typeIntersectionOfMeta(object);
		}
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		if (another.isAbstractUnionType())
		{
			// Note that at this point neither one can be bottom, since that
			// would always have been detected as a subtype of the other.
			assert !another.equals(BottomTypeDescriptor.bottom());
			return another.computeSuperkind().typeUnion(object);
		}
		return object.objectSlot(ObjectSlots.PARENT).typeUnion(another);
	}

	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.MY_TYPE);
	}

	/**
	 * Create a partially-initialized primitive type with the given name.  The
	 * type's parent and the type's myType will be set later, to allow circular
	 * constructions.  Set these fields to the {@linkplain NullDescriptor null
	 * object} to ensure pointer safety.
	 *
	 * @param typeNameString
	 *            The name to give the object being initialized.
	 * @return    The partially initialized type.
	 */
	AvailObject createPrimitiveObjectNamed (
		final String typeNameString)
	{
		final AvailObject name = ByteStringDescriptor.from(typeNameString);
		final AvailObject object = create();
		object.objectSlotPut(ObjectSlots.NAME, name);
		object.objectSlotPut(ObjectSlots.PARENT, NullDescriptor.nullObject());
		object.objectSlotPut(ObjectSlots.MY_TYPE, NullDescriptor.nullObject());
		object.integerSlotPut(IntegerSlots.HASH, typeNameString.hashCode());
		return object;
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
