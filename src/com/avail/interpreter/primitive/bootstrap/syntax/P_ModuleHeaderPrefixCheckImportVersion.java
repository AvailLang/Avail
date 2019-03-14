/*
 * P_ModuleHeaderPrefixCheckImportVersion.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.interpreter.primitive.bootstrap.syntax;

import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.ListPhraseTypeDescriptor.*;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.PhraseTypeDescriptor.Constants.stringLiteralType;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Flag.Bootstrap;
import static com.avail.interpreter.Primitive.Flag.Private;

/**
 * This is the prefix function for {@link P_ModuleHeaderPseudoMacro} associated
 * with having just read one more version string in an import clause.  Check it
 * against the already parsed version strings for that import to ensure it's not
 * a duplicate. Doing this in a macro prefix function allows early checking (for
 * duplicate import versions), as well as detecting the leftmost error.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_ModuleHeaderPrefixCheckImportVersion extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ModuleHeaderPrefixCheckImportVersion().init(
			3, Private, Bootstrap);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		final A_Phrase allImportsList = interpreter.argument(2);
		final A_Phrase lastImport = allImportsList.lastExpression();
		assert lastImport.expressionsSize() == 2;  // extends/uses, then content
		final A_Phrase lastImportNames = lastImport.expressionAt(2);
		final A_Phrase lastImportNameEntry = lastImportNames.lastExpression();
		assert lastImportNameEntry.expressionsSize() == 2;
		final A_Phrase importVersionsOptional =
			lastImportNameEntry.expressionAt(2);
		assert importVersionsOptional.expressionsSize() == 1;
		final A_Phrase importVersions = importVersionsOptional.expressionAt(1);
		final int importVersionsSize = importVersions.expressionsSize();
		final A_Phrase lastImportVersion = importVersions.lastExpression();
		assert lastImportVersion.phraseKindIsUnder(LITERAL_PHRASE);
		final A_String lastImportVersionString =
			lastImportVersion.token().literal().literal();
		for (int i = 1; i < importVersionsSize; i++)
		{
			final A_Phrase oldVersionPhrase = importVersions.expressionAt(i);
			final A_String oldVersion =
				oldVersionPhrase.token().literal().literal();
			if (lastImportVersionString.equals(oldVersion))
			{
				final A_Phrase importModuleName =
					lastImportNameEntry.expressionAt(1);
				throw new AvailRejectedParseException(
					"imported module (%s) version specification %s to be "
						+ "unique, not a duplicate (of line %d)",
					importModuleName,
					lastImportVersionString,
					oldVersionPhrase.token().lineNumber());
			}
		}
		return interpreter.primitiveSuccess(nil);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				/* Module name */
				stringLiteralType,
				/* Optional versions */
				zeroOrOneList(zeroOrMoreList(stringLiteralType)),
				/* All imports */
				zeroOrMoreList(
					list(
						LITERAL_PHRASE.create(
							inclusive(1, 2)),
						zeroOrMoreList(
							listPrefix(
								// Last one won't have imported names yet.
								2,
								// Imported module name
								stringLiteralType,
								// Imported module versions
								zeroOrOneList(
									zeroOrMoreList(stringLiteralType)),
								// Imported names
								zeroOrOneList(
									list(
										zeroOrMoreList(
											list(
												// Negated import
												LITERAL_PHRASE.create(
													booleanType()),
												// Name
												stringLiteralType,
												// Replacement name
												zeroOrOneList(
													stringLiteralType))),
										// Final ellipsis (import all the rest)
										LITERAL_PHRASE.create(
											booleanType())))))))),
			TOP.o());
	}

}
