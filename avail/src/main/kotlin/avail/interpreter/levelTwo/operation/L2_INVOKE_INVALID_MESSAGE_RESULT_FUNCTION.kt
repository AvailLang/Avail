/*
 * L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION.kt
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
package avail.interpreter.levelTwo.operation

import avail.AvailRuntime
import avail.descriptor.representation.A_Continuation
import avail.descriptor.representation.AvailObject
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.reportWrongReturnTypeMethod
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_FUNCTION
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Inliner
import org.objectweb.asm.Opcodes

/**
 * Invoke the [AvailRuntime.resultDisagreedWithExpectedTypeFunction] handler
 * function, via [Interpreter.reportWrongReturnType], which takes responsibility
 * for assembling an [A_Continuation] in the event of reification.
 *
 * The calling frame's [function], and the [returningFunction] that produced
 * the value, are read explicitly, since either may be a function embedded in
 * the chunk by [L2Inliner], which neither runs as the interpreter's current
 * function nor returns through the interpreter.
 *
 * The handler always reifies, after which the stacked action builds the
 * calling frame.  If that frame is embedded in the running chunk's frame, the
 * running chunk's own frame must be built too, beneath it.  Since no
 * exception handler is ever embedded, the reification continues past both, so
 * the [onReification] edge, when present, leads to the reification path that
 * builds the running chunk's frame.  When absent, the calling frame is the
 * running chunk's own, and the chunk just returns.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property function
 *   The function of the frame that received the wrong value.
 * @property returningFunction
 *   The function that produced the wrong value.
 * @property returnedValue
 *   The value that disagreed with the expected type.
 * @property expectedType
 *   The type the value was expected to have.
 * @property pc
 *   The L1 program counter of the calling frame, for its continuation.
 * @property stackp
 *   The L1 stack pointer of the calling frame, for its continuation.
 * @property frameValues
 *   The values of the calling frame's slots, for its continuation.
 * @property onReification
 *   Either empty, or a single edge to the reification path of the running
 *   chunk's frame, when the calling frame is embedded in it.
 */
@WritesHiddenVariable(CURRENT_FUNCTION::class)
class
L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION(
	var function: L2ReadBoxedOperand,
	var returningFunction: L2ReadBoxedOperand,
	var returnedValue: L2ReadBoxedOperand,
	var expectedType: L2ConstantOperand,
	var pc: L2IntImmediateOperand,
	var stackp: L2IntImmediateOperand,
	var frameValues: L2ReadBoxedVectorOperand,
	@On(OFF_RAMP) var onReification: L2PcVectorOperand
) : L2ControlFlowInstruction()
{
	init
	{
		assert(onReification.edges.size <= 1)
	}

	override val isCold: Boolean get() = true

	// Never remove this.
	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(" function: ")
		append(function.registerString())
		append(", returner: ")
		append(returningFunction.registerString())
		append(", got: ")
		append(returnedValue.registerString())
		append(", expected: ")
		append(expectedType.constant.typeTag)
		append(", pc: ")
		append(pc.value)
		append(", stackp: ")
		append(stackp.value)
		append("\n\tframe data: ")
		frameValues.elements.joinTo(this, limit = 5) { it.registerString() }
	}

	override fun JVMTranslator.translateToJVM()
	{
		loadInterpreter()
		// :: interpreter
		load(function)
		load(returningFunction)
		load(returnedValue)
		loadLiteralObject(expectedType.constant)
		intConstant(pc.intValue)
		intConstant(stackp.intValue)
		// :: interpreter, function, returner, value, expected, pc, stackp
		objectArray(frameValues.elements, AvailObject::class.java)
		// :: interpreter, function, returner, value, expected, pc, stackp,
		// ::    frameArray
		generateCall(reportWrongReturnTypeMethod)
		// :: valueMustBeNull
		// Note that the above call took responsibility for creating the
		// reified continuation as needed.
		when (val edge = onReification.edges.singleOrNull())
		{
			null -> method.visitInsn(Opcodes.ARETURN)
			else ->
			{
				method.visitInsn(Opcodes.POP)
				// :: []
				generateReificationPreamble(edge)
			}
		}
	}
}
