/*
 * P_BootstrapPrefixBlockArgument.kt
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

import avail.compiler.AvailRejectedParseException
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.WEAK
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import avail.descriptor.phrases.A_Phrase.Companion.lastExpression
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newArgument
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOKEN
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter

/**
 * The `P_BootstrapPrefixBlockArgument` primitive is used as a prefix function
 * for bootstrapping argument declarations within a
 * [block][P_BootstrapBlockMacro].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapPrefixBlockArgument : Primitive(1, CanInline, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val optionalBlockArgumentsList = interpreter.argument(0)

		interpreter.availLoaderOrNull() ?:
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)

		assert(optionalBlockArgumentsList.expressionsSize == 1)
		val blockArgumentsList = optionalBlockArgumentsList.lastExpression
		assert(blockArgumentsList.expressionsSize >= 1)
		val lastPair = blockArgumentsList.lastExpression
		assert(lastPair.expressionsSize == 2)
		val namePhrase = lastPair.expressionAt(1)
		val typePhrase = lastPair.expressionAt(2)

		assert(namePhrase.isInstanceOfKind(LITERAL_PHRASE.create(TOKEN.o)))
		assert(typePhrase.isInstanceOfKind(LITERAL_PHRASE.create(anyMeta)))
		val outerArgToken = namePhrase.token
		val argToken = outerArgToken.literal()
		val argName = argToken.string()

		if (argToken.tokenType() != TokenType.KEYWORD)
		{
			throw AvailRejectedParseException(
				STRONG,
				"argument name to be alphanumeric, not $argName")
		}
		val argType = typePhrase.token.literal()
		assert(argType.isType)
		if (argType.isBottom)
		{
			throw AvailRejectedParseException(
				// Weak, in case we've read a prefix of the type expression.
				WEAK,
				"block parameter type not to be ⊥")
		}

		val argDeclaration = newArgument(argToken, argType, typePhrase)
		// Add the binding and we're done.
		val conflictingDeclaration =
			FiberDescriptor.addDeclaration(argDeclaration)
		if (conflictingDeclaration !== null)
		{
			val kind = conflictingDeclaration.declarationKind().nativeKindName()
			val lineNumber = conflictingDeclaration.token.lineNumber()
			throw AvailRejectedParseException(
				STRONG,
				"block argument declaration $argName to have a name that " +
					"doesn't shadow an existing $kind (from line $lineNumber)")
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional arguments section. */
					zeroOrOneOf(
						/* Arguments are present. */
						oneOrMoreOf(
							/* An argument. */
							tupleTypeForTypes(
								/* Argument name, a token. */
								TOKEN.o,
								/* Argument type. */
								anyMeta))))),
			TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_LOADING_IS_OVER))
}
