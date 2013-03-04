/**
 * FusedPojoTypeDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
 * @author Todd L Smith &lt;todd@availlang.org&gt;
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
		 * pojos} that wrap {@linkplain Class Java classes and interfaces} to
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
		final AbstractSlotsEnum e)
	{
		return e == HASH_OR_ZERO
			|| e == TYPE_VARIABLES
			|| e == SELF_TYPE;
	}

	@Override
	boolean o_EqualsPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().equalsPojoType(aPojoType);
		}
		if (!aPojoType.javaClass().equalsNil())
		{
			return false;
		}
		final A_Map ancestors = object.slot(JAVA_ANCESTORS);
		final A_Map otherAncestors = aPojoType.javaAncestors();
		if (ancestors.mapSize() != otherAncestors.mapSize())
		{
			return false;
		}
		for (final A_Map ancestor : ancestors.keysAsSet())
		{
			if (!otherAncestors.hasKey(ancestor))
			{
				return false;
			}
			final A_Tuple params = ancestors.mapAt(ancestor);
			final A_Tuple otherParams = otherAncestors.mapAt(ancestor);
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
		// (checked by a caller), so coalesce them if possible.
		if (!isShared())
		{
			aPojoType.makeImmutable();
			object.becomeIndirectionTo(aPojoType);
		}
		else if (!aPojoType.descriptor.isShared())
		{
			object.makeImmutable();
			aPojoType.becomeIndirectionTo(object);
		}
		return true;
	}

	/**
	 * Lazily compute the hash of the specified {@linkplain
	 * FusedPojoTypeDescriptor object}.
	 *
	 * @param object An object.
	 * @return The hash.
	 */
	private int hash (final AvailObject object)
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
	int o_Hash (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return hash(object);
			}
		}
		return hash(object);
	}

	@Override
	boolean o_IsAbstract (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsPojoArrayType (final AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_IsPojoFusedType (final AvailObject object)
	{
		return true;
	}

	@Override
	AvailObject o_JavaAncestors (final AvailObject object)
	{
		return object.slot(JAVA_ANCESTORS);
	}

	@Override
	AvailObject o_JavaClass (final AvailObject object)
	{
		return NilDescriptor.nil();
	}

	@Override
	Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		// TODO: [TLS] Answer the nearest mutual parent of the leaf types.
		return Object.class;
	}

	/**
	 * Lazily compute the self type of the specified {@linkplain
	 * FusedPojoTypeDescriptor object}.
	 *
	 * @param object An object.
	 * @return The self type.
	 */
	private AvailObject pojoSelfType (final AvailObject object)
	{
		AvailObject selfType = object.slot(SELF_TYPE);
		if (selfType.equalsNil())
		{
			selfType = SelfPojoTypeDescriptor.create(
				NilDescriptor.nil(),
				object.slot(JAVA_ANCESTORS).keysAsSet());
			if (isShared())
			{
				selfType = selfType.traversed().makeShared();
			}
			object.setSlot(SELF_TYPE, selfType);
		}
		return selfType;
	}

	@Override
	A_Type o_PojoSelfType (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return pojoSelfType(object);
			}
		}
		return pojoSelfType(object);
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.FUSED_POJO_TYPE;
	}

	@Override
	A_Type o_TypeIntersectionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
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
	A_Type o_TypeIntersectionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		final A_BasicObject intersection =
			computeIntersection(object, aFusedPojoType);
		if (intersection.equalsPojoBottomType())
		{
			return PojoTypeDescriptor.pojoBottom();
		}
		// The result will be a pojo fused type. Find the union of the key sets
		// and the intersection of their parameterizations.
		return FusedPojoTypeDescriptor.create((A_Map)intersection);
	}

	@Override
	A_Type o_TypeIntersectionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
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
			for (final A_BasicObject ancestor :
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
		final A_BasicObject intersection =
			computeIntersection(object, anUnfusedPojoType);
		if (intersection.equalsPojoBottomType())
		{
			return PojoTypeDescriptor.pojoBottom();
		}
		// The result will be a pojo fused type. Find the union of the key sets
		// and the intersection of their parameterizations.
		return FusedPojoTypeDescriptor.create((A_Map)intersection);
	}

	@Override
	A_Type o_TypeUnionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().typeUnionOfPojoType(aPojoType);
		}
		return aPojoType.typeUnionOfPojoType(object);
	}

	@Override
	A_Type o_TypeUnionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		final A_Map intersectionAncestors = computeUnion(
			object, aFusedPojoType);
		final AvailObject javaClass = mostSpecificOf(
			intersectionAncestors.keysAsSet());
		// If the intersection contains a most specific type, then the answer is
		// not a fused pojo type; otherwise it is.
		return !javaClass.equalsNil()
			? UnfusedPojoTypeDescriptor.create(javaClass, intersectionAncestors)
			: create(intersectionAncestors);
	}

	@Override
	A_Type o_TypeUnionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		final A_Map intersectionAncestors =
			computeUnion(object, anUnfusedPojoType);
		final AvailObject javaClass = mostSpecificOf(
			intersectionAncestors.keysAsSet());
		// If the intersection contains a most specific type, then the answer is
		// not a fused pojo type; otherwise it is.
		return !javaClass.equalsNil()
			? UnfusedPojoTypeDescriptor.create(javaClass, intersectionAncestors)
			: create(intersectionAncestors);
	}

	/**
	 * Lazily compute the type variables of the specified {@linkplain
	 * FusedPojoTypeDescriptor object}.
	 *
	 * @param object An object.
	 * @return The type variables.
	 */
	private A_Map typeVariables (final AvailObject object)
	{
		A_Map typeVars = object.slot(TYPE_VARIABLES);
		if (typeVars.equalsNil())
		{
			typeVars = MapDescriptor.empty();
			for (final MapDescriptor.Entry entry
				: object.slot(JAVA_ANCESTORS).mapIterable())
			{
				final Class<?> ancestor = (Class<?>) entry.key().javaObject();
				final TypeVariable<?>[] vars = ancestor.getTypeParameters();
				final A_Tuple typeArgs = entry.value();
				assert vars.length == typeArgs.tupleSize();
				for (int i = 0; i < vars.length; i++)
				{
					typeVars = typeVars.mapAtPuttingCanDestroy(
						StringDescriptor.from(
							ancestor.getName() + "." + vars[i].getName()),
						typeArgs.tupleAt(i + 1),
						true);
				}
				if (isShared())
				{
					typeVars = typeVars.traversed().makeShared();
				}
			}
			object.setSlot(TYPE_VARIABLES, typeVars);
		}
		return typeVars;
	}

	@Override
	A_Map o_TypeVariables (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return typeVariables(object);
			}
		}
		return typeVariables(object);
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		final A_Map ancestors = object.slot(JAVA_ANCESTORS);
		final List<AvailObject> childless = new ArrayList<AvailObject>(
			childlessAmong(ancestors.keysAsSet()));
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
			final A_Tuple params = ancestors.hasKey(javaClass)
				? ancestors.mapAt(javaClass)
				: TupleDescriptor.empty();
			boolean firstParam = true;
			if (params.tupleSize() != 0)
			{
				builder.append('<');
				for (final A_BasicObject param : params)
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
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public FusedPojoTypeDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link FusedPojoTypeDescriptor}. */
	private final static FusedPojoTypeDescriptor mutable =
		new FusedPojoTypeDescriptor(Mutability.MUTABLE);

	@Override
	FusedPojoTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link FusedPojoTypeDescriptor}. */
	private final static FusedPojoTypeDescriptor immutable =
		new FusedPojoTypeDescriptor(Mutability.IMMUTABLE);

	@Override
	FusedPojoTypeDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link FusedPojoTypeDescriptor}. */
	private final static FusedPojoTypeDescriptor shared =
		new FusedPojoTypeDescriptor(Mutability.SHARED);

	@Override
	FusedPojoTypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Create a new {@link AvailObject} that represents an {@linkplain
	 * FusedPojoTypeDescriptor unparameterized pojo type}.
	 *
	 * @param javaAncestors
	 *        A {@linkplain MapDescriptor map} from {@linkplain PojoDescriptor
	 *        pojos} that wrap {@linkplain Class Java classes and interfaces} to
	 *        their {@linkplain TupleDescriptor type parameterizations}. The
	 *        {@linkplain AvailObject#keysAsSet() keys} constitute this type's
	 *        complete {@linkplain SetDescriptor ancestry} of Java types.
	 * @return The requested pojo type.
	 */
	static AvailObject create (final A_Map javaAncestors)
	{
		final AvailObject newObject = mutable.create();
		newObject.setSlot(HASH_OR_ZERO, 0);
		newObject.setSlot(JAVA_ANCESTORS, javaAncestors);
		newObject.setSlot(TYPE_VARIABLES, NilDescriptor.nil());
		newObject.setSlot(SELF_TYPE, NilDescriptor.nil());
		return newObject.makeImmutable();
	}
}
