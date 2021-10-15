/*
 * P_TokenClassifier.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import avail.descriptor.atoms.A_Atom
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.TokenDescriptor
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.tokens.TokenDescriptor.TokenType.COMMENT
import avail.descriptor.tokens.TokenDescriptor.TokenType.END_OF_FILE
import avail.descriptor.tokens.TokenDescriptor.TokenType.KEYWORD
import avail.descriptor.tokens.TokenDescriptor.TokenType.LITERAL
import avail.descriptor.tokens.TokenDescriptor.TokenType.OPERATOR
import avail.descriptor.tokens.TokenDescriptor.TokenType.WHITESPACE
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.tokenType
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOKEN
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Get the specified [token][TokenDescriptor]'s
 * [classifier][TokenType], which is an [atom][A_Atom].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_TokenClassifier : Primitive(1, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val token = interpreter.argument(0)
		return interpreter.primitiveSuccess(
			TokenType.lookupTokenType(token.tokenType().ordinal).atom)
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val tokenType = argumentTypes[0]
		if (tokenType.instanceCount.equalsInt(1))
		{
			return instanceType(tokenType.tokenType.atom)
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				TOKEN.o),
			enumerationWith(
				set(
					END_OF_FILE.atom,
					KEYWORD.atom,
					LITERAL.atom,
					OPERATOR.atom,
					COMMENT.atom,
					WHITESPACE.atom)))
}