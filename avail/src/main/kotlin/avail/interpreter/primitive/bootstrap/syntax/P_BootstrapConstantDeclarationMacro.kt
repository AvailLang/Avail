/*
 * P_BootstrapConstantDeclarationMacro.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.interpreter.primitive.bootstrap.syntax

import avail.compiler.AvailCompiler
import avail.compiler.AvailRejectedParseException
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newConstant
import avail.descriptor.tokens.TokenDescriptor.TokenType.KEYWORD
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LOCAL_CONSTANT_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOKEN
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.style.P_BootstrapStatementStyler

/**
 * The [P_BootstrapConstantDeclarationMacro] primitive is used for bootstrapping
 * the declaration of a [local][PhraseKind.LOCAL_CONSTANT_PHRASE] constant.
 * Constant declarations that occur at the outermost scope are rewritten by the
 * [AvailCompiler] as a module [constant][PhraseKind.MODULE_CONSTANT_PHRASE].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapConstantDeclarationMacro
	: Primitive(2, CannotFail, CanInline, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val constantNameLiteral = interpreter.argument(0)
		val initializationExpression = interpreter.argument(1)

		val nameToken = constantNameLiteral.token.literal()
		val nameString = nameToken.string()
		if (nameToken.tokenType() != KEYWORD)
		{
			throw AvailRejectedParseException(
				STRONG, "new constant name to be alphanumeric, not $nameString")
		}
		val initializationType = initializationExpression.phraseExpressionType
		if (initializationType.isTop || initializationType.isBottom)
		{
			throw AvailRejectedParseException(
				STRONG,
				"constant initialization expression to have a type other "
					+ "than $initializationType")
		}
		val constantDeclaration =
			newConstant(nameToken, initializationExpression)
		FiberDescriptor.addDeclaration(constantDeclaration)?.let {
			throw AvailRejectedParseException(
				STRONG,
				"local constant $nameString to have a name that doesn't "
					+ "shadow an existing "
					+ "${it.declarationKind().nativeKindName()} "
					+ "(from line ${it.token.lineNumber()})")
		}
		return interpreter.primitiveSuccess(constantDeclaration)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				/* Constant name token as a literal phrase */
				LITERAL_PHRASE.create(TOKEN.o),
				/* Initialization expression */
				EXPRESSION_PHRASE.create(ANY.o)),
			LOCAL_CONSTANT_PHRASE.mostGeneralType)

	override fun bootstrapStyler() = P_BootstrapStatementStyler
}
