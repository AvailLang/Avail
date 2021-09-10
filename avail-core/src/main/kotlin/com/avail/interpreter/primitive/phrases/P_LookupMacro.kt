/*
 * P_LookupMacro.kt
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

import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import com.avail.descriptor.bundles.A_Bundle.Companion.lookupMacroByPhraseTuple
import com.avail.descriptor.bundles.A_Bundle.Companion.numArgs
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.methods.A_Macro
import com.avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.exceptions.AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import com.avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.ReadsFromHiddenGlobalState
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive LookupMacro**: Given an [atom][A_Atom] and a tuple of
 * [phrases][A_Phrase], look up the body [function][A_Function] of the
 * applicable [macro][A_Macro].  *Do not* execute it.  The returned function
 * should accept the given tuple of phrases.
 *
 * The primitive fails if the number of arguments is incorrect, or if there is
 * not exactly one most-specific macro definition applicable for the supplied
 * phrases.  An atom that does not have a bundle is considered to have zero
 * applicable macro definitions.
 *
 * Note that the lookup does not take a current module into account, so each
 * macro definition of the atom/bundle is eligible to be returned.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_LookupMacro : Primitive(2, CanInline, ReadsFromHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val (atom: A_Atom, argPhrasesTuple: A_Tuple) = interpreter.argsBuffer
		val bundle = atom.bundleOrNil
		if (bundle.isNil)
		{
			return interpreter.primitiveFailure(E_NO_METHOD_DEFINITION)
		}
		val bundleArgCount = bundle.numArgs
		if (argPhrasesTuple.tupleSize != bundleArgCount)
		{
			return interpreter.primitiveFailure(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		val bestMacros = bundle.lookupMacroByPhraseTuple(argPhrasesTuple)
		return when (bestMacros.tupleSize)
		{
			0 -> interpreter.primitiveFailure(E_NO_METHOD_DEFINITION)
			1 -> interpreter.primitiveSuccess(bestMacros.tupleAt(1).bodyBlock())
			else -> interpreter.primitiveFailure(E_AMBIGUOUS_METHOD_DEFINITION)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				ATOM.o,
				zeroOrMoreOf(PARSE_PHRASE.mostGeneralType())),
			functionTypeReturning(PARSE_PHRASE.mostGeneralType()))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_NO_METHOD_DEFINITION,
				E_AMBIGUOUS_METHOD_DEFINITION))
}
