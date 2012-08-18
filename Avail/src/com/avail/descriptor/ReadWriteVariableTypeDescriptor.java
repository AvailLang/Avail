/**
 * ReadWriteVariableTypeDescriptor.java
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

import static com.avail.descriptor.ReadWriteVariableTypeDescriptor.ObjectSlots.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * A {@code ReadWriteVariableTypeDescriptor read-write variable type} is
 * parametric on the types of values that may be {@linkplain
 * ObjectSlots#READ_TYPE read} from and {@linkplain ObjectSlots#WRITE_TYPE
 * written} to object instance {@linkplain VariableDescriptor variables}.
 * Reading a variable is a covariant capability, while writing a variable is
 * a contravariant capability.
 *
 * <p>When the read and write capabilities are equivalent, the static factory
 * methods normalize the representation to an invariant {@linkplain
 * VariableTypeDescriptor variable type descriptor}.</p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see VariableTypeDescriptor
 */
public final class ReadWriteVariableTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/** The type of values that can be read from my object instances. */
		READ_TYPE,

		/** The type of values that can be written to my object instances. */
		WRITE_TYPE
	}

	@Override @AvailMethod
	AvailObject o_ReadType (
		final AvailObject object)
	{
		return object.slot(READ_TYPE);
	}

	@Override @AvailMethod
	AvailObject o_WriteType (
		final AvailObject object)
	{
		return object.slot(WRITE_TYPE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append("↑<--(");
		object.slot(READ_TYPE).printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		aStream.append(")/(");
		object.slot(WRITE_TYPE).printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		aStream.append(")-->");
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return another.equalsVariableType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsVariableType (
		final AvailObject object,
		final AvailObject aType)
	{
		if (object.sameAddressAs(aType))
		{
			return true;
		}
		if (aType.readType().equals(object.slot(READ_TYPE))
			&& aType.writeType().equals(object.slot(WRITE_TYPE)))
		{
			aType.becomeIndirectionTo(object);
			return true;
		}
		return false;
	}

	@Override @AvailMethod
	int o_Hash (
		final AvailObject object)
	{
		return
			(object.slot(READ_TYPE).hash() ^ 0xF40149E
			+ object.slot(WRITE_TYPE).hash() ^ 0x5469E1A);
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final AvailObject object,
		final AvailObject aType)
	{
		return aType.isSupertypeOfVariableType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfVariableType (
		final AvailObject object,
		final AvailObject aVariableType)
	{
		// Variable types are covariant by read capability and contravariant by
		// write capability.
		return aVariableType.readType().isSubtypeOf(object.slot(READ_TYPE))
			&& object.slot(WRITE_TYPE).isSubtypeOf(aVariableType.writeType());
	}

	@Override @AvailMethod
	AvailObject o_TypeIntersection (
		final AvailObject object,
		final AvailObject another)
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
	AvailObject o_TypeIntersectionOfVariableType (
		final AvailObject object,
		final AvailObject aVariableType)
	{
		// The intersection of two variable types is variable type whose
		// read type is the type intersection of the two incoming read types and
		// whose write type is the type union of the two incoming write types.
		return VariableTypeDescriptor.fromReadAndWriteTypes(
			object.slot(READ_TYPE).typeIntersection(aVariableType.readType()),
			object.slot(WRITE_TYPE).typeUnion(aVariableType.writeType()));
	}

	@Override @AvailMethod
	AvailObject o_TypeUnion (
		final AvailObject object,
		final AvailObject another)
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
	AvailObject o_TypeUnionOfVariableType (
		final AvailObject object,
		final AvailObject aVariableType)
	{
		// The union of two variable types is a variable type whose
		// read type is the type union of the two incoming read types and whose
		// write type is the type intersection of the two incoming write types.
		return VariableTypeDescriptor.fromReadAndWriteTypes(
			object.slot(READ_TYPE).typeUnion(aVariableType.readType()),
			object.slot(WRITE_TYPE).typeIntersection(
				aVariableType.writeType()));
	}

	@Override
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		if (object.readType().equals(object.writeType()))
		{
			return SerializerOperation.SIMPLE_VARIABLE_TYPE;
		}
		return SerializerOperation.READ_WRITE_VARIABLE_TYPE;
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
	static AvailObject fromReadAndWriteTypes (
		final AvailObject readType,
		final AvailObject writeType)
	{
		if (readType.equals(writeType))
		{
			return VariableTypeDescriptor.wrapInnerType(readType);
		}
		final AvailObject result = mutable().create();
		result.setSlot(READ_TYPE, readType);
		result.setSlot(WRITE_TYPE, writeType);
		result.makeImmutable();
		return result;
	}

	/**
	 * Construct a new {@link ReadWriteVariableTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ReadWriteVariableTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ReadWriteVariableTypeDescriptor}.
	 */
	private static final ReadWriteVariableTypeDescriptor mutable =
		new ReadWriteVariableTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ReadWriteVariableTypeDescriptor}.
	 *
	 * @return The mutable {@link ReadWriteVariableTypeDescriptor}.
	 */
	public static ReadWriteVariableTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ReadWriteVariableTypeDescriptor}.
	 */
	private static final ReadWriteVariableTypeDescriptor immutable =
		new ReadWriteVariableTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ReadWriteVariableTypeDescriptor}.
	 *
	 * @return The immutable {@link ReadWriteVariableTypeDescriptor}.
	 */
	public static ReadWriteVariableTypeDescriptor immutable ()
	{
		return immutable;
	}
}
