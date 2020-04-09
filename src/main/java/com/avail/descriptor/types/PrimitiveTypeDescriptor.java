/*
 * PrimitiveTypeDescriptor.java
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
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.NilDescriptor;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.IntegerSlotsEnum;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.representation.ObjectSlotsEnum;
import com.avail.descriptor.tuples.A_String;
import com.avail.descriptor.tuples.StringDescriptor;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.tuples.StringDescriptor.stringFrom;
import static com.avail.descriptor.types.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.types.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.types.PrimitiveTypeDescriptor.IntegerSlots.HASH;
import static com.avail.descriptor.types.PrimitiveTypeDescriptor.ObjectSlots.NAME;
import static com.avail.descriptor.types.PrimitiveTypeDescriptor.ObjectSlots.PARENT;
import static com.avail.descriptor.types.TypeDescriptor.Types.*;
import static com.avail.utility.Nulls.stripNull;

/**
 * The primitive types of Avail are different from the notion of primitive types
 * in other object-oriented languages. Traditionally, a compiler or virtual
 * machine encodes representation knowledge about and makes other special
 * provisions about its primitive types. Since <em>all</em> types are in a
 * sense provided by the Avail system, it has no special primitive types that
 * fill that role – they're <em>all</em> special.
 *
 * <p>
 * Instead, the term "primitive type" in Avail refers to the top section of the
 * type lattice which partitions the rest of the lattice into broad categories
 * of essential disjoint subgraphs. This includes the ultimate type {@linkplain
 * Types#TOP top (⊤)}, the penultimate type {@linkplain
 * Types#ANY any}, and various specialties such as {@linkplain
 * Types#ATOM atom} and {@linkplain Types#NUMBER
 * number}. Type hierarchies that have a natural root don't bother with a
 * primitive type to delimit the hierarchy, using the natural root itself. For
 * example, the tuple type whose instances include all tuples is a natural root
 * of the tuple types.
 * </p>
 *
 * @see Types all primitive types
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class PrimitiveTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnumJava
	{
		/**
		 * The low 32 bits are used for caching the hash, and the upper 32 are
		 * for the ordinal of the primitive type.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * The hash, populated during construction.
		 */
		public static final BitField HASH = new BitField(HASH_AND_MORE, 0, 32);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/**
		 * The {@linkplain StringDescriptor name} of this primitive type.
		 */
		NAME,

		/**
		 * The parent type of this primitive type.
		 */
		PARENT
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append(object.slot(NAME).asNativeString());
	}

	/**
	 * Extract the {@link Types} enum value from this primitive type.
	 *
	 * @param object The primitive type.
	 * @return The {@link Types} enum value.
	 */
	private static Types extractEnum (final AvailObject object)
	{
		return stripNull(
			((PrimitiveTypeDescriptor) object.descriptor()).primitiveType);
	}

	/**
	 * Extract the {@link Types} enum value's {@link Enum#ordinal()}
	 * from this primitive type.
	 *
	 * @param object The primitive type.
	 * @return The {@link Types} enum value's ordinal.
	 */
	public static int extractOrdinal (final AvailObject object)
	{
		return extractEnum(object).ordinal();
	}

	@Override @AvailMethod
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsPrimitiveType(object);
	}

	@Override @AvailMethod
	protected boolean o_EqualsPrimitiveType (
		final AvailObject object,
		final A_Type aPrimitiveType)
	{
		// Primitive types compare by identity.
		return object.sameAddressAs(aPrimitiveType);
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		return object.slot(HASH);
	}

	@Override @AvailMethod
	protected A_BasicObject o_Parent (final AvailObject object)
	{
		return object.slot(PARENT);
	}

	@Override @AvailMethod
	protected boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		// Check if object (a type) is a subtype of aType (should also be a
		// type).
		return aType.isSupertypeOfPrimitiveTypeEnum(extractEnum(object));
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		// This primitive type is a supertype of aFiberType if and only if
		// this primitive type is a supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		// This primitive type is a supertype of aFunctionType if and only if
		// this primitive type is a supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		// A primitive type is a supertype of a variable type if it is a
		// supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		// A primitive type is a supertype of a continuation type if it is a
		// supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		// A primitive type is a supertype of a compiled code type if it is a
		// supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		// Parent of the top integer range type is number, so continue
		// searching there.
		return object.isSupertypeOfPrimitiveTypeEnum(NUMBER);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		// This primitive type is a supertype of aTokenType if and only if this
		// primitive type is a supertype of TOKEN.
		return object.isSupertypeOfPrimitiveTypeEnum(TOKEN);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		// This primitive type is a supertype of aLiteralTokenType if and only
		// if this primitive type is a supertype of TOKEN.
		return object.isSupertypeOfPrimitiveTypeEnum(TOKEN);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		// This primitive type is a supertype of aMapType if and only if this
		// primitive type is a supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		// Check if I'm a supertype of the given eager object type. Only NONTYPE
		// and its ancestors are supertypes of an object type.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override
	protected boolean o_IsSupertypeOfPojoBottomType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return
			primitiveTypeEnum.superTests[extractOrdinal(object)];
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		// This primitive type is a supertype of aSetType if and only if this
		// primitive type is a supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		// This primitive type is a supertype of aTupleType if and only if this
		// primitive type is a supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfEnumerationType (
		final AvailObject object,
		final A_BasicObject anEnumerationType)
	{
		return topMeta().isSubtypeOf(object);
	}

	@Override
	protected final AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// There is no immutable descriptor; use the shared one.
			object.makeShared();
		}
		return object;
	}

	@Override @AvailMethod
	protected @Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		for (final Types type : all())
		{
			if (object.equals(type.o()))
			{
				switch (type)
				{
					case TOP:
						return Void.class;
					case ANY:
						return Object.class;
					case DOUBLE:
						return double.class;
					case FLOAT:
						return float.class;
					case ABSTRACT_DEFINITION:
					case ATOM:
					case CHARACTER:
					case DEFINITION_PARSING_PLAN:
					case FORWARD_DEFINITION:
					case LEXER:
					case MACRO_DEFINITION:
					case MESSAGE_BUNDLE:
					case MESSAGE_BUNDLE_TREE:
					case METHOD:
					case METHOD_DEFINITION:
					case MODULE:
					case NONTYPE:
					case NUMBER:
					case PARSING_PLAN_IN_PROGRESS:
					case RAW_POJO:
					case DEFINITION:
					case TOKEN:
						return super.o_MarshalToJava(object, ignoredClassHint);
				}
			}
		}
		assert false
			: "All cases have been dealt with, and each forces a return";
		throw new RuntimeException();
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		// Most of the primitive types are already handled as special objects,
		// so this only kicks in as a backup.
		return SerializerOperation.ARBITRARY_PRIMITIVE_TYPE;
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		return another.typeIntersectionOfPrimitiveTypeEnum(extractEnum(object));
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersectionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		if (NONTYPE.superTests[extractOrdinal(object)])
		{
			return aListNodeType;
		}
		return bottom();
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersectionOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		if (NONTYPE.superTests[extractOrdinal(object)])
		{
			return aPhraseType;
		}
		return bottom();
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersectionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return primitiveTypeEnum.intersectionTypes[extractOrdinal(object)];
	}

	@Override @AvailMethod
	protected A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
	{
		return another.typeUnionOfPrimitiveTypeEnum(extractEnum(object));
	}

	@Override @AvailMethod
	protected A_Type o_TypeUnionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return primitiveTypeEnum.unionTypes[extractOrdinal(object)];
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write(
			object.slot(NAME).asNativeString().toLowerCase() + " type");
		writer.endObject();
	}

	/**
	 * Create a partially-initialized primitive type with the given name.  The
	 * type's parent will be set later, to facilitate arbitrary construction
	 * order.  Set these fields to {@linkplain NilDescriptor nil} to ensure
	 * pointer safety.
	 *
	 * @param typeNameString
	 *        The name to give the object being initialized.
	 * @return The partially initialized type.
	 */
	static AvailObject createMutablePrimitiveObjectNamed (
		final String typeNameString)
	{
		final A_String name = stringFrom(typeNameString);
		final AvailObject object = transientMutable.create();
		object.setSlot(NAME, name.makeShared());
		object.setSlot(PARENT, nil);
		object.setSlot(HASH, typeNameString.hashCode() * multiplier);
		return object;
	}

	/**
	 * Complete the given partially-initialized primitive type.  Set the type's
	 * parent and (shared) descriptor.  Don't force the parent to be shared yet
	 * if it isn't.
	 *
	 * @param parentType
	 *        The parent of this object, not necessarily shared.
	 */
	final void finishInitializingPrimitiveTypeWithParent (
		final AvailObject object,
		final A_Type parentType)
	{
		assert mutability == Mutability.SHARED;
		object.setSlot(PARENT, parentType);
		object.setDescriptor(this);
	}

	/** The {@link Types primitive type} represented by this descriptor. */
	final @Nullable Types primitiveType;

	/**
	 * Construct a new {@link Mutability#SHARED shared} {@link
	 * PrimitiveTypeDescriptor}.
	 *
	 * @param typeTag
	 *        The {@link TypeTag} to embed in the new descriptor.
	 * @param primitiveType
	 *        The {@link Types primitive type} represented by this descriptor.
	 * @param objectSlotsEnumClass
	 *        The Java {@link Class} which is a subclass of {@link
	 *        ObjectSlotsEnum} and defines this object's object slots layout, or
	 *        null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *        The Java {@link Class} which is a subclass of {@link
	 *        IntegerSlotsEnum} and defines this object's integer slots layout,
	 *        or null if there are no integer slots.
	 */
	protected PrimitiveTypeDescriptor (
		final TypeTag typeTag,
		final Types primitiveType,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(
			Mutability.SHARED,
			typeTag,
			objectSlotsEnumClass,
			integerSlotsEnumClass);
		this.primitiveType = primitiveType;
	}

	/**
	 * Construct a new {@link Mutability#SHARED shared} {@link
	 * PrimitiveTypeDescriptor}.
	 *
	 * @param typeTag
	 *            The {@link TypeTag} to embed in the new descriptor.
	 * @param primitiveType
	 *        The {@link Types primitive type} represented by this descriptor.
	 */
	PrimitiveTypeDescriptor (
		final TypeTag typeTag,
		final Types primitiveType)
	{
		this(typeTag, primitiveType, ObjectSlots.class, IntegerSlots.class);
	}

	/**
	 * Construct the sole mutable {@link PrimitiveTypeDescriptor}, used only
	 * during early instantiation of the primitive types.
	 */
	private PrimitiveTypeDescriptor ()
	{
		super(
			Mutability.MUTABLE,
			TypeTag.UNKNOWN_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
		primitiveType = null;
	}

	/**
	 * The sole mutable {@link PrimitiveTypeDescriptor}, only used during early
	 * instantiation.
	 */
	static final PrimitiveTypeDescriptor transientMutable =
		new PrimitiveTypeDescriptor();

	@Override
	public PrimitiveTypeDescriptor mutable ()
	{
		return transientMutable;
	}

	@Override
	public PrimitiveTypeDescriptor immutable ()
	{
		// There are no immutable versions.
		assert mutability == Mutability.SHARED;
		return this;
	}

	@Override
	public PrimitiveTypeDescriptor shared ()
	{
		assert mutability == Mutability.SHARED;
		return this;
	}
}
