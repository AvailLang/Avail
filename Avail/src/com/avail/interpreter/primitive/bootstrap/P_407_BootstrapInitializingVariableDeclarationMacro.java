/**
 * P_407_BootstrapInitializingVariableDeclarationMacro.java
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

import static com.avail.descriptor.TokenDescriptor.TokenType.KEYWORD;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * The {@code P_407_BootstrapInitializingVariableDeclarationMacro} primitive is
 * used for bootstrapping declaration of a {@link #LOCAL_VARIABLE_NODE local
 * variable} (without an initializing expression).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_407_BootstrapInitializingVariableDeclarationMacro
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_407_BootstrapInitializingVariableDeclarationMacro().init(
			3, CannotFail, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_Phrase variableNameLiteral = args.get(0);
		final A_Phrase typeLiteral = args.get(1);
		final A_Phrase initializationExpression = args.get(2);

		final A_Token nameToken = variableNameLiteral.token().literal();
		final A_String nameString = nameToken.string();
		if (nameToken.tokenType() != KEYWORD)
		{
			throw new AvailRejectedParseException(
				"new variable name to be alphanumeric, not %s",
				nameString);
		}
		final A_Type type = typeLiteral.token().literal();
		if (type.isTop() || type.isBottom())
		{
			throw new AvailRejectedParseException(
				"variable's declared type to be something other than %s",
				type);
		}
		final A_Type initializationType =
			initializationExpression.expressionType();
		if (initializationType.isTop() || initializationType.isBottom())
		{
			throw new AvailRejectedParseException(
				"initialization expression to have a type other than %s",
				initializationType);
		}
		if (!initializationType.isSubtypeOf(type))
		{
			throw new AvailRejectedParseException(
				StringDescriptor.format(
					"initialization expression's type (%s) "
					+ "to match variable type (%s)",
					initializationType,
					type));
		}
		final A_Phrase variableDeclaration =
			DeclarationNodeDescriptor.newVariable(
				nameToken, type, initializationExpression);
		final A_Phrase conflictingDeclaration =
			FiberDescriptor.addDeclaration(variableDeclaration);
		if (conflictingDeclaration != null)
		{
			throw new AvailRejectedParseException(
				"local variable %s to have a name that doesn't shadow an "
				+ "existing %s (from line %d)",
				nameString,
				conflictingDeclaration.declarationKind().nativeKindName(),
				conflictingDeclaration.token().lineNumber());
		}
		return interpreter.primitiveSuccess(variableDeclaration);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Variable name token */
				LITERAL_NODE.create(TOKEN.o()),
				/* Variable type */
				LITERAL_NODE.create(InstanceMetaDescriptor.anyMeta()),
				/* Initialization expression */
				EXPRESSION_NODE.mostGeneralType()),
			LOCAL_VARIABLE_NODE.mostGeneralType());
	}
}
