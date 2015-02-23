/**
 * SelfPojoTypeDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.SelfPojoTypeDescriptor.ObjectSlots.*;
import java.lang.reflect.Modifier;
import java.util.*;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * {@code SelfPojoTypeDescriptor} describes the self type of a Java class or
 * interface. In the pojo implementation, any Java class or interface that
 * depends recursively on itself through type parameterization of self,
 * superclass, or superinterface uses a pojo self type. {@link Enum
 * java.lang.Enum} is a famous example from the Java library: its type
 * parameter, {@code E}, extends {@code Enum}'s self type. A pojo self type is
 * used to break the recursive dependency.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class SelfPojoTypeDescriptor
extends PojoTypeDescriptor
{
	/** The layout of the object slots. */
	enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} that wraps the {@linkplain
		 * Class Java class or interface} represented by this {@linkplain
		 * UnfusedPojoTypeDescriptor pojo type}.
		 */
		JAVA_CLASS,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain PojoDescriptor
		 * pojos} that wrap {@linkplain Class Java classes and interfaces}. This
		 * constitutes this type's complete ancestry of Java types. There are no
		 * {@linkplain TypeDescriptor type parameterization} {@linkplain
		 * TupleDescriptor tuples} because no Java type may appear multiply in
		 * the ancestry of any other Java type with different type
		 * parameterizations, thereby permitting pojo self types to omit type
		 * parameterization information.
		 */
		JAVA_ANCESTORS
	}

	@Override @AvailMethod
	AvailObject o_JavaAncestors (final AvailObject object)
	{
		return object.slot(JAVA_ANCESTORS);
	}

	@Override @AvailMethod
	AvailObject o_JavaClass (final AvailObject object)
	{
		return object.slot(JAVA_CLASS);
	}

	@Override @AvailMethod
	boolean o_EqualsPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		// Callers have ensured that aPojoType is either an unfused pojo type
		// or a self type.
		final A_BasicObject other = aPojoType.pojoSelfType();
		return object.slot(JAVA_CLASS).equals(other.javaClass())
			&& object.slot(JAVA_ANCESTORS).equals(other.javaAncestors());
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		// Note that this definition produces a value compatible with an unfused
		// pojo type; this is necessary to permit comparison between an unfused
		// pojo type and its self type.
		return object.slot(JAVA_ANCESTORS).hash() ^ 0xA015BC44;
	}

	@Override @AvailMethod
	boolean o_IsAbstract (final AvailObject object)
	{
		final A_BasicObject javaClass = object.slot(JAVA_CLASS);
		return javaClass.equalsNil()
			|| Modifier.isAbstract(
				((Class<?>) javaClass.javaObjectNotNull()).getModifiers());
	}

	@Override @AvailMethod
	boolean o_IsPojoArrayType (final AvailObject object)
	{
		return object.slot(JAVA_CLASS).equals(
			RawPojoDescriptor.equalityWrap(
				ArrayPojoTypeDescriptor.PojoArray.class));
	}

	@Override @AvailMethod
	boolean o_IsPojoFusedType (final AvailObject object)
	{
		return object.slot(JAVA_CLASS).equalsNil();
	}

	@Override @AvailMethod
	boolean o_IsPojoSelfType (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		// Check type compatibility by computing the set intersection of the
		// ancestry of the arguments. If the result is not equal to the
		// ancestry of object, then object is not a supertype of aPojoType.
		final A_Set ancestors = object.slot(JAVA_ANCESTORS);
		final A_Set otherAncestors = aPojoType.pojoSelfType().javaAncestors();
		final A_Set intersection =
			ancestors.setIntersectionCanDestroy(otherAncestors, false);
		return ancestors.equals(intersection);
	}

	@Override @AvailMethod
	A_Type o_PojoSelfType (final AvailObject object)
	{
		return object;
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Make the object shared, since there's not an immutable variant.
			return object.makeShared();
		}
		return object;
	}

	@Override
	@Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		final A_BasicObject javaClass = object.slot(JAVA_CLASS);
		if (javaClass.equalsNil())
		{
			// TODO: [TLS] Answer the nearest mutual parent of the leaf types.
			return Object.class;
		}
		return javaClass.javaObject();
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.SELF_POJO_TYPE_REPRESENTATIVE;
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		final A_Type other = aPojoType.pojoSelfType();
		final A_Set ancestors = object.slot(JAVA_ANCESTORS);
		final A_Set otherAncestors = other.javaAncestors();
		for (final AvailObject ancestor : ancestors)
		{
			final Class<?> javaClass = (Class<?>) ancestor.javaObjectNotNull();
			final int modifiers = javaClass.getModifiers();
			if (Modifier.isFinal(modifiers))
			{
				return BottomPojoTypeDescriptor.pojoBottom();
			}
		}
		for (final A_BasicObject ancestor : otherAncestors)
		{
			final Class<?> javaClass = (Class<?>) ancestor.javaObjectNotNull();
			final int modifiers = javaClass.getModifiers();
			if (Modifier.isFinal(modifiers))
			{
				return BottomPojoTypeDescriptor.pojoBottom();
			}
		}
		return create(
			NilDescriptor.nil(),
			ancestors.setUnionCanDestroy(otherAncestors, false));
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		final A_Set intersection =
			object.slot(JAVA_ANCESTORS).setIntersectionCanDestroy(
				aPojoType.pojoSelfType().javaAncestors(), false);
		return create(mostSpecificOf(intersection), intersection);
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	A_Map o_TypeVariables (final AvailObject object)
	{
		return MapDescriptor.empty();
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		final A_BasicObject javaClass = object.slot(JAVA_CLASS);
		if (!javaClass.equalsNil())
		{
			builder.append(
				((Class<?>) javaClass.javaObjectNotNull()).getName());
		}
		else
		{
			final A_Set ancestors = object.slot(JAVA_ANCESTORS);
			final List<AvailObject> childless = new ArrayList<>(
				childlessAmong(ancestors));
			Collections.sort(
				childless,
				new Comparator<AvailObject>()
				{
					@Override
					public int compare (
						final @Nullable AvailObject o1,
						final @Nullable AvailObject o2)
					{
						assert o1 != null;
						assert o2 != null;
						final Class<?> c1 = (Class<?>) o1.javaObjectNotNull();
						final Class<?> c2 = (Class<?>) o2.javaObjectNotNull();
						return c1.getName().compareTo(c2.getName());
					}
				});
			builder.append('(');
			boolean first = true;
			for (final A_BasicObject aClass : childless)
			{
				if (!first)
				{
					builder.append(" ∩ ");
				}
				first = false;
				builder.append(
					((Class<?>) aClass.javaObjectNotNull()).getName());
			}
			builder.append(')');
		}
		builder.append("'s self type");
	}

	/**
	 * Construct a new {@link SelfPojoTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public SelfPojoTypeDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, null);
	}

	/** The mutable {@link SelfPojoTypeDescriptor}. */
	private final static SelfPojoTypeDescriptor mutable =
		new SelfPojoTypeDescriptor(Mutability.MUTABLE);

	@Override
	SelfPojoTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link SelfPojoTypeDescriptor}. */
	private final static SelfPojoTypeDescriptor shared =
		new SelfPojoTypeDescriptor(Mutability.SHARED);

	@Override
	SelfPojoTypeDescriptor immutable ()
	{
		// There is no immutable descriptor.
		return shared;
	}

	@Override
	SelfPojoTypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Create a new {@link AvailObject} that represents a {@linkplain
	 * SelfPojoTypeDescriptor pojo self type}.
	 *
	 * @param javaClass
	 *        A {@linkplain RawPojoDescriptor raw pojo} that wraps the
	 *        {@linkplain Class Java class or interface} represented by this
	 *        pojo self type.
	 * @param javaAncestors
	 *        A {@linkplain SetDescriptor set} of {@linkplain PojoDescriptor
	 *        pojos} that wrap {@linkplain Class Java classes and interfaces}.
	 *        This constitutes this type's complete ancestry of Java types.
	 *        There are no {@linkplain TypeDescriptor type parameterization}
	 *        {@linkplain TupleDescriptor tuples} because no Java type may
	 *        appear multiply in the ancestry of any other Java type with
	 *        different type parameterizations, thereby permitting pojo self
	 *        types to omit type parameterization information.
	 * @return The requested pojo type.
	 */
	static AvailObject create (
		final AvailObject javaClass,
		final A_Set javaAncestors)
	{
		final AvailObject newObject = mutable.create();
		newObject.setSlot(JAVA_CLASS, javaClass);
		newObject.setSlot(JAVA_ANCESTORS, javaAncestors);
		return newObject.makeImmutable();
	}

	/**
	 * Convert a self pojo type to a 2-tuple holding the main class name (or
	 * null) and a set of ancestor class names.
	 *
	 * @param selfPojo The self pojo to convert.
	 * @return A 2-tuple suitable for serialization.
	 */
	public static A_Tuple toSerializationProxy (
		final A_BasicObject selfPojo)
	{
		assert selfPojo.isPojoSelfType();
		final A_BasicObject pojoClass = selfPojo.javaClass();
		final A_String mainClassName;
		if (pojoClass.equalsNil())
		{
			mainClassName = NilDescriptor.nil();
		}
		else
		{
			final Class<?> javaClass = (Class<?>)pojoClass.javaObjectNotNull();
			mainClassName = StringDescriptor.from(javaClass.getName());
		}
		A_Set ancestorNames = SetDescriptor.empty();
		for (final A_BasicObject ancestor : selfPojo.javaAncestors())
		{
			final Class<?> javaClass = (Class<?>)ancestor.javaObjectNotNull();
			ancestorNames = ancestorNames.setWithElementCanDestroy(
				StringDescriptor.from(javaClass.getName()),
				true);
		}
		return TupleDescriptor.from(mainClassName, ancestorNames);
	}

	/**
	 * Convert a proxy previously created by {@link
	 * #toSerializationProxy(A_BasicObject)} back into a self pojo type.
	 *
	 * @param selfPojoProxy
	 *            A 2-tuple with the class name (or null) and a set of ancestor
	 *            class names.
	 * @param classLoader
	 *            The {@link ClassLoader} used to load any mentioned classes.
	 * @return A self pojo type.
	 * @throws ClassNotFoundException If a class can't be loaded.
	 */
	public static AvailObject fromSerializationProxy (
		final A_Tuple selfPojoProxy,
		final ClassLoader classLoader)
	throws ClassNotFoundException
	{
		final A_String className = selfPojoProxy.tupleAt(1);
		final AvailObject mainRawType;
		if (className.equalsNil())
		{
			mainRawType = NilDescriptor.nil();
		}
		else
		{
			final Class<?> mainClass = Class.forName(
				className.asNativeString(),
				true,
				classLoader);
			mainRawType = RawPojoDescriptor.equalityWrap(mainClass);
		}
		A_Set ancestorTypes = SetDescriptor.empty();
		for (final A_String ancestorClassName : selfPojoProxy.tupleAt(2))
		{
			final Class<?> ancestorClass = Class.forName(
				ancestorClassName.asNativeString(),
				true,
				classLoader);
			ancestorTypes = ancestorTypes.setWithElementCanDestroy(
				RawPojoDescriptor.equalityWrap(ancestorClass),
				true);
		}
		return SelfPojoTypeDescriptor.create(mainRawType, ancestorTypes);
	}

}
