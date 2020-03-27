/*
 * UnfusedPojoTypeDescriptor.java
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
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.maps.A_Map;
import com.avail.descriptor.maps.MapDescriptor;
import com.avail.descriptor.maps.MapDescriptor.Entry;
import com.avail.descriptor.pojos.PojoDescriptor;
import com.avail.descriptor.pojos.RawPojoDescriptor;
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
import java.lang.reflect.TypeVariable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.maps.MapDescriptor.emptyMap;
import static com.avail.descriptor.tuples.StringDescriptor.stringFrom;
import static com.avail.descriptor.types.BottomPojoTypeDescriptor.pojoBottom;
import static com.avail.descriptor.types.FusedPojoTypeDescriptor.createFusedPojoType;
import static com.avail.descriptor.types.SelfPojoTypeDescriptor.newSelfPojoType;
import static com.avail.descriptor.types.UnfusedPojoTypeDescriptor.IntegerSlots.HASH_AND_MORE;
import static com.avail.descriptor.types.UnfusedPojoTypeDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.types.UnfusedPojoTypeDescriptor.ObjectSlots.*;
import static java.lang.reflect.Modifier.*;

/**
 * {@code UnfusedPojoTypeDescriptor} describes a fully-parameterized Java
 * reference type. This is any real Java class or interface that can be loaded
 * via Avail's {@linkplain ClassLoader class loader}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class UnfusedPojoTypeDescriptor
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
		static final BitField HASH_OR_ZERO = bitField(HASH_AND_MORE, 0, 32);
	}

	/** The layout of the object slots. */
	enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} that wraps the {@linkplain
		 * Class Java class or interface} represented by this {@linkplain
		 * UnfusedPojoTypeDescriptor pojo type}.
		 */
		JAVA_CLASS,

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

	@Override @AvailMethod
	protected boolean o_EqualsPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().equalsPojoType(aPojoType);
		}
		if (!object.slot(JAVA_CLASS).equals(aPojoType.javaClass()))
		{
			return false;
		}
		final A_Map ancestors = object.slot(JAVA_ANCESTORS);
		final A_Map otherAncestors = aPojoType.javaAncestors();
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
	 * UnfusedPojoTypeDescriptor object}.
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

	@Override @AvailMethod
	protected boolean o_IsAbstract (final AvailObject object)
	{
		final Class<?> javaClass = object.slot(JAVA_CLASS).javaObjectNotNull();
		return isAbstract(javaClass.getModifiers());
	}

	@Override @AvailMethod
	protected boolean o_IsPojoArrayType (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	protected boolean o_IsPojoFusedType (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	protected AvailObject o_JavaAncestors (final AvailObject object)
	{
		return object.slot(JAVA_ANCESTORS);
	}

	@Override @AvailMethod
	protected AvailObject o_JavaClass (final AvailObject object)
	{
		return object.slot(JAVA_CLASS);
	}

	@Override
	protected @Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return object.slot(JAVA_CLASS).javaObject();
	}

	/**
	 * Lazily compute the self type of the specified {@linkplain
	 * UnfusedPojoTypeDescriptor object}.
	 *
	 * @param object An object.
	 * @return The self type.
	 */
	private A_Type pojoSelfType (final AvailObject object)
	{
		AvailObject selfType = object.slot(SELF_TYPE);
		if (selfType.equalsNil())
		{
			selfType = newSelfPojoType(
				object.slot(JAVA_CLASS),
				object.slot(JAVA_ANCESTORS).keysAsSet());
			if (isShared())
			{
				selfType = selfType.traversed().makeShared();
			}
			object.setSlot(SELF_TYPE, selfType);
		}
		return selfType;
	}

	@Override @AvailMethod
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
		return SerializerOperation.UNFUSED_POJO_TYPE;
	}

	@Override @AvailMethod
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
			aPojoType.typeIntersectionOfPojoUnfusedType(object),
			false);
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersectionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		final Class<?> javaClass = object.slot(JAVA_CLASS).javaObjectNotNull();
		final int modifiers = javaClass.getModifiers();
		// If the unfused pojo type's class is final, then the intersection is
		// pojo bottom.
		if (isFinal(modifiers))
		{
			return pojoBottom();
		}
		// If the unfused pojo type is a class, then check that none of the
		// fused pojo type's ancestors are classes.
		if (!isInterface(modifiers))
		{
			// If any of the fused pojo type's ancestors are Java classes, then
			// the intersection is pojo bottom.
			for (final A_BasicObject ancestor :
				aFusedPojoType.javaAncestors().keysAsSet())
			{
				// Ignore java.lang.Object.
				if (!ancestor.equals(RawPojoDescriptor.rawObjectClass()))
				{
					final Class<?> otherJavaClass =
						ancestor.javaObjectNotNull();
					final int otherModifiers = otherJavaClass.getModifiers();
					if (isFinal(otherModifiers) || !isInterface(otherModifiers))
					{
						return pojoBottom();
					}
				}
			}
		}
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

	@Override @AvailMethod
	protected A_Type o_TypeIntersectionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		final Class<?> javaClass = object.slot(JAVA_CLASS).javaObjectNotNull();
		final Class<?> otherJavaClass =
			anUnfusedPojoType.javaClass().javaObjectNotNull();
		final int modifiers = javaClass.getModifiers();
		final int otherModifiers = otherJavaClass.getModifiers();
		// If either class is declared final, then the intersection is pojo
		// bottom.
		if (isFinal(modifiers) || isFinal(otherModifiers))
		{
			return pojoBottom();
		}
		// If neither class is an interface, then the intersection is pojo
		// bottom (because Java doesn't support multiple inheritance of
		// classes).
		if (!isInterface(modifiers) && !isInterface(otherModifiers))
		{
			return pojoBottom();
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

	@Override @AvailMethod
	protected A_Type o_TypeUnionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().typeUnionOfPojoType(aPojoType);
		}
		return canonicalPojoType(
			aPojoType.typeUnionOfPojoUnfusedType(object),
			false);
	}

	@Override @AvailMethod
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

	@Override @AvailMethod
	protected A_Type o_TypeUnionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		final A_Map intersectionAncestors = computeUnion(
			object, anUnfusedPojoType);
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
	 * UnfusedPojoTypeDescriptor object}.
	 *
	 * @param object An unfused pojo type.
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
			}
			if (isShared())
			{
				typeVars = typeVars.traversed().makeShared();
			}
			object.setSlot(TYPE_VARIABLES, typeVars);
		}
		return typeVars;
	}

	@Override @AvailMethod
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
		final AvailObject javaClass = object.slot(JAVA_CLASS);
		builder.append(javaClass.<Class<?>>javaObjectNotNull().getName());
		final A_Map ancestors = object.slot(JAVA_ANCESTORS);
		final A_Tuple params = ancestors.mapAt(javaClass);
		if (params.tupleSize() != 0)
		{
			builder.append('<');
			boolean first = true;
			for (final A_BasicObject param : params)
			{
				if (!first)
				{
					builder.append(", ");
				}
				first = false;
				param.printOnAvoidingIndent(builder, recursionMap, indent);
			}
			builder.append('>');
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
	 * Construct a new {@code UnfusedPojoTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	UnfusedPojoTypeDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link UnfusedPojoTypeDescriptor}. */
	private static final UnfusedPojoTypeDescriptor mutable =
		new UnfusedPojoTypeDescriptor(Mutability.MUTABLE);

	@Override
	public UnfusedPojoTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link UnfusedPojoTypeDescriptor}. */
	private static final UnfusedPojoTypeDescriptor immutable =
		new UnfusedPojoTypeDescriptor(Mutability.IMMUTABLE);

	@Override
	public UnfusedPojoTypeDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link UnfusedPojoTypeDescriptor}. */
	private static final UnfusedPojoTypeDescriptor shared =
		new UnfusedPojoTypeDescriptor(Mutability.SHARED);

	@Override
	public UnfusedPojoTypeDescriptor shared ()
	{
		return shared;
	}

	/** The most general {@linkplain PojoTypeDescriptor pojo type}. */
	static final A_Type mostGeneralType =
		pojoTypeForClass(Object.class).makeShared();

	/**
	 * Create a new {@link AvailObject} that represents an {@linkplain
	 * UnfusedPojoTypeDescriptor unparameterized pojo type}.
	 *
	 * @param javaClass
	 *        A {@linkplain RawPojoDescriptor raw pojo} that wraps the
	 *        {@linkplain Class Java class or interface} represented by this
	 *        {@code pojo type}.
	 * @param javaAncestors
	 *        A {@linkplain MapDescriptor map} from {@linkplain PojoDescriptor
	 *        pojos} that wrap {@linkplain Class Java classes and interfaces} to
	 *        their {@linkplain TupleDescriptor type parameterizations}. The
	 *        {@linkplain AvailObject#keysAsSet() keys} constitute this type's
	 *        complete {@linkplain SetDescriptor ancestry} of Java types.
	 * @return The requested pojo type.
	 */
	static AvailObject createUnfusedPojoType (
		final AvailObject javaClass,
		final A_BasicObject javaAncestors)
	{
		final AvailObject newObject = mutable.create();
		newObject.setSlot(HASH_OR_ZERO, 0);
		newObject.setSlot(JAVA_CLASS, javaClass);
		newObject.setSlot(JAVA_ANCESTORS, javaAncestors);
		newObject.setSlot(TYPE_VARIABLES, nil);
		newObject.setSlot(SELF_TYPE, nil);
		return newObject.makeImmutable();
	}
}
