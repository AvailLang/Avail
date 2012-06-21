/**
 * RawPojoDescriptor.java
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

import static com.avail.descriptor.RawPojoDescriptor.IntegerSlots.INDEX;
import static com.avail.descriptor.TypeDescriptor.Types.RAW_POJO;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import com.avail.AvailRuntime;
import com.avail.annotations.*;

/**
 * A {@code RawPojoDescriptor} is a thin veneer over a plain-old Java object
 * (pojo). Avail programs will use {@linkplain PojoDescriptor typed pojos}
 * universally, but the implementation mechanisms frequently require raw pojos
 * (especially for defining {@linkplain PojoTypeDescriptor pojo types}).
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 * @see EqualityRawPojoDescriptor
 */
public class RawPojoDescriptor
extends Descriptor
{
	/**
	 * The {@linkplain List list} of all {@linkplain Object pojos} accessible
	 * from Avail.
	 */
	@InnerAccess static final @NotNull List<Object> allPojosStrongly =
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
		static ReferenceQueue<AvailObject> recyclingQueue;

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
			final @NotNull AvailObject referent)
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
	private static final @NotNull List<WeakPojoReference> allPojosWeakly =
		new ArrayList<WeakPojoReference>(100);

	/**
	 * The {@linkplain ReentrantLock lock} that guards access to the {@linkplain
	 * #allPojosStrongly strong} and {@linkplain #allPojosWeakly weak} pojo
	 * tables.
	 */
	protected static ReentrantLock pojosLock;

	/**
	 * A {@linkplain RawPojoDescriptor raw pojo} for {@link Object}'s
	 * {@linkplain Class class}.
	 */
	private static AvailObject rawObjectClass;

	/**
	 * Answer a {@linkplain RawPojoDescriptor raw pojo} for {@link Object}'s
	 * {@linkplain Class class}.
	 *
	 * @return A raw pojo that represents {@code Object}.
	 */
	public static @NotNull AvailObject rawObjectClass ()
	{
		return rawObjectClass;
	}

	/** The {@code null} {@linkplain PojoDescriptor pojo}. */
	private static AvailObject rawNullObject;

	/**
	 * Answer the {@code null} {@linkplain RawPojoDescriptor pojo}.
	 *
	 * @return The {@code null} pojo.
	 */
	public static @NotNull AvailObject rawNullObject ()
	{
		return rawNullObject;
	}

	/**
	 * Create any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void createWellKnownObjects ()
	{
		assert allPojosStrongly.isEmpty();
		assert allPojosWeakly.isEmpty();
		assert WeakPojoReference.recyclingQueue == null;
		assert pojosLock == null;
		WeakPojoReference.recyclingQueue = new ReferenceQueue<AvailObject>();
		pojosLock = new ReentrantLock();
		rawObjectClass = equalityWrap(Object.class);
		rawNullObject = identityWrap(null);
	}

	/**
	 * Destroy or reset any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void clearWellKnownObjects ()
	{
		allPojosStrongly.clear();
		allPojosWeakly.clear();
		WeakPojoReference.recyclingQueue = null;
		pojosLock = null;
		rawObjectClass = null;
		rawNullObject = null;
	}

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
			while ((ref = WeakPojoReference.recyclingQueue.poll()) != null)
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
					final @NotNull AvailObject object = copiedReference.get();
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
		final @NotNull AbstractSlotsEnum e)
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
	private static final @NotNull Object getPojo (
		final @NotNull AvailObject object)
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
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsRawPojo(object);
	}

	@Override @AvailMethod
	boolean o_EqualsEqualityRawPojo (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aRawPojo)
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
		final @NotNull AvailObject object,
		final @NotNull AvailObject aRawPojo)
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

	@Override @AvailMethod
	boolean o_EqualsRawPojo (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aRawPojo)
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

			if (!object.sameAddressAs(aRawPojo))
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
	int o_Hash (final @NotNull AvailObject object)
	{
		// This ensures that mutations of the wrapped pojo do not corrupt hashed
		// Avail data structures.
		return System.identityHashCode(getPojo(object)) ^ 0x277AB9C3;
	}

	@Override @AvailMethod
	final boolean o_IsRawPojo (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	final Object o_JavaObject (final @NotNull AvailObject object)
	{
		return getPojo(object);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_Kind (final @NotNull AvailObject object)
	{
		return RAW_POJO.o();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		object.descriptor = immutable;
		return object;
	}

	@Override
	final Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> ignoredClassHint)
	{
		return getPojo(object);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("raw pojo@");
		builder.append(object.slot(INDEX));
		builder.append(" = ");
		builder.append(String.valueOf(getPojo(object)));
	}

	/**
	 * Construct a new {@link RawPojoDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain AbstractDescriptor descriptor} represent a
	 *        mutable object?
	 */
	protected RawPojoDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link RawPojoDescriptor}. */
	private static final @NotNull RawPojoDescriptor mutable =
		new RawPojoDescriptor(true);

	/**
	 * Answer the mutable {@link RawPojoDescriptor}.
	 *
	 * @return The mutable {@code RawPojoDescriptor}.
	 */
	public static @NotNull RawPojoDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link RawPojoDescriptor}. */
	private static final @NotNull RawPojoDescriptor immutable =
		new RawPojoDescriptor(false);

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
	private static @NotNull AvailObject wrap (
		final Object pojo,
		final @NotNull RawPojoDescriptor descriptor)
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
	public static @NotNull AvailObject identityWrap (final Object pojo)
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
	public static @NotNull AvailObject equalityWrap (final Object pojo)
	{
		return wrap(pojo, EqualityRawPojoDescriptor.mutable());
	}
}
