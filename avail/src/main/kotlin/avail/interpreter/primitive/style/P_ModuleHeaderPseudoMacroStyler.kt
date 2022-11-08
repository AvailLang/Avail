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
import avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.fiber.A_Fiber.Companion.canStyle
import avail.descriptor.methods.A_Styler.Companion.stylerFunctionType
import avail.descriptor.methods.StylerDescriptor.SystemStyle
import avail.descriptor.methods.StylerDescriptor.SystemStyle.ENTRY_POINT
import avail.descriptor.methods.StylerDescriptor.SystemStyle.EXPORT
import avail.descriptor.methods.StylerDescriptor.SystemStyle.IMPORT
import avail.descriptor.methods.StylerDescriptor.SystemStyle.MODULE_HEADER_REGION
import avail.descriptor.methods.StylerDescriptor.SystemStyle.PRAGMA
import avail.descriptor.methods.StylerDescriptor.SystemStyle.STRING_LITERAL
import avail.descriptor.methods.StylerDescriptor.SystemStyle.VERSION
import avail.descriptor.phrases.A_Phrase.Companion.allTokens
import avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.A_Token.Companion.pastEnd
import avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.component1
import avail.descriptor.tuples.A_Tuple.Companion.component2
import avail.descriptor.tuples.A_Tuple.Companion.component3
import avail.descriptor.tuples.A_Tuple.Companion.component4
import avail.descriptor.tuples.A_Tuple.Companion.component5
import avail.descriptor.tuples.A_Tuple.Companion.component6
import avail.descriptor.tuples.A_Tuple.Companion.component7
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.RepeatedElementTupleDescriptor.Companion.createRepeatedElementTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.exceptions.AvailErrorCode.E_CANNOT_STYLE
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
		val optionalSendPhrase: A_Tuple = interpreter.argument(0)
		//		val transformedPhrase: A_Phrase = interpreter.argument(1)

		val fiber = interpreter.fiber()
		if (!fiber.canStyle) return interpreter.primitiveFailure(E_CANNOT_STYLE)
		val loader = fiber.availLoader!!

		if (optionalSendPhrase.tupleSize == 0)
		{
			return interpreter.primitiveSuccess(nil)
		}
		val sendPhrase = optionalSendPhrase.tupleAt(1)

		// Color the fixed tokens of the header.
		loader.styleTokens(sendPhrase.tokens, SystemStyle.MODULE_HEADER)

		// Extract the original phrase's clauses.
		val (
			moduleName,
			optionalVersions,
			imports,
			optionalNames,
			optionalEntries,
			optionalPragmas,
			optionalCorpus
		) = sendPhrase.argumentsListNode.expressionsTuple

		loader.styleToken(moduleName.token, STRING_LITERAL)

		optionalVersions.expressionsTuple.forEach { versions ->
			versions.expressionsTuple.forEach { version ->
				loader.styleStringLiteral(version.token)
				loader.styleToken(version.token, VERSION)
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
						loader.styleToken(importVersion.token, VERSION)
					}
				}
				optionalImportNames.expressionsTuple.forEach { importNames ->
					importNames.expressionAt(1).expressionsTuple.forEach {
							imports ->
						val (negated, original, optionalReplacement) =
							imports.expressionsTuple
						loader.styleMethodName(original.token)
						if (negated.token.literal().extractBoolean)
						{
							// This is a *negated* import.
							loader.styleToken(
								original.token, SystemStyle.EXCLUDED)
						}
						optionalReplacement.expressionsTuple.forEach {
								replacement ->
							loader.styleMethodName(replacement.token)
							loader.styleToken(replacement.token, IMPORT)
						}
					}
					// expressionAt(2) indicates an ellipsis to import the rest.
				}
			}
		}

		optionalNames.expressionsTuple.forEach { names ->
			names.expressionsTuple.forEach { name ->
				loader.styleMethodName(name.token)
				loader.styleToken(name.token, EXPORT)
			}
		}

		optionalEntries.expressionsTuple.forEach { names ->
			names.expressionsTuple.forEach { name ->
				loader.styleMethodName(name.token)
				loader.styleToken(name.token, ENTRY_POINT)
			}
		}

		optionalPragmas.expressionsTuple.forEach { pragmas ->
			pragmas.expressionsTuple.forEach { pragma ->
				loader.styleMethodName(pragma.token)
				loader.styleToken(pragma.token, PRAGMA)
			}
		}

		optionalCorpus.expressionsTuple.forEach { corpus ->
			corpus.expressionsTuple.forEach { corpusPair ->
				val (corpusName, filePattern) = corpusPair.expressionsTuple
				loader.styleStringLiteral(corpusName.token)
				loader.styleStringLiteral(filePattern.token)
			}
		}

		// End by coloring the entire header span (say, with a background
		// tint).  This is done at the end, because the string and method name
		// stylers completely override the style, but the module header region
		// styling (probably) doesn't mess with the foreground color.
		val allTokens = sendPhrase.allTokens
		if (allTokens.tupleSize > 0)
		{
			val firstToken = allTokens.tupleAt(1)
			val start = firstToken.start()
			val pastEnd = allTokens.tupleAt(allTokens.tupleSize).pastEnd()
			// Ignore zero-width spans.
			if (pastEnd > start)
			{
				val fakeToken = literalToken(
					createRepeatedElementTuple(
						pastEnd - start, fromCodePoint('?'.code)
					) as A_String,
					start,
					firstToken.lineNumber(),
					nil,
					nil)
				fakeToken.setCurrentModule(loader.module)
				loader.styleToken(fakeToken, MODULE_HEADER_REGION, false)
			}
		}


		return interpreter.primitiveSuccess(nil)
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_CANNOT_STYLE))

	override fun privateBlockTypeRestriction(): A_Type = stylerFunctionType
}
