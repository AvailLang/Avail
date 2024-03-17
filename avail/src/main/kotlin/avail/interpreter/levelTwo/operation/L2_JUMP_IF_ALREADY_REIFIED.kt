/*
 * L2_JUMP_IF_ALREADY_REIFIED.kt
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

import avail.descriptor.functions.A_Continuation
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Reification of the stack into a chain of [A_Continuation]s is powerful,
 * and provides backtracking, exceptions, readable stack traces, and reliable
 * debugger support.  However, it's also expensive.
 *
 *
 * To avoid the cost of a null reification, this operation checks to see if
 * we're already at the bottom of the Java call stack – we track the depth of
 * unreified calls in [Interpreter.unreifiedCallDepth].
 *
 *
 * If we're already reified (depth = 0), that means the
 * [Interpreter.getReifiedContinuation] represents our caller's fully
 * reified state, so take the "already reified" branch.  Otherwise, take the
 * "not yet reified" branch.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object L2_JUMP_IF_ALREADY_REIFIED : L2ConditionalJump(
	PC.named("already reified", SUCCESS),
	PC.named("not yet interrupt", FAILURE))
{
	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val alreadyReified = instruction.operand<L2PcOperand>(0)
		val notYetReified = instruction.operand<L2PcOperand>(1)

		// :: if (interpreter.isInterruptRequested()) goto ifInterrupt;
		// :: else goto ifNotInterrupt;
		translator.loadInterpreter(method)
		Interpreter.callerIsReifiedMethod.generateCall(method)
		emitBranch(
			translator,
			method,
			instruction,
			Opcodes.IFNE,
			alreadyReified,
			notYetReified)
	}
}
