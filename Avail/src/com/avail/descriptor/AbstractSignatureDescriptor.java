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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.AvailInterpreter;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

@ObjectSlots({
	"signature",
	"requiresBlock",
	"returnsBlock"
})
public class AbstractSignatureDescriptor extends SignatureDescriptor
{


	// accessing

	@Override
	public void ObjectBodySignatureRequiresBlockReturnsBlock (
			final AvailObject object,
			final AvailObject bs,
			final AvailObject rqb,
			final AvailObject rtb)
	{
		object.signature(bs);
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
		return object.signature();
	}



	// GENERATED accessors

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

		return TypeDescriptor.Types.abstractSignature.object();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value.

		final int hash = (((object.signature().hash() * 13) + (object.requiresBlock().hash() * 7)) + (object.returnsBlock().hash() * 11));
		return hash;
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return TypeDescriptor.Types.abstractSignature.object();
	}



	// testing

	@Override
	public boolean ObjectIsAbstract (
			final AvailObject object)
	{
		return true;
	}

	/**
	 * Construct a new {@link AbstractSignatureDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected AbstractSignatureDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	final static AbstractSignatureDescriptor mutableDescriptor = new AbstractSignatureDescriptor(true);

	public static AbstractSignatureDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	final static AbstractSignatureDescriptor immutableDescriptor = new AbstractSignatureDescriptor(false);

	public static AbstractSignatureDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
