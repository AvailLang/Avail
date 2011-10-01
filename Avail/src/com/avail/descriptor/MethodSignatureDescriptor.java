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

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.METHOD_SIGNATURE;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.compiler.AvailCompilerException;
import com.avail.exceptions.*;
import com.avail.interpreter.Interpreter;
import com.avail.utility.*;

public class MethodSignatureDescriptor
extends SignatureDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		BODY_BLOCK,
		REQUIRES_BLOCK,
		RETURNS_BLOCK
	}

	@Override
	public AvailObject o_ComputeReturnTypeFromArgumentTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull AvailObject impSet,
		final @NotNull Interpreter interpreter,
		final Continuation1<Generator<String>> failBlock)
	{
		// We simply run the 'returns' block, passing in the static argument
		// types from the call site.
		final AvailObject result = interpreter.runFunctionArguments(
			object.returnsBlock(),
			argTypes);
		if (!result.isSubtypeOf(object.bodySignature().returnType()))
		{
			failBlock.value(
				new Generator<String>()
				{

					@Override
					public String value ()
					{
						return String.format(
							"The 'returns' block of %s should produce a type "
							+ "more specific than the body's basic return "
							+ "type%n(produced = %s, expected = %s)",
							impSet.name().name(),
							result,
							object.bodySignature().returnType());
					}
				});
			return NullDescriptor.nullObject();
		}
		return result;
	}

	@Override
	public boolean o_IsValidForArgumentTypesInterpreter (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull Interpreter interpreter)
	{
		//  We simply run the 'requires' block, passing in the static arguments types from the call site.  The result of
		//  the 'requires' block is an Avail boolean, which we convert before answering it.

		final AvailObject result = interpreter.runFunctionArguments(
			object.requiresBlock(),
			argTypes);
		return result.extractBoolean();
	}

	@Override
	public AvailObject o_BodySignature (
		final @NotNull AvailObject object)
	{
		//  Answer my signature.

		return object.bodyBlock().kind();
	}

	@Override
	public AvailObject o_BodyBlock (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.BODY_BLOCK);
	}

	@Override
	public AvailObject o_RequiresBlock (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.REQUIRES_BLOCK);
	}

	@Override
	public AvailObject o_ReturnsBlock (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.RETURNS_BLOCK);
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer a 32-bit hash value.

		final int hash = object.bodyBlock().hash() * 19 + object.requiresBlock().hash() * 13 + object.returnsBlock().hash() * 3;
		return hash;
	}

	@Override
	public AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return METHOD_SIGNATURE.o();
	}

	@Override
	public boolean o_IsMethod (
		final @NotNull AvailObject object)
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
	 * @param requiresBlock
	 *            The requires block of the signature.  This is run when a call
	 *            site is compiled with matching static argument types.  The
	 *            argument types are passed to this block and it should return
	 *            true if they are mutually acceptable.
	 * @param returnsBlock
	 *            The returns block of the signature.  This is run when a call
	 *            site is compiled with matching static argument types.  The
	 *            argument types are passed to this block as for the
	 *            requiresBlock, but it returns the static type for the result
	 *            of the call for this call site.  This type is used as the
	 *            static result type of the call.  The return value is checked
	 *            against this expected return type at runtime.
	 * @return
	 *            A method signature.
	 */
	public static AvailObject create (
		final @NotNull AvailObject bodyBlock,
		final @NotNull AvailObject requiresBlock,
		final @NotNull AvailObject returnsBlock)
	throws SignatureException
	{
		final AvailObject instance = mutable().create();
		instance.objectSlotPut(ObjectSlots.BODY_BLOCK, bodyBlock);
		instance.objectSlotPut(ObjectSlots.REQUIRES_BLOCK, requiresBlock);
		instance.objectSlotPut(ObjectSlots.RETURNS_BLOCK, returnsBlock);
		instance.makeImmutable();
		instance.ensureMetacovariant();
		return instance;
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
	private final static MethodSignatureDescriptor mutable = new MethodSignatureDescriptor(true);

	/**
	 * Answer the mutable {@link MethodSignatureDescriptor}.
	 *
	 * @return The mutable {@link MethodSignatureDescriptor}.
	 */
	public static MethodSignatureDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link MethodSignatureDescriptor}.
	 */
	private final static MethodSignatureDescriptor immutable = new MethodSignatureDescriptor(false);

	/**
	 * Answer the immutable {@link MethodSignatureDescriptor}.
	 *
	 * @return The immutable {@link MethodSignatureDescriptor}.
	 */
	public static MethodSignatureDescriptor immutable ()
	{
		return immutable;
	}
}
