/**
 * RawPojoDescriptor.java
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

import com.avail.annotations.AvailMethod;
import org.jetbrains.annotations.Nullable;

import static com.avail.descriptor.TypeDescriptor.Types.RAW_POJO;
import java.util.*;

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
	 * The actual Java {@link Object} represented by the {@link AvailObject}
	 * that uses this {@linkplain AbstractAvailObject#descriptor descriptor}.
	 */
	final @Nullable Object javaObject;

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsRawPojoFor(object, javaObject);
	}

	@Override @AvailMethod
	boolean o_EqualsEqualityRawPojo (
		final AvailObject object,
		final AvailObject otherEqualityRawPojo,
		final @Nullable Object otherJavaObject)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_EqualsRawPojoFor (
		final AvailObject object,
		final AvailObject otherRawPojo,
		final @Nullable Object otherJavaObject)
	{
		if (javaObject != otherJavaObject)
		{
			return false;
		}
		// They're equal.  If at least one of the participants is not shared,
		// then there is no danger that we could form an indirection cycle,
		// since that would involve two fibers changing both objects into
		// indirections, which is impossible if one is shared.  And no other
		// thread can transition these objects to shared, so reading the
		// mutability is stable.  Therefore *no lock* is needed.
		if (!object.sameAddressAs(otherRawPojo))
		{
			if (!isShared())
			{
				object.becomeIndirectionTo(otherRawPojo);
			}
			else if (!otherRawPojo.descriptor.isShared())
			{
				otherRawPojo.becomeIndirectionTo(object);
			}
		}
		return true;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		// This ensures that mutations of the wrapped pojo do not corrupt hashed
		// Avail data structures.
		return System.identityHashCode(javaObject) ^ 0x277AB9C3;
	}

	@Override @AvailMethod
	final boolean o_IsRawPojo (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	final @Nullable Object o_JavaObject (final AvailObject object)
	{
		return javaObject;
	}

	@Override @AvailMethod
	final A_Type o_Kind (final AvailObject object)
	{
		return RAW_POJO.o();
	}

	/**
	 * Replace the descriptor with a newly synthesized one that has the same
	 * {@link #javaObject} but is {@linkplain Mutability#IMMUTABLE immutable}.
	 */
	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			object.descriptor = new RawPojoDescriptor(
				Mutability.IMMUTABLE,
				javaObject);
		}
		return object;
	}

	/**
	 * Replace the descriptor with a newly synthesized one that has the same
	 * {@link #javaObject} but is {@linkplain Mutability#SHARED shared}.
	 */
	@Override @AvailMethod
	AvailObject o_MakeShared (final AvailObject object)
	{
		if (!isShared())
		{
			object.descriptor = new RawPojoDescriptor(
				Mutability.SHARED,
				javaObject);
		}
		return object;
	}

	@Override
	final @Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return javaObject;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		// This is not a thread-safe read of the slot, but this method is just
		// for debugging anyway, so don't bother acquiring the lock. Coherence
		// isn't important here.
		builder.append("raw pojo: ");
		builder.append(String.valueOf(javaObject));
	}

	/**
	 * A fake enumeration of slots for a nice description of this pojo.
	 */
	enum FakeSlots implements ObjectSlotsEnum
	{
		/** The sole (pseudo-)slot, the java object itself. */
		JAVA_OBJECT;
	}

	/**
	 * {@inheritDoc}
	 *
	 * Show the actual {@link #o_JavaObject(AvailObject) javaObject}, rather
	 * than just its index.  This is <em>much</em> nicer to have available in
	 * the Eclipse Java debugger.
	 */
	@Override
	AvailObjectFieldHelper[] o_DescribeForDebugger (
		final AvailObject object)
	{
		final List<AvailObjectFieldHelper> fields = new ArrayList<>();
		fields.add(
			new AvailObjectFieldHelper(
				object,
				FakeSlots.JAVA_OBJECT,
				-1,
				javaObject));
		return fields.toArray(new AvailObjectFieldHelper[fields.size()]);
	}

	/**
	 * Construct a new {@link RawPojoDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param javaObject
	 *        The actual Java {@link Object} represented by the {@link
	 *        AvailObject} that will use the new descriptor.
	 */
	protected RawPojoDescriptor (
		final Mutability mutability,
		final @Nullable Object javaObject)
	{
		super(mutability, null, null);
		this.javaObject = javaObject;
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
	 * Object Java Object} for identity-based comparison semantics.
	 *
	 * @param javaObject A Java Object, possibly {@code null}.
	 * @return The new {@linkplain PojoDescriptor Avail pojo}.
	 */
	public static AvailObject identityWrap (final @Nullable Object javaObject)
	{
		final RawPojoDescriptor descriptor = new RawPojoDescriptor(
			Mutability.MUTABLE,
			javaObject);
		final AvailObject wrapper = descriptor.create();
		return wrapper;
	}

	/**
	 * Create a new {@link AvailObject} that wraps the specified {@linkplain
	 * Object Java Object} for equality-based comparison semantics.
	 *
	 * @param javaObject A Java Object, possibly {@code null}.
	 * @return The new {@linkplain PojoDescriptor Avail pojo}.
	 */
	public static AvailObject equalityWrap (final Object javaObject)
	{
		final EqualityRawPojoDescriptor descriptor =
			new EqualityRawPojoDescriptor(
				Mutability.MUTABLE,
				javaObject);
		final AvailObject wrapper = descriptor.create();
		return wrapper;
	}

	@Deprecated
	@Override
	AbstractDescriptor mutable ()
	{
		throw unsupportedOperationException();
	}

	@Deprecated
	@Override
	AbstractDescriptor immutable ()
	{
		throw unsupportedOperationException();
	}

	@Deprecated
	@Override
	AbstractDescriptor shared ()
	{
		throw unsupportedOperationException();
	}
}
