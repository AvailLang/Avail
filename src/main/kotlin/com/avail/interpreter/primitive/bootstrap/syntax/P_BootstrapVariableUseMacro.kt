/*
 * P_BootstrapVariableUseMacro.java
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
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.*
import com.avail.descriptor.A_String
import com.avail.descriptor.A_Type
import com.avail.descriptor.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY
import com.avail.descriptor.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_MAP_KEY
import com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_CONSTANT
import com.avail.descriptor.DeclarationPhraseDescriptor.newModuleConstant
import com.avail.descriptor.DeclarationPhraseDescriptor.newModuleVariable
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.descriptor.StringDescriptor.stringFrom
import com.avail.descriptor.TokenDescriptor.TokenType
import com.avail.descriptor.TupleDescriptor.toList
import com.avail.descriptor.TypeDescriptor.Types.TOKEN
import com.avail.descriptor.VariableUsePhraseDescriptor
import com.avail.descriptor.VariableUsePhraseDescriptor.newUse
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import java.util.*
import java.util.Comparator.comparing

/**
 * The `P_BootstrapVariableUseMacro` primitive is used to create
 * [variable use][VariableUsePhraseDescriptor] phrases.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_BootstrapVariableUseMacro : Primitive(1, CannotFail, CanInline, Bootstrap)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val variableNameLiteral = interpreter.argument(0)

		val loader = interpreter.availLoaderOrNull()
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
				"variable %s to be alphanumeric",
				variableNameString)
		}
		val fiberGlobals = interpreter.fiber().fiberGlobals()
		val clientData = fiberGlobals.mapAt(
			CLIENT_DATA_GLOBAL_KEY.atom)
		val scopeMap = clientData.mapAt(COMPILER_SCOPE_MAP_KEY.atom)
		if (scopeMap.hasKey(variableNameString))
		{
			val localDeclaration = scopeMap.mapAt(variableNameString)
			// If the local constant is initialized by a literal, then treat a
			// mention of that constant as though it were the literal itself.
			if (localDeclaration.declarationKind() === LOCAL_CONSTANT && localDeclaration
					.initializationExpression()
					.phraseKindIsUnder(LITERAL_PHRASE))
			{
				return interpreter.primitiveSuccess(
					localDeclaration.initializationExpression())
			}

			val variableUse = newUse(
				actualToken, scopeMap.mapAt(variableNameString))
			variableUse.makeImmutable()
			return interpreter.primitiveSuccess(variableUse)
		}
		// Not in a block scope. See if it's a module variable or module
		// constant...
		val module = loader.module()
		if (module.variableBindings().hasKey(variableNameString))
		{
			val variableObject = module.variableBindings().mapAt(variableNameString)
			val moduleVarDecl = newModuleVariable(
				actualToken,
				variableObject,
				nil,
				nil)
			val variableUse = newUse(actualToken, moduleVarDecl)
			variableUse.makeImmutable()
			return interpreter.primitiveSuccess(variableUse)
		}
		if (!module.constantBindings().hasKey(variableNameString))
		{
			throw AvailRejectedParseException(
				// Almost any theory is better than guessing that we want the
				// value of some variable that doesn't exist.
				if (scopeMap.mapSize() == 0) SILENT else WEAK)
			{
				val builder = StringBuilder()
				builder.append("variable ")
				builder.append(variableNameString)
				builder.append(" to be in scope (local scope is: ")
				val scope = ArrayList(
					toList<A_String>(scopeMap.keysAsSet().asTuple()))
				scope.sortWith(comparing<A_String, String> { it.asNativeString() })
				var first = true
				for (eachVar in scope)
				{
					if (!first)
					{
						builder.append(", ")
					}
					builder.append(eachVar.asNativeString())
					first = false
				}
				builder.append(")")
				stringFrom(builder.toString())
			}
		}
		val variableObject = module.constantBindings().mapAt(variableNameString)
		val moduleConstDecl = newModuleConstant(actualToken, variableObject, nil)
		val variableUse = newUse(actualToken, moduleConstDecl)
		return interpreter.primitiveSuccess(variableUse)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				/* Variable name */
				LITERAL_PHRASE.create(TOKEN.o())),
			EXPRESSION_PHRASE.mostGeneralType())
	}

}