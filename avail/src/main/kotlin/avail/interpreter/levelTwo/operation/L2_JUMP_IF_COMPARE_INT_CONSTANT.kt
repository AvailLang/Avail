/*
 * L2_JUMP_IF_COMPARE_INT_CONSTANT.kt
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

import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.INT_IMMEDIATE
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_INT
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForConstant
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.L2MeetsRestrictionCondition.Companion.typeRestrictionCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor

/**
 * Jump to the target if int1 compares to the immediate int in the way
 * requested by the [numericComparator].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * @property numericComparator
 *   The [NumericComparator] on which this [L2_JUMP_IF_COMPARE_INT_CONSTANT] is
 *   based.
 */
class L2_JUMP_IF_COMPARE_INT_CONSTANT internal constructor(
	private val numericComparator: NumericComparator
) : L2ConditionalJump(
	READ_INT.named("int value"),
	INT_IMMEDIATE.named("constant"),
	PC.named("if true", SUCCESS),
	PC.named("if false", FAILURE))
{
	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(instruction, manifest)
		val int1Reg = instruction.operand<L2ReadIntOperand>(0)
		val constant = instruction.operand<L2IntImmediateOperand>(1)
		val ifTrue = instruction.operand<L2PcOperand>(2)
		val ifFalse = instruction.operand<L2PcOperand>(3)

		val restriction1 = int1Reg.restriction()
		val restriction2 = intRestrictionForConstant(constant.value)

		// Restrict the value along both branches.
		val (rest1, _, rest3, _) = numericComparator.computeRestrictions(
			restriction1.forBoxed(), restriction2.forBoxed()
		).map(TypeRestriction::forUnboxedInt)
		ifTrue.manifest().setRestriction(
			int1Reg.semanticValue(),
			restriction1.intersection(rest1))
		ifFalse.manifest().setRestriction(
			int1Reg.semanticValue(),
			restriction1.intersection(rest3))
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit
	) = with(builder)
	{
		val int1Reg = instruction.operand<L2ReadIntOperand>(0)
		val constant = instruction.operand<L2IntImmediateOperand>(1)
		//val ifTrue = instruction.operand<L2PcOperand>(2)
		//val ifFalse = instruction.operand<L2PcOperand>(3)

		renderPreamble(instruction, builder)
		append(' ')
		append(int1Reg.registerString())
		append(' ')
		append(numericComparator.comparatorName)
		append(" #")
		append(constant.value.toString())
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun toString(): String
	{
		return super.toString() + "(" + numericComparator.comparatorName + ")"
	}

	override fun interestingSplitConditions(
		instruction: L2Instruction
	): List<L2SplitCondition?>
	{
		val number1Reg = instruction.operand<L2ReadIntOperand>(0)
		val constant = instruction.operand<L2IntImmediateOperand>(1)
		val ifTrue = instruction.operand<L2PcOperand>(2)
		val ifFalse = instruction.operand<L2PcOperand>(3)

		// We can tell from the output restrictions what condition to wish for
		// (and its negation).  However, earlier comparisons may have made the
		// restriction unduly restrictive, and it may fail to find a suitable
		// split position (i.e., for v>10, when we've already narrowed v to
		// [0..20], we should still split (for the positive case) with the test
		// v ∈ [11..∞) instead of v ∈ [11..20], so that it can split at an
		// earlier position where, say, v ∈ [11..1000] was known.  Wishing for
		// v ∈ [11.20] would fail to detect that split point.
		//
		// Note that even though we know the value is an i32 here, we wish for
		// [11..∞) instead of [11..MAX_INT], in case there was a point before
		// the unboxing that detected, say, [11..10^100].

		val restriction1 = boxedRestrictionForType(integers)
		val restriction2 = boxedRestrictionForConstant(fromInt(constant.value))
		val (rest1, _, rest3, _) =
			numericComparator.computeRestrictions(restriction1, restriction2)
				.map(TypeRestriction::forUnboxedInt)
		// Wish it was statically true or statically false.  But only if that
		// situation would lead to a block that isn't cold.
		val conditions = mutableListOf<L2SplitCondition?>()
		if (!ifTrue.targetBlock().isCold)
		{
			conditions.add(
				typeRestrictionCondition(setOf(number1Reg.register()), rest1))
		}
		if (!ifFalse.targetBlock().isCold)
		{
			conditions.add(
				typeRestrictionCondition(setOf(number1Reg.register()), rest3))
		}
		return conditions
	}

	override fun emitTransformedInstruction(
		transformedOperands: Array<L2Operand>,
		regenerator: L2Regenerator)
	{
		val int1Reg = transformedOperands[0] as L2ReadIntOperand
		val constant = transformedOperands[1] as L2IntImmediateOperand
		val ifTrue = transformedOperands[2] as L2PcOperand
		val ifFalse = transformedOperands[3] as L2PcOperand

		val manifest = regenerator.currentManifest
		val int1Value = int1Reg.semanticValue()
		val restriction1 = int1Reg.restriction().intersection(
			manifest.restrictionFor(int1Value))
		val restriction2 = intRestrictionForConstant(constant.value)

		assert(restriction1.containedByType(i32))
		assert(restriction2.containedByType(i32))
		// Restrict values along both branches.
		val (rest1, _, rest3, _) = numericComparator.computeRestrictions(
			restriction1.forBoxed(), restriction2.forBoxed()
		).map(TypeRestriction::forUnboxedInt)
		val trueManifest = L2ValueManifest(manifest)
		trueManifest.updateRestriction(int1Value) { intersection(rest1) }
		val falseManifest = L2ValueManifest(manifest)
		falseManifest.updateRestriction(int1Value) { intersection(rest3) }
		when
		{
			trueManifest.hasImpossibleRestriction ->
			{
				// One of the registers would have an impossible value if the
				// ifTrue branch is taken, so always jump to the ifFalse case.
				manifest.updateRestriction(int1Value) { intersection(rest3) }
				regenerator.jumpTo(ifFalse.targetBlock())
			}
			falseManifest.hasImpossibleRestriction ->
			{
				// One of the registers would have an impossible value if the
				// ifFalse branch is taken, so always jump to the ifTrue case.
				manifest.updateRestriction(int1Value) { intersection(rest1) }
				regenerator.jumpTo(ifTrue.targetBlock())
			}
			else ->
			{
				//val int1Reg = instruction.operand<L2ReadIntOperand>(0)
				//val constant = instruction.operand<L2IntImmediateOperand>(1)
				//val ifTrue = instruction.operand<L2PcOperand>(2)
				//val ifFalse = instruction.operand<L2PcOperand>(3)
				regenerator.addInstruction(
					this,
					int1Reg,
					constant,
					L2PcOperand(
						ifTrue.targetBlock(),
						ifTrue.isBackward,
						trueManifest),
					L2PcOperand(
						ifFalse.targetBlock(),
						ifFalse.isBackward,
						falseManifest))
			}
		}
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val int1Reg = instruction.operand<L2ReadIntOperand>(0)
		val constant = instruction.operand<L2IntImmediateOperand>(1)
		val ifTrue = instruction.operand<L2PcOperand>(2)
		val ifFalse = instruction.operand<L2PcOperand>(3)

		// :: if (int1 op const) goto ifTrue;
		// :: else goto ifFalse;
		translator.load(method, int1Reg.register())
		translator.intConstant(method, constant.value)
		emitBranch(
			translator,
			method,
			instruction,
			numericComparator.opcode,
			ifTrue,
			ifFalse)
	}
}
