/**
 * PojoTypeDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.exceptions.*;
import com.avail.utility.*;

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
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
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
		/** The serial version identifier. */
		private static final long serialVersionUID = -5682929623799845184L;

		/**
		 * Construct a new {@link Canon} that has initial capacity for five
		 * bindings and includes a binding for {@link Object java.lang.Object}.
		 */
		public Canon ()
		{
			super(5);
			put(Object.class, RawPojoDescriptor.rawObjectClass());
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
		public @NotNull AvailObject canonize (final @NotNull Class<?> javaClass)
		{
			AvailObject rawPojo = get(javaClass);
			if (rawPojo == null)
			{
				rawPojo = RawPojoDescriptor.equalityWrap(javaClass);
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
		/** The serial version identifier. */
		private static final long serialVersionUID = -6642629479723500110L;

		/**
		 * Construct a new {@link TypeVariableMap} for the specified {@linkplain
		 * Class Java class or interface}.
		 *
		 * @param javaClass
		 *        A Java class or interface.
		 */
		public TypeVariableMap (final Class<?> javaClass)
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
		public final @NotNull Class<?> javaClass;

		/** The type arguments. */
		public final @NotNull AvailObject typeArgs;

		@Override
		public boolean equals (final @NotNull Object obj)
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
		 * Construct a new {@link LRUCacheKey}.
		 *
		 * @param javaClass
		 *        The {@linkplain Class Java class or interface}.
		 * @param typeArgs
		 *        The type arguments.
		 */
		public LRUCacheKey (
			final @NotNull Class<?> javaClass,
			final @NotNull AvailObject typeArgs)
		{
			this.javaClass = javaClass;
			this.typeArgs = typeArgs;
		}
	}

	/** The most general {@linkplain PojoTypeDescriptor pojo type}. */
	private static AvailObject mostGeneralType;

	/**
	 * Answer the most general {@linkplain PojoTypeDescriptor pojo
	 * type}.
	 *
	 * @return The most general pojo type.
	 */
	public static @NotNull AvailObject mostGeneralType ()
	{
		return mostGeneralType;
	}

	/**
	 * The most general {@linkplain PojoTypeDescriptor pojo array
	 * type}.
	 */
	private static AvailObject mostGeneralArrayType;

	/**
	 * Answer the most general {@linkplain PojoTypeDescriptor pojo array
	 * type}.
	 *
	 * @return The most general pojo array type.
	 */
	public static @NotNull AvailObject mostGeneralArrayType ()
	{
		return mostGeneralArrayType;
	}

	/**
	 * The most specific {@linkplain PojoTypeDescriptor pojo type},
	 * other than {@linkplain BottomTypeDescriptor#bottom() bottom}.
	 */
	private static AvailObject pojoBottom;

	/**
	 * Answer the most specific {@linkplain PojoTypeDescriptor pojo
	 * type}, other than {@linkplain BottomTypeDescriptor#bottom() bottom}.
	 *
	 * @return The most specific pojo type.
	 */
	public static @NotNull AvailObject pojoBottom ()
	{
		return pojoBottom;
	}

	/**
	 * A special {@linkplain AtomDescriptor atom} whose {@linkplain
	 * InstanceTypeDescriptor instance type} represents the self type of a
	 * {@linkplain Class Java class or interface}.
	 */
	private static AvailObject selfAtom;

	/**
	 * Answer a special {@linkplain AtomDescriptor atom} whose {@linkplain
	 * InstanceTypeDescriptor instance type} represents the self type of a
	 * {@linkplain Class Java class or interface}.
	 *
	 * @return The pojo self type atom.
	 */
	public static @NotNull AvailObject selfAtom ()
	{
		return selfAtom;
	}

	/**
	 * A special {@linkplain InstanceTypeDescriptor instance type} that
	 * represents the self type of a {@linkplain Class Java class or interface}.
	 */
	private static AvailObject selfType;

	/**
	 * Answer a special {@linkplain InstanceTypeDescriptor instance type} that
	 * represents the self type of a {@linkplain Class Java class or interface}.
	 *
	 * @return The pojo self type atom.
	 */
	public static @NotNull AvailObject selfType ()
	{
		return selfType;
	}

	/**
	 * The {@linkplain IntegerRangeTypeDescriptor integer range type} that
	 * corresponds to Java {@code byte}.
	 */
	private static AvailObject byteRange;

	/**
	 * Answer the {@linkplain IntegerRangeTypeDescriptor integer range type}
	 * that corresponds to Java {@code byte}.
	 *
	 * @return {@code [-128..127]}.
	 */
	public static AvailObject byteRange ()
	{
		return byteRange;
	}

	/**
	 * The {@linkplain IntegerRangeTypeDescriptor integer range type} that
	 * corresponds to Java {@code short}.
	 */

	private static AvailObject shortRange;
	/**
	 * Answer the {@linkplain IntegerRangeTypeDescriptor integer range type}
	 * that corresponds to Java {@code short}.
	 *
	 * @return {@code [-32768..32767]}.
	 */
	public static AvailObject shortRange ()
	{
		return shortRange;
	}

	/**
	 * The {@linkplain IntegerRangeTypeDescriptor integer range type} that
	 * corresponds to Java {@code int}.
	 */

	private static AvailObject intRange;

	/**
	 * Answer the {@linkplain IntegerRangeTypeDescriptor integer range type}
	 * that corresponds to Java {@code int}.
	 *
	 * @return {@code [-2147483648..2147483647]}.
	 */
	public static AvailObject intRange ()
	{
		return intRange;
	}

	/**
	 * The {@linkplain IntegerRangeTypeDescriptor integer range type} that
	 * corresponds to Java {@code long}.
	 */
	private static AvailObject longRange;

	/**
	 * Answer the {@linkplain IntegerRangeTypeDescriptor integer range type}
	 * that corresponds to Java {@code long}.
	 *
	 * @return {@code [-9223372036854775808..9223372036854775807]}.
	 */
	public static AvailObject longRange ()
	{
		return longRange;
	}

	/**
	 * The {@linkplain IntegerRangeTypeDescriptor integer range type} that
	 * corresponds to Java {@code char}.
	 */
	private static AvailObject charRange;

	/**
	 * Answer the {@linkplain IntegerRangeTypeDescriptor integer range type}
	 * that corresponds to Java {@code char}.
	 *
	 * @return {@code [0..65535]}.
	 */
	public static AvailObject charRange ()
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
	@InnerAccess static @NotNull AvailObject computeValue (
		final @NotNull LRUCacheKey key)
	{
		// Java allows the operations defined in java.lang.Object to be
		// performed on interface types, so interfaces are implicitly subtypes
		// of java.lang.Object. Make this relationship explicit: seed the
		// ancestry with java.lang.Object.
		final Canon canon = new Canon();
		final Mutable<AvailObject> ancestors = new Mutable<AvailObject>(
			MapDescriptor.empty());
		ancestors.value = ancestors.value.mapAtPuttingCanDestroy(
			canon.get(Object.class),
			TupleDescriptor.empty(),
			true);
		computeAncestry(
			key.javaClass, key.typeArgs, ancestors, canon);
		return UnfusedPojoTypeDescriptor.create(
			canon.get(key.javaClass), ancestors.value);
	}

	/**
	 * {@linkplain PojoTypeDescriptor Pojo types} are somewhat expensive
	 * to build, so cache them for efficiency.
	 */
	private static final @NotNull LRUCache<LRUCacheKey, AvailObject> cache =
		new LRUCache<LRUCacheKey, AvailObject>(
			1000,
			10,
			new Transformer1<LRUCacheKey, AvailObject>()
			{
				@Override
				public @NotNull AvailObject value (
					final @NotNull LRUCacheKey key)
				{
					return computeValue(key);
				}
			});

	/**
	 * Create any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void createWellKnownObjects ()
	{
		mostGeneralType = forClass(Object.class);
		mostGeneralArrayType = forArrayTypeWithSizeRange(
			ANY.o(), IntegerRangeTypeDescriptor.wholeNumbers());
		pojoBottom = BottomPojoTypeDescriptor.mutable().create();
		selfAtom = AtomDescriptor.create(
			StringDescriptor.from("pojo self"),
			NullDescriptor.nullObject());
		selfType = InstanceTypeDescriptor.on(selfAtom);
		byteRange = IntegerRangeTypeDescriptor.create(
			IntegerDescriptor.fromInt(Byte.MIN_VALUE),
			true,
			IntegerDescriptor.fromInt(Byte.MAX_VALUE),
			true);
		shortRange = IntegerRangeTypeDescriptor.create(
			IntegerDescriptor.fromInt(Short.MIN_VALUE),
			true,
			IntegerDescriptor.fromInt(Short.MAX_VALUE),
			true);
		intRange = IntegerRangeTypeDescriptor.create(
			IntegerDescriptor.fromInt(Integer.MIN_VALUE),
			true,
			IntegerDescriptor.fromInt(Integer.MAX_VALUE),
			true);
		longRange = IntegerRangeTypeDescriptor.create(
			IntegerDescriptor.fromLong(Long.MIN_VALUE),
			true,
			IntegerDescriptor.fromLong(Long.MAX_VALUE),
			true);
		charRange = IntegerRangeTypeDescriptor.create(
			IntegerDescriptor.fromInt(Character.MIN_VALUE),
			true,
			IntegerDescriptor.fromInt(Character.MAX_VALUE),
			true);
	}

	/**
	 * Destroy or reset any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void clearWellKnownObjects ()
	{
		try
		{
			cache.clear();
		}
		catch (final InterruptedException e)
		{
			throw new RuntimeException(e);
		}
		mostGeneralType = null;
		mostGeneralArrayType = null;
		pojoBottom = null;
		selfAtom = null;
		selfType = null;
		byteRange = null;
		shortRange = null;
		intRange = null;
		longRange = null;
		charRange = null;
	}

	@Override @AvailMethod
	final boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
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
		@NotNull AvailObject object,
		@NotNull AvailObject aPojoType);

	@Override @AvailMethod
	abstract boolean o_IsAbstract (@NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsPojoArrayType (@NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsPojoFusedType (@NotNull AvailObject object);

	@Override @AvailMethod
	boolean o_IsPojoSelfType (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsPojoType (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfPojoType(object);
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfPojoBottomType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		// Every pojo type is a supertype of pojo bottom.
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
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
		final AvailObject ancestors = object.javaAncestors();
		final AvailObject otherAncestors = aPojoType.javaAncestors();
		final AvailObject javaClasses = ancestors.keysAsSet();
		final AvailObject otherJavaClasses = otherAncestors.keysAsSet();
		final AvailObject intersection =
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
			final AvailObject params = ancestors.mapAt(javaClass);
			final AvailObject otherParams = otherAncestors.mapAt(javaClass);
			final int limit = params.tupleSize();
			for (int i = 1; i <= limit; i++)
			{
				final AvailObject x = params.tupleAt(i);
				final AvailObject y = otherParams.tupleAt(i);
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
	abstract int o_Hash (@NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_JavaAncestors (@NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_JavaClass (@NotNull AvailObject object);

	@Override @AvailMethod
	final @NotNull AvailObject o_Kind (final @NotNull AvailObject object)
	{
		return TYPE.o();
	}

	@Override @AvailMethod
	abstract @NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> ignoredClassHint);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_PojoSelfType (
		@NotNull AvailObject object);

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeIntersection (
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
	abstract @NotNull AvailObject o_TypeIntersectionOfPojoType (
		@NotNull AvailObject object,
		@NotNull AvailObject aPojoType);

	@Override
	abstract @NotNull AvailObject o_TypeIntersectionOfPojoFusedType (
		@NotNull AvailObject object,
		@NotNull AvailObject aFusedPojoType);

	@Override
	abstract @NotNull AvailObject o_TypeIntersectionOfPojoUnfusedType (
		@NotNull AvailObject object,
		@NotNull AvailObject anUnfusedPojoType);

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
	 * @return A new ancestry map.
	 */
	protected static @NotNull AvailObject computeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		final AvailObject ancestors = object.javaAncestors();
		final AvailObject otherAncestors = aPojoType.javaAncestors();
		final AvailObject javaClasses = ancestors.keysAsSet();
		final AvailObject otherJavaClasses = otherAncestors.keysAsSet();
		final AvailObject union = javaClasses.setUnionCanDestroy(
			otherJavaClasses, false);
		AvailObject unionAncestors = MapDescriptor.empty();
		for (final AvailObject javaClass : union)
		{
			final AvailObject params = ancestors.hasKey(javaClass)
				? ancestors.mapAt(javaClass)
				: otherAncestors.mapAt(javaClass);
			final AvailObject otherParams = otherAncestors.hasKey(javaClass)
				? otherAncestors.mapAt(javaClass)
				: ancestors.mapAt(javaClass);
			final int limit = params.tupleSize();
			assert limit == otherParams.tupleSize();
			final List<AvailObject> intersectionParams =
				new ArrayList<AvailObject>(limit);
			for (int i = 1; i <= limit; i++)
			{
				final AvailObject x = params.tupleAt(i);
				final AvailObject y = otherParams.tupleAt(i);
				final AvailObject intersection = x.typeIntersection(y);
				if (intersection.isSubtypeOf(
					PojoTypeDescriptor.pojoBottom()))
				{
					return PojoTypeDescriptor.pojoBottom();
				}
				intersectionParams.add(intersection);
			}
			unionAncestors = unionAncestors.mapAtPuttingCanDestroy(
				javaClass,
				TupleDescriptor.fromCollection(intersectionParams),
				true);
		}
		return unionAncestors;
	}

	@Override
	final @NotNull AvailObject o_TypeUnion (
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
	abstract @NotNull AvailObject o_TypeUnionOfPojoType (
		@NotNull AvailObject object,
		@NotNull AvailObject aPojoType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeUnionOfPojoFusedType (
		@NotNull AvailObject object,
		@NotNull AvailObject aFusedPojoType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeUnionOfPojoUnfusedType (
		@NotNull AvailObject object,
		@NotNull AvailObject anUnfusedPojoType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeVariables (
		@NotNull AvailObject object);

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
	protected static @NotNull AvailObject computeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		// Find the intersection of the key sets and the union of their
		// parameterizations.
		final AvailObject ancestors = object.javaAncestors();
		final AvailObject otherAncestors = aPojoType.javaAncestors();
		final AvailObject javaClasses = ancestors.keysAsSet();
		final AvailObject otherJavaClasses = otherAncestors.keysAsSet();
		final AvailObject intersection = javaClasses.setIntersectionCanDestroy(
			otherJavaClasses, false);
		AvailObject intersectionAncestors = MapDescriptor.empty();
		for (final AvailObject javaClass : intersection)
		{
			final AvailObject params = ancestors.mapAt(javaClass);
			final AvailObject otherParams = otherAncestors.mapAt(javaClass);
			final int limit = params.tupleSize();
			assert limit == otherParams.tupleSize();
			final List<AvailObject> unionParams = new ArrayList<AvailObject>(
				limit);
			for (int i = 1; i <= limit; i++)
			{
				final AvailObject x = params.tupleAt(i);
				final AvailObject y = otherParams.tupleAt(i);
				final AvailObject union = x.typeUnion(y);
				unionParams.add(union);
			}
			intersectionAncestors =
				intersectionAncestors.mapAtPuttingCanDestroy(
					javaClass.makeImmutable(),
					TupleDescriptor.fromCollection(unionParams),
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
	protected static @NotNull Set<AvailObject> childlessAmong (
		final @NotNull AvailObject ancestry)
	{
		final Set<AvailObject> childless = new HashSet<AvailObject>();
		for (final AvailObject ancestor : ancestry)
		{
			childless.add(ancestor);
		}
		for (final AvailObject ancestor : ancestry)
		{
			final Class<?> possibleAncestor = (Class<?>) ancestor.javaObject();
			for (final AvailObject child : ancestry)
			{
				final Class<?> possibleChild = (Class<?>) child.javaObject();
				if (possibleAncestor != possibleChild
					&& possibleAncestor.isAssignableFrom(possibleChild))
				{
					childless.remove(possibleAncestor);
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
	 *         NullDescriptor nil} if there is not a single most specific type
	 *         (this can only happen for interfaces).
	 */
	protected static @NotNull AvailObject mostSpecificOf (
		final @NotNull AvailObject ancestry)
	{
		AvailObject answer = RawPojoDescriptor.rawObjectClass();
		Class<?> mostSpecific = Object.class;
		for (final AvailObject rawType : ancestry)
		{
			final Class<?> javaClass = (Class<?>) rawType.javaObject();
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
			for (final AvailObject rawType : ancestry)
			{
				final Class<?> javaClass = (Class<?>) rawType.javaObject();
				if (!javaClass.isAssignableFrom(mostSpecific))
				{
					return NullDescriptor.nullObject();
				}
			}
		}
		return answer;
	}

	@Override
	abstract void printObjectOnAvoidingIndent (
		@NotNull AvailObject object,
		@NotNull StringBuilder builder,
		@NotNull List<AvailObject> recursionList,
		int indent);

	/**
	 * Construct a new {@link PojoTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain PojoTypeDescriptor descriptor}
	 *        represent a mutable object?
	 */
	protected PojoTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
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
	public static @NotNull AvailObject unmarshal (
		final Object object,
		final @NotNull AvailObject type)
	{
		if (object == null)
		{
			return PojoDescriptor.nullObject();
		}
		final Class<?> javaClass = object.getClass();
		final AvailObject availObject;
		if (javaClass.equals(AvailObject.class))
		{
			availObject = (AvailObject) object;
		}
		else if (javaClass.equals(Boolean.class))
		{
			availObject = (Boolean) object
				? AtomDescriptor.trueObject()
				: AtomDescriptor.falseObject();
		}
		else if (javaClass.equals(Byte.class))
		{
			availObject = IntegerDescriptor.fromInt((Byte) object);
		}
		else if (javaClass.equals(Short.class))
		{
			availObject = IntegerDescriptor.fromInt((Short) object);
		}
		else if (javaClass.equals(Integer.class))
		{
			availObject = IntegerDescriptor.fromInt((Integer) object);
		}
		else if (javaClass.equals(Long.class))
		{
			availObject = IntegerDescriptor.fromLong((Long) object);
		}
		else if (javaClass.equals(Float.class))
		{
			availObject = FloatDescriptor.fromFloat((Float) object);
		}
		else if (javaClass.equals(Double.class))
		{
			availObject = DoubleDescriptor.fromDouble((Double) object);
		}
		else if (javaClass.equals(Character.class))
		{
			availObject = CharacterDescriptor.fromCodePoint(
				((Character) object).charValue());
		}
		else if (javaClass.equals(String.class))
		{
			availObject = StringDescriptor.from((String) object);
		}
		else if (javaClass.equals(BigInteger.class))
		{
			availObject = IntegerDescriptor.fromBigInteger((BigInteger) object);
		}
		else
		{
			availObject = PojoDescriptor.newPojo(
				RawPojoDescriptor.identityWrap(object),
				type);
		}
		if (!availObject.isInstanceOf(type))
		{
			throw new MarshalingException();
		}
		return availObject;
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
	public static @NotNull AvailObject resolve (
		final @NotNull Type type,
		final @NotNull AvailObject typeVars)
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
			else if (aClass.isPrimitive())
			{
				// If type represents Java void, then answer top.
				if (aClass.equals(Void.TYPE))
				{
					return TOP.o();
				}
				else if (aClass.equals(Boolean.TYPE))
				{
					return EnumerationTypeDescriptor.booleanObject();
				}
				else if (aClass.equals(Byte.TYPE))
				{
					return byteRange;
				}
				else if (aClass.equals(Short.TYPE))
				{
					return shortRange;
				}
				else if (aClass.equals(Integer.TYPE))
				{
					return intRange;
				}
				else if (aClass.equals(Long.TYPE))
				{
					return longRange;
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
					return CHARACTER.o();
				}
				else
				{
					assert false : "There are only nine primitive types!";
					return null;
				}
			}
			else if (aClass.equals(String.class))
			{
				return TupleTypeDescriptor.stringTupleType();
			}
			else if (aClass.equals(BigInteger.class))
			{
				return IntegerRangeTypeDescriptor.integers();
			}
			return forClass((Class<?>) type);
		}
		// If type is a type variable, then resolve it using the map of type
		// variables.
		else if (type instanceof TypeVariable<?>)
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
				return null;
			}
			final AvailObject name = StringDescriptor.from(
				javaClass.getName() + "." + var.getName());
			return typeVars.mapAt(name);
		}
		// If type is a parameterized type, then recursively resolve it using
		// the map of type variables.
		else if (type instanceof ParameterizedType)
		{
			final ParameterizedType parameterized = (ParameterizedType) type;
			final Type[] unresolved = parameterized.getActualTypeArguments();
			final List<AvailObject> resolved = new ArrayList<AvailObject>(
				unresolved.length);
			for (int i = 0; i < unresolved.length; i++)
			{
				resolved.add(resolve(unresolved[i], typeVars));
			}
			return forClassWithTypeArguments(
				(Class<?>) parameterized.getRawType(),
				TupleDescriptor.fromCollection(resolved));
		}
		assert false : "Unsupported generic declaration";
		return null;
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
	@NotNull AvailObject computeTypeArgumentsOf (
		final @NotNull ParameterizedType target,
		final @NotNull TypeVariableMap vars,
		final @NotNull AvailObject typeArgs,
		final @NotNull Canon canon)
	{
		final Type[] args = target.getActualTypeArguments();
		final List<AvailObject> propagation = new ArrayList<AvailObject>(2);
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
				final AvailObject typeArg =
					canon.containsKey(javaClass)
					? selfTypeForClass(javaClass)
					: forClass(javaClass);
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
					vars.get(((TypeVariable<?>) arg).getName());
				assert index != null;
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
				final AvailObject localArgs = computeTypeArgumentsOf(
					parameterized,
					vars,
					typeArgs,
					canon);
				propagation.add(forClassWithTypeArguments(
					(Class<?>) parameterized.getRawType(), localArgs));
			}
			// There are no other conditions, but in the unlikely event that
			// the structure of the Java language changes significantly in a
			// future release ...
			else
			{
				assert false : "Unsupported generic declaration";
				return null;
			}
		}
		return TupleDescriptor.fromCollection(propagation);
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
	private static @NotNull AvailObject computeSupertypeParameters (
		final @NotNull Class<?> target,
		final @NotNull Type supertype,
		final @NotNull AvailObject typeArgs,
		final @NotNull Canon canon)
	{
		// class Target<...> extends GenericSupertype { ... }
		//
		// If the supertype is an unparameterized class or interface, then
		// answer an empty type parameterization tuple.
		if (supertype instanceof Class<?>)
		{
			return TupleDescriptor.empty();
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
			return null;
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
	@InnerAccess static void computeAncestry (
		final @NotNull Class<?> target,
		final @NotNull AvailObject typeArgs,
		final @NotNull Mutable<AvailObject> ancestry,
		final @NotNull Canon canon)
	{
		final AvailObject javaClass = canon.canonize(target);
		ancestry.value = ancestry.value.mapAtPuttingCanDestroy(
			javaClass, typeArgs, true);
		// Recursively accumulate the class ancestry.
		final Class<?> superclass = target.getSuperclass();
		if (superclass != null)
		{
			if (!canon.containsKey(superclass))
			{
				final AvailObject supertypeParams = computeSupertypeParameters(
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
				final AvailObject supertypeParams = computeSupertypeParameters(
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
	public static @NotNull AvailObject forClassWithTypeArguments (
		final @NotNull Class<?> target,
		final @NotNull AvailObject typeArgs)
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
	public static @NotNull AvailObject forClass (final @NotNull Class<?> target)
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
		return forClassWithTypeArguments(
			target, TupleDescriptor.fromCollection(params));
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
	public static @NotNull AvailObject forArrayTypeWithSizeRange (
		final @NotNull AvailObject elementType,
		final @NotNull AvailObject sizeRange)
	{
		assert sizeRange.isSubtypeOf(IntegerRangeTypeDescriptor.wholeNumbers());
		return ArrayPojoTypeDescriptor.create(elementType, sizeRange);
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
		final @NotNull Class<?> target,
		final @NotNull Set<AvailObject> ancestors,
		final @NotNull Canon canon)
	{
		if (target != null)
		{
			ancestors.add(canon.canonize(target));
			final Class<?> superclass = target.getSuperclass();
			if (superclass != null)
			{
				computeUnparameterizedAncestry(superclass, ancestors, canon);
			}
			for (final Class<?> superinterface : target.getInterfaces())
			{
				computeUnparameterizedAncestry(superinterface, ancestors, canon);
			}
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
	public static @NotNull AvailObject selfTypeForClass (
		final @NotNull Class<?> target)
	{
		final Canon canon = new Canon();
		final Set<AvailObject> ancestors = new HashSet<AvailObject>(5);
		ancestors.add(canon.get(Object.class));
		computeUnparameterizedAncestry(target, ancestors, canon);
		return SelfPojoTypeDescriptor.create(
			canon.get(target), SetDescriptor.fromCollection(ancestors));
	}
}
