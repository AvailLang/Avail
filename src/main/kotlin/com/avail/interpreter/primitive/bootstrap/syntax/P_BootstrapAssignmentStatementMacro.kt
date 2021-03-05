/*
 * P_BootstrapAssignmentStatementMacro.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_MAP_KEY
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.STATIC_TOKENS_KEY
import com.avail.descriptor.maps.A_Map.Companion.hasKey
import com.avail.descriptor.maps.A_Map.Companion.mapAt
import com.avail.descriptor.module.A_Module.Companion.constantBindings
import com.avail.descriptor.module.A_Module.Companion.variableBindings
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.A_Phrase.Companion.declaredType
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.phrases.AssignmentPhraseDescriptor.Companion.newAssignment
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newModuleConstant
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newModuleVariable
import com.avail.descriptor.phrases.ExpressionAsStatementPhraseDescriptor.Companion.newExpressionAsStatement
import com.avail.descriptor.phrases.VariableUsePhraseDescriptor.Companion.newUse
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.tokens.TokenDescriptor.TokenType
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.StringDescriptor.Companion.formatString
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.TypeDescriptor.Types.TOKEN
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.execution.Interpreter

/**
 * The `P_BootstrapAssignmentStatementMacro` primitive is used for assignment
 * statements.  It constructs an expression-as-statement containing an
 * assignment that has the isInline flag cleared.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapAssignmentStatementMacro
	: Primitive(2, CannotFail, CanInline, Bootstrap)
{
	/** The key to the all tokens tuple in the fiber's environment. */
	private val staticTokensKey = STATIC_TOKENS_KEY.atom

	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val variableNameLiteral = interpreter.argument(0)
		val valueExpression = interpreter.argument(1)

		val loader =
			interpreter.fiber().availLoader()
	             ?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		assert(variableNameLiteral.isInstanceOf(
			LITERAL_PHRASE.mostGeneralType()))
		val literalToken = variableNameLiteral.token()
		assert(literalToken.tokenType() == TokenType.LITERAL)
		val actualToken = literalToken.literal()
		assert(actualToken.isInstanceOf(TOKEN.o))
		val variableNameString = actualToken.string()
		if (actualToken.tokenType() != TokenType.KEYWORD)
		{
			throw AvailRejectedParseException(
				STRONG,
				"variable name for assignment to be alphanumeric, not $variableNameString")
		}
		val fiberGlobals = interpreter.fiber().fiberGlobals()
		val clientData =
			fiberGlobals.mapAt(CLIENT_DATA_GLOBAL_KEY.atom)
		val scopeMap = clientData.mapAt(COMPILER_SCOPE_MAP_KEY.atom)
		val module = loader.module()
		val declaration: A_Phrase = when {
			scopeMap.hasKey(variableNameString) -> {
				scopeMap.mapAt(variableNameString)
			}
			module.variableBindings().hasKey(variableNameString) -> {
				val variableObject =
					module.variableBindings().mapAt(variableNameString)
				newModuleVariable(actualToken, variableObject, nil, nil)
			}
			module.constantBindings().hasKey(variableNameString) -> {
				val variableObject =
					module.constantBindings().mapAt(variableNameString)
				newModuleConstant(actualToken, variableObject, nil)
			}
			else -> throw AvailRejectedParseException(STRONG)
			{
				formatString(
					"variable (%s) for assignment to be in scope",
					variableNameString)
			}
		}
		if (!declaration.declarationKind().isVariable)
		{
			throw AvailRejectedParseException(STRONG)
			{
				formatString(
					"a name of a variable, not a %s",
					declaration.declarationKind().nativeKindName())
			}
		}
		if (!valueExpression.phraseExpressionType().isSubtypeOf(
				declaration.declaredType()))
		{
			throw AvailRejectedParseException(STRONG)
			{
				formatString(
					"assignment expression's type (%s) "
						+ "to match variable type (%s)",
					valueExpression.phraseExpressionType(),
					declaration.declaredType())
			}
		}
		val tokens = clientData.mapAt(staticTokensKey)
		val assignment = newAssignment(
			newUse(actualToken, declaration), valueExpression, tokens, false)
		assignment.makeImmutable()
		val assignmentAsStatement = newExpressionAsStatement(assignment)
		return interpreter.primitiveSuccess(assignmentAsStatement)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				/* Variable name for assignment */
				LITERAL_PHRASE.create(TOKEN.o),
				/* Assignment value */
				EXPRESSION_PHRASE.create(ANY.o)),
			EXPRESSION_AS_STATEMENT_PHRASE.mostGeneralType())
}
