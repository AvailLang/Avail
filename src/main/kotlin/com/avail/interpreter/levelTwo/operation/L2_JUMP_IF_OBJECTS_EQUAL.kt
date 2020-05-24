/*
 * L2_JUMP_IF_OBJECTS_EQUAL.kt
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
package com.avail.interpreter.levelTwo.operation

import com.avail.descriptor.representation.A_BasicObject
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Branch based on whether the two values are equal to each other.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_JUMP_IF_OBJECTS_EQUAL : L2ConditionalJump(
	L2OperandType.READ_BOXED.named("first value"),
	L2OperandType.READ_BOXED.named("second value"),
	L2OperandType.PC.named("is equal", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.named("is not equal", L2NamedOperandType.Purpose.FAILURE))
{
	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		assert(this == instruction.operation())
		val first = instruction.operand<L2ReadBoxedOperand>(0)
		val second = instruction.operand<L2ReadBoxedOperand>(1)
		val ifEqual = instruction.operand<L2PcOperand>(2)
		//val ifNotEqual = instruction.operand<L2PcOperand>(3)

		super.instructionWasAdded(instruction, manifest)

		// Merge the source and destination only along the ifEqual branch.
		ifEqual.manifest().mergeExistingSemanticValues(
			first.semanticValue(), second.semanticValue())
	}

	override fun branchReduction(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator): BranchReduction
	{
		val firstReg = instruction.operand<L2ReadBoxedOperand>(0)
		val secondReg = instruction.operand<L2ReadBoxedOperand>(1)
		//		final L2PcOperand ifEqual = instruction.operand(2);
//		final L2PcOperand notEqual = instruction.operand(3);
		val constant1: A_BasicObject? = firstReg.constantOrNull()
		val constant2: A_BasicObject? = secondReg.constantOrNull()
		return when
		{
			constant1 !== null
				&& constant2 !== null
				&& constant1.equals(constant2) ->
					BranchReduction.AlwaysTaken
			constant1 !== null && constant2 !== null ->
				BranchReduction.NeverTaken
			// They can't be equal.
			firstReg.type().typeIntersection(secondReg.type()).isBottom ->
				BranchReduction.NeverTaken
			// Otherwise it's still contingent.
			else -> BranchReduction.SometimesTaken
		}
	}

	override fun propagateTypes(
		instruction: L2Instruction,
		registerSets: List<RegisterSet>,
		generator: L2Generator)
	{
		val firstReg = instruction.operand<L2ReadBoxedOperand>(0)
		val secondReg = instruction.operand<L2ReadBoxedOperand>(1)
		assert(registerSets.size == 2)
		//		final RegisterSet fallThroughSet = registerSets.get(0);
		val postJumpSet = registerSets[1]

		// In the path where the registers compared equal, we can deduce that
		// both registers' origin registers must be constrained to the type
		// intersection of the two registers.
		val intersection =
			postJumpSet.typeAt(firstReg.register()).typeIntersection(
				postJumpSet.typeAt(secondReg.register()))
		postJumpSet.strengthenTestedTypeAtPut(
			firstReg.register(), intersection)
		postJumpSet.strengthenTestedTypeAtPut(
			secondReg.register(), intersection)

		// Furthermore, if one register is a constant, then in the path where
		// the registers compared equal we can deduce that both registers'
		// origin registers hold that same constant.
		if (postJumpSet.hasConstantAt(firstReg.register()))
		{
			postJumpSet.strengthenTestedValueAtPut(
				secondReg.register(),
				postJumpSet.constantAt(firstReg.register()))
		}
		else if (postJumpSet.hasConstantAt(secondReg.register()))
		{
			postJumpSet.strengthenTestedValueAtPut(
				firstReg.register(),
				postJumpSet.constantAt(secondReg.register()))
		}
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val first =
			instruction.operand<L2ReadBoxedOperand>(0)
		val second =
			instruction.operand<L2ReadBoxedOperand>(1)
		//		final L2PcOperand ifEqual = instruction.operand(2);
//		final L2PcOperand ifNotEqual = instruction.operand(3);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(first.registerString())
		builder.append(" = ")
		builder.append(second.registerString())
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val first = instruction.operand<L2ReadBoxedOperand>(0)
		val second = instruction.operand<L2ReadBoxedOperand>(1)
		val ifEqual = instruction.operand<L2PcOperand>(2)
		val ifNotEqual = instruction.operand<L2PcOperand>(3)

		// :: if (first.equals(second)) goto ifEqual;
		// :: else goto notEqual;
		translator.load(method, first.register())
		translator.load(method, second.register())
		A_BasicObject.equalsMethod.generateCall(method)
		emitBranch(translator, method, instruction, Opcodes.IFNE, ifEqual, ifNotEqual)
	}
}