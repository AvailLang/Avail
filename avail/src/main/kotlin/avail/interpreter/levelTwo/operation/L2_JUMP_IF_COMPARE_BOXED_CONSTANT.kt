/*
 * L2_JUMP_IF_COMPARE_BOXED_CONSTANT.kt
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

import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.CONSTANT
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.L2IsUnboxedIntCondition.Companion.unboxedIntCondition
import avail.optimizer.L2SplitCondition.L2MeetsRestrictionCondition.Companion.typeRestrictionCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Jump to the target if the boxed value compares to the boxed constant in the
 * way requested by the [numericComparator].  Note that they may be
 * incomparable, due to the way floating point numbers work, in which case the
 * comparison produces false.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *   Construct an [L2_JUMP_IF_COMPARE_BOXED_CONSTANT].
 * @property numericComparator
 *   The [NumericComparator] on which this [L2_JUMP_IF_COMPARE_BOXED_CONSTANT]
 *   is based.
 */
class L2_JUMP_IF_COMPARE_BOXED_CONSTANT internal constructor(
	private val numericComparator: NumericComparator
) : L2ConditionalJump(
	READ_BOXED.named("boxed value"),
	CONSTANT.named("constant"),
	PC.named("if true", SUCCESS),
	PC.named("if false", FAILURE))
{
	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(instruction, manifest)
		val number1Reg = instruction.operand<L2ReadBoxedOperand>(0)
		val constant = instruction.operand<L2ConstantOperand>(1)
		val ifTrue = instruction.operand<L2PcOperand>(2)
		val ifFalse = instruction.operand<L2PcOperand>(3)

		val restriction1 = number1Reg.restriction()
		val restriction2 = boxedRestrictionForConstant(constant.constant)

		if (restriction1.containedByType(integers)
			&& restriction2.containedByType(integers))
		{
			// Restrict the boxed value along both branches.
			val (rest1, _, rest3, _) =
				numericComparator.computeRestrictions(
					restriction1, restriction2)
			ifTrue.manifest().setRestriction(
				number1Reg.semanticValue(),
				restriction1.intersection(rest1))
			ifFalse.manifest().setRestriction(
				number1Reg.semanticValue(),
				restriction1.intersection(rest3))
		}
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit
	) = with(builder)
	{
		val number1Reg = instruction.operand<L2ReadBoxedOperand>(0)
		val constant = instruction.operand<L2ConstantOperand>(1)
		//val ifTrue = instruction.operand<L2PcOperand>(2)
		//val ifFalse = instruction.operand<L2PcOperand>(3)
		renderPreamble(instruction, builder)
		append(' ')
		append(number1Reg.registerString())
		append(' ')
		append(numericComparator.comparatorName)
		append(" $(")
		append(constant.constant)
		append(')')
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
		val number1Reg = instruction.operand<L2ReadBoxedOperand>(0)
		val constant = instruction.operand<L2ConstantOperand>(1)
		val ifTrue = instruction.operand<L2PcOperand>(2)
		val ifFalse = instruction.operand<L2PcOperand>(3)

		val conditions = mutableListOf<L2SplitCondition?>()
		// If the constant is an i32, it would be nice if the input value was
		// also unboxed.
		if (constant.constant.isInt)
		{
			conditions.add(unboxedIntCondition(listOf(number1Reg.register())))
		}
		// If the constant is an integer, and if the argument is an extended
		// integer, we can try to leverage that by keeping the code split
		// whenever the comparison would have been always true or always false.
		if (constant.constant.isInstanceOf(integers)
			&& number1Reg.restriction().containedByType(integers))
		{
			// HOWEVER, don't use the current restriction for the value, since
			// it might have been narrowed by previous comparisons.  Use the
			// broadest range (integers), to get the broadest type that can be
			// used to split the code as early as possible.
			val restriction1 = boxedRestrictionForType(integers)
			val restriction2 = boxedRestrictionForConstant(constant.constant)
			val (rest1, _, rest3, _) =
				numericComparator.computeRestrictions(
					restriction1, restriction2)
			// First, wish it was true, but only if the true path isn't cold.
			if (!ifTrue.targetBlock().isCold)
			{
				conditions.add(
					typeRestrictionCondition(
						setOf(number1Reg.register()),
						rest1))
			}
			// Also, wish it was false, but only if the false path isn't cold.
			if (!ifFalse.targetBlock().isCold)
			{
				conditions.add(
					typeRestrictionCondition(
						setOf(number1Reg.register()),
						rest3))
			}
		}
		return conditions
	}

	override fun emitTransformedInstruction(
		transformedOperands: Array<L2Operand>,
		regenerator: L2Regenerator)
	{
		val number1Reg = transformedOperands[0] as L2ReadBoxedOperand
		val constant = transformedOperands[1] as L2ConstantOperand
		val ifTrue = transformedOperands[2] as L2PcOperand
		val ifFalse = transformedOperands[3] as L2PcOperand

		// Use the basic generator to check if the branch can be elided.
		regenerator.compareAndBranchBoxed(
			numericComparator,
			number1Reg,
			regenerator.boxedConstant(constant.constant),
			ifTrue,
			ifFalse)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val number1Reg = instruction.operand<L2ReadBoxedOperand>(0)
		val constant = instruction.operand<L2ConstantOperand>(1)
		val ifTrue = instruction.operand<L2PcOperand>(2)
		val ifFalse = instruction.operand<L2PcOperand>(3)

		// :: if (num1 op const) goto ifTrue;
		// :: else goto ifFalse;
		translator.load(method, number1Reg.register())
		translator.literal(method, constant.constant)
		numericComparator.comparatorMethod.generateCall(method)
		// The boolean is now on the stack.  See if we can emit a single branch
		// and fall-through, versus having to emit a branch and a jump.
		when (instruction.offset + 1)
		{
			ifTrue.instruction.offset ->
				translator.jumpIf(method, Opcodes.IFEQ, ifFalse)
			ifFalse.instruction.offset ->
				translator.jumpIf(method, Opcodes.IFNE, ifTrue)
			else ->
			{
				// Can't fall through.  Emit a branch and a jump.
				translator.jumpIf(method, Opcodes.IFEQ, ifFalse)
				translator.jump(method, ifTrue)
			}
		}
	}
}
