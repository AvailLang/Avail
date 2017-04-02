/**
 * VariableSharedWriteOnceDescriptor.java
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

import static com.avail.descriptor.VariableSharedWriteOnceDescriptor.IntegerSlots.*;
import static com.avail.descriptor.VariableSharedWriteOnceDescriptor.ObjectSlots.*;
import java.util.Map;
import java.util.WeakHashMap;
import com.avail.AvailRuntime;
import com.avail.annotations.AvailMethod;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.VariableGetException;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.effects.LoadingEffect;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.serialization.SerializerOperation;
import org.jetbrains.annotations.Nullable;

/**
 * My {@linkplain AvailObject object instances} are {@linkplain
 * Mutability#SHARED shared} variables that can not be overwritten or cleared
 * after they have been assigned once.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @see VariableDescriptor
 * @see VariableSharedDescriptor
 */
public final class VariableSharedWriteOnceDescriptor
extends VariableSharedDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the hash, but the upper 32 can be used
		 * by subclasses.
		 */
//		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * A slot to hold the hash value.  Must be computed when (or before)
		 * making a variable shared.
		 */
		static final BitField HASH_ALWAYS_SET = bitField(HASH_AND_MORE, 0, 32);

		/**
		 * A flag indicating whether this variable was initialized to a value
		 * that was produced by a pure computation, specifically the kind of
		 * computation that does not disqualify {@link LoadingEffect}s from
		 * being recorded in place of top level statements.
		 */
		static final BitField VALUE_IS_STABLE = bitField(HASH_AND_MORE, 32, 1);

		static
		{
			assert VariableSharedDescriptor.IntegerSlots.HASH_AND_MORE.ordinal()
				== HASH_AND_MORE.ordinal();
			assert VariableSharedDescriptor.IntegerSlots.HASH_ALWAYS_SET
				.isSamePlaceAs(HASH_ALWAYS_SET);
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain AvailObject contents} of the {@linkplain
		 * VariableDescriptor variable}.  This may be assigned exactly once,
		 * and never cleared or overwritten.
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
		WRITE_REACTORS,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} holding a weak set
		 * (implemented as the {@linkplain Map#keySet() key set} of a {@link
		 * WeakHashMap}) of {@link L2Chunk}s that depend on the content of this
		 * variable.  A change to this variable will invalidate all such
		 * chunks.  This field holds the {@linkplain NilDescriptor#nil() nil}
		 * object initially.
		 */
		DEPENDENT_CHUNKS_WEAK_SET_POJO;

		static
		{
			assert VariableSharedDescriptor.ObjectSlots.VALUE.ordinal()
				== VALUE.ordinal();
			assert VariableSharedDescriptor.ObjectSlots.KIND.ordinal()
				== KIND.ordinal();
			assert VariableSharedDescriptor.ObjectSlots.WRITE_REACTORS.ordinal()
				== WRITE_REACTORS.ordinal();
			assert VariableSharedDescriptor.ObjectSlots
					.DEPENDENT_CHUNKS_WEAK_SET_POJO.ordinal()
				== DEPENDENT_CHUNKS_WEAK_SET_POJO.ordinal();
		}
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return super.allowsImmutableToMutableReferenceInField(e)
			|| e == VALUE
			|| e == WRITE_REACTORS
			|| e == DEPENDENT_CHUNKS_WEAK_SET_POJO
			|| e == HASH_AND_MORE;  // only for VALUE_IS_STABLE flag.
	}

	@Override @AvailMethod
	void o_SetValue (final AvailObject object, final A_BasicObject newValue)
		throws VariableSetException
	{
		synchronized (object)
		{
			if (object.hasValue())
			{
				throw new VariableSetException(
					AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE);
			}
			super.o_SetValue(object, newValue.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	void o_SetValueNoCheck (
		final AvailObject object,
		final A_BasicObject newValue)
	{
		synchronized (object)
		{
			if (object.hasValue())
			{
				throw new VariableSetException(
					AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE);
			}
			super.o_SetValueNoCheck(object, newValue.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	AvailObject o_GetAndSetValue (
			final AvailObject object,
			final A_BasicObject newValue)
		throws VariableSetException
	{
		throw new VariableSetException(
			AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE);
	}

	@Override @AvailMethod
	boolean o_CompareAndSwapValues (
			final AvailObject object,
			final A_BasicObject reference,
			final A_BasicObject newValue)
		throws VariableSetException
	{
		throw new VariableSetException(
			AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE);
	}

	@Override @AvailMethod
	A_Number o_FetchAndAddValue (
			final AvailObject object,
			final A_Number addend)
		throws VariableSetException
	{
		throw new VariableSetException(
			AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE);
	}

	@Override
	void o_AtomicAddToMap (
		final AvailObject object,
		final A_BasicObject key,
		final A_BasicObject value)
	throws VariableGetException, VariableSetException
	{
		throw new VariableSetException(
			AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE);
	}

	@Override @AvailMethod
	void o_ClearValue (final AvailObject object)
	{
		throw new VariableSetException(
			AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE);
	}

	@Override @AvailMethod
	boolean o_IsInitializedWriteOnceVariable (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	void o_ValueWasStablyComputed (
		final AvailObject object,
		final boolean wasStablyComputed)
	{
		object.setSlot(VALUE_IS_STABLE, wasStablyComputed ? 1 : 0);
	}

	@Override @AvailMethod
	boolean o_ValueWasStablyComputed (
		final AvailObject object)
	{
		return object.slot(VALUE_IS_STABLE) != 0;
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		// Do nothing; just answer the (shared, write-once) receiver.
		return object;
	}

	@Override @AvailMethod
	AvailObject o_MakeShared (final AvailObject object)
	{
		// Do nothing; just answer the (shared, write-once) receiver.
		return object;
	}

	@Override @AvailMethod
	final SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		if (object.slot(VALUE_IS_STABLE) != 0)
		{
			return SerializerOperation.WRITE_ONCE_VARIABLE_STABLE;
		}
		return SerializerOperation.WRITE_ONCE_VARIABLE_UNSTABLE;
	}

	/**
	 * Create a {@linkplain VariableSharedWriteOnceDescriptor variable}. This
	 * method should only be used to create module constants, and maybe
	 * eventually local constants.  It should <em>not</em> be used for
	 * converting existing variables; that's what {@link
	 * VariableSharedDescriptor} is for.
	 *
	 * @param variableType
	 *        The {@linkplain VariableTypeDescriptor variable type}.
	 * @return
	 *         The new write-once shared variable.
	 */
	public static AvailObject forVariableType (
		final A_Type variableType)
	{
		final AvailObject result = mutableWriteOnce.create();
		result.setSlot(KIND, variableType);
		result.setSlot(HASH_ALWAYS_SET, AvailRuntime.nextHash());
		result.setSlot(VALUE, NilDescriptor.nil());
		result.setSlot(WRITE_REACTORS, NilDescriptor.nil());
		result.setSlot(DEPENDENT_CHUNKS_WEAK_SET_POJO, NilDescriptor.nil());
		result.descriptor = VariableSharedWriteOnceDescriptor.sharedWriteOnce;
		return result;
	}

	/**
	 * Construct a new {@link VariableSharedWriteOnceDescriptor}.
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
	private VariableSharedWriteOnceDescriptor (
		final Mutability mutability,
		final TypeTag typeTag,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	/**
	 * The mutable {@link VariableSharedWriteOnceDescriptor}. Exists only to
	 * support creation.
	 */
	private static final VariableSharedWriteOnceDescriptor mutableWriteOnce =
		new VariableSharedWriteOnceDescriptor(
			Mutability.MUTABLE,
			TypeTag.VARIABLE_TAG,
			ObjectSlots.class,
			IntegerSlots.class);

	/** The shared {@link VariableSharedWriteOnceDescriptor}. */
	private static final VariableSharedWriteOnceDescriptor sharedWriteOnce =
		new VariableSharedWriteOnceDescriptor(
			Mutability.SHARED,
			TypeTag.VARIABLE_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
}
