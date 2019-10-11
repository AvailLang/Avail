/*
 * P_DefinitionForArgumentTypes.java
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

package com.avail.interpreter.primitive.methods

import com.avail.descriptor.A_Atom
import com.avail.descriptor.A_Bundle
import com.avail.descriptor.A_Definition
import com.avail.descriptor.A_Tuple
import com.avail.descriptor.A_Type
import com.avail.descriptor.DefinitionDescriptor
import com.avail.descriptor.MessageBundleDescriptor
import com.avail.descriptor.MethodDescriptor
import com.avail.descriptor.TupleDescriptor
import com.avail.descriptor.TypeDescriptor
import com.avail.exceptions.MethodDefinitionException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.descriptor.TypeDescriptor.Types.DEFINITION
import com.avail.exceptions.AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import com.avail.exceptions.AvailErrorCode.E_NO_METHOD
import com.avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Lookup the unique [ ] in the specified [ ]'s [ method][MethodDescriptor] by the [tuple][TupleDescriptor] of parameter [ ].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_DefinitionForArgumentTypes : Primitive(2, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(2)
		val atom = interpreter.argument(0)
		val argTypes = interpreter.argument(1)
		val bundle = atom.bundleOrNil()
		try
		{
			if (bundle.equalsNil())
			{
				throw MethodDefinitionException.noMethod()
			}
			if (bundle.bundleMethod().numArgs() != argTypes.tupleSize())
			{
				return interpreter.primitiveFailure(
					E_INCORRECT_NUMBER_OF_ARGUMENTS)
			}
			val definition = bundle.bundleMethod().lookupByTypesFromTuple(argTypes)
			assert(!definition.equalsNil())
			return interpreter.primitiveSuccess(definition)
		}
		catch (e: MethodDefinitionException)
		{
			return interpreter.primitiveFailure(e.errorCode)
		}

	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(tuple(ATOM.o(), zeroOrMoreOf(
			anyMeta())), DEFINITION.o())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(set(E_NO_METHOD, E_NO_METHOD_DEFINITION,
		                           E_AMBIGUOUS_METHOD_DEFINITION,
		                           E_INCORRECT_NUMBER_OF_ARGUMENTS))
	}

}