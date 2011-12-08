/**
 * EnumerationMetaDescriptor.java
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
import com.avail.annotations.*;

/**
 * {@code EnumerationMetaDescriptor} represents the type of {@linkplain
 * AbstractEnumerationTypeDescriptor enumerations} (and is therefore a
 * metatype). Instances are called <em>enumeration types</em>. Instances are
 * parameterized on the kinds of their instances' instances. For example, an
 * {@code enumeration type over extended integer} has instances
 * <code>enumeration of {1, 2, 3}</code>, <code>enumeration of {1, 3, 7}</code>,
 * etc.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class EnumerationMetaDescriptor
extends TypeDescriptor
{
	/** The layout of integer slots for my instances. */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The parametric kind of the {@linkplain AbstractTypeDescriptor
		 * enumeration type}. This is a kind that includes all instance's
		 * instances. For example, given {@code enumeration type over [1..7]},
		 * instances are <code>enumeration of {1, 3, 7}</code>,
		 * <code>enumeration of {1, 2, 3}</code>, {@code 7's type}, etc.; the kind
		 * {@code [1..7]} includes all instances of these enumerations.
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
		aStream.append("enum type of ");
		object.innerKind().printOnAvoidingIndent(
			aStream, recursionList, indent);
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.INNER_KIND).hash() ^ 0xC5987B13;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_InnerKind (final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.INNER_KIND);
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsEnumerationType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsEnumerationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anEnumerationType)
	{
		return object.innerKind().equals(anEnumerationType.innerKind());
	}

	@Override @AvailMethod
	boolean o_IsEnumerationType (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfEnumerationType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfEnumerationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anEnumerationType)
	{
		return anEnumerationType.innerKind().isSubtypeOf(object.innerKind());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return META.o();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersection (
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
			return EnumerationMetaDescriptor.of(
				object.innerKind().typeIntersection(TYPE.o()));
		}
		if (another.isEnumerationType())
		{
			if (another.isEnumeration())
			{
				return InstanceTypeDescriptor.on(
					object.innerKind().typeIntersection(another.innerKind()));
			}
			return EnumerationMetaDescriptor.of(
				object.innerKind().typeIntersection(another.innerKind()));
		}
		if (another.isSubtypeOf(TYPE.o()))
		{
			return InstanceTypeDescriptor.on(
				BottomTypeDescriptor.bottom());
		}
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMeta)
	{
		return EnumerationMetaDescriptor.of(
			object.innerKind().typeIntersection(TYPE.o()));
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnion (
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
		if (another.isEnumerationType())
		{
			return EnumerationMetaDescriptor.of(
				object.innerKind().typeUnion(another.innerKind()));
		}
		return another.typeUnion(object.innerKind().kind());
	}

	/**
	 * Construct a new {@link EnumerationMetaDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected EnumerationMetaDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The {@linkplain Descriptor descriptor} instance that describes a
	 * mutable {@link EnumerationMetaDescriptor}.
	 */
	final private static @NotNull EnumerationMetaDescriptor mutable =
		new EnumerationMetaDescriptor(true);

	/**
	 * Answer the {@linkplain Descriptor descriptor} instance that describes a
	 * mutable enumeration type.
	 *
	 * @return A {@link EnumerationMetaDescriptor} for mutable objects.
	 */
	public static @NotNull EnumerationMetaDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The {@linkplain Descriptor descriptor} instance that describes
	 * an immutable {@link EnumerationMetaDescriptor}.
	 */
	final private static @NotNull EnumerationMetaDescriptor immutable =
		new EnumerationMetaDescriptor(false);

	/**
	 * Answer the {@linkplain Descriptor descriptor} instance that describes an
	 * immutable enumeration type.
	 *
	 * @return A {@link EnumerationMetaDescriptor} for immutable objects.
	 */
	public static EnumerationMetaDescriptor immutable ()
	{
		return immutable;
	}

	/**
	 * Answer a new {@linkplain EnumerationMetaDescriptor enumeration type}
	 * based on the specified {@linkplain TypeDescriptor type}.
	 *
	 * @param type
	 *        The type of the result's instances' instances. {@linkplain
	 *        AbstractEnumerationTypeDescriptor Enumerations} are normalized to
	 *        their superkinds.
	 * @return A new enumeration type over the specified kind.
	 */
	public static @NotNull AvailObject of (
		final @NotNull AvailObject type)
	{
		assert type.isType();
		type.makeImmutable();
		final AvailObject kind = type.isEnumeration()
			? type.computeSuperkind()
			: type;
		final AvailObject result = mutable().create();
		result.objectSlotPut(ObjectSlots.INNER_KIND, kind);
		return result;
	}

	/**
	 * The most general {@linkplain EnumerationMetaDescriptor enumeration type}.
	 */
	private static AvailObject mostGeneralType;

	/**
	 * Answer the most general {@linkplain EnumerationMetaDescriptor enumeration
	 * type}.
	 *
	 * @return The most general enumeration type.
	 */
	public static @NotNull AvailObject mostGeneralType ()
	{
		return mostGeneralType;
	}

	/**
	 * The {@linkplain InstanceTypeDescriptor type} of the most general
	 * {@linkplain EnumerationMetaDescriptor enumeration type}.
	 */
	private static AvailObject meta;

	/**
	 * Answer the {@linkplain InstanceTypeDescriptor type} of the most general
	 * {@linkplain EnumerationMetaDescriptor enumeration type}.
	 *
	 * @return
	 *         The instance type containing the most general {@linkplain
	 *         EnumerationMetaDescriptor enumeration type}.
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
		mostGeneralType = of(ANY.o());
		mostGeneralType.makeImmutable();
		meta = InstanceTypeDescriptor.on(mostGeneralType);
		meta.makeImmutable();
	}
}
