/*
 * P_PrivateCreateModuleVariable.kt
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
package avail.interpreter.primitive.modules

import avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import avail.descriptor.module.A_Module.Companion.addConstantBinding
import avail.descriptor.module.A_Module.Companion.addVariableBinding
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.MODULE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableMeta
import avail.descriptor.variables.VariableSharedGlobalDescriptor
import avail.descriptor.variables.VariableSharedGlobalDescriptor.Companion.createGlobal
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.Flag.Private
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Create a
 * [global&#32;variable&#32;or&#32;constant][VariableSharedGlobalDescriptor],
 * registering it with the given module.
 */
object P_PrivateCreateModuleVariable
	: Primitive(5, CanFold, CanInline, Private, CannotFail)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(5)
		val (module, name, varType, isConstantObject, stablyComputedObject) =
			interpreter.argsBuffer

		val isConstant = isConstantObject.extractBoolean
		val stablyComputed = stablyComputedObject.extractBoolean

		assert(isConstant || !stablyComputed)

		val variable = createGlobal(varType, module, name, isConstant)
		if (stablyComputed)
		{
			variable.setValueWasStablyComputed(true)
		}
		// The compiler should ensure this will always succeed.
		when
		{
			isConstant -> module.addConstantBinding(name, variable)
			else -> module.addVariableBinding(name, variable)
		}
		return interpreter.primitiveSuccess(variable)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				MODULE.o,
				stringType,
				mostGeneralVariableMeta,
				booleanType,
				booleanType),
			TOP.o)
}
