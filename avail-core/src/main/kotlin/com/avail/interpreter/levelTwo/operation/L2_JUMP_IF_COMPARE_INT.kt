/*
 * L2_JUMP_IF_COMPARE_INT.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.interpreter.levelTwo.operation

import com.avail.descriptor.numbers.A_Number.Companion.extractLong
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2OperandType.PC
import com.avail.interpreter.levelTwo.L2OperandType.READ_INT
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.utility.Tuple4
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.math.max
import kotlin.math.min

/**
 * Jump to the target if int1 is less than int2.
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
 *   operand's lower and upper bound.  These are in the [int32] range, but are
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
		private val computeRestrictions: (Long, Long, Long, Long) ->
			Tuple4<A_Type, A_Type, A_Type, A_Type>
	) : L2ConditionalJump(
		READ_INT.named("int1"),
		READ_INT.named("int2"),
		PC.named("if true", Purpose.SUCCESS),
		PC.named("if false", Purpose.FAILURE))
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
		val (type1, type2, type3, type4) =
			computeRestrictions(low1, high1, low2, high2)
		when
		{
			type1.isBottom || type2.isBottom ->
			{
				// One of the registers would have an impossible value if the
				// ifTrue branch is taken, so always jump to the ifFalse case.
				ifFalse.manifest().setRestriction(
					int1Reg.semanticValue(),
					restriction1.intersectionWithType(type3))
				ifFalse.manifest().setRestriction(
					int2Reg.semanticValue(),
					restriction2.intersectionWithType(type4))
				generator.addInstruction(L2_JUMP, ifFalse)
			}
			type3.isBottom || type4.isBottom ->
			{
				// One of the registers would have an impossible value if the
				// ifFalse branch is taken, so always jump to the ifTrue case.
				ifTrue.manifest().setRestriction(
					int1Reg.semanticValue(),
					restriction1.intersectionWithType(type1))
				ifTrue.manifest().setRestriction(
					int2Reg.semanticValue(),
					restriction2.intersectionWithType(type2))
				generator.addInstruction(L2_JUMP, ifTrue)
			}
			else -> generator.addInstruction(
				this, int1Reg, int2Reg, ifTrue, ifFalse)
		}
	}

	override fun instructionWasAdded(
		instruction: L2Instruction, manifest: L2ValueManifest)
	{
		assert(this == instruction.operation())
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
			restriction1.intersectionWithType(type1))
		ifTrue.manifest().setRestriction(
			int2Reg.semanticValue(),
			restriction2.intersectionWithType(type2))
		ifFalse.manifest().setRestriction(
			int1Reg.semanticValue(),
			restriction1.intersectionWithType(type3))
		ifFalse.manifest().setRestriction(
			int2Reg.semanticValue(),
			restriction2.intersectionWithType(type4))
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
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
		 * Given two [int32] subranges, answer the range that a value from the
		 * first range can have if it's known to be less than a value from the
		 * second range.
		 */
		@Suppress("UNUSED_PARAMETER")
		private fun lessHelper(
			low1: Long, high1: Long, low2: Long, high2: Long
		) = inclusive(low1, min(high1, high2 - 1)).narrow()

		/**
		 * Given two [int32] subranges, answer the range that a value from the
		 * first range can have if it's known to be less than or equal to a
		 * value from the second range.
		 */
		@Suppress("UNUSED_PARAMETER")
		private fun lessOrEqualHelper(
			low1: Long, high1: Long, low2: Long, high2: Long
		) = inclusive(low1, min(high1, high2)).narrow()

		/**
		 * Given two [int32] subranges, answer the range that a value from the
		 * first range can have if it's known to be greater than a value from
		 * the second range.
		 */
		@Suppress("UNUSED_PARAMETER")
		private fun greaterHelper(
			low1: Long, high1: Long, low2: Long, high2: Long
		) = inclusive(max(low1, low2 + 1), high1).narrow()

		/**
		 * Given two [int32] subranges, answer the range that a value from the
		 * first range can have if it's known to be greater than or equal to a
		 * value from the second range.
		 */
		@Suppress("UNUSED_PARAMETER")
		private fun greaterOrEqualHelper(
			low1: Long, high1: Long, low2: Long, high2: Long
		) = inclusive(max(low1, low2), high1).narrow()

		/**
		 * Given two [int32] subranges, answer the range that a value from the
		 * first range can have if it's known to be equal to a value from the
		 * second range.
		 */
		private fun equalHelper(
			low1: Long, high1: Long, low2: Long, high2: Long
		) = inclusive(max(low1, low2), min(high1, high2)).narrow()

		/**
		 * Given two [int32] subranges, answer the range that a value from the
		 * first range can have if it's known to be unequal to some value from
		 * the second range.
		 */
		private fun unequalHelper(
			low1: Long, high1: Long, low2: Long, high2: Long
		) : A_Type =
			lessHelper(low1, high1, low2, high2).typeUnion(
				greaterHelper(low1, high1, low2, high2)
			).narrow()

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
