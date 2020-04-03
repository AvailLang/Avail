/*
 * L2_SET_CONTINUATION.java
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

import com.avail.descriptor.functions.A_Continuation;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_CONTINUATION;
import com.avail.interpreter.levelTwo.WritesHiddenVariable;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;
import java.util.function.Consumer;

import static com.avail.interpreter.Interpreter.setReifiedContinuationMethod;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED;

/**
 * Set the {@link Interpreter#setReifiedContinuation(A_Continuation)}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@WritesHiddenVariable(
	CURRENT_CONTINUATION.class)
public final class L2_SET_CONTINUATION
extends L2Operation
{
	/**
	 * Construct an {@code L2_SET_CONTINUATION}.
	 */
	private L2_SET_CONTINUATION ()
	{
		super(
			READ_BOXED.is("replacement continuation"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_SET_CONTINUATION instance =
		new L2_SET_CONTINUATION();

	@Override
	public boolean hasSideEffect ()
	{
		// It updates the current continuation of the interpreter.
		return true;
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand continuation = instruction.operand(0);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(continuation.registerString());
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedOperand continuation = instruction.operand(0);

		// :: interpreter.setReifiedContinuation(aContinuation);
		translator.loadInterpreter(method);
		translator.load(method, continuation.register());
		setReifiedContinuationMethod.generateCall(method);
	}
}
