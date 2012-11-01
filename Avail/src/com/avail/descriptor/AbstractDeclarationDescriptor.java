/**
 * AbstractDeclarationDescriptor.java
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

import com.avail.annotations.*;
import com.avail.descriptor.TypeDescriptor.Types;


/**
 * This is a specialization of {@link ImplementationDescriptor} that is an abstract
 * declaration of an Avail method (i.e., no implementation).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AbstractDeclarationDescriptor
extends ImplementationDescriptor
{

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain FunctionTypeDescriptor function type} for which this
		 * signature is being specified.
		 */
		BODY_SIGNATURE,
	}


	@Override @AvailMethod
	AvailObject o_BodySignature (final AvailObject object)
	{
		return object.signature();
	}

	@Override @AvailMethod
	AvailObject o_Signature (final AvailObject object)
	{
		return object.slot(ObjectSlots.BODY_SIGNATURE);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return (object.signature().hash() * 19) ^ 0x201FE782;
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		return Types.ABSTRACT_SIGNATURE.o();
	}


	@Override @AvailMethod
	boolean o_IsAbstract (final AvailObject object)
	{
		return true;
	}


	/**
	 * Create a new abstract method signature from the provided arguments.
	 *
	 * @param bodySignature
	 *            The function type at which this abstract method signature will
	 *            be stored in the hierarchy of multimethods.
	 * @return
	 *            An abstract method signature.
	 */
	public static AvailObject create (
		final AvailObject bodySignature)
	{
		final AvailObject instance = mutable().create();
		instance.setSlot(ObjectSlots.BODY_SIGNATURE, bodySignature);
		instance.makeImmutable();
		return instance;
	}


	/**
	 * Construct a new {@link AbstractDeclarationDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected AbstractDeclarationDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link AbstractDeclarationDescriptor}.
	 */
	private static final AbstractDeclarationDescriptor mutable =
		new AbstractDeclarationDescriptor(true);

	/**
	 * @return The mutable {@link AbstractDeclarationDescriptor}.
	 */
	public static AbstractDeclarationDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link AbstractDeclarationDescriptor}.
	 */
	private static final AbstractDeclarationDescriptor immutable =
		new AbstractDeclarationDescriptor(false);

	/**
	 * @return The mutable {@link AbstractDeclarationDescriptor}.
	 */
	public static AbstractDeclarationDescriptor immutable ()
	{
		return immutable;
	}
}
