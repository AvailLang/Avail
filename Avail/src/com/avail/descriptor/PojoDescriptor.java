/**
 * PojoDescriptor.java
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

import static com.avail.descriptor.PojoDescriptor.ObjectSlots.*;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.annotations.*;

/**
 * A {@code PojoDescriptor} describes a plain-old Java object (pojo) that is
 * accessible to an Avail programmer as an {@linkplain AvailObject Avail
 * object}. An Avail pojo comprises a {@linkplain RawPojoDescriptor raw pojo}
 * and a {@linkplain PojoTypeDescriptor pojo type} that describes the pojo
 * contextually.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class PojoDescriptor
extends Descriptor
{
	/** The {@linkplain PojoDescriptor pojo} that wraps Java's {@code null}. */
	private static AvailObject nullObject;

	/**
	 * Answer the {@linkplain PojoDescriptor pojo} that wraps Java's
	 * {@code null}.
	 *
	 * @return The {@code null} pojo.
	 */
	public static AvailObject nullObject ()
	{
		return nullObject;
	}

	/**
	 * Create any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void createWellKnownObjects ()
	{
		nullObject = newPojo(
			RawPojoDescriptor.rawNullObject(),
			PojoTypeDescriptor.pojoBottom());
	}

	/**
	 * Destroy or reset any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void clearWellKnownObjects ()
	{
		nullObject = null;
	}

	/** The layout of the object slots. */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain RawPojoDescriptor raw pojo}.
		 */
		RAW_POJO,

		/**
		 * The {@linkplain PojoTypeDescriptor kind} of the {@linkplain
		 * PojoDescriptor descriptor}.
		 */
		KIND
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return another.equalsPojo(object);
	}

	@Override @AvailMethod
	boolean o_EqualsPojo (
		final AvailObject object,
		final AvailObject aPojo)
	{
		if (!object.slot(RAW_POJO).equals(aPojo.slot(RAW_POJO))
			|| !object.slot(KIND).equals(aPojo.slot(KIND)))
		{
			return false;
		}

		if (!object.sameAddressAs(aPojo))
		{
			object.becomeIndirectionTo(aPojo);
			aPojo.makeImmutable();
		}

		return true;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		int hash = object.slot(RAW_POJO).hash() ^ 0x749101DD;
		hash *= AvailObject.multiplier;
		hash += object.slot(KIND).hash();
		return hash;
	}

	@Override @AvailMethod
	boolean o_IsPojo (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		return object.slot(KIND);
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		object.descriptor = immutable();
		object.slot(RAW_POJO).makeImmutable();
		object.slot(KIND).makeImmutable();
		return object;
	}

	@Override
	Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return object.slot(RAW_POJO).javaObject();
	}

	@Override @AvailMethod
	AvailObject o_RawPojo (final AvailObject object)
	{
		return object.slot(RAW_POJO);
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (
		final AvailObject object)
	{
		return false;
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append(String.valueOf(object.slot(RAW_POJO).javaObject()));
		builder.append(" ∈ ");
		object.slot(KIND).printOnAvoidingIndent(
			builder, recursionList, indent);
	}

	/**
	 * Construct a new {@link PojoDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain AbstractDescriptor descriptor} represent a
	 *        mutable object?
	 */
	private PojoDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link PojoDescriptor}. */
	private static final PojoDescriptor mutable =
		new PojoDescriptor(true);

	/**
	 * Answer the mutable {@link PojoDescriptor}.
	 *
	 * @return The mutable {@code PojoDescriptor}.
	 */
	public static PojoDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link PojoDescriptor}. */
	private static final PojoDescriptor immutable =
		new PojoDescriptor(false);

	/**
	 * Answer the immutable {@link PojoDescriptor}.
	 *
	 * @return The immutable {@code PojoDescriptor}.
	 */
	public static PojoDescriptor immutable ()
	{
		return immutable;
	}

	/**
	 * Create a new {@link AvailObject} that wraps the specified {@linkplain
	 * RawPojoDescriptor raw pojo} and has the specified {@linkplain
	 * PojoTypeDescriptor pojo type}.
	 *
	 * @param rawPojo A raw pojo.
	 * @param pojoType A pojo type.
	 * @return The new {@linkplain PojoDescriptor Avail pojo}.
	 */
	public static AvailObject newPojo (
		final AvailObject rawPojo,
		final AvailObject pojoType)
	{
		final AvailObject newObject = mutable.create();
		newObject.setSlot(RAW_POJO, rawPojo);
		newObject.setSlot(KIND, pojoType);
		return newObject.makeImmutable();
	}
}
