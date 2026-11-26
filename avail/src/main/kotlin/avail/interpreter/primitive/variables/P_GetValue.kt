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

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Fiber.Companion.recordVariableAccess
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.readType
import avail.descriptor.representation.A_Variable.Companion.getValue
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.variables.VariableDescriptor
import avail.exceptions.AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE
import avail.exceptions.AvailErrorCode.E_JAVA_MARSHALING_FAILED
import avail.exceptions.VariableGetException
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operation.variables.L2_GET_VARIABLE
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive1
import avail.optimizer.L2CallSite
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2GeneratorInterface

/**
 * **Primitive:** There are two possibilities.  The
 * [variable][VariableDescriptor] is mutable, in which case we want to destroy
 * it, or the variable is immutable, in which case we want to make sure the
 * extracted value becomes immutable (in case the variable is being held onto by
 * something). Since the primitive invocation code is going to erase it if it's
 * mutable anyhow, only the second case requires any real work.
 */
@Suppress("unused")
object P_GetValue : Primitive1(
	CanInline,
	HasSideEffect)
{
	override fun attempt1(
		interpreter: Interpreter,
		variable: AvailObject
	): A_BasicObject? = interpreter.run {
		return try
		{
			if (traceVariableReadsBeforeWrites())
			{
				fiber().recordVariableAccess(variable, true)
			}
			variable.getValue()
		}
		catch (e: VariableGetException)
		{
			fail(e.errorCode)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralVariableType),
			ANY())

	context(_: Protected)
	override fun restrictionGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>): TypeRestriction
	{
		val varType = argumentTypes[0]
		val readType = varType.readType
		return restrictionForType(if (readType.isTop) ANY() else readType)
	}

	override fun L2GeneratorInterface.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSite: L2CallSite
	): Boolean
	{
		val varReg = arguments[0]
		val varType = varReg.type
		val varInnerType = varType.readType

		val success = createBasicBlock("get value success")
		val failure = createBasicBlock("get value failure/observe")
		val extractedValue = boxedWriteTemp(
			"extracted",
			restrictionForType(varInnerType))
		// Emit the get-variable instruction.
		+L2_GET_VARIABLE(
			varReg,
			extractedValue,
			edgeTo(success),
			edgeTo(failure))

		// Emit the failure path, which is the fallback to invoking the
		// primitive function and having it fail (presumably) into its failure
		// handling code.
		startBlock(failure)
		callSite.generateGeneralFunctionInvocation(
			functionToCallReg, false, arguments)

		// And now the success path.  Note that the failure path could have
		// also made it to the callSite's after-everything block if the
		// call returns successfully.
		startBlock(success)
		// Reading the variable can't cause any local variables to become shared
		// or acquire reactors.
		callSite.useAnswer(readBoxed(extractedValue))
		return true
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(
			E_CANNOT_READ_UNASSIGNED_VARIABLE,
			E_JAVA_MARSHALING_FAILED))
}
