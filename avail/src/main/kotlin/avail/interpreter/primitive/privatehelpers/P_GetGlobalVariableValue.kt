/*
 * P_GetGlobalVariableValue.kt
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
package avail.interpreter.primitive.privatehelpers

import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.literalAt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.readType
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.variables.A_Variable.Companion.getValue
import avail.descriptor.variables.A_Variable.Companion.globalName
import avail.descriptor.variables.A_Variable.Companion.hasValue
import avail.descriptor.variables.A_Variable.Companion.valueWasStablyComputed
import avail.exceptions.VariableGetException
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operation.variables.GetClearMode.NeverClear
import avail.interpreter.levelTwoSimple.L2SimpleTranslator
import avail.interpreter.levelTwoSimple.L2Simple_MoveConstant
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.Private
import avail.interpreter.primitive.Primitive.Flag.SpecialForm
import avail.interpreter.primitive.Primitive1
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator

/**
 * **Primitive:** A global variable's value is being returned.
 */
@Suppress("unused")
object P_GetGlobalVariableValue : Primitive1(
	SpecialForm, CanInline, Private, CannotFail)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val code = function!!.code()
		val literalVariable = code.literalAt(1)
		try
		{
			return literalVariable.getValue()
		}
		catch (e: VariableGetException)
		{
			throw AssertionError("A write-only variable must be assigned!", e)
		}
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type = rawFunction!!.literalAt(1).kind().readType

	/**
	 * This primitive is suitable for any function with any as the return type.
	 * We can't express that yet, so we allow any function.
	 */
	override fun privateBlockTypeRestriction(): A_Type = bottom

	override fun checkSpecialForm(
		numArgs: Int,
		literals: A_Tuple
	): Boolean = literals.tupleSize >= 1 &&
		literals.tupleAt(1).isInitializedWriteOnceVariable

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		// We have to know the specific function to know what variable to read
		// from, since it's the first literal.
		val function = functionToCallReg.constantOrNull ?: return false
		val variable = function.code().literalAt(1)
		// Avoid generating a constant move if the value wasn't stably computed.
		// While it would be the correct value, it wouldn't trigger the fast
		// loader suppression necessary to indicate that an unstable global
		// constant had been accessed, and a new global constant initialization
		// running this L2Chunk wouldn't be flagged correctly as also unstable.
		if (variable.isInitializedWriteOnceVariable &&
			variable.valueWasStablyComputed)
		{
			// The variable is permanently set to this value.
			callSiteHelper.useAnswer(boxedConstant(variable.getValue()), false)
			return true
		}
		val temp = newTemp("value of global ${variable.globalName}")
		emitGetVariableOffRamp(
			NeverClear,
			boxedConstant(variable),
			variable.kind().readType,
			destination = temp)
		callSiteHelper.useAnswer(readBoxed(temp), false)
		return true
	}

	override fun L2SimpleTranslator.attemptToGenerateSimpleInvocation(
		functionIfKnown: A_Function?,
		rawFunction: A_RawFunction,
		argRestrictions: List<TypeRestriction>,
		expectedType: A_Type
	): TypeRestriction?
	{
		val variable = rawFunction.literalAt(1)
		if (!variable.isInitializedWriteOnceVariable ||
			!variable.valueWasStablyComputed ||
			!variable.hasValue())
		{
			// The variable is not an initialized stable global.
			return defaultAttemptToGenerateSimpleInvocation(
				functionIfKnown,
				rawFunction,
				argRestrictions,
				expectedType)
		}
		// The variable is permanently set to this value.
		val constant = try
		{
			variable.getValue()
		}
		catch (e : VariableGetException)
		{
			throw RuntimeException(
				"Assigned write-once variable should not fail in getValue()")
		}
		+L2Simple_MoveConstant(constant, stackp)
		return boxedRestrictionForConstant(constant)
	}
}
