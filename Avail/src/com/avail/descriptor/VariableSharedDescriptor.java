/**
 * VariableSharedDescriptor.java
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

import static com.avail.descriptor.VariableSharedDescriptor.IntegerSlots.*;
import static com.avail.descriptor.VariableSharedDescriptor.ObjectSlots.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import com.avail.annotations.*;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.exceptions.AvailException;
import com.avail.exceptions.VariableGetException;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.levelTwo.L2Chunk;

/**
 * My {@linkplain AvailObject object instances} are {@linkplain
 * Mutability#SHARED shared} variables.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see VariableDescriptor
 */
public class VariableSharedDescriptor
extends VariableDescriptor
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
		WRITE_REACTORS,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} holding a weak set
		 * (implemented as the {@linkplain Map#keySet() key set} of a {@link
		 * WeakHashMap}) of {@link L2Chunk}s that depend on the membership of
		 * this method.  A change to the membership will invalidate all such
		 * chunks.  This field holds the {@linkplain NilDescriptor#nil() nil}
		 * object initially.
		 */
		DEPENDENT_CHUNKS_WEAK_SET_POJO;

		static
		{
			assert VariableDescriptor.ObjectSlots.VALUE.ordinal()
				== VALUE.ordinal();
			assert VariableDescriptor.ObjectSlots.KIND.ordinal()
				== KIND.ordinal();
			assert VariableDescriptor.ObjectSlots.WRITE_REACTORS.ordinal()
				== WRITE_REACTORS.ordinal();
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
	int o_Hash (final AvailObject object)
	{
		synchronized (object)
		{
			return super.o_Hash(object);
		}
	}

	@Override @AvailMethod
	AvailObject o_Value (final AvailObject object)
	{
		synchronized (object)
		{
			return super.o_Value(object);
		}
	}

	@Override @AvailMethod
	AvailObject o_GetValue (final AvailObject object)
		throws VariableGetException
	{
		synchronized (object)
		{
			return super.o_GetValue(object);
		}
	}

	@Override @AvailMethod
	boolean o_HasValue (final AvailObject object)
	{
		synchronized (object)
		{
			return super.o_HasValue(object);
		}
	}

	@Override @AvailMethod
	void o_SetValue (final AvailObject object, final A_BasicObject newValue)
		throws VariableSetException
	{
		synchronized (object)
		{
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
			super.o_SetValueNoCheck(object, newValue.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	AvailObject o_GetAndSetValue (
			final AvailObject object,
			final A_BasicObject newValue)
		throws VariableGetException, VariableSetException
	{
		// Because the separate read and write operations are performed within
		// the critical section, atomicity is ensured.
		synchronized (object)
		{
			return super.o_GetAndSetValue(
				object,
				newValue.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	boolean o_CompareAndSwapValues (
			final AvailObject object,
			final A_BasicObject reference,
			final A_BasicObject newValue)
		throws VariableGetException, VariableSetException
	{
		// Because the separate read, compare, and write operations are all
		// performed within the critical section, atomicity is ensured.
		synchronized (object)
		{
			return super.o_CompareAndSwapValues(
				object,
				reference,
				newValue.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	A_Number o_FetchAndAddValue (
			final AvailObject object,
			final A_Number addend)
		throws VariableGetException, VariableSetException
	{
		// Because the separate read and write operations are all performed
		// within the critical section, atomicity is ensured.
		synchronized (object)
		{
			return super.o_FetchAndAddValue(
				object,
				addend.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	void o_ClearValue (final AvailObject object)
	{
		synchronized (object)
		{
			super.o_ClearValue(object);
		}
	}

	/**
	 * Record the fact that the chunk indexed by aChunkIndex depends on
	 * this object not changing.
	 */
	@SuppressWarnings("unchecked")
	@Override @AvailMethod
	void o_AddDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		// Record the fact that the given chunk depends on this object not
		// changing.  Local synchronization is sufficient, since invalidation
		// can't happen while L2 code is running (and therefore when the
		// L2Translator could be calling this).
		synchronized (object)
		{
			final A_BasicObject pojo =
				object.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO);
			final Set<L2Chunk> chunkSet;
			if (pojo.equalsNil())
			{
				chunkSet = Collections.newSetFromMap(
					new WeakHashMap<L2Chunk, Boolean>());
				object.setSlot(
					DEPENDENT_CHUNKS_WEAK_SET_POJO,
					RawPojoDescriptor.identityWrap(chunkSet).makeShared());
			}
			else
			{
				chunkSet = (Set<L2Chunk>) pojo.javaObjectNotNull();
			}
			chunkSet.add(chunk);
		}
	}

	@Override @AvailMethod
	void o_RemoveDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		assert L2Chunk.invalidationLock.isHeldByCurrentThread();
		final A_BasicObject pojo =
			object.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO);
		if (!pojo.equalsNil())
		{
			@SuppressWarnings("unchecked")
			final Set<L2Chunk> chunkSet =
				(Set<L2Chunk>) pojo.javaObjectNotNull();
			chunkSet.remove(chunk);
		}
	}

	/**
	 * Invalidate any dependent {@linkplain L2Chunk Level Two chunks}.
	 *
	 * @param object The method that changed.
	 */
	@SuppressWarnings("unused")
	private static void invalidateChunks (final AvailObject object)
	{
		assert L2Chunk.invalidationLock.isHeldByCurrentThread();
		// Invalidate any affected level two chunks.
		final A_BasicObject pojo = object.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO);
		if (!pojo.equalsNil())
		{
			// Copy the set of chunks to avoid modification during iteration.
			@SuppressWarnings("unchecked")
			final
			Set<L2Chunk> originalSet = (Set<L2Chunk>) pojo.javaObjectNotNull();
			final Set<L2Chunk> chunksToInvalidate =
				new HashSet<>(originalSet);
			for (final L2Chunk chunk : chunksToInvalidate)
			{
				chunk.invalidate();
			}
			// The chunk invalidations should have removed all dependencies.
			assert originalSet.isEmpty();
		}
	}

	@Override @AvailMethod
	A_Variable o_AddWriteReactor (
		final AvailObject object,
		final A_Atom key,
		final VariableAccessReactor reactor)
	{
		synchronized (object)
		{
			return super.o_AddWriteReactor(object, key, reactor);
		}
	}

	@Override @AvailMethod
	void o_RemoveWriteReactor (final AvailObject object, final A_Atom key)
		throws AvailException
	{
		synchronized (object)
		{
			super.o_RemoveWriteReactor(object, key);
		}
	}

	@Override @AvailMethod
	A_Set o_ValidWriteReactorFunctions (final AvailObject object)
	{
		synchronized (object)
		{
			return super.o_ValidWriteReactorFunctions(object);
		}
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		// Do nothing; just answer the (shared) receiver.
		return object;
	}

	@Override @AvailMethod
	AvailObject o_MakeShared (final AvailObject object)
	{
		// Do nothing; just answer the (shared) receiver.
		return object;
	}

	/**
	 * Create a {@linkplain VariableSharedDescriptor variable}. This method
	 * should only be used to "upgrade" a variable's representation.
	 *
	 * @param variableType
	 *        The {@linkplain VariableTypeDescriptor variable type}.
	 * @param hash
	 *        The hash of the variable.
	 * @param value
	 *        The contents of the variable.
	 * @return
	 */
	static AvailObject create (
		final A_Type variableType,
		final int hash,
		final AvailObject value)
	{
		final AvailObject result = mutableInitial.create();
		result.setSlot(KIND, variableType);
		result.setSlot(HASH_OR_ZERO, hash);
		result.setSlot(VALUE, value);
		result.setSlot(WRITE_REACTORS, NilDescriptor.nil());
		result.setSlot(DEPENDENT_CHUNKS_WEAK_SET_POJO, NilDescriptor.nil());
		result.descriptor = VariableSharedDescriptor.shared;
		return result;
	}

	/**
	 * Construct a new {@link VariableDescriptor}.  Only provided for use by
	 * {@link VariableSharedWriteOnceDescriptor}.
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
	protected VariableSharedDescriptor (
		final Mutability mutability,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	/**
	 * Construct a new {@link VariableSharedDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private VariableSharedDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/**
	 * The mutable {@link VariableSharedDescriptor}. Exists only to support
	 * creation.
	 */
	private static final VariableSharedDescriptor mutableInitial =
		new VariableSharedDescriptor(Mutability.MUTABLE);

	/** The shared {@link VariableSharedDescriptor}. */
	static final VariableSharedDescriptor shared =
		new VariableSharedDescriptor(Mutability.SHARED);
}
