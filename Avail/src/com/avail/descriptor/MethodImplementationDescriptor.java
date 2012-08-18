/**
 * MethodImplementationDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.METHOD_SIGNATURE;
import com.avail.annotations.*;

/**
 * An object instance of {@code MethodImplementationDescriptor} represents a
 * function in the collection of available functions for this method hierarchy.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class MethodImplementationDescriptor
extends ImplementationDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * A function to execute this signature is selected during a call.
		 */
		BODY_BLOCK
	}

	@Override @AvailMethod
	AvailObject o_BodySignature (
		final AvailObject object)
	{
		return object.bodyBlock().kind();
	}

	@Override @AvailMethod
	AvailObject o_BodyBlock (
		final AvailObject object)
	{
		return object.slot(ObjectSlots.BODY_BLOCK);
	}

	@Override @AvailMethod
	int o_Hash (
		final AvailObject object)
	{
		return (object.bodyBlock().hash() * 19) ^ 0x70B2B1A9;
	}

	@Override @AvailMethod
	AvailObject o_Kind (
		final AvailObject object)
	{
		//  Answer the object's type.

		return METHOD_SIGNATURE.o();
	}

	@Override @AvailMethod
	boolean o_IsMethod (
		final AvailObject object)
	{
		return true;
	}


	/**
	 * Create a new method signature from the provided arguments.
	 *
	 * @param bodyBlock
	 *            The body of the signature.  This will be invoked when the
	 *            message is sent, assuming the argument types match and there
	 *            is no more specific version.
	 * @return
	 *            A method signature.
	 */
	public static AvailObject create (
		final AvailObject bodyBlock)
	{
		final AvailObject instance = mutable().create();
		instance.setSlot(ObjectSlots.BODY_BLOCK, bodyBlock);
		instance.makeImmutable();
		return instance;
	}


	/**
	 * Construct a new {@link MethodImplementationDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected MethodImplementationDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link MethodImplementationDescriptor}.
	 */
	private static final MethodImplementationDescriptor mutable =
		new MethodImplementationDescriptor(true);

	/**
	 * Answer the mutable {@link MethodImplementationDescriptor}.
	 *
	 * @return The mutable {@link MethodImplementationDescriptor}.
	 */
	public static MethodImplementationDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link MethodImplementationDescriptor}.
	 */
	private static final MethodImplementationDescriptor immutable =
		new MethodImplementationDescriptor(false);

	/**
	 * Answer the immutable {@link MethodImplementationDescriptor}.
	 *
	 * @return The immutable {@link MethodImplementationDescriptor}.
	 */
	public static MethodImplementationDescriptor immutable ()
	{
		return immutable;
	}
}
