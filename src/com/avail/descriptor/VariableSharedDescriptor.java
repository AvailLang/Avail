/**
 * VariableSharedDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.utility.json.JSONWriter;

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
		 * The low 32 bits are used for the hash, but the upper 32 can be used
		 * by subclasses.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * A slot to hold the hash value.  Must be computed when (or before)
		 * making a variable shared.
		 */
		static final BitField HASH_ALWAYS_SET = bitField(HASH_AND_MORE, 0, 32);

		static
		{
			assert VariableDescriptor.IntegerSlots.HASH_AND_MORE.ordinal()
				== HASH_AND_MORE.ordinal();
			assert VariableDescriptor.IntegerSlots.HASH_OR_ZERO.isSamePlaceAs(
				HASH_ALWAYS_SET);
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
			|| e == VALUE
			|| e == WRITE_REACTORS
			|| e == DEPENDENT_CHUNKS_WEAK_SET_POJO;
	}

	/**
	 * Indicate in the current fiber's {@link Interpreter#availLoader()
	 * availLoader} that a shared variable has just been modified.
	 */
	private static void recordWriteToSharedVariable ()
	{
		final AvailLoader loader = Interpreter.current().availLoaderOrNull();
		if (loader != null)
		{
			loader.statementCanBeSummarized(false);
		}
	}

	/**
	 * Indicate in the current fiber's {@link Interpreter#availLoader()
	 * availLoader} that a shared variable has just been read.
	 *
	 * @param object The shared variable that was read.
	 */
	private static void recordReadFromSharedVariable (
		final AvailObject object)
	{
		final AvailLoader loader = Interpreter.current().availLoaderOrNull();
		if (loader != null
			&& !object.slot(VALUE).equalsNil()
			&& !object.valueWasStablyComputed())
		{
			loader.statementCanBeSummarized(false);
		}
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(HASH_ALWAYS_SET);
	}

	@Override @AvailMethod
	AvailObject o_Value (final AvailObject object)
	{
		recordReadFromSharedVariable(object);
		synchronized (object)
		{
			return super.o_Value(object);
		}
	}

	@Override @AvailMethod
	AvailObject o_GetValue (final AvailObject object)
		throws VariableGetException
	{
		recordReadFromSharedVariable(object);
		synchronized (object)
		{
			return super.o_GetValue(object);
		}
	}

	@Override @AvailMethod
	boolean o_HasValue (final AvailObject object)
	{
		recordReadFromSharedVariable(object);
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
		recordWriteToSharedVariable();
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
		recordWriteToSharedVariable();
	}

	@Override @AvailMethod
	AvailObject o_GetAndSetValue (
			final AvailObject object,
			final A_BasicObject newValue)
		throws VariableGetException, VariableSetException
	{
		// Because the separate read and write operations are performed within
		// the critical section, atomicity is ensured.
		try
		{
			synchronized (object)
			{
				return super.o_GetAndSetValue(
					object,
					newValue.traversed().makeShared());
			}
		}
		finally
		{
			recordWriteToSharedVariable();
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
		try
		{
			synchronized (object)
			{
				return super.o_CompareAndSwapValues(
					object,
					reference,
					newValue.traversed().makeShared());
			}
		}
		finally
		{
			recordWriteToSharedVariable();
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
		try
		{
			synchronized (object)
			{
				return super.o_FetchAndAddValue(
					object,
					addend.traversed().makeShared());
			}
		}
		finally
		{
			recordWriteToSharedVariable();
		}
	}

	@Override @AvailMethod
	void o_ClearValue (final AvailObject object)
	{
		synchronized (object)
		{
			super.o_ClearValue(object);
		}
		recordWriteToSharedVariable();
	}

	/**
	 * Record the fact that the chunk indexed by aChunkIndex depends on this
	 * object not changing.
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
		final A_BasicObject pojo = object.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO);
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
		recordReadFromSharedVariable(object);
		synchronized (object)
		{
			return super.o_AddWriteReactor(object, key, reactor);
		}
	}

	@Override @AvailMethod
	void o_RemoveWriteReactor (final AvailObject object, final A_Atom key)
		throws AvailException
	{
		recordReadFromSharedVariable(object);
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

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable");
		writer.write("variable type");
		object.slot(KIND).writeTo(writer);
		writer.write("value");
		object.value().writeSummaryTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable");
		writer.write("variable type");
		object.kind().writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a {@linkplain VariableSharedDescriptor variable}. This method
	 * should only be used to "upgrade" a variable's representation.
	 *
	 * @param kind
	 *        The {@linkplain VariableTypeDescriptor variable type}.
	 * @param hash
	 *        The hash of the variable.
	 * @param value
	 *        The contents of the variable.
	 * @param oldVariable
	 *        The variable being made shared.
	 * @return
	 */
	static AvailObject createSharedFrom (
		final A_Type kind,
		final int hash,
		final A_BasicObject value,
		final AvailObject oldVariable)
	{
		// Make the parts immutable (not shared), just so they won't be
		// destroyed when the original variable becomes an indirection.
		kind.makeImmutable();
		value.makeImmutable();

		// Create the new variable, but allow the slots to be made shared
		// *after* its initialization.  The existence of a shared object
		// temporarily having non-shared fields is not a violation of the
		// invariant, since no other fibers can access the value until the
		// entire makeShared activity has completed.
		final AvailObject newVariable = mutableInitial.create();
		newVariable.setSlot(KIND, kind);
		newVariable.setSlot(HASH_ALWAYS_SET, hash);
		newVariable.setSlot(VALUE, value);
		newVariable.setSlot(WRITE_REACTORS, NilDescriptor.nil());
		newVariable.setSlot(
			DEPENDENT_CHUNKS_WEAK_SET_POJO, NilDescriptor.nil());

		// Redirect the old to the new to allow cyclic structures.
		assert !oldVariable.descriptor.isShared();
		oldVariable.becomeIndirectionTo(newVariable);

		// Make the parts shared.  This may recurse, but it will terminate when
		// it sees this variable again.  Write back the shared versions for
		// efficiency.
		newVariable.setSlot(KIND, kind.makeShared());
		newVariable.setSlot(VALUE, value.makeShared());

		// Now switch the new variable to truly shared.
		assert newVariable.descriptor == mutableInitial;
		newVariable.descriptor = shared;

		// For safety, make sure the indirection is also shared.
		oldVariable.makeShared();

		return newVariable;
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