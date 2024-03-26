/*
 * P_DefinitionForArgumentTypes.kt
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

import avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.MessageBundleDescriptor
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_Method
import avail.descriptor.methods.A_Method.Companion.lookupByTypesFromTuple
import avail.descriptor.methods.A_Method.Companion.numArgs
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.DEFINITION
import avail.exceptions.AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION
import avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import avail.exceptions.AvailErrorCode.E_NO_METHOD
import avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION
import avail.exceptions.MethodDefinitionException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Lookup the unique [definition][A_Definition] in the specified
 * [message&#32;bundle's][MessageBundleDescriptor] [method][A_Method] by the
 * [tuple][A_Tuple] of parameter [types][A_Type].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_DefinitionForArgumentTypes : Primitive(2, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val atom = interpreter.argument(0)
		val argTypes = interpreter.argument(1)
		val bundle = atom.bundleOrNil
		try
		{
			if (bundle.isNil)
			{
				throw MethodDefinitionException.noMethod()
			}
			if (bundle.bundleMethod.numArgs != argTypes.tupleSize)
			{
				return interpreter.primitiveFailure(
					E_INCORRECT_NUMBER_OF_ARGUMENTS)
			}
			val definition =
				bundle.bundleMethod.lookupByTypesFromTuple(argTypes)
			assert(definition.notNil)
			return interpreter.primitiveSuccess(definition)
		}
		catch (e: MethodDefinitionException)
		{
			return interpreter.primitiveFailure(e.errorCode)
		}

	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(ATOM.o, zeroOrMoreOf(anyMeta)), DEFINITION.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(
			E_NO_METHOD,
			E_NO_METHOD_DEFINITION,
			E_AMBIGUOUS_METHOD_DEFINITION,
			E_INCORRECT_NUMBER_OF_ARGUMENTS))
}
