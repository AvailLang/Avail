/*
 * P_GetValue.kt
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
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.readType
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.variables.A_Variable.Companion.getValue
import avail.descriptor.variables.VariableDescriptor
import avail.exceptions.AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE
import avail.exceptions.AvailErrorCode.E_JAVA_MARSHALING_FAILED
import avail.exceptions.VariableGetException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operation.variables.L2_GET_VARIABLE
import avail.optimizer.CallSiteHelper
import avail.optimizer.L2Generator.Companion.edgeTo

/**
 * **Primitive:** There are two possibilities.  The
 * [variable][VariableDescriptor] is mutable, in which case we want to destroy
 * it, or the variable is immutable, in which case we want to make sure the
 * extracted value becomes immutable (in case the variable is being held onto by
 * something). Since the primitive invocation code is going to erase it if it's
 * mutable anyhow, only the second case requires any real work.
 */
@Suppress("unused")
object P_GetValue : Primitive(1, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val variable = interpreter.argument(0)
		return try
		{
			interpreter.primitiveSuccess(variable.getValue())
		}
		catch (e: VariableGetException)
		{
			interpreter.primitiveFailure(e)
		}
	}

	/**
	 * If the variable had a reactor, reading it can activate that reactor,
	 * which might cause a variable captured in it to become shared.
	 */
	override fun mightMakeEscapedVariableShared(
		argumentTypes: List<A_Type>
	): Boolean = true

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralVariableType),
			ANY.o)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>): A_Type
	{
		val varType = argumentTypes[0]
		val readType = varType.readType
		return if (readType.isTop) ANY.o else readType
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper,
		arguments: List<L2ReadBoxedOperand>): Boolean
	{
		val varReg = arguments[0]
		val varType = varReg.type()
		val varInnerType = varType.readType

		val translator = callSiteHelper.translator
		val success = translator.createBasicBlock("get value success")
		val failure = translator.createBasicBlock("get value failure/observe")
		val extractedValue = translator.boxedWriteTemp(
			"extracted",
			boxedRestrictionForType(varInnerType))
		// Emit the get-variable instruction.
		translator.addInstruction(
			L2_GET_VARIABLE(
				varReg,
				extractedValue,
				edgeTo(success),
				edgeTo(failure)))

		// Emit the failure path, which is the fallback to invoking the
		// primitive function and having it fail (presumably) into its failure
		// handling code.
		translator.startBlock(failure)
		translator.generateGeneralFunctionInvocation(
			functionToCallReg, false, callSiteHelper, arguments)

		// And now the success path.  Note that the failure path could have
		// also made it to the callSiteHelper's after-everything block if the
		// call returns successfully.
		translator.startBlock(success)
		// Reading the variable can't cause any local variables to become shared
		// or acquire reactors.
		callSiteHelper.useAnswer(translator.readBoxed(extractedValue), false)
		return true
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(
			E_CANNOT_READ_UNASSIGNED_VARIABLE,
			E_JAVA_MARSHALING_FAILED))
}
