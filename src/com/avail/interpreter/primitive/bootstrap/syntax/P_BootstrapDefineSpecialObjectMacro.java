/**
 * P_BootstrapDefineSpecialObjectMacro.java
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
import static com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.exceptions.AmbiguousNameException;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive</strong>: Construct a method and an accompanying
 * literalizing macro that provide access to the specified special object.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_BootstrapDefineSpecialObjectMacro
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_BootstrapDefineSpecialObjectMacro().init(
			2, Bootstrap, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Phrase nameLiteral = args.get(0);
		final A_Phrase specialObjectLiteral = args.get(1);
		final A_Fiber fiber = interpreter.fiber();
		final AvailLoader loader = fiber.availLoader();
		if (loader == null || loader.module().equalsNil())
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		final A_Bundle bundle;
		try
		{
			final A_Atom trueName = loader.lookupName(
				nameLiteral.token().literal());
			bundle = trueName.bundleOrCreate();
		}
		catch (final AmbiguousNameException|MalformedMessageException e)
		{
			return interpreter.primitiveFailure(e);
		}
		// Create a send of the bootstrap method definer that, when actually
		// sent, will produce a method that answers the special object.
		final A_Phrase defineMethod = SendNodeDescriptor.from(
			TupleDescriptor.empty(),
			SpecialMethodAtom.METHOD_DEFINER.bundle,
			ListNodeDescriptor.newExpressions(
				TupleDescriptor.from(
					nameLiteral,
					BlockNodeDescriptor.newBlockNode(
						TupleDescriptor.empty(),
						0,
						TupleDescriptor.from(specialObjectLiteral),
						specialObjectLiteral.expressionType(),
						SetDescriptor.empty(),
						0))),
			TOP.o());
		// Create a send of the bootstrap macro definer that, when actually
		// sent, will produce a method that literalizes the special object.
		final A_Phrase getValue = SendNodeDescriptor.from(
			TupleDescriptor.empty(),
			bundle,
			ListNodeDescriptor.newExpressions(
				TupleDescriptor.empty()),
			specialObjectLiteral.expressionType());
		final A_Phrase createLiteralToken = SendNodeDescriptor.from(
			TupleDescriptor.empty(),
			SpecialMethodAtom.CREATE_LITERAL_TOKEN.bundle,
			ListNodeDescriptor.newExpressions(
				TupleDescriptor.from(
					getValue,
					LiteralNodeDescriptor.syntheticFrom(
						specialObjectLiteral.token().string()))),
			LiteralTokenTypeDescriptor.create(
				specialObjectLiteral.expressionType()));
		final A_Phrase createLiteralNode = SendNodeDescriptor.from(
			TupleDescriptor.empty(),
			SpecialMethodAtom.CREATE_LITERAL_PHRASE.bundle,
			ListNodeDescriptor.newExpressions(
				TupleDescriptor.from(
					createLiteralToken)),
			LITERAL_NODE.create(
				specialObjectLiteral.expressionType()));
		final A_Phrase defineMacro = SendNodeDescriptor.from(
			TupleDescriptor.empty(),
			SpecialMethodAtom.MACRO_DEFINER.bundle,
			ListNodeDescriptor.newExpressions(
				TupleDescriptor.from(
					nameLiteral,
					ListNodeDescriptor.empty(),
					BlockNodeDescriptor.newBlockNode(
						TupleDescriptor.empty(),
						0,
						TupleDescriptor.from(createLiteralNode),
						LITERAL_NODE.create(
							specialObjectLiteral.expressionType()),
						SetDescriptor.empty(),
						0))),
			TOP.o());
		return interpreter.primitiveSuccess(
			SequenceNodeDescriptor.newStatements(
				TupleDescriptor.from(
					ExpressionAsStatementNodeDescriptor.fromExpression(
						defineMethod),
					ExpressionAsStatementNodeDescriptor.fromExpression(
						defineMacro))));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				LITERAL_NODE.create(
					TupleTypeDescriptor.oneOrMoreOf(CHARACTER.o())),
				LITERAL_NODE.create(ANY.o())),
			SEQUENCE_NODE.mostGeneralType());
	}
}
