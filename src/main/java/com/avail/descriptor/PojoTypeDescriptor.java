/*
 * PojoTypeDescriptor.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.annotations.AvailMethod;
import com.avail.exceptions.MarshalingException;
import com.avail.utility.LRUCache;
import com.avail.utility.Mutable;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.avail.descriptor.ArrayPojoTypeDescriptor.arrayPojoType;
import static com.avail.descriptor.AtomDescriptor.createSpecialAtom;
import static com.avail.descriptor.AtomDescriptor.objectFromBoolean;
import static com.avail.descriptor.BottomPojoTypeDescriptor.pojoBottom;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.DoubleDescriptor.fromDouble;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FloatDescriptor.fromFloat;
import static com.avail.descriptor.FusedPojoTypeDescriptor.createFusedPojoType;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerDescriptor.fromBigInteger;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerDescriptor.fromLong;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.int32;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.int64;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.integers;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.PojoDescriptor.newPojo;
import static com.avail.descriptor.PojoDescriptor.nullPojo;
import static com.avail.descriptor.RawPojoDescriptor.equalityPojo;
import static com.avail.descriptor.RawPojoDescriptor.rawObjectClass;
import static com.avail.descriptor.SelfPojoTypeDescriptor.newSelfPojoType;
import static com.avail.descriptor.SetDescriptor.setFromCollection;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.DOUBLE;
import static com.avail.descriptor.TypeDescriptor.Types.FLOAT;
import static com.avail.descriptor.TypeDescriptor.Types.NONTYPE;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.UnfusedPojoTypeDescriptor.createUnfusedPojoType;
import static com.avail.utility.Casts.nullableCast;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.Short.MAX_VALUE;

/**
 * An {@code PojoTypeDescriptor} describes the type of a plain-old Java
 * object (pojo) that is accessible to an Avail programmer as an {@linkplain
 * AvailObject Avail object}.
 *
 * <p>Even though Java uses type erasure for its generic types, Java class files
 * contain enough reflectively available information about genericity for Avail
 * to expose Java types as if they were fully polymorphic (like Avail's own
 * types). Avail does not need to create new Java types by extending the Java
 * class hierarchy, so there is no need to model Java generic types directly.
 * Polymorphic types are therefore sufficient for construction and employment,
 * which runs the gamut of purposes from an Avail programmer's perspective.</p>
 *
 * <p>Java interfaces are presented to Avail as though they were Java classes.
 * Avail sees interface inheritance as though it were class inheritance, with
 * root interfaces implicitly inheriting from {@link Object}. So an Avail
 * programmer sees Java as though it supported multiple inheritance of classes.
 * </p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class PojoTypeDescriptor
extends TypeDescriptor
{
	/**
	 * {@code Canon} specifies a {@linkplain Map map} from {@linkplain Class
	 * Java classes} to {@linkplain TupleDescriptor type parameterization
	 * tuples}.
	 */
	private static final class Canon
	extends HashMap<Class<?>, AvailObject>
	{
		/**
		 * Construct a new {@code Canon} that has initial capacity for five
		 * bindings and includes a binding for {@link Object java.lang.Object}.
		 */
		Canon ()
		{
			super(5);
			put(Object.class, rawObjectClass());
		}

		/**
		 * Answer the locally canonical {@linkplain RawPojoDescriptor raw pojo}
		 * that represents the specified {@linkplain Class Java class}. (If the
		 * canon already contains a raw pojo for the class, then answer it. If
		 * not, then install a new one and answer that one.)
		 *
		 * @param javaClass
		 *        A Java class or interface.
		 * @return A locally canonical raw pojo corresponding to the argument.
		 */
		AvailObject canonize (final Class<?> javaClass)
		{
			AvailObject rawPojo = get(javaClass);
			if (rawPojo == null)
			{
				rawPojo = equalityPojo(javaClass);
				put(javaClass, rawPojo);
			}
			return rawPojo;
		}
	}

	/**
	 * {@code TypeVariableMap} is a {@linkplain Map map} from {@linkplain String
	 * local type variable names} to their type parameterization indices.
	 */
	private static final class TypeVariableMap
	extends HashMap<String, Integer>
	{
		/**
		 * Construct a new {@code TypeVariableMap} for the specified {@linkplain
		 * Class Java class or interface}.
		 *
		 * @param javaClass
		 *        A Java class or interface.
		 */
		TypeVariableMap (final Class<?> javaClass)
		{
			super(2);
			final TypeVariable<?>[] vars = javaClass.getTypeParameters();
			for (int i = 0; i < vars.length; i++)
			{
				put(vars[i].getName(), i);
			}
		}
	}

	/**
	 * {@code LRUCacheKey} combines a {@linkplain Class Java class or interface}
	 * with its complete type parameterization. It serves as the key to the
	 * {@linkplain PojoTypeDescriptor pojo type} {@linkplain #cache}.
	 */
	private static final class LRUCacheKey
	{
		/** The {@linkplain Class Java class or interface}. */
		public final Class<?> javaClass;

		/** The type arguments. */
		final A_Tuple typeArgs;

		@Override
		public boolean equals (final @Nullable Object obj)
		{
			if (obj instanceof LRUCacheKey)
			{
				final LRUCacheKey other = (LRUCacheKey) obj;
				return javaClass.equals(other.javaClass)
					&& typeArgs.equals(other.typeArgs);
			}
			return false;
		}

		@Override
		public int hashCode ()
		{
			return javaClass.hashCode() * typeArgs.hash() ^ 0x1FA07381;
		}

		/**
		 * Construct a new {@code LRUCacheKey}.
		 *
		 * @param javaClass
		 *        The {@linkplain Class Java class or interface}.
		 * @param typeArgs
		 *        The type arguments.
		 */
		LRUCacheKey (
			final Class<?> javaClass,
			final A_Tuple typeArgs)
		{
			this.javaClass = javaClass;
			this.typeArgs = typeArgs;
		}
	}

	/**
	 * Answer the most general pojo type.
	 *
	 * @return The most general pojo type.
	 */
	public static A_Type mostGeneralPojoType ()
	{
		return UnfusedPojoTypeDescriptor.mostGeneralType;
	}

	/**
	 * Answer the most general pojo array type.
	 *
	 * @return The most general pojo array type.
	 */
	public static A_Type mostGeneralPojoArrayType ()
	{
		return ArrayPojoTypeDescriptor.mostGeneralType;
	}

	/**
	 * A special {@linkplain AtomDescriptor atom} whose {@linkplain
	 * InstanceTypeDescriptor instance type} represents the self type of a
	 * {@linkplain Class Java class or interface}.
	 */
	private static final A_Atom selfTypeAtom =
		createSpecialAtom("pojo self");

	/**
	 * Answer a special {@linkplain AtomDescriptor atom} whose {@linkplain
	 * InstanceTypeDescriptor instance type} represents the self type of a
	 * {@linkplain Class Java class or interface}.
	 *
	 * @return The pojo self type atom.
	 */
	public static A_Atom pojoSelfTypeAtom ()
	{
		return selfTypeAtom;
	}

	/**
	 * A special {@linkplain InstanceTypeDescriptor instance type} that
	 * represents the self type of a {@linkplain Class Java class or interface}.
	 */
	private static final A_Type selfType =
		instanceType(selfTypeAtom).makeShared();

	/**
	 * Answer a special {@linkplain InstanceTypeDescriptor instance type} that
	 * represents the self type of a {@linkplain Class Java class or interface}.
	 *
	 * @return The pojo self type atom.
	 */
	public static A_Type pojoSelfType ()
	{
		return selfType;
	}

	/**
	 * The {@linkplain IntegerRangeTypeDescriptor integer range type} that
	 * corresponds to Java {@code byte}.
	 */
	private static final A_Type byteRange =
		inclusive(Byte.MIN_VALUE, Byte.MAX_VALUE).makeShared();

	/**
	 * Answer the {@linkplain IntegerRangeTypeDescriptor integer range type}
	 * that corresponds to Java {@code byte}.
	 *
	 * @return {@code [-128..127]}.
	 */
	public static A_Type byteRange ()
	{
		return byteRange;
	}

	/**
	 * The {@linkplain IntegerRangeTypeDescriptor integer range type} that
	 * corresponds to Java {@code short}.
	 */
	private static final A_Type shortRange =
		inclusive(Short.MIN_VALUE, MAX_VALUE).makeShared();

	/**
	 * Answer the {@linkplain IntegerRangeTypeDescriptor integer range type}
	 * that corresponds to Java {@code short}.
	 *
	 * @return {@code [-32768..32767]}.
	 */
	public static A_Type shortRange ()
	{
		return shortRange;
	}

	/**
	 * The {@linkplain IntegerRangeTypeDescriptor integer range type} that
	 * corresponds to Java {@code int}.
	 */
	private static final A_Type intRange = int32();

	/**
	 * Answer the {@linkplain IntegerRangeTypeDescriptor integer range type}
	 * that corresponds to Java {@code int}.
	 *
	 * @return {@code [-2147483648..2147483647]}.
	 */
	public static A_Type intRange ()
	{
		return intRange;
	}

	/**
	 * The {@linkplain IntegerRangeTypeDescriptor integer range type} that
	 * corresponds to Java {@code long}.
	 */
	private static final A_Type longRange = int64();

	/**
	 * Answer the {@linkplain IntegerRangeTypeDescriptor integer range type}
	 * that corresponds to Java {@code long}.
	 *
	 * @return {@code [-9223372036854775808..9223372036854775807]}.
	 */
	public static A_Type longRange ()
	{
		return longRange;
	}

	/**
	 * The {@linkplain IntegerRangeTypeDescriptor integer range type} that
	 * corresponds to Java {@code char}.
	 */
	private static final A_Type charRange =
		inclusive(Character.MIN_VALUE, Character.MAX_VALUE).makeShared();

	/**
	 * Answer the {@linkplain IntegerRangeTypeDescriptor integer range type}
	 * that corresponds to Java {@code char}.
	 *
	 * @return {@code [-9223372036854775808..9223372036854775807]}.
	 */
	public static A_Type charRange ()
	{
		return charRange;
	}

	/**
	 * Given an {@link LRUCacheKey}, compute the corresponding {@linkplain
	 * PojoTypeDescriptor pojo type}.
	 *
	 * @param key An {@code LRUCacheKey}.
	 * @return A pojo type.
	 */
	static AvailObject computeValue (final LRUCacheKey key)
	{
		// Java allows the operations defined in java.lang.Object to be
		// performed on interface types, so interfaces are implicitly subtypes
		// of java.lang.Object. Make this relationship explicit: seed the
		// ancestry with java.lang.Object.
		final Canon canon = new Canon();
		final Mutable<A_Map> ancestors = new Mutable<>(emptyMap());
		ancestors.value = ancestors.value.mapAtPuttingCanDestroy(
			canon.get(Object.class), emptyTuple(), true);
		computeAncestry(key.javaClass, key.typeArgs, ancestors, canon);
		return createUnfusedPojoType(canon.get(key.javaClass), ancestors.value);
	}

	/**
	 * {@linkplain PojoTypeDescriptor Pojo types} are somewhat expensive
	 * to build, so cache them for efficiency.
	 */
	private static final LRUCache<LRUCacheKey, AvailObject> cache =
		new LRUCache<>(1000, 10, PojoTypeDescriptor::computeValue);

	@Override @AvailMethod
	final boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another)
	{
		// Short circuit if the arguments are reference identical.
		if (another.traversed().sameAddressAs(object))
		{
			return true;
		}
		// Note that pojo bottom is a pojo array type.
		return
			object.isPojoType() == another.isPojoType()
			&& object.isPojoFusedType() == another.isPojoFusedType()
			&& object.isPojoArrayType() == another.isPojoArrayType()
			&& object.hash() == another.hash()
			&& another.equalsPojoType(object);
	}

	@Override @AvailMethod
	abstract boolean o_EqualsPojoType (
		AvailObject object,
		AvailObject aPojoType);

	@Override @AvailMethod
	abstract int o_Hash (AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsAbstract (AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsPojoArrayType (AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsPojoFusedType (AvailObject object);

	@Override @AvailMethod
	boolean o_IsPojoSelfType (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsPojoType (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final AvailObject object,
		final A_Type aType)
	{
		return aType.isSupertypeOfPojoType(object);
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfPojoBottomType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		// Every pojo type is a supertype of pojo bottom.
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		// If aPojoType is a self type, then answer whether object's self type
		// is a supertype of aPojoType.
		if (aPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().isSupertypeOfPojoType(aPojoType);
		}
		// Check type compatibility by computing the set intersection of the
		// unparameterized ancestry of the arguments. If the result is not equal
		// to the unparameterized ancestry of object, then object is not a
		// supertype of aPojoType.
		final A_Map ancestors = object.javaAncestors();
		final A_Map otherAncestors = aPojoType.javaAncestors();
		final A_Set javaClasses = ancestors.keysAsSet();
		final A_Set otherJavaClasses = otherAncestors.keysAsSet();
		final A_Set intersection =
			javaClasses.setIntersectionCanDestroy(otherJavaClasses, false);
		if (!javaClasses.equals(intersection))
		{
			return false;
		}
		// For each Java class in the intersection, ensure that the
		// parameterizations are compatible. Java's type parameters are
		// (brokenly) always covariant, so check that the type arguments of
		// aPojoType are subtypes of the corresponding type argument of object.
		for (final AvailObject javaClass : intersection)
		{
			final A_Tuple params = ancestors.mapAt(javaClass);
			final A_Tuple otherParams = otherAncestors.mapAt(javaClass);
			final int limit = params.tupleSize();
			for (int i = 1; i <= limit; i++)
			{
				final A_Type x = params.tupleAt(i);
				final A_Type y = otherParams.tupleAt(i);
				if (!y.isSubtypeOf(x))
				{
					return false;
				}
			}
		}
		// If object is a supertype of aPojoType sans any embedded pojo self
		// types, then object is really a supertype of aPojoType. The
		// corresponding pojo self types must be compatible, and that
		// compatibility has already been checked indirectly by some part of the
		// above computation.
		return true;
	}

	@Override @AvailMethod
	abstract AvailObject o_JavaAncestors (AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_JavaClass (AvailObject object);

	@Override @AvailMethod
	abstract @Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint);

	@Override @AvailMethod
	abstract A_Type o_PojoSelfType (
		AvailObject object);

	@Override @AvailMethod
	final A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
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
	abstract A_Type o_TypeIntersectionOfPojoType (
		AvailObject object,
		A_Type aPojoType);

	@Override
	abstract A_Type o_TypeIntersectionOfPojoFusedType (
		AvailObject object,
		A_Type aFusedPojoType);

	@Override
	abstract A_Type o_TypeIntersectionOfPojoUnfusedType (
		AvailObject object,
		A_Type anUnfusedPojoType);

	@Override
	final A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
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
	abstract A_Type o_TypeUnionOfPojoType (
		AvailObject object,
		A_Type aPojoType);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfPojoFusedType (
		AvailObject object,
		A_Type aFusedPojoType);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfPojoUnfusedType (
		AvailObject object,
		A_Type anUnfusedPojoType);

	@Override @AvailMethod
	abstract A_Map o_TypeVariables (
		AvailObject object);

	/**
	 * Compute the intersection of two {@linkplain PojoTypeDescriptor
	 * pojo types}. This is utility method that only examines the {@linkplain
	 * AvailObject#javaAncestors() ancestry} of the pojo types. It computes and
	 * answers the union of the key sets and the intersections of their
	 * parameterizations.
	 *
	 * @param object
	 *        A pojo type.
	 * @param aPojoType
	 *        Another pojo type.
	 * @return A new ancestry map OR the bottom pojo type.
	 */
	protected static A_BasicObject computeIntersection (
		final A_BasicObject object,
		final A_BasicObject aPojoType)
	{
		final A_Map ancestors = object.javaAncestors();
		final A_Map otherAncestors = aPojoType.javaAncestors();
		final A_Set javaClasses = ancestors.keysAsSet();
		final A_Set otherJavaClasses = otherAncestors.keysAsSet();
		final A_Set union = javaClasses.setUnionCanDestroy(
			otherJavaClasses, false);
		A_Map unionAncestors = emptyMap();
		for (final AvailObject javaClass : union)
		{
			final A_Tuple params = ancestors.hasKey(javaClass)
				? ancestors.mapAt(javaClass)
				: otherAncestors.mapAt(javaClass);
			final A_Tuple otherParams = otherAncestors.hasKey(javaClass)
				? otherAncestors.mapAt(javaClass)
				: ancestors.mapAt(javaClass);
			final int limit = params.tupleSize();
			assert limit == otherParams.tupleSize();
			final List<A_Type> intersectionParams =
				new ArrayList<>(limit);
			for (int i = 1; i <= limit; i++)
			{
				final A_Type x = params.tupleAt(i);
				final A_Type y = otherParams.tupleAt(i);
				final A_Type intersection = x.typeIntersection(y);
				if (intersection.isSubtypeOf(pojoBottom()))
				{
					return pojoBottom();
				}
				intersectionParams.add(intersection);
			}
			unionAncestors = unionAncestors.mapAtPuttingCanDestroy(
				javaClass,
				tupleFromList(intersectionParams),
				true);
		}
		return unionAncestors;
	}

	/**
	 * Compute the union of two {@linkplain PojoTypeDescriptor pojo
	 * types}. This is utility method that only examines the {@linkplain
	 * AvailObject#javaAncestors() ancestry} of the pojo types. It computes and
	 * answers the intersection of the key sets and the union of their
	 * parameterizations.
	 *
	 * @param object
	 *        A pojo type.
	 * @param aPojoType
	 *        Another pojo type.
	 * @return A new ancestry map.
	 */
	protected static A_Map computeUnion (
		final A_BasicObject object,
		final A_BasicObject aPojoType)
	{
		// Find the intersection of the key sets and the union of their
		// parameterizations.
		final A_Map ancestors = object.javaAncestors();
		final A_Map otherAncestors = aPojoType.javaAncestors();
		final A_Set javaClasses = ancestors.keysAsSet();
		final A_Set otherJavaClasses = otherAncestors.keysAsSet();
		final A_Set intersection = javaClasses.setIntersectionCanDestroy(
			otherJavaClasses, false);
		A_Map intersectionAncestors = emptyMap();
		for (final AvailObject javaClass : intersection)
		{
			final A_Tuple params = ancestors.mapAt(javaClass);
			final A_Tuple otherParams = otherAncestors.mapAt(javaClass);
			final int limit = params.tupleSize();
			assert limit == otherParams.tupleSize();
			final List<A_Type> unionParams = new ArrayList<>(limit);
			for (int i = 1; i <= limit; i++)
			{
				final A_Type x = params.tupleAt(i);
				final A_Type y = otherParams.tupleAt(i);
				final A_Type union = x.typeUnion(y);
				unionParams.add(union);
			}
			intersectionAncestors =
				intersectionAncestors.mapAtPuttingCanDestroy(
					javaClass.makeImmutable(),
					tupleFromList(unionParams),
					true);
		}
		return intersectionAncestors;
	}

	/**
	 * Answer the locally childless {@linkplain Class Java types} from among
	 * the types present in the specified ancestry.
	 *
	 * @param ancestry
	 *        A {@linkplain SetDescriptor set} of {@linkplain RawPojoDescriptor
	 *        raw pojos} that wrap related Java types.
	 * @return Those subset of the ancestry that is locally childless, i.e.,
	 *         those elements that do not have any subtypes also present in the
	 *         ancestry.
	 */
	protected static Set<AvailObject> childlessAmong (
		final A_Set ancestry)
	{
		final Set<AvailObject> childless = new HashSet<>();
		for (final AvailObject ancestor : ancestry)
		{
			childless.add(ancestor);
		}
		for (final AvailObject ancestor : ancestry)
		{
			final Class<?> possibleAncestor = ancestor.javaObjectNotNull();
			for (final A_BasicObject child : ancestry)
			{
				final Class<?> possibleChild = child.javaObjectNotNull();
				if (possibleAncestor != possibleChild
					&& possibleAncestor.isAssignableFrom(possibleChild))
				{
					childless.remove(ancestor);
				}
			}
		}
		return childless;
	}

	/**
	 * Answer the most specific {@linkplain Class Java type} present in the
	 * specified ancestry.
	 *
	 * @param ancestry
	 *        A {@linkplain SetDescriptor set} of {@linkplain RawPojoDescriptor
	 *        raw pojos} that wrap Java types. The set contains related types
	 *        that were computed during a type union of two {@linkplain
	 *        PojoTypeDescriptor pojo types}.
	 * @return The most specific Java type in the set. Answer {@linkplain
	 *         NilDescriptor nil} if there is not a single most specific type
	 *         (this can only happen for interfaces).
	 */
	protected static AvailObject mostSpecificOf (
		final A_Set ancestry)
	{
		AvailObject answer = rawObjectClass();
		Class<?> mostSpecific = Object.class;
		for (final AvailObject rawType : ancestry)
		{
			final Class<?> javaClass = rawType.javaObjectNotNull();
			if (mostSpecific.isAssignableFrom(javaClass))
			{
				mostSpecific = javaClass;
				answer = rawType;
			}
		}
		// If the (tentative) answer is an interface, then verify that it is
		// strictly more specific than all other types in the set.
		final int modifiers = mostSpecific.getModifiers();
		if (Modifier.isInterface(modifiers))
		{
			for (final A_BasicObject rawType : ancestry)
			{
				final Class<?> javaClass = rawType.javaObjectNotNull();
				if (!javaClass.isAssignableFrom(mostSpecific))
				{
					return nil;
				}
			}
		}
		return answer;
	}

	@Override
	abstract void printObjectOnAvoidingIndent (
		AvailObject object,
		StringBuilder builder,
		IdentityHashMap<A_BasicObject, Void> recursionMap,
		int indent);

	/**
	 * Construct a new {@link PojoTypeDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	protected PojoTypeDescriptor (
		final Mutability mutability,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(
			mutability,
			TypeTag.POJO_TYPE_TAG,
			objectSlotsEnumClass,
			integerSlotsEnumClass);
	}

	/**
	 * Marshal the supplied {@link A_Tuple} of {@link A_Type}s.
	 *
	 * @param types
	 *        A {@linkplain TupleDescriptor tuple} of types.
	 * @return The Java {@linkplain Class classes} that represent the supplied
	 *         types.
	 * @throws MarshalingException
	 *         If marshaling fails for any of the supplied types.
	 */
	public static Class<?>[] marshalTypes (final A_Tuple types)
	throws MarshalingException
	{
		// Marshal the argument types.
		final Class<?>[] marshaledTypes = new Class<?>[types.tupleSize()];
		for (int i = 0; i < marshaledTypes.length; i++)
		{
			marshaledTypes[i] =
				nullableCast(types.tupleAt(i + 1).marshalToJava(null));
		}
		return marshaledTypes;
	}

	/**
	 * Marshal the supplied {@link A_Type}, as though it will be used for
	 * {@link Executable} lookup, using a boxed Java class to represent a
	 * primitive Java type.
	 *
	 * @param type
	 *        A type.
	 * @return The Java class that represents the supplied type.
	 * @throws MarshalingException
	 *         If marshaling fails for any reason.
	 */
	public static Class<?> marshalDefiningType (final A_Type type)
	{
		final @Nullable Class<?> aClass = nullableCast(
			type.marshalToJava(null));
		assert aClass != null;
		if (aClass.isPrimitive())
		{
			if (aClass.equals(Boolean.TYPE))
			{
				return Boolean.class;
			}
			else if (aClass.equals(Byte.TYPE))
			{
				return Byte.class;
			}
			else if (aClass.equals(Short.TYPE))
			{
				return Short.class;
			}
			else if (aClass.equals(Integer.TYPE))
			{
				return Integer.class;
			}
			else if (aClass.equals(Long.TYPE))
			{
				return Long.class;
			}
			else if (aClass.equals(Float.TYPE))
			{
				return Float.class;
			}
			else if (aClass.equals(Double.TYPE))
			{
				return Double.class;
			}
			else if (aClass.equals(Character.TYPE))
			{
				return Character.class;
			}
		}
		return aClass;
	}

	/**
	 * Marshal the arbitrary {@linkplain Object Java object} to its counterpart
	 * {@linkplain AvailObject Avail object}.
	 *
	 * @param object
	 *        A Java object, or {@code null}.
	 * @param type
	 *        A {@linkplain TypeDescriptor type} to which the resultant Avail
	 *        object must conform.
	 * @return An Avail Object.
	 */
	public static AvailObject unmarshal (
		final @Nullable Object object,
		final A_Type type)
	{
		if (object == null)
		{
			return nullPojo();
		}
		final Class<?> javaClass = object.getClass();
		final A_BasicObject availObject;
		if (javaClass.equals(AvailObject.class))
		{
			availObject = (AvailObject) object;
		}
		else if (javaClass.equals(Boolean.class))
		{
			availObject = objectFromBoolean((Boolean) object);
		}
		else if (javaClass.equals(Byte.class))
		{
			availObject = fromInt((Byte) object);
		}
		else if (javaClass.equals(Short.class))
		{
			availObject = fromInt((Short) object);
		}
		else if (javaClass.equals(Integer.class))
		{
			availObject = fromInt((Integer) object);
		}
		else if (javaClass.equals(Long.class))
		{
			availObject = fromLong((Long) object);
		}
		else if (javaClass.equals(Float.class))
		{
			availObject = fromFloat((Float) object);
		}
		else if (javaClass.equals(Double.class))
		{
			availObject = fromDouble((Double) object);
		}
		else if (javaClass.equals(Character.class))
		{
			availObject = fromInt((Character) object);
		}
		else if (javaClass.equals(String.class))
		{
			availObject = stringFrom((String) object);
		}
		else if (javaClass.equals(BigInteger.class))
		{
			availObject = fromBigInteger((BigInteger) object);
		}
		else
		{
			availObject = newPojo(equalityPojo(object), type);
		}

		if (!availObject.isInstanceOf(type))
		{
			throw new MarshalingException();
		}
		return (AvailObject) availObject;
	}

	/**
	 * Resolve the specified {@linkplain Type type} using the given {@linkplain
	 * AvailObject#typeVariables() type variables}.
	 *
	 * @param type
	 *        A type.
	 * @param typeVars
	 *        A {@linkplain MapDescriptor map} from fully-qualified {@linkplain
	 *        TypeVariable type variable} {@linkplain StringDescriptor names} to
	 *        their {@linkplain TypeDescriptor types}.
	 * @return An Avail type.
	 */
	public static A_Type resolvePojoType (
		final Type type,
		final A_Map typeVars)
	{
		// If type is a Java class or interface, then answer a pojo type.
		if (type instanceof Class<?>)
		{
			final Class<?> aClass = (Class<?>) type;
			// If type represents java.lang.Object, then answer any.
			if (aClass.equals(Object.class))
			{
				return ANY.o();
			}
			// If type represents a Java primitive, then unmarshal it.
			if (aClass.isPrimitive())
			{
				// If type represents Java void, then answer top.
				if (aClass.equals(Void.TYPE))
				{
					return TOP.o();
				}
				else if (aClass.equals(Boolean.TYPE))
				{
					return booleanType();
				}
				else if (aClass.equals(Byte.TYPE))
				{
					return byteRange();
				}
				else if (aClass.equals(Short.TYPE))
				{
					return shortRange();
				}
				else if (aClass.equals(Integer.TYPE))
				{
					return intRange();
				}
				else if (aClass.equals(Long.TYPE))
				{
					return longRange();
				}
				else if (aClass.equals(Float.TYPE))
				{
					return FLOAT.o();
				}
				else if (aClass.equals(Double.TYPE))
				{
					return DOUBLE.o();
				}
				else if (aClass.equals(Character.TYPE))
				{
					return charRange();
				}
				else
				{
					assert false : "There are only nine primitive types!";
					throw new RuntimeException();
				}
			}
			if (aClass.equals(Void.class))
			{
				return TOP.o();
			}
			if (aClass.equals(Boolean.class))
			{
				return booleanType();
			}
			if (aClass.equals(Byte.class))
			{
				return byteRange();
			}
			if (aClass.equals(Short.class))
			{
				return shortRange();
			}
			if (aClass.equals(Integer.class))
			{
				return intRange();
			}
			if (aClass.equals(Long.class))
			{
				return longRange();
			}
			if (aClass.equals(Float.class))
			{
				return FLOAT.o();
			}
			if (aClass.equals(Double.class))
			{
				return DOUBLE.o();
			}
			if (aClass.equals(Character.class))
			{
				return charRange();
			}
			if (aClass.equals(String.class))
			{
				return stringType();
			}
			if (aClass.equals(BigInteger.class))
			{
				return integers();
			}
			return pojoTypeForClass((Class<?>) type);
		}
		// If type is a type variable, then resolve it using the map of type
		// variables.
		if (type instanceof TypeVariable<?>)
		{
			final TypeVariable<?> var = (TypeVariable<?>) type;
			final GenericDeclaration decl = var.getGenericDeclaration();
			final Class<?> javaClass;
			// class Foo<X> { ... }
			if (decl instanceof Class<?>)
			{
				javaClass = ((Class<?>) decl);
			}
			// class Foo { <X> Foo(X x) { ... } ... }
			else if (decl instanceof Constructor<?>)
			{
				javaClass = ((Constructor<?>) decl).getDeclaringClass();
			}
			// class Foo { <X> X compute (X x) { ... } }
			else if (decl instanceof Method)
			{
				javaClass = ((Method) decl).getDeclaringClass();
			}
			else
			{
				assert false :
					"There should only be three contexts that can define a "
					+ "type variable!";
				throw new RuntimeException();
			}
			final A_String name = stringFrom(javaClass.getName()
				+ "."
				+ var.getName());
			if (typeVars.hasKey(name))
			{
				// The type variable was bound, so answer the binding.
				return typeVars.mapAt(name);
			}
			// The type variable was unbound, so compute the upper bound.
			A_Type union = bottom();
			for (final Type bound : var.getBounds())
			{
				union = union.typeIntersection(resolvePojoType(
					bound, typeVars));
			}
			return union;
		}
		// If type is a parameterized type, then recursively resolve it using
		// the map of type variables.
		if (type instanceof ParameterizedType)
		{
			final ParameterizedType parameterized = (ParameterizedType) type;
			final Type[] unresolved = parameterized.getActualTypeArguments();
			final List<A_Type> resolved = new ArrayList<>(
				unresolved.length);
			for (final Type anUnresolved : unresolved)
			{
				resolved.add(resolvePojoType(anUnresolved, typeVars));
			}
			return pojoTypeForClassWithTypeArguments(
				(Class<?>) parameterized.getRawType(),
				tupleFromList(resolved));
		}
		assert false : "Unsupported generic declaration";
		throw new RuntimeException();
	}

	/**
	 * Answer the canonical pojo type for the specified pojo type. This marshals
	 * certain pojo types to Avail types (e.g., java.lang.String -> string).
	 *
	 * @param probablePojoType
	 *        An arbitrary Avail type, but one that might be a pojo type.
	 * @param allowMetas
	 *        {@code true} if metatypes are contextually possible outcomes,
	 *        {@code false} if only nontype values are contextually possible
	 *        outcomes.
	 * @return The canonical Avail type for the given pojo type.
	 */
	public static A_Type canonicalPojoType (
		final A_Type probablePojoType,
		final boolean allowMetas)
	{
		if (probablePojoType.isPojoType()
			&& !probablePojoType.equalsPojoBottomType())
		{
			final A_BasicObject pojoClass = probablePojoType.javaClass();
			if (!pojoClass.equalsNil())
			{
				final Class<?> javaClass = pojoClass.javaObjectNotNull();
				if (javaClass.getTypeParameters().length == 0)
				{
					final A_Type resolved =
						resolvePojoType(javaClass, emptyMap());
					if (!allowMetas && resolved.equals(ANY.o()))
					{
						return NONTYPE.o();
					}
					return resolved;
				}
			}
		}
		return probablePojoType;
	}

	/**
	 * In the context of a reference {@linkplain Class Java class or interface}
	 * implicitly specified by the {@linkplain TypeVariableMap type variable
	 * map} and {@linkplain TupleDescriptor tuple of type arguments}, compute
	 * the type arguments of the specified target Java class or interface.
	 *
	 * @param target
	 *        A Java class or interface (encountered during processing of the
	 *        reference type's ancestry).
	 * @param vars
	 *        The reference type's type variable map. Indices are specified
	 *        relative to ...
	 * @param typeArgs
	 *        The reference type's type arguments.
	 * @param canon
	 *        The current {@linkplain Canon canon}, used to identify recursive
	 *        type dependency.
	 * @return The type arguments of the target.
	 */
	private static
	A_Tuple computeTypeArgumentsOf (
		final ParameterizedType target,
		final TypeVariableMap vars,
		final A_Tuple typeArgs,
		final Canon canon)
	{
		final Type[] args = target.getActualTypeArguments();
		final List<A_Type> propagation = new ArrayList<>(2);
		for (final Type arg : args)
		{
			// class Target<...> extends Supertype<Arg> { ... }
			//
			// If the type argument is an unparameterized class or
			// interface, then add its pojo type to the supertype's tuple of
			// type arguments.
			if (arg instanceof Class<?>)
			{
				final Class<?> javaClass = (Class<?>) arg;
				final A_Type typeArg =
					canon.containsKey(javaClass)
						? selfTypeForClass(javaClass)
						: pojoTypeForClass(javaClass);
				propagation.add(typeArg);
			}
			// class Target<A, ...> extends Supertype<A, ...> { ... }
			//
			// If the type argument is a type variable, then copy the
			// corresponding type argument from the subtype's type
			// parameterization tuple to the supertype's tuple of type
			// arguments.
			else if (arg instanceof TypeVariable<?>)
			{
				final Integer index =
					stripNull(vars.get(((TypeVariable<?>) arg).getName()));
				propagation.add(typeArgs.tupleAt(index + 1));
			}
			// class Target<A, ...>
			// extends Supertype<B<A>, D<Arg>, ...> { ... }
			//
			// If the type argument is a parameterized class or interface,
			// then recursively resolve it to a pojo type and copy the
			// result to the supertype's tuple of type arguments.
			else if (arg instanceof ParameterizedType)
			{
				final ParameterizedType parameterized =
					(ParameterizedType) arg;
				final A_Tuple localArgs = computeTypeArgumentsOf(
					parameterized,
					vars,
					typeArgs,
					canon);
				propagation.add(pojoTypeForClassWithTypeArguments(
					(Class<?>) parameterized.getRawType(), localArgs));
			}
			// There are no other conditions, but in the unlikely event that
			// the structure of the Java language changes significantly in a
			// future release ...
			else
			{
				assert false : "Unsupported generic declaration";
				throw new RuntimeException();
			}
		}
		return tupleFromList(propagation);
	}

	/**
	 * Given the type parameterization of the {@linkplain Class target Java
	 * class or interface}, use type propagation to determine the type
	 * parameterization of the specified direct {@linkplain Type supertype}.
	 *
	 * @param target
	 *        A Java class or interface.
	 * @param supertype
	 *        A parameterized direct supertype of the target.
	 * @param typeArgs
	 *        The type parameters of the target. These may be any {@linkplain
	 *        TypeDescriptor Avail types}, not just pojo types.
	 * @param canon
	 *        The current {@linkplain Canon canon}, used to identify recursive
	 *        type dependency.
	 * @return The type parameters of the specified supertype.
	 */
	private static A_Tuple computeSupertypeParameters (
		final Class<?> target,
		final Type supertype,
		final A_Tuple typeArgs,
		final Canon canon)
	{
		// class Target<...> extends GenericSupertype { ... }
		//
		// If the supertype is an unparameterized class or interface, then
		// answer an empty type parameterization tuple.
		if (supertype instanceof Class<?>)
		{
			return emptyTuple();
		}
		// class Target<A, B, ...> extends GenericSupertype<A, B, ...> { ... }
		//
		// If the supertype is a parameterized class or interface, then compute
		// the type parameterization tuple.
		else if (supertype instanceof ParameterizedType)
		{
			return computeTypeArgumentsOf(
				(ParameterizedType) supertype,
				new TypeVariableMap(target),
				typeArgs,
				canon);
		}
		// There are no other conditions, but in the unlikely event that the
		// structure of the Java language changes significantly in a future
		// release ...
		else
		{
			assert false : "Unsupported generic declaration";
			throw new RuntimeException();
		}
	}

	/**
	 * Recursively compute the complete ancestry (of Java types) of the
	 * specified {@linkplain Class Java class or interface}.
	 *
	 * @param target
	 *        A Java class or interface.
	 * @param typeArgs
	 *        The type arguments. These may be any {@linkplain TypeDescriptor
	 *        Avail types}, not just pojo types.
	 * @param ancestry
	 *        The working partial {@linkplain MapDescriptor ancestry}.
	 * @param canon
	 *        The current {@linkplain Canon canon}, used to deduplicate the
	 *        collection of ancestors.
	 */
	private static void computeAncestry (
		final Class<?> target,
		final A_Tuple typeArgs,
		final Mutable<A_Map> ancestry,
		final Canon canon)
	{
		final AvailObject javaClass = canon.canonize(target);
		ancestry.value = ancestry.value.mapAtPuttingCanDestroy(
			javaClass, typeArgs, true);
		// Recursively accumulate the class ancestry.
		final @Nullable Class<?> superclass = target.getSuperclass();
		if (superclass != null)
		{
			if (!canon.containsKey(superclass))
			{
				final A_Tuple supertypeParams = computeSupertypeParameters(
					target,
					target.getGenericSuperclass(),
					typeArgs,
					canon);
				computeAncestry(
					superclass,
					supertypeParams,
					ancestry,
					canon);
			}
		}
		// Recursively accumulate the interface ancestry.
		final Class<?>[] superinterfaces = target.getInterfaces();
		final Type[] genericSuperinterfaces = target.getGenericInterfaces();
		for (int i = 0; i < superinterfaces.length; i++)
		{
			if (!canon.containsKey(superinterfaces[i]))
			{
				final A_Tuple supertypeParams = computeSupertypeParameters(
					target,
					genericSuperinterfaces[i],
					typeArgs,
					canon);
				computeAncestry(
					superinterfaces[i],
					supertypeParams,
					ancestry,
					canon);
			}
		}
	}

	/**
	 * Create a {@linkplain PojoTypeDescriptor pojo type} from the
	 * specified {@linkplain Class Java class} and type arguments.
	 *
	 * @param target
	 *        A Java class or interface.
	 * @param typeArgs
	 *        The type arguments. These may be any {@linkplain TypeDescriptor
	 *        Avail types}, not just pojo types.
	 * @return The requested pojo type.
	 */
	public static AvailObject pojoTypeForClassWithTypeArguments (
		final Class<?> target,
		final A_Tuple typeArgs)
	{
		return cache.get(new LRUCacheKey(target, typeArgs));
	}

	/**
	 * Create a {@linkplain PojoTypeDescriptor pojo type} for the
	 * specified {@linkplain Class Java class}.
	 *
	 * @param target
	 *        A Java class or interface.
	 * @return The requested pojo type.
	 */
	public static AvailObject pojoTypeForClass (final Class<?> target)
	{
		final int paramCount = target.getTypeParameters().length;
		final List<AvailObject> params;
		if (paramCount > 0)
		{
			params = Arrays.asList(new AvailObject[paramCount]);
			Collections.fill(params, ANY.o());
		}
		else
		{
			params = Collections.emptyList();
		}
		return pojoTypeForClassWithTypeArguments(
			target, tupleFromList(params));
	}

	/**
	 * Create a {@linkplain PojoTypeDescriptor pojo type} that
	 * represents an array of the specified {@linkplain TypeDescriptor element
	 * type}.
	 *
	 * @param elementType
	 *        The element type. This may be any Avail type, not just a pojo
	 *        type.
	 * @param sizeRange
	 *        An {@linkplain IntegerRangeTypeDescriptor integer range} that
	 *        specifies all allowed array sizes for instances of this type. This
	 *        must be a subtype of {@linkplain
	 *        IntegerRangeTypeDescriptor#wholeNumbers() whole number}.
	 * @return The requested pojo type.
	 */
	public static AvailObject pojoArrayType (
		final A_Type elementType,
		final A_Type sizeRange)
	{
		assert sizeRange.isSubtypeOf(wholeNumbers());
		return arrayPojoType(elementType, sizeRange);
	}

	/**
	 * Create a {@link FusedPojoTypeDescriptor fused pojo type} based on the
	 * given complete parameterization map.  Each ancestor class and interface
	 * occurs as a key, with that class or interface's parameter tuple as the
	 * value.
	 *
	 * @param ancestorMap
	 *            A map from {@link RawPojoDescriptor#equalityPojo(Object)
	 *            equality-wrapped} {@link RawPojoDescriptor raw pojos} to their
	 *            tuples of type parameters.
	 * @return
	 *            A fused pojo type.
	 */
	public static AvailObject fusedTypeFromAncestorMap (
		final A_Map ancestorMap)
	{
		assert ancestorMap.isMap();
		return createFusedPojoType(ancestorMap);
	}

	/**
	 * Recursively compute the complete ancestry (of Java types) of the
	 * specified {@linkplain Class Java class or interface}. Ignore type
	 * parameters.
	 *
	 * @param target
	 *        A Java class or interface.
	 * @param ancestors
	 *        The {@linkplain Set set} of ancestors.
	 * @param canon
	 *        The current {@linkplain Canon canon}, used to deduplicate the
	 *        collection of ancestors.
	 */
	private static void computeUnparameterizedAncestry (
		final Class<?> target,
		final Set<AvailObject> ancestors,
		final Canon canon)
	{
		ancestors.add(canon.canonize(target));
		final @Nullable Class<?> superclass = target.getSuperclass();
		if (superclass != null)
		{
			computeUnparameterizedAncestry(superclass, ancestors, canon);
		}
		for (final Class<?> superinterface : target.getInterfaces())
		{
			computeUnparameterizedAncestry(superinterface, ancestors, canon);
		}
	}

	/**
	 * Create a {@linkplain PojoTypeDescriptor pojo self type} for the
	 * specified {@linkplain Class Java class}.
	 *
	 * @param target
	 *        A Java class or interface. This element should define no type
	 *        parameters.
	 * @return The requested pojo type.
	 */
	public static AvailObject selfTypeForClass (
		final Class<?> target)
	{
		final Canon canon = new Canon();
		final Set<AvailObject> ancestors = new HashSet<>(5);
		ancestors.add(canon.get(Object.class));
		computeUnparameterizedAncestry(target, ancestors, canon);
		return newSelfPojoType(canon.get(target), setFromCollection(ancestors));
	}
}
