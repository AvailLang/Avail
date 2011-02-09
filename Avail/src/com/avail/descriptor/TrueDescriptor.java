/**
 * descriptor/TrueDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

import static com.avail.descriptor.TypeDescriptor.Types.TRUE_TYPE;
import com.avail.annotations.NotNull;

public class TrueDescriptor
extends BooleanDescriptor
{

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsTrue();
	}

	@Override
	public boolean o_EqualsTrue (
		final @NotNull AvailObject object)
	{
		//  Answer true if this is the Avail true object, which it is.

		return true;
	}

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return TRUE_TYPE.o();
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer a 32-bit integer that is always the same for equal objects, but
		//  statistically different for different objects.

		return 0x13F8564E;
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return TRUE_TYPE.o();
	}

	@Override
	public boolean o_ExtractBoolean (
		final @NotNull AvailObject object)
	{
		//  Extract a Smalltalk Boolean from object.

		return true;
	}

	/* Object creation */
	public static AvailObject objectFromBoolean (final boolean b)
	{
		return b ? TrueBooleanObject : FalseBooleanObject;
	}

	/**
	 * Construct a new {@link TrueDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected TrueDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link TrueDescriptor}.
	 */
	private final static TrueDescriptor mutable = new TrueDescriptor(true);

	/**
	 * Answer the mutable {@link TrueDescriptor}.
	 *
	 * @return The mutable {@link TrueDescriptor}.
	 */
	public static TrueDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link TrueDescriptor}.
	 */
	private final static TrueDescriptor immutable = new TrueDescriptor(false);

	/**
	 * Answer the immutable {@link TrueDescriptor}.
	 *
	 * @return The immutable {@link TrueDescriptor}.
	 */
	public static TrueDescriptor immutable ()
	{
		return immutable;
	}
}
