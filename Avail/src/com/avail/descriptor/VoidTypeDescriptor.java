/**
 * descriptor/VoidTypeDescriptor.java
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

import com.avail.annotations.NotNull;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.AvailObject;

/**
 * {@code VoidType} implements the type of the {@linkplain
 * VoidDescriptor#voidObject() void object}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
@IntegerSlots("hash")
@ObjectSlots({
	"name", 
	"parent", 
	"myType"
})
public class VoidTypeDescriptor
extends PrimitiveTypeDescriptor
{
	@Override
	@ThreadSafe
	public boolean ObjectIsSubtypeOf (
		final @NotNull AvailObject object, 
		final @NotNull AvailObject aType)
	{
		//  Check if object (the type void) is a subtype of aType (may also be
		// void).
		return aType.isSupertypeOfVoid();
	}

	@Override
	@ThreadSafe
	public boolean ObjectIsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object, 
		final @NotNull AvailObject aPrimitiveType)
	{
		//  Check if object (the type void) is a supertype of aPrimitiveType (a
		// primitive type). Always true.
		return true;
	}

	@Override
	@ThreadSafe
	public boolean ObjectIsSupertypeOfVoid (final @NotNull AvailObject object)
	{
		//  Only void is a supertype of void.
		return true;
	}


	/**
	 * Construct a new {@link VoidTypeDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected VoidTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}


	final static VoidTypeDescriptor mutableDescriptor = new VoidTypeDescriptor(true);

	/**
	 * Answer a mutable {@link VoidTypeDescriptor}.
	 * 
	 * @return A mutable {@link VoidTypeDescriptor}.
	 */
	@ThreadSafe
	public static VoidTypeDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	final static VoidTypeDescriptor immutableDescriptor = new VoidTypeDescriptor(false);

	/**
	 * Answer an immutable {@link VoidTypeDescriptor}.
	 * 
	 * @return An immutable {@link VoidTypeDescriptor}.
	 */
	@ThreadSafe
	public static VoidTypeDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}

}
