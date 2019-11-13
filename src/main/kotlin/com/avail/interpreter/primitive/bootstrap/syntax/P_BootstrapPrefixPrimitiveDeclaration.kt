/*
 * P_BootstrapPrefixPrimitiveDeclaration.kt
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
import com.avail.descriptor.parsing.A_Phrase
import com.avail.descriptor.A_Type
import com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind
import com.avail.descriptor.DeclarationPhraseDescriptor.newPrimitiveFailureVariable
import com.avail.descriptor.FiberDescriptor
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.descriptor.TokenDescriptor.TokenType
import com.avail.descriptor.TupleTypeDescriptor.*
import com.avail.descriptor.TypeDescriptor.Types.TOKEN
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import com.avail.utility.Nulls.stripNull
import java.util.*

/**
 * The `P_BootstrapPrefixVariableDeclaration` primitive is used for
 * bootstrapping declaration of a primitive declaration, including an optional
 * [primitive failure variable][DeclarationKind.PRIMITIVE_FAILURE_REASON]
 * which holds the reason for a primitive's failure.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapPrefixPrimitiveDeclaration
	: Primitive(2, CanInline, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val optionalBlockArgumentsList = interpreter.argument(0)
		val optionalPrimPhrase = interpreter.argument(1)

		interpreter.availLoaderOrNull() ?:
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)

		assert(optionalPrimPhrase.expressionsSize() == 1)
		val primPhrase = optionalPrimPhrase.lastExpression()
		val primNamePhrase = primPhrase.expressionAt(1)
		if (!primNamePhrase.phraseKindIsUnder(LITERAL_PHRASE))
		{
			throw AvailRejectedParseException(
				STRONG, "primitive specification to be a literal keyword token")
		}
		val primName = primNamePhrase.token().string()
		val prim =
			primitiveByName(primName.asNativeString())
	           ?: throw AvailRejectedParseException(
		           STRONG,
		           "a supported primitive name, not $primName")

		// Check that the primitive signature agrees with the arguments.
		val blockArgumentPhrases = ArrayList<A_Phrase>()
		if (optionalBlockArgumentsList.expressionsSize() == 1)
		{
			val blockArgumentsList =
				optionalBlockArgumentsList.lastExpression()
			assert(blockArgumentsList.expressionsSize() >= 1)
			for (pair in blockArgumentsList.expressionsTuple())
			{
				assert(pair.expressionsSize() == 2)
				val namePhrase = pair.expressionAt(1)
				val name = namePhrase.token().literal().string()
				assert(name.isString)
				val declaration =
					stripNull(FiberDescriptor.lookupBindingOrNull(name))
				blockArgumentPhrases.add(declaration)
			}
		}
		validatePrimitiveAcceptsArguments(
				prim.primitiveNumber, blockArgumentPhrases)
			?.let { p -> throw AvailRejectedParseException(STRONG, p) }

		// The section marker occurs inside the optionality of the primitive
		// clause, but outside the primitive failure variable declaration.
		// Therefore, the variable declaration is not required to be present.
		// Make sure the failure variable is present exactly when it should be.
		val optionalFailure = primPhrase.expressionAt(2)
		if (optionalFailure.expressionsSize() == 1)
		{
			if (prim.hasFlag(CannotFail))
			{
				throw AvailRejectedParseException(
					STRONG,
					"no primitive failure variable declaration for this "
						+ "infallible primitive")
			}
			val failurePair = optionalFailure.expressionAt(1)
			assert(failurePair.expressionsSize() == 2)
			val failureNamePhrase = failurePair.expressionAt(1)
			val failureName = failureNamePhrase.token().literal()
			if (failureName.tokenType() != TokenType.KEYWORD)
			{
				throw AvailRejectedParseException(
					STRONG,
					"primitive failure variable name to be alphanumeric")
			}
			val failureTypePhrase = failurePair.expressionAt(2)
			val failureType = failureTypePhrase.token().literal()
			if (failureType.isBottom || failureType.isTop)
			{
				throw AvailRejectedParseException(
					STRONG,
					"primitive failure variable type not to be $failureType")
			}
			val requiredFailureType = prim.failureVariableType
			if (!requiredFailureType.isSubtypeOf(failureType))
			{
				throw AvailRejectedParseException(
					STRONG,
					"primitive failure variable to be a supertype of: "
					+ "$requiredFailureType, not $failureType")
			}
			val failureDeclaration =
				newPrimitiveFailureVariable(
					failureName, failureTypePhrase, failureType)
			FiberDescriptor.addDeclaration(failureDeclaration)
				?.let{ conflictingDeclaration ->
					throw AvailRejectedParseException(
						STRONG,
						"primitive failure variable ${failureName.string()} to "
							+ "have a name that doesn't shadow an existing "
							+ conflictingDeclaration.declarationKind()
								.nativeKindName()
							+  "(from line "
							+ "${conflictingDeclaration.token().lineNumber()})")
			}
			return interpreter.primitiveSuccess(nil)
		}
		if (!prim.hasFlag(CannotFail))
		{
			throw AvailRejectedParseException(
				STRONG,
				"a primitive failure variable declaration for this "
					+ "fallible primitive")
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
								TOKEN.o(),
								/* Argument type. */
								anyMeta())))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional primitive declaration. */
					zeroOrOneOf(
						/* Primitive declaration */
						tupleTypeForTypes(
							/* Primitive number. */
							TOKEN.o(),
							/* Optional failure variable declaration. */
							zeroOrOneOf(
								/* Primitive failure variable parts. */
								tupleTypeForTypes(
									/* Primitive failure variable name token */
									TOKEN.o(),
									/* Primitive failure variable type */
									anyMeta())))))),
			TOP.o())
}