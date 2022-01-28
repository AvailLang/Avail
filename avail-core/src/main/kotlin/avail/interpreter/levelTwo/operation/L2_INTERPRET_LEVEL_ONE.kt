/*
 * L2_INTERPRET_LEVEL_ONE.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import avail.descriptor.functions.A_Continuation
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L1InstructionStepper
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2Operation
import avail.optimizer.StackReifier
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Use the [Interpreter.levelOneStepper] to execute the Level One
 * unoptimized nybblecodes.  If an interrupt request is indicated, throw a
 * [StackReifier], making sure to synthesize a continuation for the
 * current frame.
 *
 *
 * Note that Avail calls are now executed as Java calls, causing this thread
 * to block until either it completes or a [StackReifier] is
 * thrown, which causes an [A_Continuation] to be built, allowing the
 * Avail frame to continue executing later.
 *
 *
 * Single-stepping is currently not supported – perhaps a separate [L2Operation]
 * in a special [L2Chunk] would be an appropriate way to do that.  Also, be
 * careful not to saturate the interrupt request to the point that no progress
 * can be made.  Perhaps a solution to both concerns is to add a
 * one-step-delayed interrupt flag.  Querying the interrupt flag would cause the
 * delayed flag to be OR-ed into the current interrupt flag, returning its
 * previous value.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_INTERPRET_LEVEL_ONE : L2ControlFlowOperation(
	L2OperandType.PC.named(
		"call reentry point", L2NamedOperandType.Purpose.ON_RAMP),
	L2OperandType.PC.named(
		"interrupt reentry point", L2NamedOperandType.Purpose.ON_RAMP))
{
	// Keep this instruction from being removed, since it's only used by the
	// default chunk.
	override val hasSideEffect get() = true

	override fun isEntryPoint(instruction: L2Instruction): Boolean = true

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
//		final int callReentryOffset = instruction.pcAt(0);
//		final int interruptReentryOffset = instruction.pcAt(1);

		// :: return interpreter.levelOneStepper.run();
		translator.loadInterpreter(method)
		Interpreter.levelOneStepperField.generateRead(method)
		L1InstructionStepper.runMethod.generateCall(method)
		method.visitInsn(Opcodes.ARETURN)
	}
}
