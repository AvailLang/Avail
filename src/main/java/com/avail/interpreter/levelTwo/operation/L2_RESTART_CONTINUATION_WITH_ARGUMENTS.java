/*
 * L2_RESTART_CONTINUATION_WITH_ARGUMENTS.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED_VECTOR;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

/**
 * Restart the given {@link A_Continuation continuation}, which already has the
 * correct program counter and level two offset (in case the {@link L2Chunk} is
 * still valid).  The function will start at the beginning, using the supplied
 * arguments, rather than the ones that were captured within the continuation.
 *
 * <p>This operation does the same thing as running {@link
 * P_RestartContinuationWithArguments}, but avoids the need for a reified
 * calling continuation.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_RESTART_CONTINUATION_WITH_ARGUMENTS
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_RESTART_CONTINUATION_WITH_ARGUMENTS}.
	 */
	private L2_RESTART_CONTINUATION_WITH_ARGUMENTS ()
	{
		super(
			READ_BOXED.is("continuation to restart"),
			READ_BOXED_VECTOR.is("arguments"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_RESTART_CONTINUATION_WITH_ARGUMENTS instance =
		new L2_RESTART_CONTINUATION_WITH_ARGUMENTS();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Generator generator)
	{
		// Do nothing; there are no destinations reached from here within the
		// current chunk.  Technically the restart might be to somewhere in the
		// current chunk, but that's not a requirement.
		assert registerSets.isEmpty();
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove this.
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand continuation = instruction.operand(0);
		final L2ReadBoxedVectorOperand arguments = instruction.operand(1);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(continuation.registerString());
		builder.append("(");
		builder.append(arguments.elements());
		builder.append(")");
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedOperand continuation = instruction.operand(0);
		final L2ReadBoxedVectorOperand arguments = instruction.operand(1);

		// :: return interpreter.reifierToRestart(
		// ::    continuation, argsArray);
		translator.loadInterpreter(method);
		translator.load(method, continuation.register());
		translator.objectArray(method, arguments.elements(), AvailObject.class);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"reifierToRestartWithArguments",
			getMethodDescriptor(
				getType(StackReifier.class),
				getType(A_Continuation.class),
				getType(AvailObject[].class)),
			false);
		method.visitInsn(ARETURN);
	}
}
