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

import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.PC
import avail.interpreter.levelTwo.L2OperandType.READ_INT
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.optimizer.L2Generator
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.utility.Tuple4
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.math.max
import kotlin.math.min

/**
 * Jump to the target if int1 compares to int2 in the way requested by the
 * [opcode].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property opcode
 *   The opcode that compares and branches to the success case.
 * @property opcodeName
 *   The symbolic name of the opcode that compares and branches to the success
 *   case.
 * @constructor
 * Construct an `L2_JUMP_IF_LESS_THAN_CONSTANT`.
 *
 * @param opcode
 *   The opcode number for this compare-and-branch.
 * @param opcodeName
 *   The symbolic name of the opcode for this compare-and-branch.
 * @param computeRestrictions
 *   A function for computing output ranges along the ifTrue and ifFalse edges.
 *   It takes the first operand's lower and upper bounds, and the second
 *   operand's lower and upper bound.  These are in the [i32] range, but are
 *   passed as a Long to simplify calculation.  It then produces four integer
 *   range [types][A_Type]s, restricting:
 *   1. the first operand if the condition holds,
 *   2. the second operand if the condition holds,
 *   3. the first operand if the condition fails,
 *   4. the second operand if the condition fails.
 */
class L2_JUMP_IF_COMPARE_INT private constructor(
		private val opcode: Int,
		private val opcodeName: String,
		private val computeRestrictions: (Long, Long, Long, Long) -> Tuple4<
			TypeRestriction, TypeRestriction, TypeRestriction, TypeRestriction>
	) : L2ConditionalJump(
		READ_INT.named("int1"),
		READ_INT.named("int2"),
		PC.named("if true", SUCCESS),
		PC.named("if false", FAILURE))
{
	/**
	 * Compare the int register values and branch to one target or the other.
	 * Restrict the possible values as much as possible along both branches.
	 * Convert the branch to an unconditional jump if possible.
	 */
	fun compareAndBranch(
		generator: L2Generator,
		int1Reg: L2ReadIntOperand,
		int2Reg: L2ReadIntOperand,
		ifTrue: L2PcOperand,
		ifFalse: L2PcOperand)
	{
		val restriction1 = int1Reg.restriction()
		val restriction2 = int2Reg.restriction()

		val low1 = restriction1.type.lowerBound.extractLong
		val high1 = restriction1.type.upperBound.extractLong
		val low2 = restriction2.type.lowerBound.extractLong
		val high2 = restriction2.type.upperBound.extractLong

		// Restrict both values along both branches.
		val (rest1, rest2, rest3, rest4) =
			computeRestrictions(low1, high1, low2, high2)
		when
		{
			rest1.type.isBottom || rest2.type.isBottom ->
			{
				// One of the registers would have an impossible value if the
				// ifTrue branch is taken, so always jump to the ifFalse case.
				generator.currentManifest.setRestriction(
					int1Reg.semanticValue(),
					restriction1.intersection(rest3))
				generator.currentManifest.setRestriction(
					int2Reg.semanticValue(),
					restriction2.intersection(rest4))
				generator.addInstruction(L2_JUMP, ifFalse)
			}
			rest3.type.isBottom || rest4.type.isBottom ->
			{
				// One of the registers would have an impossible value if the
				// ifFalse branch is taken, so always jump to the ifTrue case.
				generator.currentManifest.setRestriction(
					int1Reg.semanticValue(),
					restriction1.intersection(rest1))
				generator.currentManifest.setRestriction(
					int2Reg.semanticValue(),
					restriction2.intersection(rest2))
				generator.addInstruction(L2_JUMP, ifTrue)
			}
			else -> generator.addInstruction(
				this, int1Reg, int2Reg, ifTrue, ifFalse)
		}
	}

	override fun instructionWasAdded(
		instruction: L2Instruction, manifest: L2ValueManifest)
	{
		assert(this == instruction.operation)
		super.instructionWasAdded(instruction, manifest)
		val int1Reg = instruction.operand<L2ReadIntOperand>(0)
		val int2Reg = instruction.operand<L2ReadIntOperand>(1)
		val ifTrue = instruction.operand<L2PcOperand>(2)
		val ifFalse = instruction.operand<L2PcOperand>(3)

		val restriction1 = int1Reg.restriction()
		val restriction2 = int2Reg.restriction()

		val low1 = restriction1.type.lowerBound.extractLong
		val high1 = restriction1.type.upperBound.extractLong
		val low2 = restriction2.type.lowerBound.extractLong
		val high2 = restriction2.type.upperBound.extractLong

		// Restrict both values along both branches.
		val (type1, type2, type3, type4) =
			computeRestrictions(low1, high1, low2, high2)
		ifTrue.manifest().setRestriction(
			int1Reg.semanticValue(),
			restriction1.intersection(type1))
		ifTrue.manifest().setRestriction(
			int2Reg.semanticValue(),
			restriction2.intersection(type2))
		ifFalse.manifest().setRestriction(
			int1Reg.semanticValue(),
			restriction1.intersection(type3))
		ifFalse.manifest().setRestriction(
			int2Reg.semanticValue(),
			restriction2.intersection(type4))
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
		//		final L2PcOperand ifTrue = instruction.operand(2);
		//		final L2PcOperand ifFalse = instruction.operand(3);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(int1Reg.registerString())
		builder.append(" ")
		builder.append(opcodeName)
		builder.append(" ")
		builder.append(int2Reg.registerString())
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun toString(): String
	{
		return super.toString() + "(" + opcodeName + ")"
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
		compareAndBranch(
			regenerator.targetGenerator, int1Reg, int2Reg, ifTrue, ifFalse)
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
		emitBranch(translator, method, instruction, opcode, ifTrue, ifFalse)
	}

	companion object
	{
		private fun A_Type.narrow(): A_Type = when
		{
			lowerBound.equals(upperBound) -> instanceType(lowerBound)
			else -> this
		}

		/**
		 * Given two [i32] subranges, answer the range that a value from the
		 * first range can have if it's known to be less than a value from the
		 * second range.
		 */
		@Suppress("UNUSED_PARAMETER")
		private fun lessHelper(
			low1: Long, high1: Long, low2: Long, high2: Long
		) = restrictionForType(
			inclusive(low1, min(high1, high2 - 1)).narrow(), UNBOXED_INT_FLAG)

		/**
		 * Given two [i32] subranges, answer the range that a value from the
		 * first range can have if it's known to be less than or equal to a
		 * value from the second range.
		 */
		@Suppress("UNUSED_PARAMETER")
		private fun lessOrEqualHelper(
			low1: Long, high1: Long, low2: Long, high2: Long
		) = restrictionForType(
			inclusive(low1, min(high1, high2)).narrow(), UNBOXED_INT_FLAG)

		/**
		 * Given two [i32] subranges, answer the range that a value from the
		 * first range can have if it's known to be greater than a value from
		 * the second range.
		 */
		@Suppress("UNUSED_PARAMETER")
		private fun greaterHelper(
			low1: Long, high1: Long, low2: Long, high2: Long
		) = restrictionForType(
			inclusive(max(low1, low2 + 1), high1).narrow(), UNBOXED_INT_FLAG)

		/**
		 * Given two [i32] subranges, answer the range that a value from the
		 * first range can have if it's known to be greater than or equal to a
		 * value from the second range.
		 */
		@Suppress("UNUSED_PARAMETER")
		private fun greaterOrEqualHelper(
			low1: Long, high1: Long, low2: Long, high2: Long
		) = restrictionForType(
			inclusive(max(low1, low2), high1).narrow(), UNBOXED_INT_FLAG)

		/**
		 * Given two [i32] subranges, answer the range that a value from the
		 * first range can have if it's known to be equal to a value from the
		 * second range.
		 */
		private fun equalHelper(
			low1: Long, high1: Long, low2: Long, high2: Long
		) = restrictionForType(
			inclusive(max(low1, low2), min(high1, high2)).narrow(),
			UNBOXED_INT_FLAG)

		/**
		 * Given two [i32] subranges, answer the range that a value from the
		 * first range can have if it's known to be unequal to some value from
		 * the second range.
		 */
		private fun unequalHelper(
			low1: Long, high1: Long, low2: Long, high2: Long
		): TypeRestriction
		{
			if (low2 == high2)
			{
				// The second value is a particular constant which we can
				// exclude in the event the values are unequal.
				return restriction(
					inclusive(fromLong(low1), fromLong(high1)),
					null,
					givenExcludedValues = setOf(fromLong(low2)),
					isBoxed = false,
					isUnboxedInt = true)
			}
			return lessHelper(low1, high1, low2, high2).union(
				greaterHelper(low1, high1, low2, high2))
		}


		/** An instance for testing whether a < b. */
		val less = L2_JUMP_IF_COMPARE_INT(Opcodes.IF_ICMPLT, "<") {
			low1, high1, low2, high2 -> Tuple4(
				lessHelper(low1, high1, low2, high2),
				greaterHelper(low2, high2, low1, high1),
				greaterOrEqualHelper(low1, high1, low2, high2),
				lessOrEqualHelper(low2, high2, low1, high1))
		}

		/** An instance for testing whether a > b. */
		val greater = L2_JUMP_IF_COMPARE_INT(Opcodes.IF_ICMPGT, ">") {
			low1, high1, low2, high2 -> Tuple4(
				greaterHelper(low1, high1, low2, high2),
				lessHelper(low2, high2, low1, high1),
				lessOrEqualHelper(low1, high1, low2, high2),
				greaterOrEqualHelper(low2, high2, low1, high1))
		}

		/** An instance for testing whether a ≤ b. */
		val lessOrEqual = L2_JUMP_IF_COMPARE_INT(Opcodes.IF_ICMPLE, "≤") {
			low1, high1, low2, high2 -> Tuple4(
				lessOrEqualHelper(low1, high1, low2, high2),
				greaterOrEqualHelper(low2, high2, low1, high1),
				greaterHelper(low1, high1, low2, high2),
				lessHelper(low2, high2, low1, high1))
		}

		/** An instance for testing whether a ≥ b. */
		val greaterOrEqual = L2_JUMP_IF_COMPARE_INT(Opcodes.IF_ICMPGE, "≥") {
			low1, high1, low2, high2 -> Tuple4(
				greaterOrEqualHelper(low1, high1, low2, high2),
				lessOrEqualHelper(low2, high2, low1, high1),
				lessHelper(low1, high1, low2, high2),
				greaterHelper(low2, high2, low1, high1))
		}

		/** An instance for testing whether a = b. */
		val equal = L2_JUMP_IF_COMPARE_INT(Opcodes.IF_ICMPEQ, "=") {
			low1, high1, low2, high2 -> Tuple4(
				equalHelper(low1, high1, low2, high2),
				equalHelper(low2, high2, low1, high1),
				unequalHelper(low1, high1, low2, high2),
				unequalHelper(low2, high2, low1, high1))
		}

		/** An instance for testing whether a ≠ b. */
		val notEqual = L2_JUMP_IF_COMPARE_INT(Opcodes.IF_ICMPNE, "≠") {
			low1, high1, low2, high2 -> Tuple4(
				unequalHelper(low1, high1, low2, high2),
				unequalHelper(low2, high2, low1, high1),
				equalHelper(low1, high1, low2, high2),
				equalHelper(low2, high2, low1, high1))
		}
	}
}
