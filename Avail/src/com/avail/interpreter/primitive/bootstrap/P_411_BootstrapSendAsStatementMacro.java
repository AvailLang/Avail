/**
 * P_411_BootstrapSendAsStatementMacro.java
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

package com.avail.interpreter.primitive.bootstrap;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.annotations.Nullable;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * The {@code P_411_BootstrapSendAsStatementMacro} primitive is used to allow
 * message sends producing ⊤ to be used as statements, by wrapping them inside
 * {@linkplain ExpressionAsStatementNodeDescriptor expression-as-statement
 * phrases}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_411_BootstrapSendAsStatementMacro
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_411_BootstrapSendAsStatementMacro().init(
			1, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Phrase sendPhraseInLiteral = args.get(0);

		final @Nullable AvailLoader loader = interpreter.fiber().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		final A_Phrase sendPhrase = sendPhraseInLiteral.token().literal();
		if (!sendPhrase.parseNodeKindIsUnder(SEND_NODE))
		{
			throw new AvailRejectedParseException(
				"statement to be a ⊤-valued send node, not a %s: %s",
				sendPhrase.parseNodeKind().name(),
				sendPhrase);
		}
		if (!sendPhrase.expressionType().isTop())
		{
			throw new AvailRejectedParseException(
				"statement's type to be ⊤, but it was %s",
				sendPhrase.expressionType());
		}
		final A_Phrase sendAsStatement =
			ExpressionAsStatementNodeDescriptor.fromExpression(sendPhrase);
		return interpreter.primitiveSuccess(sendAsStatement);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* The send node to treat as a statement */
				LITERAL_NODE.create(SEND_NODE.mostGeneralType())),
			EXPRESSION_AS_STATEMENT_NODE.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstance(
			E_LOADING_IS_OVER.numericCode());
	}
}
