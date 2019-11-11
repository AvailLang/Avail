/*
 * P_PrivateCreateModuleVariable.kt
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
package com.avail.interpreter.primitive.modules

import com.avail.descriptor.A_Type
import com.avail.descriptor.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TypeDescriptor.Types.MODULE
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.descriptor.VariableSharedGlobalDescriptor
import com.avail.descriptor.VariableSharedGlobalDescriptor.createGlobal
import com.avail.descriptor.VariableTypeDescriptor.variableMeta
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * **Primitive:** Create a
 * [global variable or constant][VariableSharedGlobalDescriptor], registering it
 * with the given module.
 */
object P_PrivateCreateModuleVariable
	: Primitive(5, CanFold, CanInline, Private, CannotFail)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(5)
		val module = interpreter.argument(0)
		val name = interpreter.argument(1)
		val varType = interpreter.argument(2)
		val isConstant =
			interpreter.argument(3).extractBoolean()
		val stablyComputed =
			interpreter.argument(4).extractBoolean()

		assert(isConstant || !stablyComputed)

		val variable =
			createGlobal(varType, module, name, isConstant)
		if (stablyComputed)
		{
			variable.valueWasStablyComputed(true)
		}
		// The compiler should ensure this will always succeed.
		if (isConstant)
		{
			module.addConstantBinding(name, variable)
		}
		else
		{
			module.addVariableBinding(name, variable)
		}
		return interpreter.primitiveSuccess(variable)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				MODULE.o(),
				stringType(),
				variableMeta(),
				booleanType(),
				booleanType()),
			TOP.o())
}
