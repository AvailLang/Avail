/*
 * P_CreateBlockExpression.kt
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

import avail.descriptor.objects.ObjectTypeDescriptor.Companion.Exceptions.exceptionType
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.flattenStatementsInto
import avail.descriptor.phrases.BlockPhraseDescriptor
import avail.descriptor.phrases.BlockPhraseDescriptor.Companion.newBlockNode
import avail.descriptor.phrases.PhraseDescriptor.Companion.containsOnlyStatements
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.ARGUMENT_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.BLOCK_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.exceptions.AvailErrorCode.E_BLOCK_CONTAINS_INVALID_STATEMENTS
import avail.exceptions.AvailErrorCode.E_INVALID_PRIMITIVE_NAME
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.PrimitiveHolder.Companion.primitiveByName
import avail.interpreter.execution.Interpreter

/**
* **Primitive:** Create a [block&#32;expression][BlockPhraseDescriptor] from
 * the specified [argument&#32;declarations][PhraseKind.ARGUMENT_PHRASE],
 * primitive name, statements, result type, and exception set.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_CreateBlockExpression : Primitive(5, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(5)
		val argDecls = interpreter.argument(0)
		val primitiveName = interpreter.argument(1)
		val statements = interpreter.argument(2)
		val resultType = interpreter.argument(3)
		val exceptions = interpreter.argument(4)
		// Verify that each element of "statements" is actually a statement,
		// and that the last statement's expression type agrees with
		// "resultType".
		val flat = mutableListOf<A_Phrase>()
		statements.forEach { it.flattenStatementsInto(flat) }
		val primitive = when (primitiveName.tupleSize)
		{
			0 -> null
			else -> primitiveByName(primitiveName.asNativeString())
				?: return interpreter.primitiveFailure(
					E_INVALID_PRIMITIVE_NAME)
		}
		if (!containsOnlyStatements(flat, resultType))
		{
			return interpreter.primitiveFailure(
				E_BLOCK_CONTAINS_INVALID_STATEMENTS)
		}
		val block = newBlockNode(
			argDecls,
			primitive,
			statements,
			resultType,
			exceptions,
			0)
		return interpreter.primitiveSuccess(block)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				zeroOrMoreOf(ARGUMENT_PHRASE.mostGeneralType),
				stringType,
				zeroOrMoreOf(PARSE_PHRASE.mostGeneralType),
				topMeta(),
				setTypeForSizesContentType(wholeNumbers, exceptionType)),
			BLOCK_PHRASE.mostGeneralType)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_BLOCK_CONTAINS_INVALID_STATEMENTS,
				E_INVALID_PRIMITIVE_NAME))
}
