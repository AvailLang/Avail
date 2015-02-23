/**
 * P_401_BootstrapPrefixPrimitiveFailureVariableDeclaration.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * The {@code P_401_BootstrapPrefixPrimitiveFailureVariableDeclaration}
 * primitive is used for bootstrapping declaration of a {@link
 * #PRIMITIVE_FAILURE_REASON_NODE primitive failure variable} which holds the
 * reason for a primitive's failure.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_401_BootstrapPrefixPrimitiveFailureVariableDeclaration
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_401_BootstrapPrefixPrimitiveFailureVariableDeclaration().init(
			2, CannotFail, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
//		final A_Phrase blockArgumentsPhrase = args.get(0);
		final A_Phrase optionalPrimFailurePhrase = args.get(1);

		// Note that because the section marker occurs inside the optionality
		// of the primitive failure variable declaration, this function will
		// only be invoked when there truly is a failure variable declaration.
		final A_Tuple primFailurePairs =
			optionalPrimFailurePhrase.expressionsTuple();
		assert primFailurePairs.tupleSize() == 1;
		final A_Phrase primFailurePairPhrase = primFailurePairs.tupleAt(1);
		final A_Tuple primFailurePair =
			primFailurePairPhrase.expressionsTuple();
		assert primFailurePair.tupleSize() == 2;
		final A_Phrase failureVariableNamePhrase = primFailurePair.tupleAt(1);
		final A_Phrase failureVariableTypePhrase = primFailurePair.tupleAt(2);
		final A_Token failureVariableName =
			failureVariableNamePhrase.token().literal();
		final A_Type failureVariableType =
			failureVariableTypePhrase.token().literal();
		if (failureVariableType.isBottom())
		{
			throw new AvailRejectedParseException(
				"primitive failure variable type not to be ⊥");
		}
		final A_Phrase failureDeclaration =
			DeclarationNodeDescriptor.newPrimitiveFailureVariable(
				failureVariableName,
				failureVariableType);
		failureDeclaration.makeImmutable();
		return interpreter.primitiveSuccess(failureDeclaration);
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
								/* Argument name, a literal node holding a
								 * synthetic token holding the real token.
								 */
								LITERAL_NODE.create(
									/* The outer synthetic literal token. */
									LiteralTokenTypeDescriptor.create(
										/* Inner original token. */
										TOKEN.o())),
								/* Argument type. */
								LITERAL_NODE.create(
									/* The synthetic literal token. */
									LiteralTokenTypeDescriptor.create(
										/* Holding the type. */
										InstanceMetaDescriptor.anyMeta())))))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional primitive failure variable declaration. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Primitive failure variable parts. */
						TupleTypeDescriptor.forTypes(
							/* Primitive failure variable name token */
							TOKEN.o(),
							/* Primitive failure variable type */
							LITERAL_NODE.create(
								/* The synthetic literal token. */
								LiteralTokenTypeDescriptor.create(
									/* Holding the failure var's type. */
									InstanceMetaDescriptor.anyMeta())))))),
			TOP.o());
	}
}
