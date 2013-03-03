/**
 * RawPojoDescriptor.java
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

import static com.avail.descriptor.RawPojoDescriptor.IntegerSlots.INDEX;
import static com.avail.descriptor.TypeDescriptor.Types.RAW_POJO;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import com.avail.annotations.*;

/**
 * A {@code RawPojoDescriptor} is a thin veneer over a plain-old Java object
 * (pojo). Avail programs will use {@linkplain PojoDescriptor typed pojos}
 * universally, but the implementation mechanisms frequently require raw pojos
 * (especially for defining {@linkplain PojoTypeDescriptor pojo types}).
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see EqualityRawPojoDescriptor
 */
public class RawPojoDescriptor
extends Descriptor
{
	/**
	 * The {@linkplain List list} of all {@linkplain Object pojos} accessible
	 * from Avail.
	 */
	@InnerAccess static final List<Object> allPojosStrongly =
		new ArrayList<Object>(100);

	/**
	 * A {@code WeakPojoReference} is the mechanism by which {@linkplain
	 * RawPojoDescriptor raw pojos} are discarded by the Java garbage collector.
	 * {@link RawPojoDescriptor#allPojosWeakly} retains weakly all {@linkplain
	 * AvailObject Avail objects} containing pojo references. When the garbage
	 * collector clears a particular {@linkplain WeakReference weak reference},
	 * the pojo corresponding to the index embedded in the weak reference is
	 * discarded from {@link RawPojoDescriptor#allPojosStrongly}, where, as the
	 * name suggests, it is strongly held. The index may then be recycled when
	 * the next pojo is created.
	 */
	private static class WeakPojoReference
	extends WeakReference<AvailObject>
	{
		/**
		 * The {@linkplain ReferenceQueue finalization queue} onto which
		 * {@linkplain RawPojoDescriptor Avail pojos} will be placed when the
		 * Java garbage collector reclaims them.
		 */
		static @Nullable ReferenceQueue<AvailObject> recyclingQueue =
			new ReferenceQueue<AvailObject>();

		/**
		 * The {@linkplain RawPojoDescriptor.IntegerSlots#INDEX index} of
		 * the {@linkplain RawPojoDescriptor Avail pojo} in the {@linkplain
		 * RawPojoDescriptor#allPojosStrongly strong} and {@linkplain
		 * RawPojoDescriptor#allPojosWeakly weak tables} of pojos.
		 */
		int index;

		/**
		 * Construct a new {@link WeakPojoReference}.
		 *
		 * @param referent The {@linkplain RawPojoDescriptor referent}.
		 */
		WeakPojoReference (
			final AvailObject referent)
		{
			super(referent, recyclingQueue);
			assert referent.isRawPojo();
			this.index = referent.slot(INDEX);
		}

		@Override
		public String toString ()
		{
			return String.format(
				"weak ref@%d = %s",
				index,
				String.valueOf(allPojosStrongly.get(index)));
		}
	}

	/**
	 * The {@linkplain List list} of all {@linkplain WeakPojoReference weak
	 * references} to {@linkplain Object pojos} wrapped in {@linkplain
	 * AvailObject Avail objects}.
	 */
	private static final List<WeakPojoReference> allPojosWeakly =
		new ArrayList<WeakPojoReference>(100);

	/**
	 * The {@linkplain ReentrantLock lock} that guards access to the {@linkplain
	 * #allPojosStrongly strong} and {@linkplain #allPojosWeakly weak} pojo
	 * tables.
	 */
	protected static final ReentrantLock pojosLock = new ReentrantLock();

	/**
	 * Compact the {@linkplain #allPojosStrongly strong} and {@linkplain
	 * #allPojosWeakly weak} tables of pojos. Though it is likely that these
	 * tables will be maximally compact after the method returns, poorly timed
	 * garbage collector interference may prevent this; therefore it cannot be
	 * promised.
	 */
	public static void compactPojos ()
	{
		// Grab the lock before modifying the (global) pojo tables.
		pojosLock.lock();
		try
		{
			// Start by finding the highest-indexed weak reference whose
			// referent is not null.
			final int size = allPojosWeakly.size();
			int newSize = size;

			// Consume all defunct weak references from the queue.
			Reference<? extends AvailObject> ref;
			final ReferenceQueue<AvailObject> refQueue =
				WeakPojoReference.recyclingQueue;
			assert refQueue != null;
			while ((ref = refQueue.poll()) != null)
			{
				assert ref.get() == null;
				final WeakPojoReference oldRef = (WeakPojoReference) ref;
				final int destinationIndex = oldRef.index;
				final int sourceIndex = newSize - 1;
				if (destinationIndex != sourceIndex)
				{
					final WeakPojoReference copiedReference =
						allPojosWeakly.get(sourceIndex);
					allPojosStrongly.set(
						destinationIndex, allPojosStrongly.get(sourceIndex));
					allPojosWeakly.set(destinationIndex, copiedReference);
					copiedReference.index = destinationIndex;
					// It is possible that the garbage collector cleared the
					// copied weak reference while we were processing the queue.
					// Take a strong reference to the referent before trying to
					// update its INDEX integer slot. Note that this situation
					// is not harmful as long as we are careful to avoid
					// NullPointerExceptions; it just means that the tables
					// won't necessarily be maximally compact when this method
					// returns. It may still turn out maximally compact if the
					// garbage collector adds the newly defunct weak reference
					// to the queue before the loop exits.
					final AvailObject object = copiedReference.get();
					if (object != null)
					{
						assert object.descriptor() instanceof RawPojoDescriptor;
						object.setSlot(INDEX, destinationIndex);
					}
				}
				allPojosStrongly.remove(sourceIndex);
				allPojosWeakly.remove(sourceIndex);
				newSize--;
			}
		}
		finally
		{
			pojosLock.unlock();
		}
	}

	/** The layout of the integer slots. */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * An index into the {@linkplain RawPojoDescriptor#allPojosStrongly
		 * strong} and {@linkplain RawPojoDescriptor#allPojosWeakly weak tables
		 * of pojos}. As such it constitutes an abstract reference to a
		 * particular pojo.
		 */
		INDEX
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		// Indices are allowed to move because of compaction (triggered by the
		// Java garbage collector).
		return e == INDEX;
	}

	/**
	 * Answer the {@linkplain Object pojo} wrapped by the specified {@linkplain
	 * AvailObject Avail object}.
	 *
	 * @param object An object.
	 * @return A pojo.
	 */
	private static final Object getPojo (final A_BasicObject object)
	{
		pojosLock.lock();
		try
		{
			assert object.isRawPojo();
			final Object pojo = allPojosStrongly.get(
				object.traversed().slot(INDEX));
			return pojo;
		}
		finally
		{
			pojosLock.unlock();
		}
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsRawPojo(object);
	}

	@Override @AvailMethod
	boolean o_EqualsEqualityRawPojo (
		final AvailObject object,
		final AvailObject aRawPojo)
	{
		return false;
	}

	/**
	 * The arguments are equal but not reference identical, so coalesce them.
	 *
	 * @param object
	 *        A {@linkplain RawPojoDescriptor raw pojo}.
	 * @param aRawPojo
	 *        A raw pojo.
	 */
	protected static void coalesce (
		final AvailObject object,
		final AvailObject aRawPojo)
	{
		pojosLock.lock();
		try
		{
			final AvailObject keeper;
			final AvailObject loser;
			if (object.slot(INDEX) < aRawPojo.slot(INDEX))
			{
				keeper = object;
				loser = aRawPojo;
			}
			else
			{
				keeper = aRawPojo;
				loser = object;
			}
			final int index = loser.slot(INDEX);
			allPojosStrongly.set(index, null);
			final WeakPojoReference ref = allPojosWeakly.get(index);
			ref.clear();
			ref.enqueue();
			loser.becomeIndirectionTo(keeper);
			keeper.makeImmutable();
		}
		finally
		{
			pojosLock.unlock();
		}
	}

	@Override @AvailMethod
	boolean o_EqualsRawPojo (
		final AvailObject object,
		final AvailObject aRawPojo)
	{
		pojosLock.lock();
		try
		{
			// Identity semantics only, because Avail equality must be stable,
			// reflexive, symmetric, and transitive. Java equality semantics are
			// available through reflective Java method invocation.
			if (getPojo(object) != getPojo(aRawPojo))
			{
				return false;
			}

			if (!object.sameAddressAs(aRawPojo)
				&& (!isShared() || !aRawPojo.descriptor.isShared()))
			{
				coalesce(object, aRawPojo);
			}
		}
		finally
		{
			pojosLock.unlock();
		}

		return true;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		// This ensures that mutations of the wrapped pojo do not corrupt hashed
		// Avail data structures.
		return System.identityHashCode(getPojo(object)) ^ 0x277AB9C3;
	}

	@Override @AvailMethod
	final boolean o_IsRawPojo (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	final Object o_JavaObject (final AvailObject object)
	{
		return getPojo(object);
	}

	@Override @AvailMethod
	final A_Type o_Kind (final AvailObject object)
	{
		return RAW_POJO.o();
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			object.descriptor = immutable;
		}
		return object;
	}

	@Override @AvailMethod
	AvailObject o_MakeShared (final AvailObject object)
	{
		if (!isShared())
		{
			object.descriptor = shared;
		}
		return object;
	}

	@Override
	final Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return getPojo(object);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		// This is not a thread-safe read of the slot, but this method is just
		// for debugging anyway, so don't bother acquiring the lock. Coherence
		// isn't important here.
		builder.append("raw pojo@");
		builder.append(object.slot(INDEX));
		builder.append(" = ");
		builder.append(String.valueOf(getPojo(object)));
	}

	/**
	 * Construct a new {@link RawPojoDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	protected RawPojoDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link RawPojoDescriptor}. */
	private static final RawPojoDescriptor mutable =
		new RawPojoDescriptor(Mutability.MUTABLE);

	@Override
	RawPojoDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link RawPojoDescriptor}. */
	private static final RawPojoDescriptor immutable =
		new RawPojoDescriptor(Mutability.IMMUTABLE);

	@Override
	RawPojoDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link RawPojoDescriptor}. */
	private static final RawPojoDescriptor shared =
		new RawPojoDescriptor(Mutability.SHARED);

	@Override
	RawPojoDescriptor shared ()
	{
		return shared;
	}

	/**
	 * A {@linkplain RawPojoDescriptor raw pojo} for {@link Object}'s
	 * {@linkplain Class class}.
	 */
	private static final AvailObject rawObjectClass =
		equalityWrap(Object.class).makeShared();

	/**
	 * Answer a {@linkplain RawPojoDescriptor raw pojo} for {@link Object}'s
	 * {@linkplain Class class}.
	 *
	 * @return A raw pojo that represents {@code Object}.
	 */
	public static AvailObject rawObjectClass ()
	{
		return rawObjectClass;
	}

	/** The {@code null} {@linkplain PojoDescriptor pojo}. */
	private static final AvailObject rawNullObject =
		identityWrap(null).makeShared();

	/**
	 * Answer the {@code null} {@linkplain RawPojoDescriptor pojo}.
	 *
	 * @return The {@code null} pojo.
	 */
	public static AvailObject rawNullObject ()
	{
		return rawNullObject;
	}

	/**
	 * Create a new {@link AvailObject} that wraps the specified {@linkplain
	 * Object pojo}.
	 *
	 * @param pojo
	 *        A pojo, possibly {@code null}.
	 * @param descriptor
	 *        The {@linkplain RawPojoDescriptor descriptor} to instantiate.
	 * @return The new {@linkplain PojoDescriptor Avail pojo}.
	 */
	private static AvailObject wrap (
		final @Nullable Object pojo,
		final RawPojoDescriptor descriptor)
	{
		pojosLock.lock();
		try
		{
			// Compact the pojo tables before allocating a new index.
			compactPojos();

			final AvailObject newObject = descriptor.create();
			final int newIndex = allPojosStrongly.size();
			newObject.setSlot(INDEX, newIndex);
			allPojosStrongly.add(pojo);
			allPojosWeakly.add(new WeakPojoReference(newObject));
			return newObject.makeImmutable();
		}
		finally
		{
			pojosLock.unlock();
		}
	}

	/**
	 * Create a new {@link AvailObject} that wraps the specified {@linkplain
	 * Object pojo} for identity-based comparison semantics.
	 *
	 * @param pojo A pojo, possibly {@code null}.
	 * @return The new {@linkplain PojoDescriptor Avail pojo}.
	 */
	public static AvailObject identityWrap (final @Nullable Object pojo)
	{
		return wrap(pojo, mutable);
	}

	/**
	 * Create a new {@link AvailObject} that wraps the specified {@linkplain
	 * Object pojo} for equality-based comparison semantics.
	 *
	 * @param pojo A pojo, possibly {@code null}.
	 * @return The new {@linkplain PojoDescriptor Avail pojo}.
	 */
	public static AvailObject equalityWrap (final Object pojo)
	{
		return wrap(pojo, EqualityRawPojoDescriptor.mutable);
	}
}
