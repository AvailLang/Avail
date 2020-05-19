/*
 * L2_JUMP_IF_EQUALS_CONSTANT.java
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
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding
import com.avail.optimizer.L2Generator
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.function.Consumer

/**
 * Jump to `"if equal"` if the value equals the constant, otherwise jump
 * to `"if unequal"`.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct an `L2_JUMP_IF_EQUALS_CONSTANT`.
 */
object L2_JUMP_IF_EQUALS_CONSTANT :
	L2ConditionalJump(
	L2OperandType.READ_BOXED.`is`("value"),
	L2OperandType.CONSTANT.`is`("constant"),
	L2OperandType.PC.`is`("if equal", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.`is`("if unequal", L2NamedOperandType.Purpose.FAILURE))
{
	override fun instructionWasAdded(
		instruction: L2Instruction, manifest: L2ValueManifest)
	{
		assert(this == instruction.operation())
		val reader = instruction.operand<L2ReadBoxedOperand>(0)
		val constant = instruction.operand<L2ConstantOperand>(1)
		val ifEqual = instruction.operand<L2PcOperand>(2)
		val ifNotEqual = instruction.operand<L2PcOperand>(3)
		super.instructionWasAdded(instruction, manifest)

		// Restrict the value to the constant along the ifEqual branch, and
		// exclude the constant along the ifNotEqual branch.
		val oldRestriction = reader.restriction()
		ifEqual.manifest().setRestriction(
			reader.semanticValue(),
			TypeRestriction.restrictionForConstant(
				constant.constant, RestrictionFlagEncoding.BOXED))
		ifNotEqual.manifest().setRestriction(
			reader.semanticValue(),
			oldRestriction.minusValue(constant.constant))
	}

	override fun branchReduction(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator): BranchReduction
	{
		// Eliminate tests due to type propagation.
		val value =
			instruction.operand<L2ReadBoxedOperand>(0)
		val constant =
			instruction.operand<L2ConstantOperand>(1)
		val valueOrNull: A_BasicObject? = value.constantOrNull()
		if (valueOrNull != null)
		{
			// Compare them right now.
			return if (valueOrNull.equals(constant.constant))
			{
				BranchReduction.AlwaysTaken
			}
			else
			{
				BranchReduction.NeverTaken
			}
		}
		return if (!constant.constant.isInstanceOf(value.type()))
		{
			// They can't be equal.
			BranchReduction.NeverTaken
		}
		else
		{
			BranchReduction.SometimesTaken
		}
		// Otherwise it's still contingent.
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: Consumer<Boolean>)
	{
		assert(this == instruction.operation())
		val value =
			instruction.operand<L2ReadBoxedOperand>(0)
		val constant
			= instruction.operand<L2ConstantOperand>(1)
		//		final L2PcOperand ifEqual = instruction.operand(2);
//		final L2PcOperand ifUnequal = instruction.operand(3);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(value.registerString())
		builder.append(" = ")
		builder.append(constant.constant)
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val value =
			instruction.operand<L2ReadBoxedOperand>(0)
		val constant =
			instruction.operand<L2ConstantOperand>(1)
		val ifEqual = instruction.operand<L2PcOperand>(2)
		val ifUnequal = instruction.operand<L2PcOperand>(3)

		// :: if (value.equals(constant)) goto ifEqual;
		// :: else goto ifUnequal;
		translator.load(method, value.register())
		translator.literal(method, constant.constant)
		A_BasicObject.equalsMethod.generateCall(method)
		emitBranch(
			translator, method, instruction, Opcodes.IFNE, ifEqual, ifUnequal)
	}
}