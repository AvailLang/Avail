/*
 * P_CreateToken.kt
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

package com.avail.interpreter.primitive.phrases

import com.avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tokens.TokenDescriptor.Companion.newToken
import com.avail.descriptor.tokens.TokenDescriptor.StaticInit.tokenTypeOrdinalKey
import com.avail.descriptor.tokens.TokenDescriptor.TokenType
import com.avail.descriptor.tokens.TokenDescriptor.TokenType.COMMENT
import com.avail.descriptor.tokens.TokenDescriptor.TokenType.END_OF_FILE
import com.avail.descriptor.tokens.TokenDescriptor.TokenType.KEYWORD
import com.avail.descriptor.tokens.TokenDescriptor.TokenType.OPERATOR
import com.avail.descriptor.tokens.TokenDescriptor.TokenType.WHITESPACE
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import com.avail.descriptor.types.TokenTypeDescriptor.Companion.tokenType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import com.avail.descriptor.types.TypeDescriptor.Types.TOKEN
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Create a [token][TokenDescriptor] with the specified
 * [TokenType], [lexeme][A_Token.string],
 * [starting&#32;character&#32;position][A_Token.start], and
 * [line&#32;number][A_Token.lineNumber].
 *
 * @author Todd L Smith &lt;tsmith@safetyweb.org&gt;
 */
@Suppress("unused")
object P_CreateToken : Primitive(4, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(4)
		val type = interpreter.argument(0)
		val lexeme = interpreter.argument(1)
		val start = interpreter.argument(2)
		val line = interpreter.argument(3)
		return interpreter.primitiveSuccess(
			newToken(
				lexeme,
				start.extractInt(),
				line.extractInt(),
				TokenType.lookupTokenType(
					type.getAtomProperty(tokenTypeOrdinalKey).extractInt())))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val atomType = argumentTypes[0]
		// final A_Type lexemeType = argumentTypes.get(1);
		// final A_Type startType = argumentTypes.get(2);
		// final A_Type lineType = argumentTypes.get(3);

		if (atomType.instanceCount().equalsInt(1))
		{
			val atom = atomType.instance()
			return tokenType(
				TokenType.lookupTokenType(
					atom.getAtomProperty(tokenTypeOrdinalKey).extractInt()))
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
				stringType(),
				inclusive(0L, (1L shl 32) - 1),
				inclusive(0L, (1L shl 28) - 1)),
			TOKEN.o
		)
}
