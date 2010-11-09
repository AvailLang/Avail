/**
 * descriptor/TerminatesMetaDescriptor.java
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
import com.avail.descriptor.TypeDescriptor;

@IntegerSlots("hash")
@ObjectSlots({
	"name", 
	"parent", 
	"myType"
})
public class TerminatesMetaDescriptor extends PrimitiveTypeDescriptor
{


	// operations-types

	boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Check if object (terminatesType) is a subtype of aType (should also be a type).
		//  It's true if and only if aType inherits from type.  Well, actually it's true if aType
		//  is one of the ancestors of type as well (by substitutability).  Well, actually again,
		//  terminatesType is not a subtype of terminates, even though terminates is a meta
		//  (remember, terminates inherits from type and meta and every other type).

		if (aType.equals(TypeDescriptor.terminates()))
		{
			return false;
		}
		return (aType.isSubtypeOf(TypeDescriptor.type()) || TypeDescriptor.type().isSubtypeOf(aType));
	}

	boolean ObjectIsSupertypeOfPrimitiveType (
			final AvailObject object, 
			final AvailObject aPrimitiveType)
	{
		//  Check if object (terminates type) is a supertype of aPrimitiveType (a primitive type).
		//  Never true, because terminates type is the most specific metatype and nothing
		//  is allowed to inherit from it.

		return false;
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
		//  The types are unrelated.  Since terminatesType is the subtype of every
		//  meta except terminates, and it's a supertype of terminates, we have a
		//  non-meta and terminatesType being intersected.  Answer terminates,
		//  as it's the highest common subtype.
		return TypeDescriptor.terminates();
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

		return TypeDescriptor.terminatesType();
	}

	AvailObject ObjectTypeUnion (
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
		//  It's terminatesType and a type unrelated to terminatesType.
		//  Therefore the other type can't be above or below meta, so
		//  answer the nearest ancestor of another and meta.
		return TypeDescriptor.meta().typeUnion(another);
	}

	boolean ObjectIsCyclicType (
			final AvailObject object)
	{
		//  Because terminatesType is a subtype of all other metatypes, it is even considered
		//  a cyclic type.  Coincidentally, terminatesType has itself as its type.

		return true;
	}





	/* Descriptor lookup */
	public static TerminatesMetaDescriptor mutableDescriptor()
	{
		return (TerminatesMetaDescriptor) AllDescriptors [148];
	};
	public static TerminatesMetaDescriptor immutableDescriptor()
	{
		return (TerminatesMetaDescriptor) AllDescriptors [149];
	};

}
