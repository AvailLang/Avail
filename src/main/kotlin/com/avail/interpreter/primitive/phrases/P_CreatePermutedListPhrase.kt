/*
 * P_CreatePermutedListPhrase.kt
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

package com.avail.interpreter.primitive.phrases

import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.one
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import com.avail.descriptor.phrases.ListPhraseDescriptor
import com.avail.descriptor.phrases.PermutedListPhraseDescriptor
import com.avail.descriptor.phrases.PermutedListPhraseDescriptor.Companion.newPermutedListNode
import com.avail.descriptor.sets.A_Set.Companion.setSize
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.asSet
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor.Companion.createInterval
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PERMUTED_LIST_PHRASE
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import com.avail.exceptions.AvailErrorCode.E_INCONSISTENT_ARGUMENT_REORDERING
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive**: Create a
 * [permuted&#32;list&#32;phrase][PermutedListPhraseDescriptor] from the given
 * [list][ListPhraseDescriptor] and permutation [tuple][TupleDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_CreatePermutedListPhrase : Primitive(2, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val list: A_Phrase = interpreter.argument(0)
		val permutation: A_Tuple = interpreter.argument(1)
		val size = permutation.tupleSize()
		return when
		{
			// Permutation is empty, or different size than list.
			size == 0 || size != list.expressionsTuple().tupleSize() ->
				interpreter.primitiveFailure(E_INCONSISTENT_ARGUMENT_REORDERING)
			// Permutation values are not all int32.
			permutation.any { !it.isInt } ->
				interpreter.primitiveFailure(E_INCONSISTENT_ARGUMENT_REORDERING)
			// Permutation values are not unique.
			size != permutation.asSet().setSize() ->
				interpreter.primitiveFailure(E_INCONSISTENT_ARGUMENT_REORDERING)
			// Entries are unique, but don't cover 1..N (pigeonhole principle).
			permutation.maxBy { it.extractInt() }!!.extractInt() != size ->
				interpreter.primitiveFailure(E_INCONSISTENT_ARGUMENT_REORDERING)
			// Permutation is the forbidden identity.
			permutation.equals(createInterval(one, fromInt(size), one)) ->
				interpreter.primitiveFailure(E_INCONSISTENT_ARGUMENT_REORDERING)
			else -> interpreter.primitiveSuccess(
				newPermutedListNode(list, permutation))
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				LIST_PHRASE.mostGeneralType(),
				oneOrMoreOf(naturalNumbers)),
			PERMUTED_LIST_PHRASE.mostGeneralType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INCONSISTENT_ARGUMENT_REORDERING))
}
