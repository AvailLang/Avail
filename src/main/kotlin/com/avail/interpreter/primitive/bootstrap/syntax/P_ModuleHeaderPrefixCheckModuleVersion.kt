/*
 * P_ModuleHeaderPrefixCheckModuleVersion.kt
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
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromArray
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.ListPhraseTypeDescriptor.zeroOrMoreList
import com.avail.descriptor.types.ListPhraseTypeDescriptor.zeroOrOneList
import com.avail.descriptor.types.PhraseTypeDescriptor.Constants.stringLiteralType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.Private

/**
 * This is the prefix function for [P_ModuleHeaderPseudoMacro] associated with
 * having just read one more module version string.  Check it against the
 * already parsed version strings to ensure it's not a duplicate.  Doing this in
 * a macro prefix function allows early checking (for duplicate versions).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_ModuleHeaderPrefixCheckModuleVersion : Primitive(2, Private, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		val versionsOptionalList = interpreter.argument(1)
		assert(versionsOptionalList.expressionsSize() == 1)
		val versions = versionsOptionalList.expressionAt(1)
		val versionsCount = versions.expressionsSize()
		val latestVersionPhrase = versions.lastExpression()
		assert(latestVersionPhrase.phraseKindIsUnder(LITERAL_PHRASE))
		val latestVersionToken = latestVersionPhrase.token()
		assert(latestVersionToken.isLiteralToken())
		val latestVersionString = latestVersionToken.literal().literal()
		for (i in 1 until versionsCount)
		{
			val oldVersionPhrase = versions.expressionAt(i)
			val oldVersion = oldVersionPhrase.token().literal().literal()
			if (latestVersionString.equals(oldVersion))
			{
				throw AvailRejectedParseException(
					STRONG,
					"module version $latestVersionString to be unique, not a "
						+  "duplicate of #$i in " + "the list (on line "
						+ "${oldVersionPhrase.token().lineNumber()})")
			}
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tupleFromArray(
				/* Module name */
				stringLiteralType,
				/* Optional versions */
				zeroOrOneList(zeroOrMoreList(stringLiteralType))),
			TOP.o())
}
