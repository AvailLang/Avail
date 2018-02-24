/*
 * P_BootstrapConstantDeclarationMacro.java
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

import com.avail.compiler.AvailCompiler;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.FiberDescriptor;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;

import static com.avail.descriptor.DeclarationPhraseDescriptor.newConstant;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.*;
import static com.avail.descriptor.TokenDescriptor.TokenType.KEYWORD;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.TOKEN;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * The {@code P_BootstrapConstantDeclarationMacro} primitive is used for
 * bootstrapping declaration of a {@link PhraseKind#LOCAL_CONSTANT_PHRASE local
 * constant declaration}.  Constant declarations that occur at the outermost
 * scope are rewritten by the {@link AvailCompiler} as a {@link PhraseKind
 * #MODULE_CONSTANT_NODE}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapConstantDeclarationMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_BootstrapConstantDeclarationMacro().init(
			2, CannotFail, CanInline, Bootstrap);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Phrase constantNameLiteral = interpreter.argument(0);
		final A_Phrase initializationExpression = interpreter.argument(1);

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
			newConstant(nameToken, initializationExpression);
		final @Nullable A_Phrase conflictingDeclaration =
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
		return functionType(
			tuple(
				/* Constant name token as a literal phrase */
				LITERAL_PHRASE.create(TOKEN.o()),
				/* Initialization expression */
				EXPRESSION_PHRASE.create(ANY.o())),
			LOCAL_CONSTANT_PHRASE.mostGeneralType());
	}
}
