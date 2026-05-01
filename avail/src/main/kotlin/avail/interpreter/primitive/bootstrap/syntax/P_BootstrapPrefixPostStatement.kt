/*
 * P_BootstrapPrefixPostStatement.kt
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
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.WEAK
import avail.descriptor.phrases.A_Phrase.Companion.lastExpression
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.STATEMENT_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOKEN
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.Bootstrap
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive4

/**
 * The `P_BootstrapPrefixPostStatement` primitive is used for ensuring that
 * statements are top-valued before over-parsing.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapPrefixPostStatement : Primitive4(CanInline, Bootstrap)
{
	override fun Interpreter.attempt4(
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject,
		arg4: AvailObject
	): A_BasicObject?
	{
		// val blockArgumentsPhrase: A_Phrase = arg1
		// val optionalPrimFailurePhrase: A_Phrase = arg2
		// val optionalLabelPhrase: A_Phrase = arg3
		val statementsPhrase = arg4

		availLoaderOrNull() ?: return fail(E_LOADING_IS_OVER)

		// Here the statements so far are a list phrase, not a sequence.
		// The section marker is inside the repetition, so this primitive could
		// only be invoked if there is at least one statement.
		val latestStatementLiteral = statementsPhrase.lastExpression
		val latestStatement = latestStatementLiteral.token.literal()
		if (!latestStatement.phraseExpressionType.equals(TOP()))
		{
			throw AvailRejectedParseException(WEAK, "statement to have type ⊤")
		}
		return nil
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				// Macro argument is a phrase.
				LIST_PHRASE.create(
					// Optional arguments section.
					zeroOrOneOf(
						// Arguments are present.
						oneOrMoreOf(
							// An argument.
							tupleTypeForTypes(
								// Argument name, a token.
								TOKEN(),
								// Argument type.
								anyMeta)))),
				// Macro argument is a phrase.
				LIST_PHRASE.create(
					// Optional primitive declaration.
					zeroOrOneOf(
						// Primitive declaration
						tupleTypeForTypes(
							// Primitive name.
							TOKEN(),
							// Optional failure variable declaration.
							zeroOrOneOf(
								// Primitive failure variable parts.
								tupleTypeForTypes(
									// Primitive failure variable name token
									TOKEN(),
									// Primitive failure variable type
									anyMeta))))),
				// Macro argument is a phrase.
				LIST_PHRASE.create(
					// Optional label declaration.
					zeroOrOneOf(
						// Label parts.
						tupleTypeForTypes(
							// Label name
							TOKEN(),
							// Optional label return type.
							zeroOrOneOf(
								// Label return type.
								topMeta)))),
				// Macro argument is a phrase.
				LIST_PHRASE.create(
					// Statements and declarations so far.
					zeroOrMoreOf(
						// The "_!" mechanism wrapped each statement or
						// declaration inside a literal phrase, so expect a
						// phrase here instead of TOP.
						STATEMENT_PHRASE.mostGeneralType))),
			TOP())
}
