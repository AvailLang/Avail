/**
 * descriptor/MethodSignatureDescriptor.java
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
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.AvailInterpreter;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

@ObjectSlots({
	"bodyBlock",
	"requiresBlock",
	"returnsBlock"
})
public class MethodSignatureDescriptor extends SignatureDescriptor
{


	// accessing

	@Override
	public void ObjectBodyBlockRequiresBlockReturnsBlock (
			final AvailObject object,
			final AvailObject bb,
			final AvailObject rqb,
			final AvailObject rtb)
	{
		//  Set my blocks.  Also verify metacovariance and metacontravariance here.

		object.bodyBlock(bb);
		object.requiresBlock(rqb);
		object.returnsBlock(rtb);
		object.ensureMetacovariant();
	}

	@Override
	public AvailObject ObjectComputeReturnTypeFromArgumentTypesInterpreter (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final AvailInterpreter anAvailInterpreter)
	{
		//  We simply run the 'returns' block, passing in the static argument types from the call site.

		final AvailObject result = anAvailInterpreter.runClosureArguments(object.returnsBlock(), argTypes);
		if (!result.isSubtypeOf(object.bodySignature().returnType()))
		{
			error("The 'returns' block should produce a type more specific than the body's basic return type", object);
			return VoidDescriptor.voidObject();
		}
		return result;
	}

	@Override
	public boolean ObjectIsValidForArgumentTypesInterpreter (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final AvailInterpreter interpreter)
	{
		//  We simply run the 'requires' block, passing in the static arguments types from the call site.  The result of
		//  the 'requires' block is an Avail boolean, which we convert before answering it.

		final AvailObject result = interpreter.runClosureArguments(object.requiresBlock(), argTypes);
		//  Make sure this is a valid Avail boolean, convert it to a Smalltalk boolean, and return it.
		return result.extractBoolean();
	}

	@Override
	public AvailObject ObjectBodySignature (
			final AvailObject object)
	{
		//  Answer my signature.

		return object.bodyBlock().type();
	}



	// GENERATED accessors

	/**
	 * Setter for field bodyBlock.
	 */
	@Override
	public void ObjectBodyBlock (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Setter for field requiresBlock.
	 */
	@Override
	public void ObjectRequiresBlock (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-8, value);
	}

	/**
	 * Setter for field returnsBlock.
	 */
	@Override
	public void ObjectReturnsBlock (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-12, value);
	}

	/**
	 * Getter for field bodyBlock.
	 */
	@Override
	public AvailObject ObjectBodyBlock (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}

	/**
	 * Getter for field requiresBlock.
	 */
	@Override
	public AvailObject ObjectRequiresBlock (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-8);
	}

	/**
	 * Getter for field returnsBlock.
	 */
	@Override
	public AvailObject ObjectReturnsBlock (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-12);
	}



	// operations

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.

		return Types.methodSignature.object();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value.

		final int hash = (((object.bodyBlock().hash() * 19) + (object.requiresBlock().hash() * 13)) + (object.returnsBlock().hash() * 3));
		return hash;
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.methodSignature.object();
	}



	// testing

	@Override
	public boolean ObjectIsImplementation (
			final AvailObject object)
	{
		return true;
	}

	/**
	 * Construct a new {@link MethodSignatureDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected MethodSignatureDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link MethodSignatureDescriptor}.
	 */
	private final static MethodSignatureDescriptor mutableDescriptor = new MethodSignatureDescriptor(true);

	/**
	 * Answer the mutable {@link MethodSignatureDescriptor}.
	 *
	 * @return The mutable {@link MethodSignatureDescriptor}.
	 */
	public static MethodSignatureDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link MethodSignatureDescriptor}.
	 */
	private final static MethodSignatureDescriptor immutableDescriptor = new MethodSignatureDescriptor(false);

	/**
	 * Answer the immutable {@link MethodSignatureDescriptor}.
	 *
	 * @return The immutable {@link MethodSignatureDescriptor}.
	 */
	public static MethodSignatureDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
