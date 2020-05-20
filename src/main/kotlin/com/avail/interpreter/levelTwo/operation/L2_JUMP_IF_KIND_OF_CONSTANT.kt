/*
 * L2_JUMP_IF_KIND_OF_CONSTANT.kt
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
import com.avail.optimizer.L2Generator
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Jump to the target if the object is an instance of the constant type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_JUMP_IF_KIND_OF_CONSTANT : L2ConditionalJump(
	L2OperandType.READ_BOXED.named("value"),
	L2OperandType.CONSTANT.named("constant type"),
	L2OperandType.PC.named("is kind", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.named("is not kind", L2NamedOperandType.Purpose.FAILURE))
{
	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		assert(this == instruction.operation())
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val constantType = instruction.operand<L2ConstantOperand>(1)
		val ifKind = instruction.operand<L2PcOperand>(2)
		val ifNotKind = instruction.operand<L2PcOperand>(3)
		super.instructionWasAdded(instruction, manifest)

		// Restrict to the intersection along the ifKind branch, and exclude the
		// type along the ifNotKind branch.
		val oldRestriction = value.restriction()
		ifKind.manifest().setRestriction(
			value.semanticValue(),
			oldRestriction.intersectionWithType(constantType.constant))
		ifNotKind.manifest().setRestriction(
			value.semanticValue(),
			oldRestriction.minusValue(constantType.constant))
	}

	override fun branchReduction(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator): BranchReduction
	{
		// Eliminate tests due to type propagation.
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val constantType = instruction.operand<L2ConstantOperand>(1)
		//		final L2PcOperand ifKind = instruction.operand(2);
//		final L2PcOperand ifNotKind = instruction.operand(3);
		val valueConstant: A_BasicObject? = value.constantOrNull()
		if (valueConstant != null)
		{
			return if (valueConstant.isInstanceOf(constantType.constant))
			{
				BranchReduction.AlwaysTaken
			}
			else
			{
				BranchReduction.NeverTaken
			}
		}
		val knownType = value.type()
		if (knownType.isSubtypeOf(constantType.constant))
		{
			// It's a subtype, so it must always pass the type test.
			return BranchReduction.AlwaysTaken
		}
		val intersection = constantType.constant.typeIntersection(knownType)
		return if (intersection.isVacuousType)
		{
			// The types don't intersect, so it can't ever pass the type test.
			BranchReduction.NeverTaken
		}
		else
		{
			BranchReduction.SometimesTaken
		}
	}

	override fun propagateTypes(
		instruction: L2Instruction,
		registerSets: List<RegisterSet>,
		generator: L2Generator)
	{
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val constantType = instruction.operand<L2ConstantOperand>(1)
		assert(registerSets.size == 2)
		val isKindSet = registerSets[0]
		//		final RegisterSet notKindSet = registerSets.get(1);
		val existingType = isKindSet.typeAt(value.register())
		val intersection = existingType.typeIntersection(constantType.constant)
		isKindSet.strengthenTestedTypeAtPut(value.register(), intersection)
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val constantType = instruction.operand<L2ConstantOperand>(1)
		//		final L2PcOperand ifKind = instruction.operand(2);
//		final L2PcOperand ifNotKind = instruction.operand(3);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(value.registerString())
		builder.append(" ∈ ")
		builder.append(constantType.constant)
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val constantType = instruction.operand<L2ConstantOperand>(1)
		val ifKind = instruction.operand<L2PcOperand>(2)
		val ifNotKind = instruction.operand<L2PcOperand>(3)

		// :: if (value.isInstanceOf(type)) goto isKind;
		// :: else goto notKind;
		translator.load(method, value.register())
		translator.literal(method, constantType.constant)
		A_BasicObject.isInstanceOfMethod.generateCall(method)
		emitBranch(translator, method, instruction, Opcodes.IFNE, ifKind, ifNotKind)
	}
}