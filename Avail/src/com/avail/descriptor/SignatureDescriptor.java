/**
 * descriptor/SignatureDescriptor.java
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
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.exceptions.*;
import com.avail.exceptions.ArithmeticException;
import com.avail.interpreter.Interpreter;
import com.avail.utility.*;

public abstract class SignatureDescriptor
extends Descriptor
{

	@Override
	public abstract
	@NotNull AvailObject o_ComputeReturnTypeFromArgumentTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull AvailObject impSet,
		final @NotNull Interpreter anAvailInterpreter,
		final Continuation1<Generator<String>> failBlock);

	@Override
	public abstract boolean o_IsValidForArgumentTypesInterpreter (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull Interpreter interpreter);

	@Override
	public abstract @NotNull AvailObject o_BodySignature (
		final @NotNull AvailObject object);

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compare by address (identity) for now. Eventually we can introduce value
	 * semantics.
	 * </p>
	 */
	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override
	public abstract int o_Hash (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object);

	@Override
	public boolean o_IsAbstract (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	public boolean o_IsForward (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	public boolean o_IsMethod (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	public boolean o_IsMacro (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	public void o_EnsureMetacovariant (
		final @NotNull AvailObject object)
	throws SignatureException
	{
		// Make sure my requires clauses and returns clauses are expecting the
		// right types, based on the declaration of the body.  Defaulted here,
		// but subclasses may need to override.

		final AvailObject sig = object.bodySignature();
		final AvailObject req = object.requiresBlock();
		final AvailObject ret = object.returnsBlock();
		final int numArgs = req.code().numArgs();
		assert IntegerDescriptor.fromInt(numArgs).kind().equals(
			sig.argsTupleType().sizeRange())
			: "Wrong number of arguments in requires block";
		assert ret.code().numArgs() == numArgs
			: "Wrong number of arguments in returns block.";
		assert req.kind().returnType().isSubtypeOf(
				UnionTypeDescriptor.booleanObject())
			: "Wrong return type in requires block";
		assert ret.kind().returnType().isSubtypeOf(TYPE.o())
			: "Wrong return type in returns block";
		for (int i = 1, end = numArgs; i <= end; i++)
		{
			final AvailObject bodyType = sig.argsTupleType().typeAtIndex(i);
			final AvailObject bodyMeta = InstanceTypeDescriptor.on(
				bodyType);
			if (!bodyMeta.isSubtypeOf(
				req.kind().argsTupleType().typeAtIndex(i)))
			{
				throw new SignatureException(
					E_REQUIRES_CLAUSE_ARGUMENTS_ARE_NOT_METACOVARIANT);
			}
			if (!bodyMeta.isSubtypeOf(
				ret.kind().argsTupleType().typeAtIndex(i)))
			{
				throw new SignatureException(
					E_RETURNS_CLAUSE_ARGUMENTS_ARE_NOT_METACOVARIANT);
			}
		}
	}

	/**
	 * Construct a new {@link SignatureDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected SignatureDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}
}
