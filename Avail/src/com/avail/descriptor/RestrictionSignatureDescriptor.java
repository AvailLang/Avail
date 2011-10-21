/**
 * descriptor/RestrictionSignatureDescriptor.java
 * Copyright (c) 2011, Mark van Gulik.
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.exceptions.*;
import com.avail.interpreter.*;
import com.avail.utility.*;

/**
 * A restriction signature is a signature (without a body) that is used to
 * specify a type-strengthening property at some position in the multimethod
 * signature hierarchy.  It specifies a function that operates on the static
 * types of the arguments, and either rejects the parsing (via the {@linkplain
 * Primitive#prim352_RejectParsing reject parsing primitive}) or returns the
 * type of the result of the call.  At runtime, the value produced by the call
 * is checked against this strengthened type, raising an exception if it does
 * not agree.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class RestrictionSignatureDescriptor
extends SignatureDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The argument types for which this restriction applies.
		 */
		BODY_SIGNATURE,

		/**
		 * The function to evaluate at link time, specifically the compilation
		 * time of a call site.  The function is passed the static types of the
		 * arguments that will be provided at runtime.  It should return a type
		 * indicating the return type at the call site.
		 *
		 * <p>
		 * Additionally, the function may invoke the {@linkplain
		 * Primitive#prim352_RejectParsing parsing rejection primitive} to
		 * indicate the argument types are mutually incompatible.  This is a
		 * more powerful mechanism (by far) than F-bounded polymorphic types,
		 * for the same kind of reason that parametric types are vastly more
		 * expressive than (compile-time only) generic types.
		 * </p>
		 *
		 * <p>
		 * Note that the function must accept
		 */
		RESTRICTION_FUNCTION,
	}

	@Override
	public AvailObject o_BodySignature (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.BODY_SIGNATURE);
	}

	@Override
	public AvailObject o_ComputeReturnTypeFromArgumentTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull AvailObject impSet,
		final @NotNull Interpreter interpreter,
		final Continuation1<Generator<String>> failBlock)
	{
		// We simply run the restriction block, passing in the static argument
		// types from the call site.
		final AvailObject result = interpreter.runFunctionArguments(
			object.objectSlot(ObjectSlots.RESTRICTION_FUNCTION),
			argTypes);
		// Restriction clauses are now independent of method signatures, so the
		// return type is unconstrained by other signatures.
		assert result.isSubtypeOf(TYPE.o());
		return result;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Make sure my {@linkplain ObjectSlots#RESTRICTION_FUNCTION restriction
	 * function} accepts anything to which the {@linkplain
	 * ObjectSlots#BODY_SIGNATURE body signature} applies.
	 */
	@Override
	public void o_EnsureMetacovariant (
		final @NotNull AvailObject object)
	throws SignatureException
	{
		final AvailObject signature =
			object.objectSlot(ObjectSlots.BODY_SIGNATURE);
		final AvailObject restrictionFunction =
			object.objectSlot(ObjectSlots.RESTRICTION_FUNCTION);
		final AvailObject signatureArgsTupleType = signature.argsTupleType();
		final AvailObject functionArgsTupleType =
			restrictionFunction.kind().argsTupleType();
		final int numArgs = restrictionFunction.code().numArgs();
		assert IntegerDescriptor.fromInt(numArgs).kind().equals(
				signatureArgsTupleType.sizeRange())
			: "Wrong number of arguments in restriction signature";
		assert restrictionFunction.kind().returnType().isSubtypeOf(TYPE.o())
			: "Wrong return type in restriction block";
		for (int i = 1; i <= numArgs; i++)
		{
			final AvailObject functionArgType =
				functionArgsTupleType.typeAtIndex(i);
			final AvailObject signatureArgType =
				signatureArgsTupleType.typeAtIndex(i);
			// The signature takes anything, but the function takes its type.
			if (!signatureArgType.isInstanceOf(functionArgType))
			{
				throw new SignatureException(
					E_REQUIRES_CLAUSE_ARGUMENTS_ARE_NOT_METACOVARIANT);
			}
		}
	}

	@Override
	public boolean o_IsMethod (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	public boolean o_IsValidForArgumentTypesInterpreter (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull Interpreter interpreter)
	{
		//  We simply run the 'requires' block, passing in the static arguments types from the call site.  The result of
		//  the 'requires' block is an Avail boolean, which we convert before answering it.

		try
		{
			@SuppressWarnings("unused")
			final AvailObject result = interpreter.runFunctionArguments(
				object.objectSlot(ObjectSlots.RESTRICTION_FUNCTION),
				argTypes);
		}
		catch (final AvailRejectedParseException e)
		{
			return false;
		}
		return true;
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		int hash = object.objectSlot(ObjectSlots.RESTRICTION_FUNCTION).hash();
		hash += 0x647B829A;
		hash ^= 0x157A87DF;
		return hash;
	}

	@Override
	public AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return RESTRICTION_SIGNATURE.o();
	}

	/**
	 * Create a new restriction signature from the provided arguments.
	 *
	 * @param restrictionFunction
	 *            The function to invoke to check the restriction.  The
	 *            function's argument types determine when the restriction must
	 *            apply.
	 * @return
	 *            A restriction signature.
	 */
	public static AvailObject create (
		final @NotNull AvailObject bodySignature,
		final @NotNull AvailObject restrictionFunction)
	throws SignatureException
	{
		final AvailObject instance = mutable().create();
		instance.objectSlotPut(
			ObjectSlots.BODY_SIGNATURE,
			bodySignature);
		instance.objectSlotPut(
			ObjectSlots.RESTRICTION_FUNCTION,
			restrictionFunction);
		instance.makeImmutable();
		instance.ensureMetacovariant();
		return instance;
	}


	/**
	 * Construct a new {@link RestrictionSignatureDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected RestrictionSignatureDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link RestrictionSignatureDescriptor}.
	 */
	private final static RestrictionSignatureDescriptor mutable = new RestrictionSignatureDescriptor(true);

	/**
	 * Answer the mutable {@link RestrictionSignatureDescriptor}.
	 *
	 * @return The mutable {@link RestrictionSignatureDescriptor}.
	 */
	public static RestrictionSignatureDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link RestrictionSignatureDescriptor}.
	 */
	private final static RestrictionSignatureDescriptor immutable = new RestrictionSignatureDescriptor(false);

	/**
	 * Answer the immutable {@link RestrictionSignatureDescriptor}.
	 *
	 * @return The immutable {@link RestrictionSignatureDescriptor}.
	 */
	public static RestrictionSignatureDescriptor immutable ()
	{
		return immutable;
	}
}
