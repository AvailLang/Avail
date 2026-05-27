/*
 * P_AtomicCompareAndSwap.kt
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

import avail.AvailRuntimeSupport
import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.writeType
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.A_Variable.Companion.compareAndSwapValues
import avail.descriptor.variables.A_Variable.Companion.compareAndSwapValuesNoCheck
import avail.exceptions.AvailErrorCode.E_CANNOT_MODIFY_FINAL_JAVA_FIELD
import avail.exceptions.AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE
import avail.exceptions.AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE
import avail.exceptions.AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE
import avail.exceptions.AvailErrorCode.E_JAVA_MARSHALING_FAILED
import avail.exceptions.AvailErrorCode.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operation.variables.L2_VARIABLE_COMPARE_AND_SWAP_NO_CHECK
import avail.interpreter.levelTwoSimple.L2SimpleTranslator
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive3
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.performance.Statistic
import avail.performance.StatisticReport.PRIMITIVES

/**
 * **Primitive:** Atomically read and conditionally overwrite the specified
 * [variable][A_Variable]. The overwrite occurs only if the value read from the
 * variable equals the reference value.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_AtomicCompareAndSwap : Primitive3(CanInline, HasSideEffect)
{
	override fun Interpreter.attempt3(
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?
	{
		val variable = arg1
		val reference = arg2
		val newValue = arg3
		val before = AvailRuntimeSupport.captureNanos()
		val replaced: Boolean = try
		{
			variable.compareAndSwapValues(reference, newValue)
		}
		catch (e: VariableGetException)
		{
			return fail(e.errorCode)
		}
		catch (e: VariableSetException)
		{
			return fail(e.errorCode)
		}
		if (!replaced)
		{
			val after = AvailRuntimeSupport.captureNanos()
			conflictStatistic.record(after - before, interpreterIndex)
		}
		return objectFromBoolean(replaced)
	}

	/**
	 * If the variable is shared and a local variable is captured inside the
	 * newValue, it could become shared.
	 */
	override fun mightMakeEscapedVariableShared(
		argumentTypes: List<A_Type>
	): Boolean = true

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val (variableReg, referenceReg, newValueReg) = arguments
		if (!newValueReg.type().isSubtypeOf(variableReg.type().writeType))
		{
			// We can't guarantee the type being assigned is strong enough.
			return false
		}
		val success = createBasicBlock("swap success")
		val failure = createBasicBlock("swap failure")
		val exception = createBasicBlock("swap exception")
		+L2_VARIABLE_COMPARE_AND_SWAP_NO_CHECK(
			variableReg,
			referenceReg,
			newValueReg,
			edgeTo(success),
			edgeTo(failure),
			edgeTo(exception))
		startBlock(success)
		callSiteHelper.useAnswer(boxedConstant(trueObject), false)

		startBlock(failure)
		callSiteHelper.useAnswer(boxedConstant(falseObject), false)

		startBlock(exception)
		generateGeneralFunctionInvocation(
			functionToCallReg, false, callSiteHelper, arguments)

		return true
	}

	/**
	 * Override to produce special code for this primitive, if it can be shown
	 * statically that the value being written is of the correct type.
	 */
	override fun L2SimpleTranslator.simplePrimitiveNilpotentInvocation(
		functionIfKnown: A_Function?,
		rawFunction: A_RawFunction,
		argRestrictions: List<TypeRestriction>,
		expectedType: A_Type
	): ((Interpreter)->A_BasicObject?)?
	{
		val variableType = argRestrictions[0].type
		//val referenceType = argRestrictions[1].type
		val newValueType = argRestrictions[2].type

		assert(variableType.isSubtypeOf(mostGeneralVariableType))
		val contentType = variableType.writeType
		if (!newValueType.isSubtypeOf(contentType))
		{
			return null
		}
		// The value being written doesn't need to be type checked at runtime.
		return ::nilpotentAttempt
	}

	override fun nilpotentAttempt(interpreter: Interpreter): A_BasicObject?
	{
		val (variable, reference, newValue) = interpreter.argsBuffer
		return try {
			objectFromBoolean(
				variable.compareAndSwapValuesNoCheck(reference, newValue))
		}
		catch (e: VariableGetException)
		{
			interpreter.fail(e.errorCode)
		}
		catch (e: VariableSetException)
		{
			interpreter.fail(e.errorCode)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralVariableType,
				ANY(),
				ANY()),
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

	/**
	 * A statistic that tracks the number of times (and CPU time) for attempts
	 * that didn't succeed in replacing the value because the reference value
	 * was no longer present in the variable.
	 */
	val conflictStatistic =
		Statistic(PRIMITIVES, "$simpleName (conflict on write)")
}
