/*
 * P_SetValue.java
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
package com.avail.interpreter.primitive.variables

import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.AvailObject
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.descriptor.VariableDescriptor
import com.avail.descriptor.VariableTypeDescriptor.mostGeneralVariableType
import com.avail.exceptions.AvailErrorCode.*
import com.avail.exceptions.VariableSetException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operation.L2_SET_VARIABLE
import com.avail.interpreter.levelTwo.operation.L2_SET_VARIABLE_NO_CHECK
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import com.avail.optimizer.L2Generator.edgeTo

/**
 * **Primitive:** Assign the [value][AvailObject]
 * to the [variable][VariableDescriptor].
 */
object P_SetValue : Primitive(2, CanInline, HasSideEffect)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val `var` = interpreter.argument(0)
		val value = interpreter.argument(1)
		try
		{
			`var`.setValue(value)
		}
		catch (e: VariableSetException)
		{
			return interpreter.primitiveFailure(e)
		}

		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				mostGeneralVariableType(),
				ANY.o()),
			TOP.o())
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val varReg = arguments[0]
		val valueReg = arguments[1]

		val varType = varReg.type()
		val valueType = valueReg.type()
		val varInnerType = varType.writeType()

		// These two operations have the same operand layouts.
		val setOperation = if (valueType.isSubtypeOf(varInnerType))
			L2_SET_VARIABLE_NO_CHECK.instance
		else
			L2_SET_VARIABLE.instance

		val generator = translator.generator
		val success = generator.createBasicBlock("set local success")
		val failure = generator.createBasicBlock("set local failure")
		// Emit the set-variable instruction.
		translator.addInstruction(
			setOperation,
			varReg,
			valueReg,
			edgeTo(success),
			edgeTo(failure))

		// Emit the failure path.  Simply invoke the primitive function.
		generator.startBlock(failure)
		translator.generateGeneralFunctionInvocation(
			functionToCallReg, arguments, false, callSiteHelper)

		// End with the success block.  Note that the failure path could have
		// also made it to the callSiteHelper's after-everything block if the
		// call returns successfully.
		generator.startBlock(success)
		callSiteHelper.useAnswer(generator.boxedConstant(nil))
		return true
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE,
				E_CANNOT_MODIFY_FINAL_JAVA_FIELD,
				E_JAVA_MARSHALING_FAILED,
				E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE,
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED))
	}

}