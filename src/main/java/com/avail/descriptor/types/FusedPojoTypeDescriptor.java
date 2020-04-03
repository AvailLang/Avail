/*
 * FusedPojoTypeDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.descriptor.types;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.maps.A_Map;
import com.avail.descriptor.maps.MapDescriptor;
import com.avail.descriptor.maps.MapDescriptor.Entry;
import com.avail.descriptor.pojos.PojoDescriptor;
import com.avail.descriptor.pojos.RawPojoDescriptor;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.AbstractSlotsEnum;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.sets.SetDescriptor;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.tuples.StringDescriptor;
import com.avail.descriptor.tuples.TupleDescriptor;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.maps.MapDescriptor.emptyMap;
import static com.avail.descriptor.tuples.StringDescriptor.stringFrom;
import static com.avail.descriptor.tuples.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.types.BottomPojoTypeDescriptor.pojoBottom;
import static com.avail.descriptor.types.FusedPojoTypeDescriptor.IntegerSlots.HASH_AND_MORE;
import static com.avail.descriptor.types.FusedPojoTypeDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.types.FusedPojoTypeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.types.SelfPojoTypeDescriptor.newSelfPojoType;
import static com.avail.descriptor.types.UnfusedPojoTypeDescriptor.createUnfusedPojoType;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isInterface;

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
	enum IntegerSlots implements IntegerSlotsEnumJava
	{
		/**
		 * The low 32 bits are used for the {@link #HASH_OR_ZERO}, but the upper
		 * 32 can be used by other {@link BitField}s in subclasses.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * A slot to hold the hash value, or zero if it has not been computed.
		 * The hash of an atom is a random number, computed once.
		 */
		static final BitField HASH_OR_ZERO = new BitField(HASH_AND_MORE, 0, 32);
	}

	/** The layout of the object slots. */
	enum ObjectSlots implements ObjectSlotsEnumJava
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
	protected boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == HASH_AND_MORE
			|| e == TYPE_VARIABLES
			|| e == SELF_TYPE;
	}

	@Override
	protected boolean o_EqualsPojoType (
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
		else if (!aPojoType.descriptor().isShared())
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
	private static int hash (final AvailObject object)
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
	public int o_Hash (final AvailObject object)
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
	protected boolean o_IsAbstract (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	protected boolean o_IsPojoArrayType (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsPojoFusedType (final AvailObject object)
	{
		return true;
	}

	@Override
	protected AvailObject o_JavaAncestors (final AvailObject object)
	{
		return object.slot(JAVA_ANCESTORS);
	}

	@Override
	protected AvailObject o_JavaClass (final AvailObject object)
	{
		return nil;
	}

	@Override
	protected Object o_MarshalToJava (
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
			selfType = newSelfPojoType(
				nil, object.slot(JAVA_ANCESTORS).keysAsSet());
			if (isShared())
			{
				selfType = selfType.traversed().makeShared();
			}
			object.setSlot(SELF_TYPE, selfType);
		}
		return selfType;
	}

	@Override
	protected A_Type o_PojoSelfType (final AvailObject object)
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
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.FUSED_POJO_TYPE;
	}

	@Override
	protected A_Type o_TypeIntersectionOfPojoType (
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
			return pojoBottom();
		}
		return canonicalPojoType(
			aPojoType.typeIntersectionOfPojoFusedType(object),
			false);
	}

	@Override
	protected A_Type o_TypeIntersectionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		final A_BasicObject intersection =
			computeIntersection(object, aFusedPojoType);
		if (intersection.equalsPojoBottomType())
		{
			return pojoBottom();
		}
		// The result will be a pojo fused type. Find the union of the key sets
		// and the intersection of their parameterizations.
		return createFusedPojoType((A_Map)intersection);
	}

	@Override
	protected A_Type o_TypeIntersectionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		final Class<?> otherJavaClass =
			anUnfusedPojoType.javaClass().javaObjectNotNull();
		final int otherModifiers = otherJavaClass.getModifiers();
		// If the unfused pojo type's class is final, then the intersection is
		// pojo bottom.
		if (isFinal(otherModifiers))
		{
			return pojoBottom();
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
					final Class<?> javaClass = ancestor.javaObjectNotNull();
					final int modifiers = javaClass.getModifiers();
					if (isFinal(modifiers) || !isInterface(modifiers))
					{
						return pojoBottom();
					}
				}
			}
		}
		final A_BasicObject intersection =
			computeIntersection(object, anUnfusedPojoType);
		if (intersection.equalsPojoBottomType())
		{
			return pojoBottom();
		}
		// The result will be a pojo fused type. Find the union of the key sets
		// and the intersection of their parameterizations.
		return createFusedPojoType((A_Map)intersection);
	}

	@Override
	protected A_Type o_TypeUnionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().typeUnionOfPojoType(aPojoType);
		}
		return canonicalPojoType(
			aPojoType.typeUnionOfPojoType(object),
			false);
	}

	@Override
	protected A_Type o_TypeUnionOfPojoFusedType (
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
			? createUnfusedPojoType(javaClass, intersectionAncestors)
			: createFusedPojoType(intersectionAncestors);
	}

	@Override
	protected A_Type o_TypeUnionOfPojoUnfusedType (
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
			? createUnfusedPojoType(javaClass, intersectionAncestors)
			: createFusedPojoType(intersectionAncestors);
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
			typeVars = emptyMap();
			for (final Entry entry
				: object.slot(JAVA_ANCESTORS).mapIterable())
			{
				final Class<?> ancestor = entry.key().javaObjectNotNull();
				final TypeVariable<?>[] vars = ancestor.getTypeParameters();
				final A_Tuple typeArgs = entry.value();
				assert vars.length == typeArgs.tupleSize();
				for (int i = 0; i < vars.length; i++)
				{
					typeVars = typeVars.mapAtPuttingCanDestroy(
						stringFrom(
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
	protected A_Map o_TypeVariables (final AvailObject object)
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
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		final A_Map ancestors = object.slot(JAVA_ANCESTORS);
		final List<AvailObject> childless = new ArrayList<>(
			childlessAmong(ancestors.keysAsSet()));
		childless.sort((o1, o2) ->
		{
			assert o1 != null && o2 != null;
			final Class<?> c1 = o1.javaObjectNotNull();
			final Class<?> c2 = o2.javaObjectNotNull();
			return c1.getName().compareTo(c2.getName());
		});
		boolean firstChildless = true;
		for (final AvailObject javaClass : childless)
		{
			if (!firstChildless)
			{
				builder.append(" ∩ ");
			}
			firstChildless = false;
			builder.append((javaClass.<Class<?>>javaObjectNotNull()).getName());
			final A_Tuple params = ancestors.hasKey(javaClass)
				? ancestors.mapAt(javaClass)
				: emptyTuple();
			if (params.tupleSize() != 0)
			{
				builder.append('<');
				boolean firstParam = true;
				for (final A_BasicObject param : params)
				{
					if (!firstParam)
					{
						builder.append(", ");
					}
					firstParam = false;
					param.printOnAvoidingIndent(builder, recursionMap, indent);
				}
				builder.append('>');
			}
		}
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("pojo type");
		writer.write("class");
		writer.write(object.toString());
		writer.endObject();
	}

	/**
	 * Construct a new {@link FusedPojoTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	FusedPojoTypeDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link FusedPojoTypeDescriptor}. */
	private static final FusedPojoTypeDescriptor mutable =
		new FusedPojoTypeDescriptor(Mutability.MUTABLE);

	@Override
	public FusedPojoTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link FusedPojoTypeDescriptor}. */
	private static final FusedPojoTypeDescriptor immutable =
		new FusedPojoTypeDescriptor(Mutability.IMMUTABLE);

	@Override
	public FusedPojoTypeDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link FusedPojoTypeDescriptor}. */
	private static final FusedPojoTypeDescriptor shared =
		new FusedPojoTypeDescriptor(Mutability.SHARED);

	@Override
	public FusedPojoTypeDescriptor shared ()
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
	static AvailObject createFusedPojoType (final A_Map javaAncestors)
	{
		final AvailObject newObject = mutable.create();
		newObject.setSlot(HASH_OR_ZERO, 0);
		newObject.setSlot(JAVA_ANCESTORS, javaAncestors);
		newObject.setSlot(TYPE_VARIABLES, nil);
		newObject.setSlot(SELF_TYPE, nil);
		return newObject.makeImmutable();
	}
}
