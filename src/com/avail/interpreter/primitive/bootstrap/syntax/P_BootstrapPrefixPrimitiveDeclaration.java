/**
 * P_BootstrapPrefixPrimitiveDeclaration.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;

import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import javax.annotation.Nullable;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.interpreter.*;

/**
 * The {@code P_BootstrapPrefixVariableDeclaration} primitive is used for
 * bootstrapping declaration of a primitive declaration, including an optional
 * {@link DeclarationKind#PRIMITIVE_FAILURE_REASON primitive failure variable}
 * which holds the reason for a primitive's failure.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapPrefixPrimitiveDeclaration
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_BootstrapPrefixPrimitiveDeclaration().init(
			2, CanInline, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Phrase optionalBlockArgumentsList = args.get(0);
		final A_Phrase optionalPrimPhrase = args.get(1);

		final AvailLoader loader = interpreter.fiber().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		assert optionalPrimPhrase.expressionsSize() == 1;
		final A_Phrase primPhrase = optionalPrimPhrase.expressionAt(1);
		final A_Phrase primNamePhrase = primPhrase.expressionAt(1);
		if (!primNamePhrase.parseNodeKindIsUnder(LITERAL_NODE))
		{
			throw new AvailRejectedParseException(
				"primitive specification to be a (compiler created) literal "
				+ "keyword token");
		}
		final A_String primName = primNamePhrase.token().string();
		final @Nullable Primitive prim =
			Primitive.byName(primName.asNativeString());
		if (prim == null)
		{
			throw new AvailRejectedParseException(
				"a supported primitive name, not %s",
				primName);
		}

		// Check that the primitive signature agrees with the arguments.
		final List<A_Phrase> blockArgumentPhrases = new ArrayList<>();
		if (optionalBlockArgumentsList.expressionsSize() == 1)
		{
			final A_Phrase blockArgumentsList =
				optionalBlockArgumentsList.expressionAt(1);
			assert blockArgumentsList.expressionsSize() >= 1;
			for (final A_Phrase pair : blockArgumentsList.expressionsTuple())
			{
				assert pair.expressionsSize() == 2;
				final A_Phrase namePhrase = pair.expressionAt(1);
				final A_String name = namePhrase.token().literal().string();
				assert name.isString();
				final A_Phrase declaration =
					FiberDescriptor.lookupBindingOrNull(name);
				assert declaration != null;
				blockArgumentPhrases.add(declaration);
			}
		}
		final @Nullable String problem =
			Primitive.validatePrimitiveAcceptsArguments(
				prim.primitiveNumber, blockArgumentPhrases);
		if (problem != null)
		{
			throw new AvailRejectedParseException(problem);
		}

		// The section marker occurs inside the optionality of the primitive
		// clause, but outside the primitive failure variable declaration.
		// Therefore, the variable declaration is not required to be present.
		// Make sure the failure variable is present exactly when it should be.
		final A_Phrase optionalFailure = primPhrase.expressionAt(2);
		if (optionalFailure.expressionsSize() == 1)
		{
			if (prim.hasFlag(CannotFail))
			{
				throw new AvailRejectedParseException(
					"no primitive failure variable declaration for this "
					+ "infallible primitive");
			}
			final A_Phrase failurePair = optionalFailure.expressionAt(1);
			assert failurePair.expressionsSize() == 2;
			final A_Phrase failureNamePhrase = failurePair.expressionAt(1);
			final A_Token failureName = failureNamePhrase.token().literal();
			if (failureName.tokenType() != TokenType.KEYWORD)
			{
				throw new AvailRejectedParseException(
					"primitive failure variable name to be alphanumeric");
			}
			final A_Phrase failureTypePhrase = failurePair.expressionAt(2);
			final A_Type failureType = failureTypePhrase.token().literal();
			if (failureType.isBottom() || failureType.isTop())
			{
				throw new AvailRejectedParseException(
					"primitive failure variable type not to be " + failureType);
			}
			final A_Phrase failureDeclaration =
				DeclarationNodeDescriptor.newPrimitiveFailureVariable(
					failureName, failureTypePhrase, failureType);
			final A_Phrase conflictingDeclaration =
				FiberDescriptor.addDeclaration(failureDeclaration);
			if (conflictingDeclaration != null)
			{
				throw new AvailRejectedParseException(
					"primitive failure variable %s to have a name that doesn't "
					+ "shadow an existing %s (from line %d)",
					failureName.string(),
					conflictingDeclaration.declarationKind().nativeKindName(),
					conflictingDeclaration.token().lineNumber());
			}
			return interpreter.primitiveSuccess(NilDescriptor.nil());
		}
		if (!prim.hasFlag(CannotFail))
		{
			throw new AvailRejectedParseException(
				"a primitive failure variable declaration for this "
				+ "fallible primitive");
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.functionType(
			TupleDescriptor.tuple(
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional arguments section. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Arguments are present. */
						TupleTypeDescriptor.oneOrMoreOf(
							/* An argument. */
							TupleTypeDescriptor.tupleTypeForTypes(
								/* Argument name, a token. */
								TOKEN.o(),
								/* Argument type. */
								InstanceMetaDescriptor.anyMeta())))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional primitive declaration. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Primitive declaration */
						TupleTypeDescriptor.tupleTypeForTypes(
							/* Primitive number. */
							TOKEN.o(),
							/* Optional failure variable declaration. */
							TupleTypeDescriptor.zeroOrOneOf(
								/* Primitive failure variable parts. */
								TupleTypeDescriptor.tupleTypeForTypes(
									/* Primitive failure variable name token */
									TOKEN.o(),
									/* Primitive failure variable type */
									InstanceMetaDescriptor.anyMeta())))))),
			TOP.o());
	}
}
