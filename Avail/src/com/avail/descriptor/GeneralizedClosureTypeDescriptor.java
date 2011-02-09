/**
 * descriptor/GeneralizedClosureTypeDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.GENERALIZED_CLOSURE_TYPE;
import java.util.List;
import com.avail.annotations.NotNull;

public class GeneralizedClosureTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		RETURN_TYPE
	}

	@Override
	public void o_ReturnType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.RETURN_TYPE, value);
	}

	@Override
	public @NotNull AvailObject o_ReturnType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.RETURN_TYPE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append("[...]->");
		object.returnType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsGeneralizedClosureType(object);
	}

	@Override
	public boolean o_EqualsGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		//  Generalized closure types are equal iff they have the same return type.

		if (object.sameAddressAs(aType))
		{
			return true;
		}
		if (!object.returnType().equals(aType.returnType()))
		{
			return false;
		}
		object.becomeIndirectionTo(aType);
		//  They're equal but physically disjoint, so merge the objects.
		aType.makeImmutable();
		//  There are at least 2 references now.
		return true;
	}

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return GENERALIZED_CLOSURE_TYPE.o();
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		//  The hash value is always recomputed from the argTypeTuple and returnType.

		return object.returnType().hash() * 13 + 0x359991;
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return GENERALIZED_CLOSURE_TYPE.o();
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfGeneralizedClosureType(object);
	}

	@Override
	public boolean o_IsSupertypeOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		//  Closure types are contravariant by arguments and covariant by return type.  Since
		//  generalized closure types don't know anything about arguments, just compare the
		//  return types.

		return aClosureType.returnType().isSubtypeOf(object.returnType());
	}

	@Override
	public boolean o_IsSupertypeOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		//  Generalized closure types are covariant by return type.

		if (object.equals(aGeneralizedClosureType))
		{
			return true;
		}
		return aGeneralizedClosureType.returnType().isSubtypeOf(object.returnType());
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
		return another.typeIntersectionOfGeneralizedClosureType(object);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  The intersection
		//  of a closure type and a generalized closure type is always a closure type, so simply
		//  intersect the return types, and use the argument types verbatim.
		//
		//  Here we do something unusual - we invert the arguments again, and let aClosureType take
		//  its best shot at running the show.  It knows better how to deal with cloning aClosureType.

		return aClosureType.typeIntersectionOfGeneralizedClosureType(object);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  Respect
		//  the covariance of the return types.

		return GeneralizedClosureTypeDescriptor.forReturnType(object.returnType().typeIntersection(aGeneralizedClosureType.returnType()));
	}

	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
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
		return another.typeUnionOfGeneralizedClosureType(object);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.  Respect
		//  the covariance of the return types.  Discard the argument information, because
		//  the union of a generalized closure type and a closure type is always a generalized
		//  closure type.

		return GeneralizedClosureTypeDescriptor.forReturnType(object.returnType().typeUnion(aClosureType.returnType()));
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.  Respect
		//  the covariance of the return types.

		return GeneralizedClosureTypeDescriptor.forReturnType(object.returnType().typeUnion(aGeneralizedClosureType.returnType()));
	}

	public static @NotNull AvailObject forReturnType (
		final @NotNull AvailObject returnType)
	{
		AvailObject result = mutable().create();
		result.returnType(returnType);
		return result;
	};

	/**
	 * Construct a new {@link GeneralizedClosureTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected GeneralizedClosureTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link GeneralizedClosureTypeDescriptor}.
	 */
	private final static GeneralizedClosureTypeDescriptor mutable = new GeneralizedClosureTypeDescriptor(true);

	/**
	 * Answer the mutable {@link GeneralizedClosureTypeDescriptor}.
	 *
	 * @return The mutable {@link GeneralizedClosureTypeDescriptor}.
	 */
	public static GeneralizedClosureTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link GeneralizedClosureTypeDescriptor}.
	 */
	private final static GeneralizedClosureTypeDescriptor immutable = new GeneralizedClosureTypeDescriptor(false);

	/**
	 * Answer the immutable {@link GeneralizedClosureTypeDescriptor}.
	 *
	 * @return The immutable {@link GeneralizedClosureTypeDescriptor}.
	 */
	public static GeneralizedClosureTypeDescriptor immutable ()
	{
		return immutable;
	}
}
