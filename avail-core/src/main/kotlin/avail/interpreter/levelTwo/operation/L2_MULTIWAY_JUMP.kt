/*
 * L2_MULTIWAY_JUMP.kt
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.CONSTANT
import avail.interpreter.levelTwo.L2OperandType.PC_VECTOR
import avail.interpreter.levelTwo.L2OperandType.READ_INT
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.optimizer.L2Generator
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor

/**
 * Given an integer and a constant tuple of N distinct integers in ascending
 * order, determine where the provided integer fits between the integers in the
 * tuple.  Then jump to one of the N+1 targets based on that computed index.
 *
 * If the integer is less than the first entry, jump to the first target.  If
 * the integer is greater than or equal to the first entry but less than the
 * second entry, jump to the second target, and so on.  If the integer is
 * greater than the last entry, jump to the N+1st target.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object L2_MULTIWAY_JUMP : L2ConditionalJump(
	READ_INT.named("value"),
	CONSTANT.named("split points"),
	PC_VECTOR.named("branch edges", SUCCESS))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val value = instruction.operand<L2ReadIntOperand>(0)
		val splitPoints = instruction.operand<L2ConstantOperand>(1)
		//val edges = instruction.operand<L2PcVectorOperand>(2)
		renderPreamble(instruction, builder)
		builder
			.append(" ")
			.append(value.registerString())
			.append(" in ")
			.append(splitPoints.constant)
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override val isPlaceholder get() = true

	override fun generateReplacement(
		instruction: L2Instruction,
		regenerator: L2Regenerator)
	{
		//TODO: If a lookupswitch instruction would be better than the binary
		// search mechanism, we could just do a super call to leave this
		// instruction intact, and then alter translateToJVM to generate the
		// lookupswitch instruction.
		assert(this == instruction.operation)
		val generator = regenerator.targetGenerator
		val value = regenerator.transformOperand(
			instruction.operand<L2ReadIntOperand>(0))
		val splitPoints = regenerator.transformOperand(
			instruction.operand<L2ConstantOperand>(1))
		val edges = regenerator.transformOperand(
			instruction.operand<L2PcVectorOperand>(2))

		val splitTuple : A_Tuple = splitPoints.constant
		generateSubtree(
			generator,
			value,
			splitTuple,
			edges.edges,
			1,
			splitTuple.tupleSize)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		throw UnsupportedOperationException(
			"${this@L2_MULTIWAY_JUMP.javaClass.simpleName} should " +
				"have been replaced during optimization")
	}

	/**
	 * Use the split values with indices `[`firstSplit..lastSplit`]` to
	 * determine which target to jump to.  For example if `firstSplit = 1` and
	 * `lastSplit = 1`, it should test against splitPoints`[`1`]` and branch to
	 * either edges`[`1`]` or edges`[`2`]`.  As another example, if
	 * `firstSplit = 5` and `lastSplit = 4`, this indicates edges`[`5`]` should
	 * be used without a further test.
	 */
	private fun generateSubtree(
		generator: L2Generator,
		value: L2ReadIntOperand,
		splitPoints: A_Tuple,
		edges: List<L2PcOperand>,
		firstSplit: Int,
		lastSplit: Int)
	{
		if (!generator.currentlyReachable()) return
		if (firstSplit > lastSplit)
		{
			assert(firstSplit == lastSplit + 1)
			val edge = edges[firstSplit - 1]  // Convert to zero-based.
			generator.jumpTo(edge.targetBlock())
			return
		}
		// Pick a split point near the middle of the range.
		val splitIndex = (firstSplit + lastSplit) ushr 1
		val splitValue = splitPoints.tupleIntAt(splitIndex)
		val left = generator.createBasicBlock("< $splitValue")
		val right = generator.createBasicBlock("≥ $splitValue")
		L2_JUMP_IF_COMPARE_INT.greaterOrEqual.compareAndBranch(
			generator,
			value,
			generator.unboxedIntConstant(splitValue),
			L2Generator.edgeTo(right),
			L2Generator.edgeTo(left))
		// Generate the left side.
		generator.startBlock(left)
		generateSubtree(
			generator,
			value,
			splitPoints,
			edges,
			firstSplit,
			splitIndex - 1)
		// Generate the right side.
		generator.startBlock(right)
		generateSubtree(
			generator,
			value,
			splitPoints,
			edges,
			splitIndex + 1,
			lastSplit)
	}
}
