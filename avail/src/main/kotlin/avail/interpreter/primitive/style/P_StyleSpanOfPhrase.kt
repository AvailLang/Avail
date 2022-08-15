/*
 * P_StyleSpanOfPhrase.kt
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
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.allTokens
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.A_Token.Companion.pastEnd
import avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.RepeatedElementTupleDescriptor.Companion.createRepeatedElementTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Apply the given style name to the region of the file bounded
 * by the given phrase.  This includes whitespace and comments, as well as the
 * subphrases and tokens within this phrase.  If the "overwrite" argument is
 * true, the style for that region is replaced, otherwise the style name is
 * appended (with a separating ",") to the existing style name.  If the
 * "overwrite" argument is true, but the style name is the empty string, clear
 * all styles for that range.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_StyleSpanOfPhrase : Primitive(3, CanInline, WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val phrase: A_Phrase = interpreter.argument(0)
		val styleName: A_String = interpreter.argument(1)
		val overwrite = interpreter.argument(2).extractBoolean

		val loader = interpreter.fiber().availLoader
			?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}

		val allTokens = phrase.allTokens
		if (allTokens.tupleSize == 0)
		{
			// Nothing to style.
			return interpreter.primitiveSuccess(nil)
		}
		val firstToken = allTokens.tupleAt(1)
		val start = firstToken.start()
		val pastEnd = allTokens.tupleAt(allTokens.tupleSize).pastEnd()
		if (start == pastEnd)
		{
			// Span is zero width.  Ignore it.
			return interpreter.primitiveSuccess(nil)
		}
		val styleOrNull = when (styleName.tupleSize)
		{
			0 -> null
			else -> styleName.asNativeString()
		}
		if (styleOrNull === null && !overwrite)
		{
			return interpreter.primitiveSuccess(nil)
		}
		val fakeToken = literalToken(
			createRepeatedElementTuple(
				pastEnd - start, fromCodePoint('?'.code)
			) as A_String,
			start,
			firstToken.lineNumber(),
			nil,
			nil)
		fakeToken.setCurrentModule(loader.module)
		loader.styleToken(fakeToken, styleOrNull, overwrite)
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION))

	override fun privateBlockTypeRestriction(): A_Type = functionType(
		tuple(
			PARSE_PHRASE.mostGeneralType,
			stringType,
			booleanType),
		Types.TOP.o)
}
