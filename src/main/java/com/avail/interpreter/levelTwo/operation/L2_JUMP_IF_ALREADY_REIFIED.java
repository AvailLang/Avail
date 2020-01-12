/*
 * L2_JUMP_IF_ALREADY_REIFIED.java
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.descriptor.A_Continuation;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import static com.avail.interpreter.Interpreter.callerIsReifiedMethod;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static org.objectweb.asm.Opcodes.IFNE;

/**
 * Reification of the stack into a chain of {@link A_Continuation}s is powerful,
 * and provides backtracking, exceptions, readable stack traces, and reliable
 * debugger support.  However, it's also expensive.
 *
 * <p>To avoid the cost of a null reification, this operation checks to see if
 * we're already at the bottom of the Java call stack – we track the depth of
 * unreified calls in {@link Interpreter#unreifiedCallDepth()}.</p>
 *
 * <p>If we're already reified (depth = 0), that means the
 * {@link Interpreter#getReifiedContinuation()} represents our caller's fully
 * reified state, so take the "already reified" branch.  Otherwise, take the
 * "not yet reified" branch.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2_JUMP_IF_ALREADY_REIFIED
extends L2ConditionalJump
{
	/**
	 * Construct an {@code L2_JUMP_IF_ALREADY_REIFIED}.
	 */
	private L2_JUMP_IF_ALREADY_REIFIED ()
	{
		super(
			PC.is("already reified", SUCCESS),
			PC.is("not yet interrupt", FAILURE));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_JUMP_IF_ALREADY_REIFIED instance =
		new L2_JUMP_IF_ALREADY_REIFIED();

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2PcOperand alreadyReified = instruction.operand(0);
		final L2PcOperand notYetReified = instruction.operand(1);

		// :: if (interpreter.isInterruptRequested()) goto ifInterrupt;
		// :: else goto ifNotInterrupt;
		translator.loadInterpreter(method);
		callerIsReifiedMethod.generateCall(method);
		emitBranch(
			translator,
			method,
			instruction,
			IFNE,
			alreadyReified,
			notYetReified);
	}
}
