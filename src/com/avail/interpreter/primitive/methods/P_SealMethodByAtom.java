/**
 * P_SealMethodByAtom.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.methods;

import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.MethodDescriptor;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.AvailLoader.Phase;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.compiler.splitter.MessageSplitter.possibleErrors;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor
	.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive:</strong> Seal the named {@linkplain MethodDescriptor
 * method} at the specified {@linkplain TupleTypeDescriptor signature}. No
 * further definitions may be added below this signature.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_SealMethodByAtom
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_SealMethodByAtom().init(
			2, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Atom methodName = args.get(0);
		final A_Tuple sealSignature = args.get(1);
		final @Nullable AvailLoader loader = interpreter.availLoaderOrNull();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		if (loader.phase() != Phase.EXECUTING)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION);
		}
		try
		{
			loader.addSeal(methodName, sealSignature);
		}
		catch (
			final MalformedMessageException
				| SignatureException e)
		{
			return interpreter.primitiveFailure(e);
		}
		return interpreter.primitiveSuccess(nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				ATOM.o(),
				tupleTypeForSizesTypesDefaultType(
					wholeNumbers(),
					emptyTuple(),
					anyMeta())),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION,
				E_INCORRECT_NUMBER_OF_ARGUMENTS
			).setUnionCanDestroy(possibleErrors, true));
	}
}
