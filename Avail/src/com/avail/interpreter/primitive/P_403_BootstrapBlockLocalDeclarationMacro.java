/**
 * P_403_BootstrapBlockLocalDeclarationMacro.java
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

package com.avail.interpreter.primitive;

import static com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.*;

/**
 * The {@code P_403_BootstrapBlockLocalDeclarationMacro} primitive is used for
 * bootstrapping a {@linkplain DeclarationNodeDescriptor declaration} of a
 * {@link #LOCAL_VARIABLE} or {@link #LOCAL_CONSTANT} within a block.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_403_BootstrapBlockLocalDeclarationMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_403_BootstrapBlockLocalDeclarationMacro().init(
			4, Unknown, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 4;
//		final A_Phrase blockArgumentsPhrase = args.get(0);
//		final A_Phrase optionalPrimFailurePhrase = args.get(1);
//		final A_Phrase optionalLabelPhrase = args.get(2);
		final A_Phrase statementsPhrase = args.get(3);

		final AvailLoader loader = interpreter.fiber().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}

		// At this point the statements so far are a list node – not a sequence.
		final int statementCountSoFar = statementsPhrase.expressionsSize();
		// The section marker is inside the repetition, so this primitive could
		// only be invoked if there is at least one statement.
		assert statementCountSoFar > 0;
		final A_Phrase latestStatementLiteral =
			statementsPhrase.expressionAt(statementCountSoFar);
		final A_Phrase latestStatement =
			latestStatementLiteral.token().literal();
		if (!latestStatement.parseNodeKind().isSubkindOf(DECLARATION_NODE))
		{
			// This isn't a declaration, it's some other kind of statement.
			// For now, just ensure it has type ⊤.  The macro body will deal
			// with it later.
			if (!latestStatement.expressionType().equals(TOP.o()))
			{
				throw new AvailRejectedParseException(
					"statement to have type ⊤");
			}
			return interpreter.primitiveSuccess(NilDescriptor.nil());
		}
		final DeclarationKind declarationKind =
			latestStatement.declarationKind();
		if (declarationKind != LOCAL_CONSTANT
			&& declarationKind != LOCAL_VARIABLE)
		{
			// I don't know why there's a non-local declaration in here.
			throw new AvailRejectedParseException(
				"declaration to be a local variable or constant, not %s",
				declarationKind.name());
		}
		final AvailErrorCode error = loader.addDeclaration(latestStatement);
		if (error != null)
		{
			if (error == E_LOCAL_DECLARATION_SHADOWS_ANOTHER)
			{
				throw new AvailRejectedParseException(
					"local %s %s to have a name that doesn't shadow another"
					+ " local declaration",
					declarationKind == LOCAL_CONSTANT ? "constant" : "variable",
					latestStatement.token().string());
			}
			return interpreter.primitiveFailure(error);
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional arguments section. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Arguments are present. */
						TupleTypeDescriptor.oneOrMoreOf(
							/* An argument. */
							TupleTypeDescriptor.forTypes(
								/* Argument name, a token. */
								TOKEN.o(),
								/* Argument type. */
								InstanceMetaDescriptor.anyMeta())))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional primitive declaration. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Primitive declaration */
						TupleTypeDescriptor.forTypes(
							/* Primitive number. */
							TOKEN.o(),
							/* Optional failure variable declaration. */
							TupleTypeDescriptor.zeroOrOneOf(
								/* Primitive failure variable parts. */
								TupleTypeDescriptor.forTypes(
									/* Primitive failure variable name token */
									TOKEN.o(),
									/* Primitive failure variable type */
									InstanceMetaDescriptor.anyMeta()))))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional label declaration. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Label parts. */
						TupleTypeDescriptor.forTypes(
							/* Label name */
							TOKEN.o(),
							/* Optional label return type. */
							TupleTypeDescriptor.zeroOrOneOf(
								/* Label return type. */
								InstanceMetaDescriptor.topMeta())))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Statements and declarations so far. */
					TupleTypeDescriptor.zeroOrMoreOf(
						/* The "_!" mechanism wrapped each statement or
						 * declaration inside a literal phrase, so expect a
						 * phrase here instead of TOP.o().
						 */
						PARSE_NODE.mostGeneralType()))),
			TOP.o());
	}
}