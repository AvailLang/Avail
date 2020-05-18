/*
 * L2_RESTART_CONTINUATION_WITH_ARGUMENTS.java
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
package com.avail.interpreter.levelTwo.operation

import com.avail.descriptor.functions.A_Continuation
import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.function.Consumer

/**
 * Restart the given [continuation][A_Continuation], which already has the
 * correct program counter and level two offset (in case the [L2Chunk] is
 * still valid).  The function will start at the beginning, using the supplied
 * arguments, rather than the ones that were captured within the continuation.
 *
 *
 * This operation does the same thing as running
 * [P_RestartContinuationWithArguments], but avoids the need for a reified
 * calling continuation.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_RESTART_CONTINUATION_WITH_ARGUMENTS
/**
 * Construct an `L2_RESTART_CONTINUATION_WITH_ARGUMENTS`.
 */
private constructor() : L2ControlFlowOperation(
	L2OperandType.READ_BOXED.`is`("continuation to restart"),
	L2OperandType.READ_BOXED_VECTOR.`is`("arguments"))
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSets: List<RegisterSet>,
		generator: L2Generator)
	{
		// Do nothing; there are no destinations reached from here within the
		// current chunk.  Technically the restart might be to somewhere in the
		// current chunk, but that's not a requirement.
		assert(registerSets.isEmpty())
	}

	override fun hasSideEffect(): Boolean
	{
		// Never remove this.
		return true
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: Consumer<Boolean>)
	{
		assert(this == instruction.operation())
		val continuation = instruction.operand<L2ReadBoxedOperand>(0)
		val arguments = instruction.operand<L2ReadBoxedVectorOperand>(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(continuation.registerString())
		builder.append("(")
		builder.append(arguments.elements())
		builder.append(")")
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val continuation = instruction.operand<L2ReadBoxedOperand>(0)
		val arguments = instruction.operand<L2ReadBoxedVectorOperand>(1)

		// :: return interpreter.reifierToRestart(
		// ::    continuation, argsArray);
		translator.loadInterpreter(method)
		translator.load(method, continuation.register())
		translator.objectArray(method, arguments.elements(), AvailObject::class.java)
		Interpreter.reifierToRestartWithArgumentsMethod.generateCall(method)
		method.visitInsn(Opcodes.ARETURN)
	}

	companion object
	{
		/**
		 * Initialize the sole instance.
		 */
		val instance = L2_RESTART_CONTINUATION_WITH_ARGUMENTS()
	}
}