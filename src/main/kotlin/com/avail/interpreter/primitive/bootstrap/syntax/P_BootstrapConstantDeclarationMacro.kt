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

package com.avail.interpreter.primitive.bootstrap.syntax

import com.avail.compiler.AvailCompiler
import com.avail.compiler.AvailRejectedParseException
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import com.avail.descriptor.A_Type
import com.avail.descriptor.DeclarationPhraseDescriptor.newConstant
import com.avail.descriptor.FiberDescriptor
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.*
import com.avail.descriptor.TokenDescriptor.TokenType.KEYWORD
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.descriptor.TypeDescriptor.Types.TOKEN
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * The `P_BootstrapConstantDeclarationMacro` primitive is used for
 * bootstrapping declaration of a [local][PhraseKind.LOCAL_CONSTANT_PHRASE].  Constant declarations that occur at the outermost
 * scope are rewritten by the [AvailCompiler] as a [ #MODULE_CONSTANT_NODE][PhraseKind].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_BootstrapConstantDeclarationMacro : Primitive(2, CannotFail, CanInline, Bootstrap)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val constantNameLiteral = interpreter.argument(0)
		val initializationExpression = interpreter.argument(1)

		val nameToken = constantNameLiteral.token().literal()
		val nameString = nameToken.string()
		if (nameToken.tokenType() != KEYWORD)
		{
			throw AvailRejectedParseException(
				STRONG,
				"new constant name to be alphanumeric, not %s",
				nameString)
		}
		val initializationType = initializationExpression.expressionType()
		if (initializationType.isTop || initializationType.isBottom)
		{
			throw AvailRejectedParseException(
				STRONG,
				"constant initialization expression to have a type other " + "than %s",
				initializationType)
		}
		val constantDeclaration = newConstant(nameToken, initializationExpression)
		val conflictingDeclaration = FiberDescriptor.addDeclaration(constantDeclaration)
		if (conflictingDeclaration !== null)
		{
			throw AvailRejectedParseException(
				STRONG,
				"local constant %s to have a name that doesn't shadow an " + "existing %s (from line %d)",
				nameString,
				conflictingDeclaration.declarationKind().nativeKindName(),
				conflictingDeclaration.token().lineNumber())
		}
		return interpreter.primitiveSuccess(constantDeclaration)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				/* Constant name token as a literal phrase */
				LITERAL_PHRASE.create(TOKEN.o()),
				/* Initialization expression */
				EXPRESSION_PHRASE.create(ANY.o())),
			LOCAL_CONSTANT_PHRASE.mostGeneralType())
	}

}