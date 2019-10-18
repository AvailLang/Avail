/*
 * P_BootstrapAssignmentStatementMacro.kt
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

import com.avail.compiler.AvailRejectedParseException
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import com.avail.descriptor.A_Phrase
import com.avail.descriptor.A_Type
import com.avail.descriptor.AssignmentPhraseDescriptor.newAssignment
import com.avail.descriptor.AtomDescriptor.SpecialAtom.*
import com.avail.descriptor.DeclarationPhraseDescriptor.newModuleConstant
import com.avail.descriptor.DeclarationPhraseDescriptor.newModuleVariable
import com.avail.descriptor.ExpressionAsStatementPhraseDescriptor.newExpressionAsStatement
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.*
import com.avail.descriptor.StringDescriptor.formatString
import com.avail.descriptor.TokenDescriptor.TokenType
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.descriptor.TypeDescriptor.Types.TOKEN
import com.avail.descriptor.VariableUsePhraseDescriptor.newUse
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * The `P_BootstrapAssignmentStatementMacro` primitive is used for
 * assignment statements.  It constructs an expression-as-statement containing
 * an assignment that has the isInline flag cleared.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_BootstrapAssignmentStatementMacro : Primitive(2, CannotFail, CanInline, Bootstrap)
{

	/** The key to the all tokens tuple in the fiber's environment.  */
	internal val staticTokensKey = STATIC_TOKENS_KEY.atom

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val variableNameLiteral = interpreter.argument(0)
		val valueExpression = interpreter.argument(1)

		val loader = interpreter.fiber().availLoader()
		             ?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		assert(variableNameLiteral.isInstanceOf(
			LITERAL_PHRASE.mostGeneralType()))
		val literalToken = variableNameLiteral.token()
		assert(literalToken.tokenType() == TokenType.LITERAL)
		val actualToken = literalToken.literal()
		assert(actualToken.isInstanceOf(TOKEN.o()))
		val variableNameString = actualToken.string()
		if (actualToken.tokenType() != TokenType.KEYWORD)
		{
			throw AvailRejectedParseException(
				STRONG,
				"variable name for assignment to be alphanumeric, not $variableNameString")
		}
		val fiberGlobals = interpreter.fiber().fiberGlobals()
		val clientData = fiberGlobals.mapAt(
			CLIENT_DATA_GLOBAL_KEY.atom)
		val scopeMap = clientData.mapAt(COMPILER_SCOPE_MAP_KEY.atom)
		val module = loader.module()
		var declaration: A_Phrase? = null
		if (scopeMap.hasKey(variableNameString))
		{
			declaration = scopeMap.mapAt(variableNameString)
		}
		else if (module.variableBindings().hasKey(variableNameString))
		{
			val variableObject = module.variableBindings().mapAt(variableNameString)
			declaration = newModuleVariable(
				actualToken, variableObject, nil, nil)
		}
		else if (module.constantBindings().hasKey(variableNameString))
		{
			val variableObject = module.constantBindings().mapAt(variableNameString)
			declaration = newModuleConstant(actualToken, variableObject, nil)
		}

		if (declaration === null)
		{
			throw AvailRejectedParseException(
				STRONG
			) {
				formatString(
					"variable (%s) for assignment to be in scope",
					variableNameString)
			}
		}
		val declarationFinal = declaration
		if (!declaration.declarationKind().isVariable)
		{
			throw AvailRejectedParseException(
				STRONG
			) {
				formatString(
					"a name of a variable, not a %s",
					declarationFinal.declarationKind().nativeKindName())
			}
		}
		if (!valueExpression.expressionType().isSubtypeOf(
				declaration.declaredType()))
		{
			throw AvailRejectedParseException(
				STRONG
			) {
				formatString(
					"assignment expression's type (%s) " + "to match variable type (%s)",
					valueExpression.expressionType(),
					declarationFinal.declaredType())
			}
		}
		val tokens = clientData.mapAt(staticTokensKey)
		val assignment = newAssignment(
			newUse(actualToken, declaration), valueExpression, tokens, false)
		assignment.makeImmutable()
		val assignmentAsStatement = newExpressionAsStatement(assignment)
		return interpreter.primitiveSuccess(assignmentAsStatement)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				/* Variable name for assignment */
				LITERAL_PHRASE.create(TOKEN.o()),
				/* Assignment value */
				EXPRESSION_PHRASE.create(ANY.o())),
			EXPRESSION_AS_STATEMENT_PHRASE.mostGeneralType())
	}

}