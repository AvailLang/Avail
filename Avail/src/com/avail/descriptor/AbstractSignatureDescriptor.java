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
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.Interpreter;


/**
 * This is a specialization of {@link SignatureDescriptor} that is an abstract
 * declaration of an Avail method (i.e., no implementation).
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AbstractSignatureDescriptor extends SignatureDescriptor
{

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@link ClosureTypeDescriptor closure type} for which this
		 * signature is being specified.
		 */
		SIGNATURE,

		/**
		 * A closure that takes argument types at a call site and answers
		 * whether this is a legal call.  This is necessary because some usage
		 * conditions on methods, even though they may be inherently static,
		 * cannot be expressed solely in terms of a lattice of types.  Sometimes
		 * the relationship between the types of different arguments must also
		 * be taken into account.
		 */
		REQUIRES_BLOCK,

		/**
		 * A closure that maps argument types at a call site into a return type
		 * for that call.
		 */
		RETURNS_BLOCK
	}



	@Override
	public void o_BodySignatureRequiresBlockReturnsBlock (
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

	/**
	 * We simply run the 'returns' block, passing in the static argument types
	 * from the call site.
	 */
	@Override
	public AvailObject o_ComputeReturnTypeFromArgumentTypesInterpreter (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final Interpreter anAvailInterpreter)
	{
		final AvailObject result = anAvailInterpreter.runClosureArguments(object.returnsBlock(), argTypes);
		if (!result.isSubtypeOf(object.bodySignature().returnType()))
		{
			error("The 'returns' block should produce a type more specific than the body's basic return type", object);
			return VoidDescriptor.voidObject();
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
			final AvailObject object,
			final List<AvailObject> argTypes,
			final Interpreter interpreter)
	{
		final AvailObject result = interpreter.runClosureArguments(
			object.requiresBlock(),
			argTypes);
		// Make sure this is a valid Avail boolean, convert it to a Java
		// boolean, and return it.
		return result.extractBoolean();
	}

	@Override
	public AvailObject o_BodySignature (
			final AvailObject object)
	{
		return object.signature();
	}



	// GENERATED accessors

	/**
	 * Setter for field requiresBlock.
	 */
	@Override
	public void o_RequiresBlock (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.REQUIRES_BLOCK, value);
	}

	/**
	 * Setter for field returnsBlock.
	 */
	@Override
	public void o_ReturnsBlock (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.RETURNS_BLOCK, value);
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
	 * Getter for field requiresBlock.
	 */
	@Override
	public AvailObject o_RequiresBlock (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.REQUIRES_BLOCK);
	}

	/**
	 * Getter for field returnsBlock.
	 */
	@Override
	public AvailObject o_ReturnsBlock (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.RETURNS_BLOCK);
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



	// operations

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		return Types.ABSTRACT_SIGNATURE.o();
	}

	@Override
	public int o_Hash (
			final AvailObject object)
	{
		final int hash = object.signature().hash() * 19
			+ object.requiresBlock().hash() * 37
			+ object.returnsBlock().hash() * 131;
		return hash;
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		return Types.ABSTRACT_SIGNATURE.o();
	}



	// testing

	@Override
	public boolean o_IsAbstract (
			final AvailObject object)
	{
		return true;
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
