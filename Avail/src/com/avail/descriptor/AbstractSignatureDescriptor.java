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

import static com.avail.descriptor.AvailObject.error;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.exceptions.SignatureException;
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
		SIGNATURE,

		/**
		 * A {@linkplain FunctionDescriptor function} that takes argument
		 * {@linkplain TypeDescriptor types} at a call site and answers whether
		 * this is a legal call. This is necessary because some usage conditions
		 * on methods, even though they may be inherently static, cannot be
		 * expressed solely in terms of a lattice of types. Sometimes the
		 * relationship between the types of different arguments must also be
		 * taken into account.
		 */
		REQUIRES_BLOCK,

		/**
		 * A {@linkplain FunctionDescriptor function} that maps argument
		 * {@linkplain TypeDescriptor types} at a call site into a return type
		 * for that call.
		 */
		RETURNS_BLOCK
	}



	/**
	 * We simply run the 'returns' block, passing in the static argument types
	 * from the call site.
	 */
	@Override
	public @NotNull AvailObject o_ComputeReturnTypeFromArgumentTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull AvailObject impSet,
		final @NotNull Interpreter interpreter,
		final Continuation1<Generator<String>> failBlock)
	{
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

	/**
	 * We simply run the 'requires' block, passing in the static arguments types
	 * from the call site.  The result of the 'requires' block is an Avail
	 * boolean, which we convert before answering it.
	 */
	@Override
	public boolean o_IsValidForArgumentTypesInterpreter (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull Interpreter interpreter)
	{
		final AvailObject result = interpreter.runFunctionArguments(
			object.requiresBlock(),
			argTypes);
		// Make sure this is a valid Avail boolean, convert it to a Java
		// boolean, and return it.
		return result.extractBoolean();
	}

	@Override
	public @NotNull AvailObject o_BodySignature (
		final @NotNull AvailObject object)
	{
		return object.signature();
	}

	@Override
	public @NotNull AvailObject o_RequiresBlock (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.REQUIRES_BLOCK);
	}

	@Override
	public @NotNull AvailObject o_ReturnsBlock (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.RETURNS_BLOCK);
	}

	@Override
	public @NotNull AvailObject o_Signature (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.SIGNATURE);
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		final int hash = object.signature().hash() * 19
			+ object.requiresBlock().hash() * 37
			+ object.returnsBlock().hash() * 131;
		return hash;
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
	 *            static result type of the call.  The return value (from a
	 *            non-abstract, more specific implementation) is checked against
	 *            this expected return type at runtime.
	 * @return
	 *            An abstract method signature.
	 */
	public static AvailObject create (
		final @NotNull AvailObject bodySignature,
		final @NotNull AvailObject requiresBlock,
		final @NotNull AvailObject returnsBlock)
	throws SignatureException
	{
		final AvailObject instance = mutable().create();
		instance.objectSlotPut(ObjectSlots.SIGNATURE, bodySignature);
		instance.objectSlotPut(ObjectSlots.REQUIRES_BLOCK, requiresBlock);
		instance.objectSlotPut(ObjectSlots.RETURNS_BLOCK, returnsBlock);
		instance.makeImmutable();
		instance.ensureMetacovariant();
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
