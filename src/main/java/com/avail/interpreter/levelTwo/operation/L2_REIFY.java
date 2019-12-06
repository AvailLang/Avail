/*
 * L2_REIFY.java
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

import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2NamedOperandType;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.STACK_REIFIER;
import com.avail.interpreter.levelTwo.WritesHiddenVariable;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP;
import static com.avail.interpreter.levelTwo.L2OperandType.INT_IMMEDIATE;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.utility.Strings.increaseIndentation;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Type.*;

/**
 * Create a StackReifier and jump to the "on reification" label.  This will
 * reify the entire Java stack (or discard it if "capture frames" is false).
 * If "process interrupt" is true, then process an interrupt as soon as the
 * reification is complete.  Otherwise continue running at "on reification" with
 * the reified state captured in the
 * {@link Interpreter#getReifiedContinuation()}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@WritesHiddenVariable({
	// The instruction just sets up a new StackReifier.  It's the subsequent
	// operations that manipulate the state.
	STACK_REIFIER.class
})
public final class L2_REIFY
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_REIFY}.
	 */
	private L2_REIFY ()
	{
		super(
			INT_IMMEDIATE.is("capture frames"),
			INT_IMMEDIATE.is("process interrupt"),
			INT_IMMEDIATE.is("statistic category"),
			PC.is("on reification", OFF_RAMP));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_REIFY instance = new L2_REIFY();

	/**
	 * An enumeration of reasons for reification, for the purpose of
	 * categorizing statistics gathering.
	 */
	public enum StatisticCategory
	{
		/**
		 * For measuring reifications for interrupts in L2 code.
		 */
		INTERRUPT_OFF_RAMP_IN_L2,

		/**
		 * For measuring reifications required before label construction in L2
		 * code.
		 */
		PUSH_LABEL_IN_L2,

		/**
		 * For measuring stack-clearing reifications prior to {@link
		 * P_RestartContinuation} and {@link P_RestartContinuationWithArguments}
		 * invocations in L2 code.
		 */
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
		assert this == instruction.operation();
		final L2IntImmediateOperand actuallyReify = instruction.operand(0);
		final L2IntImmediateOperand processInterrupt = instruction.operand(1);
		final L2IntImmediateOperand categoryIndex = instruction.operand(2);
//		final L2PcOperand onReification = instruction.operand(3);

		final StatisticCategory category =
			StatisticCategory.values()[categoryIndex.value];

		renderPreamble(instruction, builder);
		builder.append(' ');
		//noinspection DynamicRegexReplaceableByCompiledPattern
		builder.append(category.name().replace("_IN_L2", "").toLowerCase());
		if (actuallyReify.value != 0 || processInterrupt.value != 0)
		{
			builder.append(" [");
			if (actuallyReify.value != 0)
			{
				builder.append("actually reify");
				if (processInterrupt.value != 0)
				{
					builder.append(", ");
				}
			}
			if (processInterrupt.value != 0)
			{
				builder.append("process interrupt");
			}
			builder.append(']');
		}
		final L2NamedOperandType type = operandTypes()[3];
		if (desiredTypes.contains(type.operandType()))
		{
			final L2Operand operand = instruction.operand(3);
			builder.append("\n\t");
			assert operand.operandType() == type.operandType();
			builder.append(type.name());
			builder.append(" = ");
			builder.append(increaseIndentation(operand.toString(), 1));
		}
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2IntImmediateOperand actuallyReify = instruction.operand(0);
		final L2IntImmediateOperand processInterrupt = instruction.operand(1);
		final L2IntImmediateOperand categoryIndex = instruction.operand(2);
		final L2PcOperand onReification = instruction.operand(3);

		// :: reifier = interpreter.reify(
		// ::    actuallyReify, processInterrupt, categoryIndex);
		translator.loadInterpreter(method);
		translator.literal(method, actuallyReify.value);
		translator.literal(method, processInterrupt.value);
		translator.literal(method, categoryIndex.value);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"reify",
			getMethodDescriptor(
				getType(StackReifier.class),
				BOOLEAN_TYPE,
				BOOLEAN_TYPE,
				INT_TYPE),
			false);
		method.visitVarInsn(ASTORE, translator.reifierLocal());
		// :: goto reify;
		translator.jump(method, instruction, onReification);
	}
}
