/*
 * P_SetValue.kt
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
package avail.interpreter.primitive.variables

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.writeType
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.variables.VariableDescriptor
import avail.exceptions.AvailErrorCode.E_CANNOT_MODIFY_FINAL_JAVA_FIELD
import avail.exceptions.AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE
import avail.exceptions.AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE
import avail.exceptions.AvailErrorCode.E_JAVA_MARSHALING_FAILED
import avail.exceptions.AvailErrorCode.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED
import avail.exceptions.VariableSetException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.L2_SET_VARIABLE
import avail.interpreter.levelTwo.operation.L2_SET_VARIABLE_NO_CHECK
import avail.optimizer.L1Translator
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2Generator.Companion.edgeTo

/**
 * **Primitive:** Assign the [value][AvailObject] to the
 * [variable][VariableDescriptor].
 */
@Suppress("unused")
object P_SetValue : Primitive(2, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val variable = interpreter.argument(0)
		val value = interpreter.argument(1)
		return try
		{
			variable.setValue(value)
			interpreter.primitiveSuccess(nil)
		}
		catch (e: VariableSetException)
		{
			interpreter.primitiveFailure(e)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralVariableType,
				ANY.o),
			TOP.o)

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val (varReg, valueReg) = arguments
		val varType = varReg.type()
		val valueType = valueReg.type()
		val varInnerType = varType.writeType

		// These two operations have the same operand layouts.
		val setOperation = if (valueType.isSubtypeOf(varInnerType))
			L2_SET_VARIABLE_NO_CHECK
		else
			L2_SET_VARIABLE

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

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE,
				E_CANNOT_MODIFY_FINAL_JAVA_FIELD,
				E_JAVA_MARSHALING_FAILED,
				E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE,
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED))
}
