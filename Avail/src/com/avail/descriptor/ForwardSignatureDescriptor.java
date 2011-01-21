/**
 * descriptor/ForwardSignatureDescriptor.java
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
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.Interpreter;

/**
 * This is a forward declaration of a method.  An actual method must be declared
 * with the same signature before the end of the current module.
 *
 * <p>While a call to this method signature can be compiled after the forward
 * declaration, an attempt to actually call the method will result in an error
 * indicating this problem.</p>
 *
 * <p>Because of the nature of forward declarations, it is meaningless to
 * forward declare a macro, so this facility is not provided.  It's
 * meaningless because a "call-site" for a macro causes the body to execute
 * immediately.</p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class ForwardSignatureDescriptor
extends SignatureDescriptor
{

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The signature being forward-declared.  This is a {@linkplain
		 * ClosureTypeDescriptor closure type}.
		 */
		SIGNATURE
	}


	@Override
	public void o_BodySignature (
			final AvailObject object,
			final AvailObject signature)
	{
		object.signature(signature);
		object.ensureMetacovariant();
	}

	/**
	 * This is just a forward declaration, so just say the actual
	 * implementation's result will agree with our signature's return type.
	 * This will be replaced by a real implementation by the time the module has
	 * finished loading, but calls encountered before the real declaration
	 * occurs will not be able to use the returns clause to parameterize the
	 * return type, and the basic return type of the signature will have to
	 * suffice.
	 */
	@Override
	public AvailObject o_ComputeReturnTypeFromArgumentTypesInterpreter (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final Interpreter anAvailInterpreter)
	{
		return object.bodySignature().returnType();
	}

	/**
	 * This is just a forward declaration, so just say our implementation
	 * accepts the argument types.  There is an issue similar to that mentioned
	 * in {@link #o_ComputeReturnTypeFromArgumentTypesInterpreter} in that all
	 * call sites encountered before the actual method definition occurs will
	 * not have a chance to test the requires clause.  This problem is local to
	 * a method, and is a result of the one-pass parsing scheme.
	 */
	@Override
	public boolean o_IsValidForArgumentTypesInterpreter (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final Interpreter interpreter)
	{
		return true;
	}

	@Override
	public AvailObject o_BodySignature (
			final AvailObject object)
	{
		return object.signature();
	}


	/**
	 * Setter for field signature.
	 */
	@Override
	public void o_Signature (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.SIGNATURE, value);
	}

	/**
	 * Getter for field signature.
	 */
	@Override
	public AvailObject o_Signature (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.SIGNATURE);
	}



	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		return Types.FORWARD_SIGNATURE.o();
	}

	@Override
	public int o_Hash (
			final AvailObject object)
	{
		final int hash = object.signature().hash() * 19;
		return hash;
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		return Types.FORWARD_SIGNATURE.o();
	}


	@Override
	public boolean o_IsForward (
			final AvailObject object)
	{
		return true;
	}


	/**
	 * Make sure my requires clauses and returns clauses are expecting the right
	 * types, based on the declaration of the body.  Do nothing because a
	 * forward declaration can't declare requires and returns clauses.
	 */
	@Override
	public void o_EnsureMetacovariant (
			final AvailObject object)
	{
		return;
	}

	/**
	 * Construct a new {@link ForwardSignatureDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ForwardSignatureDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ForwardSignatureDescriptor}.
	 */
	private final static ForwardSignatureDescriptor mutable =
		new ForwardSignatureDescriptor(true);

	/**
	 * Answer the mutable {@link ForwardSignatureDescriptor}.
	 *
	 * @return The mutable {@link ForwardSignatureDescriptor}.
	 */
	public static ForwardSignatureDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ForwardSignatureDescriptor}.
	 */
	private final static ForwardSignatureDescriptor immutable =
		new ForwardSignatureDescriptor(false);

	/**
	 * Answer the immutable {@link ForwardSignatureDescriptor}.
	 *
	 * @return The immutable {@link ForwardSignatureDescriptor}.
	 */
	public static ForwardSignatureDescriptor immutable ()
	{
		return immutable;
	}
}
