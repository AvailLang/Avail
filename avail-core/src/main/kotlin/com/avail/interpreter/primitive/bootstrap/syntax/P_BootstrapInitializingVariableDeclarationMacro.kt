/*
 * P_BootstrapInitializingVariableDeclarationMacro.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import com.avail.compiler.AvailRejectedParseException
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newVariable
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind
import com.avail.descriptor.tokens.TokenDescriptor.TokenType.KEYWORD
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LOCAL_VARIABLE_PHRASE
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.TypeDescriptor.Types.TOKEN
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.execution.Interpreter

/**
 * The `P_BootstrapInitializingVariableDeclarationMacro` primitive is used for
 * bootstrapping declaration of a
 * [local&#32;variable][DeclarationKind.LOCAL_VARIABLE] with an initializing
 * expression.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapInitializingVariableDeclarationMacro
	: Primitive(3, CanInline, CannotFail, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val variableNameLiteral = interpreter.argument(0)
		val typeLiteral = interpreter.argument(1)
		val initializationExpression = interpreter.argument(2)

		val nameToken = variableNameLiteral.token.literal()
		val nameString = nameToken.string()
		if (nameToken.tokenType() != KEYWORD)
		{
			throw AvailRejectedParseException(
				STRONG,
				"new variable name to be alphanumeric, not $nameString")
		}
		val type = typeLiteral.token.literal()
		if (type.isTop || type.isBottom)
		{
			throw AvailRejectedParseException(
				STRONG,
				"variable's declared type to be something other than $type")
		}
		val initializationType = initializationExpression.phraseExpressionType
		if (initializationType.isTop || initializationType.isBottom)
		{
			throw AvailRejectedParseException(
				STRONG,
				"initialization expression to have a type other than %s",
				initializationType)
		}
		if (!initializationType.isSubtypeOf(type))
		{
			throw AvailRejectedParseException(
				STRONG,
				"initialization expression's type ($initializationType) to "
					+ "match variable type ($type)")
		}
		val variableDeclaration = newVariable(
			nameToken, type, typeLiteral, initializationExpression)
		val conflictingDeclaration =
			FiberDescriptor.addDeclaration(variableDeclaration)
		if (conflictingDeclaration !== null)
		{
			throw AvailRejectedParseException(
				STRONG,
				"local variable $nameString to have a name that doesn't shadow "
					+ "an existing "
					+ conflictingDeclaration.declarationKind().nativeKindName()
					+ " (from line " +
					"${conflictingDeclaration.token.lineNumber()})")
		}
		return interpreter.primitiveSuccess(variableDeclaration)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				/* Variable name token */
				LITERAL_PHRASE.create(TOKEN.o),
				/* Variable type */
				LITERAL_PHRASE.create(anyMeta()),
				/* Initialization expression */
				EXPRESSION_PHRASE.create(ANY.o)),
			LOCAL_VARIABLE_PHRASE.mostGeneralType())
}
