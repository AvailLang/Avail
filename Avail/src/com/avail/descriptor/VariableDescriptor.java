/**
 * VariableDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.VariableDescriptor.IntegerSlots.*;
import static com.avail.descriptor.VariableDescriptor.ObjectSlots.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.exceptions.*;
import com.avail.serialization.SerializerOperation;

/**
 * My {@linkplain AvailObject object instances} are variables which can hold
 * any object that agrees with my {@linkplain #forInnerType(A_Type) inner type}.
 * A variable may also hold no value at all.  Any attempt to read the
 * {@linkplain #o_GetValue(AvailObject) current value} of a variable that holds
 * no value will fail immediately.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class VariableDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		@HideFieldInDebugger
		HASH_OR_ZERO
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain AvailObject contents} of the {@linkplain
		 * VariableDescriptor variable}.
		 */
		VALUE,

		/**
		 * The {@linkplain AvailObject kind} of the {@linkplain
		 * VariableDescriptor variable}.  Note that this is always a
		 * {@linkplain VariableTypeDescriptor variable type}.
		 */
		KIND
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == VALUE
			|| e == HASH_OR_ZERO;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		int hash = object.slot(HASH_OR_ZERO);
		if (hash == 0)
		{
			do
			{
				hash = AvailRuntime.nextHash();
			}
			while (hash == 0);
			object.setSlot(HASH_OR_ZERO, hash);
		}
		return hash;
	}

	@Override @AvailMethod
	AvailObject o_Value (final AvailObject object)
	{
		return object.slot(VALUE);
	}

	@Override @AvailMethod
	AvailObject o_GetValue (final AvailObject object)
	{
		// Answer the current value of the variable. Fail if no value is
		// currently assigned.
		final AvailObject value = object.slot(VALUE);
		if (value.equalsNil())
		{
			throw new VariableGetException(
				AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE);
		}
		if (mutability == Mutability.IMMUTABLE)
		{
			value.makeImmutable();
		}
		return value;
	}

	@Override @AvailMethod
	void o_SetValue (final AvailObject object, final A_BasicObject newValue)
	{
		final A_BasicObject outerKind = object.slot(ObjectSlots.KIND);
		if (!newValue.isInstanceOf(outerKind.writeType()))
		{
			throw new VariableSetException(
				AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE);
		}
		object.setSlot(VALUE, newValue);
	}

	@Override @AvailMethod
	void o_SetValueNoCheck (
		final AvailObject object,
		final AvailObject newValue)
	{
		object.setSlot(VALUE, newValue);
	}

	@Override @AvailMethod
	AvailObject o_GetAndSetValue (
		final AvailObject object,
		final AvailObject newValue)
	{
		final AvailObject outerKind = object.slot(KIND);
		if (!newValue.isInstanceOf(outerKind.writeType()))
		{
			throw new VariableSetException(
				AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE);
		}
		// The variable is not visible to multiple fibers, and cannot become
		// visible to any other fiber except by an act of the current fiber,
		// therefore do not worry about atomicity.
		final AvailObject value = object.slot(VALUE);
		if (value.equalsNil())
		{
			throw new VariableGetException(
				AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE);
		}
		object.setSlot(VALUE, newValue);
		if (mutability == Mutability.MUTABLE)
		{
			value.makeImmutable();
		}
		return value;
	}

	@Override @AvailMethod
	boolean o_CompareAndSwapValues (
		final AvailObject object,
		final AvailObject reference,
		final AvailObject newValue)
	{
		final AvailObject outerKind = object.slot(KIND);
		if (!newValue.isInstanceOf(outerKind.writeType()))
		{
			throw new VariableSetException(
				AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE);
		}
		// The variable is not visible to multiple fibers, and cannot become
		// visible to any other fiber except by an act of the current fiber,
		// therefore do not worry about atomicity.
		final AvailObject value = object.slot(VALUE);
		if (value.equalsNil())
		{
			throw new VariableGetException(
				AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE);
		}
		final boolean swap = value.equals(reference);
		if (swap)
		{
			object.setSlot(VALUE, newValue);
		}
		if (mutability == Mutability.MUTABLE)
		{
			value.makeImmutable();
		}
		return swap;
	}

	@Override @AvailMethod
	A_Number o_FetchAndAddValue (
		final AvailObject object,
		final A_Number addend)
	{
		final A_Type outerKind = object.slot(KIND);
		assert outerKind.readType().isSubtypeOf(
			IntegerRangeTypeDescriptor.extendedIntegers());
		// The variable is not visible to multiple fibers, and cannot become
		// visible to any other fiber except by an act of the current fiber,
		// therefore do not worry about atomicity.
		final A_Number value = object.slot(VALUE);
		if (value.equalsNil())
		{
			throw new VariableGetException(
				AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE);
		}
		final A_Number newValue = value.plusCanDestroy(addend, false);
		if (!newValue.isInstanceOf(outerKind.writeType()))
		{
			throw new VariableSetException(
				AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE);
		}
		object.setSlot(VALUE, newValue);
		if (mutability == Mutability.MUTABLE)
		{
			value.makeImmutable();
		}
		return value;
	}

	@Override @AvailMethod
	void o_ClearValue (final AvailObject object)
	{
		object.setSlot(VALUE, NilDescriptor.nil());
	}

	@Override @AvailMethod
	final A_Type o_Kind (final AvailObject object)
	{
		return object.slot(KIND);
	}

	@Override @AvailMethod
	final boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsVariable(object);
	}

	@Override @AvailMethod
	final boolean o_EqualsVariable (
		final AvailObject object,
		final AvailObject aVariable)
	{
		return object.sameAddressAs(aVariable);
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		// If I am being frozen (a variable), I don't need to freeze my current
		// value. I do, on the other hand, have to freeze my kind object.
		if (isMutable())
		{
			object.descriptor = immutable;
			object.slot(KIND).makeImmutable();
		}
		return object;
	}

	@Override @AvailMethod
	final SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.VARIABLE;
	}

	/**
	 * Create a {@linkplain VariableDescriptor variable} which can only
	 * contain values of the specified type.  The new variable initially holds
	 * no value.
	 *
	 * @param innerType
	 *        The type of objects the new variable can contain.
	 * @return A new variable able to hold the specified type of objects.
	 */
	public static AvailObject forInnerType (final A_Type innerType)
	{
		return VariableDescriptor.forOuterType(
			VariableTypeDescriptor.wrapInnerType(innerType));
	}

	/**
	 * Create a {@linkplain VariableDescriptor variable} of the specified
	 * {@linkplain VariableTypeDescriptor variable type}.  The new variable
	 * initially holds no value.
	 *
	 * @param outerType
	 *            The variable type to instantiate.
	 * @return
	 *            A new variable of the given type.
	 */
	public static AvailObject forOuterType (
		final A_Type outerType)
	{
		final AvailObject result = mutable.create();
		result.setSlot(KIND, outerType);
		result.setSlot(HASH_OR_ZERO, 0);
		result.setSlot(VALUE, NilDescriptor.nil());
		return result;
	}

	/**
	 * Construct a new {@link VariableDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	protected VariableDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link VariableDescriptor}. */
	private static final VariableDescriptor mutable =
		new VariableDescriptor(Mutability.MUTABLE);

	@Override
	final VariableDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link VariableDescriptor}. */
	private static final VariableDescriptor immutable =
		new VariableDescriptor(Mutability.IMMUTABLE);

	@Override
	final VariableDescriptor immutable ()
	{
		return immutable;
	}

	@Override
	final VariableDescriptor shared ()
	{
		return VariableSharedDescriptor.shared;
	}
}
