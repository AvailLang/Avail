/*
 * P_CreateFirstOfSequenceOfStatements.kt
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

import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.FirstOfSequencePhraseDescriptor
import com.avail.descriptor.phrases.FirstOfSequencePhraseDescriptor.Companion.newFirstOfSequenceNode
import com.avail.descriptor.sets.SetDescriptor.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.FIRST_OF_SEQUENCE_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.containsOnlyStatements
import com.avail.descriptor.types.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_SEQUENCE_CONTAINS_INVALID_STATEMENTS
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Create a [first-of-sequence][FirstOfSequencePhraseDescriptor]
 * phrase from the specified [tuple][TupleDescriptor] of statements.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_CreateFirstOfSequenceOfStatements : Primitive(1, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val statements = interpreter.argument(0)
		val statementsSize = statements.tupleSize()
		val flat = mutableListOf<A_Phrase>()
		for (i in 2 .. statementsSize)
		{
			statements.tupleAt(i).flattenStatementsInto(flat)
		}
		if (!containsOnlyStatements(flat, TOP.o()))
		{
			return interpreter.primitiveFailure(
				E_SEQUENCE_CONTAINS_INVALID_STATEMENTS)
		}
		flat.add(0, statements.tupleAt(1))
		return interpreter.primitiveSuccess(newFirstOfSequenceNode(statements))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				zeroOrMoreOf(PARSE_PHRASE.mostGeneralType())),
			FIRST_OF_SEQUENCE_PHRASE.mostGeneralType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_SEQUENCE_CONTAINS_INVALID_STATEMENTS))
}
