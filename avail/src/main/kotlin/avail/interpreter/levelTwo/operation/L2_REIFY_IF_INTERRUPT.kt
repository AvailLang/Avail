/*
 * L2_REIFY_IF_INTERRUPT.kt
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

import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.optimizer.jvm.JVMTranslator
import avail.performance.Statistic
import org.objectweb.asm.Opcodes

/**
 * If reification has been requested for some reason for this fiber, the call
 * to [Interpreter.statisticForRequestedInterruptMethod] will return the
 * [Statistic] under which to track the cost, and we should reify the outer call
 * stack, eventually invoking an action that runs more L2 code to reify the
 * current frame as well, ultimately returning null from the JVM frame.  After
 * interrupt processing completes, the chunk will be resumed at an L2 offset
 * captured within that continuation (if the chunk has become invalid, an L1
 * default chunk entry point will be invoked instead).
 *
 * In the far more frequent case that the returned statistic was `null`, we just
 * jump to [ifNotInterrupt].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_REIFY_IF_INTERRUPT(
	@On(OFF_RAMP) var ifInterrupt: L2PcOperand,
	@On(SUCCESS) var ifNotInterrupt: L2PcOperand
): L2ControlFlowInstruction()
{
	// It jumps, which counts as a side effect.
	override val hasSideEffect: Boolean get() = true

	override fun JVMTranslator.translateToJVM()
	{
		// :: if (interpreter.reifyIfInterrupt()) goto ifInterrupt
		// :: else goto ifNotinterrupt
		loadInterpreter()
		generateCall(Interpreter.reifyIfInterruptMethod)
		// Note: Don't bother capturing branch statistics here.
		method.visitJumpInsn(Opcodes.IFEQ, labelFor(ifNotInterrupt.offset()))
		// Interrupt was requested, and a reifier was set up for it, but not
		// yet populated with continuations.
		generateReificationPreamble(ifInterrupt)
	}
}
