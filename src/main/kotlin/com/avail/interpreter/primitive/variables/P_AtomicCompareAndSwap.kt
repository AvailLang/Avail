/*
 * P_AtomicCompareAndSwap.kt
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

package com.avail.interpreter.primitive.variables

import com.avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import com.avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import com.avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.writeType
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import com.avail.descriptor.variables.A_Variable
import com.avail.exceptions.AvailErrorCode.E_CANNOT_MODIFY_FINAL_JAVA_FIELD
import com.avail.exceptions.AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE
import com.avail.exceptions.AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE
import com.avail.exceptions.AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE
import com.avail.exceptions.AvailErrorCode.E_JAVA_MARSHALING_FAILED
import com.avail.exceptions.AvailErrorCode.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED
import com.avail.exceptions.VariableGetException
import com.avail.exceptions.VariableSetException
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operation.L2_VARIABLE_COMPARE_AND_SWAP_NO_CHECK
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L2Generator.Companion.edgeTo

/**
 * **Primitive:** Atomically read and conditionally overwrite the specified
 * [variable][A_Variable]. The overwrite occurs only if the value read from the
 * variable equals the reference value.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_AtomicCompareAndSwap : Primitive(3, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val variable = interpreter.argument(0)
		val reference = interpreter.argument(1)
		val newValue = interpreter.argument(2)
		return try {
			interpreter.primitiveSuccess(
				objectFromBoolean(
					variable.compareAndSwapValues(reference, newValue)))
		} catch (e: VariableGetException) {
			interpreter.primitiveFailure(e)
		} catch (e: VariableSetException) {
			interpreter.primitiveFailure(e)
		}
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: L1Translator.CallSiteHelper
	): Boolean
	{
		val variableReg = arguments[0]
		val referenceReg = arguments[1]
		val newValueReg = arguments[2]

		if (!newValueReg.type().isSubtypeOf(variableReg.type().writeType()))
		{
			// We can't guarantee the type being assigned is strong enough.
			// Fall back.
			return super.tryToGenerateSpecialPrimitiveInvocation(
				functionToCallReg,
				rawFunction,
				arguments,
				argumentTypes,
				translator,
				callSiteHelper)
		}
		val generator = translator.generator
		val success = generator.createBasicBlock("swap success")
		val failure = generator.createBasicBlock("swap failure")
		val exception = generator.createBasicBlock("swap exception")
		translator.addInstruction(
			L2_VARIABLE_COMPARE_AND_SWAP_NO_CHECK,
			variableReg,
			referenceReg,
			newValueReg,
			edgeTo(success),
			edgeTo(failure),
			edgeTo(exception))
		generator.startBlock(success)
		callSiteHelper.useAnswer(generator.boxedConstant(trueObject))

		generator.startBlock(failure)
		callSiteHelper.useAnswer(generator.boxedConstant(falseObject))

		generator.startBlock(exception)
		translator.generateGeneralFunctionInvocation(
			functionToCallReg, arguments, false, callSiteHelper)

		return true
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralVariableType(),
				ANY.o,
				ANY.o
			),
			booleanType)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_CANNOT_READ_UNASSIGNED_VARIABLE,
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE,
				E_CANNOT_MODIFY_FINAL_JAVA_FIELD,
				E_JAVA_MARSHALING_FAILED,
				E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE,
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED))
}
