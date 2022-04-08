/*
 * P_BootstrapVariableUseMacro.kt
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

package avail.interpreter.primitive.bootstrap.syntax

import avail.compiler.AvailRejectedParseException
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.SILENT
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.WEAK
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_MAP_KEY
import avail.descriptor.fiber.A_Fiber.Companion.fiberGlobals
import avail.descriptor.maps.A_Map.Companion.keysAsSet
import avail.descriptor.maps.A_Map.Companion.mapAt
import avail.descriptor.maps.A_Map.Companion.mapAtOrNull
import avail.descriptor.maps.A_Map.Companion.mapSize
import avail.descriptor.module.A_Module.Companion.constantBindings
import avail.descriptor.module.A_Module.Companion.variableBindings
import avail.descriptor.phrases.A_Phrase.Companion.initializationExpression
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newModuleConstant
import avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newModuleVariable
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_CONSTANT
import avail.descriptor.phrases.VariableUsePhraseDescriptor
import avail.descriptor.phrases.VariableUsePhraseDescriptor.Companion.newUse
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set.Companion.asTuple
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.toList
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.VARIABLE_USE_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOKEN
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter

/**
 * The `P_BootstrapVariableUseMacro` primitive is used to create
 * [variable&#32;use][VariableUsePhraseDescriptor] phrases.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapVariableUseMacro
	: Primitive(1, CanInline, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val variableNameLiteral = interpreter.argument(0)

		val loader = interpreter.availLoaderOrNull()
			?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		assert(
			variableNameLiteral.isInstanceOf(
				LITERAL_PHRASE.mostGeneralType))
		val literalToken = variableNameLiteral.token
		assert(literalToken.tokenType() == TokenType.LITERAL)
		val actualToken = literalToken.literal()
		assert(actualToken.isInstanceOf(TOKEN.o))
		val variableNameString = actualToken.string()
		if (actualToken.tokenType() != TokenType.KEYWORD)
		{
			throw AvailRejectedParseException(
				STRONG, "variable $variableNameString to be alphanumeric")
		}
		val fiberGlobals = interpreter.fiber().fiberGlobals
		val clientData = fiberGlobals.mapAt(CLIENT_DATA_GLOBAL_KEY.atom)
		val scopeMap = clientData.mapAt(COMPILER_SCOPE_MAP_KEY.atom)
		scopeMap.mapAtOrNull(variableNameString)?.let { localDeclaration ->
			// If the local constant is initialized by a literal, then treat a
			// mention of that constant as though it were the literal itself.
			if (localDeclaration.declarationKind() === LOCAL_CONSTANT
				&& localDeclaration.initializationExpression
					.phraseKindIsUnder(LITERAL_PHRASE))
			{
				return interpreter.primitiveSuccess(
					localDeclaration.initializationExpression)
			}
			val variableUse = newUse(actualToken, localDeclaration)
			variableUse.makeImmutable()
			return interpreter.primitiveSuccess(variableUse)
		}
		// Not in a block scope. See if it's a module variable or module
		// constant...
		val module = loader.module
		module.variableBindings.mapAtOrNull(variableNameString)?.let {
				variableObject ->
			val moduleVarDecl =
				newModuleVariable(actualToken, variableObject, nil, nil)
			val variableUse = newUse(actualToken, moduleVarDecl)
			return interpreter.primitiveSuccess(variableUse.makeImmutable())
		}
		module.constantBindings.mapAtOrNull(variableNameString)?.let {
				variableObject ->
			val moduleConstDecl =
				newModuleConstant(actualToken, variableObject, nil)
			val variableUse = newUse(actualToken, moduleConstDecl)
			return interpreter.primitiveSuccess(variableUse.makeImmutable())
		}
		throw AvailRejectedParseException(
			// Almost any theory is better than guessing that we want the
			// value of some variable that doesn't exist.
			if (scopeMap.mapSize == 0) SILENT else WEAK)
		{
			stringFrom(
				buildString {
					val scope =
						toList<A_String>(scopeMap.keysAsSet.asTuple)
							.map(A_String::asNativeString)
					append("potential variable ")
					append(variableNameString)
					append(" to be in scope (local scope is")
					when
					{
						scope.isEmpty() -> append(" empty)")
						else -> append(
							scope.sorted().joinToString(
								prefix = ": ",
								postfix = ")"))
					}
				})
		}
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_LOADING_IS_OVER))

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(LITERAL_PHRASE.create(TOKEN.o)), // Variable name
			VARIABLE_USE_PHRASE.mostGeneralType)
}
