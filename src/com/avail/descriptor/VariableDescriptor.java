/*
 * VariableDescriptor.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
import com.avail.annotations.HideFieldInDebugger;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.AvailException;
import com.avail.exceptions.VariableGetException;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;

import static com.avail.AvailRuntimeSupport.nextHash;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.extendedIntegers;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.VariableDescriptor.IntegerSlots.HASH_AND_MORE;
import static com.avail.descriptor.VariableDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.VariableDescriptor.ObjectSlots.*;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.exceptions.AvailErrorCode.*;

/**
 * My {@linkplain AvailObject object instances} are variables which can hold
 * any object that agrees with my {@linkplain #newVariableWithContentType(A_Type) inner type}.
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
			new AtomicReference<>(nil);

		/**
		 * Atomically get and clear {@linkplain FunctionDescriptor reactor
		 * function}.
		 *
		 * @return The reactor function, or {@linkplain NilDescriptor#nil nil}
		 *         if the reactor function has already been requested (and the
		 *         reactor is therefore invalid).
		 */
		public A_Function getAndClearFunction ()
		{
			return function.getAndSet(nil);
		}

		/**
		 * Answer whether the {@code VariableAccessReactor} is invalid.
		 *
		 * @return {@code true} if the reactor is invalid, {@code false}
		 *         otherwise.
		 */
		boolean isInvalid ()
		{
			return function.get().equalsNil();
		}

		/**
		 * Construct a new {@code VariableAccessReactor}.
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
		 * The low 32 bits are used for the {@link #HASH_OR_ZERO}, but the upper
		 * 32 can be used by subclasses.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * A slot to hold the cached hash value.  Zero if not yet computed.
		 */
		static final BitField HASH_OR_ZERO = bitField(HASH_AND_MORE, 0, 32);
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
		@HideFieldInDebugger
		WRITE_REACTORS
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == VALUE
			|| e == HASH_AND_MORE
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
				hash = nextHash();
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

	@SuppressWarnings("ThrowsRuntimeException")
	@Override @AvailMethod
	AvailObject o_GetValue (final AvailObject object)
	throws VariableGetException
	{
		try
		{
			final Interpreter interpreter = Interpreter.current();
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
			throw new VariableGetException(E_CANNOT_READ_UNASSIGNED_VARIABLE);
		}
		if (mutability == Mutability.IMMUTABLE)
		{
			value.makeImmutable();
		}
		return value;
	}

	@Override @AvailMethod
	boolean o_HasValue (final AvailObject object)
	{
		try
		{
			final Interpreter interpreter = Interpreter.current();
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
		final AvailObject value = object.slot(VALUE);
		return !value.equalsNil();
	}

	@Override @AvailMethod
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.LOCAL_VARIABLE;
	}

	/**
	 * Discard all {@linkplain VariableAccessReactor#isInvalid() invalid}
	 * {@linkplain VariableAccessReactor write reactors} from the specified
	 * {@linkplain Map map}.
	 *
	 * @param writeReactors
	 *        The map of write reactors.
	 */
	public static void discardInvalidWriteReactors (
		final Map<A_Atom, VariableAccessReactor> writeReactors)
	{
		writeReactors.values().removeIf(VariableAccessReactor::isInvalid);
	}

	/**
	 * If {@linkplain Interpreter#traceVariableWrites() variable write tracing}
	 * is enabled, then {@linkplain A_Fiber#recordVariableAccess(A_Variable,
	 * boolean) record the write}. If variable write tracing is disabled, but
	 * the variable has write reactors, then raise an {@linkplain
	 * VariableSetException exception} with {@link
	 * AvailErrorCode#E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED} as the error
	 * code.
	 *
	 * @param object
	 *        The variable.
	 * @throws VariableSetException
	 *         If variable write tracing is disabled, but the variable has
	 *         write reactors.
	 */
	@SuppressWarnings("ThrowsRuntimeException")
	private static void handleVariableWriteTracing (final AvailObject object)
	throws VariableSetException
	{
		try
		{
			final Interpreter interpreter = Interpreter.current();
			if (interpreter.traceVariableWrites())
			{
				final A_Fiber fiber = interpreter.fiber();
				fiber.recordVariableAccess(object, false);
			}
			else
			{
				final AvailObject rawPojo = object.slot(WRITE_REACTORS);
				if (!rawPojo.equalsNil())
				{
					final Map<A_Atom, VariableAccessReactor> writeReactors =
						rawPojo.javaObjectNotNull();
					discardInvalidWriteReactors(writeReactors);
					// If there are write reactors, but write tracing isn't
					// active, then raise an exception.
					if (!writeReactors.isEmpty())
					{
						throw new VariableSetException(
							E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED);
					}
				}
			}
		}
		catch (final ClassCastException e)
		{
			// No implementation required.
		}
	}

	@Override @AvailMethod
	void o_SetValue (final AvailObject object, final A_BasicObject newValue)
	throws VariableSetException
	{
		handleVariableWriteTracing(object);
		final A_Type outerKind = object.slot(KIND);
		if (!newValue.isInstanceOf(outerKind.writeType()))
		{
			throw new VariableSetException(
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE);
		}
		object.setSlot(VALUE, newValue);
	}

	@Override @AvailMethod
	void o_SetValueNoCheck (
			final AvailObject object,
			final A_BasicObject newValue)
		throws VariableSetException
	{
		assert !newValue.equalsNil();
		handleVariableWriteTracing(object);
		object.setSlot(VALUE, newValue);
	}

	@Override @AvailMethod
	AvailObject o_GetAndSetValue (
			final AvailObject object,
			final A_BasicObject newValue)
		throws VariableGetException, VariableSetException
	{
		handleVariableWriteTracing(object);
		final AvailObject outerKind = object.slot(KIND);
		if (!newValue.isInstanceOf(outerKind.writeType()))
		{
			throw new VariableSetException(
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE);
		}
		// The variable is not visible to multiple fibers, and cannot become
		// visible to any other fiber except by an act of the current fiber,
		// therefore do not worry about atomicity.
		final AvailObject value = object.slot(VALUE);
		if (value.equalsNil())
		{
			throw new VariableGetException(
				E_CANNOT_READ_UNASSIGNED_VARIABLE);
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
			final A_BasicObject reference,
			final A_BasicObject newValue)
		throws VariableGetException, VariableSetException
	{
		handleVariableWriteTracing(object);
		final AvailObject outerKind = object.slot(KIND);
		if (!newValue.isInstanceOf(outerKind.writeType()))
		{
			throw new VariableSetException(
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE);
		}
		// The variable is not visible to multiple fibers, and cannot become
		// visible to any other fiber except by an act of the current fiber,
		// therefore do not worry about atomicity.
		final AvailObject value = object.slot(VALUE);
		if (value.equalsNil())
		{
			throw new VariableGetException(E_CANNOT_READ_UNASSIGNED_VARIABLE);
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
		throws VariableGetException, VariableSetException
	{
		handleVariableWriteTracing(object);
		final A_Type outerKind = object.slot(KIND);
		assert outerKind.readType().isSubtypeOf(extendedIntegers());
		// The variable is not visible to multiple fibers, and cannot become
		// visible to any other fiber except by an act of the current fiber,
		// therefore do not worry about atomicity.
		final A_Number value = object.slot(VALUE);
		if (value.equalsNil())
		{
			throw new VariableGetException(E_CANNOT_READ_UNASSIGNED_VARIABLE);
		}
		final A_Number newValue = value.plusCanDestroy(addend, false);
		if (!newValue.isInstanceOf(outerKind.writeType()))
		{
			throw new VariableSetException(
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE);
		}
		object.setSlot(VALUE, newValue);
		if (mutability == Mutability.MUTABLE)
		{
			value.makeImmutable();
		}
		return value;
	}

	@Override @AvailMethod
	void o_AtomicAddToMap (
		final AvailObject object,
		final A_BasicObject key,
		final A_BasicObject value)
	throws VariableGetException, VariableSetException
	{
		handleVariableWriteTracing(object);
		final A_Type outerKind = object.slot(KIND);
		final A_Type readType = outerKind.readType();
		assert readType.isMapType();
		final A_Map oldMap = object.slot(VALUE);
		if (oldMap.equalsNil())
		{
			throw new VariableGetException(E_CANNOT_READ_UNASSIGNED_VARIABLE);
		}
		assert oldMap.isMap();
		if (readType.isMapType())
		{
			// Make sure the new map will satisfy the writeType.  We do these
			// checks before modifying the map, since the new key/value pair can
			// be added destructively.
			if (!key.isInstanceOf(readType.keyType())
				|| !value.isInstanceOf(readType.valueType()))
			{
				throw new VariableSetException(
					E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE);
			}
			if (readType.sizeRange().upperBound().equalsInt(oldMap.mapSize()))
			{
				// Map is as full as the type will allow.  Ensure we're
				// replacing a key, not adding one.
				if (!oldMap.hasKey(key))
				{
					throw new VariableSetException(
						E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE);
				}
			}
		}
		final A_Map newMap = oldMap.mapAtPuttingCanDestroy(key, value, true);
		// We already checked the key, value, and resulting size, so we can skip
		// a separate type check.
		object.setSlot(VALUE, newMap.makeShared());
	}

	@Override @AvailMethod
	boolean o_VariableMapHasKey (
		final AvailObject object,
		final A_BasicObject key)
	throws VariableGetException
	{
		handleVariableWriteTracing(object);
		final A_Type outerKind = object.slot(KIND);
		final A_Type readType = outerKind.readType();
		assert readType.isMapType();
		final A_Map oldMap = object.slot(VALUE);
		if (oldMap.equalsNil())
		{
			throw new VariableGetException(E_CANNOT_READ_UNASSIGNED_VARIABLE);
		}
		return oldMap.hasKey(key);
	}

	@Override @AvailMethod
	void o_ClearValue (final AvailObject object)
	{
		handleVariableWriteTracing(object);
		object.setSlot(VALUE, nil);
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
	void o_AddWriteReactor (
		final AvailObject object,
		final A_Atom key,
		final VariableAccessReactor reactor)
	{
		AvailObject rawPojo = object.slot(WRITE_REACTORS);
		if (rawPojo.equalsNil())
		{
			rawPojo = identityPojo(
				new HashMap<A_Atom, VariableAccessReactor>());
			object.setMutableSlot(WRITE_REACTORS, rawPojo);
		}
		final Map<A_Atom, VariableAccessReactor> writeReactors =
			rawPojo.javaObjectNotNull();
		discardInvalidWriteReactors(writeReactors);
		writeReactors.put(key, reactor);
	}

	@Override @AvailMethod
	void o_RemoveWriteReactor (final AvailObject object, final A_Atom key)
		throws AvailException
	{
		final AvailObject rawPojo = object.slot(WRITE_REACTORS);
		if (rawPojo.equalsNil())
		{
			throw new AvailException(E_KEY_NOT_FOUND);
		}
		final Map<A_Atom, VariableAccessReactor> writeReactors =
			rawPojo.javaObjectNotNull();
		discardInvalidWriteReactors(writeReactors);
		if (writeReactors.remove(key) == null)
		{
			throw new AvailException(E_KEY_NOT_FOUND);
		}
	}

	@Override @AvailMethod
	A_Set o_ValidWriteReactorFunctions (final AvailObject object)
	{
		final AvailObject rawPojo = object.slot(WRITE_REACTORS);
		if (!rawPojo.equalsNil())
		{
			final Map<A_Atom, VariableAccessReactor> writeReactors =
				rawPojo.javaObjectNotNull();
			A_Set set = emptySet();
			for (final Entry<A_Atom, VariableAccessReactor> entry :
				writeReactors.entrySet())
			{
				final A_Function function =
					entry.getValue().getAndClearFunction();
				if (!function.equalsNil())
				{
					set = set.setWithElementCanDestroy(function, true);
				}
			}
			writeReactors.clear();
			return set;
		}
		return emptySet();
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

		return VariableSharedDescriptor.createSharedFrom(
			object.slot(KIND), object.hash(), object.slot(VALUE), object);
	}

	@Override
	boolean o_IsInitializedWriteOnceVariable (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsGlobal(
		final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_ValueWasStablyComputed (
		final AvailObject object)
	{
		// The override in VariableSharedWriteOnceDescriptor answer a stored
		// flag set during initialization, but other variables always answer
		// false.
		return false;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable");
		writer.write("variable type");
		object.slot(KIND).writeTo(writer);
		if (!object.slot(VALUE).equalsNil())
		{
			writer.write("value");
			object.slot(VALUE).writeSummaryTo(writer);
		}
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable");
		writer.write("variable type");
		object.slot(KIND).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a {@code VariableDescriptor variable} which can only
	 * contain values of the specified type.  The new variable initially holds
	 * no value.
	 *
	 * @param contentType
	 *        The type of objects the new variable can contain.
	 * @return A new variable able to hold the specified type of objects.
	 */
	public static AvailObject newVariableWithContentType (
		final A_Type contentType)
	{
		return newVariableWithOuterType(variableTypeFor(contentType));
	}

	/**
	 * Create a {@code variable} of the specified {@linkplain
	 * VariableTypeDescriptor variable type}.  The new variable initially holds
	 * no value.
	 *
	 * @param variableType
	 *        The {@linkplain VariableTypeDescriptor variable type}.
	 * @return A new variable of the given type.
	 */
	@ReferencedInGeneratedCode
	public static AvailObject newVariableWithOuterType (
		final A_Type variableType)
	{
		final AvailObject result = mutable.create();
		result.setSlot(KIND, variableType);
		result.setSlot(HASH_OR_ZERO, 0);
		result.setSlot(VALUE, nil);
		result.setSlot(WRITE_REACTORS, nil);
		return result;
	}

	/**
	 * Construct a new {@code VariableDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param typeTag
	 *            The {@link TypeTag} to embed in the new descriptor.
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
		final TypeTag typeTag,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	/** The mutable {@link VariableDescriptor}. */
	private static final VariableDescriptor mutable =
		new VariableDescriptor(
			Mutability.MUTABLE,
			TypeTag.VARIABLE_TAG,
			ObjectSlots.class,
			IntegerSlots.class);

	@Override
	final VariableDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link VariableDescriptor}. */
	private static final VariableDescriptor immutable =
		new VariableDescriptor(
			Mutability.IMMUTABLE,
			TypeTag.VARIABLE_TAG,
			ObjectSlots.class,
			IntegerSlots.class);

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
