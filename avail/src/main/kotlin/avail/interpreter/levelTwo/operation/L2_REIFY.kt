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

import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.HiddenVariable.STACK_REIFIER
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
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
 * reify the entire Java stack. If "process interrupt" is true, process an
 * interrupt as soon as the reification is complete.  Otherwise continue running
 * at "on reification" with the reified state captured in the
 * [Interpreter.getReifiedContinuation].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@WritesHiddenVariable(STACK_REIFIER::class)
class L2_REIFY(
	var processInterrupt: L2IntImmediateOperand,
	var statisticName: L2ConstantOperand,
	@On(OFF_RAMP) var ifReification: L2PcOperand
) : L2ControlFlowInstruction()
{
	override val isCold get() = true

	/**
	 * Technically it doesn't have a side-effect, but this flag keeps the
	 * instruction from being re-ordered to a place where the interpreter's top
	 * reified continuation is no longer the right one.
	 */
	override val hasSideEffect get() = true

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

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		if (statisticName.constant.notNil)
		{
			append(' ')
			append(statisticName.constant)
		}
		if (processInterrupt.value != 0)
		{
			append(" [process interrupt]")
		}
		if (PC in desiredOperandTypes)
		{
			append("\n\t")
			append(::ifReification.name)
			append(" = ")
			append(increaseIndentation(ifReification.toString(), 1))
		}
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: reifier = interpreter.reify(processInterrupt, statistic)
		translator.loadInterpreter(method)
		translator.intConstant(method, processInterrupt.value)
		val statistic = if (processInterrupt.value != 0)
		{
			StatisticCategory.INTERRUPT_OFF_RAMP_IN_L2.statistic
		}
		else
		{
			Statistic(REIFICATIONS, statisticName.constant.asNativeString())
		}
		translator.loadLiteralObject(method, statistic)
		Interpreter.reifyMethod.generateCall(method)
		method.visitVarInsn(Opcodes.ASTORE, translator.reifierLocal())
		// Arrange to arrive at the onReification target, which must be an
		// L2_ENTER_L2_CHUNK.
		translator.generateReificationPreamble(method, ifReification)
	}
}
