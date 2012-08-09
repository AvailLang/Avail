/**
 * VariableTypeDescriptor.java
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
import com.avail.serialization.SerializerOperation;

/**
 * A {@code VariableTypeDescriptor variable type} is the {@linkplain
 * TypeDescriptor type} of any {@linkplain VariableDescriptor variable} that can
 * only hold objects having the specified {@linkplain ObjectSlots#INNER_TYPE
 * inner type}. The read and write capabilities of the object instances are
 * equivalent, therefore the inner type is invariant.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd Smith &lt;anarakul@gmail.com&gt;
 * @see ReadWriteVariableTypeDescriptor
 */
public class VariableTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The type of values that my object instances can contain.
		 */
		INNER_TYPE
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ReadType (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.INNER_TYPE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_WriteType (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.INNER_TYPE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append("↑");
		object.slot(ObjectSlots.INNER_TYPE).printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsVariableType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		if (object.sameAddressAs(aType))
		{
			return true;
		}
		if (aType.readType().equals(
				object.slot(ObjectSlots.INNER_TYPE))
			&& aType.writeType().equals(
				object.slot(ObjectSlots.INNER_TYPE)))
		{
			aType.becomeIndirectionTo(object);
			return true;
		}
		return false;
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.INNER_TYPE).hash()
			* 17 ^ 0x613E420;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfVariableType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType)
	{
		final AvailObject innerType = object.slot(ObjectSlots.INNER_TYPE);

		// Variable types are covariant by read capability and contravariant by
		// write capability.
		return aVariableType.readType().isSubtypeOf(innerType)
			&& innerType.isSubtypeOf(aVariableType.writeType());
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
		return another.typeIntersectionOfVariableType(object);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType)
	{
		final AvailObject innerType = object.slot(ObjectSlots.INNER_TYPE);

		// The intersection of two variable types is a variable type whose
		// read type is the type intersection of the two incoming read types and
		// whose write type is the type union of the two incoming write types.
		return VariableTypeDescriptor.fromReadAndWriteTypes(
			innerType.typeIntersection(aVariableType.readType()),
			innerType.typeUnion(aVariableType.writeType()));
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
		return another.typeUnionOfVariableType(object);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType)
	{
		final AvailObject innerType = object.slot(ObjectSlots.INNER_TYPE);

		// The union of two variable types is a variable type whose
		// read type is the type union of the two incoming read types and whose
		// write type is the type intersection of the two incoming write types.
		return VariableTypeDescriptor.fromReadAndWriteTypes(
			innerType.typeUnion(aVariableType.readType()),
			innerType.typeIntersection(aVariableType.writeType()));
	}

	@Override
	@NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
	{
		return SerializerOperation.SIMPLE_VARIABLE_TYPE;
	}

	/**
	 * Create a {@linkplain VariableTypeDescriptor variable type} based on
	 * the given content {@linkplain TypeDescriptor type}.
	 *
	 * @param innerType
	 *            The content type on which to base the variable type.
	 * @return
	 *            The new variable type.
	 */
	public static @NotNull AvailObject wrapInnerType (
		final @NotNull AvailObject innerType)
	{
		final AvailObject result = mutable().create();
		result.setSlot(
			ObjectSlots.INNER_TYPE,
			innerType.makeImmutable());
		result.makeImmutable();
		return result;
	}

	/**
	 * Create a {@linkplain VariableTypeDescriptor variable type} based on the
	 * given read and write {@linkplain TypeDescriptor types}.
	 *
	 * @param readType
	 *        The read type.
	 * @param writeType
	 *        The write type.
	 * @return The new variable type.
	 */
	public static @NotNull AvailObject fromReadAndWriteTypes (
		final @NotNull AvailObject readType,
		final @NotNull AvailObject writeType)
	{
		if (readType.equals(writeType))
		{
			return wrapInnerType(readType);
		}
		return ReadWriteVariableTypeDescriptor.fromReadAndWriteTypes(
			readType, writeType);
	}

	/**
	 * Construct a new {@link VariableTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected VariableTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link VariableTypeDescriptor}.
	 */
	private static final @NotNull VariableTypeDescriptor mutable =
		new VariableTypeDescriptor(true);

	/**
	 * Answer the mutable {@link VariableTypeDescriptor}.
	 *
	 * @return The mutable {@link VariableTypeDescriptor}.
	 */
	public static @NotNull VariableTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link VariableTypeDescriptor}.
	 */
	private static final @NotNull VariableTypeDescriptor immutable =
		new VariableTypeDescriptor(false);

	/**
	 * Answer the immutable {@link VariableTypeDescriptor}.
	 *
	 * @return The immutable {@link VariableTypeDescriptor}.
	 */
	public static @NotNull VariableTypeDescriptor immutable ()
	{
		return immutable;
	}

	/**
	 * The most general {@linkplain ReadWriteVariableTypeDescriptor variable
	 * type}.
	 */
	private static AvailObject mostGeneralType;

	/**
	 * Answer the most general {@linkplain ReadWriteVariableTypeDescriptor
	 * variable type}.
	 *
	 * @return The most general {@linkplain ReadWriteVariableTypeDescriptor
	 *         variable type}.
	 */
	public static @NotNull AvailObject mostGeneralType ()
	{
		return mostGeneralType;
	}

	/**
	 * The (instance) type of the most general {@linkplain
	 * ReadWriteVariableTypeDescriptor variable} metatype.
	 */
	private static AvailObject meta;

	/**
	 * Answer the (instance) type of the most general {@linkplain
	 * ReadWriteVariableTypeDescriptor variable} metatype.
	 *
	 * @return
	 *         The instance type containing the most general {@linkplain
	 *         ReadWriteVariableTypeDescriptor variable} metatype.
	 */
	public static @NotNull AvailObject meta ()
	{
		return meta;
	}

	/**
	 * Forget any registered objects.
	 */
	public static void clearWellKnownObjects ()
	{
		mostGeneralType = null;
		meta = null;
	}

	/**
	 * Register objects.
	 */
	public static void createWellKnownObjects ()
	{
		mostGeneralType = fromReadAndWriteTypes(
			TOP.o(), BottomTypeDescriptor.bottom());
		mostGeneralType.makeImmutable();
		meta = InstanceMetaDescriptor.on(mostGeneralType);
		meta.makeImmutable();
	}
}
