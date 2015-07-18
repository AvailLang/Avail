/**
 * P_BootstrapAssignmentStatementMacro.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
import static com.avail.descriptor.TypeDescriptor.Types.TOKEN;
import static com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.annotations.Nullable;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.interpreter.*;
import com.avail.utility.Generator;

/**
 * The {@code P_BootstrapAssignmentStatementMacro} primitive is used for
 * assignment statements.  It constructs an expression-as-statement containing
 * an assignment that has the isInline flag cleared.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapAssignmentStatementMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_BootstrapAssignmentStatementMacro().init(
			2, CannotFail, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Phrase variableNameLiteral = args.get(0);
		final A_Phrase valueExpression = args.get(1);

		final @Nullable AvailLoader loader = interpreter.fiber().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		assert variableNameLiteral.isInstanceOf(LITERAL_NODE.mostGeneralType());
		final A_Token literalToken = variableNameLiteral.token();
		assert literalToken.tokenType() == TokenType.SYNTHETIC_LITERAL;
		final A_Token actualToken = literalToken.literal();
		assert actualToken.isInstanceOf(TOKEN.o());
		if (actualToken.tokenType() != TokenType.KEYWORD)
		{
			throw new AvailRejectedParseException(
				"variable name for assignment to be alphanumeric");
		}
		final A_String variableNameString = actualToken.string();
		final A_Map fiberGlobals = interpreter.fiber().fiberGlobals();
		final A_Map clientData = fiberGlobals.mapAt(
			AtomDescriptor.clientDataGlobalKey());
		final A_Map scopeMap =
			clientData.mapAt(AtomDescriptor.compilerScopeMapKey());
		final A_Module module = loader.module();
		A_Phrase declaration = null;
		if (scopeMap.hasKey(variableNameString))
		{
			declaration = scopeMap.mapAt(variableNameString);
		}
		else if (module.variableBindings().hasKey(variableNameString))
		{
			final A_BasicObject variableObject =
				module.variableBindings().mapAt(variableNameString);
			declaration = DeclarationNodeDescriptor.newModuleVariable(
				actualToken,
				variableObject,
				NilDescriptor.nil());
		}
		else if (module.constantBindings().hasKey(variableNameString))
		{
			final A_BasicObject variableObject =
				module.constantBindings().mapAt(variableNameString);
			declaration = DeclarationNodeDescriptor.newModuleConstant(
				actualToken,
				variableObject,
				NilDescriptor.nil());
		}

		if (declaration == null)
		{
			throw new AvailRejectedParseException(
				"variable for assignment to be in scope");
		}
		final A_Phrase declarationFinal = declaration;
		if (!declaration.declarationKind().isVariable())
		{
			throw new AvailRejectedParseException(
				new Generator<A_String>()
				{
					@Override
					public A_String value ()
					{
						return StringDescriptor.format(
							"a name of a variable, not a %s",
							declarationFinal.declarationKind()
								.nativeKindName());
					}
				});
		}
		if (!valueExpression.expressionType().isSubtypeOf(
			declaration.declaredType()))
		{
			throw new AvailRejectedParseException(
				new Generator<A_String>()
				{
					@Override
					public A_String value()
					{
						return StringDescriptor.format(
							"assignment expression's type (%s) "
							+ "to match variable type (%s)",
							valueExpression.expressionType(),
							declarationFinal.declaredType());
					}
				});
		}
		final A_Phrase assignment = AssignmentNodeDescriptor.from(
			VariableUseNodeDescriptor.newUse(actualToken, declaration),
			valueExpression,
			false);
		assignment.makeImmutable();
		final A_Phrase assignmentAsStatement =
			ExpressionAsStatementNodeDescriptor.fromExpression(assignment);
		return interpreter.primitiveSuccess(assignmentAsStatement);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Variable name for assignment */
				LITERAL_NODE.create(TOKEN.o()),
				/* Assignment value */
				EXPRESSION_NODE.mostGeneralType()),
			EXPRESSION_AS_STATEMENT_NODE.mostGeneralType());
	}
}
