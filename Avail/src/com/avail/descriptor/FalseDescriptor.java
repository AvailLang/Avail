/**
 * descriptor/FalseDescriptor.java
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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TypeDescriptor.Types;

public class FalseDescriptor extends BooleanDescriptor
{


	// operations

	@Override
	public boolean o_Equals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsFalse();
	}

	@Override
	public boolean o_EqualsFalse (
			final AvailObject object)
	{
		//  Answer true if this is the Avail false object, which it is.

		return true;
	}

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.falseType.object();
	}

	@Override
	public int o_Hash (
			final AvailObject object)
	{
		//  Answer a 32-bit long that is always the same for equal objects, but
		//  statistically different for different objects.

		return 0x15F6584D;
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.falseType.object();
	}



	// operations-booleans

	@Override
	public boolean o_ExtractBoolean (
			final AvailObject object)
	{
		//  Extract a Smalltalk Boolean from object.

		return false;
	}





	/* Object creation */
	public static AvailObject objectFromBoolean (boolean b)
	{
		return b ? TrueBooleanObject : FalseBooleanObject;
	};

	/**
	 * Construct a new {@link FalseDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected FalseDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link FalseDescriptor}.
	 */
	private final static FalseDescriptor mutableDescriptor = new FalseDescriptor(true);

	/**
	 * Answer the mutable {@link FalseDescriptor}.
	 *
	 * @return The mutable {@link FalseDescriptor}.
	 */
	public static FalseDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link FalseDescriptor}.
	 */
	private final static FalseDescriptor immutableDescriptor = new FalseDescriptor(false);

	/**
	 * Answer the immutable {@link FalseDescriptor}.
	 *
	 * @return The immutable {@link FalseDescriptor}.
	 */
	public static FalseDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
