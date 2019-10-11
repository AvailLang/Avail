/*
 * P_CreateBlockExpression.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.descriptor.A_Phrase
import com.avail.descriptor.A_Set
import com.avail.descriptor.A_String
import com.avail.descriptor.A_Tuple
import com.avail.descriptor.A_Type
import com.avail.descriptor.AvailObject
import com.avail.descriptor.BlockPhraseDescriptor
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import java.util.ArrayList

import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.BlockPhraseDescriptor.newBlockNode
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.topMeta
import com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.ObjectTypeDescriptor.exceptionType
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.ARGUMENT_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.BLOCK_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.containsOnlyStatements
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.exceptions.AvailErrorCode.E_BLOCK_CONTAINS_INVALID_STATEMENTS
import com.avail.exceptions.AvailErrorCode.E_INVALID_PRIMITIVE_NUMBER
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Create a [ block expression][BlockPhraseDescriptor] from the specified [ argument declarations][PhraseKind.ARGUMENT_PHRASE], primitive number, statements, result type, and
 * exception set.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_CreateBlockExpression : Primitive(5, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
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
		val flat = ArrayList<A_Phrase>(statements.tupleSize() + 3)
		for (statement in statements)
		{
			statement.flattenStatementsInto(flat)
		}
		val primNumber: Int
		if (primitiveName.tupleSize() > 0)
		{
			val primitive = Primitive.primitiveByName(primitiveName.asNativeString())
			                ?: return interpreter.primitiveFailure(E_INVALID_PRIMITIVE_NUMBER)
			primNumber = primitive.primitiveNumber
		}
		else
		{
			primNumber = 0
		}
		if (!containsOnlyStatements(flat, resultType))
		{
			return interpreter.primitiveFailure(
				E_BLOCK_CONTAINS_INVALID_STATEMENTS)
		}
		val block = newBlockNode(
			argDecls,
			primNumber,
			statements,
			resultType,
			exceptions,
			0,
			emptyTuple())
		return interpreter.primitiveSuccess(block)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				zeroOrMoreOf(ARGUMENT_PHRASE.mostGeneralType()),
				stringType(),
				zeroOrMoreOf(PARSE_PHRASE.mostGeneralType()),
				topMeta(),
				setTypeForSizesContentType(wholeNumbers(), exceptionType())),
			BLOCK_PHRASE.mostGeneralType())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(
				E_BLOCK_CONTAINS_INVALID_STATEMENTS,
				E_INVALID_PRIMITIVE_NUMBER))
	}

}