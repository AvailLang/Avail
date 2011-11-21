/**
 * descriptor/AbstractSignatureDescriptor.java
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

import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.Interpreter;
import com.avail.utility.*;


/**
 * This is a specialization of {@link SignatureDescriptor} that is an abstract
 * declaration of an Avail method (i.e., no implementation).
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AbstractSignatureDescriptor
extends SignatureDescriptor
{

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@linkplain FunctionTypeDescriptor function type} for which this
		 * signature is being specified.
		 */
		BODY_SIGNATURE,
	}


	@Override
	public @NotNull AvailObject o_BodySignature (
		final @NotNull AvailObject object)
	{
		return object.signature();
	}

	@Override
	public @NotNull AvailObject o_Signature (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.BODY_SIGNATURE);
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		return (object.signature().hash() * 19) ^ 0x201FE782;
	}

	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return Types.ABSTRACT_SIGNATURE.o();
	}


	@Override
	public boolean o_IsAbstract (
		final @NotNull AvailObject object)
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
		final @NotNull AvailObject bodySignature)
	{
		final AvailObject instance = mutable().create();
		instance.objectSlotPut(ObjectSlots.BODY_SIGNATURE, bodySignature);
		instance.makeImmutable();
		return instance;
	}


	/**
	 * Construct a new {@link AbstractSignatureDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected AbstractSignatureDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link AbstractSignatureDescriptor}.
	 */
	private final static AbstractSignatureDescriptor mutable =
		new AbstractSignatureDescriptor(true);

	/**
	 * @return The mutable {@link AbstractSignatureDescriptor}.
	 */
	public static AbstractSignatureDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link AbstractSignatureDescriptor}.
	 */
	private final static AbstractSignatureDescriptor immutable =
		new AbstractSignatureDescriptor(false);

	/**
	 * @return The mutable {@link AbstractSignatureDescriptor}.
	 */
	public static AbstractSignatureDescriptor immutable ()
	{
		return immutable;
	}
}
