/**
 * descriptor/ObjectMetaDescriptor.java
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

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.NotNull;

/**
 * TODO: Document this type!
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class ObjectMetaDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/** The foundational {@linkplain ObjectTypeDescriptor object type}. */
		MY_OBJECT_TYPE,

		/**
		 * The range of ordinals corresponding to metatypes within the infinite
		 * {@linkplain ObjectMetaDescriptor object metatype} hyper-lineage. A
		 * range of [M..N] indicates that the type of the foundational
		 * {@linkplain ObjectTypeDescriptor object type} has been recursively
		 * requested between M and N times. When M equals N, then it denotes a
		 * particular metatype.
		 */
		OBJECT_META_LEVELS
	}

	@Override
	public void o_MyObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.MY_OBJECT_TYPE, value);
	}

	@Override
	public @NotNull AvailObject o_MyObjectType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.MY_OBJECT_TYPE);
	}

	@Override
	public void o_ObjectMetaLevels (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.OBJECT_META_LEVELS, value);
	}

	@Override
	public @NotNull AvailObject o_ObjectMetaLevels (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.OBJECT_META_LEVELS);
	}

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		object.myObjectType().makeImmutable();
		AvailObject newMetaLevels = object.objectMetaLevels();
		if (!newMetaLevels.lowerBound().isFinite())
		{
			return object;
		}

		newMetaLevels = IntegerRangeTypeDescriptor.create(
			newMetaLevels.lowerBound().plusCanDestroy(
				IntegerDescriptor.one(), false),
			true,
			newMetaLevels.upperBound().plusCanDestroy(
				IntegerDescriptor.one(), false),
			newMetaLevels.upperInclusive());
		return fromObjectTypeAndLevel(object.myObjectType(), newMetaLevels);
	}

	@Override
	public int o_Hash (final @NotNull AvailObject object)
	{
		return
			(object.myObjectType().hash()
					* AvailObject.Multiplier
				+ object.objectMetaLevels().hash())
					* AvailObject.Multiplier
			^ 0x1317C873;
	}

	/**
	 * Answer whether this object's hash value can be computed without creating
	 * new objects. This method is used by the garbage collector to decide which
	 * objects to attempt to coalesce. The garbage collector uses the hash
	 * values to find objects that it is likely can be coalesced together.
	 */
	@Override
	public boolean o_IsHashAvailable (
		final @NotNull AvailObject object)
	{
		return object.myObjectType().isHashAvailable();
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		object.myObjectType().makeImmutable();
		AvailObject newMetaLevels = object.objectMetaLevels();
		if (!newMetaLevels.lowerBound().isFinite())
		{
			return object;
		}

		newMetaLevels = IntegerRangeTypeDescriptor.create(
			newMetaLevels.lowerBound().plusCanDestroy(
				IntegerDescriptor.one(), false),
			true,
			newMetaLevels.upperBound().plusCanDestroy(
				IntegerDescriptor.one(), false),
			newMetaLevels.upperInclusive());
		return fromObjectTypeAndLevel(object.myObjectType(), newMetaLevels);
	}

	@Override
	public @NotNull AvailObject o_Instance (
		final @NotNull AvailObject object)
	{
		object.myObjectType().makeImmutable();
		AvailObject metaLevels = object.objectMetaLevels();
		if (!metaLevels.lowerBound().isFinite())
		{
			return object;
		}

		if (metaLevels.lowerBound().equals(IntegerDescriptor.one()))
		{
			if (!metaLevels.upperBound().equals(IntegerDescriptor.one()))
			{
				error(
					"cannot uniquely determine instance of metatype smear "
					+ "that includes first level object metatype");
			}

			return object.myObjectType();
		}

		metaLevels = IntegerRangeTypeDescriptor.create(
			metaLevels.lowerBound().minusCanDestroy(
				IntegerDescriptor.one(), false),
			true,
			metaLevels.upperBound().minusCanDestroy(
				IntegerDescriptor.one(), false),
			metaLevels.upperInclusive());
		return fromObjectTypeAndLevel(object.myObjectType(), metaLevels);
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfObjectMeta(object);
	}

	@Override
	public boolean o_IsSupertypeOfObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		return
			anObjectMeta.objectMetaLevels().isSubtypeOf(
				object.objectMetaLevels())
			&& anObjectMeta.myObjectType().isSubtypeOf(object.myObjectType());
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
		return another.typeIntersectionOfObjectMeta(object);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		final AvailObject levelIntersection =
			object.objectMetaLevels().typeIntersection(
				anObjectMeta.objectMetaLevels());
		if (levelIntersection.equals(TERMINATES.o()))
		{
			return TERMINATES_TYPE.o();
		}

		final AvailObject baseIntersection =
			object.myObjectType().typeIntersection(
				anObjectMeta.myObjectType());
		if (baseIntersection.equals(TERMINATES.o()))
		{
			return TERMINATES_TYPE.o();
		}

		return fromObjectTypeAndLevel(baseIntersection, levelIntersection);
	}

	/**
	 * Answer the most general type that is still at least as specific as these.
	 * Since metas intersect at {@linkplain Types#TERMINATES_TYPE
	 * TERMINATES_TYPE} rather than terminates, we must be very careful to
	 * override this properly. Note that the cases of the types being equal or
	 * one being a subtype of the other have already been dealt with (in {@link
	 * #o_TypeIntersection(AvailObject, AvailObject) o_TypeIntersection}), so
	 * don't test for them here.
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject someMeta)
	{
		return TERMINATES_TYPE.o();
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
		return another.typeUnionOfObjectMeta(object);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		return fromObjectTypeAndLevel(
			object.myObjectType().typeUnion(anObjectMeta.myObjectType()),
			object.objectMetaLevels().typeUnion(
				anObjectMeta.objectMetaLevels()));
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		object.myObjectType().printOnAvoidingIndent(
			builder, recursionList, indent);
		builder.append(" metatype ");
		builder.append(object.objectMetaLevels());
	}

	/**
	 * Answer an {@link ObjectMetaDescriptor object metatype} of the appropriate
	 * {@linkplain ObjectTypeDescriptor object type} and metatype level.
	 *
	 * @param myObjectType
	 *        An {@linkplain ObjectTypeDescriptor object type}.
	 * @param objectMetaLevel
	 *        The metatype level of the desired {@linkplain ObjectMetaDescriptor
	 *        object metatype}.
	 * @return An {@linkplain ObjectMetaDescriptor object metatype}.
	 */
	public static @NotNull AvailObject fromObjectTypeAndLevel (
		final @NotNull AvailObject myObjectType,
		final @NotNull AvailObject objectMetaLevel)
	{
		assert objectMetaLevel.lowerBound().greaterOrEqual(
			IntegerDescriptor.one());

		if (objectMetaLevel.equals(TERMINATES.o()))
		{
			return TERMINATES.o();
		}

		AvailObject result = mutable().create();
		result.myObjectType(myObjectType);
		result.objectMetaLevels(objectMetaLevel);
		return result;
	}

	/**
	 * Construct a new {@link ObjectMetaDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ObjectMetaDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link ObjectMetaDescriptor}. */
	private final static @NotNull ObjectMetaDescriptor mutable =
		new ObjectMetaDescriptor(true);

	/**
	 * Answer the mutable {@link ObjectMetaDescriptor}.
	 *
	 * @return The mutable {@link ObjectMetaDescriptor}.
	 */
	public static @NotNull ObjectMetaDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ObjectMetaDescriptor}. */
	private final static @NotNull ObjectMetaDescriptor immutable =
		new ObjectMetaDescriptor(false);

	/**
	 * Answer the immutable {@link ObjectMetaDescriptor}.
	 *
	 * @return The immutable {@link ObjectMetaDescriptor}.
	 */
	public static @NotNull ObjectMetaDescriptor immutable ()
	{
		return immutable;
	}
}
