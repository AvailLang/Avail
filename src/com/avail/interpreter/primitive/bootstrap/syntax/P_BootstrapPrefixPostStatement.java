/*
 * P_BootstrapPrefixPostStatement.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.bootstrap.syntax;

import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.STATEMENT_PHRASE;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.TOKEN;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER;
import static com.avail.interpreter.Primitive.Flag.Bootstrap;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * The {@code P_BootstrapPrefixPostStatement} primitive is used for
 * ensuring that statements are top-valued before over-parsing.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapPrefixPostStatement extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_BootstrapPrefixPostStatement().init(
			4, CanInline, Bootstrap);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(4);
//		final A_Phrase blockArgumentsPhrase = interpreter.argument(0);
//		final A_Phrase optionalPrimFailurePhrase = interpreter.argument(1);
//		final A_Phrase optionalLabelPhrase = interpreter.argument(2);
		final A_Phrase statementsPhrase = interpreter.argument(3);

		final @Nullable AvailLoader loader = interpreter.availLoaderOrNull();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}

		// At this point the statements so far are a list phrase – not a sequence.
		final int statementCountSoFar = statementsPhrase.expressionsSize();
		// The section marker is inside the repetition, so this primitive could
		// only be invoked if there is at least one statement.
		assert statementCountSoFar > 0;
		final A_Phrase latestStatementLiteral =
			statementsPhrase.expressionAt(statementCountSoFar);
		final A_Phrase latestStatement =
			latestStatementLiteral.token().literal();
		if (!latestStatement.expressionType().equals(TOP.o()))
		{
			throw new AvailRejectedParseException(
				"statement to have type ⊤");
		}
		return interpreter.primitiveSuccess(nil);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
						/* Optional arguments section. */
					zeroOrOneOf(
							/* Arguments are present. */
						oneOrMoreOf(
								/* An argument. */
							tupleTypeForTypes(
									/* Argument name, a token. */
								TOKEN.o(),
									/* Argument type. */
								anyMeta())))),
					/* Macro argument is a phrase. */
				LIST_PHRASE.create(
						/* Optional primitive declaration. */
					zeroOrOneOf(
							/* Primitive declaration */
						tupleTypeForTypes(
								/* Primitive name. */
							TOKEN.o(),
								/* Optional failure variable declaration. */
							zeroOrOneOf(
									/* Primitive failure variable parts. */
								tupleTypeForTypes(
										/* Primitive failure variable name token */
									TOKEN.o(),
										/* Primitive failure variable type */
									anyMeta()))))),
					/* Macro argument is a phrase. */
				LIST_PHRASE.create(
						/* Optional label declaration. */
					zeroOrOneOf(
							/* Label parts. */
						tupleTypeForTypes(
								/* Label name */
							TOKEN.o(),
								/* Optional label return type. */
							zeroOrOneOf(
									/* Label return type. */
								topMeta())))),
					/* Macro argument is a phrase. */
				LIST_PHRASE.create(
						/* Statements and declarations so far. */
					zeroOrMoreOf(
							/* The "_!" mechanism wrapped each statement or
							 * declaration inside a literal phrase, so expect a
							 * phrase here instead of TOP.o().
							 */
						STATEMENT_PHRASE.mostGeneralType()))),
			TOP.o());
	}
}
