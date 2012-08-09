/**
 * ArrayPojoTypeDescriptor.java
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

import static com.avail.descriptor.ArrayPojoTypeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.ArrayPojoTypeDescriptor.ObjectSlots.*;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.List;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

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
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
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
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 * @param <T> The element type.
	 */
	static final class PojoArray<T>
	implements Cloneable, Serializable
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = -4266609576367881124L;
	}

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
	boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == HASH_OR_ZERO;
	}

	@Override
	@NotNull AvailObject o_ContentType (final @NotNull AvailObject object)
	{
		return object.slot(CONTENT_TYPE);
	}

	@Override @AvailMethod
	boolean o_EqualsPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
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
		return false;
	}

	@Override @AvailMethod
	boolean o_IsPojoArrayType (final @NotNull AvailObject object)
	{
		return true;
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
		return RawPojoDescriptor.equalityWrap(PojoArray.class);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (final @NotNull AvailObject object)
	{
		object.descriptor = immutable;
		object.slot(JAVA_ANCESTORS).makeImmutable();
		object.slot(CONTENT_TYPE).makeImmutable();
		object.slot(SIZE_RANGE).makeImmutable();
		return object;
	}

	@Override
	Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> classHint)
	{
		final AvailObject elementType = object.slot(CONTENT_TYPE);
		return Array.newInstance(
			(Class<?>) elementType.marshalToJava(classHint), 0).getClass();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_PojoSelfType (final @NotNull AvailObject object)
	{
		return SelfPojoTypeDescriptor.create(
			RawPojoDescriptor.equalityWrap(PojoArray.class),
			object.slot(JAVA_ANCESTORS));
	}

	@Override
	@NotNull AvailObject o_SizeRange (final @NotNull AvailObject object)
	{
		return object.slot(SIZE_RANGE);
	}

	@Override @AvailMethod @ThreadSafe
	@NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
	{
		return SerializerOperation.ARRAY_POJO_TYPE;
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
		if (!aPojoType.isPojoArrayType())
		{
			return PojoTypeDescriptor.pojoBottom();
		}
		// Compute the type intersection of the two pojo array types.
		return create(
			object.slot(CONTENT_TYPE).typeIntersection(
				aPojoType.traversed().slot(CONTENT_TYPE)),
			object.slot(SIZE_RANGE).typeIntersection(
				aPojoType.traversed().slot(SIZE_RANGE)));
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfPojoFusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfPojoUnfusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anUnfusedPojoType)
	{
		throw unsupportedOperationException();
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
		return aPojoType.typeUnionOfPojoFusedType(object);
	}

	@Override @AvailMethod
	AvailObject o_TypeUnionOfPojoFusedType (
		final @NotNull AvailObject object,
		final AvailObject aFusedPojoType)
	{
		final AvailObject intersectionAncestors = computeUnion(
			object, aFusedPojoType);
		final AvailObject javaClass = mostSpecificOf(
			intersectionAncestors.keysAsSet());
		// If the intersection contains a most specific type, then the answer is
		// not a fused pojo type; otherwise it is.
		return !javaClass.equalsNull()
			? UnfusedPojoTypeDescriptor.create(javaClass, intersectionAncestors)
			: FusedPojoTypeDescriptor.create(intersectionAncestors);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfPojoUnfusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anUnfusedPojoType)
	{
		if (anUnfusedPojoType.isPojoSelfType())
		{
			return object.pojoSelfType().typeUnionOfPojoUnfusedType(
				anUnfusedPojoType);
		}
		final AvailObject intersectionAncestors = computeUnion(
			object, anUnfusedPojoType);
		final AvailObject javaClass = mostSpecificOf(
			intersectionAncestors.keysAsSet());
		// If the intersection contains a most specific type, then the answer is
		// not a fused pojo type; otherwise it is.
		return !javaClass.equalsNull()
			? UnfusedPojoTypeDescriptor.create(javaClass, intersectionAncestors)
			: FusedPojoTypeDescriptor.create(intersectionAncestors);
	}

	@Override
	@NotNull AvailObject o_TypeVariables (final @NotNull AvailObject object)
	{
		return MapDescriptor.empty();
	}

	@Override
	void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		object.slot(CONTENT_TYPE).printOnAvoidingIndent(
			builder, recursionList, indent);
		builder.append('[');
		final AvailObject range = object.slot(SIZE_RANGE);
		if (range.lowerBound().equals(range.upperBound()))
		{
			range.lowerBound().printOnAvoidingIndent(
				builder, recursionList, indent);
		}
		else if (IntegerRangeTypeDescriptor.wholeNumbers().isSubtypeOf(range))
		{
			// This is the most common range, as it corresponds with all real
			// Java array types.
		}
		else
		{
			range.lowerBound().printOnAvoidingIndent(
				builder, recursionList, indent);
			builder.append("..");
			range.upperBound().printOnAvoidingIndent(
				builder, recursionList, indent);
		}
		builder.append(']');
	}

	/**
	 * Construct a new {@link ArrayPojoTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain ArrayPojoTypeDescriptor descriptor}
	 *        represent a mutable object?
	 */
	public ArrayPojoTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link ArrayPojoTypeDescriptor}. */
	private final static @NotNull ArrayPojoTypeDescriptor mutable =
		new ArrayPojoTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ArrayPojoTypeDescriptor}.
	 *
	 * @return The mutable {@code ArrayPojoTypeDescriptor}.
	 */
	static @NotNull ArrayPojoTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ArrayPojoTypeDescriptor}. */
	private final static @NotNull ArrayPojoTypeDescriptor immutable =
		new ArrayPojoTypeDescriptor(false);

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
	static @NotNull AvailObject create (
		final @NotNull AvailObject elementType,
		final @NotNull AvailObject sizeRange)
	{
		AvailObject javaAncestors = PojoTypeDescriptor.arrayBaseAncestorMap();
		javaAncestors = javaAncestors.mapAtPuttingCanDestroy(
			RawPojoDescriptor.equalityWrap(PojoArray.class),
			TupleDescriptor.from(elementType),
			false);
		final AvailObject newObject = mutable.create();
		newObject.setSlot(JAVA_ANCESTORS, javaAncestors);
		newObject.setSlot(CONTENT_TYPE, elementType);
		newObject.setSlot(SIZE_RANGE, sizeRange);
		return newObject.makeImmutable();
	}
}
