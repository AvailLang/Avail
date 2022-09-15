/*
 * P_CreateLiteralToken.kt
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

package avail.interpreter.primitive.phrases

import avail.descriptor.fiber.A_Fiber.Companion.currentLexer
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.A_Token
import avail.descriptor.tokens.LiteralTokenDescriptor
import avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.literalTokenType
import avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.mostGeneralLiteralTokenType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import avail.exceptions.AvailErrorCode.E_EXCEEDS_VM_LIMIT
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter

/**
* **Primitive:** Create a [literal&#32;token][LiteralTokenDescriptor] with the
 * specified literal value, [lexeme][A_Token.string],
 * [starting&#32;character&#32;position][A_Token.start], and
 * [line&#32;number][A_Token.lineNumber].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_CreateLiteralToken : Primitive(5, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(5)
		val (value, lexeme, start, line, optionalGeneratingPhrase) =
			interpreter.argsBuffer
		if (!start.isInt || !line.isInt || line.extractInt >= (1L shl 28))
		{
			// The low end was already limited by the primitive's argument type
			// restrictions.
			return interpreter.primitiveFailure(E_EXCEEDS_VM_LIMIT)
		}
		val generatingPhrase = when (optionalGeneratingPhrase.tupleSize)
		{
			0 -> nil
			else -> optionalGeneratingPhrase.tupleAt(1)
		}
		return interpreter.primitiveSuccess(
			literalToken(
				lexeme,
				start.extractInt,
				line.extractInt,
				value,
				interpreter.fiber().currentLexer,
				generatingPhrase))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val valueType = argumentTypes[0]
		//val lexemeType = argumentTypes[1]
		//val startType = argumentTypes[2]
		//val lineType = argumentTypes[3]
		//val optionalGeneratingPhraseType = argumentTypes[4]

		return literalTokenType(valueType)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				ANY.o,
				stringType,
				wholeNumbers,
				wholeNumbers,
				zeroOrOneOf(PARSE_PHRASE.mostGeneralType)),
			mostGeneralLiteralTokenType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_EXCEEDS_VM_LIMIT))
}
