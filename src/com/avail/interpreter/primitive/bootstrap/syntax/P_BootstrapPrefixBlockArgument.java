/**
 * P_BootstrapPrefixBlockArgument.java
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

package com.avail.interpreter.primitive.bootstrap.syntax;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.Bootstrap;
import static com.avail.interpreter.Primitive.Flag.CanInline;

import java.util.List;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

/**
 * The {@code P_BootstrapPrefixBlockArgument} primitive is used as a prefix
 * function for bootstrapping argument declarations within a {@link
 * P_BootstrapBlockMacro block}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapPrefixBlockArgument extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_BootstrapPrefixBlockArgument().init(
			1, CanInline, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Phrase optionalBlockArgumentsList = args.get(0);

		final AvailLoader loader = interpreter.fiber().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}

		assert optionalBlockArgumentsList.expressionsSize() == 1;
		final A_Phrase blockArgumentsList =
			optionalBlockArgumentsList.expressionAt(1);
		assert blockArgumentsList.expressionsSize() >= 1;
		final A_Phrase lastPair = blockArgumentsList.expressionAt(
			blockArgumentsList.expressionsSize());
		assert lastPair.expressionsSize() == 2;
		final A_Phrase namePhrase = lastPair.expressionAt(1);
		final A_Phrase typePhrase = lastPair.expressionAt(2);

		assert namePhrase.isInstanceOfKind(LITERAL_NODE.create(TOKEN.o()));
		assert typePhrase.isInstanceOfKind(
			LITERAL_NODE.create(InstanceMetaDescriptor.anyMeta()));
		final A_Token outerArgToken = namePhrase.token();
		final A_Token argToken = outerArgToken.literal();
		final A_String argName = argToken.string();

		if (argToken.tokenType() != TokenType.KEYWORD)
		{
			throw new AvailRejectedParseException(
				"argument name to be alphanumeric, not %s",
				argName);
		}
		final A_Type argType = typePhrase.token().literal();
		assert argType.isType();
		if (argType.isBottom())
		{
			throw new AvailRejectedParseException(
				"block parameter type not to be ⊥");
		}

		final A_Phrase argDeclaration =
			DeclarationNodeDescriptor.newArgument(
				argToken, argType, typePhrase);
		// Add the binding and we're done.
		final A_Phrase conflictingDeclaration =
			FiberDescriptor.addDeclaration(argDeclaration);
		if (conflictingDeclaration != null)
		{
			throw new AvailRejectedParseException(
				"block argument declaration %s to have a name that doesn't "
				+ "shadow an existing %s (from line %d)",
				argName,
				conflictingDeclaration.declarationKind().nativeKindName(),
				conflictingDeclaration.token().lineNumber());
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
								InstanceMetaDescriptor.anyMeta()))))),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
				E_LOADING_IS_OVER));
	}
}
