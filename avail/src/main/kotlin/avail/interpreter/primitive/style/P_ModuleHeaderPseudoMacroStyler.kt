/*
 * P_ModuleHeaderPseudoMacroStyler.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.interpreter.primitive.style

import avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.methods.A_Styler
import avail.descriptor.methods.StylerDescriptor.SystemStyle.MODULE_HEADER
import avail.descriptor.methods.StylerDescriptor.SystemStyle.STRING_LITERAL
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.representation.NilDescriptor
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.tuples.A_Tuple.Companion.component1
import avail.descriptor.tuples.A_Tuple.Companion.component2
import avail.descriptor.tuples.A_Tuple.Companion.component3
import avail.descriptor.tuples.A_Tuple.Companion.component4
import avail.descriptor.tuples.A_Tuple.Companion.component5
import avail.descriptor.tuples.A_Tuple.Companion.component6
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.bootstrap.syntax.P_ModuleHeaderPseudoMacro

/**
 * **Primitive:** The [P_ModuleHeaderPseudoMacroStyler] primitive applies
 * styling to a module header parsed with [P_ModuleHeaderPseudoMacro].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_ModuleHeaderPseudoMacroStyler : Primitive(2, CanInline, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val sendPhrase: A_Phrase = interpreter.argument(0)
		//		val transformedPhrase: A_Phrase = interpreter.argument(1)

		val loader = interpreter.fiber().availLoader
			?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}

		// Color the fixed tokens of the header.
		loader.styleTokens(sendPhrase.tokens, MODULE_HEADER)

		// Extract the original phrase's clauses.
		val (
			moduleName,
			optionalVersions,
			imports,
			optionalNames,
			optionalEntries,
			optionalPragmas
		) = sendPhrase.argumentsListNode.expressionsTuple

		loader.styleToken(moduleName.token, STRING_LITERAL)

		optionalVersions.expressionsTuple.forEach { versions ->
			versions.expressionsTuple.forEach { version ->
				loader.styleStringLiteral(version.token)
			}
		}

		imports.expressionsTuple.forEach { importSection ->
			// expressionAt(1) is literal 1 for Extends, 2 for Uses.
			importSection.expressionAt(2).expressionsTuple.forEach {
					importDetail ->
				val (
					importModuleName,
					optionalImportVersions,
					optionalImportNames
				) = importDetail.expressionsTuple
				loader.styleStringLiteral(importModuleName.token)
				optionalImportVersions.expressionsTuple.forEach { importVersions ->
					importVersions.expressionsTuple.forEach { importVersion ->
						loader.styleStringLiteral(importVersion.token)
					}
				}
				optionalImportNames.expressionsTuple.forEach { importNames ->
					importNames.expressionAt(1).expressionsTuple.forEach {
							imports ->
						val (negated, original, optionalReplacement) =
							imports.expressionsTuple
						if (negated.token.literal().extractBoolean)
						{
							// This is a *negated* import.
							//TODO Introduce negated import style.
						}
						loader.styleStringLiteral(original.token)
						loader.styleMethodName(original.token)
						optionalReplacement.expressionsTuple.forEach {
								replacement ->
							loader.styleStringLiteral(replacement.token)
							loader.styleMethodName(replacement.token)
						}
					}
					// expressionAt(2) indicates an ellipsis to import the rest.
				}
			}
		}

		// Handle Names and Entries sections the same for nom.
		(optionalNames.expressionsTuple + optionalEntries.expressionsTuple)
			.forEach { names ->
				names.expressionsTuple.forEach { name ->
					loader.styleStringLiteral(name.token)
					loader.styleMethodName(name.token)
				}
			}

		optionalPragmas.expressionsTuple.forEach { pragmas ->
			pragmas.expressionsTuple.forEach { pragma ->
				loader.styleStringLiteral(pragma.token)
			}
		}

		//TODO Complete this.

		return interpreter.primitiveSuccess(NilDescriptor.nil)
	}

	override fun privateFailureVariableType(): A_Type =
		AbstractEnumerationTypeDescriptor.enumerationWith(
			SetDescriptor.set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION))

	override fun privateBlockTypeRestriction(): A_Type =
		A_Styler.stylerFunctionType
}
