/**
 * P_BootstrapPrefixLabelDeclaration.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.interpreter.*;

/**
 * The {@code P_BootstrapPrefixLabelDeclaration} primitive is used
 * for bootstrapping declaration of a {@link DeclarationKind#LABEL label}.
 * The label indicates a way to restart or exit a block, so it's probably best
 * if Avail's block syntax continues to constrain this to occur at the start of
 * a block.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapPrefixLabelDeclaration
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_BootstrapPrefixLabelDeclaration().init(
			3, CannotFail, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_Phrase optionalBlockArgumentsList = args.get(0);
//		final A_Phrase optionalPrimFailurePhrase = args.get(1);
		final A_Phrase optionalLabelPhrase = args.get(2);

		final AvailLoader loader = interpreter.fiber().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}

		// Note that because the section marker occurs inside the optionality
		// of the label declaration, this function will only be invoked when
		// there truly is a label declaration.
		assert optionalLabelPhrase.expressionsSize() == 1;
		final A_Phrase labelPairPhrase = optionalLabelPhrase.expressionAt(1);
		assert labelPairPhrase.expressionsSize() == 2;
		final A_Phrase labelNamePhrase = labelPairPhrase.expressionAt(1);
		final A_Token labelName = labelNamePhrase.token().literal();
		if (labelName.tokenType() != TokenType.KEYWORD)
		{
			throw new AvailRejectedParseException(
				"label name to be alphanumeric");
		}
		final A_Phrase optionalLabelReturnTypePhrase =
			labelPairPhrase.expressionAt(2);
		final A_Type labelReturnType;
		if (optionalLabelReturnTypePhrase.expressionsSize() == 1)
		{
			final A_Phrase labelReturnTypePhrase =
				optionalLabelReturnTypePhrase.expressionAt(1);
			assert labelReturnTypePhrase.parseNodeKindIsUnder(
				LITERAL_NODE);
			labelReturnType = labelReturnTypePhrase.token().literal();
		}
		else
		{
			// If the label doesn't specify a return type, use bottom.  Because
			// of continuation return type contravariance, this is the most
			// general answer.
			labelReturnType = BottomTypeDescriptor.bottom();
		}

		// Re-extract all the argument types so we can specify the exact type of
		// the continuation.
		final List<A_Type> blockArgumentTypes = new ArrayList<>();
		if (optionalBlockArgumentsList.expressionsSize() > 0)
		{
			assert optionalBlockArgumentsList.expressionsSize() == 1;
			final A_Phrase blockArgumentsList =
				optionalBlockArgumentsList.expressionAt(1);
			assert blockArgumentsList.expressionsSize() >= 1;
			for (final A_Phrase argumentPair :
				blockArgumentsList.expressionsTuple())
			{
				assert argumentPair.expressionsSize() == 2;
				final A_Phrase typePhrase = argumentPair.expressionAt(2);
				assert typePhrase.isInstanceOfKind(
					LITERAL_NODE.create(InstanceMetaDescriptor.anyMeta()));
				final A_Type argType = typePhrase.token().literal();
				assert argType.isType();
				blockArgumentTypes.add(argType);
			}
		}
		final A_Type functionType = FunctionTypeDescriptor.create(
			TupleDescriptor.fromList(blockArgumentTypes), labelReturnType);
		final A_Type continuationType =
			ContinuationTypeDescriptor.forFunctionType(functionType);
		final A_Phrase labelDeclaration =
			DeclarationNodeDescriptor.newLabel(labelName, continuationType);
		final A_Phrase conflictingDeclaration =
			FiberDescriptor.addDeclaration(labelDeclaration);
		if (conflictingDeclaration != null)
		{
			throw new AvailRejectedParseException(
				"label declaration %s to have a name that doesn't "
				+ "shadow an existing %s (from line %d)",
				labelName.string(),
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
								InstanceMetaDescriptor.anyMeta())))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional primitive declaration. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Primitive declaration */
						TupleTypeDescriptor.forTypes(
							/* Primitive name. */
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
								InstanceMetaDescriptor.topMeta()))))),
			TOP.o());
	}
}
