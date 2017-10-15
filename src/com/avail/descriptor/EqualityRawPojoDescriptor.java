/**
 * EqualityRawPojoDescriptor.java
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

import com.avail.annotations.AvailMethod;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

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
	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsEqualityRawPojoFor(object, javaObject);
	}

	@Override @AvailMethod
	boolean o_EqualsEqualityRawPojo (
		final AvailObject object,
		final AvailObject otherEqualityRawPojo,
		final @Nullable Object otherJavaObject)
	{
		final @Nullable Object javaObject2 = javaObject;
		if (javaObject2 == null || otherJavaObject == null)
		{
			if (javaObject2 == null && otherJavaObject == null)
			{
				if (!object.sameAddressAs(otherEqualityRawPojo))
				{
					// They're equal.  If at least one of the participants is
					// not shared, then there is no danger that we could form an
					// indirection cycle, since that would involve two fibers
					// changing both objects into indirections, which is
					// impossible if one is shared.  And no other thread can
					// transition these objects to shared, so reading the
					// mutability is stable.  Therefore *no lock* is needed.
					if (!isShared())
					{
						object.becomeIndirectionTo(otherEqualityRawPojo);
					}
					else if (!otherEqualityRawPojo.descriptor.isShared())
					{
						otherEqualityRawPojo.becomeIndirectionTo(object);
					}
				}
				return true;
			}
			return false;
		}
		// Neither is null.
		if (!javaObject2.equals(otherJavaObject))
		{
			return false;
		}
		// They're equal.  If at least one of the participants is not shared,
		// then there is no danger that we could form an indirection cycle,
		// since that would involve two fibers changing both objects into
		// indirections, which is impossible if one is shared.  And no other
		// thread can transition these objects to shared, so reading the
		// mutability is stable.  Therefore *no lock* is needed.
		if (!object.sameAddressAs(otherEqualityRawPojo))
		{
			if (!isShared())
			{
				object.becomeIndirectionTo(otherEqualityRawPojo);
			}
			else if (!otherEqualityRawPojo.descriptor.isShared())
			{
				otherEqualityRawPojo.becomeIndirectionTo(object);
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_EqualsRawPojoFor (
		final AvailObject object,
		final AvailObject otherRawPojo,
		final @Nullable Object aRawPojo)
	{
		return false;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		final Object javaObject2 = javaObject;
		return javaObject2 == null
			? 0xC44EEE95
			: javaObject2.hashCode() ^ 0x59EEE44C;
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
			object.descriptor = new EqualityRawPojoDescriptor(
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
			object.descriptor = new EqualityRawPojoDescriptor(
				Mutability.SHARED,
				javaObject);
		}
		return object;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append("equality raw pojo: ");
		builder.append(String.valueOf(javaObject));
	}

	/**
	 * Construct a new {@link EqualityRawPojoDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param javaObject
	 *        The actual Java {@link Object} represented by the {@link
	 *        AvailObject} that will use the new descriptor.
	 */
	EqualityRawPojoDescriptor (
		final Mutability mutability,
		final @Nullable Object javaObject)
	{
		super(mutability, javaObject);
	}

}
