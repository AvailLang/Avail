/*
 * L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.utility.Mutable;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static com.avail.interpreter.levelTwo.L2OperandType.INT_IMMEDIATE;
import static com.avail.optimizer.L2Translator.OptimizationLevel.optimizationLevel;
import static com.avail.utility.Nulls.stripNull;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * Explicitly decrement the current compiled code's countdown via {@link
 * AvailObject#countdownToReoptimize(int)}.  If it reaches zero then
 * re-optimize the code and jump to its {@link
 * L2Chunk#offsetAfterInitialTryPrimitive()}, which expects the arguments to
 * still be set up in the {@link Interpreter}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO
extends L2Operation
{
	/**
	 * Construct an {@code L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO}.
	 */
	private L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO ()
	{
		super(
			INT_IMMEDIATE.is("new optimization level"),
			INT_IMMEDIATE.is("is entry point"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO instance =
		new L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO();

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}

	@Override
	public boolean isEntryPoint (final L2Instruction instruction)
	{
		return instruction.intImmediateAt(1) != 0;
	}

	@ReferencedInGeneratedCode
	public static boolean decrement (
		final Interpreter interpreter,
		int targetOptimizationLevel)
	{
		final A_Function function = stripNull(interpreter.function);
		final A_RawFunction code = function.code();
		final Mutable<Boolean> chunkChanged = new Mutable<>(false);
		code.decrementCountdownToReoptimize(optimize ->
		{
			if (optimize)
			{
				code.countdownToReoptimize(
					L2Chunk.countdownForNewlyOptimizedCode());
				L2Translator.translateToLevelTwo(
					code,
					optimizationLevel(targetOptimizationLevel),
					interpreter);
			}
			final L2Chunk chunk = stripNull(code.startingChunk());
			interpreter.chunk = chunk;
			interpreter.offset = chunk.offsetAfterInitialTryPrimitive();
			chunkChanged.value = true;
		});
		return chunkChanged.value;
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final int targetOptimizationLevel = instruction.intImmediateAt(0);
//		final int isEntryPoint = instruction.intImmediateAt(1);

		// :: if (L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.decrement(
		// ::    interpreter, targetOptimizationLevel)) return null;
		translator.loadInterpreter(method);
		translator.literal(method, targetOptimizationLevel);
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.class),
			"decrement",
			getMethodDescriptor(
				BOOLEAN_TYPE,
				getType(Interpreter.class),
				INT_TYPE),
			false);
		final Label didNotOptimize = new Label();
		method.visitJumpInsn(IFEQ, didNotOptimize);
		method.visitInsn(ACONST_NULL);
		method.visitInsn(ARETURN);
		method.visitLabel(didNotOptimize);
	}
}
