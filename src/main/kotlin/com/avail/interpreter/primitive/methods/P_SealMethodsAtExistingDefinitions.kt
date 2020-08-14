/*
 * P_SealMethodsAtExistingDefinitions.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.exceptions.AvailRuntimeException
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Seal the [named][A_Atom] [A_Method] at each existing
 * [definition][A_Definition]. Ignore macros and forward definitions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_SealMethodsAtExistingDefinitions : Primitive(1, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val methodNames = interpreter.argument(0)
		val loader = interpreter.fiber().availLoader()
			?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		val runtime = interpreter.runtime
		val module = interpreter.module()
		for (name in methodNames)
		{
			val bundle = name.bundleOrNil()
			if (!bundle.equalsNil())
			{
				// The definition tuple of a method can only be replaced in a
				// Level One safe zone. Like the vast majority of primitives,
				// this one runs in a Level One *unsafe* zone. Therefore it is
				// not necessary to lock the method while traversing its
				// definition tuple.
				val method = bundle.bundleMethod()
				val definitions = method.definitionsTuple()
				// Ignore macros.
				for (definition in definitions)
				{
					if (!definition.isForwardDefinition())
					{
						val function = definition.bodySignature()
						val params = function.argsTupleType()
						val signature =
							params.tupleOfTypesFromTo(1, method.numArgs())
						try
						{
							runtime.addSeal(name, signature)
							module.addSeal(name, signature)
						}
						catch (e: MalformedMessageException)
						{
							assert(false) { "This should not happen!" }
							throw AvailRuntimeException(e.errorCode)
						}

					}
				}
			}
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(setTypeForSizesContentType(wholeNumbers, ATOM.o)),
			TOP.o
		)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_LOADING_IS_OVER, E_CANNOT_DEFINE_DURING_COMPILATION))
}
