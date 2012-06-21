/**
 * FusedPojoTypeDescriptor.java
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

import static com.avail.descriptor.FusedPojoTypeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.FusedPojoTypeDescriptor.ObjectSlots.*;
import static java.lang.reflect.Modifier.*;
import java.io.Serializable;
import java.lang.reflect.TypeVariable;
import java.util.*;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * {@code FusedPojoTypeDescriptor} describes synthetic points in Avail's pojo
 * type hierarchy. This is a superset of Java's own reference type hierarchy. In
 * particular, the pojo type hierarchy includes type unions and type
 * intersections that may still conform to actual (but unspecified) Java classes
 * and interfaces. For instance, the type intersection of {@link Cloneable} and
 * {@link Serializable} describes <strong>1)</strong> any interface that extends
 * both and <strong>2)</strong> any class that implements both.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
final class FusedPojoTypeDescriptor
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

	@Override
	boolean o_EqualsPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().equalsPojoType(aPojoType);
		}
		if (!aPojoType.javaClass().equalsNull())
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

	@Override
	boolean o_IsAbstract (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsPojoArrayType (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_IsPojoFusedType (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override
	@NotNull AvailObject o_JavaAncestors (final @NotNull AvailObject object)
	{
		return object.slot(JAVA_ANCESTORS);
	}

	@Override
	@NotNull AvailObject o_JavaClass (final @NotNull AvailObject object)
	{
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_MakeImmutable (final @NotNull AvailObject object)
	{
		object.descriptor = immutable;
		object.slot(JAVA_ANCESTORS).makeImmutable();
		return object;
	}

	@Override
	Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> ignoredClassHint)
	{
		// TODO: [TLS] Answer the nearest mutual parent of the leaf types.
		return Object.class;
	}

	@Override
	@NotNull AvailObject o_PojoSelfType (final @NotNull AvailObject object)
	{
		AvailObject selfType = object.slot(SELF_TYPE);
		if (selfType.equalsNull())
		{
			selfType = SelfPojoTypeDescriptor.create(
				NullDescriptor.nullObject(),
				object.slot(JAVA_ANCESTORS).keysAsSet());
			object.setSlot(SELF_TYPE, selfType);
		}
		return selfType;
	}

	@Override @AvailMethod @ThreadSafe
	@NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
	{
		return SerializerOperation.FUSED_POJO_TYPE;
	}

	@Override
	AvailObject o_TypeIntersectionOfPojoType (
		final @NotNull AvailObject object,
		final AvailObject aPojoType)
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
		return aPojoType.typeIntersectionOfPojoFusedType(object);
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfPojoFusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFusedPojoType)
	{
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

	@Override
	@NotNull AvailObject o_TypeIntersectionOfPojoUnfusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anUnfusedPojoType)
	{
		final Class<?> otherJavaClass =
			(Class<?>) object.javaClass().javaObject();
		final int otherModifiers = otherJavaClass.getModifiers();
		// If the unfused pojo type's class is final, then the intersection is
		// pojo bottom.
		if (isFinal(otherModifiers))
		{
			return PojoTypeDescriptor.pojoBottom();
		}
		// If the unfused pojo type is a class, then check that none of the
		// fused pojo type's ancestors are classes.
		if (!isInterface(otherModifiers))
		{
			// If any of the fused pojo type's ancestors are Java classes, then
			// the intersection is pojo bottom.
			for (final AvailObject ancestor :
				object.slot(JAVA_ANCESTORS).keysAsSet())
			{
				// Ignore java.lang.Object.
				if (!ancestor.equals(RawPojoDescriptor.rawObjectClass()))
				{
					final Class<?> javaClass = (Class<?>) ancestor.javaObject();
					final int modifiers = javaClass.getModifiers();
					if (isFinal(modifiers) || !isInterface(modifiers))
					{
						return PojoTypeDescriptor.pojoBottom();
					}
				}
			}
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

	@Override
	@NotNull AvailObject o_TypeUnionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().typeUnionOfPojoType(aPojoType);
		}
		return aPojoType.typeUnionOfPojoType(object);
	}

	@Override
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
			? UnfusedPojoTypeDescriptor.create(javaClass, intersectionAncestors)
			: create(intersectionAncestors);
	}

	@Override
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
			? UnfusedPojoTypeDescriptor.create(javaClass, intersectionAncestors)
			: create(intersectionAncestors);
	}

	@Override
	@NotNull AvailObject o_TypeVariables (final @NotNull AvailObject object)
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
		final AvailObject ancestors = object.slot(JAVA_ANCESTORS);
		final List<AvailObject> childless = new ArrayList<AvailObject>(
			childlessAmong(ancestors.keysAsSet()));
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
		boolean firstChildless = true;
		for (final AvailObject javaClass : childless)
		{
			if (!firstChildless)
			{
				builder.append(" ∩ ");
			}
			firstChildless = false;
			builder.append(((Class<?>) javaClass.javaObject()).getName());
			final AvailObject params = ancestors.hasKey(javaClass)
				? ancestors.mapAt(javaClass)
				: TupleDescriptor.empty();
			boolean firstParam = true;
			if (params.tupleSize() != 0)
			{
				builder.append('<');
				for (final AvailObject param : params)
				{
					if (!firstParam)
					{
						builder.append(", ");
					}
					firstParam = false;
					param.printOnAvoidingIndent(builder, recursionList, indent);
				}
				builder.append('>');
			}
		}
	}

	/**
	 * Construct a new {@link FusedPojoTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain FusedPojoTypeDescriptor descriptor}
	 *        represent a mutable object?
	 */
	public FusedPojoTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link FusedPojoTypeDescriptor}. */
	private final static @NotNull FusedPojoTypeDescriptor mutable =
		new FusedPojoTypeDescriptor(true);

	/**
	 * Answer the mutable {@link FusedPojoTypeDescriptor}.
	 *
	 * @return The mutable {@code FusedPojoTypeDescriptor}.
	 */
	static @NotNull FusedPojoTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link FusedPojoTypeDescriptor}. */
	private final static @NotNull FusedPojoTypeDescriptor immutable =
		new FusedPojoTypeDescriptor(false);

	/**
	 * Create a new {@link AvailObject} that represents an {@linkplain
	 * FusedPojoTypeDescriptor unparameterized pojo type}.
	 *
	 * @param javaAncestors
	 *        A {@linkplain MapDescriptor map} from {@linkplain PojoDescriptor
	 *        pojos} that wrap {@Linkplain Class Java classes and interfaces} to
	 *        their {@linkplain TupleDescriptor type parameterizations}. The
	 *        {@linkplain AvailObject#keysAsSet() keys} constitute this type's
	 *        complete {@linkplain SetDescriptor ancestry} of Java types.
	 * @return The requested pojo type.
	 */
	static @NotNull AvailObject create (
		final @NotNull AvailObject javaAncestors)
	{
		final AvailObject newObject = mutable.create();
		newObject.setSlot(HASH_OR_ZERO, 0);
		newObject.setSlot(JAVA_ANCESTORS, javaAncestors);
		newObject.setSlot(SELF_TYPE, NullDescriptor.nullObject());
		return newObject.makeImmutable();
	}
}
