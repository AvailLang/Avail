/**
 * PrimitiveTypeDescriptor.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.descriptor.PrimitiveTypeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.PrimitiveTypeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

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
 * TypeDescriptor.Types#TOP top (⊤)}, the penultimate type {@linkplain
 * TypeDescriptor.Types#ANY any}, and various specialties such as {@linkplain
 * TypeDescriptor.Types#ATOM atom} and {@linkplain TypeDescriptor.Types#NUMBER
 * number}. Type hierarchies that have a natural root don't bother with a
 * primitive type to delimit the hierarchy, using the natural root itself. For
 * example, the tuple type whose instances include all tuples is a natural root
 * of the tuple types.
 * </p>
 *
 * @see TypeDescriptor.Types all primitive types
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class PrimitiveTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The hash of this primitive type, computed at construction time.
		 */
		HASH,

		/**
		 * This primitive type's (mutually) unique ordinal number.
		 */
		PRIMITIVE_TYPE_ORDINAL
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
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
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		aStream.append(object.slot(NAME).asNativeString());
	}

	@Override @AvailMethod
	AvailObject o_Name (final AvailObject object)
	{
		return object.slot(NAME);
	}

	@Override @AvailMethod
	A_BasicObject o_Parent (final AvailObject object)
	{
		return object.slot(PARENT);
	}

	@Override @AvailMethod
	void o_Parent (final AvailObject object, final AvailObject value)
	{
		object.setSlot(PARENT, value);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(HASH);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsPrimitiveType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsPrimitiveType (
		final AvailObject object,
		final A_Type aPrimitiveType)
	{
		// Primitive types compare by identity.
		return object.sameAddressAs(aPrimitiveType);
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		// Check if object (a type) is a subtype of aType (should also be a
		// type).
		return aType.isSupertypeOfPrimitiveTypeEnum(
			Types.values()[object.slot(PRIMITIVE_TYPE_ORDINAL)]);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		// This primitive type is a supertype of aFiberType if and only if
		// this primitive type is a supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		// This primitive type is a supertype of aFunctionType if and only if
		// this primitive type is a supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		// A primitive type is a supertype of a variable type if it is a
		// supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		// A primitive type is a supertype of a continuation type if it is a
		// supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		// A primitive type is a supertype of a compiled code type if it is a
		// supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		// Parent of the top integer range type is number, so continue
		// searching there.
		return object.isSupertypeOfPrimitiveTypeEnum(NUMBER);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		// This primitive type is a supertype of aLiteralTokenType if and only
		// if this primitive type is a supertype of TOKEN.
		return object.isSupertypeOfPrimitiveTypeEnum(TOKEN);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		// This primitive type is a supertype of aMapType if and only if this
		// primitive type is a supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final A_Type anObjectType)
	{
		// Check if I'm a supertype of the given eager object type. Only NONTYPE
		// and its ancestors are supertypes of an object type.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfParseNodeType (
		final AvailObject object,
		final AvailObject aParseNodeType)
	{
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override
	boolean o_IsSupertypeOfPojoBottomType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final A_BasicObject aPojoType)
	{
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		final boolean[] row =
			TypeDescriptor.Types.supertypeTable[primitiveTypeEnum.ordinal()];
		return row[object.slot(PRIMITIVE_TYPE_ORDINAL)];
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		// This primitive type is a supertype of aSetType if and only if this
		// primitive type is a supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		// This primitive type is a supertype of aTupleType if and only if this
		// primitive type is a supertype of NONTYPE.
		return object.isSupertypeOfPrimitiveTypeEnum(NONTYPE);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfEnumerationType (
		final AvailObject object,
		final A_BasicObject anEnumerationType)
	{
		return InstanceMetaDescriptor.topMeta().isSubtypeOf(object);
	}

	@Override @AvailMethod
	A_Type o_TypeIntersection (
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
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeUnion (
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
		if (another.isEnumeration())
		{
			// Note that at this point neither one can be bottom, since that
			// would always have been detected as a subtype of the other.
			assert !another.isBottom();
			return another.computeSuperkind().typeUnion(object);
		}
		return object.slot(PARENT).typeUnion(another);
	}

	@Override @AvailMethod
	@Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		for (final Types type : Types.values())
		{
			if (object.equals(type.o()))
			{
				switch (type)
				{
					case TOP:
						return Void.class;
					case ANY:
						return Object.class;
					case CHARACTER:
						return Character.TYPE;
					case DOUBLE:
						return Double.TYPE;
					case FLOAT:
						return Float.TYPE;
					case ABSTRACT_DEFINITION:
					case ATOM:
					case FORWARD_DEFINITION:
					case MACRO_DEFINITION:
					case MESSAGE_BUNDLE:
					case MESSAGE_BUNDLE_TREE:
					case METHOD:
					case METHOD_DEFINITION:
					case MODULE:
					case NONTYPE:
					case NUMBER:
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
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		// Any primitive type that can be serialized should occur in the special
		// objects list.
		throw unsupportedOperationException();
	}

	@Override
	final AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// There is no immutable descriptor; use the shared one.
			object.makeShared();
		}
		return object;
	}

	/**
	 * Create a partially-initialized primitive type with the given name.  The
	 * type's parent will be set later, to facilitate arbitrary construction
	 * order.  Set these fields to {@linkplain NilDescriptor nil} to ensure
	 * pointer safety.
	 *
	 * @param typeNameString
	 *        The name to give the object being initialized.
	 * @param ordinal
	 *        The unique ordinal number for this primitive type.
	 * @return The partially initialized type.
	 */
	AvailObject createPrimitiveObjectNamed (
		final String typeNameString,
		final int ordinal)
	{
		final A_String name = StringDescriptor.from(typeNameString);
		final AvailObject object = create();
		object.setSlot(NAME, name);
		object.setSlot(PARENT, NilDescriptor.nil());
		object.setSlot(HASH, typeNameString.hashCode());
		object.setSlot(PRIMITIVE_TYPE_ORDINAL, ordinal);
		return object;
	}

	/**
	 * Construct a new {@link PrimitiveTypeDescriptor}.
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
	protected PrimitiveTypeDescriptor (
		final Mutability mutability,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	/**
	 * Construct a new {@link PrimitiveTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private PrimitiveTypeDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link PrimitiveTypeDescriptor}. */
	final static PrimitiveTypeDescriptor mutable =
		new PrimitiveTypeDescriptor(Mutability.MUTABLE);

	@Override
	PrimitiveTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link PrimitiveTypeDescriptor}. */
	final private static PrimitiveTypeDescriptor shared =
		new PrimitiveTypeDescriptor(Mutability.SHARED);

	@Override
	PrimitiveTypeDescriptor immutable ()
	{
		// There is no immutable descriptor.
		return shared;
	}

	@Override
	PrimitiveTypeDescriptor shared ()
	{
		return shared;
	}
}
