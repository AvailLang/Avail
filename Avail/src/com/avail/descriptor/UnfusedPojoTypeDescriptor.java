/**
 * UnfusedPojoTypeDescriptor.java
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

import static java.lang.reflect.Modifier.*;
import static com.avail.descriptor.UnfusedPojoTypeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.UnfusedPojoTypeDescriptor.ObjectSlots.*;
import java.lang.reflect.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * {@code UnfusedPojoTypeDescriptor} describes a fully-parameterized Java
 * reference type. This is any real Java class or interface that can be loaded
 * via Avail's {@linkplain ClassLoader class loader}.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
final class UnfusedPojoTypeDescriptor
extends PojoTypeDescriptor
{
	/** The layout of the integer slots. */
	enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The {@linkplain AvailObject#hash() hash}, or zero ({@code 0}) if the
		 * hash should be computed.
		 */
		@HideFieldInDebugger
		HASH_OR_ZERO
	}

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
		 * A {@linkplain MapDescriptor map} from {@linkplain PojoDescriptor
		 * pojos} that wrap {@Linkplain Class Java classes and interfaces} to
		 * their {@linkplain TupleDescriptor type parameterizations}. The
		 * {@linkplain AvailObject#keysAsSet() keys} constitute this type's
		 * complete {@linkplain SetDescriptor ancestry} of Java types.
		 */
		JAVA_ANCESTORS,

		/**
		 * A {@linkplain MapDescriptor map} from fully-qualified {@linkplain
		 * TypeVariable type variable} {@linkplain StringDescriptor names} to
		 * their {@linkplain TypeDescriptor values} in this {@linkplain
		 * UnfusedPojoTypeDescriptor type}.
		 */
		TYPE_VARIABLES,

		/**
		 * The cached {@linkplain SelfPojoTypeDescriptor self type} of this
		 * {@linkplain UnfusedPojoTypeDescriptor pojo type}.
		 */
		SELF_TYPE
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == HASH_OR_ZERO
			|| e == TYPE_VARIABLES
			|| e == SELF_TYPE;
	}

	@Override @AvailMethod
	boolean o_EqualsPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().equalsPojoType(aPojoType);
		}
		if (!object.slot(JAVA_CLASS).equals(aPojoType.javaClass()))
		{
			return false;
		}
		final AvailObject ancestors = object.slot(JAVA_ANCESTORS);
		final AvailObject otherAncestors = aPojoType.javaAncestors();
		if (ancestors.mapSize() != otherAncestors.mapSize())
		{
			return false;
		}
		for (final AvailObject ancestor : ancestors.keysAsSet())
		{
			if (!otherAncestors.hasKey(ancestor))
			{
				return false;
			}
			final AvailObject params = ancestors.mapAt(ancestor);
			final AvailObject otherParams = otherAncestors.mapAt(ancestor);
			final int limit = params.tupleSize();
			assert limit == otherParams.tupleSize();
			for (int i = 1; i <= limit; i++)
			{
				if (!params.tupleAt(i).equals(otherParams.tupleAt(i)))
				{
					return false;
				}
			}
		}
		// The objects are known to be equal and not reference identical
		// (checked by a caller), so coalesce them.
		object.becomeIndirectionTo(aPojoType);
		aPojoType.makeImmutable();
		return true;
	}

	@Override @AvailMethod
	int o_Hash (final @NotNull AvailObject object)
	{
		int hash = object.slot(HASH_OR_ZERO);
		if (hash == 0)
		{
			// Note that this definition produces a value compatible with a pojo
			// self type; this is necessary to permit comparison between an
			// unfused pojo type and its self type.
			hash = object.slot(JAVA_ANCESTORS).keysAsSet().hash() ^ 0xA015BC44;
			object.setSlot(HASH_OR_ZERO, hash);
		}
		return hash;
	}

	@Override @AvailMethod
	boolean o_IsAbstract (final @NotNull AvailObject object)
	{
		return Modifier.isAbstract(
			((Class<?>) object.slot(JAVA_CLASS).javaObject()).getModifiers());
	}

	@Override @AvailMethod
	boolean o_IsPojoArrayType (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsPojoFusedType (final @NotNull AvailObject object)
	{
		return false;
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

	@Override
	Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> ignoredClassHint)
	{
		return object.slot(JAVA_CLASS).javaObject();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_PojoSelfType (final @NotNull AvailObject object)
	{
		AvailObject selfType = object.slot(SELF_TYPE);
		if (selfType.equalsNull())
		{
			selfType = SelfPojoTypeDescriptor.create(
				object.slot(JAVA_CLASS),
				object.slot(JAVA_ANCESTORS).keysAsSet());
			object.setSlot(SELF_TYPE, selfType);
		}
		return selfType;
	}

	@Override @AvailMethod @ThreadSafe
	@NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
	{
		return SerializerOperation.UNFUSED_POJO_TYPE;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().typeIntersectionOfPojoType(aPojoType);
		}
		// A Java array type is effectively final, so the type intersection with
		// of a pojo array type and a singleton pojo type is pojo bottom.
		if (aPojoType.isPojoArrayType())
		{
			return PojoTypeDescriptor.pojoBottom();
		}
		return aPojoType.typeIntersectionOfPojoUnfusedType(object);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfPojoFusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFusedPojoType)
	{
		final Class<?> javaClass =
			(Class<?>) object.slot(JAVA_CLASS).javaObject();
		final int modifiers = javaClass.getModifiers();
		// If the unfused pojo type's class is final, then the intersection is
		// pojo bottom.
		if (isFinal(modifiers))
		{
			return PojoTypeDescriptor.pojoBottom();
		}
		// If the unfused pojo type is a class, then check that none of the
		// fused pojo type's ancestors are classes.
		if (!isInterface(modifiers))
		{
			// If any of the fused pojo type's ancestors are Java classes, then
			// the intersection is pojo bottom.
			for (final AvailObject ancestor :
				aFusedPojoType.javaAncestors().keysAsSet())
			{
				// Ignore java.lang.Object.
				if (!ancestor.equals(RawPojoDescriptor.rawObjectClass()))
				{
					final Class<?> otherJavaClass =
						(Class<?>) ancestor.javaObject();
					final int otherModifiers = otherJavaClass.getModifiers();
					if (isFinal(otherModifiers) || !isInterface(otherModifiers))
					{
						return PojoTypeDescriptor.pojoBottom();
					}
				}
			}
		}
		final AvailObject intersection =
			computeIntersection(object, aFusedPojoType);
		if (intersection.equalsPojoBottomType())
		{
			return intersection;
		}
		// The result will be a pojo fused type. Find the union of the key sets
		// and the intersection of their parameterizations.
		return FusedPojoTypeDescriptor.create(intersection);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfPojoUnfusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anUnfusedPojoType)
	{
		final Class<?> javaClass =
			(Class<?>) object.slot(JAVA_CLASS).javaObject();
		final Class<?> otherJavaClass =
			(Class<?>) anUnfusedPojoType.javaClass().javaObject();
		final int modifiers = javaClass.getModifiers();
		final int otherModifiers = otherJavaClass.getModifiers();
		// If either class is declared final, then the intersection is pojo
		// bottom.
		if (isFinal(modifiers) || isFinal(otherModifiers))
		{
			return PojoTypeDescriptor.pojoBottom();
		}
		// If neither class is an interface, then the intersection is pojo
		// bottom (because Java doesn't support multiple inheritance of
		// classes).
		if (!isInterface(modifiers) && !isInterface(otherModifiers))
		{
			return PojoTypeDescriptor.pojoBottom();
		}
		final AvailObject intersection =
			computeIntersection(object, anUnfusedPojoType);
		if (intersection.equalsPojoBottomType())
		{
			return intersection;
		}
		// The result will be a pojo fused type. Find the union of the key sets
		// and the intersection of their parameterizations.
		return FusedPojoTypeDescriptor.create(intersection);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().typeUnionOfPojoType(aPojoType);
		}
		return aPojoType.typeUnionOfPojoUnfusedType(object);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfPojoFusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFusedPojoType)
	{
		final AvailObject intersectionAncestors = computeUnion(
			object, aFusedPojoType);
		final AvailObject javaClass = mostSpecificOf(
			intersectionAncestors.keysAsSet());
		// If the intersection contains a most specific type, then the answer is
		// not a fused pojo type; otherwise it is.
		return !javaClass.equalsNull()
			? create(javaClass, intersectionAncestors)
			: FusedPojoTypeDescriptor.create(intersectionAncestors);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfPojoUnfusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anUnfusedPojoType)
	{
		final AvailObject intersectionAncestors = computeUnion(
			object, anUnfusedPojoType);
		final AvailObject javaClass = mostSpecificOf(
			intersectionAncestors.keysAsSet());
		// If the intersection contains a most specific type, then the answer is
		// not a fused pojo type; otherwise it is.
		return !javaClass.equalsNull()
			? create(javaClass, intersectionAncestors)
			: FusedPojoTypeDescriptor.create(intersectionAncestors);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeVariables (
		final @NotNull AvailObject object)
	{
		AvailObject typeVars = object.slot(TYPE_VARIABLES);
		if (typeVars.equalsNull())
		{
			typeVars = MapDescriptor.empty();
			for (final MapDescriptor.Entry entry
				: object.slot(JAVA_ANCESTORS).mapIterable())
			{
				final Class<?> ancestor = (Class<?>) entry.key.javaObject();
				final TypeVariable<?>[] vars = ancestor.getTypeParameters();
				final AvailObject typeArgs = entry.value;
				assert vars.length == typeArgs.tupleSize();
				for (int i = 0; i < vars.length; i++)
				{
					typeVars = typeVars.mapAtPuttingCanDestroy(
						StringDescriptor.from(
							ancestor.getName() + "." + vars[i].getName()),
						typeArgs.tupleAt(i + 1),
						true);
				}
			}
			object.setSlot(TYPE_VARIABLES, typeVars);
		}
		return typeVars;
	}

	@Override
	void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		final AvailObject javaClass = object.slot(JAVA_CLASS);
		builder.append(((Class<?>) javaClass.javaObject()).getName());
		final AvailObject ancestors = object.slot(JAVA_ANCESTORS);
		final AvailObject params = ancestors.mapAt(javaClass);
		boolean first = true;
		if (params.tupleSize() != 0)
		{
			builder.append('<');
			for (final AvailObject param : params)
			{
				if (!first)
				{
					builder.append(", ");
				}
				first = false;
				param.printOnAvoidingIndent(builder, recursionList, indent);
			}
			builder.append('>');
		}
	}

	/**
	 * Construct a new {@link UnfusedPojoTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain UnfusedPojoTypeDescriptor descriptor}
	 *        represent a mutable object?
	 */
	public UnfusedPojoTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link UnfusedPojoTypeDescriptor}. */
	private final static @NotNull UnfusedPojoTypeDescriptor mutable =
		new UnfusedPojoTypeDescriptor(true);

	/**
	 * Answer the mutable {@link UnfusedPojoTypeDescriptor}.
	 *
	 * @return The mutable {@code UnfusedPojoTypeDescriptor}.
	 */
	static @NotNull UnfusedPojoTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link UnfusedPojoTypeDescriptor}. */
	private final static @NotNull UnfusedPojoTypeDescriptor immutable =
		new UnfusedPojoTypeDescriptor(false);

	/**
	 * Create a new {@link AvailObject} that represents an {@linkplain
	 * UnfusedPojoTypeDescriptor unparameterized pojo type}.
	 *
	 * @param javaClass
	 *        A {@linkplain RawPojoDescriptor raw pojo} that wraps the
	 *        {@linkplain Class Java class or interface} represented by this
	 *        {@linkplain UnfusedPojoTypeDescriptor pojo type}.
	 * @param javaAncestors
	 *        A {@linkplain MapDescriptor map} from {@linkplain PojoDescriptor
	 *        pojos} that wrap {@linkplain Class Java classes and interfaces} to
	 *        their {@linkplain TupleDescriptor type parameterizations}. The
	 *        {@linkplain AvailObject#keysAsSet() keys} constitute this type's
	 *        complete {@linkplain SetDescriptor ancestry} of Java types.
	 * @return The requested pojo type.
	 */
	static @NotNull AvailObject create (
		final @NotNull AvailObject javaClass,
		final @NotNull AvailObject javaAncestors)
	{
		final AvailObject newObject = mutable.create();
		newObject.setSlot(HASH_OR_ZERO, 0);
		newObject.setSlot(JAVA_CLASS, javaClass);
		newObject.setSlot(JAVA_ANCESTORS, javaAncestors);
		newObject.setSlot(TYPE_VARIABLES, NullDescriptor.nullObject());
		newObject.setSlot(SELF_TYPE, NullDescriptor.nullObject());
		return newObject.makeImmutable();
	}
}
