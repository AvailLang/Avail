/**
 * PojoTypeDescriptor.java
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
import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;

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

	/** The most general {@linkplain PojoTypeDescriptor pojo array type}. */
	private static AvailObject mostGeneralArrayType;

	/**
	 * Answer the most general {@linkplain PojoTypeDescriptor pojo array type}.
	 *
	 * @return The most general pojo array type.
	 */
	public static @NotNull AvailObject mostGeneralArrayType ()
	{
		return mostGeneralArrayType;
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
		mostGeneralType.upperBoundMap(MapDescriptor.empty());
		mostGeneralArrayType = create(
			pojoArrayClass(), TupleDescriptor.from(TYPE.o()));
		mostGeneralArrayType.upperBoundMap(createUpperBoundMap(
			pojoArrayClass()));
		mostSpecificType = create((Class<?>) null, TupleDescriptor.empty());
		mostSpecificType.upperBoundMap(MapDescriptor.empty());
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
		mostGeneralArrayType = null;
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
	 *        #resolveAllUpperBounds(TypeVariable, AvailObject, Map) upper bounds
	 *        are currently being computed}.
	 * @param typeVarMap
	 *        A {@linkplain MapDescriptor map} from type variable {@linkplain
	 *        StringDescriptor names} to their resolved {@linkplain
	 *        TypeDescriptor upper bounds}.
	 * @param rawTypeMap
	 *        A map from raw {@linkplain Class Java types} to {@linkplain
	 *        RawPojoDescriptor raw pojos}.
	 * @return A pojo type.
	 */
	private static @NotNull AvailObject resolveParameterizedType (
		final @NotNull ParameterizedType parameterizedType,
		final @NotNull TypeVariable<?> currentTypeVar,
		final @NotNull AvailObject typeVarMap,
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
				final TypeVariable<?> typeVar = (TypeVariable<?>) type;
				final AvailObject pojoType;
				if (typeVar.equals(currentTypeVar))
				{
					pojoType = PojoSelfTypeDescriptor.create(rawType);
				}
				else
				{
					pojoType = typeVarMap.mapAt(typeVariableName(typeVar));
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
	 *        A {@linkplain MapDescriptor map} from type variable {@linkplain
	 *        StringDescriptor names} to their resolved {@linkplain
	 *        TypeDescriptor upper bounds}.
	 * @param rawTypeMap
	 *        A map from raw {@linkplain Class Java types} to {@linkplain
	 *        RawPojoDescriptor raw pojos}.
	 * @return The resolved upper bounds.
	 */
	private static @NotNull List<AvailObject> resolveAllUpperBounds (
		final @NotNull TypeVariable<?> typeVar,
		final @NotNull AvailObject typeVarMap,
		final @NotNull Map<Class<?>, AvailObject> rawTypeMap)
	{
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
				final ParameterizedType parameterizedType =
					(ParameterizedType) upperBound;
				pojoType = resolveParameterizedType(
					parameterizedType, typeVar, typeVarMap, rawTypeMap);
			}
			// Any type variable encountered at this point must have already
			// been completely resolved (i.e. it had to occur lexically before
			// the type variable undergoing upper bound computation).
			else if (upperBound instanceof TypeVariable<?>)
			{
				final TypeVariable<?> resolvedTypeVar =
					(TypeVariable<?>) upperBound;
				pojoType = typeVarMap.mapAt(typeVariableName(resolvedTypeVar));
			}
			// This should not happen.
			else
			{
				assert false : "This should not happen.";
				pojoType = null;
			}
			// Canonize the raw type to a raw pojo and add the raw type to the
			// output.
			assert pojoType != null;
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
	 *        A {@linkplain MapDescriptor map} from type variable {@linkplain
	 *        StringDescriptor names} to their resolved upper bounds.
	 * @param rawTypeMap
	 *        A map from raw {@linkplain Class Java types} to {@linkplain
	 *        RawPojoDescriptor raw pojos}.
	 * @return The upper bound.
	 */
	private static @NotNull AvailObject upperBound (
		final @NotNull TypeVariable<?> typeVar,
		final @NotNull AvailObject typeVarMap,
		final @NotNull Map<Class<?>, AvailObject> rawTypeMap)
	{
		final List<AvailObject> upperBounds = resolveAllUpperBounds(
			typeVar, typeVarMap, rawTypeMap);
		AvailObject intersectionBound = ANY.o();
		for (final AvailObject upperBound : upperBounds)
		{
			intersectionBound =
				intersectionBound.typeIntersection(upperBound);
		}
		return intersectionBound;
	}

	/**
	 * Answer the fully-qualified name of the specified {@linkplain TypeVariable
	 * type variable}.
	 *
	 * @param typeVar A type variable.
	 * @return The fully-qualified name of the declarer, then a ".", then the
	 *         lexical name of the type variable.
	 */
	public static @NotNull AvailObject typeVariableName (
		final @NotNull TypeVariable<?> typeVar)
	{
		final GenericDeclaration declarer = typeVar.getGenericDeclaration();
		final String typeName;
		// TODO: [TLS] Implement the other cases (if there are any).
		if (declarer instanceof Class<?>)
		{
			typeName =
				((Class<?>) declarer).getName() + "." + typeVar.getName();
		}
		else
		{
			assert false : "This should never happen";
			typeName = null;
		}
		return StringDescriptor.from(typeName);
	}

	/**
	 * Create the {@linkplain MapDescriptor map} from the fully-qualified
	 * {@linkplain TypeVariable type variable} {@linkplain StringDescriptor
	 * names} declared by the specified {@linkplain GenericDeclaration Java
	 * element} to their {@linkplain TypeDescriptor upper bounds}.
	 *
	 * @param javaElement
	 *        A Java element.
	 * @return A new {@linkplain ObjectSlots#UPPER_BOUND_MAP upper bound map}.
	 */
	public static @NotNull AvailObject createUpperBoundMap (
		final @NotNull GenericDeclaration javaElement)
	{
		AvailObject typeVarMap = MapDescriptor.empty();
		final TypeVariable<?>[] typeVars = javaElement.getTypeParameters();
		if (typeVars.length > 0)
		{
			final Map<Class<?>, AvailObject> rawTypeMap =
				new HashMap<Class<?>, AvailObject>(5);
			typeVarMap = MapDescriptor.newWithCapacity(typeVars.length);
			for (final TypeVariable<?> typeVar : typeVars)
			{
				final AvailObject upperBound = upperBound(
					typeVar, typeVarMap, rawTypeMap);
				typeVarMap = typeVarMap.mapAtPuttingCanDestroy(
					typeVariableName(typeVar), upperBound, true);
			}
		}
		return typeVarMap.makeImmutable();
	}

	/**
	 * {@code PojoArray} mimics the reflective properties of a Java array
	 * {@linkplain Class class}. It implements the same interfaces and professes
	 * that {@link Object} is its superclass. Array {@linkplain
	 * PojoTypeDescriptor pojo types} use {@code PojoArray} as their most
	 * specific class and represent the array's component type using the sole
	 * type parameter.
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 * @param <T> The component type of the represented array.
	 */
	private static class PojoArray<T> implements Cloneable, Serializable
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = 6632261359267941627L;

		/** An array. */
		final Object array;

		/**
		 * Answer the length of the array.
		 *
		 * @return The length of the array.
		 */
		public int length ()
		{
			return Array.getLength(array);
		}

		/**
		 * Get the element at the specified index of the array.
		 *
		 * @param index An index.
		 * @return An element.
		 */
		@SuppressWarnings("unchecked")
		public T get (final int index)
		{
			return (T) Array.get(array, index);
		}

		/**
		 * Store the element at the specified index of the array.
		 *
		 * @param index An index.
		 * @param value A value.
		 */
		private void set (final int index, final T value)
		{
			Array.set(array, index, value);
		}

		/**
		 * Construct a new {@link PojoArray}.
		 *
		 * @param array An array.
		 */
		public PojoArray (final @NotNull Object array)
		{
			assert array.getClass().isArray();
			this.array = array;
		}
	}

	/**
	 * Answer the {@linkplain Class Java class} corresponding to the array
	 * {@linkplain PojoTypeDescriptor pojo type}.
	 *
	 * @return A Java class.
	 */
	@SuppressWarnings("rawtypes")
	public static Class<PojoArray> pojoArrayClass ()
	{
		return PojoArray.class;
	}

	/**
	 * Marshal the {@linkplain TupleDescriptor tuple} of {@linkplain AvailObject
	 * Avail objects} to an array of Java {@linkplain Object counterparts}.
	 *
	 * @param availObjects
	 *        The Avail objects to be marshaled.
	 * @param counterpartClassPojos
	 *        The counterpart raw {@linkplain Class Java class} {@linkplain
	 *        RawPojoDescriptor pojos}. Each object to be marshaled will be
	 *        converted to an instance of its Java counterpart.
	 * @return The marshaled Java objects.
	 */
	public static @NotNull Object[] marshal (
		final @NotNull AvailObject availObjects,
		final @NotNull AvailObject counterpartClassPojos)
	{
		assert availObjects.isTuple();
		final Object[] javaObjects = new Object[availObjects.tupleSize()];
		for (int i = 0; i < javaObjects.length; i++)
		{
			final AvailObject availObject = availObjects.tupleAt(i + 1);
			final Class<?> javaClass = (Class<?>) RawPojoDescriptor.getPojo(
				counterpartClassPojos.tupleAt(i + 1));
			final Object object;
			if (javaClass.isPrimitive())
			{
				// Deliberately avoid autoboxing here so that we have explicit
				// control over the boxing conversions.
				if (javaClass.equals(Boolean.TYPE))
				{
					assert availObject.isBoolean();
					object = new Boolean(availObject.extractBoolean());
				}
				else if (javaClass.equals(Byte.TYPE))
				{
					assert availObject.isByte();
					object = new Byte((byte) availObject.extractByte());
				}
				else if (javaClass.equals(Short.TYPE))
				{
					assert availObject.isShort();
					object = new Short((short) availObject.extractShort());
				}
				else if (javaClass.equals(Integer.TYPE))
				{
					assert availObject.isInt();
					object = new Integer(availObject.extractInt());
				}
				else if (javaClass.equals(Long.TYPE))
				{
					assert availObject.isLong();
					object = new Long(availObject.extractLong());
				}
				else if (javaClass.equals(Float.TYPE))
				{
					assert availObject.isFloat();
					object = new Float(availObject.extractFloat());
				}
				else if (javaClass.equals(Double.TYPE))
				{
					assert availObject.isDouble();
					object = new Double(availObject.extractDouble());
				}
				else if (javaClass.equals(Character.TYPE))
				{
					if (availObject.isCharacter())
					{
						final int codePoint = availObject.codePoint();
						assert codePoint >= Character.MIN_VALUE
							&& codePoint <= Character.MAX_VALUE;
						object = new Character((char) codePoint);
					}
					else
					{
						object = new Character(
							(char) availObject.extractShort());
					}
				}
				else
				{
					assert false
						: "There are only the eight primitive Java types";
					object = null;
				}
			}
			else if (javaClass.equals(String.class))
			{
				assert availObject.isString();
				object = availObject.asNativeString();
			}
			else if (availObject.isPojo())
			{
				final Object rawPojo = RawPojoDescriptor.getPojo(
					availObject.rawPojo());
				if (rawPojo instanceof PojoArray<?>)
				{
					object = ((PojoArray<?>) rawPojo).array;
				}
				else
				{
					object = rawPojo;
				}
			}
			else
			{
				// Pass other Avail objects through unmarshaled.
				object = availObject;
			}
			javaObjects[i] = object;
		}
		return javaObjects;
	}

	/**
	 * Marshal the {@linkplain TupleDescriptor tuple} of {@linkplain AvailObject
	 * Avail types} to an array of Java {@linkplain Class counterparts}.
	 *
	 * @param availTypes
	 *        The Avail types to be marshaled.
	 * @return The marshaled Java types.
	 */
	public static @NotNull Class<?>[] marshalTypes (
		final @NotNull AvailObject availTypes)
	{
		assert availTypes.isTuple();
		final Class<?>[] javaClasses = new Class<?>[availTypes.tupleSize()];
		for (int i = 0; i < javaClasses.length; i++)
		{
			final AvailObject availType = availTypes.tupleAt(i + 1);
			final Class<?> javaClass;
			// Marshal integer range types to Java primitive classes.
			if (availType.isIntegerRangeType())
			{
				if (availType.equals(byteRange))
				{
					javaClass = Byte.TYPE;
				}
				else if (availType.equals(shortRange))
				{
					javaClass = Short.TYPE;
				}
				else if (availType.equals(intRange))
				{
					javaClass = Integer.TYPE;
				}
				else if (availType.equals(longRange))
				{
					javaClass = Long.TYPE;
				}
				// If the integer range type is something else, then treat the
				// type as opaque.
				else
				{
					javaClass = AvailObject.class;
				}
			}
			else if (availType.isSubtypeOf(
				EnumerationTypeDescriptor.booleanObject()))
			{
				javaClass = Boolean.TYPE;
			}
			else if (availType.isSubtypeOf(CHARACTER.o()))
			{
				javaClass = Character.TYPE;
			}
			else if (availType.isSubtypeOf(FLOAT.o()))
			{
				javaClass = Float.TYPE;
			}
			else if (availType.isSubtypeOf(DOUBLE.o()))
			{
				javaClass = Double.TYPE;
			}
			else if (availType.isSubtypeOf(
				TupleTypeDescriptor.stringTupleType()))
			{
				javaClass = String.class;
			}
			else if (availType.isSubtypeOf(mostGeneralType))
			{
				Class<?> tempClass = (Class<?>) RawPojoDescriptor.getPojo(
					availType.traversed().objectSlot(MOST_SPECIFIC_CLASS));
				// Recursively resolve a pojo array type.
				if (tempClass.equals(PojoArray.class))
				{
					tempClass = marshalTypes(availType.traversed().objectSlot(
						PARAMETERIZATION_MAP).mapAt(RawPojoDescriptor.create(
							PojoArray.class)))[0];
					tempClass = Array.newInstance(tempClass, 0).getClass();
				}
				javaClass = tempClass;
			}
			else
			{
				// Treat other Avail types (tuple, set, map, etc.) as opaque.
				javaClass = AvailObject.class;
			}
			javaClasses[i] = javaClass;
		}
		return javaClasses;
	}

	/**
	 * Marshal the arbitrary {@linkplain Object Java object} to its counterpart
	 * {@linkplain AvailObject Avail object}.
	 *
	 * @param object
	 *        A Java object.
	 * @param type
	 *        A {@linkplain TypeDescriptor type} to which the resultant Avail
	 *        object must conform.
	 * @return An Avail Object.
	 */
	public static @NotNull AvailObject unmarshal (
		final @NotNull Object object,
		final @NotNull AvailObject type)
	{
		final Class<?> javaClass = object.getClass();
		final AvailObject availObject;
		// If the type is explicitly a pojo type, then make sure that the result
		// does not undergo conversion to a semantically corresponding Avail
		// object.
		if (type.isPojoType())
		{
			availObject = PojoDescriptor.create(
				RawPojoDescriptor.create(object),
				type);
		}
		// Otherwise attempt to convert the object into a semantically
		// corresponding Avail object.
		else if (javaClass.isPrimitive())
		{
			if (javaClass.equals(Boolean.TYPE))
			{
				availObject = (Boolean) object
					? AtomDescriptor.trueObject()
					: AtomDescriptor.falseObject();
			}
			else if (javaClass.equals(Byte.TYPE))
			{
				availObject = IntegerDescriptor.fromInt((Byte) object);
			}
			else if (javaClass.equals(Short.TYPE))
			{
				availObject = IntegerDescriptor.fromInt((Short) object);
			}
			else if (javaClass.equals(Integer.TYPE))
			{
				availObject = IntegerDescriptor.fromInt((Integer) object);
			}
			else if (javaClass.equals(Long.TYPE))
			{
				availObject = IntegerDescriptor.fromLong((Long) object);
			}
			else if (javaClass.equals(Float.TYPE))
			{
				availObject = FloatDescriptor.fromFloat((Float) object);
			}
			else if (javaClass.equals(Double.TYPE))
			{
				availObject = DoubleDescriptor.fromDouble((Double) object);
			}
			else if (javaClass.equals(Character.TYPE))
			{
				availObject = CharacterDescriptor.fromCodePoint(
					((Character) object).charValue());
			}
			else
			{
				assert false
					: "There are only the eight primitive Java types";
				availObject = null;
			}
		}
		else if (javaClass.equals(String.class))
		{
			availObject = StringDescriptor.from((String) object);
		}
		else
		{
			availObject = (AvailObject) object;
		}
		assert availObject != null;
		assert availObject.isInstanceOfKind(type);
		return availObject;
	}

	/** The layout of the object slots. */
	@InnerAccess enum ObjectSlots
	implements ObjectSlotsEnum
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

		/**
		 * A {@linkplain MapDescriptor map} of {@linkplain StringDescriptor
		 * type variable names} to their {@linkplain TypeDescriptor upper
		 * bounds}.
		 */
		UPPER_BOUND_MAP
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == UPPER_BOUND_MAP;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsPojoType(object);
	}

	@Override @AvailMethod
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
		if (!object.objectSlot(PARAMETERIZATION_MAP).equals(
				aPojoType.objectSlot(PARAMETERIZATION_MAP)))
		{
			return false;
		}

		assert object.objectSlot(MOST_SPECIFIC_CLASS).equals(
			aPojoType.objectSlot(MOST_SPECIFIC_CLASS));

		// Only if the upper bound maps match (and the objects are not reference
		// identical) are we allowed to coalesce them.
		if (object.objectSlot(UPPER_BOUND_MAP).equals(
				aPojoType.objectSlot(UPPER_BOUND_MAP))
			&& !object.sameAddressAs(aPojoType))
		{
			object.becomeIndirectionTo(aPojoType);
			aPojoType.makeImmutable();
		}

		return true;
	}

	@Override @AvailMethod
	boolean o_IsAbstract (final @NotNull AvailObject object)
	{
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

		// This handles the most specific type.
		if (javaClass == null)
		{
			return true;
		}

		final int modifiers = javaClass.getModifiers();
		return Modifier.isAbstract(modifiers)
			|| Modifier.isInterface(modifiers);
	}

	@Override @AvailMethod
	boolean o_IsPojoType (final @NotNull AvailObject object)
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

	@Override @AvailMethod
	int o_Hash (final @NotNull AvailObject object)
	{
		// Note that this definition produces a value compatible with a pojo
		// self type; this is necessary to permit comparison between a pojo type
		// and its self type.
		final AvailObject map = object.objectSlot(PARAMETERIZATION_MAP);
		return (map.equalsNull() ? map.hash() : map.keysAsSet().hash())
			^ 0xA015BC44;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_JavaClass (final @NotNull AvailObject object)
	{
		return object.objectSlot(MOST_SPECIFIC_CLASS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (final @NotNull AvailObject object)
	{
		return TYPE.o();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		object.descriptor = immutable();
		object.objectSlot(MOST_SPECIFIC_CLASS).makeImmutable();
		object.objectSlot(PARAMETERIZATION_MAP).makeImmutable();
		return object;
	}

	@Override @AvailMethod
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

	@Override @AvailMethod
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

	@Override @AvailMethod
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
		// TODO: [TLS] Consider computing the upper bound map here. It would be
		// the "union map" of the upper bound maps of the pojo types being
		// intersected.
		return create(NullDescriptor.nullObject(), newParamMap);
	}

	@Override @AvailMethod
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

	@Override @AvailMethod
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

		// TODO: [TLS] Consider creating the upper bound map. It must include
		// all most specific classes and interfaces (we are currently not
		// computing them).
		return create(mostSpecificClass, newParamMap);
	}

	@Override @AvailMethod
	public @NotNull AvailObject o_UpperBoundMap (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(UPPER_BOUND_MAP);
	}

	@Override @AvailMethod
	public void o_UpperBoundMap (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMap)
	{
		assert object.objectSlot(UPPER_BOUND_MAP).equalsNull();
		object.objectSlotPut(UPPER_BOUND_MAP, aMap);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		final AvailObject classPojo = object.objectSlot(MOST_SPECIFIC_CLASS);
		if (classPojo.equalsNull())
		{
			super.printObjectOnAvoidingIndent(
				object, builder, recursionList, indent);
			return;
		}

		final Class<?> javaClass =
			(Class<?>) RawPojoDescriptor.getPojo(classPojo);
		if (javaClass == null)
		{
			builder.append(String.valueOf(javaClass));
			return;
		}

		builder.append(javaClass.getName());
		final AvailObject typeParams =
			object.objectSlot(PARAMETERIZATION_MAP).mapAt(classPojo);
		if (typeParams.tupleSize() > 0)
		{
			final TypeVariable<?>[] typeVars = javaClass.getTypeParameters();
			final AvailObject upperBoundMap =
				object.objectSlot(UPPER_BOUND_MAP);
			boolean first = true;
			builder.append('<');
			for (int i = 1; i <= typeParams.tupleSize(); i++)
			{
				if (!first)
				{
					builder.append(", ");
					first = true;
				}
				typeParams.tupleAt(i).printOnAvoidingIndent(
					builder, recursionList, indent);
				builder.append(" ≤ ");
				final AvailObject typeVarName =
					typeVariableName(typeVars[i - 1]);
				upperBoundMap.mapAt(typeVarName).printOnAvoidingIndent(
					builder, recursionList, indent);
			}
			builder.append('>');
		}
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
	 * PojoTypeDescriptor pojo type}. The {@linkplain
	 * AvailObject#upperBoundMap() upper bound map} is set to the {@linkplain
	 * NullDescriptor#nullObject() null object}.
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
		newObject.objectSlotPut(UPPER_BOUND_MAP, NullDescriptor.nullObject());
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
				// If weakSuperVar is a Class (e.g. String), then it is fully
				// specified. Adjust rawTypeMap if necessary and append the
				// appropriate raw pojo to the propagation list.
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
				// If weakSuperVar is a ParameterizedType (e.g. List<String>),
				// then it may be recursively specified in terms of Classes and
				// already encountered TypeVariables.
				else if (weakSuperVar instanceof ParameterizedType)
				{
					// TODO: [TLS] Implement this!
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
				RawPojoDescriptor.rawNullObject(),
				NullDescriptor.nullObject());
		}

		// Explicitly seed the parameterization map with Object. From an Avail
		// programmer's point of view, a Java interface seems to inherit from
		// Object. This accomplishes that. It also prevents raw pojos for
		// Object's class from being created repeatedly.
		final Map<Class<?>, AvailObject> rawTypeMap =
			new HashMap<Class<?>, AvailObject>(5);
		final AvailObject rawObjectClass = canonize(rawTypeMap, Object.class);
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
