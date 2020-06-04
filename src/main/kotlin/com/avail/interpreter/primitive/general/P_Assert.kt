/*
 * P_Assert.kt
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
package com.avail.interpreter.primitive.general

import com.avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import com.avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import com.avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import com.avail.descriptor.fiber.FiberDescriptor.ExecutionState
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.ContinuationDescriptor.Companion.dumpStackThen
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomTypeDescriptor.bottom
import com.avail.descriptor.types.EnumerationTypeDescriptor
import com.avail.descriptor.types.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.TupleTypeDescriptor
import com.avail.descriptor.types.TupleTypeDescriptor.stringType
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailAssertionFailedException
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.Primitive.Flag.Unknown
import com.avail.interpreter.Primitive.Result.FIBER_SUSPENDED
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_EQUALS_CONSTANT
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import com.avail.optimizer.L2Generator.edgeTo

/**
 * **Primitive:** Assert the specified
 * [predicate][EnumerationTypeDescriptor.booleanType] or raise an
 * [AvailAssertionFailedException] (in Java) that contains the provided
 * [message][TupleTypeDescriptor.stringType].
 */
@Suppress("unused")
object P_Assert : Primitive(2, Unknown, CanSuspend, CannotFail)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val predicate = interpreter.argument(0)
		val failureMessage = interpreter.argument(1)

		if (predicate.extractBoolean())
		{
			return interpreter.primitiveSuccess(nil)
		}

		val fiber = interpreter.fiber()
		val continuation = interpreter.getReifiedContinuation()!!
		interpreter.primitiveSuspend(interpreter.function!!)
		dumpStackThen(
			interpreter.runtime(), fiber.textInterface(), continuation
		) { stack ->
			val killer = AvailAssertionFailedException(
				stack.joinToString(
					prefix = failureMessage.asNativeString(),
					separator = "",
					postfix = "\n\n"
				) { "\n\t-- $it" })
			killer.fillInStackTrace()
			fiber.setExecutionState(ExecutionState.ABORTED)
			fiber.failureContinuation()(killer)
		}
		return FIBER_SUSPENDED
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(booleanType(), stringType()), TOP.o())

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
	{
		val booleanType = argumentTypes[0]
		return if (trueObject().isInstanceOf(booleanType))
		{
			// The assertion might pass, so the type is top.
			TOP.o()
		}
		else
		{
			// The assertion can't pass, so the fiber will always terminate.
			// Thus, the type is bottom.
			bottom()
		}
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		assert(arguments.size == 2)
		val conditionType = argumentTypes[0]
		if (!falseObject().isInstanceOf(conditionType))
		{
			// The condition can't be false, so skip the call.
			callSiteHelper.useAnswer(
				translator.generator.boxedConstant(nil))
			return true
		}
		if (!trueObject().isInstanceOf(conditionType))
		{
			// The condition can't be true, so don't optimize the call.
			return false
		}
		// Failed assertions are rare, so avoid the cost of even the primitive
		// invocation if possible.  Actually, this is more about setting up a
		// branched control flow so that some of the computation of the message
		// string and the reification state can be pushed into the rare failure
		// path.
		val failPath = translator.generator.createBasicBlock("assertion failed")
		val passPath = translator.generator.createBasicBlock("after assertion")

		translator.addInstruction(
			L2_JUMP_IF_EQUALS_CONSTANT,
			arguments[0],
			L2ConstantOperand(trueObject()),
			edgeTo(passPath),
			edgeTo(failPath))

		translator.generator.startBlock(failPath)
		// Since this invocation will also be optimized, pass the constant false
		// as the condition argument to avoid infinite recursion.
		translator.generateGeneralFunctionInvocation(
			functionToCallReg,
			listOf(
				translator.generator.boxedConstant(falseObject()),
				arguments[1]),
			true,
			callSiteHelper)

		// Happy case.  Just push nil and jump to a suitable exit point.
		translator.generator.startBlock(passPath)
		callSiteHelper.useAnswer(translator.generator.boxedConstant(nil))
		return true
	}
}
