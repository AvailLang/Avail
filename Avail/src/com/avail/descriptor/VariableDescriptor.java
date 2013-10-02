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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.exceptions.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.serialization.SerializerOperation;

/**
 * My {@linkplain AvailObject object instances} are variables which can hold
 * any object that agrees with my {@linkplain #forContentType(A_Type) inner type}.
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
	 * A {@code VariableAccessReactor} records a one-shot {@linkplain
	 * FunctionDescriptor function}. It is cleared upon read.
	 */
	public static class VariableAccessReactor
	{
		/** The {@linkplain FunctionDescriptor reactor function}. */
		private final AtomicReference<A_Function> function =
			new AtomicReference<A_Function>(NilDescriptor.nil());

		/**
		 * Atomically get and clear {@linkplain FunctionDescriptor reactor
		 * function}.
		 *
		 * @return The reactor function, or {@linkplain NilDescriptor#nil() nil}
		 *         if the reactor function has already been requested (and the
		 *         reactor is therefore invalid).
		 */
		public A_Function getAndClearFunction ()
		{
			return function.getAndSet(NilDescriptor.nil());
		}

		/**
		 * Is the {@linkplain VariableAccessReactor reactor} invalid?
		 *
		 * @return {@code true} if the reactor is invalid, {@code false}
		 *         otherwise.
		 */
		boolean isInvalid ()
		{
			return function.get().equalsNil();
		}

		/**
		 * Construct a new {@link VariableAccessReactor}.
		 *
		 * @param function
		 *        The reactor {@linkplain AvailObject function}.
		 */
		public VariableAccessReactor (final A_Function function)
		{
			this.function.set(function);
		}
	}

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
		KIND,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} that wraps a {@linkplain
		 * Map map} from arbitrary {@linkplain AvailObject Avail values} to
		 * {@linkplain VariableAccessReactor writer reactors} that respond to
		 * writes of the {@linkplain VariableDescriptor variable}.
		 */
		WRITE_REACTORS
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == VALUE
			|| e == HASH_OR_ZERO
			|| e == WRITE_REACTORS;
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
		final Interpreter interpreter;
		try
		{
			interpreter = Interpreter.current();
			if (interpreter.traceVariableReadsBeforeWrites())
			{
				final A_Fiber fiber = interpreter.fiber();
				fiber.recordVariableAccess(object, true);
			}
		}
		catch (final ClassCastException e)
		{
			// No implementation required.
		}
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
		final Interpreter interpreter;
		try
		{
			interpreter = Interpreter.current();
			if (interpreter.traceVariableWrites())
			{
				final A_Fiber fiber = interpreter.fiber();
				fiber.recordVariableAccess(object, false);
			}
		}
		catch (final ClassCastException e)
		{
			// No implementation required.
		}
		final A_BasicObject outerKind = object.slot(KIND);
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
		final Interpreter interpreter;
		try
		{
			interpreter = Interpreter.current();
			if (interpreter.traceVariableWrites())
			{
				final A_Fiber fiber = interpreter.fiber();
				fiber.recordVariableAccess(object, false);
			}
		}
		catch (final ClassCastException e)
		{
			// No implementation required.
		}
		object.setSlot(VALUE, newValue);
	}

	@Override @AvailMethod
	AvailObject o_GetAndSetValue (
		final AvailObject object,
		final AvailObject newValue)
	{
		final Interpreter interpreter;
		try
		{
			interpreter = Interpreter.current();
			if (interpreter.traceVariableWrites())
			{
				final A_Fiber fiber = interpreter.fiber();
				fiber.recordVariableAccess(object, true);
			}
		}
		catch (final ClassCastException e)
		{
			// No implementation required.
		}
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
		final Interpreter interpreter;
		try
		{
			interpreter = Interpreter.current();
			if (interpreter.traceVariableWrites())
			{
				final A_Fiber fiber = interpreter.fiber();
				fiber.recordVariableAccess(object, true);
			}
		}
		catch (final ClassCastException e)
		{
			// No implementation required.
		}
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
		final Interpreter interpreter;
		try
		{
			interpreter = Interpreter.current();
			if (interpreter.traceVariableWrites())
			{
				final A_Fiber fiber = interpreter.fiber();
				fiber.recordVariableAccess(object, true);
			}
		}
		catch (final ClassCastException e)
		{
			// No implementation required.
		}
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
		final Interpreter interpreter;
		try
		{
			interpreter = Interpreter.current();
			if (interpreter.traceVariableWrites())
			{
				final A_Fiber fiber = interpreter.fiber();
				fiber.recordVariableAccess(object, false);
			}
		}
		catch (final ClassCastException e)
		{
			// No implementation required.
		}
		object.setSlot(VALUE, NilDescriptor.nil());
	}

	@Override @AvailMethod
	void o_AddDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		assert !isShared();
		final A_Variable sharedVariable = object.makeShared();
		sharedVariable.addDependentChunk(chunk);
		object.becomeIndirectionTo(sharedVariable);
	}

	@Override @AvailMethod
	void o_RemoveDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		assert !isShared();
		// This representation doesn't support dependent chunks, so the
		// specified index can't be a match. This is certainly an error.
		assert false : "Chunk removed but not added!";
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Variable o_AddWriteReactor (
		final AvailObject object,
		final A_Atom key,
		final VariableAccessReactor reactor)
	{
		AvailObject rawPojo = object.slot(WRITE_REACTORS);
		if (rawPojo.equalsNil())
		{
			rawPojo = RawPojoDescriptor.identityWrap(
				new HashMap<A_Atom, VariableAccessReactor>());
			object.setMutableSlot(WRITE_REACTORS, rawPojo);
		}
		@SuppressWarnings("unchecked")
		final Map<A_Atom, VariableAccessReactor> writeReactors =
			(Map<A_Atom, VariableAccessReactor>) rawPojo.javaObject();
		// Discard invalidated reactors.
		final Iterator<Map.Entry<A_Atom, VariableAccessReactor>> iterator =
			writeReactors.entrySet().iterator();
		while (iterator.hasNext())
		{
			final Map.Entry<A_Atom, VariableAccessReactor> entry =
				iterator.next();
			if (entry.getValue().isInvalid())
			{
				iterator.remove();
			}
		}
		writeReactors.put(key, reactor);
		return object;
	}

	@Override @AvailMethod
	void o_RemoveWriteReactor (final AvailObject object, final A_Atom key)
		throws AvailException
	{
		final AvailObject rawPojo = object.slot(WRITE_REACTORS);
		if (rawPojo.equalsNil())
		{
			throw new AvailException(AvailErrorCode.E_KEY_NOT_FOUND);
		}
		@SuppressWarnings("unchecked")
		final Map<A_Atom, VariableAccessReactor> writeReactors =
			(Map<A_Atom, VariableAccessReactor>) rawPojo.javaObject();
		// Discard invalidated reactors.
		final Iterator<Map.Entry<A_Atom, VariableAccessReactor>> iterator =
			writeReactors.entrySet().iterator();
		while (iterator.hasNext())
		{
			final Map.Entry<A_Atom, VariableAccessReactor> entry =
				iterator.next();
			if (entry.getValue().isInvalid())
			{
				iterator.remove();
			}
		}
		if (writeReactors.remove(key) == null)
		{
			throw new AvailException(AvailErrorCode.E_KEY_NOT_FOUND);
		}
		if (writeReactors.isEmpty())
		{
			object.setSlot(WRITE_REACTORS, NilDescriptor.nil());
		}
	}

	@Override @AvailMethod
	A_Set o_ValidWriteReactorFunctions (final AvailObject object)
	{
		final AvailObject rawPojo = object.slot(WRITE_REACTORS);
		if (!rawPojo.equalsNil())
		{
			@SuppressWarnings("unchecked")
			final Map<A_Atom, VariableAccessReactor> writeReactors =
				(Map<A_Atom, VariableAccessReactor>) rawPojo.javaObject();
			A_Set set = SetDescriptor.empty();
			for (final Map.Entry<A_Atom, VariableAccessReactor> entry :
				writeReactors.entrySet())
			{
				final A_Function function = entry.getValue().getAndClearFunction();
				if (!function.equalsNil())
				{
					set = set.setWithElementCanDestroy(function, true);
				}
			}
			writeReactors.clear();
			return set;
		}
		return SetDescriptor.empty();
	}

	@Override @AvailMethod
	final A_Type o_Kind (final AvailObject object)
	{
		return object.slot(KIND);
	}

	@Override @AvailMethod
	final boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another)
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

	@Override
	AvailObject o_MakeShared (final AvailObject object)
	{
		assert !isShared();
		final A_Type kind = object.slot(KIND).makeShared();
		final AvailObject value = object.slot(VALUE).makeShared();
		// The value might refer recursively to the variable, so it is possible
		// that the variable has just become shared.
		if (!object.descriptor.isShared())
		{
			final AvailObject substitutionVariable =
				VariableSharedDescriptor.create(
					kind,
					object.slot(HASH_OR_ZERO),
					value);
			object.becomeIndirectionTo(substitutionVariable);
			object.makeShared();
			return substitutionVariable;
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
	 * @param contentType
	 *        The type of objects the new variable can contain.
	 * @return A new variable able to hold the specified type of objects.
	 */
	public static AvailObject forContentType (final A_Type contentType)
	{
		return VariableDescriptor.forVariableType(
			VariableTypeDescriptor.wrapInnerType(contentType));
	}

	/**
	 * Create a {@linkplain VariableDescriptor variable} of the specified
	 * {@linkplain VariableTypeDescriptor variable type}.  The new variable
	 * initially holds no value.
	 *
	 * @param variableType
	 *        The {@linkplain VariableTypeDescriptor variable type}.
	 * @return A new variable of the given type.
	 */
	public static AvailObject forVariableType (final A_Type variableType)
	{
		final AvailObject result = mutable.create();
		result.setSlot(KIND, variableType);
		result.setSlot(HASH_OR_ZERO, 0);
		result.setSlot(VALUE, NilDescriptor.nil());
		result.setSlot(WRITE_REACTORS, NilDescriptor.nil());
		return result;
	}

	/**
	 * Construct a new {@link VariableDescriptor}.
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
	protected VariableDescriptor (
		final Mutability mutability,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	/**
	 * Construct a new {@link VariableDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private VariableDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
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
