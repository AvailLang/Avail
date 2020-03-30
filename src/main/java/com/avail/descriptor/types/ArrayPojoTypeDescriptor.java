/*
 * ArrayPojoTypeDescriptor.java
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
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.maps.A_Map;
import com.avail.descriptor.maps.MapDescriptor;
import com.avail.descriptor.pojos.PojoDescriptor;
import com.avail.descriptor.pojos.RawPojoDescriptor;
import com.avail.descriptor.representation.AbstractSlotsEnum;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.sets.SetDescriptor;
import com.avail.descriptor.tuples.TupleDescriptor;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.IdentityHashMap;

import static com.avail.descriptor.maps.MapDescriptor.emptyMap;
import static com.avail.descriptor.pojos.RawPojoDescriptor.equalityPojo;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.tuples.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.types.ArrayPojoTypeDescriptor.IntegerSlots.HASH_AND_MORE;
import static com.avail.descriptor.types.ArrayPojoTypeDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.types.ArrayPojoTypeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.types.BottomPojoTypeDescriptor.pojoBottom;
import static com.avail.descriptor.types.FusedPojoTypeDescriptor.createFusedPojoType;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.types.SelfPojoTypeDescriptor.newSelfPojoType;
import static com.avail.descriptor.types.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.types.UnfusedPojoTypeDescriptor.createUnfusedPojoType;

/**
 * {@code ArrayPojoTypeDescriptor} describes Java array types. A Java array
 * type extends {@link Object java.lang.Object} and implements {@link
 * Cloneable java.lang.Cloneable} and {@link Serializable java.io.Serializable}.
 * It has an element type and a fixed size.
 *
 * <p>Avail expands upon these features in two ways. First, a pojo array type
 * may have any {@linkplain TypeDescriptor Avail type} as its element type; this
 * is, of course, a superset of pojo types. Second, it may express a range of
 * sizes, not just a single fixed size; this is analogous to the size ranges
 * supported by {@linkplain TupleTypeDescriptor tuple types}, {@linkplain
 * SetTypeDescriptor set types}, and {@linkplain MapTypeDescriptor map
 * types}.</p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class ArrayPojoTypeDescriptor
extends PojoTypeDescriptor
{
	/**
	 * {@code PojoArray} mimics the type properties of Java array types. It
	 * extends {@link Object java.lang.Object} and implements {@link
	 * Cloneable java.lang.Cloneable} and {@link Serializable
	 * java.io.Serializable}, as required by the Java language specification.
	 * The type parameter is used to specify the element type.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 * @param <T> The element type.
	 */
	@SuppressWarnings({
		"AbstractClassNeverImplemented",
		"AbstractClassWithoutAbstractMethods",
		"unused"
	})
	abstract static class PojoArray<T>
	implements Cloneable, Serializable
	{
	}

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
		 * A lazy {@linkplain MapDescriptor map} from {@linkplain PojoDescriptor
		 * pojos} that wrap {@linkplain Class Java classes and interfaces} to
		 * their {@linkplain TupleDescriptor type parameterizations}. The
		 * {@linkplain AvailObject#keysAsSet() keys} constitute this type's
		 * complete {@linkplain SetDescriptor ancestry} of Java types.
		 */
		JAVA_ANCESTORS,

		/**
		 * The {@linkplain TypeDescriptor type} of elements that may be read
		 * from instances of this type. (We say "read" because Java incorrectly
		 * treats arrays as though they are covariant data types.)
		 */
		CONTENT_TYPE,

		/**
		 * An {@linkplain IntegerRangeTypeDescriptor integer range} that
		 * specifies all allowed array sizes for instances of this type.
		 */
		SIZE_RANGE
	}

	@Override
	protected boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == HASH_AND_MORE;
	}

	@Override
	protected A_Type o_ContentType (final AvailObject object)
	{
		return object.slot(CONTENT_TYPE);
	}

	@Override @AvailMethod
	protected boolean o_EqualsPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		if (aPojoType.equalsPojoBottomType())
		{
			return false;
		}
		if (aPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().equalsPojoType(aPojoType);
		}
		if (!object.slot(SIZE_RANGE).equals(aPojoType.sizeRange())
			|| !object.slot(CONTENT_TYPE).equals(aPojoType.contentType()))
		{
			return false;
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
	 * Lazily compute and install the hash of the {@linkplain
	 * ArrayPojoTypeDescriptor object}.
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
		return false;
	}

	@Override @AvailMethod
	protected boolean o_IsPojoArrayType (final AvailObject object)
	{
		return true;
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
		return equalityPojo(PojoArray.class);
	}

	@Override
	protected @Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> classHint)
	{
		final A_BasicObject elementType = object.slot(CONTENT_TYPE);
		return Array.newInstance(
			(Class<?>) elementType.marshalToJava(classHint), 0).getClass();
	}

	@Override @AvailMethod
	protected A_Type o_PojoSelfType (final AvailObject object)
	{
		return newSelfPojoType(
			equalityPojo(PojoArray.class),
			object.slot(JAVA_ANCESTORS));
	}

	@Override
	protected A_Type o_SizeRange (final AvailObject object)
	{
		return object.slot(SIZE_RANGE);
	}

	@Override @AvailMethod @ThreadSafe
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.ARRAY_POJO_TYPE;
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
		if (!aPojoType.isPojoArrayType())
		{
			return pojoBottom();
		}
		// Compute the type intersection of the two pojo array types.
		return arrayPojoType(
			object.slot(CONTENT_TYPE).typeIntersection(
				aPojoType.traversed().slot(CONTENT_TYPE)),
			object.slot(SIZE_RANGE).typeIntersection(
				aPojoType.traversed().slot(SIZE_RANGE)));
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersectionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersectionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		throw unsupportedOperationException();
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
			aPojoType.typeUnionOfPojoFusedType(object),
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
		if (anUnfusedPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().typeUnionOfPojoUnfusedType(
				anUnfusedPojoType);
		}
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

	@Override
	protected A_Map o_TypeVariables (final AvailObject object)
	{
		return emptyMap();
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		object.slot(CONTENT_TYPE).printOnAvoidingIndent(
			builder, recursionMap, indent);
		builder.append('[');
		final AvailObject range = object.slot(SIZE_RANGE);
		if (range.lowerBound().equals(range.upperBound()))
		{
			range.lowerBound().printOnAvoidingIndent(
				builder, recursionMap, indent);
		}
		else if (wholeNumbers().isSubtypeOf(range))
		{
			// This is the most common range, as it corresponds with all real
			// Java array types.
		}
		else
		{
			range.lowerBound().printOnAvoidingIndent(
				builder, recursionMap, indent);
			builder.append("..");
			range.upperBound().printOnAvoidingIndent(
				builder, recursionMap, indent);
		}
		builder.append(']');
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("array pojo type");
		writer.write("content type");
		object.slot(CONTENT_TYPE).writeTo(writer);
		writer.write("size range");
		object.slot(SIZE_RANGE).writeTo(writer);
		writer.endObject();
	}

	/**
	 * Construct a new {@code ArrayPojoTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ArrayPojoTypeDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link ArrayPojoTypeDescriptor}. */
	private static final ArrayPojoTypeDescriptor mutable =
		new ArrayPojoTypeDescriptor(Mutability.MUTABLE);

	@Override
	public ArrayPojoTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ArrayPojoTypeDescriptor}. */
	private static final ArrayPojoTypeDescriptor immutable =
		new ArrayPojoTypeDescriptor(Mutability.IMMUTABLE);

	@Override
	public ArrayPojoTypeDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link ArrayPojoTypeDescriptor}. */
	private static final ArrayPojoTypeDescriptor shared =
		new ArrayPojoTypeDescriptor(Mutability.SHARED);

	@Override
	public ArrayPojoTypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * The {@linkplain MapDescriptor map} used by {@linkplain
	 * ArrayPojoTypeDescriptor array pojo types}.  Note that this map does not
	 * contain the entry for {@link PojoArray}, as this has to be specialized
	 * per pojo array type.
	 */
	private static final A_Map arrayBaseAncestorMap;

	static
	{
		A_Map javaAncestors = emptyMap();
		javaAncestors = javaAncestors.mapAtPuttingCanDestroy(
			RawPojoDescriptor.rawObjectClass(),
			emptyTuple(),
			true);
		javaAncestors = javaAncestors.mapAtPuttingCanDestroy(
			equalityPojo(Cloneable.class),
			emptyTuple(),
			true);
		javaAncestors = javaAncestors.mapAtPuttingCanDestroy(
			equalityPojo(Serializable.class),
			emptyTuple(),
			true);
		arrayBaseAncestorMap = javaAncestors.makeShared();
	}

	/** The most general {@linkplain PojoTypeDescriptor pojo array type}. */
	static final A_Type mostGeneralType = pojoArrayType(
		ANY.o(), wholeNumbers()).makeShared();

	/**
	 * Create a new {@link AvailObject} that represents a {@linkplain
	 * ArrayPojoTypeDescriptor pojo array type}.
	 *
	 * @param elementType
	 *        The {@linkplain TypeDescriptor type} of elements that may be read
	 *        from instances of this type. (We say "read" because Java
	 *        incorrectly treats arrays as though they are covariant data
	 *        types.)
	 * @param sizeRange
	 *        An {@linkplain IntegerRangeTypeDescriptor integer range} that
	 *        specifies all allowed array sizes for instances of this type. This
	 *        must be a subtype of {@linkplain
	 *        IntegerRangeTypeDescriptor#wholeNumbers() whole number}.
	 * @return The requested pojo array type.
	 */
	static AvailObject arrayPojoType (
		final A_Type elementType,
		final A_Type sizeRange)
	{
		A_Map javaAncestors = arrayBaseAncestorMap;
		javaAncestors = javaAncestors.mapAtPuttingCanDestroy(
			equalityPojo(PojoArray.class),
			tuple(elementType),
			false);
		final AvailObject newObject = mutable.create();
		newObject.setSlot(JAVA_ANCESTORS, javaAncestors);
		newObject.setSlot(CONTENT_TYPE, elementType);
		newObject.setSlot(SIZE_RANGE, sizeRange);
		return newObject.makeImmutable();
	}
}
