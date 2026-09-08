/*
 * P_GetFiberPriority.kt
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
package avail.interpreter.primitive.fibers

import avail.descriptor.numbers.IntegerDescriptor.Companion.fromUnsignedByte
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Fiber
import avail.descriptor.representation.A_Fiber.Companion.priority
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u8
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.ReadsFromHiddenGlobalState
import avail.interpreter.primitive.Primitive1
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.jvm.JVMTranslator

/**
 * **Primitive:** Get the priority of a fiber.
 */
@Suppress("unused")
object P_GetFiberPriority : Primitive1(
	CannotFail, CanInline, ReadsFromHiddenGlobalState)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val fiber = arg1
		val priority = fiber.priority
		assert(priority in 0 .. 255)
		return fromUnsignedByte(priority.toShort())
	}

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		// First generate the specialized instruction to fill an int register.
		val fiberRead = arguments[0]
		val priorityIntWrite = intWriteTemp(
			"priority",
			restrictionForType(u8))
		+L2_GET_FIBER_PRIORITY_INT(fiberRead, priorityIntWrite)
		// Now box it in case someone needs it.  If nobody does, this will
		// evaporate later.
		val prioritySemanticIntValue = priorityIntWrite.pickSemanticValue()
		val boxedPriority = readBoxed(prioritySemanticIntValue)
		callSiteHelper.useAnswer(boxedPriority, false)
		return true
	}

	/**
	 * Extract the priority into an int.  A separate instruction will box it.
	 * Then when an L2 comparison between two priorities is discovered, we can
	 * translate that into a comparison of the unboxed versions, allowing the
	 * boxing operation to be omitted.
	 */
	class L2_GET_FIBER_PRIORITY_INT(
		var fiber: L2ReadBoxedOperand,
		var priority: L2WriteIntOperand
	): L2Instruction()
	{
		override fun JVMTranslator.translateToJVM()
		{
			load(fiber)
			generateCall(A_Fiber.getFiberPriorityMethod)
			store(priority.register())
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralFiberType()), u8)

	override val canDestroyArguments get() = false
}
