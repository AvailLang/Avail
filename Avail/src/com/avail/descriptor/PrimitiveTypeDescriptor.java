/**
 * PrimitiveTypeDescriptor.java
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
import java.util.List;
import com.avail.annotations.*;

/**
 * The primitive types of Avail are different from the notion of primitive types
 * in other object-oriented languages.  Traditionally, a compiler or virtual
 * machine encodes representation knowledge about and makes other special
 * provisions about its primitive types.  Since <em>all</em> types are in a
 * sense provided by the Avail system, it has no special primitive types that
 * fill that role – they're <em>all</em> special.
 *
 * <p>
 * Instead, the term "primitive type" in Avail refers to the top section of the
 * type lattice which partitions the rest of the lattice into broad categories
 * of essential disjoint subgraphs.  This includes the ultimate type {@linkplain
 * TypeDescriptor.Types#TOP top (⊤)}, the penultimate type {@linkplain
 * TypeDescriptor.Types#ANY any}, and various specialties such as {@linkplain
 * TypeDescriptor.Types#ATOM atom} and {@linkplain TypeDescriptor.Types#NUMBER
 * number}.  Type hierarchies that have a natural root don't bother with a
 * primitive type to delimit the hierarchy, using the natural root itself.  For
 * example, the tuple type whose instances include all tuples is a natural root
 * of the tuple types.
 * </p>
 *
 * @see TypeDescriptor.Types all primitive types
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class PrimitiveTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The hash of this primitive type, computed at construction time.
		 */
		HASH
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain StringDescriptor name} of this primitive type.
		 */
		NAME,

		/**
		 * The parent type of this primitive type.
		 */
		PARENT,

		/**
		 * The type (i.e., a metatype) of this primitive type.
		 */
		MY_TYPE
	}

	@Override @AvailMethod
	void o_Hash (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(IntegerSlots.HASH, value);
	}

	@Override @AvailMethod
	void o_MyType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.MY_TYPE, value);
	}

	@Override @AvailMethod
	void o_Name (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.NAME, value);
	}

	@Override @AvailMethod
	void o_Parent (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.PARENT, value);
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.HASH);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Name (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.NAME);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Parent (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.PARENT);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append(object.name().asNativeString());
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsPrimitiveType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		//  Primitive types compare by identity.

		return object.sameAddressAs(aType);
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfPrimitiveType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		//  This primitive type is a supertype of aFunctionType if and only if this
		//  primitive type is a supertype of ANY.

		return ANY.o().isSubtypeOf(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType)
	{
		// A primitive type is a supertype of a variable type if it is a
		// supertype of ANY.
		return ANY.o().isSubtypeOf(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		// A primitive type is a supertype of a continuation type if it is a
		// supertype of ANY.
		return ANY.o().isSubtypeOf(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		// A primitive type is a supertype of a compiled code type if it is a
		// supertype of ANY.
		return ANY.o().isSubtypeOf(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		// Parent of the top integer range type is number, so continue
		// searching there.
		return NUMBER.o().isSubtypeOf(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		//  This primitive type is a supertype of aMapType if and only if this
		//  primitive type is a supertype of ANY.

		return ANY.o().isSubtypeOf(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anEagerObjectType)
	{
		//  Check if I'm a supertype of the given eager object type.  Only all and its
		//  ancestors are supertypes of an object type.

		return ANY.o().isSubtypeOf(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		return ANY.o().isSubtypeOf(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return ANY.o().isSubtypeOf(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPrimitiveType)
	{
		// Check if object (a primitive type) is a supertype of aPrimitiveType
		// (also a primitive type).
		AvailObject type = aPrimitiveType;
		while (true)
		{
			if (object.equals(type))
			{
				return true;
			}
			type = type.parent();
			if (type.equalsNull())
			{
				return false;
			}
		}
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		//  This primitive type is a supertype of aSetType if and only if this
		//  primitive type is a supertype of ANY.

		return ANY.o().isSubtypeOf(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		//  This primitive type is a supertype of aTupleType if and only if this
		//  primitive type is a supertype of ANY.

		return ANY.o().isSubtypeOf(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfEnumerationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anEnumerationType)
	{
		if (anEnumerationType.innerKind().isSubtypeOf(TYPE.o()))
		{
			return META.o().isSubtypeOf(object);
		}
		return TYPE.o().isSubtypeOf(object);
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
		if (object.equals(META.o()))
		{
			return another.typeIntersectionOfMeta(object);
		}
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnion (
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
		if (another.isEnumeration())
		{
			// Note that at this point neither one can be bottom, since that
			// would always have been detected as a subtype of the other.
			assert !another.equals(BottomTypeDescriptor.bottom());
			return another.computeSuperkind().typeUnion(object);
		}
		return object.slot(ObjectSlots.PARENT).typeUnion(another);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.MY_TYPE);
	}

	/**
	 * Create a partially-initialized primitive type with the given name.  The
	 * type's parent and the type's myType will be set later, to allow circular
	 * constructions.  Set these fields to the {@linkplain NullDescriptor null
	 * object} to ensure pointer safety.
	 *
	 * @param typeNameString
	 *            The name to give the object being initialized.
	 * @return    The partially initialized type.
	 */
	AvailObject createPrimitiveObjectNamed (
		final String typeNameString)
	{
		final AvailObject name = StringDescriptor.from(typeNameString);
		final AvailObject object = create();
		object.setSlot(ObjectSlots.NAME, name);
		object.setSlot(ObjectSlots.PARENT, NullDescriptor.nullObject());
		object.setSlot(ObjectSlots.MY_TYPE, NullDescriptor.nullObject());
		object.setSlot(IntegerSlots.HASH, typeNameString.hashCode());
		return object;
	}

	/**
	 * Construct a new {@link PrimitiveTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected PrimitiveTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The descriptor instance that describes a mutable primitive type.
	 */
	final private static PrimitiveTypeDescriptor mutable = new PrimitiveTypeDescriptor(true);

	/**
	 * Answer the descriptor instance that describes a mutable primitive type.
	 *
	 * @return a PrimitiveTypeDescriptor for mutable objects.
	 */
	public static PrimitiveTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The descriptor instance that describes an immutable primitive type.
	 */
	final private static PrimitiveTypeDescriptor immutable = new PrimitiveTypeDescriptor(false);

	/**
	 * Answer the descriptor instance that describes an immutable primitive
	 * type.
	 *
	 * @return a PrimitiveTypeDescriptor for immutable objects.
	 */
	public static PrimitiveTypeDescriptor immutable ()
	{
		return immutable;
	}
}
