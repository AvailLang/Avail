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

import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FiberDescriptor;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.DeclarationNodeDescriptor.newArgument;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind
	.LIST_NODE;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind
	.LITERAL_NODE;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.TOKEN;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER;
import static com.avail.interpreter.Primitive.Flag.Bootstrap;
import static com.avail.interpreter.Primitive.Flag.CanInline;

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

		final @Nullable AvailLoader loader = interpreter.availLoaderOrNull();
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
		assert typePhrase.isInstanceOfKind(LITERAL_NODE.create(anyMeta()));
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
			newArgument(argToken, argType, typePhrase);
		// Add the binding and we're done.
		final @Nullable A_Phrase conflictingDeclaration =
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
		return interpreter.primitiveSuccess(nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional arguments section. */
					zeroOrOneOf(
						/* Arguments are present. */
						oneOrMoreOf(
							/* An argument. */
							tupleTypeForTypes(
								/* Argument name, a token. */
								TOKEN.o(),
								/* Argument type. */
								anyMeta()))))),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_LOADING_IS_OVER));
	}
}
