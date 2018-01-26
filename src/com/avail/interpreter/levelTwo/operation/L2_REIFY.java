/*
 * L2_REIFY.java
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

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP;
import static com.avail.interpreter.levelTwo.L2OperandType.IMMEDIATE;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.utility.Nulls.stripNull;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Type.*;

/**
 * // TODO: [MvG] The comment is entirely wrong. Fix!
 * Throw a {@link StackReifier}, which unwinds the Java stack to the
 * outer {@link Interpreter} loop.  Any {@link L2Chunk}s that are active on the
 * stack will catch this throwable, reify an {@link A_Continuation} representing
 * the chunk's suspended state, add this to a list inside the throwable, then
 * re-throw for the next level.  Execution then continues within this
 * instruction, allowing the subsequent instructions to reify the current frame
 * as well.
 *
 * <p>If the top frame is reified this way, {@link L2_GET_CURRENT_CONTINUATION}
 * can be used to create label continuations.</p>
 *
 * <p>The "capture frames" operand is a flag that indicates whether to actually
 * capture the frames (1) or just discard them (0).</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2_REIFY
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_REIFY().init(
			IMMEDIATE.is("capture frames"),
			IMMEDIATE.is("process interrupt"),
			IMMEDIATE.is("statistic category"),
			PC.is("on reification", OFF_RAMP));

	/**
	 * An enumeration of reasons for reification, for the purpose of
	 * categorizing statistics gathering.
	 */
	public enum StatisticCategory
	{
		INTERRUPT_OFF_RAMP_IN_L2,
		PUSH_LABEL_IN_L2,
		ABANDON_BEFORE_RESTART_IN_L2;

		/** {@link Statistic} for reifying in L1 interrupt-handler preamble. */
		public final Statistic statistic =
			new Statistic(
				"Explicit L2_REIFY for " + name(),
				StatisticReport.REIFICATIONS);

		/** All the enumeration values. */
		private static final StatisticCategory[] all = values();

		/**
		 * Look up the category with the given ordinal.
		 *
		 * @param ordinal
		 *        The ordinal of the category to look up.
		 * @return The statistic category.
		 */
		public static StatisticCategory lookup (final int ordinal)
		{
			return all[ordinal];
		}
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Technically it doesn't have a side-effect, but this flag keeps the
		// instruction from being re-ordered to a place where the interpreter's
		// top reified continuation is no longer the right one.
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation;
		renderPreamble(instruction, builder);
		final boolean actuallyReify = instruction.immediateAt(0) == 1;
		final boolean processInterrupt = instruction.immediateAt(1) == 1;
		final StatisticCategory category =
			StatisticCategory.values()[instruction.immediateAt(2)];
		builder.append(' ');
		builder.append(category.name().replace("_IN_L2", "").toLowerCase());
		if (actuallyReify || processInterrupt)
		{
			builder.append(" [");
			if (actuallyReify)
			{
				builder.append("actually reify");
				if (processInterrupt)
				{
					builder.append(", ");
				}
			}
			if (processInterrupt)
			{
				builder.append("process interrupt");
			}
			builder.append(']');
		}
	}

	@SuppressWarnings("unused")
	@ReferencedInGeneratedCode
	public static StackReifier reify (
		final Interpreter interpreter,
		final boolean actuallyReify,
		final boolean processInterrupt,
		final int categoryIndex)
	{
		if (processInterrupt)
		{
			// Reify-and-interrupt.
			return new StackReifier(
				actuallyReify,
				interpreter.unreifiedCallDepth(),
				StatisticCategory.lookup(categoryIndex).statistic,
				() ->
				{
					interpreter.returnNow = false;
					interpreter.processInterrupt(
						stripNull(interpreter.reifiedContinuation));
				});
		}
		else
		{
			// Capture the interpreter's state, reify the frames, and as an
			// after-reification action, restore the interpreter's state.
			final A_Function savedFunction = stripNull(interpreter.function);
			final boolean newReturnNow = interpreter.returnNow;
			final @Nullable AvailObject newReturnValue =
				newReturnNow ? interpreter.latestResult() : null;

			// Reify-and-continue.  The current frame is also reified.
			return new StackReifier(
				actuallyReify,
				interpreter.unreifiedCallDepth(),
				StatisticCategory.lookup(categoryIndex).statistic,
				() ->
				{
					final A_Continuation continuation =
						stripNull(interpreter.reifiedContinuation);
					interpreter.function = savedFunction;
					interpreter.chunk = continuation.levelTwoChunk();
					interpreter.offset = continuation.levelTwoOffset();
					interpreter.returnNow = newReturnNow;
					interpreter.latestResult(newReturnValue);
					// Return into the Interpreter's run loop.
				});
		}
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final int actuallyReify = instruction.immediateAt(0);
		final int processInterrupt = instruction.immediateAt(1);
		final int categoryIndex = instruction.immediateAt(2);
		final L2PcOperand reify = instruction.pcAt(3);

		// :: reifier = L2_REIFY.reify(
		// ::    interpreter, actuallyReify, categoryIndex);
		translator.loadInterpreter(method);
		translator.literal(method, actuallyReify);
		translator.literal(method, processInterrupt);
		translator.literal(method, categoryIndex);
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(L2_REIFY.class),
			"reify",
			getMethodDescriptor(
				getType(StackReifier.class),
				getType(Interpreter.class),
				BOOLEAN_TYPE,
				BOOLEAN_TYPE,
				INT_TYPE),
			false);
		method.visitVarInsn(ASTORE, translator.reifierLocal());
		// :: goto reify;
		translator.branch(method, instruction, reify);
	}
}
