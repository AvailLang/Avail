/*
 * L2_UNBOX_INT.kt
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

import avail.descriptor.numbers.A_Number
import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_INT
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.L2IsUnboxedIntCondition.Companion.unboxedIntCondition
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticUnboxedInt
import org.objectweb.asm.MethodVisitor

/**
 * Unbox an [Int] from an [AvailObject].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_UNBOX_INT : L2Operation(
	READ_BOXED.named("source"),
	WRITE_INT.named("destination"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		val source = instruction.operand<L2ReadBoxedOperand>(0)
		val destination = instruction.operand<L2WriteIntOperand>(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(destination.registerString())
		builder.append(" ← ")
		builder.append(source.registerString())
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val source = instruction.operand<L2ReadBoxedOperand>(0)
		val destination = instruction.operand<L2WriteIntOperand>(1)

		// :: destination = source.extractInt();
		translator.load(method, source.register())
		A_Number.extractIntStaticMethod.generateCall(method)
		translator.store(method, destination.register())
	}

	override fun interestingSplitConditions(
		instruction: L2Instruction
	): List<L2SplitCondition?>
	{
		val source = instruction.operand<L2ReadBoxedOperand>(0)
		val destination = instruction.operand<L2WriteIntOperand>(1)
		return listOf(
			unboxedIntCondition(
				listOf(source.register(), destination.register())))
	}

	override fun emitTransformedInstruction(
		transformedOperands: Array<L2Operand>,
		regenerator: L2Regenerator)
	{
		val source = transformedOperands[0] as L2ReadBoxedOperand
		val destination = transformedOperands[1] as L2WriteIntOperand

		// Synonyms of ints are tricky, so check if there's an int version of
		// a synonym of the source available.
		val manifest = regenerator.currentManifest
		for (otherBoxed in
			manifest.semanticValueToSynonym(source.semanticValue())
				.semanticValues())
		{
			val otherUnboxed = L2SemanticUnboxedInt(otherBoxed)
			if (manifest.hasSemanticValue(otherUnboxed))
			{
				if (manifest.getDefinitions(otherUnboxed).isEmpty()) continue
				// It's already unboxed in an int register.  Make sure each
				// destination int semantic value gets written.
				for (destInt in destination.semanticValues())
				{
					if (!manifest.hasSemanticValue(destInt))
					{
						regenerator.moveRegister(
							L2_MOVE.unboxedInt, otherUnboxed, setOf(destInt))
					}
				}
				return
			}
		}
		// We have to unbox it.
		super.emitTransformedInstruction(transformedOperands, regenerator)
	}
}
