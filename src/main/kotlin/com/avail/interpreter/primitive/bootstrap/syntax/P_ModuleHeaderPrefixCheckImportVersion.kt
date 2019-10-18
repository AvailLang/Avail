/*
 * P_ModuleHeaderPrefixCheckImportVersion.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
import com.avail.descriptor.A_Type
import com.avail.descriptor.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive
import com.avail.descriptor.ListPhraseTypeDescriptor.*
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.Constants.stringLiteralType
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.Private

/**
 * This is the prefix function for [P_ModuleHeaderPseudoMacro] associated with
 * having just read one more version string in an import clause.  Check it
 * against the already parsed version strings for that import to ensure it's not
 * a duplicate. Doing this in a macro prefix function allows early checking (for
 * duplicate import versions), as well as detecting the leftmost error.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_ModuleHeaderPrefixCheckImportVersion
	: Primitive(3, Private, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		val allImportsList = interpreter.argument(2)
		val lastImport = allImportsList.lastExpression()
		assert(lastImport.expressionsSize() == 2)  // extends/uses, then content
		val lastImportNames = lastImport.expressionAt(2)
		val lastImportNameEntry = lastImportNames.lastExpression()
		assert(lastImportNameEntry.expressionsSize() == 2)
		val importVersionsOptional = lastImportNameEntry.expressionAt(2)
		assert(importVersionsOptional.expressionsSize() == 1)
		val importVersions = importVersionsOptional.expressionAt(1)
		val importVersionsSize = importVersions.expressionsSize()
		val lastImportVersion = importVersions.lastExpression()
		assert(lastImportVersion.phraseKindIsUnder(LITERAL_PHRASE))
		val lastImportVersionString =
			lastImportVersion.token().literal().literal()
		for (i in 1 until importVersionsSize)
		{
			val oldVersionPhrase = importVersions.expressionAt(i)
			val oldVersion =
				oldVersionPhrase.token().literal().literal()
			if (lastImportVersionString.equals(oldVersion))
			{
				val importModuleName =
					lastImportNameEntry.expressionAt(1)
				throw AvailRejectedParseException(
					STRONG,
					"imported module ($importModuleName) version specification "
						+ "$lastImportVersionString to be unique, not a "
						+ "duplicate (of line ${oldVersionPhrase.token().lineNumber()})")
			}
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				stringLiteralType, // Module name
				zeroOrOneList(zeroOrMoreList(stringLiteralType)), // Optional versions
				/* All imports */
				zeroOrMoreList(
					list(
						LITERAL_PHRASE.create(inclusive(1, 2)),
						zeroOrMoreList(
							listPrefix(
								2, // Last one won't have imported names yet.
								stringLiteralType, // Imported module name
								// Imported module versions
								zeroOrOneList(zeroOrMoreList(stringLiteralType)),
								// Imported names
								zeroOrOneList(list(
									zeroOrMoreList(list(
											// Negated import
											LITERAL_PHRASE.create(booleanType()),
											stringLiteralType, // Name
											// Replacement name
											zeroOrOneList(stringLiteralType))),
										// Final ellipsis (import all the rest)
										LITERAL_PHRASE.create(
											booleanType())))))))),
			TOP.o())
}