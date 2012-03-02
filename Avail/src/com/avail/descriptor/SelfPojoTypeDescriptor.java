/**
 * SelfPojoTypeDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
final class SelfPojoTypeDescriptor
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
		 * pojos} that wrap {@Linkplain Class Java classes and interfaces}. This
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
	boolean o_EqualsPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		// Callers have ensured that aPojoType is either an unfused pojo type
		// or a self type.
		final AvailObject other = aPojoType.pojoSelfType();
		return object.slot(JAVA_CLASS).equals(other.javaClass())
			&& object.slot(JAVA_ANCESTORS).equals(other.javaAncestors());
	}

	@Override @AvailMethod
	int o_Hash (final @NotNull AvailObject object)
	{
		// Note that this definition produces a value compatible with an unfused
		// pojo type; this is necessary to permit comparison between an unfused
		// pojo type and its self type.
		return object.slot(JAVA_ANCESTORS).hash() ^ 0xA015BC44;
	}

	@Override @AvailMethod
	boolean o_IsAbstract (final @NotNull AvailObject object)
	{
		final AvailObject javaClass = object.slot(JAVA_CLASS);
		return javaClass.equalsNull()
			|| Modifier.isAbstract(
				((Class<?>) javaClass.javaObject()).getModifiers());
	}

	@Override @AvailMethod
	boolean o_IsPojoArrayType (final @NotNull AvailObject object)
	{
		return object.slot(JAVA_CLASS).equals(
			RawPojoDescriptor.equalityWrap(
				ArrayPojoTypeDescriptor.PojoArray.class));
	}

	@Override @AvailMethod
	boolean o_IsPojoFusedType (final @NotNull AvailObject object)
	{
		return object.slot(JAVA_CLASS).equalsNull();
	}

	@Override @AvailMethod
	boolean o_IsPojoSelfType (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		// Check type compatibility by computing the set intersection of the
		// ancestry of the arguments. If the result is not equal to the
		// ancestry of object, then object is not a supertype of aPojoType.
		final AvailObject ancestors = object.slot(JAVA_ANCESTORS);
		final AvailObject otherAncestors =
			aPojoType.pojoSelfType().javaAncestors();
		final AvailObject intersection =
			ancestors.setIntersectionCanDestroy(otherAncestors, false);
		return ancestors.equals(intersection);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_JavaAncestors (final @NotNull AvailObject object)
	{
		return object.slot(JAVA_ANCESTORS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_JavaClass (final @NotNull AvailObject object)
	{
		return object.slot(JAVA_CLASS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (final @NotNull AvailObject object)
	{
		object.descriptor = immutable;
		object.slot(JAVA_CLASS).makeImmutable();
		object.slot(JAVA_ANCESTORS).makeImmutable();
		return object;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_PojoSelfType (final @NotNull AvailObject object)
	{
		return object;
	}

	@Override
	Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> ignoredClassHint)
	{
		final AvailObject javaClass = object.slot(JAVA_CLASS);
		if (javaClass.equalsNull())
		{
			// TODO: [TLS] Answer the nearest mutual parent of the leaf types.
			return Object.class;
		}
		return javaClass.javaObject();
	}

	@Override @AvailMethod @ThreadSafe
	@NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
	{
		return SerializerOperation.SELF_POJO_TYPE;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		final AvailObject other = aPojoType.pojoSelfType();
		final AvailObject ancestors = object.slot(JAVA_ANCESTORS);
		final AvailObject otherAncestors = other.javaAncestors();
		for (final AvailObject ancestor : ancestors)
		{
			final Class<?> javaClass = (Class<?>) ancestor.javaObject();
			final int modifiers = javaClass.getModifiers();
			if (Modifier.isFinal(modifiers))
			{
				return PojoTypeDescriptor.pojoBottom();
			}
		}
		for (final AvailObject ancestor : otherAncestors)
		{
			final Class<?> javaClass = (Class<?>) ancestor.javaObject();
			final int modifiers = javaClass.getModifiers();
			if (Modifier.isFinal(modifiers))
			{
				return PojoTypeDescriptor.pojoBottom();
			}
		}
		return create(
			NullDescriptor.nullObject(),
			ancestors.setUnionCanDestroy(otherAncestors, false));
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfPojoFusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfPojoUnfusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anUnfusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		final AvailObject intersection =
			object.slot(JAVA_ANCESTORS).setIntersectionCanDestroy(
				aPojoType.pojoSelfType().javaAncestors(), false);
		return create(mostSpecificOf(intersection), intersection);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfPojoFusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfPojoUnfusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anUnfusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	@NotNull AvailObject o_TypeVariables (final @NotNull AvailObject object)
	{
		return MapDescriptor.empty();
	}

	@Override
	void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		final AvailObject javaClass = object.slot(JAVA_CLASS);
		if (!javaClass.equalsNull())
		{
			builder.append(((Class<?>) javaClass.javaObject()).getName());
		}
		else
		{
			final AvailObject ancestors = object.slot(JAVA_ANCESTORS);
			final List<AvailObject> childless = new ArrayList<AvailObject>(
				childlessAmong(ancestors));
			Collections.sort(
				childless,
				new Comparator<AvailObject>()
				{
					@Override
					public int compare (
						final @NotNull AvailObject o1,
						final @NotNull AvailObject o2)
					{
						final Class<?> c1 = (Class<?>) o1.javaObject();
						final Class<?> c2 = (Class<?>) o2.javaObject();
						return c1.getName().compareTo(c2.getName());
					}
				});
			builder.append('(');
			boolean first = true;
			for (final AvailObject aClass : childless)
			{
				if (!first)
				{
					builder.append(" ∩ ");
				}
				first = false;
				builder.append(((Class<?>) aClass.javaObject()).getName());
			}
			builder.append(')');
		}
		builder.append("'s self type");
	}

	/**
	 * Construct a new {@link SelfPojoTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain SelfPojoTypeDescriptor descriptor}
	 *        represent a mutable object?
	 */
	public SelfPojoTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link SelfPojoTypeDescriptor}. */
	private final static @NotNull SelfPojoTypeDescriptor mutable =
		new SelfPojoTypeDescriptor(true);

	/**
	 * Answer the mutable {@link SelfPojoTypeDescriptor}.
	 *
	 * @return The mutable {@code SelfPojoTypeDescriptor}.
	 */
	static @NotNull SelfPojoTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link SelfPojoTypeDescriptor}. */
	private final static @NotNull SelfPojoTypeDescriptor immutable =
		new SelfPojoTypeDescriptor(false);

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
	 *        pojos} that wrap {@Linkplain Class Java classes and interfaces}.
	 *        This constitutes this type's complete ancestry of Java types.
	 *        There are no {@linkplain TypeDescriptor type parameterization}
	 *        {@linkplain TupleDescriptor tuples} because no Java type may
	 *        appear multiply in the ancestry of any other Java type with
	 *        different type parameterizations, thereby permitting pojo self
	 *        types to omit type parameterization information.
	 * @return The requested pojo type.
	 */
	static @NotNull AvailObject create (
		final @NotNull AvailObject javaClass,
		final @NotNull AvailObject javaAncestors)
	{
		final AvailObject newObject = mutable.create();
		newObject.setSlot(JAVA_CLASS, javaClass);
		newObject.setSlot(JAVA_ANCESTORS, javaAncestors);
		return newObject.makeImmutable();
	}
}
