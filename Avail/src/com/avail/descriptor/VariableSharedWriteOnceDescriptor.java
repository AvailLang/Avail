/**
 * VariableSharedWriteOnceDescriptor.java
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

import static com.avail.descriptor.VariableSharedWriteOnceDescriptor.IntegerSlots.*;
import static com.avail.descriptor.VariableSharedWriteOnceDescriptor.ObjectSlots.*;
import java.util.Map;
import java.util.WeakHashMap;
import com.avail.annotations.*;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.serialization.SerializerOperation;

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
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		@HideFieldInDebugger
		HASH_OR_ZERO;

		static
		{
			assert VariableDescriptor.IntegerSlots.HASH_OR_ZERO.ordinal()
				== HASH_OR_ZERO.ordinal();
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
			|| e == HASH_OR_ZERO
			|| e == VALUE
			|| e == WRITE_REACTORS
			|| e == DEPENDENT_CHUNKS_WEAK_SET_POJO;
	}

	@Override @AvailMethod
	void o_SetValue (final AvailObject object, final A_BasicObject newValue)
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
		final AvailObject newValue)
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
		final AvailObject newValue)
	{
		throw new VariableSetException(
			AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE);
	}

	@Override @AvailMethod
	boolean o_CompareAndSwapValues (
		final AvailObject object,
		final AvailObject reference,
		final AvailObject newValue)
	{
		throw new VariableSetException(
			AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE);
	}

	@Override @AvailMethod
	A_Number o_FetchAndAddValue (
		final AvailObject object,
		final A_Number addend)
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
		return SerializerOperation.WRITE_ONCE_VARIABLE;
	}

	/**
	 * Create a {@linkplain VariableSharedWriteOnceDescriptor variable}. This
	 * method should only be used to create module constants, and eventually
	 * local constants.  It should <em>not</em> be used for converting existing
	 * variables; that's what {@link VariableSharedDescriptor} is for.
	 *
	 * @param variableType
	 *        The {@linkplain VariableTypeDescriptor variable type}.
	 * @return
	 */
	public static AvailObject forVariableType (
		final A_Type variableType)
	{
		final AvailObject result = mutableWriteOnce.create();
		result.setSlot(KIND, variableType);
		result.setSlot(HASH_OR_ZERO, 0);
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
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private VariableSharedWriteOnceDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/**
	 * The mutable {@link VariableSharedWriteOnceDescriptor}. Exists only to
	 * support creation.
	 */
	private static final VariableSharedWriteOnceDescriptor mutableWriteOnce =
		new VariableSharedWriteOnceDescriptor(Mutability.MUTABLE);

	/** The shared {@link VariableSharedWriteOnceDescriptor}. */
	static final VariableSharedWriteOnceDescriptor sharedWriteOnce =
		new VariableSharedWriteOnceDescriptor(Mutability.SHARED);
}
