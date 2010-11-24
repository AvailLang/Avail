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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.AvailInterpreter;
import java.util.List;

@ObjectSlots("signature")
public class ForwardSignatureDescriptor extends SignatureDescriptor
{


	// accessing

	@Override
	public void ObjectBodySignature (
			final AvailObject object,
			final AvailObject signature)
	{
		object.signature(signature);
		object.ensureMetacovariant();
	}

	@Override
	public AvailObject ObjectComputeReturnTypeFromArgumentTypesInterpreter (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final AvailInterpreter anAvailInterpreter)
	{
		//  We're just a forward declaration, so just say the actual implementation's result will
		//  agree with our signature's return type.  We wiil be replaced by a real implementation
		//  by the time the module has finished loading, but calls encountered before the real
		//  declaration occurs will not be able to use the 'returns' clause to parameterize the
		//  return type, and the basic return type of the signature will have to suffice.

		return object.bodySignature().returnType();
	}

	@Override
	public boolean ObjectIsValidForArgumentTypesInterpreter (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final AvailInterpreter interpreter)
	{
		//  We're just a forward declaration, so just say our implementation accepts the argument types.  There
		//  is an issue similar to that mentioned in computeReturnTypeFromArgumentTypes:interpreter:.  All
		//  call sites encountered before the actual method definition occurs will not have a chance to try the
		//  'requires' clause.  This problem is local to a method, and is a result of the one-pass multi-tiered
		//  parsing scheme.

		return true;
	}

	@Override
	public AvailObject ObjectBodySignature (
			final AvailObject object)
	{
		return object.signature();
	}



	// GENERATED accessors

	/**
	 * Setter for field signature.
	 */
	@Override
	public void ObjectSignature (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Getter for field signature.
	 */
	@Override
	public AvailObject ObjectSignature (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}



	// operations

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.

		return Types.forwardSignature.object();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value.

		final int hash = (object.signature().hash() * 19);
		return hash;
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.forwardSignature.object();
	}



	// testing

	@Override
	public boolean ObjectIsForward (
			final AvailObject object)
	{
		return true;
	}



	// validation

	@Override
	public void ObjectEnsureMetacovariant (
			final AvailObject object)
	{
		//  Make sure my requires clauses and returns clauses are expecting the
		//  right types, based on the declaration of the body.  Do nothing because
		//  a forward declaration can't declare requires and returns clauses.


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
	private final static ForwardSignatureDescriptor mutableDescriptor = new ForwardSignatureDescriptor(true);

	/**
	 * Answer the mutable {@link ForwardSignatureDescriptor}.
	 *
	 * @return The mutable {@link ForwardSignatureDescriptor}.
	 */
	public static ForwardSignatureDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link ForwardSignatureDescriptor}.
	 */
	private final static ForwardSignatureDescriptor immutableDescriptor = new ForwardSignatureDescriptor(false);

	/**
	 * Answer the immutable {@link ForwardSignatureDescriptor}.
	 *
	 * @return The immutable {@link ForwardSignatureDescriptor}.
	 */
	public static ForwardSignatureDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
