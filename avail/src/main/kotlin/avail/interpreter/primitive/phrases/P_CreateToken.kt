/*
 * P_CreateToken.kt
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

import avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import avail.descriptor.fiber.A_Fiber.Companion.currentLexer
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.A_Token
import avail.descriptor.tokens.TokenDescriptor.Companion.newToken
import avail.descriptor.tokens.TokenDescriptor.StaticInit.tokenTypeOrdinalKey
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.tokens.TokenDescriptor.TokenType.COMMENT
import avail.descriptor.tokens.TokenDescriptor.TokenType.END_OF_FILE
import avail.descriptor.tokens.TokenDescriptor.TokenType.KEYWORD
import avail.descriptor.tokens.TokenDescriptor.TokenType.OPERATOR
import avail.descriptor.tokens.TokenDescriptor.TokenType.WHITESPACE
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOKEN
import avail.descriptor.types.TokenTypeDescriptor.Companion.tokenType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.exceptions.AvailErrorCode.E_EXCEEDS_VM_LIMIT
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Create a [token][A_Token] with the specified
 * [TokenType], [lexeme][A_Token.string],
 * [starting&#32;character&#32;position][A_Token.start], and
 * [line&#32;number][A_Token.lineNumber].
 *
 * @author Todd L Smith &lt;tsmith@safetyweb.org&gt;
 */
@Suppress("unused")
object P_CreateToken : Primitive(4, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(4)
		val (type, lexeme, start, line) = interpreter.argsBuffer
		if (!start.isInt || !line.isInt || line.extractInt >= (1L shl 28))
		{
			// The low end was already limited by the primitive's argument type
			// restrictions.
			return interpreter.primitiveFailure(E_EXCEEDS_VM_LIMIT)
		}
		return interpreter.primitiveSuccess(
			newToken(
				lexeme,
				start.extractInt,
				line.extractInt,
				TokenType.lookupTokenType(
					type.getAtomProperty(tokenTypeOrdinalKey).extractInt),
				interpreter.fiber().currentLexer))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val atomType = argumentTypes[0]
		// final A_Type lexemeType = argumentTypes.get(1);
		// final A_Type startType = argumentTypes.get(2);
		// final A_Type lineType = argumentTypes.get(3);

		if (atomType.instanceCount.equalsInt(1))
		{
			val atom = atomType.instance
			return tokenType(
				TokenType.lookupTokenType(
					atom.getAtomProperty(tokenTypeOrdinalKey).extractInt))
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		FunctionTypeDescriptor.functionType(
			tuple(
				enumerationWith(
					set(
						END_OF_FILE.atom,
						KEYWORD.atom,
						OPERATOR.atom,
						COMMENT.atom,
						WHITESPACE.atom)),
				stringType,
				wholeNumbers,
				wholeNumbers),
			TOKEN.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_EXCEEDS_VM_LIMIT))
}
