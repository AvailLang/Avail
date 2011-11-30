/**
 * com.avail.descriptor/PojoSelfTypeDescriptor.java
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

import static com.avail.descriptor.PojoSelfTypeDescriptor.ObjectSlots.*;
import java.lang.reflect.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;

/**
 * A {@code PojoSelfTypeDescriptor} occurs only in a {@linkplain
 * TupleDescriptor type parameterization tuple} of a {@linkplain
 * PojoTypeDescriptor pojo type}. Whenever a pojo type would be compelled to
 * mention itself somewhere in the parameterization of itself, its superclasses,
 * and/or superinterfaces (i.e. its inheritance type set), it mentions an
 * appropriate self reference instead. Since a Java type may not appear multiply
 * with different parameterizations in the ancestry of a type, a self reference
 * need not record its referent's type parameterization.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class PojoSelfTypeDescriptor
extends TypeDescriptor
{
	/**
	 * A special {@linkplain AtomDescriptor atom} whose {@linkplain
	 * InstanceTypeDescriptor instance type} represents the self type of a
	 * {@linkplain Class Java class} which features a {@linkplain TypeVariable
	 * type variable} whose upper bound is the fully-parameterized Java class.
	 */
	private static AvailObject selfType;

	/**
	 * Answer a special {@linkplain AtomDescriptor atom} whose {@linkplain
	 * InstanceTypeDescriptor instance type} represents the self type of a
	 * {@linkplain Class Java class} which features a {@linkplain TypeVariable
	 * type variable} whose upper bound is the fully-parameterized Java class.
	 *
	 * @return The pojo self type atom.
	 */
	public static @NotNull AvailObject selfType ()
	{
		return selfType;
	}

	/**
	 * Answer the {@linkplain PojoSelfTypeDescriptor self type} of the
	 * {@linkplain PojoTypeDescriptor#mostSpecificType() most specific pojo
	 * type}.
	 *
	 * @return The self type of the most specific pojo type.
	 */
	private static @NotNull AvailObject mostSpecificSelfType ()
	{
		return PojoTypeDescriptor.mostSpecificType().pojoSelfType();
	}

	/**
	 * Create any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void createWellKnownObjects ()
	{
		selfType =
			InstanceTypeDescriptor.on(AtomDescriptor.create(
				ByteStringDescriptor.from("self type")));
	}

	/**
	 * Destroy or reset any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void clearWellKnownObjects ()
	{
		selfType = null;
	}

	/** The layout of the object slots. */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} that represents the most
		 * specific {@linkplain Class raw Java class} included by this
		 * {@linkplain PojoSelfTypeDescriptor pojo self type}. May be the Avail
		 * {@linkplain NullDescriptor#nullObject() null object} if this pojo
		 * type does not represent a Java class, e.g. it may instead represent
		 * an interface or a fused pojo type.
		 */
		MOST_SPECIFIC_CLASS,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain
		 * RawPojoDescriptor raw pojos}, each of which represents a {@linkplain
		 * Class Java class} in the ancestry of the {@linkplain
		 * PojoTypeDescriptor pojo type} represented by this {@linkplain
		 * PojoSelfTypeDescriptor self type}.
		 */
		RAW_TYPES
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsPojoType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return object.objectSlot(RAW_TYPES).equals(
			aPojoType.pojoSelfType().traversed().objectSlot(RAW_TYPES));
	}

	@Override @AvailMethod
	int o_Hash (final @NotNull AvailObject object)
	{
		// Note that this definition produces a value compatible with a pojo
		// type; this is necessary to permit comparison between a pojo type and
		// its self type.
		return object.objectSlot(RAW_TYPES).hash() ^ 0xA015BC44;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfPojoType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		final AvailObject mst = PojoTypeDescriptor.mostSpecificType();
		if (aPojoType.equals(mst))
		{
			return true;
		}

		final AvailObject objectTypes = object.objectSlot(RAW_TYPES);
		if (objectTypes.equalsNull())
		{
			return false;
		}

		final AvailObject aPojoTypeTypes =
			aPojoType.pojoSelfType().traversed().objectSlot(RAW_TYPES);
		final AvailObject intersection =
			objectTypes.setIntersectionCanDestroy(aPojoTypeTypes, false);
		return objectTypes.equals(intersection);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		object.descriptor = immutable();
		object.objectSlot(RAW_TYPES).makeImmutable();
		return object;
	}

	@Override @AvailMethod
	boolean o_IsPojoSelfType (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_PojoSelfType (
		final @NotNull AvailObject object)
	{
		return object;
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
		return another.typeIntersectionOfPojoType(object);
	}

	@Override @AvailMethod
	AvailObject o_TypeIntersectionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		final AvailObject aPojoSelfType = aPojoType.pojoSelfType().traversed();
		final AvailObject objectMSC = object.objectSlot(MOST_SPECIFIC_CLASS);
		final AvailObject aPojoTypeMSC = aPojoSelfType.traversed().objectSlot(
			MOST_SPECIFIC_CLASS);
		final Class<?> objectMSCClass =
			!objectMSC.equalsNull()
			? (Class<?>) RawPojoDescriptor.getPojo(objectMSC)
			: null;
		final Class<?> aPojoTypeMSCClass =
			!aPojoTypeMSC.equalsNull()
			? (Class<?>) RawPojoDescriptor.getPojo(aPojoTypeMSC)
			: null;
		final int objectModifiers = objectMSCClass != null
			? objectMSCClass.getModifiers()
			: 0;
		final int aPojoTypeModifiers = aPojoTypeMSCClass != null
			? aPojoTypeMSCClass.getModifiers()
			: 0;
		// If either class is declared final, then the intersection is the
		// most specific pojo type.
		if (Modifier.isFinal(objectModifiers)
			|| Modifier.isFinal(aPojoTypeModifiers))
		{
			return mostSpecificSelfType();
		}
		// If neither class is an interface, then the intersection is the
		// most specific pojo type (because Java does not support multiple
		// inheritance of classes).
		if (!objectMSC.equalsNull()
			&& !Modifier.isInterface(objectModifiers)
			&& !aPojoTypeMSC.equalsNull()
			&& !Modifier.isInterface(aPojoTypeModifiers))
		{
			return mostSpecificSelfType();
		}

		final AvailObject objectTypes = object.objectSlot(RAW_TYPES);
		final AvailObject aPojoTypeTypes = aPojoSelfType.objectSlot(RAW_TYPES);
		return create(
			NullDescriptor.nullObject(),
			objectTypes.setUnionCanDestroy(aPojoTypeTypes, false));
	}

	@Override @AvailMethod
	AvailObject o_TypeUnion (
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
		return another.typeUnionOfPojoType(object);
	}

	@Override @AvailMethod
	AvailObject o_TypeUnionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		final AvailObject objectTypes = object.objectSlot(RAW_TYPES);
		final AvailObject aPojoTypeTypes =
			another.pojoSelfType().traversed().objectSlot(RAW_TYPES);
		final AvailObject intersection =
			objectTypes.setIntersectionCanDestroy(aPojoTypeTypes, false);

		// Build a map of raw types to pojos.
		final Map<Class<?>, AvailObject> rawTypeMap =
			new HashMap<Class<?>, AvailObject>(intersection.setSize());
		for (final AvailObject javaClass : intersection)
		{
			assert javaClass.isRawPojo();
			rawTypeMap.put(
				(Class<?>) RawPojoDescriptor.getPojo(javaClass), javaClass);
		}

		// Find the most specific raw type. Note that there may be several most
		// specific interfaces. If the result is a class, however, then it must
		// be strictly more specific than any other raw types in the
		// intersection.
		Class<?> mostSpecificRawType = Object.class;
		for (final Class<?> rawType : rawTypeMap.keySet())
		{
			if (mostSpecificRawType.isAssignableFrom(rawType))
			{
				mostSpecificRawType = rawType;
			}
		}

		final int modifiers = mostSpecificRawType.getModifiers();
		final AvailObject mostSpecificClass;
		// If the most specific raw type is an interface or an abstract class,
		// then use the Avail null object to represent the most specific class.
		if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers))
		{
			mostSpecificClass = NullDescriptor.nullObject();
		}
		// If the most specific raw type is a concrete class, then get its pojo
		// from the map.
		else
		{
			mostSpecificClass = rawTypeMap.get(mostSpecificRawType);
		}

		return create(mostSpecificClass, intersection);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("pojo self type = ");
		object.objectSlot(RAW_TYPES).printOnAvoidingIndent(
			builder, recursionList, indent);
	}

	/**
	 * Construct a new {@link PojoSelfTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain AbstractDescriptor descriptor} represent a
	 *        mutable object?
	 */
	private PojoSelfTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link PojoSelfTypeDescriptor}. */
	private final static @NotNull PojoSelfTypeDescriptor mutable =
		new PojoSelfTypeDescriptor(true);

	/**
	 * Answer the mutable {@link PojoSelfTypeDescriptor}.
	 *
	 * @return The mutable {@code PojoSelfTypeDescriptor}.
	 */
	public static @NotNull PojoSelfTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link PojoSelfTypeDescriptor}. */
	private final static @NotNull PojoSelfTypeDescriptor immutable =
		new PojoSelfTypeDescriptor(false);

	/**
	 * Answer the immutable {@link PojoSelfTypeDescriptor}.
	 *
	 * @return The immutable {@code PojoSelfTypeDescriptor}.
	 */
	public static @NotNull PojoSelfTypeDescriptor immutable ()
	{
		return immutable;
	}

	/**
	 * Create a {@link PojoSelfTypeDescriptor} whose corresponding {@linkplain
	 * PojoTypeDescriptor pojo type} is undergoing construction.
	 *
	 * @param mostSpecificClass
	 *        The most specific {@linkplain Class Java class} included by this
	 *        pojo type, or the Avail {@linkplain NullDescriptor#nullObject()
	 *        null object} if the pojo type represents an interface or fusion.
	 * @param rawTypes
	 *        A {@linkplain TupleDescriptor tuple} of {@linkplain
	 *        RawPojoDescriptor raw pojos}, each of which represents a
	 *        {@linkplain Class Java class} in the ancestry of the pojo type
	 *        represented by the new self type; or the {@linkplain
	 *        NullDescriptor#nullObject() null object} to indicate the infinity
	 *        of Java types (used only for the pojo self type of the
	 *        {@linkplain PojoTypeDescriptor#mostSpecificType() most specific
	 *        type}).
	 * @return A self type.
	 */
	static @NotNull AvailObject create (
		final @NotNull AvailObject mostSpecificClass,
		final @NotNull AvailObject rawTypes)
	{
		assert rawTypes.isSet() || rawTypes.equalsNull();
		final AvailObject newObject = mutable.create();
		newObject.objectSlotPut(MOST_SPECIFIC_CLASS, mostSpecificClass);
		newObject.objectSlotPut(RAW_TYPES, rawTypes);
		return newObject.makeImmutable();
	}

	/**
	 * Recursively accumulate the ancestors of the specified {@linkplain
	 * Class raw Java type}.
	 *
	 * @param rawType
	 *        A raw Java type.
	 * @param accumulator
	 *        The {@linkplain Set set} of ancestors.
	 * @param rawTypeMap
	 *        A map from {@linkplain Class Java classes} to {@linkplain
	 *        RawPojoDescriptor raw pojos}.
	 * @return The set of ancestors.
	 */
	private static @NotNull Set<AvailObject> ancestors (
		final @NotNull Class<?> rawType,
		final @NotNull Set<AvailObject> accumulator,
		final @NotNull Map<Class<?>, AvailObject> rawTypeMap)
	{
		if (rawType != null)
		{
			accumulator.add(PojoTypeDescriptor.canonize(rawTypeMap, rawType));
			final Class<?> superclass = rawType.getSuperclass();
			if (superclass != null)
			{
				ancestors(superclass, accumulator, rawTypeMap);
			}
			for (final Class<?> superinterface : rawType.getInterfaces())
			{
				ancestors(superinterface, accumulator, rawTypeMap);
			}
		}
		return accumulator;
	}

	/**
	 * Create a {@linkplain PojoSelfTypeDescriptor pojo self type} for the
	 * specified {@linkplain Class raw Java type}.
	 *
	 * @param rawType A raw Java type.
	 * @return A new mutable pojo self type.
	 */
	public static @NotNull AvailObject create (final @NotNull Class<?> rawType)
	{
		Set<AvailObject> ancestors = new HashSet<AvailObject>(5);
		ancestors.add(RawPojoDescriptor.rawObjectClass());
		final Map<Class<?>, AvailObject> rawTypeMap =
			new HashMap<Class<?>, AvailObject>(5);
		rawTypeMap.put(Object.class, RawPojoDescriptor.rawObjectClass());
		ancestors = ancestors(rawType, ancestors, rawTypeMap);
		return create(
			rawTypeMap.get(rawType),
			SetDescriptor.fromCollection(ancestors));
	}
}
