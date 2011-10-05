/**
 * com.avail.descriptor/UnionMetaDescriptor.java
 * Copyright (c) 2011, Mark van Gulik.
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
import com.avail.descriptor.PrimitiveTypeDescriptor.*;

/**
 * My instances are called <em>instace union metatypes</em>, or just union
 * metatypes. Instances are parameterized on the kinds of their instances'
 * instances. For example, a union metatype over extended integer has instances
 * union type of (1, 2, 3), union type of (1, 3, 7), etc.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class UnionMetaDescriptor
extends TypeDescriptor
{
	/** The layout of integer slots for my instances. */
	public enum ObjectSlots
	{
		/**
		 * The parametric kind of the {@linkplain AbstractTypeDescriptor union
		 * metatype}. This is a kind that includes all instances of this
		 * metatype's object instances. For example, if I am a union metatype of
		 * [1..7], some of my instances are the union type of (1, 3, 7), the
		 * union type of (1, 2, 3), the instance type of 7, etc.; the kind
		 * [1..7] therefore includes all instances of these union types.
		 */
		INNER_KIND
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append("UnionMeta over ");
		object.innerKind().printOnAvoidingIndent(
			aStream, recursionList, indent);
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.INNER_KIND).hash() ^ 0xC5987B13;
	}

	@Override
	public @NotNull AvailObject o_InnerKind (final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.INNER_KIND);
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsUnionMeta(object);
	}

	@Override
	public boolean o_EqualsUnionMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aUnionMeta)
	{
		return object.innerKind().equals(aUnionMeta.innerKind());
	}

	@Override
	public boolean o_IsUnionMeta (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfUnionMeta(object);
	}

	@Override
	public boolean o_IsSupertypeOfUnionMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aUnionMeta)
	{
		return aUnionMeta.innerKind().isSubtypeOf(object.innerKind());
	}

	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return META.o();
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
		if (another.equals(META.o()))
		{
			return UnionMetaDescriptor.over(
				object.innerKind().typeIntersection(TYPE.o()));
		}
		if (another.isUnionMeta())
		{
			if (another.isAbstractUnionType())
			{
				return InstanceTypeDescriptor.on(
					object.innerKind().typeIntersection(another.innerKind()));
			}
			return UnionMetaDescriptor.over(
				object.innerKind().typeIntersection(another.innerKind()));
		}
		if (another.isSubtypeOf(TYPE.o()))
		{
			return InstanceTypeDescriptor.on(
				BottomTypeDescriptor.bottom());
		}
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject meta)
	{
		return UnionMetaDescriptor.over(
			object.innerKind().typeIntersection(TYPE.o()));
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
		if (another.isUnionMeta())
		{
			return UnionMetaDescriptor.over(
				object.innerKind().typeUnion(another.innerKind()));
		}
		return another.typeUnion(object.innerKind().kind());
	}

	/**
	 * Construct a new {@link UnionMetaDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected UnionMetaDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The {@linkplain Descriptor descriptor} instance that describes a
	 * mutable {@link UnionMetaDescriptor}.
	 */
	final private static @NotNull UnionMetaDescriptor mutable =
		new UnionMetaDescriptor(true);

	/**
	 * Answer the {@linkplain Descriptor descriptor} instance that describes a
	 * mutable union metatype.
	 *
	 * @return A {@link UnionMetaDescriptor} for mutable objects.
	 */
	public static @NotNull UnionMetaDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The {@linkplain Descriptor descriptor} instance that describes
	 * an immutable {@link UnionMetaDescriptor}.
	 */
	final private static @NotNull UnionMetaDescriptor immutable =
		new UnionMetaDescriptor(false);

	/**
	 * Answer the {@linkplain Descriptor descriptor} instance that describes an
	 * immutable  union metatype.
	 *
	 * @return A {@link UnionMetaDescriptor} for immutable objects.
	 */
	public static UnionMetaDescriptor immutable ()
	{
		return immutable;
	}

	/**
	 * Answer a new {@linkplain UnionMetaDescriptor union metatype} based on
	 * the specified {@linkplain TypeDescriptor type}.
	 *
	 * @param type
	 *        The type of the result's instances' instances. {@linkplain
	 *        AbstractUnionTypeDescriptor Union types} are normalized to their
	 *        superkinds.
	 * @return A new {@linkplain UnionMetaDescriptor union metatype} over the
	 *         specified kind.
	 */
	public static @NotNull AvailObject over (
		final @NotNull AvailObject type)
	{
		assert type.isType();
		type.makeImmutable();
		final AvailObject kind = type.isAbstractUnionType()
			? type.computeSuperkind()
			: type;
		final AvailObject result = mutable().create();
		result.objectSlotPut(ObjectSlots.INNER_KIND, kind);
		return result;
	}

	/** The most general {@linkplain UnionMetaDescriptor union metatype}. */
	private static AvailObject mostGeneralType;

	/**
	 * Answer the most general {@linkplain UnionMetaDescriptor union metatype}.
	 *
	 * @return The most general {@linkplain UnionMetaDescriptor union metatype}.
	 */
	public static @NotNull AvailObject mostGeneralType ()
	{
		return mostGeneralType;
	}

	/** The (instance) type of the most general union metatype. */
	private static AvailObject meta;

	/**
	 * Answer the (instance) type of the most general {@linkplain
	 * UnionMetaDescriptor union metatype}.
	 *
	 * @return
	 *         The instance type containing the most general {@linkplain
	 *         UnionMetaDescriptor union metatype}.
	 */
	public static @NotNull AvailObject meta ()
	{
		return meta;
	}

	/**
	 * Forget any registered objects.
	 */
	public static void clearWellKnownObjects ()
	{
		mostGeneralType = null;
		meta = null;
	}

	/**
	 * Register objects.
	 */
	public static void createWellKnownObjects ()
	{
		mostGeneralType = over(ANY.o());
		mostGeneralType.makeImmutable();
		meta = InstanceTypeDescriptor.on(mostGeneralType);
		meta.makeImmutable();
	}
}
