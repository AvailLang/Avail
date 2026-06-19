/*
 * L2_CREATE_CONTINUATION.kt
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationExceptFrameMethod
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_RawFunction.Companion.declarationNamesWithoutOuters
import avail.descriptor.representation.A_RawFunction.Companion.numArgs
import avail.descriptor.representation.A_RawFunction.Companion.numLocals
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Tuple.Companion.tupleAt
import avail.descriptor.representation.A_Tuple.Companion.tupleSize
import avail.descriptor.representation.AvailObject
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.HideInSimpleVisualization
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.jvm.JVMTranslator

/**
 * Create a continuation from scratch, using the specified caller, function,
 * constant level one program counter, constant stack pointer, continuation
 * slot values, and level two program counter.  Write the new continuation
 * into the specified register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_CREATE_CONTINUATION
constructor (
	var function: L2ReadBoxedOperand,
	@HideInSimpleVisualization
	var code: L2ConstantOperand,
	var caller: L2ReadBoxedOperand,
	var levelOnePc: L2IntImmediateOperand,
	var levelOneStackp: L2IntImmediateOperand,
	var slotValues: L2ReadBoxedVectorOperand,
	var destination: L2WriteBoxedOperand,
	var labelAddress: L2ReadIntOperand,
	var registerDump: L2ReadBoxedOperand,
	var comment: L2CommentOperand
): L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(destination)
		append(" ← $[")
		append(function)
		append("]\n\tpc=")
		append(levelOnePc.value)
		append("\n\tstack=[")
		val code = this@L2_CREATE_CONTINUATION.code.constant
		val slotNames = code.declarationNamesWithoutOuters
		// This range includes a primitive failure variable (first), if present.
		var localRangeStart = code.numArgs() + 1
		var primFailureIndex = -1
		if (code.codePrimitive() != null) primFailureIndex = localRangeStart++
		val localRange = localRangeStart .. code.numArgs() + code.numLocals
		slotValues.elements.forEachIndexed { zeroIndex, slot ->
			val slotIndex = zeroIndex + 1
			append(
				when (slotIndex == levelOneStackp.value)
				{
					true -> "\n\t->\t"
					else -> "\n\t\t"
				})
			append(slotIndex)
			if (slotIndex <= slotNames.tupleSize)
			{
				append(" ")
				when (slotIndex)
				{
					primFailureIndex -> append("[PrimFail] ")
					in localRange -> append("↑ ")
				}
				append(slotNames.tupleAt(slotIndex).asNativeString())
			}
			append(": ")
			append(slot.registerString())
		}
		append("]\n\tstackp=")
		append(levelOneStackp.value)
		append("\n\tcaller=")
		append(caller)
		renderOperandsExcludingFields(
			desiredOperandTypes,
			::comment,
			::destination,
			::function,
			::code,
			::slotValues,
			::levelOneStackp,
			::caller)
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: continuation = createContinuationExceptFrame(
		// ::    function,
		// ::    caller,
		// ::    registerDump
		// ::    levelOnePC,
		// ::    levelOneStackp,
		// ::    interpreter.chunk,
		// ::    onRampOffset);
		load(function)
		load(caller)
		load(registerDump)
		intConstant(levelOnePc.value)
		intConstant(levelOneStackp.value)
		loadInterpreter()
		load(Interpreter.chunkField)
		load(labelAddress)
		generateCall(createContinuationExceptFrameMethod)
		val slotCount = slotValues.elements.size
		var pushed = 0
		for (i in 0 until slotCount)
		{
			val regRead = slotValues.elements[i]
			val constant: A_BasicObject? = regRead.constantOrNull
			// Skip if it's always nil, since the continuation was already
			// initialized with nils.
			if (constant === null || constant.notNil)
			{
				// :: continuation.frameAtPut(«i + 1», «slots[i]»)...
				// [continuation]
				intConstant(i + 1)
				load(slotValues.elements[i])
				if (++pushed == 6)
				{
					generateCall(AvailObject.frameAtPut6Method)
					// Method returns continuation to simplify stack handling.
					// [continuation]
					pushed = 0
				}
			}
		}
		when (pushed)
		{
			0 -> { }
			1 -> generateCall(AvailObject.frameAtPutMethod)
			2 -> generateCall(AvailObject.frameAtPut2Method)
			3 -> generateCall(AvailObject.frameAtPut3Method)
			4 -> generateCall(AvailObject.frameAtPut4Method)
			5 -> generateCall(AvailObject.frameAtPut5Method)
			else -> throw AssertionError(
				"Internal error - wrong bulk write size for frame")
		}
		// [continuation]
		store(destination.register())
	}
}
