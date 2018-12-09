/*
 * P_ModuleHeaderPrefixCheckModuleVersion.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray;
import static com.avail.descriptor.PhraseTypeDescriptor.Constants.stringLiteralType;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Flag.Bootstrap;
import static com.avail.interpreter.Primitive.Flag.Private;
import static com.avail.interpreter.primitive.bootstrap.syntax.P_ModuleHeaderPseudoMacro.zeroOrMoreList;
import static com.avail.interpreter.primitive.bootstrap.syntax.P_ModuleHeaderPseudoMacro.zeroOrOneList;

/**
 * This is the prefix function for {@link P_ModuleHeaderPseudoMacro} associated
 * with having just read one more module version string.  Check it against the
 * already parsed version strings to ensure it's not a duplicate.  Doing this in
 * a macro prefix function allows early checking (for duplicate versions).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_ModuleHeaderPrefixCheckModuleVersion extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ModuleHeaderPrefixCheckModuleVersion().init(
			2, Private, Bootstrap);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		final A_Phrase versionsOptionalList = interpreter.argument(1);
		assert versionsOptionalList.expressionsSize() == 1;
		final A_Phrase versions = versionsOptionalList.expressionAt(1);
		final int versionsCount = versions.expressionsSize();
		assert versionsCount >= 1;
		final A_Phrase latestVersionPhrase =
			versions.expressionAt(versionsCount);
		assert latestVersionPhrase.phraseKindIsUnder(LITERAL_PHRASE);
		final A_Token latestVersionToken = latestVersionPhrase.token();
		assert latestVersionToken.isLiteralToken();
		final A_String latestVersionString =
			latestVersionToken.literal().literal();
		for (int i = 1; i < versionsCount; i++)
		{
			final A_Phrase oldVersionPhrase = versions.expressionAt(i);
			final A_String oldVersion =
				oldVersionPhrase.token().literal().literal();
			if (latestVersionString.equals(oldVersion))
			{
				throw new AvailRejectedParseException(
					"module version %s to be unique, not a duplicate of #%d in "
					+ "the list (on line %d)",
					latestVersionString,
					i,
					oldVersionPhrase.token().lineNumber());
			}
		}
		return interpreter.primitiveSuccess(nil);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tupleFromArray(
				/* Module name */
				stringLiteralType,
				/* Optional versions */
				zeroOrOneList(zeroOrMoreList(stringLiteralType))),
			TOP.o());
	}

}
