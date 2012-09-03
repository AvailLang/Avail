/**
 * EqualityRawPojoDescriptor.java
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

import static com.avail.descriptor.EqualityRawPojoDescriptor.IntegerSlots.*;
import java.util.List;
import com.avail.annotations.*;

/**
 * {@code EqualityRawPojoDescriptor} differs from {@link RawPojoDescriptor}
 * in that equality of wrapped pojos is based on semantic equivalence rather
 * than referential equality. It is used only for immutable reflective classes
 * and exists only to support certain recursive comparison operations.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see RawPojoDescriptor
 */
final class EqualityRawPojoDescriptor
extends RawPojoDescriptor
{
	/** The layout of the integer slots. */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * An abstract reference to a particular pojo.
		 */
		INDEX;

		static
		{
			assert RawPojoDescriptor.IntegerSlots.INDEX.ordinal()
				== INDEX.ordinal();
		}
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		// Indices are allowed to move because of compaction (triggered by the
		// Java garbage collector).
		return e == INDEX
			|| e == RawPojoDescriptor.IntegerSlots.INDEX;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return another.equalsEqualityRawPojo(object);
	}

	@Override @AvailMethod
	boolean o_EqualsEqualityRawPojo (
		final AvailObject object,
		final AvailObject aRawPojo)
	{
		pojosLock.lock();
		try
		{
			// Use Java equality semantics: the creator of this wrapper has
			// ensured that the operation satisfies the Avail requirements for
			// equality.
			if (!object.javaObject().equals(aRawPojo.javaObject()))
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
	boolean o_EqualsRawPojo (
		final AvailObject object,
		final AvailObject aRawPojo)
	{
		return false;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		// Objects eligible for semantic equality comparison must satisfy the
		// contract for Object.hashCode().
		return object.javaObject().hashCode() ^ 0x59EEE44C;
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (
		final AvailObject object)
	{
		object.descriptor = immutable;
		return object;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("equality raw pojo@");
		builder.append(object.slot(INDEX));
		builder.append(" = ");
		builder.append(String.valueOf(object.javaObject()));
	}

	/**
	 * Construct a new {@link EqualityRawPojoDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain EqualityRawPojoDescriptor descriptor}
	 *        represent a mutable object?
	 */
	private EqualityRawPojoDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link EqualityRawPojoDescriptor}. */
	private static final EqualityRawPojoDescriptor mutable =
		new EqualityRawPojoDescriptor(true);

	/**
	 * Answer the mutable {@link EqualityRawPojoDescriptor}.
	 *
	 * @return The mutable {@code EqualityRawPojoDescriptor}.
	 */
	public static EqualityRawPojoDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link EqualityRawPojoDescriptor}. */
	private static final EqualityRawPojoDescriptor immutable =
		new EqualityRawPojoDescriptor(false);
}
