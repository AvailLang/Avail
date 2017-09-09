/**
 * ReadWriteVariableTypeDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import com.avail.annotations.AvailMethod;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;

import static com.avail.descriptor.ReadWriteVariableTypeDescriptor
	.ObjectSlots.READ_TYPE;
import static com.avail.descriptor.ReadWriteVariableTypeDescriptor
	.ObjectSlots.WRITE_TYPE;
import static com.avail.descriptor.VariableTypeDescriptor.variableReadWriteType;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;

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
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/** The type of values that can be read from my object instances. */
		READ_TYPE,

		/** The type of values that can be written to my object instances. */
		WRITE_TYPE
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append("read ");
		object.slot(READ_TYPE).printOnAvoidingIndent(
			aStream,
			recursionMap,
			(indent + 1));
		aStream.append("/write ");
		object.slot(WRITE_TYPE).printOnAvoidingIndent(
			aStream,
			recursionMap,
			(indent + 1));
	}

	@Override @AvailMethod
	A_Type o_ReadType (final AvailObject object)
	{
		return object.slot(READ_TYPE);
	}

	@Override @AvailMethod
	A_Type o_WriteType (final AvailObject object)
	{
		return object.slot(WRITE_TYPE);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsVariableType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsVariableType (
		final AvailObject object,
		final A_Type aType)
	{
		if (object.sameAddressAs(aType))
		{
			return true;
		}
		if (aType.readType().equals(object.slot(READ_TYPE))
			&& aType.writeType().equals(object.slot(WRITE_TYPE)))
		{
			if (!isShared())
			{
				aType.makeImmutable();
				object.becomeIndirectionTo(aType);
			}
			else if (!aType.descriptor().isShared())
			{
				object.makeImmutable();
				aType.becomeIndirectionTo(object);
			}
			return true;
		}
		return false;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			(object.slot(READ_TYPE).hash() ^ 0x0F40149E
			+ object.slot(WRITE_TYPE).hash() ^ 0x05469E1A);
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfVariableType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		// Variable types are covariant by read capability and contravariant by
		// write capability.
		return aVariableType.readType().isSubtypeOf(object.slot(READ_TYPE))
			&& object.slot(WRITE_TYPE).isSubtypeOf(aVariableType.writeType());
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
		return another.typeIntersectionOfVariableType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		// The intersection of two variable types is variable type whose
		// read type is the type intersection of the two incoming read types and
		// whose write type is the type union of the two incoming write types.
		return variableReadWriteType(
			object.slot(READ_TYPE).typeIntersection(aVariableType.readType()),
			object.slot(WRITE_TYPE).typeUnion(aVariableType.writeType()));
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
		return another.typeUnionOfVariableType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		// The union of two variable types is a variable type whose
		// read type is the type union of the two incoming read types and whose
		// write type is the type intersection of the two incoming write types.
		return variableReadWriteType(
			object.slot(READ_TYPE).typeUnion(aVariableType.readType()),
			object.slot(WRITE_TYPE).typeIntersection(
				aVariableType.writeType()));
	}

	@Override @AvailMethod
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		if (object.readType().equals(object.writeType()))
		{
			return SerializerOperation.SIMPLE_VARIABLE_TYPE;
		}
		return SerializerOperation.READ_WRITE_VARIABLE_TYPE;
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Make the object shared rather than immutable (since there isn't
			// actually an immutable descriptor).
			return object.makeShared();
		}
		return object;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable type");
		writer.write("write type");
		object.slot(WRITE_TYPE).writeTo(writer);
		writer.write("read type");
		object.slot(READ_TYPE).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable type");
		writer.write("write type");
		object.slot(WRITE_TYPE).writeSummaryTo(writer);
		writer.write("read type");
		object.slot(READ_TYPE).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Construct a new {@link ReadWriteVariableTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ReadWriteVariableTypeDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.VARIABLE_TYPE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link ReadWriteVariableTypeDescriptor}. */
	private static final ReadWriteVariableTypeDescriptor mutable =
		new ReadWriteVariableTypeDescriptor(Mutability.MUTABLE);

	@Override
	ReadWriteVariableTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link ReadWriteVariableTypeDescriptor}. */
	private static final ReadWriteVariableTypeDescriptor shared =
		new ReadWriteVariableTypeDescriptor(Mutability.SHARED);

	@Override
	ReadWriteVariableTypeDescriptor immutable ()
	{
		// There isn't an immutable variant.
		return shared;
	}

	@Override
	ReadWriteVariableTypeDescriptor shared ()
	{
		return shared;
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
	static A_Type fromReadAndWriteTypes (
		final A_Type readType,
		final A_Type writeType)
	{
		if (readType.equals(writeType))
		{
			return variableTypeFor(readType);
		}
		final AvailObject result = mutable.create();
		result.setSlot(READ_TYPE, readType);
		result.setSlot(WRITE_TYPE, writeType);
		return result;
	}
}
