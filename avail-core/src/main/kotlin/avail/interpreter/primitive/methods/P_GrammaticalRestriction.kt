/*
 * P_GrammaticalRestriction.kt
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
package avail.interpreter.primitive.methods

import avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import avail.descriptor.module.A_Module.Companion.trueNamesForStringName
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.exceptions.AmbiguousNameException
import avail.exceptions.AvailErrorCode.E_AMBIGUOUS_NAME
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.exceptions.MalformedMessageException
import avail.exceptions.SignatureException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Unknown
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Message precedence declaration with [tuple][TupleDescriptor]
 * of [sets][SetDescriptor] of messages to exclude for each argument position.
 * Note that the tuple's elements should correspond with occurrences of
 * underscore in the method names, *not* with the (top-level) arguments of the
 * method. This distinction is only apparent when guillemet notation is used to
 * accept tuples of arguments.
 *
 * Note that if any of the provided names are ambiguous, *all* of the visible
 * atoms with that name will be restricted.
 */
@Suppress("unused")
object P_GrammaticalRestriction : Primitive(2, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val parentStrings = interpreter.argument(0)
		val excludedStringSets = interpreter.argument(1)
		val loader = interpreter.availLoaderOrNull() ?:
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		val excludedAtomSets =
			generateObjectTupleFrom(excludedStringSets.tupleSize) {
				excludedStringSets.tupleAt(it)
					.fold(emptySet) { set, string ->
						var atoms = loader.module.trueNamesForStringName(string)
						if (atoms.setSize == 0)
						{
							// Auto-create it if it doesn't exist yet.
							atoms = set(loader.lookupName(string))
						}
						set.setUnionCanDestroy(atoms, true)
					}
			}
		try
		{
			val parentAtoms = parentStrings.fold(emptySet) { set, string ->
				var atoms = loader.module.trueNamesForStringName(string)
				if (atoms.setSize == 0)
				{
					// Auto-create it if it doesn't exist yet.
					atoms = set(loader.lookupName(string))
				}
				set.setUnionCanDestroy(atoms, true)
			}
			loader.addGrammaticalRestrictions(parentAtoms, excludedAtomSets)
		}
		catch (e: MalformedMessageException)
		{
			return interpreter.primitiveFailure(e)
		}
		catch (e: SignatureException)
		{
			return interpreter.primitiveFailure(e)
		}
		catch (e: AmbiguousNameException)
		{
			return interpreter.primitiveFailure(e)
		}

		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				setTypeForSizesContentType(naturalNumbers, stringType),
				zeroOrMoreOf(
					setTypeForSizesContentType(wholeNumbers, stringType))),
			TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION,
				E_AMBIGUOUS_NAME,
				E_INCORRECT_NUMBER_OF_ARGUMENTS)
			.setUnionCanDestroy(possibleErrors, true))
}
