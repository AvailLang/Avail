/*
 * P_BootstrapInitializingVariableDeclarationMacro.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
import com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind;
import com.avail.descriptor.FiberDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;

import static com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG;
import static com.avail.descriptor.DeclarationPhraseDescriptor.newVariable;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LOCAL_VARIABLE_PHRASE;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.TokenDescriptor.TokenType.KEYWORD;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.TOKEN;
import static com.avail.interpreter.Primitive.Flag.Bootstrap;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.CannotFail;

/**
 * The {@code P_BootstrapInitializingVariableDeclarationMacro} primitive is
 * used for bootstrapping declaration of a {@link
 * DeclarationKind#LOCAL_VARIABLE local variable}
 * with an initializing expression.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapInitializingVariableDeclarationMacro
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_BootstrapInitializingVariableDeclarationMacro().init(
			3, CanInline, CannotFail, Bootstrap);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
		final A_Phrase variableNameLiteral = interpreter.argument(0);
		final A_Phrase typeLiteral = interpreter.argument(1);
		final A_Phrase initializationExpression = interpreter.argument(2);

		final A_Token nameToken = variableNameLiteral.token().literal();
		final A_String nameString = nameToken.string();
		if (nameToken.tokenType() != KEYWORD)
		{
			throw new AvailRejectedParseException(
				STRONG,
				"new variable name to be alphanumeric, not %s",
				nameString);
		}
		final A_Type type = typeLiteral.token().literal();
		if (type.isTop() || type.isBottom())
		{
			throw new AvailRejectedParseException(
				STRONG,
				"variable's declared type to be something other than %s",
				type);
		}
		final A_Type initializationType =
			initializationExpression.expressionType();
		if (initializationType.isTop() || initializationType.isBottom())
		{
			throw new AvailRejectedParseException(
				STRONG,
				"initialization expression to have a type other than %s",
				initializationType);
		}
		if (!initializationType.isSubtypeOf(type))
		{
			throw new AvailRejectedParseException(
				STRONG,
				formatString("initialization expression's type (%s) "
						+ "to match variable type (%s)", initializationType,
					type));
		}
		final A_Phrase variableDeclaration =
			newVariable(
				nameToken, type, typeLiteral, initializationExpression);
		final @Nullable A_Phrase conflictingDeclaration =
			FiberDescriptor.addDeclaration(variableDeclaration);
		if (conflictingDeclaration != null)
		{
			throw new AvailRejectedParseException(
				STRONG,
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
		return functionType(
			tuple(
				/* Variable name token */
				LITERAL_PHRASE.create(TOKEN.o()),
				/* Variable type */
				LITERAL_PHRASE.create(anyMeta()),
				/* Initialization expression */
				EXPRESSION_PHRASE.create(ANY.o())),
			LOCAL_VARIABLE_PHRASE.mostGeneralType());
	}
}
