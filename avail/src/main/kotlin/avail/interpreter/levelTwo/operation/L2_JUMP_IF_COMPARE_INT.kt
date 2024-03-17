/*
 * L2_JUMP_IF_COMPARE_INT.kt
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

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_INT
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor

/**
 * Jump to the target if int1 compares to int2 in the way requested by the
 * [numericComparator].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * @property numericComparator
 *   The [NumericComparator] on which this [L2_JUMP_IF_COMPARE_INT] is based.
 */
class L2_JUMP_IF_COMPARE_INT internal constructor(
	private val numericComparator: NumericComparator
) : L2ConditionalJump(
	READ_INT.named("int1"),
	READ_INT.named("int2"),
	PC.named("if true", SUCCESS),
	PC.named("if false", FAILURE))
{
	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		assert(this == instruction.operation)
		super.instructionWasAdded(instruction, manifest)
		val int1Reg = instruction.operand<L2ReadIntOperand>(0)
		val int2Reg = instruction.operand<L2ReadIntOperand>(1)
		val ifTrue = instruction.operand<L2PcOperand>(2)
		val ifFalse = instruction.operand<L2PcOperand>(3)

		val restriction1 = int1Reg.restriction().intersection(
			manifest.restrictionFor(int1Reg.semanticValue()))
		val restriction2 = int2Reg.restriction().intersection(
			manifest.restrictionFor(int2Reg.semanticValue()))

		// Restrict both values along both branches.
		val (rest1, rest2, rest3, rest4) =
			numericComparator.computeRestrictions(
				restriction1.forBoxed(), restriction2.forBoxed()
			).map(TypeRestriction::forUnboxedInt)
		ifTrue.manifest().setRestriction(
			int1Reg.semanticValue(),
			restriction1.intersection(rest1))
		ifTrue.manifest().setRestriction(
			int2Reg.semanticValue(),
			restriction2.intersection(rest2))
		ifFalse.manifest().setRestriction(
			int1Reg.semanticValue(),
			restriction1.intersection(rest3))
		ifFalse.manifest().setRestriction(
			int2Reg.semanticValue(),
			restriction2.intersection(rest4))
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val int1Reg = instruction.operand<L2ReadIntOperand>(0)
		val int2Reg = instruction.operand<L2ReadIntOperand>(1)
		//val ifTrue = instruction.operand<L2PcOperand>(2)
		//val ifFalse = instruction.operand<L2PcOperand>(3)

		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(int1Reg.registerString())
		builder.append(" ")
		builder.append(numericComparator.comparatorName)
		builder.append(" ")
		builder.append(int2Reg.registerString())
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun toString(): String
	{
		return super.toString() + "(" + numericComparator.comparatorName + ")"
	}

	override fun emitTransformedInstruction(
		transformedOperands: Array<L2Operand>,
		regenerator: L2Regenerator
	)
	{
		val int1Reg = transformedOperands[0] as L2ReadIntOperand
		val int2Reg = transformedOperands[1] as L2ReadIntOperand
		val ifTrue = transformedOperands[2] as L2PcOperand
		val ifFalse = transformedOperands[3] as L2PcOperand

		// Use the basic generator to check if the branch can be elided.
		regenerator.compareAndBranchInt(
			numericComparator, int1Reg, int2Reg, ifTrue, ifFalse)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val int1Reg = instruction.operand<L2ReadIntOperand>(0)
		val int2Reg = instruction.operand<L2ReadIntOperand>(1)
		val ifTrue = instruction.operand<L2PcOperand>(2)
		val ifFalse = instruction.operand<L2PcOperand>(3)

		// :: if (int1 op int2) goto ifTrue;
		// :: else goto ifFalse;
		translator.load(method, int1Reg.register())
		translator.load(method, int2Reg.register())
		emitBranch(
			translator,
			method,
			instruction,
			numericComparator.opcode,
			ifTrue,
			ifFalse)
	}
}
