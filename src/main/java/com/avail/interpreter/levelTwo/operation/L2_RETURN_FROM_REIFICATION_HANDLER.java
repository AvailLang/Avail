/*
 * L2_RETURN_FROM_REIFICATION_HANDLER.java
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
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Set;

import static com.avail.interpreter.Interpreter.interpreterReturningFunctionField;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED_VECTOR;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * Return from the reification clause of the current {@link L2Chunk}.  This
 * clause was invoked when a request for reification was detected inside an
 * invocation instruction.  The operand supplies a sequence of continuations
 * (each with nil callers), in the order in which the corresponding non-inlined
 * versions of the functions would have been invoked.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_RETURN_FROM_REIFICATION_HANDLER
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_RETURN_FROM_REIFICATION_HANDLER}.
	 */
	private L2_RETURN_FROM_REIFICATION_HANDLER ()
	{
		super(
			READ_BOXED_VECTOR.is("returned continuations"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_RETURN_FROM_REIFICATION_HANDLER instance =
		new L2_RETURN_FROM_REIFICATION_HANDLER();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Generator generator)
	{
		// A return instruction doesn't mention where it might end up.
		assert registerSets.size() == 0;
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
		final L2Operand registers = instruction.operand(0);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(registers);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedVectorOperand continuations = instruction.operand(0);

		method.visitVarInsn(ALOAD, translator.reifierLocal());
		for (int i = 0, limit = continuations.elements().size(); i < limit; i++)
		{
			// :: reifier.pushContinuation(«register»);
			if (i < limit - 1)
			{
				method.visitInsn(DUP);
			}
			translator.load(method, continuations.elements().get(i).register());
			method.visitMethodInsn(
				INVOKEVIRTUAL,
				getInternalName(StackReifier.class),
				"pushContinuation",
				getMethodDescriptor(VOID_TYPE, getType(A_Continuation.class)),
				false);
		}
		// :: interpreter.returnNow = true;
		translator.loadInterpreter(method);
		method.visitInsn(DUP);
		method.visitInsn(ICONST_1);
		method.visitFieldInsn(
			PUTFIELD,
			getInternalName(Interpreter.class),
			"returnNow",
			BOOLEAN_TYPE.getDescriptor());
		// :: interpreter.returningFunction = null;
		method.visitInsn(ACONST_NULL);
		interpreterReturningFunctionField.generateWrite(translator, method);
		method.visitVarInsn(ALOAD, translator.reifierLocal());
		method.visitInsn(ARETURN);
	}
}
