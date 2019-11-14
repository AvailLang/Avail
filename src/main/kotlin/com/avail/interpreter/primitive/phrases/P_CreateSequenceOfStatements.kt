/*
 * P_CreateSequenceOfStatements.kt
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

import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.SEQUENCE_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.containsOnlyStatements
import com.avail.descriptor.SequencePhraseDescriptor
import com.avail.descriptor.SequencePhraseDescriptor.newSequence
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleDescriptor
import com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.descriptor.parsing.A_Phrase
import com.avail.exceptions.AvailErrorCode.E_SEQUENCE_CONTAINS_INVALID_STATEMENTS
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Create a [sequence][SequencePhraseDescriptor] from the
 * specified [tuple][TupleDescriptor] of statements.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_CreateSequenceOfStatements : Primitive(1, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val statements = interpreter.argument(0)
		val flat = mutableListOf<A_Phrase>()
		for (statement in statements)
		{
			statement.flattenStatementsInto(flat)
		}
		if (!containsOnlyStatements(flat, TOP.o()))
		{
			return interpreter.primitiveFailure(
				E_SEQUENCE_CONTAINS_INVALID_STATEMENTS)
		}
		return interpreter.primitiveSuccess(newSequence(statements))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				zeroOrMoreOf(PARSE_PHRASE.mostGeneralType())),
			SEQUENCE_PHRASE.mostGeneralType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_SEQUENCE_CONTAINS_INVALID_STATEMENTS))
}