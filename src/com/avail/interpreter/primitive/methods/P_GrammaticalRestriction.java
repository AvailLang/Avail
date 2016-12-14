/**
 * P_GrammaticalRestriction.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.Unknown;
import java.util.List;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.*;
import com.avail.exceptions.AmbiguousNameException;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.AvailLoader.Phase;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

/**
 * <strong>Primitive:</strong> Message precedence declaration with
 * {@linkplain TupleDescriptor tuple} of {@linkplain SetDescriptor sets} of
 * messages to exclude for each argument position. Note that the tuple's
 * elements should correspond with occurrences of underscore in the method
 * names, *not* with the (top-level) arguments of the method. This
 * distinction is only apparent when guillemet notation is used to accept
 * tuples of arguments.
 */
public final class P_GrammaticalRestriction
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_GrammaticalRestriction().init(
			2, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Set parentStrings = args.get(0);
		final A_Tuple excludedStringSets = args.get(1);
		final AvailLoader loader = interpreter.fiber().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		if (loader.phase() != Phase.EXECUTING)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION);
		}
		A_Tuple excludedAtomSets = excludedStringSets.makeShared();
		for (int i = excludedStringSets.tupleSize(); i >= 1; i--)
		{
			A_Set atomSet = SetDescriptor.empty();
			for (final A_String string : excludedStringSets.tupleAt(i))
			{
				try
				{
					atomSet = atomSet.setWithElementCanDestroy(
						loader.lookupName(string), true);
				}
				catch (final AmbiguousNameException e)
				{
					return interpreter.primitiveFailure(e);
				}
			}
			excludedAtomSets = excludedAtomSets.tupleAtPuttingCanDestroy(
				i, atomSet, true);
		}
		A_Set parentAtoms = SetDescriptor.empty();
		try
		{
			for (final A_String string : parentStrings)
			{
				parentAtoms = parentAtoms.setWithElementCanDestroy(
					loader.lookupName(string), true);
			}
			loader.addGrammaticalRestrictions(parentAtoms, excludedAtomSets);
		}
		catch (
			final MalformedMessageException
				| SignatureException
				| AmbiguousNameException e)
		{
			return interpreter.primitiveFailure(e);
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.naturalNumbers(),
					TupleTypeDescriptor.stringType()),
				TupleTypeDescriptor.zeroOrMoreOf(
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						TupleTypeDescriptor.stringType()))),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
					E_LOADING_IS_OVER,
					E_CANNOT_DEFINE_DURING_COMPILATION,
					E_AMBIGUOUS_NAME,
					E_INCORRECT_NUMBER_OF_ARGUMENTS)
				.setUnionCanDestroy(MessageSplitter.possibleErrors, true));
	}
}
