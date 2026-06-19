/*
 * P_SealMethodsAtExistingDefinitions.kt
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

import avail.descriptor.representation.A_Atom
import avail.descriptor.representation.A_Atom.Companion.bundleOrNil
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Bundle.Companion.bundleMethod
import avail.descriptor.representation.A_Definition
import avail.descriptor.representation.A_Fiber.Companion.availLoader
import avail.descriptor.representation.A_Method
import avail.descriptor.representation.A_Method.Companion.definitionsTuple
import avail.descriptor.representation.A_Method.Companion.numArgs
import avail.descriptor.representation.A_Module.Companion.addSeal
import avail.descriptor.representation.A_Sendable.Companion.bodySignature
import avail.descriptor.representation.A_Sendable.Companion.isForwardDefinition
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.argsTupleType
import avail.descriptor.representation.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.exceptions.MalformedMessageException
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive1

/**
 * **Primitive:** Seal the [named][A_Atom] [A_Method] at each existing
 * [definition][A_Definition]. Ignore macros and forward definitions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_SealMethodsAtExistingDefinitions : Primitive1(CanInline, HasSideEffect)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val methodNames = arg1
		val loader = fiber().availLoader ?: return fail(E_LOADING_IS_OVER)
		if (!loader.phase.isExecuting)
		{
			return fail(E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		val module = module()
		for (name in methodNames)
		{
			val bundle = name.bundleOrNil
			if (bundle.notNil)
			{
				// The definition tuple of a method can only be replaced during
				// a safe point. Like the vast majority of primitives, this one
				// runs in an interpreter task, which is mutually exclusive of
				// safe points. Therefore, it is not necessary to lock the
				// method while traversing its definition tuple.
				val method = bundle.bundleMethod
				val definitions = method.definitionsTuple
				// Ignore macros.
				for (definition in definitions)
				{
					if (!definition.isForwardDefinition())
					{
						val function = definition.bodySignature()
						val params = function.argsTupleType
						val signature =
							params.tupleOfTypesFromTo(1, method.numArgs)
						try
						{
							runtime.addSeal(name, signature)
							module.addSeal(name, signature)
						}
						catch (e: MalformedMessageException)
						{
							throw AssertionError("This should not happen!", e)
						}

					}
				}
			}
		}
		return nil
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(setTypeForSizesContentType(wholeNumbers, ATOM())),
			TOP())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_LOADING_IS_OVER, E_CANNOT_DEFINE_DURING_COMPILATION))
}
