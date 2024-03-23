/*
 * L2_REIFY.kt
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
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.ARBITRARY_CONSTANT
import avail.interpreter.levelTwo.L2OperandType.Companion.INT_IMMEDIATE
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.L2Operation.HiddenVariable.STACK_REIFIER
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import avail.optimizer.jvm.JVMTranslator
import avail.performance.Statistic
import avail.performance.StatisticReport.REIFICATIONS
import avail.utility.Strings.increaseIndentation
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Create a StackReifier and jump to the "on reification" label.  This will
 * reify the entire Java stack (or discard it if "capture frames" is false).
 * If "process interrupt" is true, then process an interrupt as soon as the
 * reification is complete.  Otherwise continue running at "on reification" with
 * the reified state captured in the
 * [Interpreter.getReifiedContinuation].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@WritesHiddenVariable(STACK_REIFIER::class)
object L2_REIFY : L2ControlFlowOperation(
	INT_IMMEDIATE.named("capture frames"),
	INT_IMMEDIATE.named("process interrupt"),
	ARBITRARY_CONSTANT.named("statistic"),
	PC.named("on reification", OFF_RAMP))
{
	override fun isCold(instruction: L2Instruction): Boolean = true

	/**
	 * An enumeration of reasons for reification, for the purpose of
	 * categorizing statistics gathering.
	 */
	enum class StatisticCategory
	{
		/**
		 * For measuring reifications for interrupts in L2 code.
		 */
		INTERRUPT_OFF_RAMP_IN_L2,

		/**
		 * For measuring stack-clearing reifications prior to
		 * [P_RestartContinuation] and [P_RestartContinuationWithArguments]
		 * invocations in L2 code.
		 */
		ABANDON_BEFORE_RESTART_IN_L2;

		/** [Statistic] for reifying in L1 interrupt-handler preamble. */
		val statistic = Statistic(REIFICATIONS, "Explicit L2_REIFY for $name")

		companion object
		{
			/** All the enumeration values. */
			private val all = entries.toTypedArray()

			/**
			 * Look up the category with the given ordinal.
			 *
			 * @param ordinal
			 *   The ordinal of the category to look up.
			 * @return
			 *   The statistic category.
			 */
			fun lookup(ordinal: Int): StatisticCategory
			{
				return all[ordinal]
			}
		}
	}

	// Technically it doesn't have a side-effect, but this flag keeps the
	// instruction from being re-ordered to a place where the interpreter's
	// top reified continuation is no longer the right one.
	override val hasSideEffect get() = true

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		val actuallyReify = instruction.operand<L2IntImmediateOperand>(0)
		val processInterrupt = instruction.operand<L2IntImmediateOperand>(1)
		val statisticConstant =
			instruction.operand<L2ArbitraryConstantOperand>(2)
		//		final L2PcOperand onReification = instruction.operand(3);

		val statistic = statisticConstant.constant as Statistic
		instruction.renderPreamble(builder)
		builder.append(' ')
		builder.append(statistic.name())
		if (actuallyReify.value != 0 || processInterrupt.value != 0)
		{
			builder.append(" [")
			if (actuallyReify.value != 0)
			{
				builder.append("actually reify")
				if (processInterrupt.value != 0)
				{
					builder.append(", ")
				}
			}
			if (processInterrupt.value != 0)
			{
				builder.append("process interrupt")
			}
			builder.append(']')
		}
		val type = operandTypes[3]
		if (desiredTypes.contains(type.operandType()))
		{
			val operand = instruction.operand<L2Operand>(3)
			builder.append("\n\t")
			assert(operand.operandType === type.operandType())
			builder.append(type.name())
			builder.append(" = ")
			builder.append(increaseIndentation(operand.toString(), 1))
		}
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val actuallyReify = instruction.operand<L2IntImmediateOperand>(0)
		val processInterrupt = instruction.operand<L2IntImmediateOperand>(1)
		val statisticConstant =
			instruction.operand<L2ArbitraryConstantOperand>(2)
		val onReification = instruction.operand<L2PcOperand>(3)

		// :: reifier = interpreter.reify(
		// ::    actuallyReify, processInterrupt, statistic);
		translator.loadInterpreter(method)
		translator.literal(method, actuallyReify.value)
		translator.literal(method, processInterrupt.value)
		translator.literal(method, statisticConstant.constant as Statistic)
		Interpreter.reifyMethod.generateCall(method)
		method.visitVarInsn(Opcodes.ASTORE, translator.reifierLocal())
		// Arrange to arrive at the onReification target, which must be an
		// L2_ENTER_L2_CHUNK.
		translator.generateReificationPreamble(method, onReification)
	}
}
