/**
 * P_BootstrapConstantDeclarationMacro.java
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
import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;

import com.avail.compiler.AvailCompiler;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.interpreter.*;

/**
 * The {@code P_BootstrapConstantDeclarationMacro} primitive is used for
 * bootstrapping declaration of a {@link ParseNodeKind#LOCAL_CONSTANT_NODE local
 * constant declaration}.  Constant declarations that occur at the outermost
 * scope are rewritten by the {@link AvailCompiler} as a {@link ParseNodeKind
 * #MODULE_CONSTANT_NODE}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapConstantDeclarationMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_BootstrapConstantDeclarationMacro().init(
			2, CannotFail, CanInline, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Phrase constantNameLiteral = args.get(0);
		final A_Phrase initializationExpression = args.get(1);

		final A_Token nameToken = constantNameLiteral.token().literal();
		final A_String nameString = nameToken.string();
		if (nameToken.tokenType() != KEYWORD)
		{
			throw new AvailRejectedParseException(
				"new constant name to be alphanumeric, not %s",
				nameString);
		}
		final A_Type initializationType =
			initializationExpression.expressionType();
		if (initializationType.isTop() || initializationType.isBottom())
		{
			throw new AvailRejectedParseException(
				"constant initialization expression to have a type other "
				+ "than %s",
				initializationType);
		}
		final A_Phrase constantDeclaration =
			DeclarationNodeDescriptor.newConstant(
				nameToken, initializationExpression);
		final A_Phrase conflictingDeclaration =
			FiberDescriptor.addDeclaration(constantDeclaration);
		if (conflictingDeclaration != null)
		{
			throw new AvailRejectedParseException(
				"local constant %s to have a name that doesn't shadow an "
				+ "existing %s (from line %d)",
				nameString,
				conflictingDeclaration.declarationKind().nativeKindName(),
				conflictingDeclaration.token().lineNumber());
		}
		return interpreter.primitiveSuccess(constantDeclaration);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Constant name token as a literal node */
				LITERAL_NODE.create(TOKEN.o()),
				/* Initialization expression */
				EXPRESSION_NODE.create(ANY.o())),
			LOCAL_CONSTANT_NODE.mostGeneralType());
	}
}
