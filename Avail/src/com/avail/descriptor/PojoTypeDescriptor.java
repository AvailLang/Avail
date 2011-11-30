/**
 * com.avail.descriptor/PojoTypeDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.PojoTypeDescriptor.ObjectSlots.*;
import java.lang.reflect.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;

/**
 * A {@code PojoTypeDescriptor} describes the type of a plaid-old Java object
 * (pojo) that is accessible to an Avail programmer as an {@linkplain
 * AvailObject Avail object}.
 *
 * <p>Even though Java uses type erasure for its generic types, Java class files
 * contain enough reflectively available information about {@linkplain
 * TypeVariable type variables} for Avail to expose Java types as if they were
 * fully polymorphic (like Avail's own types). Avail does not need to create new
 * Java types by extending the Java class hierarchy, so there is no need to
 * model Java generic types directly. Polymorphic types are therefore sufficient
 * for construction and employment, which runs the gamut of purposes from an
 * Avail programmer's perspective.</p>
 *
 * <p>Java interfaces are presented to Avail as though they were Java classes.
 * Avail sees interface inheritance as though it were class inheritance, with
 * root interfaces implicitly inheriting from {@link Object}. So an Avail
 * programmer sees Java as though it supported multiple inheritance of classes.
 * </p>
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class PojoTypeDescriptor
extends TypeDescriptor
{
	/** The most general {@linkplain PojoTypeDescriptor pojo type}. */
	private static AvailObject mostGeneralType;

	/**
	 * Answer the most general {@linkplain PojoTypeDescriptor pojo type}.
	 *
	 * @return The most general pojo type.
	 */
	public static @NotNull AvailObject mostGeneralType ()
	{
		return mostGeneralType;
	}

	/**
	 * The most specific {@linkplain PojoTypeDescriptor pojo type}, other than
	 * {@linkplain BottomTypeDescriptor#bottom() bottom}.
	 */
	private static AvailObject mostSpecificType;

	/**
	 * Answer the most specific {@linkplain PojoTypeDescriptor pojo type}, other
	 * than {@linkplain BottomTypeDescriptor#bottom() bottom}.
	 *
	 * @return The most specific pojo type.
	 */
	public static @NotNull AvailObject mostSpecificType ()
	{
		return mostSpecificType;
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
		return longRange;
	}

	/**
	 * Create any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void createWellKnownObjects ()
	{
		mostGeneralType = create(Object.class, TupleDescriptor.empty());
		mostSpecificType = create((Class<?>) null, TupleDescriptor.empty());
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
		mostGeneralType = null;
		mostSpecificType = null;
		byteRange = null;
		shortRange = null;
		intRange = null;
		longRange = null;
		charRange = null;
	}

	/**
	 * Obtain a canonical {@linkplain RawPojoDescriptor raw pojo} for the
	 * specified {@linkplain Class raw Java type}.
	 *
	 * @param rawTypeMap
	 *        The {@linkplain Map map} that locally establishes the
	 *        canonical identity of the raw pojo.
	 * @param rawType
	 *        A raw Java type.
	 * @return A canonical raw pojo corresponding to the raw Java type.
	 */
	static @NotNull AvailObject canonize (
		final @NotNull Map<Class<?>, AvailObject> rawTypeMap,
		final @NotNull Class<?> rawType)
	{
		AvailObject rawPojo = rawTypeMap.get(rawType);
		if (rawPojo == null)
		{
			rawPojo = RawPojoDescriptor.create(rawType);
			rawTypeMap.put(rawType, rawPojo);
		}
		return rawPojo;
	}

	/**
	 * Resolve a {@linkplain ParameterizedType parameterized type} into a
	 * {@linkplain PojoTypeDescriptor pojo type}.
	 *
	 * @param parameterizedType
	 *        A parameterized type.
	 * @param currentTypeVar
	 *        The {@linkplain TypeVariable type variable} whose {@linkplain
	 *        #resolveUpperBounds(TypeVariable, Map, Map) upper bounds are
	 *        currently being computed}.
	 * @param typeVarMap
	 *        A {@linkplain Map map} from type variables to their resolved pojo
	 *        types.
	 * @param rawTypeMap
	 *        A map from {@linkplain Class Java classes} to {@linkplain
	 *        RawPojoDescriptor raw pojos}.
	 * @return A pojo type.
	 */
	private static @NotNull AvailObject resolveParameterizedType (
		final @NotNull ParameterizedType parameterizedType,
		final @NotNull TypeVariable<?> currentTypeVar,
		final @NotNull Map<TypeVariable<?>, AvailObject> typeVarMap,
		final @NotNull Map<Class<?>, AvailObject> rawTypeMap)
	{
		final Class<?> rawType =
			(Class<?>) parameterizedType.getRawType();
		final List<AvailObject> params = new ArrayList<AvailObject>();
		for (final Type type : parameterizedType.getActualTypeArguments())
		{
			// The type parameter is a Java class. Build a trivial pojo type to
			// represent it.
			if (type instanceof Class<?>)
			{
				final Class<?> rawParamType = (Class<?>) type;
				canonize(rawTypeMap, rawParamType);
				params.add(create(rawParamType, TupleDescriptor.empty()));
			}
			// The type parameter is a parameterized type. Recurse to compute
			// the pojo type.
			else if (type instanceof ParameterizedType)
			{
				params.add(resolveParameterizedType(
					(ParameterizedType) type,
					currentTypeVar,
					typeVarMap,
					rawTypeMap));
			}
			// The type parameter is a type variable. It must either be one that
			// has already been fully resolved or be the type variable
			// undergoing resolution. In the latter case, use the self type to
			// curtail the recursion.
			else if (type instanceof TypeVariable<?>)
			{
				final TypeVariable<?> var = (TypeVariable<?>) type;
				assert typeVarMap.containsKey(var)
					|| var.equals(currentTypeVar);
				final AvailObject pojoType;
				if (var.equals(currentTypeVar))
				{
					pojoType = PojoSelfTypeDescriptor.create(rawType);
				}
				else
				{
					pojoType = typeVarMap.get(var);
				}
				params.add(pojoType);
			}
			else
			{
				assert false : "This should not happen.";
			}
		}
		return create(rawType, TupleDescriptor.fromCollection(params));
	}

	/**
	 * Recursively resolve into {@linkplain PojoTypeDescriptor pojo types} all
	 * upper bounds of the specified {@linkplain TypeVariable type variable}.
	 *
	 * @param typeVar
	 *        A type variable.
	 * @param typeVarMap
	 *        A {@linkplain Map map} from type variables to their resolved pojo
	 *        types.
	 * @param rawTypeMap
	 *        A map from {@linkplain Class Java classes} to {@linkplain
	 *        RawPojoDescriptor raw pojos}.
	 * @return The resolved upper bounds.
	 */
	private static @NotNull List<AvailObject> resolveUpperBounds (
		final @NotNull TypeVariable<?> typeVar,
		final @NotNull Map<TypeVariable<?>, AvailObject> typeVarMap,
		final @NotNull Map<Class<?>, AvailObject> rawTypeMap)
	{
		assert !typeVarMap.containsKey(typeVar);
		final List<AvailObject> resolved = new ArrayList<AvailObject>();
		for (final Type upperBound : typeVar.getBounds())
		{
			final AvailObject pojoType;
			if (upperBound instanceof Class<?>)
			{
				final Class<?> rawType = (Class<?>) upperBound;
				// If the upper bound is Object, then pretend that it was the
				// Avail type "any" instead.
				if (rawType.equals(Object.class))
				{
					pojoType = ANY.o();
				}
				else
				{
					canonize(rawTypeMap, rawType);
					pojoType = create(rawType, TupleDescriptor.empty());
				}
			}
			else if (upperBound instanceof ParameterizedType)
			{
				assert !typeVarMap.containsKey(typeVar);
				final ParameterizedType parameterizedType =
					(ParameterizedType) upperBound;
				pojoType = resolveParameterizedType(
					parameterizedType, typeVar, typeVarMap, rawTypeMap);
			}
			else if (upperBound instanceof TypeVariable<?>)
			{
				final TypeVariable<?> resolvedTypeVar =
					(TypeVariable<?>) upperBound;
				pojoType = typeVarMap.get(resolvedTypeVar);
			}
			// This should not happen.
			else
			{
				assert false : "This should not happen.";
				pojoType = null;
			}
			// Canonize the raw type to a raw pojo, record the resolved raw type
			// of the type variable, and add the raw type to the output.
			assert pojoType != null;
			typeVarMap.put(typeVar, pojoType);
			resolved.add(pojoType);
		}
		return resolved;
	}


	/**
	 * Resolve into an Avail {@linkplain TypeDescriptor type} the upper bound
	 * of the specified {@linkplain TypeVariable type variable}.
	 *
	 * @param typeVar
	 *        A type variable.
	 * @param typeVarMap
	 *        A {@linkplain Map map} from type variables to their resolved pojo
	 *        types.
	 * @param rawTypeMap
	 *        A map from {@linkplain Class Java classes} to {@linkplain
	 *        PojoTypeDescriptor pojo types}.
	 * @return The upper bound.
	 */
	public static @NotNull AvailObject upperBoundFor (
		final @NotNull TypeVariable<?> typeVar,
		final @NotNull Map<TypeVariable<?>, AvailObject> typeVarMap,
		final @NotNull Map<Class<?>, AvailObject> rawTypeMap)
	{
		final List<AvailObject> upperBounds = resolveUpperBounds(
			typeVar, typeVarMap, rawTypeMap);
		AvailObject intersectionBound = ANY.o();
		for (final AvailObject upperBound : upperBounds)
		{
			intersectionBound =
				intersectionBound.typeIntersection(upperBound);
		}
		return intersectionBound;
	}

	/** The layout of the object slots. */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} that represents the most
		 * specific {@linkplain Class raw Java class} included by this
		 * {@linkplain PojoTypeDescriptor pojo type}. May be the Avail
		 * {@linkplain NullDescriptor#nullObject() null object} if this pojo
		 * type does not represent a Java class, e.g. it may instead represent
		 * an interface or a fused pojo type.
		 */
		MOST_SPECIFIC_CLASS,

		/**
		 * A {@linkplain MapDescriptor map} of {@linkplain PojoDescriptor pojos}
		 * representing {@Linkplain Class raw Java classes} to their {@linkplain
		 * TupleDescriptor type parameterizations}. The {@linkplain
		 * AvailObject#keysAsSet() keys} of this map are the complete
		 * {@linkplain SetDescriptor set} of Java types to which this pojo type
		 * conforms, i.e. its own class, all superclasses, and all
		 * superinterfaces.
		 */
		PARAMETERIZATION_MAP,
	}

	@Override
	boolean o_IsAbstract (final @NotNull AvailObject object)
	{
		if (object.equals(mostSpecificType))
		{
			return true;
		}

		final AvailObject rawType = object.objectSlot(MOST_SPECIFIC_CLASS);

		// If the most specific class is the Avail null object, then this type
		// does not represent a Java class; it represents either an interface,
		// or a fusion type. It must therefore be abstract.
		if (rawType.equalsNull())
		{
			return true;
		}

		final Class<?> javaClass =
			(Class<?>) RawPojoDescriptor.getPojo(rawType);
		assert javaClass != null;
		final int modifiers = javaClass.getModifiers();
		return Modifier.isAbstract(modifiers)
			|| Modifier.isInterface(modifiers);
	}

	@Override
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsPojoType(object);
	}

	@Override
	boolean o_EqualsPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return aPojoType.equalsPojoType(object);
		}

		// Two pojo types with equal parameterization maps must have equal
		// most specific classes, so don't bother doing that comparison
		// explicitly.
		if (
			!object.objectSlot(PARAMETERIZATION_MAP).equals(
			aPojoType.objectSlot(PARAMETERIZATION_MAP)))
		{
			return false;
		}

		if (!object.sameAddressAs(aPojoType))
		{
			object.becomeIndirectionTo(aPojoType);
			aPojoType.makeImmutable();
		}

		return true;
	}

	@Override
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfPojoType(object);
	}

	@Override
	boolean o_IsSupertypeOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().isSupertypeOfPojoType(aPojoType);
		}

		if (aPojoType.equals(mostSpecificType))
		{
			return true;
		}

		if (object.equals(mostSpecificType))
		{
			return false;
		}

		// Check raw type compatibility by computing the set intersection of the
		// raw types of the arguments. If the result is equal to the raw types
		// of object, then object is a supertype of aPojoType.
		final AvailObject objectParamMap =
			object.objectSlot(PARAMETERIZATION_MAP);
		final AvailObject aPojoTypeParamMap =
			aPojoType.objectSlot(PARAMETERIZATION_MAP);
		final AvailObject objectTypes = objectParamMap.keysAsSet();
		final AvailObject aPojoTypeTypes = aPojoTypeParamMap.keysAsSet();
		final AvailObject intersection =
			objectTypes.setIntersectionCanDestroy(aPojoTypeTypes, false);
		if (!objectTypes.equals(intersection))
		{
			return false;
		}

		// For each raw type in the intersection, ensure that the
		// parameterizations are compatible. Java's type parameters are
		// (brokenly) always covariant, so check that the type parameters of
		// aPojoType are subtypes of the corresponding type parameters in
		// object.
		for (final AvailObject rawTypePojo : intersection)
		{
			final AvailObject objectParamTuple =
				objectParamMap.mapAt(rawTypePojo);
			final AvailObject aPojoTypeParamTuple =
				aPojoTypeParamMap.mapAt(rawTypePojo);
			final int limit = objectParamTuple.tupleSize();
			for (int i = 1; i <= limit; i++)
			{
				final AvailObject x = objectParamTuple.tupleAt(i);
				final AvailObject y = aPojoTypeParamTuple.tupleAt(i);
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

	@Override
	int o_Hash (final @NotNull AvailObject object)
	{
		// Note that this definition produces a value compatible with a pojo
		// self type; this is necessary to permit comparison between a pojo type
		// and its self type.
		final AvailObject map = object.objectSlot(PARAMETERIZATION_MAP);
		return (map.equalsNull() ? map.hash() : map.keysAsSet().hash())
			^ 0xA015BC44;
	}

	@Override
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return TYPE.o();
	}

	@Override
	@NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		object.descriptor = immutable();
		object.objectSlot(MOST_SPECIFIC_CLASS).makeImmutable();
		object.objectSlot(PARAMETERIZATION_MAP).makeImmutable();
		return object;
	}

	@Override
	@NotNull AvailObject o_PojoSelfType (
		final @NotNull AvailObject object)
	{
		if (object.equals(mostSpecificType))
		{
			return PojoSelfTypeDescriptor.create(
				RawPojoDescriptor.rawNullObject(),
				NullDescriptor.nullObject());
		}

		return PojoSelfTypeDescriptor.create(
			object.objectSlot(MOST_SPECIFIC_CLASS),
			object.objectSlot(PARAMETERIZATION_MAP).keysAsSet());
	}

	@Override
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

	@Override
	@NotNull AvailObject o_TypeIntersectionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return aPojoType.typeIntersectionOfPojoType(object);
		}

		final AvailObject objectMSC = object.objectSlot(MOST_SPECIFIC_CLASS);
		final AvailObject aPojoTypeMSC = aPojoType.traversed().objectSlot(
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
			return mostSpecificType;
		}
		// If neither class is an interface, then the intersection is the
		// most specific pojo type (because Java does not support multiple
		// inheritance of classes).
		if (!objectMSC.equalsNull()
			&& !Modifier.isInterface(objectModifiers)
			&& !aPojoTypeMSC.equalsNull()
			&& !Modifier.isInterface(aPojoTypeModifiers))
		{
			return mostSpecificType;
		}

		// Find the union of the key sets and the intersection of their
		// parameterizations.
		final AvailObject objectParamMap =
			object.objectSlot(PARAMETERIZATION_MAP);
		final AvailObject aPojoTypeParamMap =
			aPojoType.objectSlot(PARAMETERIZATION_MAP);
		final AvailObject objectTypes = objectParamMap.keysAsSet();
		final AvailObject aPojoTypeTypes = aPojoTypeParamMap.keysAsSet();
		final AvailObject union = objectTypes.setUnionCanDestroy(
			aPojoTypeTypes, false);
		AvailObject newParamMap =
			MapDescriptor.newWithCapacity(union.setSize());
		for (final AvailObject rawTypePojo : union)
		{
			final AvailObject objectParamTuple =
				objectParamMap.hasKey(rawTypePojo)
				? objectParamMap.mapAt(rawTypePojo)
				: aPojoTypeParamMap.mapAt(rawTypePojo);
			final AvailObject aPojoTypeParamTuple =
				aPojoTypeParamMap.hasKey(rawTypePojo)
				? aPojoTypeParamMap.mapAt(rawTypePojo)
				: objectParamMap.mapAt(rawTypePojo);
			final int limit = objectParamTuple.tupleSize();
			final List<AvailObject> intersectionParams =
				new ArrayList<AvailObject>(limit);
			for (int i = 1; i <= limit; i++)
			{
				final AvailObject x = objectParamTuple.tupleAt(i);
				final AvailObject y = aPojoTypeParamTuple.tupleAt(i);
				final AvailObject intersection = x.typeIntersection(y);
				if (intersection.isSubtypeOf(mostSpecificType))
				{
					return mostSpecificType;
				}
				intersectionParams.add(intersection);
			}
			newParamMap = newParamMap.mapAtPuttingCanDestroy(
				rawTypePojo.makeImmutable(),
				TupleDescriptor.fromCollection(intersectionParams),
				true);
		}

		// Use the Avail null object for the most specific class here. Since
		// the pojo types had no lineal relation, then the result of the
		// intersection is certainly abstract.
		return create(NullDescriptor.nullObject(), newParamMap);
	}

	@Override
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

	@Override
	@NotNull AvailObject o_TypeUnionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		if (aPojoType.isPojoSelfType())
		{
			return aPojoType.typeUnionOfPojoType(object);
		}

		// Find the intersection of the key sets and the union of their
		// parameterizations.
		final AvailObject objectParamMap =
			object.objectSlot(PARAMETERIZATION_MAP);
		final AvailObject aPojoTypeParamMap =
			aPojoType.traversed().objectSlot(PARAMETERIZATION_MAP);
		final AvailObject objectTypes = objectParamMap.keysAsSet();
		final AvailObject aPojoTypeTypes = aPojoTypeParamMap.keysAsSet();
		final AvailObject intersection = objectTypes.setIntersectionCanDestroy(
			aPojoTypeTypes, false);
		AvailObject newParamMap =
			MapDescriptor.newWithCapacity(intersection.setSize());
		for (final AvailObject rawTypePojo : intersection)
		{
			final AvailObject objectParamTuple =
				objectParamMap.mapAt(rawTypePojo);
			final AvailObject aPojoTypeParamTuple =
				aPojoTypeParamMap.mapAt(rawTypePojo);
			final int limit = objectParamTuple.tupleSize();
			final List<AvailObject> unionParams = new ArrayList<AvailObject>(
				limit);
			for (int i = 1; i <= limit; i++)
			{
				final AvailObject x = objectParamTuple.tupleAt(i);
				final AvailObject y = aPojoTypeParamTuple.tupleAt(i);
				final AvailObject union = x.typeUnion(y);
				unionParams.add(union);
			}
			newParamMap = newParamMap.mapAtPuttingCanDestroy(
				rawTypePojo.makeImmutable(),
				TupleDescriptor.fromCollection(unionParams),
				true);
		}

		// Build a map of raw types to pojos.
		final Map<Class<?>, AvailObject> rawTypeMap =
			new HashMap<Class<?>, AvailObject>(intersection.setSize());
		for (final AvailObject javaClass : newParamMap.keysAsSet())
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

		return create(mostSpecificClass, newParamMap);
	}

	/**
	 * Construct a new {@link PojoTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain AbstractDescriptor descriptor} represent a
	 *        mutable object?
	 */
	private PojoTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link PojoTypeDescriptor}. */
	private final static @NotNull PojoTypeDescriptor mutable =
		new PojoTypeDescriptor(true);

	/**
	 * Answer the mutable {@link PojoTypeDescriptor}.
	 *
	 * @return The mutable {@code PojoTypeDescriptor}.
	 */
	public static @NotNull PojoTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link PojoTypeDescriptor}. */
	private final static @NotNull PojoTypeDescriptor immutable =
		new PojoTypeDescriptor(false);

	/**
	 * Answer the immutable {@link PojoTypeDescriptor}.
	 *
	 * @return The immutable {@code PojoTypeDescriptor}.
	 */
	public static @NotNull PojoTypeDescriptor immutable ()
	{
		return immutable;
	}

	/**
	 * Are all of the keys of the specified {@linkplain MapDescriptor map}
	 * {@linkplain Class raw Java types}?
	 *
	 * @param map A map.
	 * @return {@code true} if the map's keys are all Java raw types, {@code
	 *         false} otherwise.
	 */
	private static boolean keysAreRawTypes (final @NotNull AvailObject map)
	{
		for (final AvailObject element : map.keysAsSet())
		{
			if (!element.isRawPojo())
			{
				return false;
			}

			final Object javaClass = RawPojoDescriptor.getPojo(element);
			if (!(javaClass instanceof Class<?>))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Do the preconditions for {@link #create(AvailObject, AvailObject)} hold?
	 *
	 * @param mostSpecificClass
	 *        The most specific {@linkplain Class Java class} included by this
	 *        pojo type, or the Avail {@linkplain NullDescriptor#nullObject()
	 *        null object} if the pojo type represents an interface or fusion.
	 * @param parameterizationMap
	 *        The type parameterization {@linkplain MapDescriptor map}. It maps
	 *        {@linkplain PojoDescriptor pojos} representing {@linkplain Class
	 *        raw Java types} to type parameterization {@linkplain
	 *        TupleDescriptor tuples}. These tuples may include arbitrary Avail
	 *        {@linkplain TypeDescriptor types}. Avail types are used to
	 *        represent Java primitive types.
	 * @return {@code true} if the preconditions hold, {@code false} otherwise.
	 */
	private static boolean createPreconditionsHold (
		final @NotNull AvailObject mostSpecificClass,
		final @NotNull AvailObject parameterizationMap)
	{
		if (mostSpecificClass.equals(RawPojoDescriptor.rawNullObject()))
		{
			return parameterizationMap.equalsNull();
		}
		if (!parameterizationMap.isMap()
			|| !keysAreRawTypes(parameterizationMap))
		{
			return false;
		}
		if (mostSpecificClass.equalsNull())
		{
			return true;
		}
		return mostSpecificClass.isRawPojo()
			&& (RawPojoDescriptor.getPojo(
				mostSpecificClass) instanceof Class<?>)
			&& parameterizationMap.hasKey(mostSpecificClass);
	}

	/**
	 * Create a new {@link AvailObject} that represents a {@linkplain
	 * PojoTypeDescriptor pojo type}.
	 *
	 * @param mostSpecificClass
	 *        The most specific {@linkplain Class Java class} included by this
	 *        pojo type, or the Avail {@linkplain NullDescriptor#nullObject()
	 *        null object} if the pojo type represents an interface or fusion.
	 * @param parameterizationMap
	 *        The type parameterization {@linkplain MapDescriptor map}. It maps
	 *        {@linkplain PojoDescriptor pojos} representing {@linkplain Class
	 *        raw Java types} to type parameterization {@linkplain
	 *        TupleDescriptor tuples}. These tuples may include arbitrary Avail
	 *        {@linkplain TypeDescriptor types}. Avail types are used to
	 *        represent Java primitive types.
	 * @return The new Avail pojo type.
	 */
	public static @NotNull AvailObject create (
		final @NotNull AvailObject mostSpecificClass,
		final @NotNull AvailObject parameterizationMap)
	{
		assert createPreconditionsHold(mostSpecificClass, parameterizationMap);
		final AvailObject newObject = mutable.create();
		newObject.objectSlotPut(MOST_SPECIFIC_CLASS, mostSpecificClass);
		newObject.objectSlotPut(PARAMETERIZATION_MAP, parameterizationMap);
		return newObject.makeImmutable();
	}

	/**
	 * Compute type propagation between {@code rawType} and its contextually
	 * parameterized supertype, {@code genericSupertype}.
	 *
	 * @param rawType
	 *        A {@linkplain Class raw Java type}.
	 * @param genericSupertype
	 *        The parameterized supertype, as viewed contextually from {@code
	 *        rawType}.
	 * @param parameters
	 *        The type parameterization {@linkplain TupleDescriptor tuple} for
	 *        {@code rawType}.
	 * @param selfTypeMap
	 *        A {@linkplain Map map} from raw Java types to the {@linkplain
	 *        PojoSelfTypeDescriptor pojo self types} that represent them.
	 * @return The propagated type parameters of the supertype.
	 */
	private static @NotNull AvailObject supertypeParameters (
		final @NotNull Class<?> rawType,
		final @NotNull Type genericSupertype,
		final @NotNull AvailObject parameters,
		final @NotNull Map<Class<?>, AvailObject> selfTypeMap)
	{
		final TypeVariable<?>[] vars = rawType.getTypeParameters();
		final List<AvailObject> propagation = new ArrayList<AvailObject>(
			vars.length);
		// If genericSuperclass is a Class, then it has no type parameters.
		if (genericSupertype instanceof Class<?>)
		{
			final Class<?> superclass = (Class<?>) genericSupertype;
			assert superclass.getTypeParameters().length == 0;
		}
		// If genericSuperclass is a ParameterizedType, then ascertain type
		// parameter propagation.
		else if (genericSupertype instanceof ParameterizedType)
		{
			final ParameterizedType supertype =
				(ParameterizedType) genericSupertype;
			final Type[] superVars = supertype.getActualTypeArguments();
			for (final Type weakSuperVar : superVars)
			{
				// If weakSuperVar is a Class, then it is fully specified.
				// Adjust rawTypeMap if necessary and append the appropriate
				// raw pojo to the propagation list.
				if (weakSuperVar instanceof Class<?>)
				{
					final AvailObject pojoType =
						selfTypeMap.containsKey(weakSuperVar)
						? selfTypeMap.get(weakSuperVar)
						: create(rawType, TupleDescriptor.empty());
					propagation.add(pojoType);
				}
				// If weakSuperVar is a TypeVariable, then scan vars to find
				// how propagation happens.
				else if (weakSuperVar instanceof TypeVariable<?>)
				{
					final TypeVariable<?> strongSuperVar =
						(TypeVariable<?>) weakSuperVar;
					boolean propagationSucceeded = false;
					for (int i = 0; i < vars.length; i++)
					{
						if (vars[i].getName().equals(strongSuperVar.getName()))
						{
							propagation.add(parameters.tupleAt(i + 1));
							propagationSucceeded = true;
							break;
						}
					}
					assert propagationSucceeded;
				}
				// This probably shouldn't happen ...
				else
				{
					assert false : "Unexpected type parameter.";
				}
			}
		}
		// This probably shouldn't happen ...
		else
		{
			assert false : "Unexpected generic declaration";
		}
		return TupleDescriptor.fromCollection(propagation);
	}

	/**
	 * Recursively create the {@linkplain ObjectSlots#PARAMETERIZATION_MAP type
	 * parameterization map} for the original {@code rawType}.
	 *
	 * @param rawType
	 *        A {@linkplain Class raw Java type}.
	 * @param parameters
	 *        The type parameterization {@linkplain TupleDescriptor tuple} for
	 *        {@code rawType}.
	 * @param parameterizationMap
	 *        The type parameterization map, incrementally recursively built
	 *        quasi-destructively.
	 * @param rawTypeMap
	 *        A {@linkplain Map map} from raw Java types to the {@linkplain
	 *        RawPojoDescriptor raw pojos} that embed them.
	 * @param selfTypeMap
	 *        A {@linkplain Map map} from raw Java types to the {@linkplain
	 *        PojoSelfTypeDescriptor pojo self types} that represent them.
	 * @return The replacement type parameterization map, ultimately the
	 *         complete type parameterization for the original raw Java type.
	 */
	private static @NotNull AvailObject create (
		final @NotNull Class<?> rawType,
		final @NotNull AvailObject parameters,
		@NotNull AvailObject parameterizationMap,
		final @NotNull Map<Class<?>, AvailObject> rawTypeMap,
		final @NotNull Map<Class<?>, AvailObject> selfTypeMap)
	{
		if (!rawTypeMap.containsKey(rawType))
		{
			final AvailObject javaClass = canonize(rawTypeMap, rawType);
			final AvailObject selfType = PojoSelfTypeDescriptor.create(rawType);
			selfTypeMap.put(rawType, selfType);
			parameterizationMap = parameterizationMap.mapAtPuttingCanDestroy(
				javaClass, parameters, true);

			// Recursively create the superclass.
			final Class<?> superclass = rawType.getSuperclass();
			if (superclass != null)
			{
				final AvailObject supertypeParameters = supertypeParameters(
					rawType,
					rawType.getGenericSuperclass(),
					parameters,
					selfTypeMap);
				parameterizationMap = create(
					superclass,
					supertypeParameters,
					parameterizationMap,
					rawTypeMap,
					selfTypeMap);
			}

			// Recursively create the superinterfaces.
			final Class<?>[] superinterfaces = rawType.getInterfaces();
			final Type[] weakSuperinterfaces = rawType.getGenericInterfaces();
			assert superinterfaces.length == weakSuperinterfaces.length;
			for (int i = 0; i < superinterfaces.length; i++)
			{
				final AvailObject supertypeParameters = supertypeParameters(
					rawType,
					weakSuperinterfaces[i],
					parameters,
					selfTypeMap);
				parameterizationMap = create(
					superinterfaces[i],
					supertypeParameters,
					parameterizationMap,
					rawTypeMap,
					selfTypeMap);
			}
		}
		return parameterizationMap;
	}

	/**
	 * Create a {@linkplain PojoTypeDescriptor pojo type} from the specified
	 * {@linkplain Class raw Java type} and  {@linkplain TypeDescriptor type}
	 * parameterization {@linkplain TupleDescriptor tuple}.
	 *
	 * @param rawType
	 *        The raw Java type, possibly {@code null} for the artificial
	 *        {@linkplain #mostSpecificType() most specific pojo type}.
	 * @param parameters
	 *        The type parameters.
	 * @return A new pojo type.
	 */
	public static @NotNull AvailObject create (
		final Class<?> rawType,
		final @NotNull AvailObject parameters)
	{
		// Construct the most specific pojo type specially.
		if (rawType == null)
		{
			return create(
				RawPojoDescriptor.rawNullObject(), NullDescriptor.nullObject());
		}

		// Explicitly seed the parameterization map with Object. From an Avail
		// programmer's point of view, a Java interface seems to inherit from
		// Object. This accomplishes that. It also prevents raw pojos for
		// Object's class from being created repeatedly.
		final Map<Class<?>, AvailObject> rawTypeMap =
			new HashMap<Class<?>, AvailObject>(5);
		final AvailObject rawObjectClass = RawPojoDescriptor.rawObjectClass();
		rawTypeMap.put(Object.class, rawObjectClass);
		AvailObject parameterizationMap = MapDescriptor.newWithCapacity(5);
		parameterizationMap = parameterizationMap.mapAtPuttingCanDestroy(
			rawObjectClass, TupleDescriptor.empty(), true);

		// Recursively build the parameterization map.
		parameterizationMap = create(
			rawType,
			parameters,
			parameterizationMap,
			rawTypeMap,
			new HashMap<Class<?>, AvailObject>(5));

		// Use the Avail null object as the most specific class if the raw Java
		// type is an abstract class or interface.
		final AvailObject mostSpecificClass;
		final int modifiers = rawType.getModifiers();
		if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers))
		{
			mostSpecificClass = NullDescriptor.nullObject();
		}
		else
		{
			mostSpecificClass = rawTypeMap.get(rawType);
			assert mostSpecificClass != null;
		}

		return create(mostSpecificClass, parameterizationMap);
	}
}
